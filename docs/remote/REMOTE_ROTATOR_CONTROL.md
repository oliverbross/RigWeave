# Remote Rotator Control

Rotator state is projected from the existing desktop rotator owner. Stop is always available when the owner exists. Preparing a remote target requires OPERATOR authority, writer lease, exclusive rotator lease, foreground heartbeat, current generation, local rotator policy, and an available owner. Movement is never inferred from a command response.

Disconnect, background, heartbeat expiry, revocation, local pre-emption, or Global Stop clears the lease and requests Stop. Configuration restore is disconnected and disarmed. No physical rotator movement was performed or claimed by source/build tests.

