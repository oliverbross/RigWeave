# Intelligent Band Maps operator guide

## Enable and open

Open **Settings → Band Maps**, enable Intelligent Band Maps, and leave **Show in nav** selected. Open **Band Maps** from
the navigation rail or phone bar. Disabling or hiding the feature returns an open Band Maps destination to Settings; it
does not change the radio, network providers or app data.

## Choose a view

- **Multi vertical** shows one horizontal frequency lane per selected band.
- **Multi horizontal** shows one vertical frequency map per selected band and is suited to tablet landscape.
- **Grid overview** gives compact band cards with visible and multiplier counts; select a card to expand it.
- **Single expanded** gives one band more label and collision space.

Band chips toggle the visible set and preserve their order. At least one band always remains selected. Whole-band
segments are the initial default; custom/mode segments are validated frequency ranges and clip display only.

## Presets and truth labels

The supplied presets are All current, Needed DX, Contest S&P, DX Chaser context, Portable activators, RF evidence now
and Watchlist. Built-ins are editable data and are included in configuration backup. Filters decide visibility; ranking
decides order and emphasis. Neither action changes provider, Contest or Chaser state.

`NEEDED`, `WORKED`, `CONFIRMED` and `UNKNOWN` are independent per entity, band, mode, band-mode slot, grid, zone and
portable reference. They are local estimates, not official award adjudication. Contest duplicate/multiplier state comes
from the active Contest evaluator. Chaser labels come from its read-only snapshot; unavailable never means ineligible.

Current observed evidence, empirical outlook and historical personal context are shown separately. The app does not
combine them into a percentage or guarantee that a station is workable.

## Colours and accessibility

Text gives the callsign and primary state, borders give Need/Contest/Chaser emphasis, badges give source diversity and
labels explain age and claims. Colour is never the only status cue. The default `COLOUR_VISION_FRIENDLY` palette remains
available. Essential actions work with touch and keyboard and do not require hover.

## Marks, traversal and keyboard

Watch boosts local priority, Pin retains a stale item without changing its timestamp, and Hide locally removes an item.
All are reversible and never broadcast to a cluster or N1MM. Source refresh does not erase local mark data.

Use **N** or Down Arrow for next, **P** or Up Arrow for previous, **F** for filters and Escape to close filters/details.
F1-F12 are deliberately ignored so the integrated Keyer retains physical-hotkey ownership.

## Spot details and safe actions

Select a label to see frequency, sources and spotters, age, Needs, Contest, Chaser, evidence, portable reference and the
explainable priority components.

- **Review RX** creates a typed receive-frequency review. It does not send CAT until the existing confirmation policy is satisfied.
- **DX details**, **History** and **Open Chaser** navigate to the existing owner. They do not tune, log, start a Chaser session or select a Chaser target.

Selection, traversal, filters, presets, restore and navigation send no CAT. Band Maps cannot key PTT, send a Keyer macro,
enable Digi TX, log a QSO, start Assist/Dry Run/Chase or accept a cross-band recommendation.

## Limitations

Provider rows appear only when the existing configured owner supplies them. Missing CTY, projection, Contest, Chaser,
Keyer or provider data remains unknown/unavailable. Apple and desktop Band Maps are not included. Physical device,
screen-reader, live CAT, audio and RF validation were outside Task C.

