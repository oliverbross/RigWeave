#include "kx3/operator_intel.hpp"

#include <algorithm>
#include <array>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <ctime>
#include <limits>
#include <map>
#include <set>

namespace kx3::intel {
namespace {

constexpr double kPi = 3.14159265358979323846;
constexpr double kEarthRadiusKm = 6371.0088;

std::string upper_alnum(std::string_view value, bool allow_slash = false) {
    std::string result;
    result.reserve(value.size());
    for (const unsigned char character : value) {
        if (std::isspace(character) || character == '-' || character == '_') continue;
        if (std::isalnum(character) || (allow_slash && character == '/'))
            result.push_back(static_cast<char>(std::toupper(character)));
    }
    return result;
}

std::string upper_trim(std::string_view value) {
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.front())))
        value.remove_prefix(1);
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.back())))
        value.remove_suffix(1);
    std::string result(value);
    std::transform(result.begin(), result.end(), result.begin(), [](unsigned char character) {
        return static_cast<char>(std::toupper(character));
    });
    return result;
}

std::string base_call(std::string call) {
    const auto slash = call.find('/');
    if (slash == std::string::npos) return call;
    std::vector<std::string> parts;
    std::size_t start{};
    while (start <= call.size()) {
        const auto end = call.find('/', start);
        parts.push_back(call.substr(start, end == std::string::npos ? std::string::npos : end - start));
        if (end == std::string::npos) break;
        start = end + 1;
    }
    const auto portable = [](const std::string& part) {
        static constexpr std::array<std::string_view, 8> suffixes{
            "P", "M", "MM", "AM", "QRP", "QRPP", "LH", "A"};
        return std::find(suffixes.begin(), suffixes.end(), part) != suffixes.end() ||
               (part.size() == 1 && std::isdigit(static_cast<unsigned char>(part[0])));
    };
    const auto best = std::max_element(parts.begin(), parts.end(), [&](const auto& left, const auto& right) {
        const int left_score = (portable(left) ? -100 : 0) +
            (std::any_of(left.begin(), left.end(), [](unsigned char c) { return std::isdigit(c); }) ? 20 : 0) +
            static_cast<int>(left.size());
        const int right_score = (portable(right) ? -100 : 0) +
            (std::any_of(right.begin(), right.end(), [](unsigned char c) { return std::isdigit(c); }) ? 20 : 0) +
            static_cast<int>(right.size());
        return left_score < right_score;
    });
    return best == parts.end() ? call : *best;
}

std::uint64_t worked_hash(std::string_view call, std::string_view entity,
                          std::string_view band, std::string_view mode) {
    std::uint64_t hash = 1469598103934665603ULL;
    auto append = [&](std::string_view value) {
        for (const unsigned char character : value) {
            hash ^= character;
            hash *= 1099511628211ULL;
        }
        hash ^= 0xffU;
        hash *= 1099511628211ULL;
    };
    append(call); append(entity); append(band); append(mode);
    return hash;
}

bool source_local(LogSource source) {
    return source == LogSource::Local;
}

bool source_wavelog(LogSource source) {
    return source == LogSource::Wavelog;
}

double radians(double degrees) { return degrees * kPi / 180.0; }
double degrees(double radians_value) { return radians_value * 180.0 / kPi; }

double normalize_degrees(double value) {
    value = std::fmod(value, 360.0);
    return value < 0.0 ? value + 360.0 : value;
}

std::tm utc_time(std::int64_t epoch) {
    const std::time_t raw = static_cast<std::time_t>(epoch);
    std::tm result{};
#if defined(_WIN32)
    gmtime_s(&result, &raw);
#else
    gmtime_r(&raw, &result);
#endif
    return result;
}

unsigned day_of_year(const std::tm& utc) { return static_cast<unsigned>(utc.tm_yday + 1); }

void add_reason(PropagationContext& output, std::string reason) {
    if (output.reasons.size() < kMaxContextReasons) output.reasons.push_back(std::move(reason));
}

}  // namespace

