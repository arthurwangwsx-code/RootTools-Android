#include <jni.h>

#include <dlfcn.h>
#include <link.h>
#include <sys/types.h>
#include <unistd.h>

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <set>
#include <sstream>
#include <string>
#include <vector>

namespace {

struct MapEntry {
    uintptr_t start = 0;
    uintptr_t end = 0;
    unsigned long file_offset = 0;
    std::string permissions;
    std::string path;
    std::string raw;
};

__attribute__((noinline)) int roottools_integrity_anchor(int value) {
    return (value * 31) ^ 0x45A17;
}

std::string trim(const std::string &value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return "";
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1);
}

std::string lower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

std::vector<MapEntry> read_maps() {
    std::vector<MapEntry> result;
    std::ifstream input("/proc/self/maps");
    std::string line;
    while (std::getline(input, line)) {
        unsigned long start = 0;
        unsigned long end = 0;
        unsigned long offset = 0;
        char perms[8] = {};
        char device[32] = {};
        unsigned long inode = 0;
        int consumed = 0;
        const int parsed = std::sscanf(
            line.c_str(),
            "%lx-%lx %7s %lx %31s %lu %n",
            &start,
            &end,
            perms,
            &offset,
            device,
            &inode,
            &consumed);
        if (parsed < 6) continue;
        MapEntry entry;
        entry.start = static_cast<uintptr_t>(start);
        entry.end = static_cast<uintptr_t>(end);
        entry.file_offset = offset;
        entry.permissions = perms;
        entry.path = consumed > 0 && consumed < static_cast<int>(line.size())
                         ? trim(line.substr(static_cast<size_t>(consumed)))
                         : "";
        entry.raw = line;
        result.push_back(std::move(entry));
    }
    return result;
}

int read_tracer_pid() {
    std::ifstream input("/proc/self/status");
    std::string line;
    while (std::getline(input, line)) {
        if (line.rfind("TracerPid:", 0) == 0) {
            return std::atoi(line.substr(std::strlen("TracerPid:")).c_str());
        }
    }
    return -1;
}

int loaded_elf_callback(dl_phdr_info *info, size_t, void *data) {
    auto *count = static_cast<int *>(data);
    if (info != nullptr && info->dlpi_phnum > 0) (*count)++;
    return 0;
}

bool compare_anchor_with_file(
    const std::vector<MapEntry> &maps,
    std::string *library_path,
    int *segments,
    int *mismatches) {
    Dl_info dl_info{};
    if (dladdr(reinterpret_cast<void *>(&roottools_integrity_anchor), &dl_info) == 0 || dl_info.dli_fname == nullptr) {
        return false;
    }
    *library_path = dl_info.dli_fname;
    const uintptr_t address = reinterpret_cast<uintptr_t>(&roottools_integrity_anchor);
    const auto entry = std::find_if(maps.begin(), maps.end(), [&](const MapEntry &candidate) {
        return address >= candidate.start && address < candidate.end &&
               candidate.permissions.find('x') != std::string::npos;
    });
    if (entry == maps.end() || entry->path.empty() || entry->path.front() == '[') return false;
    (*segments)++;

    constexpr size_t kProbeBytes = 96;
    const size_t available = static_cast<size_t>(entry->end - address);
    const size_t probe_size = std::min(kProbeBytes, available);
    if (probe_size < 16) return false;

    const unsigned long file_offset = entry->file_offset + static_cast<unsigned long>(address - entry->start);
    std::ifstream file(entry->path, std::ios::binary);
    if (!file) return false;
    file.seekg(static_cast<std::streamoff>(file_offset), std::ios::beg);
    std::vector<unsigned char> disk(probe_size);
    file.read(reinterpret_cast<char *>(disk.data()), static_cast<std::streamsize>(disk.size()));
    if (static_cast<size_t>(file.gcount()) != probe_size) return false;

    const auto *memory = reinterpret_cast<const unsigned char *>(address);
    if (std::memcmp(memory, disk.data(), probe_size) != 0) (*mismatches)++;
    return true;
}

std::string sanitize_line_value(std::string value) {
    std::replace(value.begin(), value.end(), '\n', ' ');
    std::replace(value.begin(), value.end(), '\r', ' ');
    return value;
}

std::string collect_summary() {
    const auto maps = read_maps();
    const std::set<std::string> marker_names = {
        "frida", "gadget", "substrate", "xposed", "lsposed", "riru", "zygisk", "edxposed"
    };
    std::set<std::string> markers;
    int writable_executable = 0;
    int deleted_executable = 0;
    for (const auto &entry : maps) {
        const bool executable = entry.permissions.find('x') != std::string::npos;
        const bool writable = entry.permissions.find('w') != std::string::npos;
        if (executable && writable) writable_executable++;
        if (executable && entry.path.find("(deleted)") != std::string::npos) deleted_executable++;
        const auto normalized = lower(entry.path);
        for (const auto &marker : marker_names) {
            if (!normalized.empty() && normalized.find(marker) != std::string::npos) markers.insert(marker);
        }
    }

    int elf_count = 0;
    dl_iterate_phdr(loaded_elf_callback, &elf_count);
    std::string self_library_path;
    int self_segments = 0;
    int self_mismatches = 0;
    compare_anchor_with_file(maps, &self_library_path, &self_segments, &self_mismatches);

    std::ostringstream output;
    output << "available=1\n";
    output << "tracerPid=" << read_tracer_pid() << "\n";
    output << "mappingCount=" << maps.size() << "\n";
    output << "writableExecutableCount=" << writable_executable << "\n";
    output << "deletedExecutableCount=" << deleted_executable << "\n";
    output << "strongMarkers=";
    bool first = true;
    for (const auto &marker : markers) {
        if (!first) output << ',';
        output << marker;
        first = false;
    }
    output << "\n";
    output << "loadedElfCount=" << elf_count << "\n";
    output << "selfExecutableSegments=" << self_segments << "\n";
    output << "selfExecutableSegmentMismatches=" << self_mismatches << "\n";
    output << "selfLibraryPath=" << sanitize_line_value(self_library_path) << "\n";
    return output.str();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_arthur_roottools_feature_integrity_nativebridge_NativeIntegrityBridge_nativeSummary(
    JNIEnv *env,
    jobject) {
    try {
        const std::string result = collect_summary();
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception &error) {
        const std::string result = std::string("available=0\nerror=") + sanitize_line_value(error.what());
        return env->NewStringUTF(result.c_str());
    } catch (...) {
        return env->NewStringUTF("available=0\nerror=unknown native error");
    }
}
