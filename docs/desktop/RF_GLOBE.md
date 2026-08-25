# Interactive RF globe

The RF globe uses the public `QQuickPaintedItem` scene-graph path already shipped by Qt Quick; Qt Quick 3D and private Qt APIs are not dependencies. The same singular C++ observation/filter model drives the flat and globe projections.

Deterministic acceptance covers:

- orthographic coastlines from the packaged offline Natural Earth derivative;
- pointer rotation, wheel zoom, and pinch zoom;
- selected great-circle arcs and bounded propagation control points;
- station and target markers, including hollow `COARSE` endpoints;
- current-UTC solar direction, terminator, and day/night shading;
- shared source/band/evidence/age filters and selection details;
- selection-only interaction with explicit QSY and workspace handoffs.

The renderer has no WebView, network tile request, commercial key, Qt Quick 3D runtime, or private Qt API. Clicking or rotating the globe cannot tune, log, move a rotator, transmit, or publish.
