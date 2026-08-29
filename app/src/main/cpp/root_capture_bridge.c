#include <errno.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

/*
 * Root-side companion for PCAPdroid's pcapd daemon.
 *
 * Why this is a separate executable instead of an in-process JNI socket:
 * on some rooted MIUI builds the Magisk root SELinux domain cannot traverse an app's private
 * /data/user/0 directory. Keeping pcapd + its filesystem UNIX socket + PCAP writer together in
 * /data/local/tmp avoids that cross-domain boundary while preserving pcapd's true UID filter.
 *
 * pcapd_hdr_t is copied from PCAPdroid's public pcapd.h (per its redistribution terms).
 */
typedef struct {
    struct timeval ts;
    unsigned int pkt_drops;
    uid_t uid;
    uint16_t len;
    uint16_t linktype;
    uint8_t flags;
    uint8_t ifid;
    uint8_t pad[2];
} __attribute__((packed)) pcapd_hdr_t;

typedef struct {
    uint32_t magic;
    uint16_t major;
    uint16_t minor;
    int32_t thiszone;
    uint32_t sigfigs;
    uint32_t snaplen;
    uint32_t network;
} __attribute__((packed)) pcap_header_t;

typedef struct {
    uint32_t ts_sec;
    uint32_t ts_usec;
    uint32_t incl_len;
    uint32_t orig_len;
} __attribute__((packed)) pcap_record_t;

static volatile sig_atomic_t g_running = 1;
static int g_server_fd = -1;
static int g_client_fd = -1;
static pid_t g_pcapd_pid = -1;

static void stop_handler(int sig) {
    (void)sig;
    g_running = 0;
    if (g_client_fd >= 0) shutdown(g_client_fd, SHUT_RDWR);
    if (g_server_fd >= 0) shutdown(g_server_fd, SHUT_RDWR);
}

static ssize_t read_full(int fd, void *buf, size_t size) {
    size_t done = 0;
    while (done < size && g_running) {
        ssize_t n = read(fd, (char *)buf + done, size - done);
        if (n == 0) return 0;
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        done += (size_t)n;
    }
    return (ssize_t)done;
}

static int launch_pcapd(const char *binary, int uid) {
    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        char uid_text[32];
        if (uid >= 0) {
            snprintf(uid_text, sizeof(uid_text), "%d", uid);
            execl(binary, binary, "-i", "@inet", "-u", uid_text, (char *)NULL);
        } else {
            execl(binary, binary, "-i", "@inet", (char *)NULL);
        }
        _exit(127);
    }
    g_pcapd_pid = pid;
    return 0;
}

int main(int argc, char **argv) {
    if (argc != 4) {
        fprintf(stderr, "usage: %s <pcapd> <output.pcap> <uid|-1>\n", argv[0]);
        return 64;
    }
    const char *pcapd = argv[1];
    const char *output = argv[2];
    int uid = atoi(argv[3]);

    signal(SIGTERM, stop_handler);
    signal(SIGINT, stop_handler);
    signal(SIGPIPE, SIG_IGN);

    char workdir[160];
    snprintf(workdir, sizeof(workdir), "/data/local/tmp/roottools-capture-%d", getpid());
    if (mkdir(workdir, 0700) != 0 && errno != EEXIST) {
        perror("mkdir workdir");
        return 65;
    }
    if (chdir(workdir) != 0) {
        perror("chdir workdir");
        return 66;
    }
    unlink("pcapsock");
    unlink("pcapd.pid");

    g_server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (g_server_fd < 0) { perror("socket"); return 67; }
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, "pcapsock", sizeof(addr.sun_path) - 1);
    if (bind(g_server_fd, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
        perror("bind pcapsock"); return 68;
    }
    if (listen(g_server_fd, 1) != 0) { perror("listen"); return 69; }
    if (launch_pcapd(pcapd, uid) != 0) { perror("fork pcapd"); return 70; }

    g_client_fd = accept(g_server_fd, NULL, NULL);
    if (g_client_fd < 0) {
        if (g_running) perror("accept");
        goto cleanup;
    }

    FILE *out = fopen(output, "wb");
    if (!out) { perror("open output"); goto cleanup; }
    pcap_header_t gh = {0xa1b2c3d4, 2, 4, 0, 0, 65535, 12}; /* DLT_RAW */
    if (fwrite(&gh, sizeof(gh), 1, out) != 1) { perror("write pcap header"); fclose(out); goto cleanup; }
    fflush(out);

    unsigned char *packet = malloc(65535);
    if (!packet) { fclose(out); goto cleanup; }
    while (g_running) {
        pcapd_hdr_t header;
        ssize_t got = read_full(g_client_fd, &header, sizeof(header));
        if (got != sizeof(header)) break;
        if (header.len == 0 || header.len > 65535) break;
        got = read_full(g_client_fd, packet, header.len);
        if (got != header.len) break;
        pcap_record_t record = {
            (uint32_t)header.ts.tv_sec,
            (uint32_t)header.ts.tv_usec,
            header.len,
            header.len,
        };
        if (fwrite(&record, sizeof(record), 1, out) != 1 ||
            fwrite(packet, header.len, 1, out) != 1) break;
        fflush(out);
    }
    free(packet);
    fclose(out);

cleanup:
    if (g_client_fd >= 0) close(g_client_fd);
    if (g_server_fd >= 0) close(g_server_fd);
    if (g_pcapd_pid > 0) {
        kill(g_pcapd_pid, SIGTERM);
        waitpid(g_pcapd_pid, NULL, 0);
    }
    unlink("pcapsock");
    unlink("pcapd.pid");
    chdir("/data/local/tmp");
    rmdir(workdir);
    return 0;
}
