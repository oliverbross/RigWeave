# Sweep 3 later-merge contract

This branch begins at Sweep 2 `d1e956d2c21eefc905a5ecab086a8f467b7a03c4` and intentionally does not merge Tablet Acceptance Sweep 3.

Desktop-owned additions:

- `desktop/**`
- `docs/desktop/**`
- `cmake/desktop/**`
- `.github/workflows/windows-desktop.yml`
- `.github/workflows/desktop-cross-platform.yml`
- additive root `CMakePresets.json`

Shared edit: `core/CMakeLists.txt` only, for MSVC warning flags and position-independent static-library compatibility. Potential conflicts are therefore limited to later changes in that build file, root presets, workflow names and desktop documentation.

`android/**`, `ios/**`, mobile navigation/controllers/configuration/databases, Gradle and Xcode projects are intentionally untouched.

The controlled merge must start from the exact final Sweep 3 tip, verify both tips, merge this Windows branch exactly once with `git merge --no-ff`, resolve the narrow build conflict semantically if present, run desktop/core plus required Sweep 3 gates, and preserve the singular mobile authorities. It must not merge Sweep 3 back into this feature branch.
