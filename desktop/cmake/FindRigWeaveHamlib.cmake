set(_rigweave_hamlib_hints "${RIGWEAVE_HAMLIB_ROOT}" "$ENV{RIGWEAVE_HAMLIB_ROOT}")
find_path(RigWeaveHamlib_INCLUDE_DIR hamlib/rig.h HINTS ${_rigweave_hamlib_hints} PATH_SUFFIXES include)
find_library(RigWeaveHamlib_LIBRARY NAMES hamlib libhamlib HINTS ${_rigweave_hamlib_hints} PATH_SUFFIXES lib lib64)
include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(RigWeaveHamlib REQUIRED_VARS RigWeaveHamlib_INCLUDE_DIR RigWeaveHamlib_LIBRARY)
if(RigWeaveHamlib_FOUND AND NOT TARGET RigWeave::Hamlib)
    add_library(RigWeave::Hamlib UNKNOWN IMPORTED)
    set_target_properties(RigWeave::Hamlib PROPERTIES
        IMPORTED_LOCATION "${RigWeaveHamlib_LIBRARY}"
        INTERFACE_INCLUDE_DIRECTORIES "${RigWeaveHamlib_INCLUDE_DIR}")
    if(WIN32)
        set_property(TARGET RigWeave::Hamlib APPEND PROPERTY INTERFACE_LINK_LIBRARIES ws2_32 winmm setupapi version)
    endif()
endif()
