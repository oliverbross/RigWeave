#pragma once

#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

namespace kx3 {

enum class SafetyClass : std::uint8_t { ReadOnly, AbsoluteSet, EdgeTriggered, Transmit };

struct CommandSpec {
    std::string_view prefix;
    SafetyClass safety;
    bool idempotent;
    bool requires_confirmation;
    std::string_view verification_query;
    std::uint16_t timeout_ms;
};

const CommandSpec* find_command(std::string_view prefix);
std::optional<std::uint64_t> decode_frequency(std::string_view frame, std::string_view prefix);
std::optional<std::uint8_t> decode_mode(std::string_view frame);
std::optional<bool> decode_tx(std::string_view frame);
char decode_vfo_b_character(char value);
std::string encode_frequency(std::string_view prefix, std::uint64_t hz);
bool is_valid_response(std::string_view frame, std::string_view prefix);

class BaudProbe {
public:
    enum class Result : std::uint8_t { Continue, ConfirmCandidate, Accepted, Exhausted };
    static constexpr std::array<std::uint32_t, 4> kCandidates{38400, 19200, 9600, 4800};

    std::uint32_t current_baud() const { return kCandidates[index_]; }
    std::string_view query() const { return confirmation_ ? "FA;" : "ID;"; }
    Result accept_frame(std::string_view frame);
    Result timeout();
    void reset();

private:
    std::size_t index_{0};
    bool confirmation_{false};
};

}  // namespace kx3
