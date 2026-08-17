#!/usr/bin/env python3
"""Produce a reproducible long-term complex-I/Q spectrum comparison."""

from __future__ import annotations

import argparse
import json
import wave
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


def average_spectrum(path: Path, fft_size: int = 4096) -> dict[str, object]:
    with wave.open(str(path), "rb") as wav:
        if wav.getnchannels() != 2 or wav.getsampwidth() != 2:
            raise ValueError(f"{path}: expected stereo PCM16")
        rate = wav.getframerate()
        raw = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").reshape(-1, 2)
    channels = raw.astype(np.float64) / 32768.0
    channels -= np.mean(channels, axis=0, keepdims=True)
    iq = channels[:, 0] + 1j * channels[:, 1]
    hop = fft_size // 2
    count = 1 + max(0, (len(iq) - fft_size) // hop)
    if count < 2:
        raise ValueError(f"{path}: capture is too short")
    window = np.hanning(fft_size)
    power = np.zeros(fft_size, dtype=np.float64)
    for index in range(count):
        frame = iq[index * hop:index * hop + fft_size]
        transformed = np.fft.fftshift(np.fft.fft(frame * window))
        power += np.abs(transformed) ** 2
    power /= count * np.sum(window) ** 2
    dbfs = 10.0 * np.log10(np.maximum(power, 1e-20))
    frequencies = np.fft.fftshift(np.fft.fftfreq(fft_size, 1.0 / rate))

    # A broad convolution removes narrow comb peaks without using UI auto-level.
    envelope = np.convolve(np.pad(dbfs, (50, 50), mode="edge"), np.ones(101) / 101.0, mode="valid")
    reference = float(np.percentile(envelope[fft_size // 8:fft_size * 7 // 8], 70))
    valid = envelope >= reference - 18.0
    indices = np.flatnonzero(valid)
    usable_hz = float((frequencies[indices[-1]] - frequencies[indices[0]] + rate / fft_size) if indices.size else 0.0)
    edge_power = np.mean(np.r_[power[:fft_size // 8], power[-fft_size // 8:]])
    middle_power = np.mean(power[fft_size // 4:fft_size * 3 // 4])
    one_khz_period = max(1, round(rate / 1000.0))
    delayed = iq[one_khz_period:]
    reference_iq = iq[:-one_khz_period]
    periodic_correlation = float(
        np.abs(np.vdot(reference_iq, delayed)) /
        np.sqrt(np.vdot(reference_iq, reference_iq).real * np.vdot(delayed, delayed).real)
    )
    harmonic_bins = [int(np.argmin(np.abs(frequencies - harmonic * 1000.0)))
                     for harmonic in range(-12, 13) if harmonic]
    one_khz_harmonics = [
        {"frequency_hz": float(frequencies[index]), "average_dbfs": float(dbfs[index])}
        for index in harmonic_bins
    ]
    return {
        "path": str(path),
        "sample_rate_hz": rate,
        "frames": int(len(iq)),
        "duration_s": len(iq) / rate,
        "fft_size": fft_size,
        "averages": count,
        "bin_hz": rate / fft_size,
        "usable_width_hz_at_18db_envelope": usable_hz,
        "usable_fraction": usable_hz / rate,
        "middle_to_outer_edge_db": float(10.0 * np.log10(max(middle_power, 1e-20) / max(edge_power, 1e-20))),
        "i_rms_dbfs": float(20.0 * np.log10(max(np.sqrt(np.mean(channels[:, 0] ** 2)), 1e-20))),
        "q_rms_dbfs": float(20.0 * np.log10(max(np.sqrt(np.mean(channels[:, 1] ** 2)), 1e-20))),
        "iq_correlation": float(np.corrcoef(channels[:, 0], channels[:, 1])[0, 1]),
        "one_khz_period_samples": one_khz_period,
        "one_khz_complex_autocorrelation": periodic_correlation,
        "one_khz_harmonics": one_khz_harmonics,
        "frequency_hz": frequencies,
        "average_dbfs": dbfs,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("captures", nargs="+", type=Path)
    parser.add_argument("--plot", required=True, type=Path)
    parser.add_argument("--json", required=True, type=Path)
    args = parser.parse_args()
    results = [average_spectrum(path) for path in args.captures]

    width, panel_height = 1760, 520
    image = Image.new("RGB", (width, panel_height * len(results)), "#111519")
    draw = ImageDraw.Draw(image)
    for panel, result in enumerate(results):
        left, right = 110, width - 40
        top, bottom = panel * panel_height + 70, (panel + 1) * panel_height - 65
        values = result["average_dbfs"]
        low, high = float(np.percentile(values, 1)), float(np.percentile(values, 99.8))
        low = min(low, high - 35.0)
        for index in range(6):
            y = top + (bottom - top) * index / 5
            draw.line((left, y, right, y), fill="#283139", width=1)
            label = high - (high - low) * index / 5
            draw.text((8, y - 8), f"{label:.0f} dBFS", fill="#A5ADB2")
        for index in range(9):
            x = left + (right - left) * index / 8
            draw.line((x, top, x, bottom), fill="#283139", width=1)
            offset = (-0.5 + index / 8) * result["sample_rate_hz"] / 1000
            draw.text((x - 25, bottom + 10), f"{offset:.0f}", fill="#A5ADB2")
        points = []
        for index, value in enumerate(values):
            x = left + (right - left) * index / (len(values) - 1)
            y = bottom - (bottom - top) * (float(value) - low) / (high - low)
            points.append((x, min(bottom, max(top, y))))
        draw.line(points, fill="#E9A72B", width=2)
        usable = result["usable_width_hz_at_18db_envelope"] / 1000.0
        draw.text((left, panel * panel_height + 22),
                  f"{result['sample_rate_hz'] / 1000:.0f} kHz direct capture | 10 s long-term average | estimated usable width {usable:.1f} kHz",
                  fill="#F4F0E7")
        draw.text(((left + right) // 2 - 90, bottom + 35), "Complex baseband offset (kHz)", fill="#A5ADB2")
    args.plot.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.plot)

    serializable = [{key: value for key, value in result.items() if key not in {"frequency_hz", "average_dbfs"}} for result in results]
    args.json.write_text(json.dumps(serializable, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
