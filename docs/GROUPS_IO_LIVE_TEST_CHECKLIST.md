# Groups.io Owner Live-Test Checklist

Use a disposable, narrowly scoped API key. Never paste it into logs, screenshots, issues or test fixtures. Perform these checks only in private groups where test posts and files are authorised. Revoke the key when finished.

## Before testing

- Record the app commit, platform/version and disposable account/key creation time.
- Back up operator data and record SHA-256 hashes of the main QSO database before the session.
- Confirm Groups.io is disabled by default and existing downloaded/draft data is intact.
- Choose groups that safely represent read-only, normal post/reply, moderated post and archive-export permissions without naming them in public evidence.

## Authentication and permissions

- Connect with the disposable API key; confirm no password, login cookie or CSRF flow appears.
- Sync memberships and compare read/post/reply/export actions with real account permissions.
- Open a group offline first, then online; confirm cached content appears before permission refresh and stale permission wording is honest.

## Drafts and outbox

- Create a local draft, type for several seconds, close, restart and confirm exact local recovery with no server draft.
- Disable and disconnect while composing; confirm autosave, no send, and credential/content preservation rules.
- Choose Send When Online while offline; confirm nothing leaves until reconnect plus an explicit foreground Process Queued action.
- Rapidly tap actions and navigate/recompose; confirm only one remote draft/post sequence occurs.

## New topic, reply and moderation

- Send one authorised new topic and verify subject/body/paragraph escaping on Groups.io.
- Reply to a message with an authoritative message ID; check each permitted destination label against actual delivery.
- Verify a closed/locked topic blocks an invalid reply.
- Submit one moderated post and confirm Pending Moderation, not failure or ordinary posted state.
- Simulate connection loss around `/postdraft`; confirm Delivery Unknown and no automatic retry. Reconcile before deliberately choosing any retry.

## Attachments

- Select multiple outgoing files, including duplicate names and an unsafe-looking filename; confirm immediate private copy, sanitised collision-safe names, size and SHA-256.
- Upload, reconcile remote IDs and confirm a successful file is not uploaded twice.
- Remove one uploaded draft attachment and confirm `/deleteattachment` success.
- Open a message with incoming attachments; confirm no automatic download or remote image load.
- Download one attachment, interrupt another, retry, then open/share offline. Confirm partial removal, HTTPS/fresh URL use and no `file://` exposure.
- Confirm the 100 MiB technical ceiling without treating it as the service policy.

## Search and archives

- Compare Downloaded FTS results offline with Groups.io online search for one selected group.
- Exercise online Load More, cancellation and result opening; confirm the authoritative message is then available offline.
- Start a complete archive, cancel after several pages, restart the app, resume and verify counts without duplicates.
- Confirm completion appears only after the authoritative final page and FTS finds early/middle/late archive messages offline.
- In an export-permitted group, acknowledge the 24-hour warning, download one official ZIP and share/export it. Do not repeat within 24 hours and do not parse the MBOX.

## Deletion and isolation

- Clear Downloaded Cache and confirm local drafts, queue, outgoing files, credential and server-reconciliation IDs remain.
- Remove one group's archive and confirm drafts/outbox plus every other group remain.
- With unsent drafts present, verify Delete All requires the stronger warning/second confirmation and only deletes Groups.io local storage.
- Disconnect and confirm the key is gone while cache/drafts/outbox/files remain.
- Reconnect and confirm remote-linked outbox items require review before processing.
- Re-hash the main QSO database and verify it is unchanged.
- Revoke the disposable API key at Groups.io and verify subsequent requests fail safely without losing cached content.

Do not call the feature production-verified until all applicable items pass on Android and Apple, including an Apple compile/runtime pass that is outside the Phase 2 implementation task.
