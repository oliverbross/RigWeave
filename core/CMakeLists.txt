cmake_minimum_required(VERSION 3.20)
project(rigweave_core VERSION 0.1.0 LANGUAGES CXX)

add_library(rigweave_core STATIC src/core.cpp)
target_include_directories(rigweave_core PUBLIC include)
target_compile_features(rigweave_core PUBLIC cxx_std_17)
target_compile_options(rigweave_core PRIVATE -Wall -Wextra -Wpedantic -Werror)

enable_testing()
add_executable(rigweave_core_tests test/core_tests.cpp)
target_link_libraries(rigweave_core_tests PRIVATE rigweave_core)
target_compile_options(rigweave_core_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
add_test(NAME rigweave_core_tests COMMAND rigweave_core_tests)
