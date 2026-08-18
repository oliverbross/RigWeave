// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 KD9TAW <kd9taw@protonmail.com>
// Copyright (C) 2026 Oliver Bross
//
// Digital-mode DSP is derived from Nexus commit
// 6ec4a7925f1550cc364c7fd95967ce38c696ad3f. See ../../UPSTREAM.md.

pub mod cw;
pub mod cw_decode;
pub mod rtty {
    pub mod afsk;
    pub mod baudot;
    pub mod demod;
}
pub mod spectrum;

use rtty::afsk::{afsk_char_samples, AfskConfig};
use rtty::baudot::{code_bits, BaudotEncoder};
use rtty::demod::{RttyConfig, RttyDemod, RttyDemodulator};

pub use cw::{morse_duration_ms, morse_samples};
pub use cw_decode::CwStreamDecoder;
pub use tempo_sstv;

pub fn rtty_samples(text: &str, sample_rate: u32, reverse: bool) -> Vec<f32> {
    let codes = BaudotEncoder::new(true).encode(text);
    let bits = code_bits(&codes);
    afsk_char_samples(
        &bits,
        &AfskConfig {
            sample_rate,
            reverse,
            ..AfskConfig::default()
        },
    )
}

pub struct RttyStreamDecoder {
    inner: RttyDemodulator,
    transcript: String,
}

impl RttyStreamDecoder {
    pub fn new(reverse: bool) -> Self {
        let (mark_hz, space_hz) = if reverse { (2295.0, 2125.0) } else { (2125.0, 2295.0) };
        Self {
            inner: RttyDemodulator::new(RttyConfig {
                mark_hz,
                space_hz,
                ..RttyConfig::default()
            }),
            transcript: String::new(),
        }
    }

    pub fn push(&mut self, samples: &[f32]) -> &str {
        for decoded in self.inner.feed(samples) {
            if decoded.confidence >= 0.12 {
                self.transcript.push(decoded.ch);
            }
        }
        if self.transcript.len() > 4096 {
            let keep_from = self.transcript.len() - 4096;
            self.transcript.drain(..keep_from);
        }
        &self.transcript
    }

    pub fn afc_offset_hz(&self) -> f32 { self.inner.afc_offset_hz() }
    pub fn afc_locked(&self) -> bool { self.inner.afc_locked() }
    pub fn clear(&mut self) { self.transcript.clear(); }
}

