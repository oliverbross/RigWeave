#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string_view>

namespace kx3 {

class CatFrameSink {
public:
    virtual ~CatFrameSink() = default;
    virtual void on_frame(std::string_view frame) = 0;
    virtual void on_parse_error() = 0;
};

class CatParser {
public:
    static constexpr std::size_t kMaxFrameBytes = 96;

    explicit CatParser(CatFrameSink& sink) : sink_(sink) {}
    void push(const std::uint8_t* bytes, std::size_t length);
    void disconnect();
    std::uint32_t parse_errors() const { return parse_errors_; }

private:
    void reject_frame();

    CatFrameSink& sink_;
    std::array<char, kMaxFrameBytes> frame_{};
    std::size_t used_{0};
    std::uint32_t parse_errors_{0};
    bool dropping_{false};
};

}  // namespace kx3
