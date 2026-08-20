#include "kx3/spot.hpp"

#include <algorithm>
#include <array>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <utility>

namespace kx3 {
namespace {

std::string trim(std::string_view value) {
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.front())))
        value.remove_prefix(1);
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.back())))
        value.remove_suffix(1);
    return std::string(value);
}

std::string upper(std::string_view value) {
    std::string result(value);
    std::transform(result.begin(), result.end(), result.begin(), [](unsigned char c) {
        return static_cast<char>(std::toupper(c));
    });
    return result;
}

bool starts_with_ci(std::string_view value, std::string_view prefix) {
    if (value.size() < prefix.size()) return false;
    for (std::size_t i = 0; i < prefix.size(); ++i)
        if (std::toupper(static_cast<unsigned char>(value[i])) !=
            std::toupper(static_cast<unsigned char>(prefix[i]))) return false;
    return true;
}

std::string_view next_token(std::string_view& value) {
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.front())))
        value.remove_prefix(1);
    const auto end = value.find_first_of(" \t\r\n");
    const auto token = value.substr(0, end);
    value = end == std::string_view::npos ? std::string_view{} : value.substr(end + 1);
    return token;
}

std::optional<std::uint64_t> frequency_hz(std::string_view token) {
    const std::string text(token);
    char* end{};
    const double khz = std::strtod(text.c_str(), &end);
    if (end == text.c_str() || *end != '\0' || !std::isfinite(khz)) return std::nullopt;
    const auto hz = static_cast<std::uint64_t>(std::llround(khz * 1000.0));
    if (hz < kDxMinimumFrequencyHz || hz > kDxMaximumFrequencyHz) return std::nullopt;
    return hz;
}

std::int64_t days_from_civil(int year, unsigned month, unsigned day) {
    year -= month <= 2;
    const int era = (year >= 0 ? year : year - 399) / 400;
    const unsigned yoe = static_cast<unsigned>(year - era * 400);
    const unsigned shifted_month = month > 2U ? month - 3U : month + 9U;
    const unsigned doy = (153U * shifted_month + 2U) / 5U + day - 1U;
    const unsigned doe = yoe * 365U + yoe / 4U - yoe / 100U + doy;
    return static_cast<std::int64_t>(era) * 146097 + static_cast<int>(doe) - 719468;
}

