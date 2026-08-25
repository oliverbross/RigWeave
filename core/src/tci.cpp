// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/tci.hpp"

#include <algorithm>
#include <array>
#include <charconv>
#include <cmath>
#include <cstring>
#include <limits>

namespace rigweave::tci {
namespace {

std::string trim(std::string_view value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string_view::npos) return {};
    const auto last = value.find_last_not_of(" \t\r\n");
    return std::string(value.substr(first, last - first + 1U));
}

std::string lower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char byte) {
        return static_cast<char>(byte >= 'A' && byte <= 'Z' ? byte + ('a' - 'A') : byte);
    });
    return value;
}

bool receiver_valid(std::uint32_t receiver) {
    return receiver < DefaultMaximumReceivers;
}

bool sample_rate_valid(std::uint32_t sample_rate) {
    return sample_rate >= 8'000U && sample_rate <= 10'000'000U;
}

std::uint32_t read_u32_le(const std::uint8_t *bytes) {
    return static_cast<std::uint32_t>(bytes[0]) |
           (static_cast<std::uint32_t>(bytes[1]) << 8U) |
           (static_cast<std::uint32_t>(bytes[2]) << 16U) |
           (static_cast<std::uint32_t>(bytes[3]) << 24U);
}

void write_u32_le(std::vector<std::uint8_t> &output, std::uint32_t value) {
    output.push_back(static_cast<std::uint8_t>(value & 0xffU));
    output.push_back(static_cast<std::uint8_t>((value >> 8U) & 0xffU));
    output.push_back(static_cast<std::uint8_t>((value >> 16U) & 0xffU));
    output.push_back(static_cast<std::uint8_t>((value >> 24U) & 0xffU));
}

void set_error(BinaryError *error, BinaryError value) {
    if (error != nullptr) *error = value;
}

std::optional<DataType> data_type(std::uint32_t value) {
    switch (value) {
    case 0U: return DataType::Iq;
    case 1U: return DataType::RxAudio;
    case 2U: return DataType::TxAudio;
    case 3U: return DataType::TxChrono;
    default: return std::nullopt;
    }
}

std::optional<std::string> receiver_command(std::string_view name, std::uint32_t receiver) {
    if (!receiver_valid(receiver)) return std::nullopt;
    return std::string(name) + ':' + std::to_string(receiver) + ';';
}

} // namespace

std::vector<StatusCommand> parse_status(std::string_view text) {
    std::vector<StatusCommand> commands;
    std::size_t start = 0U;
    while (start < text.size()) {
        const std::size_t end = text.find(';', start);
        const std::string part = trim(text.substr(start, end == std::string_view::npos
            ? text.size() - start
            : end - start));
        if (!part.empty()) {
            const std::size_t separator = part.find(':');
            const std::string name = lower(trim(std::string_view(part).substr(0U, separator)));
            if (!name.empty() && std::all_of(name.begin(), name.end(), [](unsigned char byte) {
                    return (byte >= 'a' && byte <= 'z') || byte == '_';
                })) {
                commands.push_back({name, separator == std::string::npos
                    ? std::string{}
                    : trim(std::string_view(part).substr(separator + 1U))});
            }
        }
        if (end == std::string_view::npos) break;
        start = end + 1U;
    }
    return commands;
}

std::optional<std::string> canonical_mode(std::string_view mode) {
    const std::string value = lower(trim(mode));
    static constexpr std::array<std::string_view, 12> modes{
        "lsb", "usb", "cw", "am", "sam", "nfm", "wfm", "digu", "digl", "dsb", "ft8", "ft4"};
    if (std::find(modes.begin(), modes.end(), value) == modes.end()) return std::nullopt;
    if (value == "ft8" || value == "ft4") return std::string("digu");
    return value;
}

