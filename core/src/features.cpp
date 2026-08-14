#include "rigweave/core.h"

#include "kx3/adif.hpp"
#include "kx3/cty.hpp"
#include "kx3/dx_analysis.hpp"
#include "kx3/operator_intel.hpp"
#include "kx3/panadapter_dsp.hpp"
#include "kx3/spot.hpp"
#include "kx3/sync_queue.hpp"
#include "kx3/wsjtx_protocol.hpp"

#include <algorithm>
#include <cstring>
#include <iomanip>
#include <sstream>
#include <string>
#include <string_view>

namespace {
std::string json_escape(std::string_view value) {
    std::string output;
    output.reserve(value.size() + 8);
    for (const unsigned char byte : value) {
        switch (byte) {
            case '"': output += "\\\""; break;
            case '\\': output += "\\\\"; break;
            case '\b': output += "\\b"; break;
            case '\f': output += "\\f"; break;
            case '\n': output += "\\n"; break;
            case '\r': output += "\\r"; break;
            case '\t': output += "\\t"; break;
            default:
                if (byte < 0x20U) {
                    char escaped[7]{};
                    std::snprintf(escaped, sizeof(escaped), "\\u%04x", byte);
                    output += escaped;
                } else output.push_back(static_cast<char>(byte));
        }
    }
    return output;
}

int write_output(char *output, size_t output_size, const std::string& value) {
    if (output == nullptr || output_size == 0 || value.size() + 1 > output_size) return 0;
    std::memcpy(output, value.c_str(), value.size() + 1);
    return static_cast<int>(value.size());
}

std::string quoted(std::string_view value) { return "\"" + json_escape(value) + "\""; }
const char* boolean(bool value) { return value ? "true" : "false"; }

const char* light_name(kx3::intel::LightState value) {
    switch (value) {
        case kx3::intel::LightState::Day: return "day";
        case kx3::intel::LightState::Greyline: return "greyline";
        case kx3::intel::LightState::Night: return "night";
        default: return "unknown";
    }
}

const char* freshness_name(kx3::intel::SolarFreshness value) {
    switch (value) {
        case kx3::intel::SolarFreshness::Fresh: return "fresh";
        case kx3::intel::SolarFreshness::Stale: return "stale";
        default: return "missing";
    }
}

const char* confidence_name(kx3::intel::SampleConfidence value) {
    switch (value) {
        case kx3::intel::SampleConfidence::Low: return "low";
        case kx3::intel::SampleConfidence::Medium: return "medium";
        case kx3::intel::SampleConfidence::High: return "high";
        default: return "insufficient";
    }
}
}  // namespace

struct rw_feature_context {
    kx3::CtyResolver cty;
    kx3::DxInsightEngine dx;
    kx3::intel::WorkedIndex worked;
    kx3::PanadapterDsp panadapter;

    rw_feature_context() : worked(kx3::intel::kDefaultWorkedCells) {}
};

rw_feature_context *rw_feature_context_create(void) {
    return new rw_feature_context{};
}

void rw_feature_context_destroy(rw_feature_context *context) { delete context; }

int rw_feature_load_cty_text(rw_feature_context *context, const char *cty_text) {
    return context != nullptr && cty_text != nullptr && context->cty.load_text(cty_text) ? 1 : 0;
}

int rw_feature_set_watchlist(rw_feature_context *context, const char *watchlist_text) {
    if (context == nullptr || watchlist_text == nullptr) return 0;
    context->dx.set_watchlist(kx3::parse_dx_watchlist(watchlist_text));
    return 1;
}

int rw_feature_set_solar(rw_feature_context *context, float solar_flux, float a_index,
                         float kp_index, int64_t observed_epoch) {
    if (context == nullptr || observed_epoch <= 0) return 0;
    context->dx.update_solar({solar_flux, a_index, kp_index, observed_epoch, true});
    return 1;
}

