#pragma once

#include <cstddef>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>

namespace kx3 {

struct CtyEntity {
    std::string country;
    std::string continent;
    int cq_zone{};
    int itu_zone{};
    float latitude{};
    float longitude{};

    bool valid() const { return !country.empty(); }
};

class CtyResolver {
public:
    bool load_file(const std::string& path);
    bool load_text(std::string_view text);
    CtyEntity resolve(std::string callsign) const;
    std::size_t prefix_count() const { return prefixes_.size(); }

private:
    bool load_lines(const std::string& text);

    std::unordered_map<std::string, CtyEntity> exact_;
    std::unordered_map<std::string, CtyEntity> prefixes_;
};

// Prefer the canonical CTY.DAT entity resolved from the worked callsign, and
// fall back to the logbook country when CTY resolution is unavailable. This
// keeps Wavelog naming differences out of the DX worked/new comparison.
void add_worked_entity_keys(std::unordered_set<std::string>& keys,
                            std::string_view logged_country,
                            std::string_view callsign,
                            const CtyResolver* resolver = nullptr);
bool add_worked_wavelog_tsv_row(std::unordered_set<std::string>& keys,
                                std::string_view row,
                                const CtyResolver* resolver = nullptr);

}  // namespace kx3
