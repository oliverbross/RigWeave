cmake_minimum_required(VERSION 3.20)
project(rigweave_core VERSION 0.1.0 LANGUAGES CXX)

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
target_compile_options(rigweave_core PRIVATE -Wall -Wextra -Wpedantic -Werror)

enable_testing()
add_executable(rigweave_core_tests test/core_tests.cpp)
target_link_libraries(rigweave_core_tests PRIVATE rigweave_core)
target_include_directories(rigweave_core_tests PRIVATE third_party/sgp4/libsgp4)
target_compile_options(rigweave_core_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
add_test(NAME rigweave_core_tests COMMAND rigweave_core_tests)

add_executable(rigweave_propagation_adapter_tests test/propagation_adapter_tests.cpp)
target_link_libraries(rigweave_propagation_adapter_tests PRIVATE rigweave_core)
target_compile_options(rigweave_propagation_adapter_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
add_test(NAME rigweave_propagation_adapter_tests COMMAND rigweave_propagation_adapter_tests)
