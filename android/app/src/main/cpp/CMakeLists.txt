cmake_minimum_required(VERSION 3.22.1)
project(rigweave_jni LANGUAGES CXX)

set(CORE_ROOT ../../../../../core)
file(GLOB RIGWEAVE_SGP4_SOURCES CONFIGURE_DEPENDS ${CORE_ROOT}/third_party/sgp4/libsgp4/*.cc)
get_filename_component(FLEX_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../rust/rigweave-flex" ABSOLUTE)
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(FLEX_RUST_TARGET armv7-linux-androideabi)
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    set(FLEX_RUST_TARGET aarch64-linux-android)
elseif(ANDROID_ABI STREQUAL "x86")
    set(FLEX_RUST_TARGET i686-linux-android)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(FLEX_RUST_TARGET x86_64-linux-android)
else()
    message(FATAL_ERROR "Unsupported Android ABI for rigweave-flex: ${ANDROID_ABI}")
endif()
add_library(rigweave_flex STATIC IMPORTED)
set_target_properties(rigweave_flex PROPERTIES IMPORTED_LOCATION
    ${FLEX_ROOT}/target/${FLEX_RUST_TARGET}/release/librigweave_flex.a)
add_library(rigweave SHARED
    native_bridge.cpp
    ${CORE_ROOT}/src/core.cpp
    ${CORE_ROOT}/src/features.cpp
    ${CORE_ROOT}/src/satellite.cpp
    ${CORE_ROOT}/portable/src/adif.cpp
    ${CORE_ROOT}/portable/src/cat_parser.cpp
    ${CORE_ROOT}/portable/src/cty.cpp
    ${CORE_ROOT}/portable/src/dx_analysis.cpp
    ${CORE_ROOT}/portable/src/operator_intel.cpp
    ${CORE_ROOT}/portable/src/panadapter_dsp.cpp
    ${CORE_ROOT}/portable/src/protocol.cpp
    ${CORE_ROOT}/portable/src/spot.cpp
    ${CORE_ROOT}/portable/src/sync_queue.cpp
    ${CORE_ROOT}/portable/src/wsjtx_protocol.cpp
    ${RIGWEAVE_SGP4_SOURCES})
target_include_directories(rigweave PRIVATE ${CORE_ROOT}/include ${CORE_ROOT}/portable/include ${CORE_ROOT}/third_party/sgp4/libsgp4 ${FLEX_ROOT}/include)
target_link_libraries(rigweave PRIVATE rigweave_flex log)
target_compile_features(rigweave PRIVATE cxx_std_17)
target_compile_options(rigweave PRIVATE -Wall -Wextra -Wpedantic)
