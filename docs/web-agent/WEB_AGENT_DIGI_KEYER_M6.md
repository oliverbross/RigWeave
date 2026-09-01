# Digi and Keyer boundary

Digi, DX Chaser, Keyer and Voice requests use the M6 typed envelope. Consequential actions require an authenticated OPERATOR, `TX_OPERATOR_CAPABILITY`, matching context and Agent generations, an unexpired request, and a separate short-lived TX lease/arm.

In `--debug-no-radio`, accepted execution returns `rf=false`, `ptt=false` and deterministic fake audio evidence. Outside that isolated mode it returns `TX_UNAVAILABLE_PHYSICAL_ACCEPTANCE_REQUIRED`. No arbitrary CAT string, audio payload or hardware command is accepted by the workflow engine.
