#include "kx3/dx_analysis.hpp"

#include <algorithm>
#include <array>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <unordered_map>
#include <unordered_set>

namespace kx3 {
namespace {

constexpr std::array<const char*, kDxBandCount> kBands{{
    "160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m"}};
constexpr std::array<const char*, kDxRegionCount> kRegions{{"AF", "AN", "AS", "EU", "NA", "OC", "SA"}};

std::string upper_trim(std::string_view value) {
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.front()))) value.remove_prefix(1);
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.back()))) value.remove_suffix(1);
    std::string result(value);
    std::transform(result.begin(), result.end(), result.begin(), [](unsigned char c) {
        return static_cast<char>(std::toupper(c));
    });
    return result;
}

std::uint64_t stable_text_hash(std::string_view value) {
    std::uint64_t hash = 1469598103934665603ULL;
    for (const unsigned char character : value) {
        hash ^= character;
        hash *= 1099511628211ULL;
    }
    return hash;
}

bool close_frequency(std::uint64_t left, std::uint64_t right) {
    return left > right ? left - right <= 2000ULL : right - left <= 2000ULL;
}

unsigned freshness_score(std::int64_t age) {
    if (age <= 0) return 30U;
    if (age >= 1800) return 0U;
    return static_cast<unsigned>((1800 - age) * 30 / 1800);
}

unsigned band_index(std::string_view band) {
    const auto found = std::find(kBands.begin(), kBands.end(), band);
    return found == kBands.end() ? static_cast<unsigned>(kBands.size()) :
                                  static_cast<unsigned>(std::distance(kBands.begin(), found));
}

bool high_band(std::string_view band) {
    return band == "20m" || band == "17m" || band == "15m" || band == "12m" ||
           band == "10m" || band == "6m";
}

unsigned region_index(std::string_view region) {
    const auto found = std::find(kRegions.begin(), kRegions.end(), region);
    return found == kRegions.end() ? static_cast<unsigned>(kRegions.size()) :
                                    static_cast<unsigned>(std::distance(kRegions.begin(), found));
}

std::optional<float> number_after(std::string_view text, std::string_view marker) {
    const auto at = text.find(marker);
    if (at == std::string_view::npos) return std::nullopt;
    const auto start = text.find_first_of("+-0123456789.", at + marker.size());
    if (start == std::string_view::npos) return std::nullopt;
    std::string value(text.substr(start, std::min<std::size_t>(24, text.size() - start)));
    char* end{};
    const float parsed = std::strtof(value.c_str(), &end);
    if (end == value.c_str() || !std::isfinite(parsed)) return std::nullopt;
    return parsed;
}

std::vector<std::string> json_row_values(std::string_view row) {
    std::vector<std::string> values;
    std::string value;
    bool quoted{};
    bool escaped{};
    for (const char character : row) {
        if (escaped) {
            value.push_back(character);
            escaped = false;
        } else if (quoted && character == '\\') {
            escaped = true;
        } else if (character == '"') {
            quoted = !quoted;
        } else if (character == ',' && !quoted) {
            values.push_back(upper_trim(value));
            value.clear();
        } else {
            value.push_back(character);
        }
    }
    values.push_back(upper_trim(value));
    return values;
}

