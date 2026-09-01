# Web Agent M8 compatibility

| Surface | Version | M8 behavior |
|---|---:|---|
| Local remote protocol | v1 | Preserved |
| Hosted relay subprotocol | `rigweave.relay.v1` | Added, outbound only |
| Hosted RPC registry | 1.0 | Typed allow-list, unknown methods rejected |
| Raw IQ relay | — | Disabled |
| Offline remote commands | — | Disabled |
| Platform vault | existing desktop abstraction | Required for relay key |

M8 does not change M6 local workflow ownership or the Android reference contract. Older Agents simply have no Hosted presence; Hosted must report them as offline rather than attempting compatibility tunnelling.
