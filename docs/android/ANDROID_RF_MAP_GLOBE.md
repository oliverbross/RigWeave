# Android RF Map and Globe

`RfObservation` keeps source, evidence class, time, callsign, band, mode, transmitter/receiver coordinates, precision, signal context, worked/confirmed state, needs, Contest state, and chaser priority.

The model is capped at 100,000 observations and publishes filtered generations. The flat map and orthographic globe use Compose Canvas, bounded render batches, antimeridian-safe great-circle paths, optional explicit long path, propagation control points, precision styling, and selection-only interactions.

Observed, historical, and empirical-outlook evidence remain visually and semantically distinct. No map or globe interaction tunes a radio automatically.
