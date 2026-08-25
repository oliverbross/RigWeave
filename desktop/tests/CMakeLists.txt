set(RIGWEAVE_TEST_LIBS rigweave_desktop_services Qt6::Test)

qt_add_executable(desktop_data_contract_tests unit/desktop_data_contract_tests.cpp)
target_link_libraries(desktop_data_contract_tests PRIVATE ${RIGWEAVE_TEST_LIBS})
target_compile_definitions(desktop_data_contract_tests PRIVATE
    RIGWEAVE_SHARED_FIXTURES_DIR="${CMAKE_CURRENT_SOURCE_DIR}/../../fixtures/platform")
rigweave_target_warnings(desktop_data_contract_tests)
add_test(NAME desktop_data_contract_tests COMMAND desktop_data_contract_tests)

qt_add_executable(desktop_network_contract_tests integration/desktop_network_contract_tests.cpp)
target_link_libraries(desktop_network_contract_tests PRIVATE ${RIGWEAVE_TEST_LIBS})
rigweave_target_warnings(desktop_network_contract_tests)
add_test(NAME desktop_network_contract_tests COMMAND desktop_network_contract_tests)

qt_add_executable(desktop_platform_safety_tests unit/desktop_platform_safety_tests.cpp)
target_link_libraries(desktop_platform_safety_tests PRIVATE ${RIGWEAVE_TEST_LIBS})
rigweave_target_warnings(desktop_platform_safety_tests)
add_test(NAME desktop_platform_safety_tests COMMAND desktop_platform_safety_tests)

add_test(NAME desktop_qml_tests
    COMMAND Qt6::qmltestrunner -input ${CMAKE_CURRENT_SOURCE_DIR}/qml
            -import ${CMAKE_CURRENT_SOURCE_DIR}/../qml/RigWeave)
set_tests_properties(desktop_qml_tests PROPERTIES ENVIRONMENT "QT_QPA_PLATFORM=offscreen")
