# Live acceptance

Live acceptance is intentionally not executed by this source/build programme. No APK install and no radio connection are authorized.

Before product adoption can claim device acceptance, test on a protected device with a reversible installation plan and preserved operator data:

- USB attach/detach and permission denial/grant;
- stable-device selection across reconnect;
- baud/framing/control-line application by the Android serial owner;
- connect, poll, cancel, disconnect, background/foreground, and rapid reconnect;
- read-only proof that frequency/mode/PTT/TUNE writes are rejected;
- model-specific frequency, mode, split, RIT/XIT, levels, functions, and parameters;
- deliberate PTT/TUNE authority, timeout, emergency release, and RF observation;
- compact and expanded UI on the target display;
- authenticated network transports, if later supported, without credential logging.

Build logs, native symbols, unit tests, and emulator tests must not be cited as evidence for these physical items.
