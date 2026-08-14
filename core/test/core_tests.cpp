#include "rigweave/core.h"

#include <cassert>
#include <cstring>
#include <iostream>
#include <string>

int main() {
    rw_context *context = rw_context_create();
    assert(context != nullptr);
    auto state = rw_context_state(context);
    assert(state.connected == 0 && state.transmitting == 0);
    assert(state.vfo_a_hz == 0 && std::string(state.model) == "UNIDENTIFIED");

    const char *frames = "IDKX3;FA00014074000;MD2;IF00014075000ABCDEFGHI;TQ0;";
    assert(rw_context_feed(context, frames, std::strlen(frames)) == 5);
    state = rw_context_state(context);
    assert(state.connected == 1);
    assert(std::string(state.model) == "KX3" && std::string(state.mode) == "USB");
    assert(state.vfo_a_hz == 14075000 && state.transmitting == 0);

    std::string oversized(600, 'A');
    assert(rw_context_feed(context, oversized.data(), oversized.size()) == 0);
    assert(rw_startup_command_count() == 0);
    for (const char *query : {"ID;", "FA;", "MD;", "IF;", "TQ;"})
        assert(rw_classify_command(query) == RW_COMMAND_READ_ONLY);
    for (const char *unsafe : {"TX;", "RX;", "SWT44;", "SWH28;", "KY CQ;"})
        assert(rw_classify_command(unsafe) == RW_COMMAND_TRANSMIT);
    assert(rw_classify_command("FA00014074000;") == RW_COMMAND_MUTATION);

    char first[32]{};
    char second[32]{};
    assert(rw_qso_identity(first, sizeof(first), "vk8abc", "2026-08-14T01:02:03Z", 14074000, "USB"));
    assert(rw_qso_identity(second, sizeof(second), " VK8ABC ", "2026-08-14T01:02:03Z", 14074000, "usb"));
    assert(std::string(first) == second);

    char adif[512]{};
    const int length = rw_adif_serialize(adif, sizeof(adif), first, "VK8ABC", "20260814", "010203",
                                         14074000, "USB", "59", "57");
    assert(length > 0);
    assert(std::string(adif).find("<CALL:6>VK8ABC") != std::string::npos);
    assert(std::string(adif).find("<APP_RIGWEAVE_UUID:19>rw-") != std::string::npos);
    assert(std::string(adif).find("<EOR>") != std::string::npos);

    assert(std::string(rw_core_version()) == "0.1.0");
    rw_context_destroy(context);
    std::cout << "RigWeave core tests passed\n";
}
