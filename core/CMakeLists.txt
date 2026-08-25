cmake_minimum_required(VERSION 3.20)
project(rigweave_core VERSION 0.1.0 LANGUAGES CXX)

option(RIGWEAVE_CORE_BUILD_TESTS "Build standalone shared-core tests" ON)
option(RIGWEAVE_ENABLE_ASAN "Enable AddressSanitizer for host tests" OFF)
option(RIGWEAVE_ENABLE_UBSAN "Enable UndefinedBehaviorSanitizer for host tests" OFF)

set(RIGWEAVE_PORTABLE_SOURCES
    portable/src/adif.cpp
    portable/src/cat_parser.cpp
    portable/src/cty.cpp
    portable/src/dx_analysis.cpp
    portable/src/operator_intel.cpp
    portable/src/panadapter_dsp.cpp
    portable/src/protocol.cpp
    portable/src/spot.cpp
    portable/src/sync_queue.cpp
    portable/src/wsjtx_protocol.cpp
)
file(GLOB RIGWEAVE_SGP4_SOURCES CONFIGURE_DEPENDS third_party/sgp4/libsgp4/*.cc)

add_library(rigweave_core STATIC src/core.cpp src/features.cpp src/satellite.cpp src/propagation/p533.cpp ${RIGWEAVE_PORTABLE_SOURCES} ${RIGWEAVE_SGP4_SOURCES})
target_include_directories(rigweave_core PUBLIC include portable/include PRIVATE third_party/sgp4/libsgp4)
target_compile_features(rigweave_core PUBLIC cxx_std_17)
set_target_properties(rigweave_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(MSVC)
    target_compile_options(rigweave_core PRIVATE /W4 /WX /permissive-)
else()
    target_compile_options(rigweave_core PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()

if(RIGWEAVE_CORE_BUILD_TESTS)
    enable_testing()
    add_executable(rigweave_core_tests test/core_tests.cpp)
    target_link_libraries(rigweave_core_tests PRIVATE rigweave_core)
    target_include_directories(rigweave_core_tests PRIVATE third_party/sgp4/libsgp4)
    if(MSVC)
        target_compile_options(rigweave_core_tests PRIVATE /W4 /WX /permissive-)
    else()
        target_compile_options(rigweave_core_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
    endif()
    add_test(NAME rigweave_core_tests COMMAND rigweave_core_tests)

    add_executable(rigweave_propagation_adapter_tests test/propagation_adapter_tests.cpp)
    target_link_libraries(rigweave_propagation_adapter_tests PRIVATE rigweave_core)
    if(MSVC)
        target_compile_options(rigweave_propagation_adapter_tests PRIVATE /W4 /WX /permissive-)
    else()
        target_compile_options(rigweave_propagation_adapter_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
    endif()
    add_test(NAME rigweave_propagation_adapter_tests COMMAND rigweave_propagation_adapter_tests)

    add_executable(rigweave_lifecycle_stress_tests test/lifecycle_stress_tests.cpp)
    target_link_libraries(rigweave_lifecycle_stress_tests PRIVATE rigweave_core)
    if(MSVC)
        target_compile_options(rigweave_lifecycle_stress_tests PRIVATE /W4 /WX /permissive-)
    else()
        target_compile_options(rigweave_lifecycle_stress_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
    endif()
    add_test(NAME rigweave_lifecycle_stress_tests COMMAND rigweave_lifecycle_stress_tests)

    set(RIGWEAVE_SANITIZER_FLAGS)
    if(RIGWEAVE_ENABLE_ASAN)
        list(APPEND RIGWEAVE_SANITIZER_FLAGS -fsanitize=address)
    endif()
    if(RIGWEAVE_ENABLE_UBSAN)
        list(APPEND RIGWEAVE_SANITIZER_FLAGS -fsanitize=undefined)
    endif()
    if(RIGWEAVE_SANITIZER_FLAGS)
        if(NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
            message(FATAL_ERROR "RigWeave host sanitizers require Clang or GCC")
        endif()
        foreach(target rigweave_core rigweave_core_tests rigweave_propagation_adapter_tests rigweave_lifecycle_stress_tests)
            target_compile_options(${target} PRIVATE ${RIGWEAVE_SANITIZER_FLAGS} -fno-omit-frame-pointer)
            target_link_options(${target} PRIVATE ${RIGWEAVE_SANITIZER_FLAGS})
        endforeach()
    endif()
endif()
