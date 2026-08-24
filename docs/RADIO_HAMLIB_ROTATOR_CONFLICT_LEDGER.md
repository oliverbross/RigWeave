# Sweep 2 conflict and semantic ledger

## Frozen inputs

| Input | Frozen SHA | Merge commit |
|---|---|---|
| Tablet acceptance Sweep 2 | `09412b24159ead7b48d3810cfa86f56176d33776` | integration base |
| Hamlib Android platform | `9610a1e1186c16f1400d1d45d6d5f3840a9d09ef` | `7ddf14d` |
| QMX/QMX+ radio core | `b6b9f34af37727a37273b1129b827810414b927d` | `10c8579` |
| RGO ONE radio core | `4b40da56e279b3432cfc4ed91fb20ee17c273efe` | `9db6f05` |
| Rotator platform | `6471e85971c088a27ea09bd841d803b90949568e` | `dc64468` |

All inputs descend from `4cd3aa3b401a9f7bc9838b94217444d5b4cae3bc`. No textual merge conflict occurred. The integration work was semantic: the four feature branches intentionally stopped at adapter boundaries.

## Decisions

- Replaced persisted radio selection with stable profile/model identifiers while retaining the old three-value family only as a compatibility projection.
- Unknown or future identifiers restore to a disconnected, read-only `safe.unknown` profile.
- Kept one application radio controller and one physical identity authority. A profile switch requests RX, disconnects and closes the old backend before creating the new backend.
- Routed native QMX and RGO serial traffic through the existing Android USB owner. Embedded Hamlib uses the same owner through a bounded JNI transport bridge.
- Kept QMX UAC/IQ unavailable until the audio route proves the same stable device digest; no microphone fallback was introduced.
- RGO ONE legacy/unknown remains read-only. V6 behavior is enabled only by the explicit V6 profile and must still prove model ID 006 at connection time.
- Added embedded Hamlib rotator JNI/session support using the already-linked Hamlib archive; no second Hamlib build was introduced.
- Rotator native, rotctld and embedded-Hamlib drivers share the radio physical-device authority. Only one rotator profile may be connected at a time.
- Restored selection/configuration is inert. Radio and rotator connection, transmitter actions, automation arm, park and movement are never restored as active state.
- Global Stop now stops the established TX workflows, requests rotator STOP through the active backend, and clears rotator automation.

## Deliberately pending evidence

Physical QMX USB/UAC/IQ, RGO ONE V6, ARCO/rotator movement, audio, PTT, TUNE and RF evidence remain pending owner-present acceptance. A successful build or tablet install does not satisfy those layers.
