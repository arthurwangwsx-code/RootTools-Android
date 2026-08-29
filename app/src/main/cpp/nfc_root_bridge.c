#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <sys/types.h>
#include <unistd.h>

#define BRIDGE_VERSION "2"

static void trim_newline(char *value) {
    size_t len = strlen(value);
    while (len > 0 && (value[len - 1] == '\n' || value[len - 1] == '\r')) {
        value[--len] = '\0';
    }
}

static void json_string(const char *value) {
    putchar('"');
    if (value != NULL) {
        for (const unsigned char *p = (const unsigned char *)value; *p; ++p) {
            switch (*p) {
                case '"': fputs("\\\"", stdout); break;
                case '\\': fputs("\\\\", stdout); break;
                case '\n': fputs("\\n", stdout); break;
                case '\r': fputs("\\r", stdout); break;
                case '\t': fputs("\\t", stdout); break;
                default:
                    if (*p < 0x20) {
                        printf("\\u%04x", *p);
                    } else {
                        putchar(*p);
                    }
            }
        }
    }
    putchar('"');
}

static bool read_text_file(const char *path, char *buffer, size_t size) {
    if (size == 0) return false;
    FILE *file = fopen(path, "re");
    if (file == NULL) {
        buffer[0] = '\0';
        return false;
    }
    size_t read = fread(buffer, 1, size - 1, file);
    buffer[read] = '\0';
    fclose(file);
    trim_newline(buffer);
    return true;
}

static void read_property(const char *name, char *buffer, size_t size) {
    char value[PROP_VALUE_MAX] = {0};
    int len = __system_property_get(name, value);
    if (len <= 0) {
        buffer[0] = '\0';
        return;
    }
    snprintf(buffer, size, "%s", value);
}

static void read_first_property(const char *const *names, size_t count, char *buffer, size_t size) {
    if (size == 0) return;
    buffer[0] = '\0';
    for (size_t i = 0; i < count; ++i) {
        read_property(names[i], buffer, size);
        if (buffer[0] != '\0') return;
    }
}

static bool find_interrupt_line(char *buffer, size_t size) {
    FILE *file = fopen("/proc/interrupts", "re");
    if (file == NULL) {
        buffer[0] = '\0';
        return false;
    }
    char line[2048];
    while (fgets(line, sizeof(line), file) != NULL) {
        if (strstr(line, "nfc") != NULL || strstr(line, "nq-nci") != NULL ||
            strstr(line, "pn5") != NULL || strstr(line, "sn100") != NULL ||
            strstr(line, "sn220") != NULL) {
            snprintf(buffer, size, "%s", line);
            fclose(file);
            trim_newline(buffer);
            return true;
        }
    }
    fclose(file);
    buffer[0] = '\0';
    return false;
}

static void print_path_probe(const char *path) {
    struct stat st;
    int stat_result = stat(path, &st);
    printf("{");
    printf("\"path\":"); json_string(path);
    printf(",\"exists\":%s", stat_result == 0 ? "true" : "false");
    printf(",\"readable\":%s", access(path, R_OK) == 0 ? "true" : "false");
    printf(",\"writable\":%s", access(path, W_OK) == 0 ? "true" : "false");
    printf(",\"errno\":%d", stat_result == 0 ? 0 : errno);
    printf("}");
}

static int print_status(void) {
    char context[256] = {0};
    char enforcing[16] = {0};
    char initialized[PROP_VALUE_MAX] = {0};
    char firmware[PROP_VALUE_MAX] = {0};
    char chip_id[PROP_VALUE_MAX] = {0};
    char port[PROP_VALUE_MAX] = {0};
    char irq[2048] = {0};

    static const char *const firmware_properties[] = {
        "nfc.fw.ver",
        "vendor.qti.nfc.fwver",
        "ro.vendor.nfc.fwver",
    };
    static const char *const chip_properties[] = {
        "vendor.qti.nfc.chipid",
        "ro.vendor.nfc.chipid",
    };
    static const char *const port_properties[] = {
        "ro.nfc.port",
        "ro.vendor.nfc.port",
    };

    read_text_file("/proc/self/attr/current", context, sizeof(context));
    read_text_file("/sys/fs/selinux/enforce", enforcing, sizeof(enforcing));
    read_property("nfc.initialized", initialized, sizeof(initialized));
    read_first_property(firmware_properties, sizeof(firmware_properties) / sizeof(firmware_properties[0]), firmware, sizeof(firmware));
    read_first_property(chip_properties, sizeof(chip_properties) / sizeof(chip_properties[0]), chip_id, sizeof(chip_id));
    read_first_property(port_properties, sizeof(port_properties) / sizeof(port_properties[0]), port, sizeof(port));
    find_interrupt_line(irq, sizeof(irq));

    printf("{");
    printf("\"bridgeVersion\":"); json_string(BRIDGE_VERSION);
    printf(",\"uid\":%u,\"euid\":%u,\"gid\":%u", getuid(), geteuid(), getgid());
    printf(",\"selinuxContext\":"); json_string(context);
    printf(",\"selinuxEnforcing\":%s", strcmp(enforcing, "1") == 0 ? "true" : "false");
    printf(",\"nfcInitialized\":"); json_string(initialized);
    printf(",\"nfcFirmware\":"); json_string(firmware);
    printf(",\"nfcChipId\":"); json_string(chip_id);
    printf(",\"nfcPort\":"); json_string(port);
    printf(",\"nfcInterrupt\":"); json_string(irq);
    printf(",\"probes\":[");
    print_path_probe("/sys/fs/selinux/enforce");
    printf(","); print_path_probe("/proc/interrupts");
    printf(","); print_path_probe("/sys/class/nfc");
    /* Vendor-specific candidates are probes only; their absence is not an error. */
    printf(","); print_path_probe("/sys/class/nqx");
    printf(","); print_path_probe("/dev/nq-nci");
    printf(","); print_path_probe("/dev/pn544");
    printf(","); print_path_probe("/dev/st21nfc");
    printf(","); print_path_probe("/odm/etc/libnfc-nxp.conf");
    printf(","); print_path_probe("/vendor/etc/libnfc-nxp.conf");
    printf(","); print_path_probe("/vendor/etc/libnfc-hal-st.conf");
    printf(","); print_path_probe("/vendor/etc/libnfc-nci.conf");
    printf("]}");
    putchar('\n');
    return 0;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: nfc-root-bridge <status|version>\n");
        return 64;
    }
    if (strcmp(argv[1], "status") == 0) return print_status();
    if (strcmp(argv[1], "version") == 0) {
        puts(BRIDGE_VERSION);
        return 0;
    }
    fprintf(stderr, "unsupported command\n");
    return 64;
}