std::string canonical_call(std::string_view call) {
    std::string normalized = upper_alnum(call, true);
    if (normalized.empty() || normalized.size() > 24) return {};
    normalized = base_call(std::move(normalized));
    if (normalized.size() < 3 || normalized.size() > 16 ||
        !std::any_of(normalized.begin(), normalized.end(), [](unsigned char c) { return std::isdigit(c); }) ||
        !std::any_of(normalized.begin(), normalized.end(), [](unsigned char c) { return std::isalpha(c); }))
        return {};
    return normalized;
}

std::string canonical_entity(std::string_view entity) {
    std::string normalized = upper_trim(entity);
    normalized.erase(std::unique(normalized.begin(), normalized.end(), [](char left, char right) {
        return std::isspace(static_cast<unsigned char>(left)) &&
               std::isspace(static_cast<unsigned char>(right));
    }), normalized.end());
    if (normalized == "UNKNOWN" || normalized == "N/A" || normalized.size() > 80) return {};
    return normalized;
}

std::string canonical_band(std::string_view band) {
    std::string normalized = upper_alnum(band);
    if (normalized.empty() || normalized.size() > 8) return {};
    if (normalized.size() >= 2 && normalized.substr(normalized.size() - 2) == "CM") {
        normalized[normalized.size() - 2] = 'c';
        normalized.back() = 'm';
    } else if (normalized.back() == 'M') normalized.back() = 'm';
    return normalized;
}

std::string canonical_mode(std::string_view mode, std::string_view submode) {
    const std::string child = upper_alnum(submode);
    if (!child.empty()) return child;
    std::string parent = upper_alnum(mode);
    if (parent == "USB" || parent == "LSB" || parent == "PHONE") return "SSB";
    if (parent == "MFSK") return "DATA";
    return parent;
}

WorkedIndex::WorkedIndex(std::size_t capacity)
    : capacity_(std::max<std::size_t>(1, std::min(capacity, kMaxWorkedCells))) {
    cells_.reserve(capacity_);
    std::size_t slots = 2;
    while (slots < capacity_ * 2U) slots <<= 1U;
    lookup_.assign(slots, 0U);
    call_lookup_.assign(slots, 0U);
    entity_lookup_.assign(slots, 0U);
}

std::size_t WorkedIndex::allocated_bytes() const {
    return cells_.capacity() * sizeof(WorkedCell) +
           (lookup_.capacity() + call_lookup_.capacity() + entity_lookup_.capacity()) *
               sizeof(std::uint32_t);
}

std::size_t WorkedIndex::used_bytes() const {
    return cells_.size() * sizeof(WorkedCell) +
           (lookup_.size() + call_lookup_.size() + entity_lookup_.size()) *
               sizeof(std::uint32_t);
}

