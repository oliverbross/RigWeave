#include "kx3/panadapter_dsp.hpp"

#include <algorithm>
#include <cmath>

namespace kx3 {
namespace {
constexpr float kPi = 3.14159265358979323846F;

float sample(const std::uint8_t* p, unsigned bytes, unsigned bits) {
    std::int32_t value{};
    if (bytes == 2U) value = static_cast<std::int16_t>(p[0] | (p[1] << 8U));
    else if (bytes == 3U) {
        value = static_cast<std::int32_t>(p[0] | (p[1] << 8U) | (p[2] << 16U));
        if ((value & 0x00800000) != 0) value |= static_cast<std::int32_t>(0xff000000);
    } else if (bytes == 4U) {
        value = static_cast<std::int32_t>(p[0] | (p[1] << 8U) | (p[2] << 16U) | (p[3] << 24U));
    } else return 0.0F;
    return static_cast<float>(value) / static_cast<float>(std::uint64_t{1} << (bits - 1U));
}
}  // namespace

void PanadapterDsp::reset() {
    fill_ = 0;
    peak_db_ = -120.0F;
    bins_.fill(0);
    db_bins_.fill(-120.0F);
    smoothed_db_.fill(-120.0F);
    i_rms_db_ = q_rms_db_ = -120.0F;
    iq_correlation_ = 0.0F;
}

void PanadapterDsp::set_window(const PanWindow window) {
    window_ = window;
    smoothed_db_.fill(-120.0F);
}

void PanadapterDsp::set_display_floor(float floor_db) {
    display_floor_db_ = std::clamp(floor_db, -120.0F, -60.0F);
    smoothed_db_.fill(-120.0F);
}

bool PanadapterDsp::push_pcm(const std::uint8_t* bytes, std::size_t length,
                            unsigned channels, unsigned subframe_bytes, unsigned bits) {
    if (bytes == nullptr || channels < 2U || subframe_bytes < 2U || subframe_bytes > 4U ||
        bits < 16U || bits > 32U) return false;
    const std::size_t frame_bytes = channels * subframe_bytes;
    bool ready = false;
    for (std::size_t offset = 0; offset + frame_bytes <= length; offset += frame_bytes) {
        real_[fill_] = sample(bytes + offset, subframe_bytes, bits);
        imag_[fill_] = sample(bytes + offset + subframe_bytes, subframe_bytes, bits);
        if (++fill_ == kPanFftSize) { transform(); fill_ = 0; ready = true; }
    }
    return ready;
}

void PanadapterDsp::transform() {
    float mean_i{}, mean_q{};
    for (std::size_t i = 0; i < kPanFftSize; ++i) { mean_i += real_[i]; mean_q += imag_[i]; }
    mean_i /= static_cast<float>(kPanFftSize); mean_q /= static_cast<float>(kPanFftSize);
    float power_i{}, power_q{}, cross{};
    for (std::size_t i = 0; i < kPanFftSize; ++i) {
        const float centered_i = real_[i] - mean_i;
        const float centered_q = imag_[i] - mean_q;
        power_i += centered_i * centered_i;
        power_q += centered_q * centered_q;
        cross += centered_i * centered_q;
    }
    power_i /= static_cast<float>(kPanFftSize);
    power_q /= static_cast<float>(kPanFftSize);
    i_rms_db_ = std::max(-120.0F, 10.0F * std::log10(power_i + 1.0e-12F));
    q_rms_db_ = std::max(-120.0F, 10.0F * std::log10(power_q + 1.0e-12F));
    iq_correlation_ = cross / (static_cast<float>(kPanFftSize) *
                               std::sqrt(power_i * power_q + 1.0e-18F));
    // Remove DC independently from I and Q. I/Q gain and phase calibration is
    // deliberately not inferred from a live FFT frame: doing so can rotate or
    // suppress legitimate asymmetric RF content.
    for (std::size_t i = 0; i < kPanFftSize; ++i) {
        real_[i] -= mean_i;
        imag_[i] -= mean_q;
    }
    float window_sum{};
    for (std::size_t i = 0; i < kPanFftSize; ++i) {
        const float phase = 2.0F * kPi * static_cast<float>(i) /
                            static_cast<float>(kPanFftSize - 1U);
        float window{};
        if (window_ == PanWindow::Hann) {
            window = 0.5F - 0.5F * std::cos(phase);
        } else if (window_ == PanWindow::Nuttall) {
            window = 0.355768F - 0.487396F * std::cos(phase) +
                     0.144232F * std::cos(2.0F * phase) - 0.012604F * std::cos(3.0F * phase);
        } else {
            window = 0.35875F - 0.48829F * std::cos(phase) +
                     0.14128F * std::cos(2.0F * phase) - 0.01168F * std::cos(3.0F * phase);
        }
        window_sum += window;
        real_[i] *= window;
        imag_[i] *= window;
    }
    for (std::size_t i = 1, j = 0; i < kPanFftSize; ++i) {
        std::size_t bit = kPanFftSize >> 1U;
        for (; (j & bit) != 0U; bit >>= 1U) j ^= bit;
        j ^= bit;
        if (i < j) { std::swap(real_[i], real_[j]); std::swap(imag_[i], imag_[j]); }
    }
    for (std::size_t len = 2; len <= kPanFftSize; len <<= 1U) {
        const float angle = -2.0F * kPi / static_cast<float>(len);
        for (std::size_t base = 0; base < kPanFftSize; base += len) {
            for (std::size_t j = 0; j < len / 2U; ++j) {
                const float c = std::cos(angle * static_cast<float>(j));
                const float s = std::sin(angle * static_cast<float>(j));
                const std::size_t a = base + j, b = a + len / 2U;
                const float tr = c * real_[b] - s * imag_[b];
                const float ti = s * real_[b] + c * imag_[b];
                real_[b] = real_[a] - tr; imag_[b] = imag_[a] - ti;
                real_[a] += tr; imag_[a] += ti;
            }
        }
    }
    peak_db_ = -120.0F;
    for (std::size_t x = 0; x < kPanBins; ++x) {
        const std::size_t shifted = (x + kPanFftSize / 2U) % kPanFftSize;
        const float magnitude = std::hypot(real_[shifted], imag_[shifted]) /
                                std::max(window_sum, 1.0e-12F);
        const float raw_db = std::max(-140.0F, 20.0F * std::log10(magnitude + 1.0e-12F));
        const float alpha = raw_db > smoothed_db_[x] ? 0.55F : 0.18F;
        smoothed_db_[x] += alpha * (raw_db - smoothed_db_[x]);
        db_bins_[x] = smoothed_db_[x];
        peak_db_ = std::max(peak_db_, smoothed_db_[x]);
        const float level = std::clamp((smoothed_db_[x] - display_floor_db_) *
                                       (255.0F / -display_floor_db_), 0.0F, 255.0F);
        bins_[x] = static_cast<std::uint8_t>(level);
    }
    // A sound-card DC residual is not an RF signal. Suppress the narrow centre
    // notch after smoothing so it cannot form a permanent line in the waterfall.
    constexpr std::size_t centre = kPanBins / 2U;
    const std::uint8_t shoulder = static_cast<std::uint8_t>(
        (static_cast<unsigned>(bins_[centre - 4U]) + bins_[centre + 4U]) / 2U);
    for (std::size_t x = centre - 3U; x <= centre + 3U; ++x) {
        bins_[x] = shoulder;
        smoothed_db_[x] = display_floor_db_ +
            static_cast<float>(shoulder) * (-display_floor_db_ / 255.0F);
        db_bins_[x] = smoothed_db_[x];
    }
}

}  // namespace kx3
