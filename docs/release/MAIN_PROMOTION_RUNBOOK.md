# Main Promotion Runbook

This is a future owner-gated procedure. RC1 construction does not execute it.

Prerequisites: clean pushed RC branch; exact hosted SHA all green; artifact/digest ledger complete; lineage proof refreshed; local and origin `main` SHAs rechecked; physical/authenticated/signing exceptions accepted in writing; owner approval for the exact RC SHA.

1. Freeze the approved RC SHA and record hosted run/job/artifact identifiers.
2. Fetch without rebasing and verify local `main`, `origin/main` and recovery refs against the recorded pre-promotion values.
3. Review the exact `origin/main..RC_SHA` range and confirm no untracked release mutation.
4. Promote using the repository's owner-approved merge policy; never force-push or rewrite accepted history.
5. Run the authoritative workflow against the resulting exact `main` SHA.
6. Record rollback SHA and preserve the RC/recovery branches throughout the rollback window.

Stop on any SHA drift, dirty worktree, missing job, digest mismatch, unexpected schema/package identity change or absent authority. Tagging, release creation, signing and deployment are separate approvals.
