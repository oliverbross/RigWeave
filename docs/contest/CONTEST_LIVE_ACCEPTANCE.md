# Contest live acceptance checklist

Not executed in this branch.

1. Confirm the live event’s sponsor edition and clear every `REVIEW_REQUIRED` rule note.
2. On a disposable test session, compare exchange, dupe, point, multiplier and Cabrillo output with sponsor examples.
3. Prove physical keyboard focus, Run/S&P ESM callbacks and explicit Log/Clear behaviour on tablet and phone.
4. Confirm process death restores score/session paused with networking/keyer disarmed.
5. With a test Wavelog station, confirm standard contest ADIF fields enter the existing outbox and remote round-trip without changing conflict/tombstone behaviour.
6. Validate Cabrillo with the sponsor’s upload checker but do not submit an award entry without operator review.
7. Keep all CAT/PTT/keyer/audio/Digi/RF actions disarmed unless a separate integration acceptance authorizes them.

Pass only with recorded device, authenticated-service and sponsor-validator evidence. A build or automated test is not live acceptance.
