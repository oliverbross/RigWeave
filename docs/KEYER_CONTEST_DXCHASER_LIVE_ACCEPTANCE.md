# Keyer, Contest/N1MM and DX Chaser live acceptance

This checklist is deliberately unperformed by the source integration task. Passing builds or tests
does not prove physical UI, keyboard, audio, radio, network peer, authenticated service or RF behavior.

## Physical keyboard and Keyer

- Verify compact/wide Contest navigation and Radio/Contest/Settings Keyer handoffs.
- Exercise initial key-down, repeat/key-up suppression, text field/modal blocking and Esc global stop.
- With a dummy load and explicit operator authority, verify CW/voice arming, Contest RUN/S&P profile,
  ESM message choice, repeat-CQ stop conditions and RX recovery.

## N1MM+

- Start from OFF/loopback/untrusted/unarmed and explicitly configure the intended isolated LAN.
- Verify discovery/source-address truth, trust pins, peer/claim expiry and bounded diagnostics.
- Prove packets cannot tune, key PTT, operate Keyer/Digi/Chaser or silently edit/delete/log.
- Verify a reviewed safe add reaches the canonical QSO coordinator once and Wavelog outbox once.

## DX Chaser and Digi

- Open DX Chaser and prove it remains inactive.
- Feed real local FT8/FT4 capture plus reference/companion fixtures; only exact local decode rows may
  become eligible.
- Verify every visible interlock, operator-started Assist/Dry Run/Chase and no automatic Digi TX enable.
- Verify engagement lock, attempt/failure/RX-unconfirmed behavior, canonical QSO success feedback and
  no false completion.
- Verify incompatible Contest and trusted peer claims block visibly; new multipliers remain bounded
  context only.
- Verify cross-band requests enter receive review only and still require a fresh local decode.

## Persistence and privacy

- Recreate the process during running Contest/N1MM/Chaser/Keyer/Digi states and prove all active arms
  and sessions restore safe.
- Export/import the configuration bundle and inspect its preview; verify no runtime arm, raw QSO,
  decode transcript, peer payload or credential is present.
- Export the sanitized support bundle and verify only metadata/counts/statuses are present.

Record device model, app exact SHA, peer versions, radio/audio route, operator confirmations and
observed results. Never infer RF, PTT, authenticated Wavelog or N1MM+ success from this source/build
record. Do not install as part of this integration programme.
