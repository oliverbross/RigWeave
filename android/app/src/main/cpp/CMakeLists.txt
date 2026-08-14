cmake_minimum_required(VERSION 3.22.1)
project(rigweave_jni LANGUAGES CXX)

add_library(rigweave SHARED native_bridge.cpp ../../../../../core/src/core.cpp)
target_include_directories(rigweave PRIVATE ../../../../../core/include)
target_compile_features(rigweave PRIVATE cxx_std_17)
target_compile_options(rigweave PRIVATE -Wall -Wextra -Wpedantic)
