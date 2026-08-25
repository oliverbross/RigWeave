# Local main `27c70d0` preservation and audit

The clean local-main tip `27c70d0c2ab0ae21ef18d7fd9b39f8878b0940ea` was preserved unchanged as `recovery/local-main-27c70d0` before integration. `origin/main` remained `fb04d52df0c9ccc305125449bb188ef8e3f0185e`.

| Commit | Message | Patch relationship | Classification | Integration action |
|---|---|---|---|---|
| `c0d4026812d1b7522a295b9554e876b8651f8a5a` | Harden cross-platform safety and persistence | Not patch-identical to either frozen source; overlaps later Android lifecycle/safety work but also changes broad Android, shared-core and iOS behavior | `UNRELATED_PRESERVED` | Not cherry-picked; review separately before any main promotion. |
| `00fe01cd56c206543b1afb0fb03dfdb9befb92f7` | fix: complete whole-app review remediation | Not patch-identical to either frozen source; includes broad Digi, Groups.io, Contest/N1MM and UI remediation beyond this integration mission | `UNRELATED_PRESERVED` | Not cherry-picked; review separately before any main promotion. |
| `27c70d0c2ab0ae21ef18d7fd9b39f8878b0940ea` | Merge whole-app review remediation | Merge wrapper for the two commits above, with first parent at frozen `origin/main` | `UNRELATED_PRESERVED` | Never merged into the integration branch. |

The audit compared messages, changed paths, stable patch relationships and behavior against the hardened Android and Windows tips. Preservation is not an endorsement or a claim that every hunk is obsolete; it prevents broad unrelated work from being silently imported or lost.
