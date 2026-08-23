# RGO ONE Upstream Watch

Run manually:

```bash
python3 scripts/check_rgo_one_upstream.py
```

The branch also defines a weekly, manually dispatchable read-only GitHub workflow.
Reports are written to `build/reports/rgo-one-upstream.json` and `.md`.

## Contract

- HTTPS is restricted to `lz2jr.com` / `www.lz2jr.com` and reviewed `/blog/` paths.
- Each response is bounded to 8 MiB.
- PDF/text downloads are compared by exact SHA-256.
- Living WordPress pages keep their public page URL for provenance but compare the bounded
  canonical official REST representation to avoid theme/form nonce churn.
- ETag and Last-Modified are recorded when the server provides them.
- `CURRENT`, `CHANGED`, and `UNAVAILABLE` are distinct results.
- Changed or unavailable content returns exit 2 for human review.
- Firmware/archive extensions are rejected before network access.
- The watcher never changes `UPSTREAM.json` or production source.

Fixture behavior can be checked without network access:

```bash
python3 scripts/check_rgo_one_upstream.py --self-test
```

Any changed CAT manual, firmware note, manual index, or module document requires a fresh
official-source audit. Never infer a new command, capability, or module from a marketing
page alone.
