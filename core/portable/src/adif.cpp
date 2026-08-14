#include "kx3/adif.hpp"

#include <algorithm>
#include <cctype>

namespace kx3 {
namespace {

bool digits(const std::string_view value) {
    return std::all_of(value.begin(), value.end(), [](const char c) {
        return std::isdigit(static_cast<unsigned char>(c)) != 0;
    });
}

std::string upper(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](const unsigned char c) {
        return static_cast<char>(std::toupper(c));
    });
    return value;
}

void field(std::string& output, const std::string_view name, const std::string_view value) {
    if (value.empty()) return;
    output += '<';
    output += name;
    output += ':';
    output += std::to_string(value.size());
    output += '>';
    output.append(value.data(), value.size());
}

std::string json_escape(const std::string_view value) {
    std::string output;
    output.reserve(value.size() + 16);
    for (const unsigned char c : value) {
        switch (c) {
            case '"': output += "\\\""; break;
            case '\\': output += "\\\\"; break;
            case '\n': output += "\\n"; break;
            case '\r': output += "\\r"; break;
            case '\t': output += "\\t"; break;
            default:
                if (c >= 0x20) output.push_back(static_cast<char>(c));
                break;
        }
    }
    return output;
}

}  // namespace

std::optional<std::string> validate_qso(const Qso& qso) {
    if (qso.call.empty()) return "CALL is required";
    if (qso.qso_date.size() != 8 || !digits(qso.qso_date)) return "QSO_DATE must be YYYYMMDD";
    if (qso.time_on.size() != 6 || !digits(qso.time_on)) return "TIME_ON must be HHMMSS UTC";
    if (qso.frequency_mhz.empty() && qso.band.empty()) return "FREQ or BAND is required";
    if (qso.mode.empty()) return "MODE is required";
    if (qso.station_callsign.empty()) return "STATION_CALLSIGN is required";
    if (qso.uuid.size() != 36) return "QSO UUID must contain 36 characters";
    if (upper(qso.submode) == "FT8" && upper(qso.mode) != "MFSK") {
        return "FT8 requires MODE=MFSK and SUBMODE=FT8";
    }
    return std::nullopt;
}

std::string serialize_adif(const Qso& qso) {
    if (validate_qso(qso).has_value()) return {};
    std::string output;
    output.reserve(256 + qso.comment.size());
    field(output, "CALL", upper(qso.call));
    field(output, "QSO_DATE", qso.qso_date);
    field(output, "TIME_ON", qso.time_on);
    field(output, "FREQ", qso.frequency_mhz);
    field(output, "BAND", upper(qso.band));
    field(output, "MODE", upper(qso.mode));
    field(output, "SUBMODE", upper(qso.submode));
    field(output, "RST_SENT", qso.rst_sent);
    field(output, "RST_RCVD", qso.rst_received);
    field(output, "STATION_CALLSIGN", upper(qso.station_callsign));
    field(output, "MY_GRIDSQUARE", upper(qso.my_gridsquare));
    field(output, "MY_SIG", upper(qso.my_sig));
    field(output, "MY_SIG_INFO", upper(qso.my_sig_info));
    field(output, "NAME", qso.name);
    field(output, "QTH", qso.qth);
    field(output, "COMMENT", qso.comment);
    field(output, "APP_KX3TOUCH_UUID", qso.uuid);
    output += "<EOR>";
    return output;
}

std::string add_adif_field(std::string adif, const std::string_view name,
                           const std::string_view value) {
    if (name.empty() || value.empty() || !std::all_of(name.begin(), name.end(), [](const unsigned char c) {
            return std::isalnum(c) != 0 || c == '_';
        })) return adif;
    const std::string normalized_name = upper(std::string(name));
    const std::string upper_record = upper(adif);
    if (upper_record.find("<" + normalized_name + ":") != std::string::npos) return adif;
    const auto end = upper_record.rfind("<EOR>");
    if (end == std::string::npos) return adif;
    std::string addition;
    field(addition, normalized_name, value);
    adif.insert(end, addition);
    return adif;
}

std::string wavelog_payload(const std::string_view api_key,
                            const std::string_view station_profile_id,
                            const std::string_view adif) {
    return "{\"key\":\"" + json_escape(api_key) + "\",\"station_profile_id\":\"" +
           json_escape(station_profile_id) + "\",\"type\":\"adif\",\"string\":\"" +
           json_escape(adif) + "\"}";
}

std::string redact_secret(const std::string_view text, const std::string_view secret) {
    std::string output(text);
    if (secret.empty()) return output;
    std::size_t at = 0;
    while ((at = output.find(secret, at)) != std::string::npos) {
        output.replace(at, secret.size(), "<redacted>");
        at += 10;
    }
    return output;
}

}  // namespace kx3
