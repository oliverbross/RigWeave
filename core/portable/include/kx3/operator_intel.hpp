#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace kx3::intel {

constexpr std::size_t kDefaultWorkedCells = 65536;
constexpr std::size_t kMaxWorkedCells = 65536;
constexpr std::size_t kMaxAwardEntities = 256;
constexpr std::size_t kMaxAwardDimensions = 16;
constexpr std::size_t kMaxContextReasons = 8;

enum class LogSource : std::uint8_t { Local = 1, Wavelog = 2 };

struct WorkedRecord {
    std::string call;
    std::string entity;
    std::string band;
    std::string mode;
    std::string submode;
    std::int64_t epoch{};
    LogSource source{LogSource::Local};
};

struct WorkedClassification {
    std::string canonical_call;
    std::string canonical_entity;
    std::string canonical_band;
    std::string canonical_mode;
    bool entity_any{};
    bool entity_band{};
    bool entity_mode{};
    bool entity_band_mode{};
    bool call_any{};
    bool call_band{};
    bool call_mode{};
    bool call_band_mode{};
    bool recent_dupe_any{};
    bool recent_dupe_band_mode{};
    bool found_local{};
    bool found_wavelog{};
    unsigned matching_qsos{};
    std::int64_t last_qso_epoch{};
    bool index_complete{};
    unsigned truncated_records{};
};

struct AwardProgressQuery {
    std::vector<std::string> bands;
    std::vector<std::string> modes;
    std::size_t max_entities{kMaxAwardEntities};
};

struct EntityProgress {
    std::string entity;
    unsigned qso_count{};
    unsigned unique_calls{};
    unsigned bands_worked{};
    unsigned modes_worked{};
    unsigned band_mode_cells_worked{};
    unsigned band_mode_cells_target{};
    unsigned coverage_percent{};
    bool found_local{};
    bool found_wavelog{};
    std::int64_t first_qso_epoch{};
    std::int64_t last_qso_epoch{};
};

struct AwardProgressSummary {
    std::vector<EntityProgress> entities;
    unsigned unique_entities{};
    unsigned complete_entities{};
    unsigned truncated_entities{};
    bool index_complete{};
    unsigned truncated_records{};
};

class WorkedIndex {
public:
    explicit WorkedIndex(std::size_t capacity = kDefaultWorkedCells);
    bool add(WorkedRecord record);
    std::size_t add_all(const std::vector<WorkedRecord>& records);
    void clear();
    std::size_t size() const { return cells_.size(); }
    std::size_t capacity() const { return capacity_; }
    std::size_t allocated_bytes() const;
    std::size_t used_bytes() const;
    unsigned rejected_count() const { return rejected_count_; }
    unsigned truncated_count() const { return truncated_count_; }
    bool complete() const { return truncated_count_ == 0; }

    WorkedClassification classify(std::string_view call, std::string_view entity,
                                  std::string_view band, std::string_view mode,
                                  std::string_view submode, std::int64_t now_epoch,
                                  std::int64_t recent_dupe_seconds = 1800) const;
    AwardProgressSummary award_progress(const AwardProgressQuery& query = {}) const;

private:
    struct WorkedCell {
        std::array<char, 17> call{};
        std::array<char, 81> entity{};
        std::array<char, 9> band{};
        std::array<char, 13> mode{};
        std::int64_t first_epoch{};
        std::int64_t last_epoch{};
        unsigned qso_count{};
        std::uint32_t next_call{};
        std::uint32_t next_entity{};
        bool found_local{};
        bool found_wavelog{};
    };
    std::size_t capacity_;
    std::vector<WorkedCell> cells_;
    std::vector<std::uint32_t> lookup_;
    std::vector<std::uint32_t> call_lookup_;
    std::vector<std::uint32_t> entity_lookup_;
    unsigned rejected_count_{};
    unsigned truncated_count_{};
};

struct SpotPrefillInput {
    std::string call;
    std::string entity;
    std::string band;
    std::string mode;
    std::uint64_t frequency_hz{};
    std::int64_t received_epoch{};
};

struct LogPrefill {
    std::string call;
    std::string entity;
    std::string band;
    std::string mode;
    std::string submode;
    std::string frequency_mhz;
    std::string comment;
    std::int64_t spot_epoch{};
    WorkedClassification worked;
    bool valid{};
};

LogPrefill prefill_from_spot(const SpotPrefillInput& spot, const WorkedIndex& worked,
                             std::int64_t now_epoch,
                             std::int64_t recent_dupe_seconds = 1800);

std::string canonical_call(std::string_view call);
std::string canonical_entity(std::string_view entity);
std::string canonical_band(std::string_view band);
std::string canonical_mode(std::string_view mode, std::string_view submode = {});

struct GeoPoint {
    double latitude{};
    double longitude{};
};

enum class LightState : std::uint8_t { Day, Greyline, Night, Unknown };
enum class SolarFreshness : std::uint8_t { Fresh, Stale, Missing };
enum class SampleConfidence : std::uint8_t { Insufficient, Low, Medium, High };

struct SolarContextInput {
    bool valid{};
    float solar_flux{};
    float kp_index{};
    std::int64_t observed_epoch{};
};

struct LocalSampleInput {
    unsigned observations{};
    unsigned favorable_observations{};
};

struct PropagationContextInput {
    std::string station_grid;
    std::string target_grid;
    std::string band;
    std::int64_t epoch{};
    SolarContextInput solar;
    LocalSampleInput local_samples;
};

struct PropagationContext {
    GeoPoint station;
    GeoPoint target;
    double distance_km{};
    double initial_bearing_deg{};
    double station_solar_elevation_deg{};
    double target_solar_elevation_deg{};
    LightState station_light{LightState::Unknown};
    LightState target_light{LightState::Unknown};
    SolarFreshness solar_freshness{SolarFreshness::Missing};
    SampleConfidence sample_confidence{SampleConfidence::Insufficient};
    unsigned local_success_percent{};
    unsigned context_score{};
    unsigned observations{};
    std::vector<std::string> reasons;
    std::string label{"CONTEXT ONLY"};
    bool valid{};
};

std::optional<GeoPoint> maidenhead_center(std::string_view locator);
std::string maidenhead_locator(const GeoPoint& point, unsigned precision = 6);
double great_circle_distance_km(const GeoPoint& from, const GeoPoint& to);
double initial_bearing_deg(const GeoPoint& from, const GeoPoint& to);
double solar_elevation_deg(const GeoPoint& point, std::int64_t epoch);
LightState light_state(double solar_elevation_degrees);
PropagationContext explain_propagation(const PropagationContextInput& input);

}  // namespace kx3::intel
