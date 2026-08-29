# SDRoxide Android Provenance

- Repository: `https://github.com/dividebysandwich/sdroxide`
- Stable release: `v1.5.3`
- Commit: `a680935b10f33768a499435e8bd37f779fa640ae`
- Tree: `4697195080495da4a727b14234b85af89c10ecda`
- Observed licence: GPL version 3
- Licence SHA-256: `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986`

No upstream code or asset is vendored, copied, linked, packaged, or fetched during build. Protocol and UI behavior were reviewed, then independently implemented inside existing RigWeave owners. The only new runtime dependency is official OkHttp `5.3.0` for Android WebSocket transport.

The production pin is `docs/upstream/SDROXIDE.json`. `scripts/check_sdroxide_upstream.py` is read-only and reports review-required drift without mutating the repository.

The final Android 1.0 consolidation reviewed the later stable `v1.5.4` release at commit `1f62978036aaa0e3e9f80bca5db4c19102962fd7`, tree `77a8a562e7c44d7cc9a77cec3169aeba13bc83d3`, with the same GPL-3.0 licence digest. That review updates only the watcher pin and provenance ledger: no v1.5.4 source, asset, vendor subtree, package, model, recording, fixture, dependency, or behaviour was imported. The implementation provenance above remains the original `v1.5.3` behaviour review.