bool WorkedIndex::add(WorkedRecord record) {
    record.call = canonical_call(record.call);
    record.entity = canonical_entity(record.entity);
    record.band = canonical_band(record.band);
    const std::string normalized_mode = canonical_mode(record.mode, record.submode);
    record.mode = normalized_mode;
    record.submode.clear();
    if (record.call.empty() || record.entity.empty() || record.band.empty() ||
        record.mode.empty() || record.epoch <= 0) {
        ++rejected_count_;
        return false;
    }
    const auto same = [&](const WorkedCell& cell) {
        return record.call == cell.call.data() && record.entity == cell.entity.data() &&
               record.band == cell.band.data() && record.mode == cell.mode.data();
    };
    const std::size_t mask = lookup_.size() - 1U;
    std::size_t slot = static_cast<std::size_t>(worked_hash(record.call, record.entity,
                                                            record.band, record.mode)) & mask;
    while (lookup_[slot] != 0U && !same(cells_[lookup_[slot] - 1U])) slot = (slot + 1U) & mask;
    if (lookup_[slot] != 0U) {
        auto existing = cells_.begin() + static_cast<std::ptrdiff_t>(lookup_[slot] - 1U);
        existing->first_epoch = std::min(existing->first_epoch, record.epoch);
        existing->last_epoch = std::max(existing->last_epoch, record.epoch);
        if (existing->qso_count != std::numeric_limits<unsigned>::max()) ++existing->qso_count;
        existing->found_local = existing->found_local || source_local(record.source);
        existing->found_wavelog = existing->found_wavelog || source_wavelog(record.source);
        return true;
    }
    if (cells_.size() == capacity_) {
        ++truncated_count_;
        return false;
    }
    WorkedCell cell;
    std::snprintf(cell.call.data(), cell.call.size(), "%s", record.call.c_str());
    std::snprintf(cell.entity.data(), cell.entity.size(), "%s", record.entity.c_str());
    std::snprintf(cell.band.data(), cell.band.size(), "%s", record.band.c_str());
    std::snprintf(cell.mode.data(), cell.mode.size(), "%s", record.mode.c_str());
    cell.first_epoch = record.epoch;
    cell.last_epoch = record.epoch;
    cell.qso_count = 1;
    cell.found_local = source_local(record.source);
    cell.found_wavelog = source_wavelog(record.source);
    const auto chain_slot = [&](const std::vector<std::uint32_t>& heads,
                                std::uint64_t hash, const std::string& key,
                                bool call_key) {
        std::size_t chain = static_cast<std::size_t>(hash) & mask;
        while (heads[chain] != 0U) {
            const auto& head = cells_[heads[chain] - 1U];
            const char* existing = call_key ? head.call.data() : head.entity.data();
            if (key == existing) break;
            chain = (chain + 1U) & mask;
        }
        return chain;
    };
    const std::size_t call_slot = chain_slot(call_lookup_,
        worked_hash(record.call, {}, {}, {}), record.call, true);
    const std::size_t entity_slot = chain_slot(entity_lookup_,
        worked_hash({}, record.entity, {}, {}), record.entity, false);
    cell.next_call = call_lookup_[call_slot];
    cell.next_entity = entity_lookup_[entity_slot];
    cells_.push_back(cell);
    const auto index = static_cast<std::uint32_t>(cells_.size());
    lookup_[slot] = index;
    call_lookup_[call_slot] = index;
    entity_lookup_[entity_slot] = index;
    return true;
}

std::size_t WorkedIndex::add_all(const std::vector<WorkedRecord>& records) {
    std::size_t added{};
    for (auto record : records) if (add(std::move(record))) ++added;
    return added;
}

void WorkedIndex::clear() {
    cells_.clear();
    std::fill(lookup_.begin(), lookup_.end(), 0U);
    std::fill(call_lookup_.begin(), call_lookup_.end(), 0U);
    std::fill(entity_lookup_.begin(), entity_lookup_.end(), 0U);
    rejected_count_ = 0;
    truncated_count_ = 0;
}

WorkedClassification WorkedIndex::classify(std::string_view call, std::string_view entity,
                                           std::string_view band, std::string_view mode,
                                           std::string_view submode, std::int64_t now_epoch,
                                           std::int64_t recent_dupe_seconds) const {
    WorkedClassification result;
    result.canonical_call = canonical_call(call);
    result.canonical_entity = canonical_entity(entity);
    result.canonical_band = canonical_band(band);
    result.canonical_mode = canonical_mode(mode, submode);
    const std::int64_t dupe_window = std::max<std::int64_t>(0, recent_dupe_seconds);
    result.index_complete = complete();
    result.truncated_records = truncated_count_;
    const std::size_t mask = lookup_.size() - 1U;
    const auto chain_head = [&](const std::vector<std::uint32_t>& heads,
                                std::uint64_t hash, const std::string& key,
                                bool call_key) {
        if (key.empty()) return std::uint32_t{};
        std::size_t slot = static_cast<std::size_t>(hash) & mask;
        while (heads[slot] != 0U) {
            const auto& head = cells_[heads[slot] - 1U];
            const char* existing = call_key ? head.call.data() : head.entity.data();
            if (key == existing) return heads[slot];
            slot = (slot + 1U) & mask;
        }
        return std::uint32_t{};
    };
    const auto accumulate = [&](const WorkedCell& record, bool call_match,
                                bool entity_match, bool count_record) {
        const bool band_match = !result.canonical_band.empty() && result.canonical_band == record.band.data();
        const bool mode_match = !result.canonical_mode.empty() && result.canonical_mode == record.mode.data();
        if (entity_match) {
            result.entity_any = true;
            result.entity_band = result.entity_band || band_match;
            result.entity_mode = result.entity_mode || mode_match;
            result.entity_band_mode = result.entity_band_mode || (band_match && mode_match);
        }
        if (call_match) {
            result.call_any = true;
            result.call_band = result.call_band || band_match;
            result.call_mode = result.call_mode || mode_match;
            result.call_band_mode = result.call_band_mode || (band_match && mode_match);
            const std::int64_t age = now_epoch - record.last_epoch;
            if (age >= 0 && age <= dupe_window) {
                result.recent_dupe_any = true;
                result.recent_dupe_band_mode = result.recent_dupe_band_mode || (band_match && mode_match);
            }
        }
        result.found_local = result.found_local || record.found_local;
        result.found_wavelog = result.found_wavelog || record.found_wavelog;
        result.last_qso_epoch = std::max(result.last_qso_epoch, record.last_epoch);
        if (count_record) {
            result.matching_qsos = std::numeric_limits<unsigned>::max() - result.matching_qsos < record.qso_count ?
                std::numeric_limits<unsigned>::max() : result.matching_qsos + record.qso_count;
        }
    };
    std::uint32_t index = chain_head(call_lookup_,
        worked_hash(result.canonical_call, {}, {}, {}), result.canonical_call, true);
    while (index != 0U) {
        const auto& record = cells_[index - 1U];
        accumulate(record, true, false, true);
        index = record.next_call;
    }
    index = chain_head(entity_lookup_, worked_hash({}, result.canonical_entity, {}, {}),
                       result.canonical_entity, false);
    while (index != 0U) {
        const auto& record = cells_[index - 1U];
        const bool already_counted = !result.canonical_call.empty() &&
                                     result.canonical_call == record.call.data();
        accumulate(record, false, true, !already_counted);
        index = record.next_entity;
    }
    return result;
}

