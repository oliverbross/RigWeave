# OpenHamClock integration decisions

These decisions apply to the RigWeave OpenHamClock completion programme unless a later owner-approved decision supersedes them.

## Native clients, no embedded React

Android uses Jetpack Compose and iOS/iPadOS uses SwiftUI. RigWeave will not embed the upstream React application in a WebView, ship its Express server, or download/execute its JavaScript at runtime.

## Behaviour parity, not pixel cloning

Parity is judged by truthful operator outcomes. OpenHamClock layout density and information hierarchy may inform the native design, but upstream pixels, logos, artwork, trade dress and React components are not cloned.

## RigWeave workspaces remain authoritative

Radio, Digi, DX, Portable, Operations, Satellite, Logbook, Wavelog and Log Intelligence retain ownership of their established outcomes. Home summarizes or delegates to them rather than creating weaker duplicate implementations.

## No shared OpenHamClock DXSpider proxy

RigWeave keeps its user-configured DX-cluster connection and does not adopt the upstream shared DXSpider proxy. Existing session identity, persistence and operator configuration remain authoritative.

## No runtime source-code updating

Installed clients and workflows never download and execute upstream source. The watcher reads GitHub metadata/content only and never changes RigWeave source or pins automatically.

## Stable updates require review

A stable `main` change may open/update one audit issue and may lead to a separately reviewed implementation branch. It never auto-merges adapted code.

## Preview is report-only

`Staging` is a separate, potentially divergent preview channel. Preview changes are reported only; only security or provider-contract preview changes may open/update the audit issue.

## No unattended tune, TX or PTT

Home remains observational except for existing explicit, bounded, operator-initiated controls. No provider result, watcher result, timer or background refresh may tune a radio, arm transmission, key PTT, or retry a transmit-capable action unattended.

## Provenance and licence

The audited upstream is `accius/openhamclock` at immutable stable commit `d4a50eaaa61d3432a1de5f80cbe61790739930a5`, MIT, Copyright 2024-2026 OpenHamClock Contributors. Repository attribution is retained in `NOTICE`. No upstream implementation code is copied by this phase.
