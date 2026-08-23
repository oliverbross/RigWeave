# QMX Panadapter upstream watch

The read-only workflow `.github/workflows/qmx-upstream-watch.yml` runs every Sunday at 05:17 UTC and can also be dispatched manually. It uploads JSON/Markdown evidence and never writes the repository or opens a pull request.

Run locally:

```bash
python3 scripts/check_qmx_panadapter_upstream.py \
  --json-output /tmp/qmx-upstream-watch.json \
  --markdown-output /tmp/qmx-upstream-watch.md
```

Exit `0` means no reviewed identity change, `2` means human review is required, and `1` means the comparison failed. The watcher checks default branch, commit, tree, release version and LICENSE digest, and classifies changed paths as `CAT`, `USB`, `AUDIO_IQ`, `DSP`, `PANADAPTER`, `FT8_RX`, `FT8_TX`, `SAFETY`, `RADIO_MENU`, `SETTINGS`, `DIAGNOSTICS`, `LICENCE`, `DOCUMENTATION` or `PLATFORM_ONLY`.

Offline no-change fixture:

```bash
python3 scripts/check_qmx_panadapter_upstream.py \
  --fixture scripts/fixtures/qmx_panadapter_no_change.json
```

The script is read-only. It cannot change the pin, source, branch or pull requests.
