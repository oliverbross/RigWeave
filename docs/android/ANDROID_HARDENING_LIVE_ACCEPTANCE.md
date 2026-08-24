# Android hardening live acceptance

Date: 2026-08-25  
Package: `app.rigweave.mobile`

## Mandatory boundary

The protected Lenovo install is allowed only after source, sanitizer, package and exact-SHA hosted gates pass. The app must already be installed, a private-data backup with hashes must complete first, and installation must use only `adb install -r`. No uninstall, data clear, credential display, random monkey input, CAT/PTT/TUNE, RF or rotator motion is permitted.

## Pre-install gates

- Local Android, Rust, native, sanitizer, Apple, package and release-contract gates: PASS.
- Candidate arm64 APK: 58,426,676 bytes; SHA-256 `f99b529f43e28bc16834fd80cd488293234d5399e04a972d2d87ae83240896b9`.
- Exact-SHA hosted workflow: pending final documentation commit and push.

## Device evidence

Pending the hosted gate. Record selected ADB serial, package path, backup directory/hash manifest, pre/post UID, schema version, canonical/projection counts, in-place install result, 25 force-stop/relaunch cycles, 20 foreground/background cycles, safe workspace navigation, 30-minute soak, PSS/native heap/thread/FD samples, final crash buffer and last force-stop/relaunch.

## Evidence not implied

Source/build/install/launch do not prove authenticated Groups.io/Wavelog behavior, real radio control, exact audio routing, CAT/PTT/TUNE, RF transmission, physical rotator motion or complete gesture/visual acceptance. Those layers remain pending unless separately performed under explicit authority.

