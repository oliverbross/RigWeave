#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace kx3 {

constexpr std::size_t kPanFftSize = 1024;
constexpr std::size_t kPanBins = kPanFftSize;

enum class PanWindow : std::uint8_t { BlackmanHarris, Hann, Nuttall };

class PanadapterDsp {
public:
    void reset();
    bool push_pcm(const std::uint8_t* bytes, std::size_t length,
                  unsigned channels, unsigned subframe_bytes, unsigned bits);
    void set_display_floor(float floor_db);
    void set_window(PanWindow window);
    const std::array<std::uint8_t, kPanBins>& bins() const { return bins_; }
    const std::array<float, kPanBins>& db_bins() const { return db_bins_; }
    PanWindow window() const { return window_; }
    float peak_db() const { return peak_db_; }
    float i_rms_db() const { return i_rms_db_; }
    float q_rms_db() const { return q_rms_db_; }
    float iq_correlation() const { return iq_correlation_; }

private:
    void transform();
    std::array<float, kPanFftSize> real_{};
    std::array<float, kPanFftSize> imag_{};
    std::array<std::uint8_t, kPanBins> bins_{};
    std::array<float, kPanBins> db_bins_{};
    std::array<float, kPanBins> smoothed_{};
    std::size_t fill_{};
    float peak_db_{-120.0F};
    float display_floor_db_{-100.0F};
    float i_rms_db_{-120.0F};
    float q_rms_db_{-120.0F};
    float iq_correlation_{};
    PanWindow window_{PanWindow::BlackmanHarris};
};

}  // namespace kx3
