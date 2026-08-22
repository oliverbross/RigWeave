# Third-Party and Provenance Audit

RigWeave remains GPL-3.0-only. `NOTICE`, the Wavelog/OpenHamClock/Nexus provenance records, and `core/third_party/iturhfprop/SOURCE_MANIFEST.json` remain the authoritative manifests.

Wavelog behaviour was reviewed at 3.1.0 / `af3256140bd05403b7c4a421746c2ea653a4f04f` under MIT terms. OpenHamClock behaviour was reviewed at stable `d4a50eaaa61d3432a1de5f80cbe61790739930a5`; no upstream implementation was copied in this convergence. Nexus was reviewed at `57d11fd55f098dc9302b6aafed39e6cd4b6db216`, with the later 1.7.6 delta reviewed only for durable mode-visibility lessons.

ITU-R P.533 remains `LICENSE_BLOCKED`. No source, binary, numerical result, or implied calibrated forecast from the blocked component is present. Existing empirical outlooks retain their actual provider/provenance labels.

The release audit checks manifests, schema constants, configuration fixtures and forbidden privacy markers. Legal attribution files must be included unchanged in packaged source distributions.
