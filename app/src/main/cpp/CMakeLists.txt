cmake_minimum_required(VERSION 3.22.1)
project(nettools_capture C)

# Android Gradle packages native executables when they are named like a shared library.
# This executable is staged into /data/local/tmp and launched through Magisk `su`.
add_executable(libnettools_pcap_bridge.so root_capture_bridge.c)