std::optional<std::string> build_vfo(std::uint32_t receiver, std::uint32_t channel,
                                     std::uint64_t frequency_hz) {
    if (!receiver_valid(receiver) || channel > 1U || frequency_hz < 100'000U ||
        frequency_hz > 10'500'000'000ULL) return std::nullopt;
    return "vfo:" + std::to_string(receiver) + ',' + std::to_string(channel) + ',' +
           std::to_string(frequency_hz) + ';';
}

std::optional<std::string> build_if(std::uint32_t receiver, std::uint32_t channel,
                                    std::int64_t offset_hz) {
    if (!receiver_valid(receiver) || channel > 1U || offset_hz < -10'000'000LL ||
        offset_hz > 10'000'000LL) return std::nullopt;
    return "if:" + std::to_string(receiver) + ',' + std::to_string(channel) + ',' +
           std::to_string(offset_hz) + ';';
}

std::optional<std::string> build_mode(std::uint32_t receiver, std::string_view mode) {
    const auto canonical = canonical_mode(mode);
    if (!receiver_valid(receiver) || !canonical) return std::nullopt;
    return "modulation:" + std::to_string(receiver) + ',' + *canonical + ';';
}

std::optional<std::string> build_iq_sample_rate(std::uint32_t sample_rate) {
    if (!sample_rate_valid(sample_rate)) return std::nullopt;
    return "iq_samplerate:" + std::to_string(sample_rate) + ';';
}

std::optional<std::string> build_iq_start(std::uint32_t receiver) {
    return receiver_command("iq_start", receiver);
}

std::optional<std::string> build_iq_stop(std::uint32_t receiver) {
    return receiver_command("iq_stop", receiver);
}

std::optional<std::string> build_audio_start(std::uint32_t receiver) {
    return receiver_command("audio_start", receiver);
}

std::optional<std::string> build_audio_stop(std::uint32_t receiver) {
    return receiver_command("audio_stop", receiver);
}

std::optional<std::string> build_rx_enable(std::uint32_t receiver, bool enabled) {
    if (!receiver_valid(receiver)) return std::nullopt;
    return "rx_enable:" + std::to_string(receiver) + ',' + (enabled ? "true;" : "false;");
}

std::optional<std::string> build_mute(std::uint32_t receiver, bool muted) {
    if (!receiver_valid(receiver)) return std::nullopt;
    return "mute:" + std::to_string(receiver) + ',' + (muted ? "true;" : "false;");
}

std::optional<std::string> build_safe_stop(std::uint32_t receiver) {
    if (!receiver_valid(receiver)) return std::nullopt;
    return "trx:" + std::to_string(receiver) + ",false;tune:" + std::to_string(receiver) + ",false;";
}

std::optional<BinaryFrame> decode_binary(const std::uint8_t *message,
                                         std::size_t message_size,
                                         BinaryError *error,
                                         std::uint32_t maximum_receivers,
                                         std::size_t maximum_message_bytes) {
    set_error(error, BinaryError::None);
    if (message == nullptr || message_size < BinaryHeaderBytes) {
        set_error(error, BinaryError::HeaderTooShort);
        return std::nullopt;
    }
    if (message_size > maximum_message_bytes) {
        set_error(error, BinaryError::MessageTooLarge);
        return std::nullopt;
    }

    const std::uint32_t receiver = read_u32_le(message);
    const std::uint32_t sample_rate = read_u32_le(message + 4U);
    const std::uint32_t format = read_u32_le(message + 8U);
    const std::uint32_t value_count = read_u32_le(message + 20U);
    const auto type = data_type(read_u32_le(message + 24U));
    const std::uint32_t channels = read_u32_le(message + 28U);

    if (maximum_receivers == 0U || receiver >= maximum_receivers) {
        set_error(error, BinaryError::ReceiverOutOfRange);
        return std::nullopt;
    }
    if (format != Float32Format) {
        set_error(error, BinaryError::UnsupportedFormat);
        return std::nullopt;
    }
    if (!sample_rate_valid(sample_rate)) {
        set_error(error, BinaryError::InvalidSampleRate);
        return std::nullopt;
    }
    if (channels != 2U) {
        set_error(error, BinaryError::InvalidChannels);
        return std::nullopt;
    }
    if (!type) {
        set_error(error, BinaryError::UnknownDataType);
        return std::nullopt;
    }
    constexpr std::size_t maximum_value_count =
        (std::numeric_limits<std::size_t>::max() - BinaryHeaderBytes) / sizeof(float);
    if constexpr (maximum_value_count < std::numeric_limits<std::uint32_t>::max()) {
        if (value_count > maximum_value_count) {
            set_error(error, BinaryError::LengthOverflow);
            return std::nullopt;
        }
    }

    const std::size_t expected = BinaryHeaderBytes + static_cast<std::size_t>(value_count) * sizeof(float);
    const bool chrono = *type == DataType::TxChrono;
    if ((!chrono && expected != message_size) || (chrono && message_size != BinaryHeaderBytes)) {
        set_error(error, BinaryError::PayloadLengthMismatch);
        return std::nullopt;
    }

    BinaryFrame frame{{receiver, sample_rate, format, value_count, *type, channels}, {}};
    if (!chrono) {
        frame.values.reserve(value_count);
        for (std::size_t index = 0; index < value_count; ++index) {
            const std::uint32_t bits = read_u32_le(message + BinaryHeaderBytes + index * sizeof(float));
            float value{};
            static_assert(sizeof(value) == sizeof(bits));
            std::memcpy(&value, &bits, sizeof(value));
            if (!std::isfinite(value)) {
                set_error(error, BinaryError::NonFiniteSample);
                return std::nullopt;
            }
            frame.values.push_back(value);
        }
    }
    return frame;
}

std::vector<std::uint8_t> build_binary_for_test(DataType type,
                                                std::uint32_t receiver,
                                                std::uint32_t sample_rate,
                                                std::uint32_t channels,
                                                const std::vector<float> &values,
                                                std::uint32_t chrono_requested_values) {
    std::array<std::uint32_t, 16> header{};
    header[0] = receiver;
    header[1] = sample_rate;
    header[2] = Float32Format;
    header[5] = type == DataType::TxChrono ? chrono_requested_values
                                           : static_cast<std::uint32_t>(values.size());
    header[6] = static_cast<std::uint32_t>(type);
    header[7] = channels;
    std::vector<std::uint8_t> output;
    output.reserve(BinaryHeaderBytes + values.size() * sizeof(float));
    for (const std::uint32_t word : header) write_u32_le(output, word);
    if (type != DataType::TxChrono) {
        for (const float value : values) {
            std::uint32_t bits{};
            std::memcpy(&bits, &value, sizeof(bits));
            write_u32_le(output, bits);
        }
    }
    return output;
}

} // namespace rigweave::tci