std::optional<std::int64_t> cluster_timestamp(std::string_view date,
                                               std::string_view time) {
    const auto first_dash = date.find('-');
    const auto second_dash = first_dash == std::string_view::npos ? std::string_view::npos :
                             date.find('-', first_dash + 1U);
    if ((first_dash != 1U && first_dash != 2U) || second_dash == std::string_view::npos ||
        second_dash != first_dash + 4U || date.size() != second_dash + 5U ||
        time.size() != 5U || std::toupper(static_cast<unsigned char>(time[4])) != 'Z' ||
        !std::all_of(date.begin(), date.begin() + first_dash, [](unsigned char c) { return std::isdigit(c); }) ||
        !std::all_of(date.begin() + second_dash + 1U, date.end(), [](unsigned char c) { return std::isdigit(c); }) ||
        !std::all_of(time.begin(), time.begin() + 4U, [](unsigned char c) { return std::isdigit(c); }))
        return std::nullopt;
    constexpr std::array<const char*, 12> months{{"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                                                  "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"}};
    const int day = std::atoi(std::string(date.substr(0, first_dash)).c_str());
    const std::string month_text = upper(date.substr(first_dash + 1U, 3));
    const auto month_it = std::find_if(months.begin(), months.end(), [&](const char* value) {
        return month_text == value;
    });
    const int year = std::atoi(std::string(date.substr(second_dash + 1U, 4)).c_str());
    const int hour = std::atoi(std::string(time.substr(0, 2)).c_str());
    const int minute = std::atoi(std::string(time.substr(2, 2)).c_str());
    if (month_it == months.end() || year < 2000 || day < 1 ||
        hour < 0 || hour > 23 || minute < 0 || minute > 59) return std::nullopt;
    const unsigned month = static_cast<unsigned>(std::distance(months.begin(), month_it) + 1);
    constexpr std::array<unsigned, 12> month_days{{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31}};
    const bool leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    const unsigned maximum_day = month_days[month - 1U] + (month == 2U && leap ? 1U : 0U);
    if (static_cast<unsigned>(day) > maximum_day) return std::nullopt;
    return days_from_civil(year, month, static_cast<unsigned>(day)) * 86400LL +
           hour * 3600LL + minute * 60LL;
}

bool contains_word(const std::string& text, std::string_view word) {
    std::size_t at = 0;
    while ((at = text.find(word, at)) != std::string::npos) {
        const bool before = at == 0 || !std::isalnum(static_cast<unsigned char>(text[at - 1]));
        const std::size_t after_at = at + word.size();
        const bool after = after_at == text.size() ||
                           !std::isalnum(static_cast<unsigned char>(text[after_at]));
        if (before && after) return true;
        ++at;
    }
    return false;
}

}  // namespace

std::string clean_spot_comment(std::string_view comment) {
    std::string cleaned = trim(comment);
    const auto separator = cleaned.find_last_of(" \t");
    const std::string_view token = separator == std::string::npos ? std::string_view(cleaned) :
                                   std::string_view(cleaned).substr(separator + 1U);
    const bool cluster_time = token.size() == 5U &&
        std::toupper(static_cast<unsigned char>(token[4])) == 'Z' &&
        std::all_of(token.begin(), token.begin() + 4U,
                    [](unsigned char c) { return std::isdigit(c); }) &&
        (token[0] - '0') * 10 + token[1] - '0' <= 23 &&
        (token[2] - '0') * 10 + token[3] - '0' <= 59;
    if (cluster_time)
        cleaned = separator == std::string::npos ? "" : trim(std::string_view(cleaned).substr(0, separator));
    return cleaned;
}

std::string spot_band(std::uint64_t hz) {
    const double mhz = static_cast<double>(hz) / 1000000.0;
    if (mhz >= 1.8 && mhz < 2.0) return "160m";
    if (mhz >= 3.5 && mhz < 4.0) return "80m";
    if (mhz >= 5.25 && mhz < 5.45) return "60m";
    if (mhz >= 7.0 && mhz < 7.3) return "40m";
    if (mhz >= 10.1 && mhz < 10.15) return "30m";
    if (mhz >= 14.0 && mhz < 14.35) return "20m";
    if (mhz >= 18.068 && mhz < 18.168) return "17m";
    if (mhz >= 21.0 && mhz < 21.45) return "15m";
    if (mhz >= 24.89 && mhz < 24.99) return "12m";
    if (mhz >= 28.0 && mhz < 29.7) return "10m";
    if (mhz >= 50.0 && mhz < 54.0) return "6m";
    if (hz >= 70'000'000ULL && hz < 71'000'000ULL) return "4m";
    if (hz >= 144'000'000ULL && hz < 148'000'000ULL) return "2m";
    if (hz >= 420'000'000ULL && hz < 450'000'000ULL) return "70cm";
    if (hz >= 1'240'000'000ULL && hz < 1'300'000'000ULL) return "23cm";
    if (hz >= 10'000'000'000ULL && hz < 10'500'000'000ULL) return "3cm";
    return "other";
}

std::string spot_mode(std::uint64_t hz, std::string_view comment) {
    const std::string text = upper(comment);
    constexpr std::array<std::pair<const char*, const char*>, 12> markers{{
        {"FT8", "FT8"}, {"FT4", "FT4"}, {"RTTY", "RTTY"}, {"PSK", "PSK"},
        {"JT65", "JT65"}, {"JS8", "JS8"}, {"CW", "CW"}, {"USB", "USB"},
        {"LSB", "LSB"}, {"SSB", "SSB"}, {"AM", "AM"}, {"FM", "FM"}}};
    for (const auto& marker : markers)
        if (contains_word(text, marker.first)) return marker.second;
    if (text.find("DIGI") != std::string::npos || text.find("DATA") != std::string::npos)
        return "DATA";

    constexpr std::array<std::pair<std::uint64_t, const char*>, 15> digital{{
        {1840000ULL, "FT8"}, {3573000ULL, "FT8"}, {3575000ULL, "FT4"},
        {5357000ULL, "FT8"}, {7047500ULL, "FT4"}, {7074000ULL, "FT8"},
        {10136000ULL, "FT8"}, {10140000ULL, "FT4"}, {14074000ULL, "FT8"},
        {14080000ULL, "FT4"}, {18100000ULL, "FT8"}, {21074000ULL, "FT8"},
        {24915000ULL, "FT8"}, {28074000ULL, "FT8"}, {50313000ULL, "FT8"}}};
    for (const auto& item : digital)
        if (hz > item.first - 1600ULL && hz < item.first + 1600ULL) return item.second;

    const std::string band = spot_band(hz);
    const double mhz = static_cast<double>(hz) / 1000000.0;
    if ((band == "160m" && mhz <= 1.84) || (band == "80m" && mhz <= 3.60) ||
        (band == "60m") || (band == "40m" && mhz <= 7.08) || band == "30m" ||
        (band == "20m" && mhz <= 14.10) || (band == "17m" && mhz <= 18.11) ||
        (band == "15m" && mhz <= 21.15) || (band == "12m" && mhz <= 24.93) ||
        (band == "10m" && mhz <= 28.30)) return "CW";
    if (band == "160m" || band == "80m" || band == "40m" || band == "20m" ||
        band == "17m" || band == "15m" || band == "12m" || band == "10m" ||
        band == "6m") return "SSB";
    return "DATA";
}

std::optional<ClusterSpot> parse_cluster_spot(std::string_view input,
                                              std::int64_t now_epoch) {
    std::string line = trim(input);
    if (line.empty()) return std::nullopt;
    ClusterSpot spot{};
    if (starts_with_ci(line, "DX de ")) {
        std::string_view remainder(line);
        remainder.remove_prefix(6);
        const auto colon = remainder.find(':');
        if (colon == std::string_view::npos) return std::nullopt;
        spot.spotter = trim(remainder.substr(0, colon));
        remainder.remove_prefix(colon + 1);
        const auto frequency = frequency_hz(next_token(remainder));
        spot.callsign = upper(next_token(remainder));
        if (!frequency || spot.callsign.empty() || spot.spotter.empty()) return std::nullopt;
        spot.frequency_hz = *frequency;
        spot.comment = clean_spot_comment(remainder);
        spot.received_epoch = now_epoch;
    } else {
        std::string_view remainder(line);
        const auto frequency = frequency_hz(next_token(remainder));
        spot.callsign = upper(next_token(remainder));
        const auto date = next_token(remainder);
        const auto time = next_token(remainder);
        if (!frequency || spot.callsign.empty() || date.empty() || time.empty()) return std::nullopt;
        spot.frequency_hz = *frequency;
        const auto timestamp = cluster_timestamp(date, time);
        if (!timestamp.has_value()) return std::nullopt;
        spot.received_epoch = *timestamp;
        const std::string rest = trim(remainder);
        const auto open = rest.rfind('<');
        const auto close = rest.rfind('>');
        if (open != std::string::npos && close == rest.size() - 1U && open < close) {
            spot.spotter = trim(std::string_view(rest).substr(open + 1, close - open - 1));
            spot.comment = clean_spot_comment(std::string_view(rest).substr(0, open));
        } else {
            spot.spotter = "-";
            spot.comment = clean_spot_comment(rest);
        }
    }
    spot.band = spot_band(spot.frequency_hz);
    if (spot.band == "other") return std::nullopt;
    spot.mode = spot_mode(spot.frequency_hz, spot.comment);
    return spot;
}

}  // namespace kx3
