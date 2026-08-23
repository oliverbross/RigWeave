# Upstream watch

The weekly and manually dispatchable `hamlib-upstream-watch.yml` workflow runs `scripts/check_hamlib_upstream.py --check-latest` with read-only repository permissions. It verifies the vendored manifest and provenance first, then compares the immutable pin with the latest stable GitHub release.

The watcher never rewrites source, opens a pull request, pushes a branch, or changes the pin. A newer release is a review signal. Updating requires a new archive digest, tag/commit/tree verification, licence review, source manifest, Android ABI builds, model/backend delta review, package audit, and the complete validation matrix.

The workflow passes its read-only `github.token` as `GITHUB_TOKEN` to avoid unauthenticated API limits. The script uses the token only as an HTTP authorization header and never prints it. HTTP, network, timeout, or malformed-response failures produce a bounded `FAIL:` result rather than a traceback.

Hamlib development `master` is intentionally not tracked as a dependency. The reviewed development commit is recorded in `UPSTREAM.json` only to make the stable-versus-development decision auditable.
