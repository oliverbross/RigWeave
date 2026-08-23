# Capability model

`HamlibModelRegistry` reads a bounded JSON projection generated from the compiled Hamlib registry. It does not expose `RIG*`, pointers, callback state, or backend-private structures to Kotlin or Compose.

Each `HamlibModelDescriptor` includes stable model and backend identifiers, names, driver/status metadata, transport and serial requirements, timeouts, PTT type, targetable VFO flags, RIT/XIT/IF-shift bounds, modes, VFOs, receive/transmit frequency ranges, filters, and readable/writable levels, functions, and parameters.

Bounds are applied natively and again while parsing:

- at most 2,048 models;
- at most 128 frequency ranges per model;
- at most 64 entries in each capability family;
- at most 256 characters in upstream text fields.

The live `HamlibRadioSnapshot` is intentionally smaller: current/A/B frequency, VFO and TX VFO, mode/passband, split, RIT/XIT, PTT, and supported meter/level values. Failed or unavailable reads are omitted or retain a neutral field; they are not presented as calibrated RF measurements.
