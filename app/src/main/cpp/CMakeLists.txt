cmake_minimum_required(VERSION 3.22.1)

project(roottools_native LANGUAGES C CXX)

add_library(roottools_integrity SHARED integrity_native.cpp)

target_compile_features(roottools_integrity PRIVATE cxx_std_17)
target_compile_options(roottools_integrity PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(roottools_integrity PRIVATE log dl)

# PCAPdroid's pcapd uses a filesystem UNIX socket. This root-side executable keeps the daemon,
# socket and PCAP writer in one SELinux domain and is staged through the typed capture backend.
add_executable(libroottools_pcap_bridge.so root_capture_bridge.c)
target_compile_options(libroottools_pcap_bridge.so PRIVATE -Wall -Wextra -Werror=return-type)
