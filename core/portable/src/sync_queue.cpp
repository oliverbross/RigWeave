#include "kx3/sync_queue.hpp"

#include <algorithm>
#include <cctype>

namespace kx3 {
namespace {

const char* state_name(const SyncState state) {
    switch (state) {
        case SyncState::Pending: return "PENDING";
        case SyncState::InFlight: return "IN_FLIGHT";
        case SyncState::Acknowledged: return "ACKNOWLEDGED";
        case SyncState::RetryWait: return "RETRY_WAIT";
        case SyncState::Quarantined: return "QUARANTINED";
        case SyncState::Cancelled: return "CANCELLED";
    }
    return "QUARANTINED";
}

std::string safe_json(const std::string_view value) {
    std::string output;
    for (const char c : value) {
        if (c == '"' || c == '\\') output.push_back('\\');
        if (static_cast<unsigned char>(c) >= 0x20) output.push_back(c);
    }
    return output;
}

}  // namespace

RetryAction classify_http_result(const int status_code, const bool network_error,
                                 const bool response_ambiguous) {
    if (response_ambiguous) return RetryAction::Inspect;
    if (network_error || status_code == 429 || status_code >= 500) return RetryAction::Retry;
    if (status_code >= 200 && status_code < 300) return RetryAction::Acknowledge;
    if (status_code == 401 || status_code == 403) return RetryAction::StopForAuth;
    if (status_code >= 400 && status_code < 500) return RetryAction::Quarantine;
    return RetryAction::Inspect;
}

std::uint32_t retry_delay_seconds(const std::uint32_t attempt, const std::uint32_t jitter_seed,
                                  const std::optional<std::uint32_t> retry_after) {
    if (retry_after.has_value()) return std::min<std::uint32_t>(*retry_after, 86400);
    const auto shift = std::min<std::uint32_t>(attempt, 10);
    const auto base = std::min<std::uint32_t>(static_cast<std::uint32_t>(5) << shift, 3600);
    return std::min<std::uint32_t>(base + (jitter_seed % (base / 4 + 1)), 3600);
}

std::string serialize_sync_event(const SyncEvent& event) {
    return "{\"uuid\":\"" + safe_json(event.uuid) + "\",\"state\":\"" +
           state_name(event.state) + "\",\"attempt\":" + std::to_string(event.attempt) +
           ",\"timestamp_ms\":" + std::to_string(event.timestamp_ms) +
           ",\"detail\":\"" + safe_json(event.safe_detail) + "\"}";
}

std::string normalize_wavelog_url(const std::string_view value) {
    const auto first = std::find_if_not(value.begin(), value.end(),
        [](const unsigned char c) { return std::isspace(c) != 0; });
    const auto last = std::find_if_not(value.rbegin(), value.rend(),
        [](const unsigned char c) { return std::isspace(c) != 0; }).base();
    if (first >= last) return {};

    std::string normalized(first, last);
    if (normalized.rfind("htps://", 0) == 0) {
        normalized.insert(1, "t");
    } else if (normalized.rfind("http://", 0) == 0) {
        normalized.replace(0, 7, "https://");
    } else if (normalized.find("://") == std::string::npos) {
        normalized.insert(0, "https://");
    }
    while (normalized.size() > 8 && normalized.back() == '/') normalized.pop_back();
    return normalized;
}

}  // namespace kx3