AwardProgressSummary WorkedIndex::award_progress(const AwardProgressQuery& query) const {
    struct Accumulator {
        std::set<std::string> calls;
        std::set<std::string> bands;
        std::set<std::string> modes;
        std::set<std::string> cells;
        unsigned qsos{};
        bool local{};
        bool wavelog{};
        std::int64_t first{std::numeric_limits<std::int64_t>::max()};
        std::int64_t last{};
    };
    std::set<std::string> wanted_bands;
    std::set<std::string> wanted_modes;
    for (const auto& value : query.bands) {
        const auto canonical = canonical_band(value);
        if (!canonical.empty() && wanted_bands.size() < kMaxAwardDimensions) wanted_bands.insert(canonical);
    }
    for (const auto& value : query.modes) {
        const auto canonical = canonical_mode(value);
        if (!canonical.empty() && wanted_modes.size() < kMaxAwardDimensions) wanted_modes.insert(canonical);
    }
    std::map<std::string, Accumulator> entities;
    for (const auto& record : cells_) {
        const std::string band(record.band.data());
        const std::string mode(record.mode.data());
        if (!wanted_bands.empty() && wanted_bands.count(band) == 0) continue;
        if (!wanted_modes.empty() && wanted_modes.count(mode) == 0) continue;
        auto& value = entities[record.entity.data()];
        value.calls.insert(record.call.data());
        value.bands.insert(band);
        value.modes.insert(mode);
        value.cells.insert(band + "|" + mode);
        value.local = value.local || record.found_local;
        value.wavelog = value.wavelog || record.found_wavelog;
        value.first = std::min(value.first, record.first_epoch);
        value.last = std::max(value.last, record.last_epoch);
        value.qsos = std::numeric_limits<unsigned>::max() - value.qsos < record.qso_count ?
            std::numeric_limits<unsigned>::max() : value.qsos + record.qso_count;
    }
    AwardProgressSummary output;
    output.index_complete = complete();
    output.truncated_records = truncated_count_;
    output.unique_entities = static_cast<unsigned>(entities.size());
    const std::size_t max_entities = std::max<std::size_t>(1,
        std::min(query.max_entities, kMaxAwardEntities));
    const unsigned target = static_cast<unsigned>((wanted_bands.empty() ? 0 : wanted_bands.size()) *
                                                   (wanted_modes.empty() ? 0 : wanted_modes.size()));
    for (const auto& [name, value] : entities) {
        if (output.entities.size() == max_entities) {
            ++output.truncated_entities;
            continue;
        }
        EntityProgress item;
        item.entity = name;
        item.qso_count = value.qsos;
        item.unique_calls = static_cast<unsigned>(value.calls.size());
        item.bands_worked = static_cast<unsigned>(value.bands.size());
        item.modes_worked = static_cast<unsigned>(value.modes.size());
        item.band_mode_cells_worked = static_cast<unsigned>(value.cells.size());
        item.band_mode_cells_target = target;
        item.coverage_percent = target == 0 ? 0U : std::min(100U, item.band_mode_cells_worked * 100U / target);
        item.found_local = value.local;
        item.found_wavelog = value.wavelog;
        item.first_qso_epoch = value.first == std::numeric_limits<std::int64_t>::max() ? 0 : value.first;
        item.last_qso_epoch = value.last;
        if (target != 0 && item.band_mode_cells_worked >= target) ++output.complete_entities;
        output.entities.push_back(std::move(item));
    }
    std::sort(output.entities.begin(), output.entities.end(), [](const auto& left, const auto& right) {
        if (left.coverage_percent != right.coverage_percent) return left.coverage_percent > right.coverage_percent;
        return left.entity < right.entity;
    });
    return output;
}

