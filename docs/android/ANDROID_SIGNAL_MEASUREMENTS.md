# Android Signal Measurements

The Panadapter v6 surface supports Marker A and B. Each marker selects the strongest valid bin in a narrow neighborhood and reports exact derived frequency, peak, median out-of-signal noise, SNR, 3/6/26 dB widths, occupied bandwidth, integrated channel power, adjacent-channel power and center offset. The inspector reports frequency and level deltas when both markers exist.

Uncalibrated and relative sources use `dBFS`; calibrated `dBm` is shown only after a user saves source-specific receive calibration. Invalid/out-of-span markers produce no measurement.

The signal tracker follows a selected in-span peak and reports current frequency, drift, level, SNR and duration. Optional `LOCAL RX FOLLOW` moves only a selected local virtual receiver offset. It cannot move the radio VFO, and it reports a blocked/out-of-span state rather than issuing a radio action.

Up to four monitors reuse the reduced display trace. They publish level, occupancy, duration and HOT/RECENT/QUIET/UNKNOWN state. An NFM monitor may display configured expected CTCSS/DCS metadata, but without a validated decoder result its tone state is exactly `UNDETECTED · NO TONE CLAIM`.
