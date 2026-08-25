# Global Stop behavior for TCI and Panadapter v3

Global Stop is singular and idempotent per TCI connection generation. It:

- cancels pending/coalesced radio mutations;
- sends exactly one `trx:<receiver>,false` and one `tune:<receiver>,false` safe request for the transmitter authority;
- detaches every attached TCI I/Q stream;
- stops the exact local receive-audio/IQ source;
- stops receive-audio routing state and clears armed actions through existing owners;
- never sends a TX-on or tune-on command.

Receive-only I/Q therefore **stops after Global Stop**. The bounded DSP display contexts and last rendered rows remain available for review, explicitly stale/frozen; capture does not continue in the background. Reconnection remains explicit except for a separately persisted, user-enabled bounded auto-connect profile.
