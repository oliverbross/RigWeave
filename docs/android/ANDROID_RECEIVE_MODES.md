# Android Local Receive Modes

| Mode | Default passband | Behavior |
|---|---:|---|
| USB/LSB | 300–2700 Hz | Signed complex channel filters reject the opposite sideband. |
| DIGU/DIGL | 200–3000 Hz | Wider sideband audio; Digi consumption remains explicit. |
| CW | 500–700 Hz, 600 Hz pitch | Adjustable 100–2000 Hz pitch and bounded narrow filters. |
| DSB | 50–3300 Hz | Dual-sideband audio. |
| AM | 50–6000 Hz | Envelope, DC removal, carrier and modulation-depth metrics. |
| SAM | 50–6000 Hz | PLL reports ACQUIRING/LOCKED/FALLBACK and frequency error. |
| NFM | 12.5 kHz default | Quadrature discriminator, DC/de-emphasis, squelch and tone metadata. |
| WFM | 95 kHz | Requires at least 192 kHz source; pilot, stereo blend and RDS path. |
| SPECTRUM | no audio | Display/metadata only. |

Mode selectors show only source-supported choices. Filter edits change only the local FIR. Local frequency and passband drags are coalesced receiver-state changes and never physical radio commands.
