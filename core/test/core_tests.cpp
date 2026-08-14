#include "rigweave/core.h"

#include <cassert>
#include <array>
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

    rw_feature_context *features = rw_feature_context_create();
    assert(features != nullptr);
    assert(rw_feature_load_cty_text(features,
        "Japan: 25: 45: AS: 36.00: -138.00: -9.0: JA: JA;\n") == 1);
    assert(rw_feature_set_watchlist(features, "JA1XYZ VK9XY") == 1);
    assert(rw_feature_set_solar(features, 145.0F, 7.0F, 2.0F, 1720000000) == 1);
    assert(rw_feature_ingest_cluster_line(features,
        "DX de VK3ABC:  14074.0  JA1XYZ  FT8 -10 dB", 1720000000) == 1);
    char dx_json[8192]{};
    assert(rw_feature_dx_snapshot_json(features, dx_json, sizeof(dx_json), 1720000001) > 0);
    assert(std::string(dx_json).find("\"callsign\":\"JA1XYZ\"") != std::string::npos);
    assert(std::string(dx_json).find("\"watchlisted\":true") != std::string::npos);

    assert(rw_feature_add_worked_qso(features, "JA1XYZ", "Japan", "20m", "FT8", "", 1719999900, 0) == 1);
    char worked_json[1024]{};
    assert(rw_feature_worked_json(features, worked_json, sizeof(worked_json),
        "JA1XYZ", "Japan", "20m", "FT8", "", 1720000001) > 0);
    assert(std::string(worked_json).find("\"callWorked\":true") != std::string::npos);

    char propagation[2048]{};
    assert(rw_feature_propagation_json(propagation, sizeof(propagation),
        "QF56OC", "PM95VR", "20m", 1720000000, 155.0F, 2.0F, 1720000000, 20, 12) > 0);
    assert(std::string(propagation).find("\"valid\":true") != std::string::npos);

    std::array<uint8_t, 4096> pcm{};
    assert(rw_panadapter_push_pcm(features, pcm.data(), pcm.size(), 2, 2, 16) == 1);
    std::array<uint8_t, 1024> bins{};
    assert(rw_panadapter_copy_bins(features, bins.data(), bins.size()) == bins.size());

    char normalized_url[128]{};
    assert(rw_wavelog_normalize_url(normalized_url, sizeof(normalized_url), "htps://log.example/") > 0);
    assert(std::string(normalized_url) == "https://log.example");
    assert(rw_sync_action(200, 0, 0) == 0);
    assert(rw_sync_action(401, 0, 0) == 2);
    char wsjtx[256]{};
    const std::array<uint8_t, 4> invalid_wsjtx{};
    assert(rw_wsjtx_parse_json(wsjtx, sizeof(wsjtx), invalid_wsjtx.data(), invalid_wsjtx.size()) > 0);
    assert(std::string(wsjtx).find("\"valid\":false") != std::string::npos);
    rw_feature_context_destroy(features);

    assert(std::string(rw_core_version()) == "0.1.0");
    rw_context_destroy(context);
    std::cout << "RigWeave core tests passed\n";
}