LogPrefill prefill_from_spot(const SpotPrefillInput& spot, const WorkedIndex& worked,
                             std::int64_t now_epoch, std::int64_t recent_dupe_seconds) {
    LogPrefill result;
    result.call = canonical_call(spot.call);
    result.entity = canonical_entity(spot.entity);
    result.band = canonical_band(spot.band);
    result.mode = canonical_mode(spot.mode);
    result.submode = (result.mode == "FT8" || result.mode == "FT4") ? result.mode : std::string{};
    if (!result.submode.empty()) result.mode = "MFSK";
    result.spot_epoch = spot.received_epoch;
    result.worked = worked.classify(spot.call, spot.entity, spot.band, spot.mode, {},
                                    now_epoch, recent_dupe_seconds);
    if (spot.frequency_hz != 0) {
        char frequency[32]{};
        std::snprintf(frequency, sizeof(frequency), "%.6f",
                      static_cast<double>(spot.frequency_hz) / 1000000.0);
        result.frequency_mhz = frequency;
    }
    result.comment = "PREFILLED FROM RECEIVED DX SPOT; REVIEW BEFORE SAVE";
    result.valid = !result.call.empty() && !result.band.empty() && !result.mode.empty() &&
                   !result.frequency_mhz.empty() && spot.frequency_hz >= 1000000ULL &&
                   spot.frequency_hz <= 6000000000ULL;
    return result;
}

std::optional<GeoPoint> maidenhead_center(std::string_view locator) {
    std::string value = upper_alnum(locator);
    if (value.size() != 2 && value.size() != 4 && value.size() != 6 && value.size() != 8) return std::nullopt;
    if (value[0] < 'A' || value[0] > 'R' || value[1] < 'A' || value[1] > 'R') return std::nullopt;
    double lon = -180.0 + (value[0] - 'A') * 20.0;
    double lat = -90.0 + (value[1] - 'A') * 10.0;
    double lon_width = 20.0;
    double lat_height = 10.0;
    if (value.size() >= 4) {
        if (!std::isdigit(static_cast<unsigned char>(value[2])) ||
            !std::isdigit(static_cast<unsigned char>(value[3]))) return std::nullopt;
        lon += (value[2] - '0') * 2.0;
        lat += (value[3] - '0') * 1.0;
        lon_width = 2.0;
        lat_height = 1.0;
    }
    if (value.size() >= 6) {
        if (value[4] < 'A' || value[4] > 'X' || value[5] < 'A' || value[5] > 'X') return std::nullopt;
        lon += (value[4] - 'A') * (2.0 / 24.0);
        lat += (value[5] - 'A') * (1.0 / 24.0);
        lon_width = 2.0 / 24.0;
        lat_height = 1.0 / 24.0;
    }
    if (value.size() == 8) {
        if (!std::isdigit(static_cast<unsigned char>(value[6])) ||
            !std::isdigit(static_cast<unsigned char>(value[7]))) return std::nullopt;
        lon += (value[6] - '0') * (2.0 / 240.0);
        lat += (value[7] - '0') * (1.0 / 240.0);
        lon_width = 2.0 / 240.0;
        lat_height = 1.0 / 240.0;
    }
    return GeoPoint{lat + lat_height / 2.0, lon + lon_width / 2.0};
}

