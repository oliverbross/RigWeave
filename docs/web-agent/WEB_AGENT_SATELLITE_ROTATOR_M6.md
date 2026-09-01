# Satellite and Rotator boundary

Satellite calculations and pass selection are read-model operations. Movement is a separate Agent-owned path requiring `ROTATOR_OPERATOR_CAPABILITY`, a short-lived rotator lease and a distinct movement arm. Targets are bounded to azimuth 0–450 degrees and elevation 0–180 degrees.

Deterministic debug movement returns `physicalMovement=false`. Physical builds remain unavailable until a later hardware acceptance programme. Global Stop and local pre-emption clear every movement authority and request rotator Stop.
