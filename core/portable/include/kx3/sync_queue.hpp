#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

namespace kx3 {

enum class SyncState : std::uint8_t {
    Pending,
    InFlight,
    Acknowledged,
    RetryWait,
    Quarantined,
    Cancelled
};

enum class RetryAction : std::uint8_t { Acknowledge, Retry, StopForAuth, Quarantine, Inspect };

struct SyncEvent {
    std::string uuid;
    SyncState state;
    std::uint32_t attempt{};
    std::uint64_t timestamp_ms{};
    std::string safe_detail;
};

RetryAction classify_http_result(int status_code, bool network_error, bool response_ambiguous);
std::uint32_t retry_delay_seconds(std::uint32_t attempt, std::uint32_t jitter_seed,
                                  std::optional<std::uint32_t> retry_after);
std::string serialize_sync_event(const SyncEvent& event);
std::string normalize_wavelog_url(std::string_view value);

}  // namespace kx3
