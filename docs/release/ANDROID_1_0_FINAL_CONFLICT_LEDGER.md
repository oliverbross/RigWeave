# Android 1.0 final conflict ledger

## Integration method

The RC1 tablet sweep is an ancestor of the Android 1.0 hardening SHA. No merge was performed, so there were no textual merge conflicts and no `ours`/`theirs` resolutions.

| Area | Tablet-sweep disposition | Hardening interaction | Decision |
|---|---|---|---|
| MapLibre Spectrum/RF Map/RF Globe | `ALREADY_PRESENT_EQUIVALENT` | hardening retains the same map owners and tests | retain hardening tree |
| compact colored spot status | `ALREADY_PRESENT_EQUIVALENT` | hardening retains shared status-color resolution | retain hardening tree |
| Settings → Screens persistence | `ALREADY_PRESENT_EQUIVALENT` | hardening retains the versioned preference and route safety | retain hardening tree |
| safe Home launch/navigation | `ALREADY_PRESENT_EQUIVALENT` | hardening builds adaptive Settings around the retained owner | retain hardening tree |
| grouped radio catalogue | `SUPERSEDED` follow-up | implemented by hardening | retain hardening implementation |
| consolidated feature settings | `SUPERSEDED` follow-up | implemented by hardening for Contest, Digi, and DX Chaser | retain hardening implementation |
| database/query contract | not changed by sweep | hardening schema 17/projection 6 and indexes are authoritative | retain hardening implementation |
| lifecycle/concurrency/R8/package | not changed by sweep | hardening-only accepted work | retain hardening implementation |

## Non-source closure decisions

| Finding | Classification | Resolution |
|---|---|---|
| Linux stationd job cannot find `libsecret-1` | required CI defect | install `pkg-config`, `libglib2.0-dev`, and `libsecret-1-dev` before Linux builds |
| SDRoxide v1.5.4 watcher exit 2 | `PROVENANCE_UPDATE_REQUIRED` | review immutable release and every changed path; update pin/ledger/tests |
| watcher job hidden by job-level `continue-on-error` | gate defect | make the SDRoxide watcher failure mandatory |
| missing explicit product platform declaration | metadata defect | add `## Platform` without changing suite release identity |

No application ID, version code, signing identity, schema owner, credential path, RC1 tag, published release, or production feature owner is conflicted or replaced.
