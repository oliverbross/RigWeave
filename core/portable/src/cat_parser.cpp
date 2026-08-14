#include "kx3/cat_parser.hpp"

namespace kx3 {
namespace {

std::size_t counted_frame_size(const std::array<char, CatParser::kMaxFrameBytes>& frame,
                               const std::size_t used) {
    // DS contains ten fixed-width display/icon bytes. Those bytes may have bit 7
    // set and the eight display bytes may legitimately include ';'.
    if (used >= 2 && frame[0] == 'D' && frame[1] == 'S') return 13;
    if (used >= 5 && frame[0] == 'T' && frame[1] == 'B' && frame[2] == 'X' &&
        frame[3] >= '0' && frame[3] <= '9' && frame[4] >= '0' && frame[4] <= '9') {
        const auto count = static_cast<std::size_t>((frame[3] - '0') * 10 + frame[4] - '0');
        return 6 + count;
    }
    if (used >= 5 && frame[0] == 'T' && frame[1] == 'B' && frame[2] != 'X' &&
        frame[3] >= '0' && frame[3] <= '9' && frame[4] >= '0' && frame[4] <= '9') {
        const auto count = static_cast<std::size_t>((frame[3] - '0') * 10 + frame[4] - '0');
        return 6 + count;
    }
    return 0;
}

}  // namespace

void CatParser::reject_frame() {
    ++parse_errors_;
    sink_.on_parse_error();
    used_ = 0;
    dropping_ = true;
}

void CatParser::push(const std::uint8_t* bytes, const std::size_t length) {
    if (bytes == nullptr && length != 0) {
        reject_frame();
        return;
    }
    for (std::size_t i = 0; i < length; ++i) {
        const auto byte = static_cast<char>(bytes[i]);
        if (byte == ';') {
            if (dropping_) {
                dropping_ = false;
                used_ = 0;
                continue;
            }
            if (used_ == 0) {
                reject_frame();
                dropping_ = false;
                continue;
            }
            const auto counted_size = counted_frame_size(frame_, used_);
            if (counted_size != 0 && used_ + 1 < counted_size) {
                frame_[used_++] = ';';
                continue;
            }
            if (counted_size != 0 && used_ + 1 != counted_size) {
                reject_frame();
                dropping_ = false;
                continue;
            }
            frame_[used_++] = ';';
            sink_.on_frame(std::string_view(frame_.data(), used_));
            used_ = 0;
            continue;
        }
        if (dropping_) {
            continue;
        }
        const auto printable = static_cast<unsigned char>(byte) >= 0x20 &&
                               static_cast<unsigned char>(byte) <= 0x7e;
        const bool ds_payload = used_ >= 2 && frame_[0] == 'D' && frame_[1] == 'S' && used_ < 12;
        if ((!printable && !ds_payload) || used_ + 1 >= frame_.size()) {
            reject_frame();
            continue;
        }
        frame_[used_++] = byte;
    }
}

void CatParser::disconnect() {
    if (used_ != 0 || dropping_) {
        ++parse_errors_;
        sink_.on_parse_error();
    }
    used_ = 0;
    dropping_ = false;
}

}  // namespace kx3