std::string maidenhead_locator(const GeoPoint& point, unsigned precision) {
    if (!std::isfinite(point.latitude) || !std::isfinite(point.longitude) ||
        point.latitude < -90.0 || point.latitude > 90.0 ||
        point.longitude < -180.0 || point.longitude > 180.0) return {};
    precision = precision >= 6U ? 6U : 4U;
    double lon = std::min(359.999999, point.longitude + 180.0);
    double lat = std::min(179.999999, point.latitude + 90.0);
    std::string out;
    out.push_back(static_cast<char>('A' + static_cast<int>(lon / 20.0)));
    out.push_back(static_cast<char>('A' + static_cast<int>(lat / 10.0)));
    lon = std::fmod(lon, 20.0); lat = std::fmod(lat, 10.0);
    out.push_back(static_cast<char>('0' + static_cast<int>(lon / 2.0)));
    out.push_back(static_cast<char>('0' + static_cast<int>(lat)));
    if (precision >= 6U) {
        lon = std::fmod(lon, 2.0); lat = std::fmod(lat, 1.0);
        out.push_back(static_cast<char>('a' + std::min(23, static_cast<int>(lon * 12.0))));
        out.push_back(static_cast<char>('a' + std::min(23, static_cast<int>(lat * 24.0))));
    }
    return out;
}

double great_circle_distance_km(const GeoPoint& from, const GeoPoint& to) {
    const double dlat = radians(to.latitude - from.latitude);
    const double dlon = radians(to.longitude - from.longitude);
    const double from_lat = radians(from.latitude);
    const double to_lat = radians(to.latitude);
    const double a = std::sin(dlat / 2.0) * std::sin(dlat / 2.0) +
        std::cos(from_lat) * std::cos(to_lat) * std::sin(dlon / 2.0) * std::sin(dlon / 2.0);
    return kEarthRadiusKm * 2.0 * std::atan2(std::sqrt(a), std::sqrt(std::max(0.0, 1.0 - a)));
}

double initial_bearing_deg(const GeoPoint& from, const GeoPoint& to) {
    const double from_lat = radians(from.latitude);
    const double to_lat = radians(to.latitude);
    const double dlon = radians(to.longitude - from.longitude);
    return normalize_degrees(degrees(std::atan2(std::sin(dlon) * std::cos(to_lat),
        std::cos(from_lat) * std::sin(to_lat) -
        std::sin(from_lat) * std::cos(to_lat) * std::cos(dlon))));
}

double solar_elevation_deg(const GeoPoint& point, std::int64_t epoch) {
    const std::tm utc = utc_time(epoch);
    const double hour = utc.tm_hour + utc.tm_min / 60.0 + utc.tm_sec / 3600.0;
    const double gamma = 2.0 * kPi / 365.0 * (day_of_year(utc) - 1 + (hour - 12.0) / 24.0);
    const double equation_minutes = 229.18 * (0.000075 + 0.001868 * std::cos(gamma) -
        0.032077 * std::sin(gamma) - 0.014615 * std::cos(2 * gamma) -
        0.040849 * std::sin(2 * gamma));
    const double declination = 0.006918 - 0.399912 * std::cos(gamma) +
        0.070257 * std::sin(gamma) - 0.006758 * std::cos(2 * gamma) +
        0.000907 * std::sin(2 * gamma) - 0.002697 * std::cos(3 * gamma) +
        0.00148 * std::sin(3 * gamma);
    const double true_solar_minutes = hour * 60.0 + equation_minutes + 4.0 * point.longitude;
    const double hour_angle = radians(normalize_degrees(true_solar_minutes / 4.0 + 180.0) - 180.0);
    const double latitude = radians(point.latitude);
    const double cos_zenith = std::clamp(std::sin(latitude) * std::sin(declination) +
        std::cos(latitude) * std::cos(declination) * std::cos(hour_angle), -1.0, 1.0);
    return 90.0 - degrees(std::acos(cos_zenith));
}

LightState light_state(double elevation) {
    if (!std::isfinite(elevation)) return LightState::Unknown;
    if (elevation >= 0.0) return LightState::Day;
    if (elevation >= -12.0) return LightState::Greyline;
    return LightState::Night;
}

