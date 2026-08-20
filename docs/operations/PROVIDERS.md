# Operations provider provenance

| Provider | Purpose | Endpoint / feed | Attribution | Cache |
|---|---|---|---|---|
| NG3K ADXO | DXpedition calendar | HTTPS public HTML schedule | Show NG3K ADXO and source link | 30-minute refresh; validated last-good cache retained up to 7 days |
| WA7BNM Contest Calendar | Contest calendar | HTTPS RSS | Show WA7BNM Contest Calendar and link to the published contest/rules page | 30-minute refresh; validated last-good cache retained up to 7 days |
| Parks on the Air | Nearby park references | Existing downloaded HTTPS CSV catalogue | Existing POTA attribution/status in Portable | Existing app-private catalogue cache |
| Summits on the Air | Nearby summit references | Existing downloaded HTTPS CSV catalogue | Existing SOTA attribution/status in Portable | Existing app-private catalogue cache |
| World Wide Flora & Fauna | Nearby references where coordinates exist | Existing HTTPS spots/agenda feeds | Labelled as recent WWFF spot/agenda cache | Existing last-good Portable cache |

Behaviour was reviewed against pinned Wavelog commit `af3256140bd05403b7c4a421746c2ea653a4f04f`:
`application/models/Calendar_model.php`, `application/controllers/Dxcalendar.php`,
`application/controllers/Contestcalendar.php`, `application/views/dxcalendar/index.php`,
`application/views/contestcalendar/index.php`, `application/views/activationplanner/index.php`,
`assets/js/sections/dxcalendar.js`, and `assets/js/sections/activationplanner.js`.

No Wavelog code or data was copied. No third-party overlay dataset was introduced. CQ/ITU/state polygon
toggles report unavailable rather than fabricating boundaries; the planner renders its own Maidenhead cell outline.
