#pragma once

#include <complex>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace kx3 {

constexpr std::size_t kPanFftSize = 1024;
constexpr std::size_t kPanBins = kPanFftSize;

enum class PanWindow : std::uint8_t { BlackmanHarris, Hann, Nuttall, Rectangular, FlatTop };

struct PanadapterConfig {
    std::uint32_t sample_rate{96000};
    std::size_t fft_size{4096};
    unsigned overlap_percent{50};
    PanWindow window{PanWindow::BlackmanHarris};
    float display_floor_db{-120.0F};
    float display_top_db{0.0F};
    float attack{0.78F};
    float release{0.16F};
    unsigned average_frames{1};
    bool peak_hold{false};
    float peak_decay_db_per_second{0.0F};
    bool generic_kx3_flatness{false};
    bool swap_iq{false};
    bool invert_i{false};
    bool invert_q{false};
    bool conjugate{false};
    float i_trim{1.0F};
    float q_trim{1.0F};
    unsigned zoom_decimation{1};
    float zoom_offset_hz{0.0F};
    bool fit_auto_contrast{false};
};

struct PanadapterSnapshot {
    std::uint64_t sequence{};
    std::uint64_t input_frames{};
    std::uint64_t transforms{};
    std::uint64_t discontinuities{};
    std::uint64_t non_finite_samples{};
    std::uint32_t sample_rate{};
    std::uint32_t effective_sample_rate{};
    std::size_t fft_size{};
    std::size_t hop_size{};
    unsigned zoom_decimation{1};
    float zoom_offset_hz{};
    float enbw_bins{};
    float rbw_hz{};
    float peak_db{-140.0F};
    float floor_db{-140.0F};
    float raw_floor_db{-140.0F};
    float stabilized_floor_db{-140.0F};
    float fitted_floor_db{-120.0F};
    float fitted_top_db{0.0F};
    float valid_bin_fraction{};
    std::uint32_t valid_bin_count{};
    float i_rms_db{-140.0F};
    float q_rms_db{-140.0F};
    float iq_correlation{};
    float clipped_fraction{};
    float duplicate_correlation{};
    bool valid_stereo{};
};

class PanadapterDsp {
public:
    PanadapterDsp();
    bool configure(const PanadapterConfig& config);
    void reset();
    bool push_pcm(const std::uint8_t* bytes, std::size_t length,
                  unsigned channels, unsigned subframe_bytes, unsigned bits,
                  bool discontinuity = false);
    bool push_iq_f32(const float* interleaved_iq, std::size_t value_count,
                     bool discontinuity = false);
    void set_display_floor(float floor_db);
    void set_window(PanWindow window);
    void set_iq_correction(std::complex<float> a, std::complex<float> b, bool enabled);
    void reset_peak_hold();

    const std::vector<std::uint8_t>& bins() const { return bins_; }
    const std::vector<float>& db_bins() const { return trace_db_; }
    const std::vector<float>& waterfall_db() const { return waterfall_db_; }
    const std::vector<float>& peak_hold_db() const { return peak_hold_db_; }
    const std::vector<std::uint8_t>& valid_mask() const { return valid_mask_; }
    const PanadapterSnapshot& snapshot() const { return snapshot_; }
    const PanadapterConfig& config() const { return config_; }
    PanWindow window() const { return config_.window; }
    float peak_db() const { return snapshot_.peak_db; }
    float i_rms_db() const { return snapshot_.i_rms_db; }
    float q_rms_db() const { return snapshot_.q_rms_db; }
    float iq_correlation() const { return snapshot_.iq_correlation; }

private:
    void rebuild_configuration();
    void rebuild_window();
    void rebuild_zoom_filter();
    void accept_sample(std::complex<float> sample);
    bool process_iq(float i, float q);
    void transform();
    void fft();
    float flatness_db(float offset_hz) const;

    PanadapterConfig config_{};
    PanadapterSnapshot snapshot_{};
    std::vector<std::complex<float>> ring_{};
    std::vector<std::complex<float>> fft_data_{};
    std::vector<std::complex<float>> zoom_state_{};
    std::vector<float> zoom_taps_{};
    std::vector<float> window_coefficients_{};
    std::vector<float> instantaneous_power_{};
    std::vector<float> averaged_power_{};
    std::vector<float> trace_db_{};
    std::vector<float> waterfall_db_{};
    std::vector<float> peak_hold_db_{};
    std::vector<float> floor_scratch_{};
    std::vector<std::uint8_t> bins_{};
    std::vector<std::uint8_t> valid_mask_{};
    std::vector<std::size_t> bit_reverse_{};
    std::vector<std::complex<float>> twiddles_{};
    std::complex<float> dc_{};
    std::complex<float> correction_a_{1.0F, 0.0F};
    std::complex<float> correction_b_{};
    std::complex<float> mixer_phase_{1.0F, 0.0F};
    std::complex<float> mixer_step_{1.0F, 0.0F};
    std::size_t ring_write_{};
    std::size_t ring_fill_{};
    std::size_t hop_progress_{};
    std::size_t zoom_write_{};
    unsigned zoom_phase_{};
    bool correction_enabled_{};
    double metric_i_power_{};
    double metric_q_power_{};
    double metric_cross_{};
    double metric_delta_power_{};
    std::uint64_t metric_count_{};
    std::uint64_t clipped_samples_{};
};

}  // namespace kx3