std::optional<float> parse_noaa_kp_rows(std::string_view text) {
    std::optional<float> latest;
    std::size_t kp_column = std::string_view::npos;
    std::size_t row_start{};
    unsigned depth{};
    bool quoted{};
    bool escaped{};
    for (std::size_t i = 0; i < text.size(); ++i) {
        const char character = text[i];
        if (escaped) {
            escaped = false;
            continue;
        }
        if (quoted && character == '\\') {
            escaped = true;
            continue;
        }
        if (character == '"') {
            quoted = !quoted;
            continue;
        }
        if (quoted) continue;
        if (character == '[') {
            ++depth;
            if (depth == 2U) row_start = i + 1U;
            continue;
        }
        if (character != ']' || depth == 0U) continue;
        if (depth == 2U) {
            const auto values = json_row_values(text.substr(row_start, i - row_start));
            if (kp_column == std::string_view::npos) {
                const auto found = std::find(values.begin(), values.end(), "KP");
                if (found != values.end())
                    kp_column = static_cast<std::size_t>(std::distance(values.begin(), found));
            } else if (kp_column < values.size()) {
                char* end{};
                const float parsed = std::strtof(values[kp_column].c_str(), &end);
                if (end != values[kp_column].c_str() && *end == '\0' && std::isfinite(parsed) &&
                    parsed >= 0.0F && parsed <= 9.0F) latest = parsed;
            }
        }
        --depth;
    }
    return latest;
}

}  // namespace

bool DxInsightEngine::ingest(DxSpot spot) {
    spot.callsign = upper_trim(spot.callsign);
    spot.spotter = upper_trim(spot.spotter);
    spot.country = upper_trim(spot.country);
    spot.continent = upper_trim(spot.continent);
    if (spot.callsign.empty() || spot.frequency_hz < 1000000ULL ||
        spot.frequency_hz > 54000000ULL || spot.received_epoch <= 0) return false;
    const std::size_t recent = std::min<std::size_t>(spots_.size(), 80U);
    for (std::size_t offset = 0; offset < recent; ++offset) {
        const auto& candidate = spots_[spots_.size() - 1U - offset];
        if (candidate.callsign == spot.callsign && candidate.spotter == spot.spotter &&
            close_frequency(candidate.frequency_hz, spot.frequency_hz) &&
            std::llabs(candidate.received_epoch - spot.received_epoch) <= 120LL) {
            ++duplicate_count_;
            return false;
        }
    }
    spots_.push_back(std::move(spot));
    if (spots_.size() > kCapacity) spots_.pop_front();
    return true;
}

void DxInsightEngine::set_watchlist(std::vector<std::string> callsigns) {
    watchlist_.clear();
    for (const auto& callsign : callsigns) {
        const std::string normalized = upper_trim(callsign);
        if (!normalized.empty()) watchlist_.insert(normalized);
    }
}

void DxInsightEngine::set_worked_countries(std::unordered_set<std::string> countries) {
    worked_country_hashes_.clear();
    worked_country_hashes_.reserve(countries.size());
    for (const auto& country : countries) {
        const std::string normalized = upper_trim(country);
        if (!normalized.empty()) worked_country_hashes_.push_back(stable_text_hash(normalized));
    }
    std::sort(worked_country_hashes_.begin(), worked_country_hashes_.end());
    worked_country_hashes_.erase(std::unique(worked_country_hashes_.begin(),
                                             worked_country_hashes_.end()),
                                 worked_country_hashes_.end());
}

void DxInsightEngine::update_solar(const SolarReading& reading) {
    if (reading.valid && std::isfinite(reading.solar_flux) && std::isfinite(reading.kp_index) &&
        reading.solar_flux >= 0.0F && reading.kp_index >= 0.0F) solar_ = reading;
}

