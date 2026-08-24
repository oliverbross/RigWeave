cmake_minimum_required(VERSION 3.28)
project(RigWeaveDesktop VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)
list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

option(RIGWEAVE_BUILD_TESTS "Build focused desktop tests" ON)
option(RIGWEAVE_REQUIRE_HAMLIB "Fail configuration when the pinned Hamlib build is unavailable" OFF)

find_package(Qt6 6.11.2 EXACT REQUIRED COMPONENTS
    Core Gui Quick QuickControls2 Qml Network Sql SerialPort Multimedia WebSockets
    Concurrent Svg Positioning Location Test QuickTest)

if(RIGWEAVE_REQUIRE_HAMLIB)
    find_package(RigWeaveHamlib REQUIRED)
else()
    find_package(RigWeaveHamlib QUIET)
endif()

add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../core" "${CMAKE_CURRENT_BINARY_DIR}/core" EXCLUDE_FROM_ALL)
include(RigWeaveWarnings)

set(RIGWEAVE_DESKTOP_SERVICE_SOURCES
    src/app/DesktopApplication.cpp
    src/core/DesktopModels.cpp
    src/data/QsoDatabase.cpp
    src/data/AdifService.cpp
    src/network/WavelogSync.cpp
    src/network/ClusterController.cpp
    src/platform/DesktopPlatform.cpp
    src/radio/DesktopRadioController.cpp
    src/rotator/DesktopRotatorController.cpp
    src/panadapter/DesktopPanadapter.cpp
)
file(GLOB RIGWEAVE_DESKTOP_SERVICE_HEADERS CONFIGURE_DEPENDS
    "include/rigweave/desktop/*.hpp")

add_library(rigweave_desktop_services STATIC
    ${RIGWEAVE_DESKTOP_SERVICE_SOURCES}
    ${RIGWEAVE_DESKTOP_SERVICE_HEADERS})
target_include_directories(rigweave_desktop_services PUBLIC include)
target_link_libraries(rigweave_desktop_services PUBLIC
    rigweave_core Qt6::Core Qt6::Gui Qt6::Quick Qt6::QuickControls2 Qt6::Qml
    Qt6::Network Qt6::Sql Qt6::SerialPort Qt6::Multimedia Qt6::WebSockets
    Qt6::Concurrent Qt6::Svg Qt6::Positioning Qt6::Location)
if(RigWeaveHamlib_FOUND)
    target_link_libraries(rigweave_desktop_services PUBLIC RigWeave::Hamlib)
    target_compile_definitions(rigweave_desktop_services PRIVATE RIGWEAVE_HAVE_HAMLIB=1)
endif()
if(WIN32)
    target_link_libraries(rigweave_desktop_services PUBLIC advapi32)
endif()
rigweave_target_warnings(rigweave_desktop_services)
target_compile_definitions(rigweave_desktop_services PRIVATE
    RIGWEAVE_BUILD_SHA="$ENV{GITHUB_SHA}"
    RIGWEAVE_DESKTOP_SCHEMA=16)

qt_add_executable(RigWeaveDesktop WIN32 MACOSX_BUNDLE src/app/main.cpp)
target_link_libraries(RigWeaveDesktop PRIVATE rigweave_desktop_services)
rigweave_target_warnings(RigWeaveDesktop)
if(APPLE AND CMAKE_CXX_COMPILER_ID MATCHES "Clang")
    # Qt 6.11's generated QML cache loader uses an intentionally empty
    # variadic macro invocation; keep warnings-as-errors for owned sources.
    target_compile_options(RigWeaveDesktop PRIVATE -Wno-variadic-macro-arguments-omitted)
endif()

file(GLOB_RECURSE RIGWEAVE_QML_FILES CONFIGURE_DEPENDS "qml/*.qml")
foreach(qml_file IN LISTS RIGWEAVE_QML_FILES)
    if(qml_file MATCHES "/qml/RigWeave/App/Main\\.qml$")
        set_source_files_properties(${qml_file} PROPERTIES QT_RESOURCE_ALIAS "Main.qml")
    else()
        file(RELATIVE_PATH qml_alias "${CMAKE_CURRENT_SOURCE_DIR}/qml/RigWeave" "${qml_file}")
        set_source_files_properties(${qml_file} PROPERTIES QT_RESOURCE_ALIAS "${qml_alias}")
    endif()
endforeach()
qt_add_qml_module(RigWeaveDesktop
    URI RigWeave.App
    VERSION 1.0
    QML_FILES ${RIGWEAVE_QML_FILES}
    RESOURCES resources/default-band-plan.json resources/THIRD_PARTY_NOTICES.txt)

install(TARGETS RigWeaveDesktop
    BUNDLE DESTINATION .
    RUNTIME DESTINATION .)

if(WIN32)
    qt_generate_deploy_qml_app_script(
        TARGET RigWeaveDesktop
        OUTPUT_SCRIPT deploy_script
        NO_UNSUPPORTED_PLATFORM_ERROR)
    install(SCRIPT ${deploy_script})
endif()

if(RIGWEAVE_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
    # The embedded core registers assert-based standalone tests unconditionally;
    # Release defines NDEBUG, so its own project runs those in a Debug gate.
    # This superproject owns and runs the focused desktop test matrix below.
    set_tests_properties(rigweave_core_tests rigweave_propagation_adapter_tests
        PROPERTIES DISABLED TRUE)
endif()

set(CPACK_PACKAGE_NAME "RigWeave")
set(CPACK_PACKAGE_VENDOR "RigWeave")
set(CPACK_PACKAGE_VERSION "0.1.0-alpha.1")
set(CPACK_PACKAGE_INSTALL_DIRECTORY "RigWeave Alpha")
set(CPACK_PACKAGE_FILE_NAME "RigWeave-Windows-x64-setup")
set(CPACK_RESOURCE_FILE_LICENSE "${CMAKE_CURRENT_SOURCE_DIR}/../COPYING")
set(CPACK_NSIS_DISPLAY_NAME "RigWeave Windows Desktop Alpha")
set(CPACK_NSIS_PACKAGE_NAME "RigWeave Windows Desktop Alpha")
set(CPACK_NSIS_INSTALL_ROOT "$LOCALAPPDATA")
set(CPACK_NSIS_ENABLE_UNINSTALL_BEFORE_INSTALL ON)
set(CPACK_NSIS_MODIFY_PATH OFF)
set(CPACK_NSIS_MUI_FINISHPAGE_RUN "RigWeaveDesktop.exe")
set(CPACK_NSIS_CREATE_ICONS_EXTRA
    "CreateShortCut '$SMPROGRAMS\\RigWeave Windows Desktop Alpha.lnk' '$INSTDIR\\RigWeaveDesktop.exe'")
set(CPACK_NSIS_DELETE_ICONS_EXTRA
    "Delete '$SMPROGRAMS\\RigWeave Windows Desktop Alpha.lnk'")
set(CPACK_GENERATOR "NSIS")
include(CPack)
