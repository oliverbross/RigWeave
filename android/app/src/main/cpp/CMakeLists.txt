cmake_minimum_required(VERSION 3.22.1)
project(rigweave_jni LANGUAGES CXX)

set(CORE_ROOT ../../../../../core)
add_library(rigweave SHARED
    native_bridge.cpp
    ${CORE_ROOT}/src/core.cpp
    ${CORE_ROOT}/src/features.cpp
    ${CORE_ROOT}/portable/src/adif.cpp
    ${CORE_ROOT}/portable/src/cat_parser.cpp
    ${CORE_ROOT}/portable/src/cty.cpp
    ${CORE_ROOT}/portable/src/dx_analysis.cpp
    ${CORE_ROOT}/portable/src/operator_intel.cpp
    ${CORE_ROOT}/portable/src/panadapter_dsp.cpp
    ${CORE_ROOT}/portable/src/protocol.cpp
    ${CORE_ROOT}/portable/src/spot.cpp
    ${CORE_ROOT}/portable/src/sync_queue.cpp
    ${CORE_ROOT}/portable/src/wsjtx_protocol.cpp)
target_include_directories(rigweave PRIVATE ${CORE_ROOT}/include ${CORE_ROOT}/portable/include)
target_compile_features(rigweave PRIVATE cxx_std_17)
target_compile_options(rigweave PRIVATE -Wall -Wextra -Wpedantic)