DxInsightSnapshot DxInsightEngine::evaluate(std::int64_t now_epoch) const {
    DxInsightSnapshot output;
    output.solar = solar_;
    output.learned_spots = static_cast<unsigned>(spots_.size());
    output.duplicate_spots = duplicate_count_;
    for (std::size_t i = 0; i < kBands.size(); ++i) output.bands[i].band = kBands[i];
    for (std::size_t i = 0; i < kRegions.size(); ++i) output.regions[i].region = kRegions[i];

    std::array<std::unordered_set<std::string>, kDxBandCount> unique_calls;
    std::array<std::unordered_set<std::string>, kDxRegionCount> unique_region_calls;
    std::unordered_map<std::string, const DxSpot*> latest_call;
    std::unordered_map<std::string, const DxSpot*> latest_watch_call;
    std::unordered_map<std::string, unsigned> call_samples;
    for (const auto& spot : spots_) {
        const std::int64_t age = now_epoch - spot.received_epoch;
        if (age < -60 || age > 24 * 3600) continue;
        if (watchlist_.count(spot.callsign) != 0U) {
            auto watch = latest_watch_call.find(spot.callsign);
            if (watch == latest_watch_call.end() || watch->second->received_epoch < spot.received_epoch)
                latest_watch_call[spot.callsign] = &spot;
        }
        if (age > 3600) continue;
        const unsigned index = band_index(spot.band);
        if (index < output.bands.size()) {
            ++output.bands[index].spots_60m;
            unique_calls[index].insert(spot.callsign);
            if (age <= 300) ++output.bands[index].spots_5m;
            const unsigned bucket = std::min<unsigned>(kDxTimelineBuckets - 1U,
                static_cast<unsigned>(std::max<std::int64_t>(0, age) / 300));
            ++output.band_timeline[index][kDxTimelineBuckets - 1U - bucket];
        }
        const unsigned region = region_index(spot.continent);
        if (region < output.regions.size()) {
            ++output.regions[region].spots_60m;
            if (age <= 900) ++output.regions[region].spots_15m;
            unique_region_calls[region].insert(spot.callsign);
        }
        if (!spot.continent.empty() && std::isfinite(spot.latitude) && std::isfinite(spot.longitude)) {
            const int row = std::clamp(static_cast<int>((75.0F - spot.latitude) / 30.0F),
                                       0, static_cast<int>(kDxWorldRows - 1U));
            // CTY.DAT longitude is positive west; the display grid runs west to east.
            const float east_longitude = -spot.longitude;
            const int column = std::clamp(static_cast<int>((east_longitude + 180.0F) / 30.0F),
                                          0, static_cast<int>(kDxWorldColumns - 1U));
            ++output.world_grid[static_cast<std::size_t>(row)][static_cast<std::size_t>(column)];
        }
        ++call_samples[spot.callsign];
        auto found = latest_call.find(spot.callsign);
        if (found == latest_call.end() || found->second->received_epoch < spot.received_epoch)
            latest_call[spot.callsign] = &spot;
        output.newest_spot_epoch = std::max(output.newest_spot_epoch, spot.received_epoch);
    }

    for (std::size_t i = 0; i < output.bands.size(); ++i) {
        auto& band = output.bands[i];
        band.unique_calls_60m = static_cast<unsigned>(unique_calls[i].size());
        output.spots_5m += band.spots_5m;
        output.spots_60m += band.spots_60m;
        const unsigned baseline_5m = std::max(1U, (band.spots_60m - band.spots_5m + 10U) / 11U);
        band.surge_percent = std::min(999U, band.spots_5m * 100U / baseline_5m);
        const unsigned minimum = band.band == "6m" ? 4U : 3U;
        band.surge = band.spots_5m >= minimum && band.surge_percent >= 180U;
        if (band.surge) ++output.surging_bands;
    }

    for (std::size_t i = 0; i < output.regions.size(); ++i) {
        auto& region = output.regions[i];
        region.unique_calls_60m = static_cast<unsigned>(unique_region_calls[i].size());
        const unsigned previous_45m = region.spots_60m - region.spots_15m;
        const unsigned baseline_15m = std::max(1U, (previous_45m + 2U) / 3U);
        region.activity_percent = std::min(999U, region.spots_15m * 100U / baseline_15m);
        region.anomaly = region.spots_15m >= 3U && region.activity_percent >= 180U;
    }

    const bool solar_fresh = solar_.valid && now_epoch >= solar_.observed_epoch &&
                             now_epoch - solar_.observed_epoch <= 3 * 3600;
    const auto make_opportunity = [&](const DxSpot& spot) {
        const std::int64_t age = std::max<std::int64_t>(0, now_epoch - spot.received_epoch);
        DxOpportunity opportunity;
        opportunity.spot = spot;
        const auto samples = call_samples.find(spot.callsign);
        opportunity.samples = samples == call_samples.end() ? 1U : samples->second;
        opportunity.watchlisted = watchlist_.count(spot.callsign) != 0U;
        const auto country_hash = stable_text_hash(spot.country);
        opportunity.worked_country = !spot.country.empty() &&
            std::binary_search(worked_country_hashes_.begin(), worked_country_hashes_.end(),
                               country_hash);
        unsigned score = freshness_score(age);
        if (opportunity.watchlisted) score += 35U;
        if (!spot.country.empty() && !opportunity.worked_country) score += 22U;
        const unsigned index = band_index(spot.band);
        if (index < output.bands.size() && output.bands[index].surge) score += 13U;
        if (solar_fresh) {
            if (high_band(spot.band) && solar_.solar_flux >= 110.0F && solar_.kp_index <= 3.0F) score += 8U;
            if (solar_.kp_index >= 5.0F) score = score > 10U ? score - 10U : 0U;
        }
        opportunity.score = std::min(100U, score);
        opportunity.confidence = std::min(95U, 25U + std::min(30U, opportunity.samples * 5U) +
            (solar_fresh ? 15U : 0U) + (output.spots_60m >= 30U ? 20U : output.spots_60m * 2U / 3U));
        if (opportunity.watchlisted) opportunity.reason = "WATCHLIST";
        else if (!spot.country.empty() && !opportunity.worked_country) opportunity.reason = "NEW ENTITY IN LOGBOOK";
        else if (index < output.bands.size() && output.bands[index].surge) opportunity.reason = "BAND SURGE";
        else if (solar_fresh && high_band(spot.band) && solar_.solar_flux >= 110.0F && solar_.kp_index <= 3.0F)
            opportunity.reason = "SOLAR SUPPORT";
        else opportunity.reason = "FRESH CLUSTER ACTIVITY";
        return opportunity;
    };

    for (const auto& item : latest_call) {
        const DxSpot& spot = *item.second;
        if (now_epoch - spot.received_epoch > 1800) continue;
        auto opportunity = make_opportunity(spot);
        if (opportunity.watchlisted) ++output.watchlist_hits;
        output.opportunities.push_back(std::move(opportunity));
    }
    std::sort(output.opportunities.begin(), output.opportunities.end(),
              [](const DxOpportunity& left, const DxOpportunity& right) {
        if (left.score != right.score) return left.score > right.score;
        return left.spot.received_epoch > right.spot.received_epoch;
    });
    if (output.opportunities.size() > kDxOpportunityCount)
        output.opportunities.resize(kDxOpportunityCount);

    for (auto spot = spots_.rbegin(); spot != spots_.rend() && output.live_spots.size() < kDxLiveCount; ++spot) {
        const std::int64_t age = now_epoch - spot->received_epoch;
        if (age < -60 || age > 24 * 3600) continue;
        output.live_spots.push_back(make_opportunity(*spot));
    }
    for (const auto& item : latest_watch_call)
        output.watch_activity.push_back(make_opportunity(*item.second));
    std::sort(output.watch_activity.begin(), output.watch_activity.end(),
              [](const DxOpportunity& left, const DxOpportunity& right) {
        return left.spot.received_epoch > right.spot.received_epoch;
    });
    if (output.watch_activity.size() > kDxOpportunityCount)
        output.watch_activity.resize(kDxOpportunityCount);
    return output;
}

