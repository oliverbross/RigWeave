# Third-Party and Provenance Audit

RigWeave remains GPL-3.0-only. `NOTICE`, the Wavelog/OpenHamClock/Nexus provenance records, and `core/third_party/iturhfprop/SOURCE_MANIFEST.json` remain the authoritative manifests.

Wavelog behaviour was reviewed at 3.1.0 / `af3256140bd05403b7c4a421746c2ea653a4f04f` under MIT terms. OpenHamClock behaviour was reviewed at stable `d4a50eaaa61d3432a1de5f80cbe61790739930a5`; no upstream implementation was copied in this convergence. Nexus was reviewed at `57d11fd55f098dc9302b6aafed39e6cd4b6db216`, with the later 1.7.6 delta reviewed only for durable mode-visibility lessons.

ITU-R P.533 remains `LICENSE_BLOCKED`. No source, binary, numerical result, or implied calibrated forecast from the blocked component is present. Existing empirical outlooks retain their actual provider/provenance labels.

The release audit checks manifests, schema constants, configuration fixtures and forbidden privacy markers. Legal attribution files must be included unchanged in packaged source distributions.

Sweep 2 reuses the same byte-preserved Hamlib 4.7.2 archive for both radio and rotator APIs. It does not add a second Hamlib build, downloaded backend, proprietary ARCO source or copied firmware table. Native QMX behavior derives from the reviewed QMX branch and published CAT/USB behavior; RGO ONE is bounded to the reviewed V6 source and conservative unknown-generation behavior. microHAM ARCO is supported only through its published GS-232/EasyComm compatibility modes; no proprietary protocol claim is made.

Sweep 3 exposes a bounded in-product acknowledgement registry derived from `NOTICE` and these manifests. It introduces no new third-party source. WWFF Spotline public JSON is attributed to WWFF and refreshed no faster than official guidance; the protected WWFF Directory is neither scraped nor bundled. CQ/ITU/state overlays remain unavailable because no legally reviewed pinned geometry is packaged.
