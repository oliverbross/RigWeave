#pragma once

#include <optional>
#include <string>
#include <string_view>

namespace kx3 {

struct Qso {
    std::string uuid;
    std::string call;
    std::string qso_date;
    std::string time_on;
    std::string frequency_mhz;
    std::string band;
    std::string mode;
    std::string submode;
    std::string rst_sent;
    std::string rst_received;
    std::string station_callsign;
    std::string my_gridsquare;
    std::string name;
    std::string qth;
    std::string comment;
    std::string my_sig;
    std::string my_sig_info;
};

std::optional<std::string> validate_qso(const Qso& qso);
std::string serialize_adif(const Qso& qso);
std::string add_adif_field(std::string adif, std::string_view name, std::string_view value);
std::string wavelog_payload(std::string_view api_key, std::string_view station_profile_id,
                            std::string_view adif);
std::string redact_secret(std::string_view text, std::string_view secret);

}  // namespace kx3
