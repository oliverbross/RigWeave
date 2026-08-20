#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

namespace kx3 {

constexpr std::uint64_t kDxMinimumFrequencyHz = 1'000'000ULL;
constexpr std::uint64_t kDxMaximumFrequencyHz = 10'500'000'000ULL;

struct ClusterSpot {
    std::uint64_t frequency_hz{};
    std::string callsign;
    std::string spotter;
    std::string comment;
    std::string band;
    std::string mode;
    std::int64_t received_epoch{};
};

std::optional<ClusterSpot> parse_cluster_spot(std::string_view line,
                                              std::int64_t now_epoch);
std::string clean_spot_comment(std::string_view comment);
std::string spot_band(std::uint64_t frequency_hz);
std::string spot_mode(std::uint64_t frequency_hz, std::string_view comment);

}  // namespace kx3
