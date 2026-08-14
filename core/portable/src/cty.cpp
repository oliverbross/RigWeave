#include "kx3/cty.hpp"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdio>
#include <cstdlib>

namespace kx3 {
namespace {

std::string trim(std::string value) {
    value.erase(value.begin(), std::find_if(value.begin(), value.end(), [](unsigned char c) {
        return !std::isspace(c);
    }));
    while (!value.empty() && std::isspace(static_cast<unsigned char>(value.back()))) value.pop_back();
    return value;
}

std::string upper(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::toupper(c));
    });
    return value;
}

bool parse_number(const std::string& value, int& output) {
    char* end{};
    const long parsed = std::strtol(value.c_str(), &end, 10);
    if (end == value.c_str()) return false;
    output = static_cast<int>(parsed);
    return true;
}

bool parse_number(const std::string& value, float& output) {
    char* end{};
    const float parsed = std::strtof(value.c_str(), &end);
    if (end == value.c_str()) return false;
    output = parsed;
    return true;
}

void add_prefix_list(std::string_view text, const CtyEntity& entity,
                     std::unordered_map<std::string, CtyEntity>& exact,
                     std::unordered_map<std::string, CtyEntity>& prefixes) {
    std::size_t at{};
    while (at < text.size()) {
        const auto end = text.find_first_of(",;", at);
        std::string token = trim(std::string(text.substr(at, end == std::string_view::npos ?
            std::string_view::npos : end - at)));
        at = end == std::string_view::npos ? text.size() : end + 1U;
        CtyEntity resolved = entity;
        const auto apply_integer = [&](char open, char close, int& target) {
            const auto begin = token.find(open);
            const auto finish = begin == std::string::npos ? std::string::npos : token.find(close, begin + 1U);
            if (finish != std::string::npos)
                parse_number(token.substr(begin + 1U, finish - begin - 1U), target);
        };
        apply_integer('(', ')', resolved.cq_zone);
        apply_integer('[', ']', resolved.itu_zone);
        const auto coordinates = token.find('<');
        const auto coordinates_end = coordinates == std::string::npos ? std::string::npos :
            token.find('>', coordinates + 1U);
        if (coordinates_end != std::string::npos) {
            const std::string pair = token.substr(coordinates + 1U, coordinates_end - coordinates - 1U);
            const auto slash = pair.find('/');
            if (slash != std::string::npos) {
                parse_number(pair.substr(0, slash), resolved.latitude);
                parse_number(pair.substr(slash + 1U), resolved.longitude);
            }
        }
        const auto continent = token.find('{');
        const auto continent_end = continent == std::string::npos ? std::string::npos :
            token.find('}', continent + 1U);
        if (continent_end != std::string::npos)
            resolved.continent = upper(trim(token.substr(continent + 1U, continent_end - continent - 1U)));
        const bool is_exact = !token.empty() && token.front() == '=';
        if (is_exact) token.erase(token.begin());
        const auto modifier = token.find_first_of("([{<~");
        if (modifier != std::string::npos) token.resize(modifier);
        while (!token.empty() && token.back() == '*') token.pop_back();
        token = upper(trim(std::move(token)));
        if (token.empty() || !resolved.valid()) continue;
        (is_exact ? exact : prefixes)[token] = resolved;
    }
}

}  // namespace

bool CtyResolver::load_file(const std::string& path) {
    FILE* file = std::fopen(path.c_str(), "rb");
    if (file == nullptr) return false;
    std::string text;
    std::array<char, 4096> chunk{};
    while (std::feof(file) == 0 && std::ferror(file) == 0) {
        const std::size_t count = std::fread(chunk.data(), 1U, chunk.size(), file);
        if (count > 0U) text.append(chunk.data(), count);
    }
    const bool read_ok = std::ferror(file) == 0;
    std::fclose(file);
    return read_ok && load_lines(text);
}

bool CtyResolver::load_text(std::string_view text) {
    return load_lines(std::string(text));
}

bool CtyResolver::load_lines(const std::string& text) {
    std::unordered_map<std::string, CtyEntity> exact;
    std::unordered_map<std::string, CtyEntity> prefixes;
    CtyEntity current;
    std::size_t start{};
    while (start <= text.size()) {
        const auto end = text.find('\n', start);
        std::string line = text.substr(start, end == std::string::npos ?
            std::string::npos : end - start);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        start = end == std::string::npos ? text.size() + 1U : end + 1U;

        std::array<std::size_t, 8> colons{};
        colons.fill(std::string::npos);
        std::size_t cursor{};
        for (auto& colon : colons) {
            colon = line.find(':', cursor);
            if (colon == std::string::npos) break;
            cursor = colon + 1U;
        }
        if (colons.back() != std::string::npos) {
            CtyEntity entity;
            entity.country = trim(line.substr(0, colons[0]));
            parse_number(trim(line.substr(colons[0] + 1U, colons[1] - colons[0] - 1U)), entity.cq_zone);
            parse_number(trim(line.substr(colons[1] + 1U, colons[2] - colons[1] - 1U)), entity.itu_zone);
            entity.continent = upper(trim(line.substr(colons[2] + 1U, colons[3] - colons[2] - 1U)));
            parse_number(trim(line.substr(colons[3] + 1U, colons[4] - colons[3] - 1U)), entity.latitude);
            parse_number(trim(line.substr(colons[4] + 1U, colons[5] - colons[4] - 1U)), entity.longitude);
            current = entity;
            add_prefix_list(line.substr(colons[6] + 1U, colons[7] - colons[6] - 1U),
                            current, exact, prefixes);
            if (colons[7] + 1U < line.size())
                add_prefix_list(line.substr(colons[7] + 1U), current, exact, prefixes);
        } else {
            add_prefix_list(line, current, exact, prefixes);
        }
    }
    if (prefixes.empty()) return false;
    exact_ = std::move(exact);
    prefixes_ = std::move(prefixes);
    return true;
}

CtyEntity CtyResolver::resolve(std::string callsign) const {
    callsign = upper(trim(std::move(callsign)));
    if (const auto exact = exact_.find(callsign); exact != exact_.end()) return exact->second;
    for (std::size_t length = callsign.size(); length > 0U; --length) {
        if (const auto prefix = prefixes_.find(callsign.substr(0, length)); prefix != prefixes_.end())
            return prefix->second;
    }
    return {};
}

void add_worked_entity_keys(std::unordered_set<std::string>& keys,
                            std::string_view logged_country,
                            std::string_view callsign,
                            const CtyResolver* resolver) {
    if (resolver != nullptr && !callsign.empty()) {
        const CtyEntity resolved = resolver->resolve(std::string(callsign));
        const std::string canonical = upper(trim(resolved.country));
        if (!canonical.empty()) {
            keys.insert(canonical);
            return;
        }
    }
    const std::string logged = upper(trim(std::string(logged_country)));
    if (!logged.empty()) keys.insert(logged);
}

bool add_worked_wavelog_tsv_row(std::unordered_set<std::string>& keys,
                                std::string_view row,
                                const CtyResolver* resolver) {
    // Durable Wavelog indexes are date, time, call, name, country, DXCC, ...
    std::array<std::string_view, 5> fields{};
    std::size_t start{};
    for (auto& field : fields) {
        if (start > row.size()) return false;
        const auto end = row.find('\t', start);
        field = row.substr(start, end == std::string_view::npos ?
            std::string_view::npos : end - start);
        start = end == std::string_view::npos ? row.size() + 1U : end + 1U;
    }
    if (fields[2].empty()) return false;
    add_worked_entity_keys(keys, fields[4], fields[2], resolver);
    return true;
}

}  // namespace kx3