int rw_feature_ingest_cluster_line(rw_feature_context *context, const char *line,
                                   int64_t received_epoch) {
    if (context == nullptr || line == nullptr) return 0;
    const auto parsed = kx3::parse_cluster_spot(line, received_epoch);
    if (!parsed.has_value()) return 0;
    const auto entity = context->cty.resolve(parsed->callsign);
    kx3::DxSpot spot{};
    spot.frequency_hz = parsed->frequency_hz;
    spot.received_epoch = parsed->received_epoch;
    spot.callsign = parsed->callsign;
    spot.spotter = parsed->spotter;
    spot.comment = parsed->comment;
    spot.band = parsed->band;
    spot.mode = parsed->mode;
    spot.country = entity.country;
    spot.continent = entity.continent;
    spot.cq_zone = entity.cq_zone;
    spot.itu_zone = entity.itu_zone;
    spot.latitude = entity.latitude;
    spot.longitude = entity.longitude;
    return context->dx.ingest(std::move(spot)) ? 1 : 0;
}

int rw_feature_dx_snapshot_json(const rw_feature_context *context, char *output,
                                size_t output_size, int64_t now_epoch) {
    if (context == nullptr) return 0;
    const auto snapshot = context->dx.evaluate(now_epoch);
    std::ostringstream json;
    json << "{\"spots5m\":" << snapshot.spots_5m
         << ",\"spots60m\":" << snapshot.spots_60m
         << ",\"learnedSpots\":" << snapshot.learned_spots
         << ",\"duplicateSpots\":" << snapshot.duplicate_spots
         << ",\"watchlistHits\":" << snapshot.watchlist_hits
         << ",\"surgingBands\":" << snapshot.surging_bands
         << ",\"newestSpotEpoch\":" << snapshot.newest_spot_epoch
         << ",\"solar\":{\"valid\":" << boolean(snapshot.solar.valid)
         << ",\"flux\":" << snapshot.solar.solar_flux
         << ",\"aIndex\":" << snapshot.solar.a_index
         << ",\"kpIndex\":" << snapshot.solar.kp_index << "},\"bands\":[";
    bool first = true;
    for (const auto& band : snapshot.bands) {
        if (band.band.empty()) continue;
        if (!first) json << ',';
        first = false;
        json << "{\"band\":" << quoted(band.band) << ",\"spots5m\":" << band.spots_5m
             << ",\"spots60m\":" << band.spots_60m << ",\"uniqueCalls\":" << band.unique_calls_60m
             << ",\"surgePercent\":" << band.surge_percent << ",\"surge\":" << boolean(band.surge) << '}';
    }
    json << "],\"opportunities\":[";
    first = true;
    for (const auto& row : snapshot.opportunities) {
        if (!first) json << ',';
        first = false;
        json << "{\"callsign\":" << quoted(row.spot.callsign)
             << ",\"spotter\":" << quoted(row.spot.spotter)
             << ",\"frequencyHz\":" << row.spot.frequency_hz
             << ",\"receivedEpoch\":" << row.spot.received_epoch
             << ",\"band\":" << quoted(row.spot.band)
             << ",\"mode\":" << quoted(row.spot.mode)
             << ",\"country\":" << quoted(row.spot.country)
             << ",\"continent\":" << quoted(row.spot.continent)
             << ",\"comment\":" << quoted(row.spot.comment)
             << ",\"score\":" << row.score << ",\"confidence\":" << row.confidence
             << ",\"watchlisted\":" << boolean(row.watchlisted)
             << ",\"workedCountry\":" << boolean(row.worked_country)
             << ",\"workedCall\":" << boolean(row.worked_call)
             << ",\"recentDupe\":" << boolean(row.recent_dupe)
             << ",\"reason\":" << quoted(row.reason) << '}';
    }
    json << "]}";
    return write_output(output, output_size, json.str());
}