std::vector<std::string> parse_dx_watchlist(std::string_view text) {
    std::vector<std::string> result;
    std::unordered_set<std::string> seen;
    std::size_t start{};
    while (start <= text.size() && result.size() < 32U) {
        const auto end = text.find_first_of(",; \t\r\n", start);
        std::string value = upper_trim(text.substr(start, end == std::string_view::npos ?
            std::string_view::npos : end - start));
        if (!value.empty() && value.size() <= 16U && seen.insert(value).second)
            result.push_back(std::move(value));
        if (end == std::string_view::npos) break;
        start = end + 1U;
    }
    return result;
}

std::string serialize_dx_watchlist(const std::vector<std::string>& callsigns) {
    std::string result;
    for (const auto& callsign : parse_dx_watchlist([&]() {
        std::string joined;
        for (const auto& value : callsigns) { if (!joined.empty()) joined.push_back(','); joined += value; }
        return joined;
    }())) {
        if (!result.empty()) result.push_back(',');
        result += callsign;
    }
    return result;
}

bool dx_live_filter_matches(std::string_view band, std::string_view mode, bool worked_entity,
                            std::uint32_t band_mask, std::uint32_t mode_mask,
                            std::uint32_t entity_mask) {
    constexpr std::array<std::string_view, 11> bands{{"160m", "80m", "60m", "40m", "30m",
        "20m", "17m", "15m", "12m", "10m", "6m"}};
    constexpr std::array<std::string_view, 8> modes{{"CW", "SSB", "FT8", "FT4", "DATA",
        "RTTY", "AM", "FM"}};
    if (band_mask != 0U) {
        bool found = false;
        for (std::size_t i = 0; i < bands.size(); ++i)
            if ((band_mask & (1U << i)) != 0U && band == bands[i]) { found = true; break; }
        if (!found) return false;
    }
    if (mode_mask != 0U) {
        bool found = false;
        for (std::size_t i = 0; i < modes.size(); ++i) {
            if ((mode_mask & (1U << i)) == 0U) continue;
            if (mode == modes[i] || (modes[i] == "SSB" &&
                (mode == "USB" || mode == "LSB"))) { found = true; break; }
        }
        if (!found) return false;
    }
    if (entity_mask != 0U) {
        const std::uint32_t bit = worked_entity ? 1U : 2U;
        if ((entity_mask & bit) == 0U) return false;
    }
    return true;
}

