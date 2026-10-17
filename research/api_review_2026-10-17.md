# API review, 2026-10-17 (TCL101, Tj: "be a smart manager of the apis")

Source: the engine's `status.apiCards` (cloud-feed), `usage.json`, the engine log. Quality = p90 age of each feed's quotes by the books' own stamps, distance from Pinnacle, share of scans answered; value = lines fed and lines ONLY it fed.

| API | paid | role | p90 age | pts off Pinnacle | lines fed / only it | verdict |
|:--|:--|:--|--:|--:|--:|:--|
| SportsGameOdds | yes | active | 10 min | 1.2 | 10,830 / 1,624 | best feed; keep first |
| PropLine | yes | active | 17 min | 1.3 | 12,068 / 918 | keep; the +EV list is retired (-21,000 requests/day) |
| BoltOdds | yes ($349/mo) | **off, no key on GitHub** | n/a | 1.3 | 0 / 0 | **paid and not feeding the engine**: the secret BOLTODDS_API_KEY is absent from the engine ("keys present" lacks it). Send the key (Settings > Cloud scan > Send my API keys) or cancel |
| Kalshi | free | standby | n/a | | 6,316 / 802 | keep (exchange prices, sole lines) |
| Polymarket | free | standby | n/a | | 1,512 / 6 | keep (free, marginal) |
| ParlayAPI | yes (2 Starter keys) | standby | 8 min | | 0 / 0 | 67% of credits left with 47% of the month left, but 0 lines fed: credits will expire unused. A pacing bug locked one key out ("out of usage today"), fixed in v0.114.26 |
| The Odds API | free (14 keys) | standby | 30 s | 11.3 | 0 / 0 | poor vs Pinnacle; standby only (gap filler) |
| **Unabated** | free | active | **12.8 h** | **7.4** | 0 / 0 | **turned off** by the engine's request (answers 57%, quotes half a day old, adds nothing) |
| OddsPapi, OddsPapi Free | free | off | | | | off, nothing to do |
| PinnWire / pinnapi | | standby / off | | | | Pinnacle itself arrives through the "pinnacle" feed |

Actions taken: Unabated switched off (override to the phone, API switches only). Telling Tj: BoltOdds is paid and idle (decision his: send the key or cancel). Not changed: ParlayAPI (surplus credits; its value is unproven: no lines fed, nothing measured against Pinnacle), The Odds API.
Next review: after 24 h of the new build, read `usage.json` per-key spend to judge pace.