int rw_feature_add_worked_qso(rw_feature_context *context, const char *callsign,
                              const char *entity, const char *band, const char *mode,
                              const char *submode, int64_t epoch, int from_wavelog) {
    if (context == nullptr || callsign == nullptr) return 0;
    kx3::intel::WorkedRecord record{};
    record.call = callsign;
    record.entity = entity == nullptr ? "" : entity;
    record.band = band == nullptr ? "" : band;
    record.mode = mode == nullptr ? "" : mode;
    record.submode = submode == nullptr ? "" : submode;
    record.epoch = epoch;
    record.source = from_wavelog ? kx3::intel::LogSource::Wavelog : kx3::intel::LogSource::Local;
    return context->worked.add(std::move(record)) ? 1 : 0;
}

int rw_feature_worked_json(const rw_feature_context *context, char *output,
                           size_t output_size, const char *callsign, const char *entity,
                           const char *band, const char *mode, const char *submode,
                           int64_t now_epoch) {
    if (context == nullptr || callsign == nullptr) return 0;
    const auto row = context->worked.classify(callsign, entity == nullptr ? "" : entity,
        band == nullptr ? "" : band, mode == nullptr ? "" : mode,
        submode == nullptr ? "" : submode, now_epoch);
    std::ostringstream json;
    json << "{\"callsign\":" << quoted(row.canonical_call)
         << ",\"entity\":" << quoted(row.canonical_entity)
         << ",\"band\":" << quoted(row.canonical_band)
         << ",\"mode\":" << quoted(row.canonical_mode)
         << ",\"callWorked\":" << boolean(row.call_any)
         << ",\"callBandMode\":" << boolean(row.call_band_mode)
         << ",\"entityWorked\":" << boolean(row.entity_any)
         << ",\"entityBandMode\":" << boolean(row.entity_band_mode)
         << ",\"recentDupe\":" << boolean(row.recent_dupe_band_mode)
         << ",\"foundLocal\":" << boolean(row.found_local)
         << ",\"foundWavelog\":" << boolean(row.found_wavelog)
         << ",\"matchingQsos\":" << row.matching_qsos
         << ",\"lastQsoEpoch\":" << row.last_qso_epoch
         << ",\"indexComplete\":" << boolean(row.index_complete) << '}';
    return write_output(output, output_size, json.str());
}

int rw_feature_propagation_json(char *output, size_t output_size,
                                const char *station_grid, const char *target_grid,
                                const char *band, int64_t epoch, float solar_flux,
                                float kp_index, int64_t solar_epoch,
                                unsigned observations, unsigned favorable_observations) {
    if (station_grid == nullptr || target_grid == nullptr || band == nullptr) return 0;
    kx3::intel::PropagationContextInput input{};
    input.station_grid = station_grid;
    input.target_grid = target_grid;
    input.band = band;
    input.epoch = epoch;
    input.solar = {solar_epoch > 0, solar_flux, kp_index, solar_epoch};
    input.local_samples = {observations, favorable_observations};
    const auto row = kx3::intel::explain_propagation(input);
    std::ostringstream json;
    json << std::fixed << std::setprecision(2)
         << "{\"valid\":" << boolean(row.valid)
         << ",\"distanceKm\":" << row.distance_km
         << ",\"bearingDegrees\":" << row.initial_bearing_deg
         << ",\"stationLight\":" << quoted(light_name(row.station_light))
         << ",\"targetLight\":" << quoted(light_name(row.target_light))
         << ",\"solarFreshness\":" << quoted(freshness_name(row.solar_freshness))
         << ",\"sampleConfidence\":" << quoted(confidence_name(row.sample_confidence))
         << ",\"localSuccessPercent\":" << row.local_success_percent
         << ",\"contextScore\":" << row.context_score
         << ",\"observations\":" << row.observations
         << ",\"label\":" << quoted(row.label) << ",\"reasons\":[";
    for (size_t index = 0; index < row.reasons.size(); ++index) {
        if (index != 0) json << ',';
        json << quoted(row.reasons[index]);
    }
    json << "]}";
    return write_output(output, output_size, json.str());
}

int rw_panadapter_push_pcm(rw_feature_context *context, const uint8_t *bytes, size_t length,
                           unsigned channels, unsigned subframe_bytes, unsigned bits) {
    return context != nullptr && context->panadapter.push_pcm(bytes, length, channels, subframe_bytes, bits) ? 1 : 0;
}

