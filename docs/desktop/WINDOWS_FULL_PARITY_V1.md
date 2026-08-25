# Windows Desktop Full Parity v1

## Result

Verdict: **PARTIAL — WINDOWS PARITY BRANCH BUILT WITH EXPLICIT BLOCKER**.

This branch replaces the Alpha placeholder router with a complete, reachable desktop workspace shell and adds bounded provider, domain-store, safety, test, scale, gallery, and packaging infrastructure. It does not claim full Android feature parity: several complex Android workspaces are represented by wired desktop foundations and deterministic fixture models rather than production-equivalent controllers.

## Frozen source

- Source branch: `origin/integration/android-hardened-windows-alpha-v1`
- Source SHA: `d64c9031f75182acf27a10b6d73d73e90e9e9c56`
- Frozen `origin/main`: `fb04d52df0c9ccc305125449bb188ef8e3f0185e`
- Delivery branch: `feature/windows-desktop-full-parity-v1`
- Toolchain: Qt 6.11.2, C++17, CMake/Ninja, pinned Hamlib 4.7.2

## Delivered source

- Nineteen routed destinations, a command palette, native menus/hotkeys, resizable split navigation, Shack Display, and Escape/global Stop.
- One `DesktopApplication` composition root exposing one QSO database, spot repository, Wavelog engine, radio controller, rotator controller, panadapter, credential vault, support-bundle owner, and parity platform.
- A bounded provider policy with HTTPS-only requests, one request per provider, redirect/body/content-type limits, cooldowns, conditional cache validation, `Retry-After`, last-good cache, and sanitized errors.
- Separate versioned SQLite stores for Neural DX (schema 5), Digi (2), Groups.io (2 with FTS5), Contest (2), and DX Chaser (1). A newer unknown schema fails closed.
- Deterministic receive-only gallery mode and scale fixtures. Demo state is isolated from production configuration, credentials, and databases.

## Explicit parity blocker

The native KX3/KX2, Flex, QMX/QMX+, RGO ONE and non-Hamlib rotator selections are truthful UI/profile surfaces but do not yet bind the Android production protocol/controller implementations into the Qt owner graph. Digi, Contest/N1MM, Groups.io, Portable, Operations, Satellite/QO-100, awards and several Home modules likewise have storage, navigation, review actions and fixture views but not complete production service behavior. Connect or action controls remain unavailable where capability is unproven.

That is a deliberate fail-closed boundary. Static UI and fixture-backed screens are not relabelled as full source parity.

## Evidence boundaries

Local desktop, shared native, Rust, Android, iOS, deterministic gallery, scale, database, package and unsigned macOS evidence is recorded in this document set. Windows artifacts and hosted results are exact-SHA CI evidence only. No signing, deployment, protected-tablet change, authenticated-service acceptance, physical audio/CAT/PTT/TUNE/RF, or rotator movement was authorised or performed.
