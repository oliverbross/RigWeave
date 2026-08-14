#include "kx3/protocol.hpp"

#include <algorithm>
#include <cctype>
#include <cstdio>

namespace kx3 {
namespace {

constexpr std::array<CommandSpec, 12> kCommands{{
    {"ID", SafetyClass::ReadOnly, true, false, "", 150},
    {"FA", SafetyClass::AbsoluteSet, true, true, "FA;", 150},
    {"FB", SafetyClass::AbsoluteSet, true, true, "FB;", 150},
    {"MD", SafetyClass::AbsoluteSet, true, true, "MD;", 150},
    {"IF", SafetyClass::ReadOnly, true, false, "", 150},
    {"TQ", SafetyClass::ReadOnly, true, false, "", 150},
    {"AI", SafetyClass::AbsoluteSet, true, true, "AI;", 150},
    {"SM", SafetyClass::ReadOnly, true, false, "", 150},
    {"SWT", SafetyClass::EdgeTriggered, false, true, "IF;", 600},
    {"SWH", SafetyClass::EdgeTriggered, false, true, "IF;", 600},
    {"TX", SafetyClass::Transmit, false, true, "TQ;", 600},
    {"RX", SafetyClass::Transmit, false, true, "TQ;", 600},
}};

bool all_digits(const std::string_view value) {
    return std::all_of(value.begin(), value.end(), [](const char c) {
        return std::isdigit(static_cast<unsigned char>(c)) != 0;
    });
}

}  // namespace

const CommandSpec* find_command(const std::string_view prefix) {
    const auto found = std::find_if(kCommands.begin(), kCommands.end(), [prefix](const auto& entry) {
        return entry.prefix == prefix;
    });
    return found == kCommands.end() ? nullptr : &*found;
}

std::optional<std::uint64_t> decode_frequency(const std::string_view frame,
                                              const std::string_view prefix) {
    if (prefix.size() != 2 || frame.size() != 14 || frame.substr(0, 2) != prefix ||
        frame.back() != ';' || !all_digits(frame.substr(2, 11))) {
        return std::nullopt;
    }
    std::uint64_t result = 0;
    for (const char digit : frame.substr(2, 11)) {
        result = result * 10 + static_cast<std::uint64_t>(digit - '0');
    }
    return result;
}

std::optional<std::uint8_t> decode_mode(const std::string_view frame) {
    if (frame.size() != 4 || frame.substr(0, 2) != "MD" || frame[3] != ';' ||
        frame[2] < '1' || frame[2] > '9') {
        return std::nullopt;
    }
    return static_cast<std::uint8_t>(frame[2] - '0');
}

std::optional<bool> decode_tx(const std::string_view frame) {
    if (frame == "TQ0;") return false;
    if (frame == "TQ1;") return true;
    return std::nullopt;
}

char decode_vfo_b_character(const char value) {
    // DB uses lower-case c as the KX3 display placeholder for a slashed zero.
    return value == 'c' ? '0' : value;
}

std::string encode_frequency(const std::string_view prefix, const std::uint64_t hz) {
    if ((prefix != "FA" && prefix != "FB") || hz > 99999999999ULL) return {};
    std::array<char, 15> output{};
    std::snprintf(output.data(), output.size(), "%.*s%011llu;", static_cast<int>(prefix.size()),
                  prefix.data(), static_cast<unsigned long long>(hz));
    return output.data();
}

bool is_valid_response(const std::string_view frame, const std::string_view prefix) {
    if (frame.size() < prefix.size() + 1 || frame.back() != ';' ||
        frame.substr(0, prefix.size()) != prefix) return false;
    if (prefix == "FA" || prefix == "FB") return decode_frequency(frame, prefix).has_value();
    if (prefix == "MD") return decode_mode(frame).has_value();
    if (prefix == "TQ") return decode_tx(frame).has_value();
    return frame.size() > prefix.size() + 1;
}

BaudProbe::Result BaudProbe::accept_frame(const std::string_view frame) {
    if (!confirmation_) {
        if (!is_valid_response(frame, "ID")) return Result::Continue;
        confirmation_ = true;
        return Result::ConfirmCandidate;
    }
    return is_valid_response(frame, "FA") ? Result::Accepted : Result::Continue;
}

BaudProbe::Result BaudProbe::timeout() {
    confirmation_ = false;
    if (index_ + 1 >= kCandidates.size()) return Result::Exhausted;
    ++index_;
    return Result::Continue;
}

void BaudProbe::reset() {
    index_ = 0;
    confirmation_ = false;
}

}  // namespace kx3
