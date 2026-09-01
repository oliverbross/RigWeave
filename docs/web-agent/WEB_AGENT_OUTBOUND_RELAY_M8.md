# Web Agent outbound relay M8

The Station Agent opens one outbound `wss://` connection using subprotocol `rigweave.relay.v1`. It never opens a public inbound listener for Hosted operation and never acts as an HTTP, TCP, UDP, or WebSocket proxy. Local LAN service remains a separate operator-controlled path.

Configuration is all-or-none: relay URL, hosted station ID, Agent registration ID, public-key ID, and a platform-vault alias. The private P-256 signing key is read only by the Agent from the OS credential vault. It is never sent to Hosted, written to configuration, or included in support bundles.

The client has no offline command queue. Disconnect increments the Agent generation, invalidates remote session assumptions, and requires a new signed challenge. Global Stop is always evaluated by the Agent and stops local authority regardless of Hosted state.

Raw IQ is disabled over Hosted relay. Inbound binary frames are rejected. Audio and reduced spectrum are separate bounded subscriptions; the typed RPC allow-list cannot be expanded by Hosted input.
