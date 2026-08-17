#include "kx3/panadapter_dsp.hpp"

#include <algorithm>
#include <cmath>

namespace kx3 {
namespace {
constexpr float kPi = 3.14159265358979323846F;
constexpr float kFloorDb = -140.0F;

bool valid_fft_size(std::size_t size) {
    return size == 1024U || size == 2048U || size == 4096U || size == 8192U;
}

float decode_sample(const std::uint8_t* p, unsigned bytes, unsigned bits) {
    std::int32_t value{};
    if (bytes == 2U) value = static_cast<std::int16_t>(p[0] | (p[1] << 8U));
    else if (bytes == 3U) {
        value = static_cast<std::int32_t>(p[0] | (p[1] << 8U) | (p[2] << 16U));
        if ((value & 0x00800000) != 0) value |= static_cast<std::int32_t>(0xff000000);
    } else if (bytes == 4U) {
        const std::uint32_t raw = static_cast<std::uint32_t>(p[0]) |
            (static_cast<std::uint32_t>(p[1]) << 8U) |
            (static_cast<std::uint32_t>(p[2]) << 16U) |
            (static_cast<std::uint32_t>(p[3]) << 24U);
        value = static_cast<std::int32_t>(raw);
    } else return 0.0F;
    return static_cast<float>(value) / static_cast<float>(std::uint64_t{1} << (bits - 1U));
}

float finite_db(float power) {
    return std::max(kFloorDb, 10.0F * std::log10(std::max(power, 1.0e-14F)));
}
}  // namespace

PanadapterDsp::PanadapterDsp() { configure(PanadapterConfig{}); }

bool PanadapterDsp::configure(const PanadapterConfig& requested) {
    if (!valid_fft_size(requested.fft_size) ||
        (requested.sample_rate != 48000U && requested.sample_rate != 96000U) ||
        (requested.overlap_percent != 25U && requested.overlap_percent != 50U && requested.overlap_percent != 75U) ||
        (requested.zoom_decimation != 1U && requested.zoom_decimation != 2U &&
         requested.zoom_decimation != 4U && requested.zoom_decimation != 8U)) return false;
    config_ = requested;
    config_.display_floor_db = std::clamp(config_.display_floor_db, kFloorDb, -20.0F);
    config_.display_top_db = std::clamp(config_.display_top_db, config_.display_floor_db + 20.0F, 20.0F);
    config_.attack = std::clamp(config_.attack, 0.01F, 1.0F);
    config_.release = std::clamp(config_.release, 0.001F, 1.0F);
    config_.average_frames = std::clamp(config_.average_frames, 1U, 64U);
    config_.i_trim = std::clamp(config_.i_trim, 0.25F, 4.0F);
    config_.q_trim = std::clamp(config_.q_trim, 0.25F, 4.0F);
    const float zoom_nyquist = static_cast<float>(config_.sample_rate) * 0.5F;
    config_.zoom_offset_hz = std::clamp(config_.zoom_offset_hz, -zoom_nyquist, zoom_nyquist);
    rebuild_configuration();
    return true;
}

void PanadapterDsp::rebuild_configuration() {
    const auto size = config_.fft_size;
    ring_.assign(size, {}); fft_data_.assign(size, {});
    window_coefficients_.assign(size, 1.0F);
    instantaneous_power_.assign(size, 0.0F); averaged_power_.assign(size, 0.0F);
    trace_db_.assign(size, kFloorDb); waterfall_db_.assign(size, kFloorDb);
    peak_hold_db_.assign(size, kFloorDb); floor_scratch_.assign(size, kFloorDb);
    bins_.assign(size, 0U); valid_mask_.assign(size, 0U); bit_reverse_.assign(size, 0U); twiddles_.assign(size / 2U, {});
    unsigned bits{};
    while ((std::size_t{1} << bits) < size) ++bits;
    for (std::size_t i = 0; i < size; ++i) {
        std::size_t reversed{};
        for (unsigned b = 0; b < bits; ++b) reversed = (reversed << 1U) | ((i >> b) & 1U);
        bit_reverse_[i] = reversed;
    }
    for (std::size_t i = 0; i < size / 2U; ++i) {
        const float angle = -2.0F * kPi * static_cast<float>(i) / static_cast<float>(size);
        twiddles_[i] = {std::cos(angle), std::sin(angle)};
    }
    rebuild_window(); rebuild_zoom_filter(); reset();
}

void PanadapterDsp::rebuild_window() {
    double w1{}, w2{};
    for (std::size_t i = 0; i < config_.fft_size; ++i) {
        const float phase = 2.0F * kPi * static_cast<float>(i) / static_cast<float>(config_.fft_size - 1U);
        float value{1.0F};
        switch (config_.window) {
            case PanWindow::Hann: value = 0.5F - 0.5F * std::cos(phase); break;
            case PanWindow::Nuttall:
                value = 0.355768F - 0.487396F * std::cos(phase) + 0.144232F * std::cos(2.0F * phase) - 0.012604F * std::cos(3.0F * phase); break;
            case PanWindow::Rectangular: value = 1.0F; break;
            case PanWindow::FlatTop:
                value = 0.21557895F - 0.41663158F * std::cos(phase) + 0.277263158F * std::cos(2.0F * phase) - 0.083578947F * std::cos(3.0F * phase) + 0.006947368F * std::cos(4.0F * phase); break;
            default:
                value = 0.35875F - 0.48829F * std::cos(phase) + 0.14128F * std::cos(2.0F * phase) - 0.01168F * std::cos(3.0F * phase); break;
        }
        window_coefficients_[i] = value; w1 += value; w2 += static_cast<double>(value) * value;
    }
    snapshot_.enbw_bins = static_cast<float>(config_.fft_size * w2 / (w1 * w1));
    snapshot_.effective_sample_rate = config_.sample_rate / config_.zoom_decimation;
    snapshot_.rbw_hz = static_cast<float>(snapshot_.effective_sample_rate) / static_cast<float>(config_.fft_size) * snapshot_.enbw_bins;
}

void PanadapterDsp::rebuild_zoom_filter() {
    constexpr std::size_t tap_count = 63U;
    zoom_taps_.assign(tap_count, 0.0F); zoom_state_.assign(tap_count, {});
    const float cutoff = 0.45F / static_cast<float>(config_.zoom_decimation);
    float sum{};
    for (std::size_t i = 0; i < tap_count; ++i) {
        const float x = static_cast<float>(i) - static_cast<float>(tap_count - 1U) * 0.5F;
        const float sinc = x == 0.0F ? 2.0F * cutoff : std::sin(2.0F * kPi * cutoff * x) / (kPi * x);
        const float hamming = 0.54F - 0.46F * std::cos(2.0F * kPi * static_cast<float>(i) / static_cast<float>(tap_count - 1U));
        zoom_taps_[i] = sinc * hamming; sum += zoom_taps_[i];
    }
    for (float& tap : zoom_taps_) tap /= sum;
    const float angle = -2.0F * kPi * config_.zoom_offset_hz / static_cast<float>(config_.sample_rate);
    mixer_step_ = {std::cos(angle), std::sin(angle)}; mixer_phase_ = {1.0F, 0.0F};
    zoom_write_ = 0; zoom_phase_ = 0;
}

void PanadapterDsp::reset() {
    std::fill(ring_.begin(), ring_.end(), std::complex<float>{});
    std::fill(averaged_power_.begin(), averaged_power_.end(), 0.0F);
    std::fill(trace_db_.begin(), trace_db_.end(), kFloorDb);
    std::fill(waterfall_db_.begin(), waterfall_db_.end(), kFloorDb);
    std::fill(peak_hold_db_.begin(), peak_hold_db_.end(), kFloorDb);
    std::fill(bins_.begin(), bins_.end(), 0U);
    dc_ = {}; ring_write_ = ring_fill_ = hop_progress_ = 0;
    metric_i_power_ = metric_q_power_ = metric_cross_ = metric_delta_power_ = 0.0;
    metric_count_ = clipped_samples_ = 0; snapshot_ = {};
    snapshot_.sample_rate = config_.sample_rate;
    snapshot_.effective_sample_rate = config_.sample_rate / config_.zoom_decimation;
    snapshot_.fft_size = config_.fft_size;
    snapshot_.hop_size = config_.fft_size * (100U - config_.overlap_percent) / 100U;
    snapshot_.zoom_decimation = config_.zoom_decimation; snapshot_.zoom_offset_hz = config_.zoom_offset_hz;
    snapshot_.floor_db = snapshot_.raw_floor_db = snapshot_.stabilized_floor_db = kFloorDb;
    rebuild_window();
}

void PanadapterDsp::set_display_floor(float floor_db) { config_.display_floor_db = std::clamp(floor_db, kFloorDb, config_.display_top_db - 20.0F); }
void PanadapterDsp::set_window(PanWindow window) { config_.window = window; rebuild_window(); }

void PanadapterDsp::set_iq_correction(std::complex<float> a, std::complex<float> b, bool enabled) {
    if (!std::isfinite(a.real()) || !std::isfinite(a.imag()) || !std::isfinite(b.real()) || !std::isfinite(b.imag()) || std::abs(a) < 0.1F || std::abs(a) > 4.0F || std::abs(b) > 1.0F) {
        correction_a_ = {1.0F, 0.0F}; correction_b_ = {}; correction_enabled_ = false; return;
    }
    correction_a_ = a; correction_b_ = b; correction_enabled_ = enabled;
}

void PanadapterDsp::reset_peak_hold() { std::fill(peak_hold_db_.begin(), peak_hold_db_.end(), kFloorDb); }

bool PanadapterDsp::push_pcm(const std::uint8_t* bytes, std::size_t length, unsigned channels, unsigned subframe_bytes, unsigned bits, bool discontinuity) {
    if (bytes == nullptr || channels < 2U || subframe_bytes < 2U || subframe_bytes > 4U || bits < 16U || bits > 32U || length == 0U) return false;
    const std::size_t frame_bytes = channels * subframe_bytes;
    if (discontinuity || length % frame_bytes != 0U) ++snapshot_.discontinuities;
    const float dc_alpha = std::exp(-1.0F / (0.5F * static_cast<float>(config_.sample_rate)));
    const std::uint64_t before = snapshot_.sequence;
    for (std::size_t offset = 0; offset + frame_bytes <= length; offset += frame_bytes) {
        float left = decode_sample(bytes + offset, subframe_bytes, bits);
        float right = decode_sample(bytes + offset + subframe_bytes, subframe_bytes, bits);
        if (config_.swap_iq) std::swap(left, right);
        float i = left * config_.i_trim * (config_.invert_i ? -1.0F : 1.0F);
        float q = right * config_.q_trim * (config_.invert_q ? -1.0F : 1.0F);
        if (std::abs(i) >= 0.999F || std::abs(q) >= 0.999F) ++clipped_samples_;
        dc_ = dc_ * dc_alpha + std::complex<float>(i, q) * (1.0F - dc_alpha);
        std::complex<float> value = std::complex<float>(i, q) - dc_;
        metric_i_power_ += static_cast<double>(value.real()) * value.real(); metric_q_power_ += static_cast<double>(value.imag()) * value.imag();
        metric_cross_ += static_cast<double>(value.real()) * value.imag();
        const float delta = value.real() - value.imag(); metric_delta_power_ += static_cast<double>(delta) * delta; ++metric_count_;
        if (correction_enabled_) value = correction_a_ * value + correction_b_ * std::conj(value);
        if (config_.conjugate) value = std::conj(value);
        if (config_.zoom_decimation == 1U) accept_sample(value);
        else {
            const std::complex<float> mixed = value * mixer_phase_; mixer_phase_ *= mixer_step_;
            if ((snapshot_.input_frames & 4095U) == 0U) mixer_phase_ /= std::abs(mixer_phase_);
            zoom_state_[zoom_write_] = mixed; zoom_write_ = (zoom_write_ + 1U) % zoom_state_.size();
            if (++zoom_phase_ == config_.zoom_decimation) {
                zoom_phase_ = 0; std::complex<float> filtered{}; std::size_t index = zoom_write_;
                for (std::size_t tap = 0; tap < zoom_taps_.size(); ++tap) { index = index == 0U ? zoom_state_.size() - 1U : index - 1U; filtered += zoom_state_[index] * zoom_taps_[tap]; }
                accept_sample(filtered);
            }
        }
        ++snapshot_.input_frames;
    }
    return snapshot_.sequence != before;
}

void PanadapterDsp::accept_sample(std::complex<float> sample) {
    ring_[ring_write_] = sample; ring_write_ = (ring_write_ + 1U) % ring_.size();
    if (ring_fill_ < ring_.size()) {
        ++ring_fill_;
        if (ring_fill_ == ring_.size()) transform();
        return;
    }
    if (++hop_progress_ >= snapshot_.hop_size) { hop_progress_ = 0; transform(); }
}

void PanadapterDsp::fft() {
    const auto size = fft_data_.size();
    for (std::size_t i = 0; i < size; ++i) if (i < bit_reverse_[i]) std::swap(fft_data_[i], fft_data_[bit_reverse_[i]]);
    for (std::size_t len = 2U; len <= size; len <<= 1U) {
        const std::size_t stride = size / len;
        for (std::size_t base = 0; base < size; base += len) for (std::size_t j = 0; j < len / 2U; ++j) {
            const auto product = fft_data_[base + j + len / 2U] * twiddles_[j * stride]; const auto first = fft_data_[base + j];
            fft_data_[base + j] = first + product; fft_data_[base + j + len / 2U] = first - product;
        }
    }
}

float PanadapterDsp::flatness_db(float offset_hz) const {
    if (!config_.generic_kx3_flatness) return 0.0F;
    const float x = std::abs(offset_hz);
    if (x <= 24000.0F) return 2.5F * x / 24000.0F;
    if (x <= 48000.0F) return 2.5F + 1.5F * (x - 24000.0F) / 24000.0F;
    return std::min(7.0F, 4.0F + 3.0F * (x - 48000.0F) / 48000.0F);
}

void PanadapterDsp::transform() {
    double window_sum{};
    for (std::size_t i = 0; i < ring_.size(); ++i) { fft_data_[i] = ring_[(ring_write_ + i) % ring_.size()] * window_coefficients_[i]; window_sum += window_coefficients_[i]; }
    fft();
    const float inv_w1_squared = 1.0F / static_cast<float>(window_sum * window_sum);
    const float averaging_alpha = 1.0F / static_cast<float>(config_.average_frames);
    const float effective_rate = static_cast<float>(snapshot_.effective_sample_rate); snapshot_.peak_db = kFloorDb;
    for (std::size_t x = 0; x < fft_data_.size(); ++x) {
        const std::size_t shifted = (x + fft_data_.size() / 2U) % fft_data_.size(); const float power = std::norm(fft_data_[shifted]) * inv_w1_squared;
        instantaneous_power_[x] = power; averaged_power_[x] = snapshot_.transforms == 0U ? power : averaged_power_[x] + averaging_alpha * (power - averaged_power_[x]);
        const float offset = (static_cast<float>(x) - static_cast<float>(fft_data_.size()) * 0.5F) * effective_rate / static_cast<float>(fft_data_.size());
        const float db = finite_db(averaged_power_[x]) + flatness_db(offset); waterfall_db_[x] = db;
        const float coefficient = db >= trace_db_[x] ? config_.attack : config_.release; trace_db_[x] += coefficient * (db - trace_db_[x]);
        if (!std::isfinite(trace_db_[x])) trace_db_[x] = kFloorDb;
        if (config_.peak_hold) { const float decay = config_.peak_decay_db_per_second * static_cast<float>(snapshot_.hop_size) / effective_rate; peak_hold_db_[x] = std::max(trace_db_[x], peak_hold_db_[x] - decay); }
        else peak_hold_db_[x] = trace_db_[x];
        snapshot_.peak_db = std::max(snapshot_.peak_db, trace_db_[x]);
        const float scaled = (trace_db_[x] - config_.display_floor_db) / (config_.display_top_db - config_.display_floor_db);
        bins_[x] = static_cast<std::uint8_t>(std::clamp(scaled * 255.0F, 0.0F, 255.0F));
    }
    const std::size_t guard = std::max<std::size_t>(4U, fft_data_.size() / 50U); std::size_t count{};
    const std::size_t centre = trace_db_.size() / 2U;
    std::fill(valid_mask_.begin(), valid_mask_.end(), 0U);
    for (std::size_t i = guard; i + guard < trace_db_.size(); ++i) {
        if (std::abs(static_cast<long>(i) - static_cast<long>(centre)) <= 3L || !std::isfinite(trace_db_[i])) continue;
        valid_mask_[i] = 1U;
        floor_scratch_[count++] = trace_db_[i];
    }
    snapshot_.valid_bin_count = static_cast<std::uint32_t>(count);
    snapshot_.valid_bin_fraction = static_cast<float>(count) / static_cast<float>(trace_db_.size());
    if (count >= trace_db_.size() / 4U) {
        // The 35th valid-band percentile is insensitive to narrow carriers and never sees masked dark edges.
        const std::size_t percentile = static_cast<std::size_t>(0.35F * static_cast<float>(count - 1U));
        std::nth_element(floor_scratch_.begin(), floor_scratch_.begin() + percentile, floor_scratch_.begin() + count);
        snapshot_.raw_floor_db = floor_scratch_[percentile];
        const float alpha = snapshot_.transforms == 0U ? 1.0F :
            (snapshot_.raw_floor_db > snapshot_.stabilized_floor_db ? 0.08F : 0.018F);
        snapshot_.stabilized_floor_db += alpha * (snapshot_.raw_floor_db - snapshot_.stabilized_floor_db);
        snapshot_.floor_db = snapshot_.stabilized_floor_db;
    } else {
        snapshot_.raw_floor_db = snapshot_.stabilized_floor_db = snapshot_.floor_db = kFloorDb;
    }
    if (metric_count_ > 0U) {
        const double pi = metric_i_power_ / static_cast<double>(metric_count_), pq = metric_q_power_ / static_cast<double>(metric_count_);
        snapshot_.i_rms_db = finite_db(static_cast<float>(pi)); snapshot_.q_rms_db = finite_db(static_cast<float>(pq));
        snapshot_.iq_correlation = static_cast<float>(metric_cross_ / (static_cast<double>(metric_count_) * std::sqrt(pi * pq + 1.0e-24)));
        snapshot_.duplicate_correlation = 1.0F - static_cast<float>(metric_delta_power_ / (metric_i_power_ + metric_q_power_ + 1.0e-24));
        snapshot_.clipped_fraction = static_cast<float>(clipped_samples_) / static_cast<float>(metric_count_ * 2U);
        snapshot_.valid_stereo = pi > 1.0e-10 && pq > 1.0e-10 && std::abs(snapshot_.iq_correlation) < 0.995F && snapshot_.duplicate_correlation < 0.995F;
        metric_i_power_ = metric_q_power_ = metric_cross_ = metric_delta_power_ = 0.0; metric_count_ = clipped_samples_ = 0;
    }
    ++snapshot_.transforms; ++snapshot_.sequence;
}

}  // namespace kx3
