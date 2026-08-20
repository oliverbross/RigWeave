#pragma once

#include <array>
#include <cstdint>
#include <deque>
#include <functional>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_set>
#include <vector>

namespace kx3 {

constexpr std::size_t kDxBandCount = 16;
constexpr std::size_t kDxOpportunityCount = 40;
constexpr std::size_t kDxLiveCount = 64;
constexpr std::size_t kDxTimelineBuckets = 12;
constexpr std::size_t kDxRegionCount = 7;
constexpr std::size_t kDxWorldRows = 5;
constexpr std::size_t kDxWorldColumns = 12;

struct DxSpot {
    std::uint64_t frequency_hz{};
    std::int64_t received_epoch{};
    std::string callsign;
    std::string spotter;
    std::string comment;
    std::string country;
    std::string band;
    std::string mode;
    std::string continent;
    int cq_zone{};
    int itu_zone{};
    float latitude{};
    float longitude{};
};

struct SolarReading {
    float solar_flux{};
    float a_index{};
    float kp_index{};
    std::int64_t observed_epoch{};
    bool valid{};
};

struct DxBandActivity {
    std::string band;
    unsigned spots_5m{};
    unsigned spots_60m{};
    unsigned unique_calls_60m{};
    unsigned surge_percent{};
    bool surge{};
};

struct DxWorkedState {
    bool entity_any{};
    bool entity_band{};
    bool entity_mode{};
    bool entity_band_mode{};
    bool call_any{};
    bool call_band{};
    bool call_mode{};
    bool call_band_mode{};
    bool recent_dupe{};
    bool index_loaded{};
    bool index_complete{};
};

using DxWorkedClassifier = std::function<DxWorkedState(const DxSpot&)>;

struct DxOpportunity {
    DxSpot spot;
    unsigned score{};
    unsigned confidence{};
    unsigned samples{};
    bool watchlisted{};
    bool worked_country{};
    bool worked_call{};
    bool worked_band{};
    bool worked_mode{};
    bool worked_band_mode{};
    bool recent_dupe{};
    bool worked_index_complete{};
    unsigned distance_km{};
    unsigned bearing_degrees{};
    unsigned propagation_context_score{};
    std::string path_state;
    std::string reason;
};

struct DxRegionActivity {
    std::string region;
    unsigned spots_15m{};
    unsigned spots_60m{};
    unsigned unique_calls_60m{};
    unsigned activity_percent{};
    bool anomaly{};
};

struct DxInsightSnapshot {
    std::array<DxBandActivity, kDxBandCount> bands{};
    std::vector<DxOpportunity> opportunities;
    std::vector<DxOpportunity> live_spots;
    std::vector<DxOpportunity> watch_activity;
    std::array<std::array<unsigned, kDxTimelineBuckets>, kDxBandCount> band_timeline{};
    std::array<DxRegionActivity, kDxRegionCount> regions{};
    std::array<std::array<unsigned, kDxWorldColumns>, kDxWorldRows> world_grid{};
    SolarReading solar;
    unsigned spots_5m{};
    unsigned spots_60m{};
    unsigned learned_spots{};
    unsigned duplicate_spots{};
    unsigned watchlist_hits{};
    unsigned surging_bands{};
    std::int64_t newest_spot_epoch{};
};

class DxInsightEngine {
public:
    // The full cluster journal remains on SD. RAM keeps a bounded analytical
    // working set; 1,536 spots is enough for the rolling rate context without
    // allowing thousands of small string allocations to exhaust internal RAM.
    static constexpr std::size_t kCapacity = 1536;

    bool ingest(DxSpot spot);
    void set_watchlist(std::vector<std::string> callsigns);
    void update_solar(const SolarReading& reading);
    DxInsightSnapshot evaluate(std::int64_t now_epoch,
                               const DxWorkedClassifier& classify_worked = {}) const;
    std::size_t size() const { return spots_.size(); }
    unsigned duplicate_count() const { return duplicate_count_; }

private:
    std::deque<DxSpot> spots_;
    std::unordered_set<std::string> watchlist_;
    SolarReading solar_{};
    unsigned duplicate_count_{};
};

std::vector<std::string> parse_dx_watchlist(std::string_view text);
std::string serialize_dx_watchlist(const std::vector<std::string>& callsigns);
bool dx_live_filter_matches(std::string_view band, std::string_view mode, bool worked_entity,
                            std::uint32_t band_mask, std::uint32_t mode_mask,
                            std::uint32_t entity_mask);
std::optional<SolarReading> parse_noaa_wwv(std::string_view text,
                                           std::int64_t observed_epoch);
std::optional<float> parse_noaa_kp(std::string_view text);

}  // namespace kx3
