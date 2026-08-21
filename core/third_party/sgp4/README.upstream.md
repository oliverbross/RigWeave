# SGP4

C++17 implementation of the SGP4 satellite orbit propagation algorithm.

## Requirements

- C++17 compiler
- CMake 3.14+
- Google Test (fetched automatically)

## Building

```bash
mkdir build && cd build
cmake ..
cmake --build .
```

Default install prefix is `build/install/`. Override with:

```bash
cmake .. -DCMAKE_INSTALL_PREFIX=/usr/local
```

### Build options

| Option | Default | Description |
|--------|---------|-------------|
| `BUILD_EXAMPLES` | `ON` | Build example programs |
| `BUILD_TESTS` | `ON` | Build unit tests |

## Tests

```bash
cd build
ctest --output-on-failure
```

## Examples

| App | Description |
|-----|-------------|
| `runtest` | Verification against `SGP4-VER.TLE` |
| `sattrack` | Track a satellite from a ground observer |
| `passpredict` | Predict satellite passes over a location |
| `csvprop` | Propagate satellites from a CelesTrak CSV file |

## Usage

```cpp
#include <libsgp4/Tle.h>
#include <libsgp4/SGP4.h>
#include <libsgp4/Eci.h>

libsgp4::Tle tle("SAT NAME",
    "1 35683U 09041C   12289.23158813  .00000484  00000-0  89219-4 0  5863",
    "2 35683  98.0221 185.3682 0001499 100.5295 259.6088 14.69819587172294");
libsgp4::SGP4 sgp4(tle);

libsgp4::Eci eci = sgp4.FindPosition(tle.Epoch());
libsgp4::Vector pos = eci.Position();
```

### Loading from CelesTrak CSV

```cpp
#include <libsgp4/CsvTleLoader.h>

auto tles = libsgp4::LoadCsvTleFile("path/to/celestrak.csv");
libsgp4::SGP4 sgp4(tles[0]);
```

## Library classes

| Class | Description |
|-------|-------------|
| `SGP4` | Core propagator |
| `Tle` | Two-line element set parser |
| `Eci` | Position/velocity in TEME frame |
| `DateTime` | Date/time with microsecond precision |
| `TimeSpan` | Duration |
| `Observer` | Ground station observer |
| `CoordGeodetic` | Lat/lon/alt |
| `CoordTopocentric` | Azimuth/elevation/range |
| `Vector` | 3D/4D vector |
| `OrbitalElements` | Keplerian elements |

## Data

`data/geodetic.csv` contains a snapshot of [CelesTrak geodetic satellites](https://celestrak.org/NORAD/elements/gp.php?GROUP=geodetic&FORMAT=csv).
