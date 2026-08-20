cmake_minimum_required(VERSION 3.22.1)

project(roottools_integrity LANGUAGES CXX)

add_library(roottools_integrity SHARED integrity_native.cpp)

target_compile_features(roottools_integrity PRIVATE cxx_std_17)
target_compile_options(roottools_integrity PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(roottools_integrity PRIVATE log dl)
