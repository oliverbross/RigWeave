# Remote Digi, Keyer, and Voice

Remote Digi/Keyer/Voice messages are typed protocol channels, not arbitrary commands. They must use the existing canonical owners and the same TX authority as local operation. The station advertises the typed capability vocabulary but rejects transmitting mutations while the desktop TX owner is unavailable.

Global Stop stops or disarms remote Digi, keyer and voice work through the existing radio/application stop chain. Voice assets, raw audio, macros, credentials, and pending TX state are not exported or synchronized to the station. The Debug Remote Lab displays synthetic states only and always says `DEMO · NO RADIO`.

