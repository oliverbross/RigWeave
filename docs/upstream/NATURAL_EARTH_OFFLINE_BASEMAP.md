# Natural Earth offline basemap provenance

The RF flat map and globe use a deliberately coarse, compiled-in outline derived from Natural Earth `ne_110m_land` release `v5.1.2`.

- Source: `https://github.com/nvkelso/natural-earth-vector/blob/v5.1.2/geojson/ne_110m_land.geojson`
- Upstream SHA-256: `9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9`
- Upstream size: 138,160 bytes
- Licence: Natural Earth data is public domain; attribution is included voluntarily.
- Transformation: Douglas-Peucker simplification at 1.1 degrees, coordinate rounding to two decimals, then retention of seven major land-outline rings for the low-data offline view.
- Packaging impact: no tile engine, network request, WebView, API key, or runtime dataset; the retained outline is compiled into the desktop renderer.

This basemap is orientation context, not a boundary authority. Endpoint precision and provenance are rendered separately; a DXCC nominal centre remains visibly `COARSE`.

The map computes current UTC solar direction and the terminator locally. Great-circle paths, short/explicit-long path selection, antimeridian segmentation, and bounded control points are computed from observation endpoints. Control points are never labelled as exact MUF; paths below 1,000 km do not receive ionospheric control points, maximum nominal hop length is 3,500 km, and speculative hop count is capped at five.
