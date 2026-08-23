# Rotator upstream watch

`scripts/check_rotator_upstreams.py` performs bounded read-only checks of the pinned owner repository, official microHAM ARCO product/download/manual/firmware/history sources, and Hamlib 4.7.2 tag/commit/tree. It emits Markdown or JSON and classifies review work without changing source or opening a pull request.

The owner repository may be private. CI must provide `ROTATOR_UPSTREAM_TOKEN`; absence or denial returns `UNAVAILABLE` and a non-zero status without revealing credentials or private contents. microHAM and Hamlib unavailability also fail truthfully.

Review classes include SECURITY, ARCO_PROTOCOL, ARCO_FIRMWARE, ROTATOR_MODEL, HAMLIB_API, SERIAL_TRANSPORT, TCP_TRANSPORT, GEOMETRY, AUTOMATION, SATELLITE, SAFETY, LICENCE and DOCUMENTATION. Weekly/manual workflow results are retained as artifacts.
