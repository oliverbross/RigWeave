# Web and Agent compatibility

Web M8 base `80b3999f9c43311d62bba418ce229b6e4baf050a` and Agent M8 base `96a4bfca41a6b2d899d86ff38a88ab5e45071d4c` share the typed observer/control/workflow/relay contracts. Unknown major versions, missing capabilities, stale generations, expired leases and identity mismatch fail closed. M9 mobile sync routes and schemas are intentionally absent.

Cross-repository CI must check out both exact candidate SHAs and run fixtures against immutable worktrees.
