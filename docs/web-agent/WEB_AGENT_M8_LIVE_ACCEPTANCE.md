# Web Agent M8 live acceptance

No physical radio, audio device, rotator, provider, TX, or RF acceptance was authorized for M8. Acceptance uses deterministic `DEMO · NO RADIO` fixtures only.

Required checks are: missing/partial/non-WSS configuration fails closed; a vault-held P-256 key signs a fresh challenge; unauthenticated RPC is rejected; allow-listed health RPC dispatches; unknown methods and raw IQ are rejected; disconnect has no offline queue; and shutdown calls Global Stop before service exit.

Live Hosted-provider, production-email, public-DNS, billing, and public-deployment evidence remains explicitly outside this milestone.
