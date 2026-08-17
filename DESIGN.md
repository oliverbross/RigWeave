# RigWeave Flightline design system

## Direction

RigWeave is one coherent field instrument across SwiftUI, Compose, and the planned Qt/QML desktop client. The visual language is shared; navigation, focus, dialogs, menus, keyboard, pointer, windowing, and accessibility follow each platform.

The instrument is a low-glare graphite chassis around an amber measurement surface. Off-white carries primary labels, yellow carries secondary/hold meaning, green signals healthy observed state, and red is reserved for safety and transmit state. Colour is always reinforced by text, shape, symbol, or state wording.

Dense radio information is intentional. Do not turn the operator console into a grid of decorative dashboard cards. Spectrum and waterfall are operational instrumentation where they are implemented, not ambient animation.

## Durable rules

- Live radio truth dominates hierarchy: dual VFO, mode, split/RIT/XIT, meters, gain, bandwidth, power, and connection state remain legible.
- Interpret the KX3 faceplate without copying its physical limitations. Keep the amber hierarchy, raised control language, off-white primary labels, and yellow secondary labels.
- Use platform-appropriate touch targets and accessibility APIs. Android targets at least 48 dp; Apple follows current Human Interface Guidelines and Dynamic Type/VoiceOver behaviour.
- Compact, portrait, landscape, multi-window, keyboard, and pointer layouts must reveal unavailable controls rather than silently clipping them.
- Potentially transmitting controls require explicit state, confirmation where appropriate, and an immediately available abort/Emergency RX path.
- No colour-only status, fabricated signal animation, placeholder QSO, or generated spectrum.
- The panadapter axis follows live CAT and the physical audio sample rate. Empty/offline input stays visibly offline.
- Future desktop panels may resize or detach, but no desktop implementation is implied by this contract.
- Before public binary distribution, an About/Licences surface must make GPL and applicable third-party notices readily accessible.

## Current surface truth

Android implements the KX3-style console plus the broader Neural DX workspace. Apple implements a native iPad-focused navigation and radio/log/DX/panadapter flow. Their destination sets need not be forced into artificial parity; durable behaviour and evidence must remain clear.

Phase 0 changes no navigation or operator-facing UI.
