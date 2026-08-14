#include "rigweave/core.h"

#include <algorithm>
#include <cassert>
#include <array>
#include <cmath>
#include <cstring>
#include <iostream>
#include <string>

int main() {
    rw_context *context = rw_context_create();
    assert(context != nullptr);
    auto state = rw_context_state(context);
    assert(state.connected == 0 && state.transmitting == 0);
    assert(state.vfo_a_hz == 0 && std::string(state.model) == "UNIDENTIFIED");

    const char *frames = "ID017;K30;OM A-F-------02;FA00014074000;MD2;IF00014075000ABCDEFGHI;TQ0;";
    assert(rw_context_feed(context, frames, std::strlen(frames)) == 7);
    state = rw_context_state(context);
    assert(state.connected == 1);
    assert(std::string(state.model) == "KX3" && std::string(state.mode) == "USB");
    assert(state.vfo_a_hz == 14075000 && state.transmitting == 0);

    rw_context *kx2_context = rw_context_create();
    const char *kx2_identity = "ID017;K30;OM A-F-------01;";
    assert(rw_context_feed(kx2_context, kx2_identity, std::strlen(kx2_identity)) == 3);
    assert(std::string(rw_context_state(kx2_context).model) == "KX2");
    rw_context_destroy(kx2_context);

    const char *instrument = "FB00007030000;SM18;SW14;PO37;AG190;RG210;BW0270;PC010;PA1;RA01;RT1;XT0;FR0;FT1;";
    assert(rw_context_feed(context, instrument, std::strlen(instrument)) == 14);
    state = rw_context_state(context);
    assert(state.vfo_b_hz == 7030000 && state.meter == 18);
    assert(state.swr_tenths == 14 && state.rf_output_tenths == 37);
    assert(state.af_gain == 190 && state.rf_gain == 210 && state.bandwidth_hz == 270);
    assert(state.power_w == 10 && state.preamp == 1 && state.attenuator == 1);
    assert(state.rit == 1 && state.xit == 0 && state.split == 1);

    std::string oversized(600, 'A');
    assert(rw_context_feed(context, oversized.data(), oversized.size()) == 0);
    assert(rw_startup_command_count() == 0);
    for (const char *query : {"K3;", "OM;", "ID;", "FA;", "MD;", "IF;", "TQ;"})
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
    assert(std::string(dx_json).find("\"bandTimeline\":[") != std::string::npos);
    assert(std::string(dx_json).find("\"worldGrid\":[") != std::string::npos);
    assert(std::string(dx_json).find("\"liveSpots\":[") != std::string::npos);

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
    std::array<float, 1024> db_bins{};
    assert(rw_panadapter_copy_db_bins(features, db_bins.data(), db_bins.size()) == db_bins.size());
    assert(std::all_of(db_bins.begin(), db_bins.end(), [](float value) {
        return std::isfinite(value) && value >= -140.0F && value <= 6.0F;
    }));

    std::array<int16_t, 2048> iq_tone{};
    constexpr float tone_amplitude = 0.5F;
    constexpr std::size_t tone_bin = 96;
    for (std::size_t frame = 0; frame < 1024; ++frame) {
        const float phase = 2.0F * 3.14159265358979323846F *
                            static_cast<float>(tone_bin * frame) / 1024.0F;
        iq_tone[frame * 2] = static_cast<int16_t>(std::cos(phase) * tone_amplitude * 32767.0F);
        iq_tone[frame * 2 + 1] = static_cast<int16_t>(std::sin(phase) * tone_amplitude * 32767.0F);
    }
    for (int frame = 0; frame < 4; ++frame) {
        assert(rw_panadapter_push_pcm(features, reinterpret_cast<const uint8_t *>(iq_tone.data()),
                                     iq_tone.size() * sizeof(int16_t), 2, 2, 16) == 1);
    }
    rw_panadapter_copy_db_bins(features, db_bins.data(), db_bins.size());
    const auto peak = static_cast<std::size_t>(std::distance(db_bins.begin(),
        std::max_element(db_bins.begin(), db_bins.end())));
    assert(peak == 512 + tone_bin);
    assert(db_bins[512 + tone_bin] - db_bins[512 - tone_bin] > 45.0F);

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