size_t rw_panadapter_copy_bins(const rw_feature_context *context, uint8_t *output,
                               size_t output_size) {
    if (context == nullptr || output == nullptr) return 0;
    const auto& bins = context->panadapter.bins();
    const size_t count = std::min(output_size, bins.size());
    std::copy_n(bins.begin(), count, output);
    return count;
}

float rw_panadapter_peak_db(const rw_feature_context *context) {
    return context == nullptr ? -120.0F : context->panadapter.peak_db();
}
float rw_panadapter_i_rms_db(const rw_feature_context *context) {
    return context == nullptr ? -120.0F : context->panadapter.i_rms_db();
}
float rw_panadapter_q_rms_db(const rw_feature_context *context) {
    return context == nullptr ? -120.0F : context->panadapter.q_rms_db();
}
float rw_panadapter_iq_correlation(const rw_feature_context *context) {
    return context == nullptr ? 0.0F : context->panadapter.iq_correlation();
}

int rw_sync_action(int status_code, int network_error, int response_ambiguous) {
    return static_cast<int>(kx3::classify_http_result(status_code, network_error != 0,
                                                      response_ambiguous != 0));
}

uint32_t rw_sync_retry_delay(uint32_t attempt, uint32_t jitter_seed,
                             uint32_t retry_after, int has_retry_after) {
    return kx3::retry_delay_seconds(attempt, jitter_seed,
        has_retry_after ? std::optional<uint32_t>(retry_after) : std::nullopt);
}

int rw_wavelog_normalize_url(char *output, size_t output_size, const char *url) {
    return url == nullptr ? 0 : write_output(output, output_size, kx3::normalize_wavelog_url(url));
}

int rw_wavelog_payload(char *output, size_t output_size, const char *api_key,
                       const char *station_profile_id, const char *adif) {
    if (api_key == nullptr || station_profile_id == nullptr || adif == nullptr) return 0;
    return write_output(output, output_size, kx3::wavelog_payload(api_key, station_profile_id, adif));
}

int rw_wsjtx_parse_json(char *output, size_t output_size,
                        const uint8_t *datagram, size_t datagram_size) {
    kx3::wsjtx::ParseError error{};
    const auto message = kx3::wsjtx::parse_datagram(datagram, datagram_size, &error);
    if (!message.has_value()) {
        return write_output(output, output_size,
            std::string("{\"valid\":false,\"error\":") + quoted(kx3::wsjtx::parse_error_text(error)) + '}');
    }
    std::ostringstream json;
    json << "{\"valid\":true,\"stationId\":" << quoted(message->header.id);
    if (const auto* status = std::get_if<kx3::wsjtx::Status>(&message->payload)) {
        json << ",\"type\":\"status\",\"frequencyHz\":" << status->dial_frequency_hz
             << ",\"mode\":" << quoted(status->mode) << ",\"dxCall\":" << quoted(status->dx_call)
             << ",\"transmitting\":" << boolean(status->transmitting)
             << ",\"decoding\":" << boolean(status->decoding);
    } else if (const auto* decode = std::get_if<kx3::wsjtx::Decode>(&message->payload)) {
        json << ",\"type\":\"decode\",\"snrDb\":" << decode->snr_db
             << ",\"deltaFrequencyHz\":" << decode->delta_frequency_hz
             << ",\"mode\":" << quoted(decode->mode) << ",\"message\":" << quoted(decode->message)
             << ",\"lowConfidence\":" << boolean(decode->low_confidence);
    } else if (const auto* logged = std::get_if<kx3::wsjtx::LoggedAdif>(&message->payload)) {
        json << ",\"type\":\"loggedAdif\",\"callsign\":" << quoted(logged->call)
             << ",\"band\":" << quoted(logged->band) << ",\"mode\":" << quoted(logged->mode)
             << ",\"submode\":" << quoted(logged->submode) << ",\"adif\":" << quoted(logged->raw);
    }
    json << '}';
    return write_output(output, output_size, json.str());
}