PropagationContext explain_propagation(const PropagationContextInput& input) {
    PropagationContext output;
    const auto station = maidenhead_center(input.station_grid);
    const auto target = maidenhead_center(input.target_grid);
    if (!station.has_value() || !target.has_value() || input.epoch <= 0) {
        add_reason(output, "VALID STATION AND TARGET LOCATORS REQUIRED");
        return output;
    }
    output.station = *station;
    output.target = *target;
    output.distance_km = great_circle_distance_km(*station, *target);
    output.initial_bearing_deg = initial_bearing_deg(*station, *target);
    output.station_solar_elevation_deg = solar_elevation_deg(*station, input.epoch);
    output.target_solar_elevation_deg = solar_elevation_deg(*target, input.epoch);
    output.station_light = light_state(output.station_solar_elevation_deg);
    output.target_light = light_state(output.target_solar_elevation_deg);
    output.observations = input.local_samples.observations;
    if (input.local_samples.observations == 0) output.sample_confidence = SampleConfidence::Insufficient;
    else if (input.local_samples.observations < 8) output.sample_confidence = SampleConfidence::Low;
    else if (input.local_samples.observations < 30) output.sample_confidence = SampleConfidence::Medium;
    else output.sample_confidence = SampleConfidence::High;
    output.local_success_percent = input.local_samples.observations == 0 ? 0U :
        std::min(100U, input.local_samples.favorable_observations * 100U /
                       input.local_samples.observations);
    if (!input.solar.valid || input.solar.observed_epoch <= 0) {
        output.solar_freshness = SolarFreshness::Missing;
        add_reason(output, "SOLAR DATA MISSING");
    } else {
        const std::int64_t age = input.epoch - input.solar.observed_epoch;
        output.solar_freshness = age >= 0 && age <= 3 * 3600 ? SolarFreshness::Fresh : SolarFreshness::Stale;
        add_reason(output, output.solar_freshness == SolarFreshness::Fresh ?
            "SOLAR SAMPLE FRESH" : "SOLAR SAMPLE STALE");
    }

    unsigned score = 25;
    const std::string band = canonical_band(input.band);
    const bool low_band = band == "160m" || band == "80m" || band == "60m" || band == "40m";
    const bool high_band = band == "20m" || band == "17m" || band == "15m" ||
                           band == "12m" || band == "10m" || band == "6m";
    if (output.station_light == LightState::Greyline || output.target_light == LightState::Greyline) {
        score += 12;
        add_reason(output, "ONE END NEAR GREYLINE");
    }
    if (low_band && output.station_light != LightState::Day && output.target_light != LightState::Day) {
        score += 15;
        add_reason(output, "LOW BAND DARKNESS AT BOTH ENDS");
    }
    if (high_band && output.station_light == LightState::Day && output.target_light == LightState::Day) {
        score += 10;
        add_reason(output, "DAYLIGHT AT BOTH ENDS");
    }
    if (output.solar_freshness == SolarFreshness::Fresh) {
        if (high_band && input.solar.solar_flux >= 110.0F) {
            score += 10;
            add_reason(output, "ELEVATED SOLAR FLUX SUPPORTS HIGH-BAND CONTEXT");
        }
        if (input.solar.kp_index >= 5.0F) {
            score = score > 15 ? score - 15 : 0;
            add_reason(output, "ELEVATED KP MAY DISRUPT PATHS");
        } else if (input.solar.kp_index <= 3.0F) {
            score += 5;
            add_reason(output, "QUIET GEOMAGNETIC CONTEXT");
        }
    }
    if (output.sample_confidence == SampleConfidence::Insufficient) {
        add_reason(output, "NO LOCAL OBSERVATION BASIS");
    } else {
        const unsigned weight = output.sample_confidence == SampleConfidence::Low ? 5U :
                                output.sample_confidence == SampleConfidence::Medium ? 12U : 20U;
        score += output.local_success_percent * weight / 100U;
        add_reason(output, "LOCAL SAMPLE RATE INCLUDED");
    }
    output.context_score = std::min(100U, score);
    output.valid = !band.empty();
    return output;
}

}  // namespace kx3::intel
