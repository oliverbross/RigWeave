# Branch Retention and Archive Plan

RC1 changes no retention state. All branches and worktrees remain available until the owner approves a separate, post-promotion archive operation.

| Class | Present action | Future owner-gated action |
| --- | --- | --- |
| ANCESTOR_OF_RC | retain | optionally archive after promotion and rollback-window expiry |
| ALREADY_PRESENT_EQUIVALENT | retain with ledger | optionally archive only after equivalence is rechecked against promoted SHA |
| SUPERSEDED_BY_RC | retain | document supersession before any archival |
| UNIQUE_REQUIRED | integrate through audited semantic commit | retain original provenance indefinitely |
| UNRELATED_PRESERVED | retain untouched | no implied archival |
| EXPERIMENTAL_NOT_ACCEPTED | retain | owner decides independently |
| ABANDONED_WITH_REASON | retain | delete only under an explicit destructive-action approval |

`main`, `origin/main`, recovery refs and the RC branch are protected targets. Promotion is described in `MAIN_PROMOTION_RUNBOOK.md` but is not part of RC construction.