std::optional<SolarReading> parse_noaa_wwv(std::string_view text,
                                           std::int64_t observed_epoch) {
    const auto flux = number_after(text, "Solar flux");
    const auto a = number_after(text, "A-index");
    const auto kp_marker = text.find("K-index");
    const auto kp = kp_marker == std::string_view::npos ? std::optional<float>{} :
        ([&]() {
            const std::string_view sentence = text.substr(kp_marker);
            const auto after_was = number_after(sentence, "was");
            return after_was.has_value() ? after_was : number_after(sentence, "K-index");
        })();
    if (!flux.has_value() || !a.has_value() || !kp.has_value()) return std::nullopt;
    const float flux_value = flux.value();
    const float a_value = a.value();
    const float kp_value = kp.value();
    if (flux_value < 40.0F || flux_value > 500.0F || a_value < 0.0F ||
        a_value > 400.0F || kp_value < 0.0F || kp_value > 9.0F) return std::nullopt;
    return SolarReading{flux_value, a_value, kp_value, observed_epoch, true};
}

std::optional<float> parse_noaa_kp(std::string_view text) {
    std::optional<float> latest;
    std::size_t at{};
    while ((at = text.find("\"Kp\"", at)) != std::string_view::npos) {
        std::size_t colon = at + 4U;
        while (colon < text.size() && std::isspace(static_cast<unsigned char>(text[colon]))) ++colon;
        if (colon >= text.size() || text[colon] != ':') {
            at += 4U;
            continue;
        }
        const auto number = text.find_first_of("+-0123456789.", colon + 1U);
        if (number == std::string_view::npos) break;
        std::string value(text.substr(number, std::min<std::size_t>(24, text.size() - number)));
        char* end{};
        const float parsed = std::strtof(value.c_str(), &end);
        if (end != value.c_str() && std::isfinite(parsed) && parsed >= 0.0F && parsed <= 9.0F)
            latest = parsed;
        at = number + 1U;
    }
    return latest.has_value() ? latest : parse_noaa_kp_rows(text);
}

}  // namespace kx3
