# Vigilant GitHub engine: live status

**Idle (the phone's switch is off)** · updated Nov 7 19:26:16 UTC (0 s ago) · run `42401070939` · pass 0

_Reload the page for the latest. Files: [status.json](https://github.com/tjshea90/novig/blob/cloud-feed/status.json) · [usage.json](https://github.com/tjshea90/novig/blob/cloud-feed/usage.json) · [trades.json](https://github.com/tjshea90/novig/blob/cloud-feed/trades.json) · [engine-log.txt](https://github.com/tjshea90/novig/blob/cloud-feed/engine-log.txt)_

## Engine

| | |
|:--|:--|
| Last scan | none finished yet |
| List age | Novig prices median n/a (oldest n/a) · fair odds median n/a (oldest n/a) |
| Novig | Novig read-only key active: 16 reads a second, websocket off (reads by request) |
| Novig and this machine's address | accepted by Novig |
| Runner address | accepted by Novig (no refusal; 0 replaced runners in a row) |
| Trading | OFF |
| Wallet | SLEEPING: the trading wallet has $0.01, under $0.50: no scans or feed calls until it is funded (balance re-read every 30 min, or when you pull to refresh) |

## APIs: working or not, and what each has left

Health is from the **last finished scan** (no report yet: this engine build is older than the page, the next job fills it in): leagues answered fresh+reused, Novig games matched. An API the phone has switched off, or with no key on GitHub, is never called and shows as off.

| API | Health | Leagues (fresh+reused) | Games matched | Left of allowance | Resets | Calls today | Throttled | Note |
|:--|:--|--:|--:|--:|:--|--:|--:|:--|
| SportsGameOdds | … no scan report yet | — | — | no allowance published | — | 0 | 0 |  |
| BoltOdds | … no scan report yet | — | — | no allowance published | — | 0 | 0 |  |
| OddsPapi Free (v4) | … no scan report yet | — | — | 740 of 1,000 requests (74%) | Dec 1 00:00:00 UTC | 104 | 0 |  |
| Pinnacle (PinnWire) | … no scan report yet | — | — | no allowance published | — | 0 | 0 |  |
| Pinnacle (pinnapi) | … no scan report yet | — | — | no allowance published | — | 76 | 0 |  |
| PropLine | … no scan report yet | — | — | 244,647 of 252,000 requests (97%) | Nov 8 00:00:00 UTC | 6,716 | 0 |  |
| ParlayAPI | … no scan report yet | — | — | 852,097 of 1,028,000 credits (82%) | Dec 1 00:00:00 UTC | 8,927 | 0 |  |
| The Odds API | … no scan report yet | — | — | 9,087 of 10,500 credits (86%) | Dec 1 00:00:00 UTC | 8,867 | 0 |  |
| Novig | … no scan report yet | — | — | no key, not metered | — | 242 | 0 |  |
| Polymarket | … no scan report yet | — | — | no key, not metered | — | 29,121 | 0 |  |
| Kalshi | … no scan report yet | — | — | no key, not metered | — | 20,795 | 0 |  |
| SX Bet | … no scan report yet | — | — | no allowance published | — | 28,834 | 0 |  |
| SharpAPI | … no scan report yet | — | — | no allowance published | — | 7,497 | 0 |  |

## Usage meters per key

A key is shown by an id (never the key). **provider** = the provider's own figure, read at the time shown; **count** = the engine's own tally. The active key is the one the next call uses.

| API | Key | State | Used | Left | Figure | Until |
|:--|:--|:--|--:|--:|:--|:--|
| SportsGameOdds | `f8bf…a824` | ▶ active | 0 | — | count | — |
| BoltOdds | `1b46…70df` | ▶ active | 0 | — | count | — |
| BoltOdds | `d9ad…19a9` | standby | 0 | — | count | — |
| BoltOdds | `958c…b636` | standby | 0 | — | count | — |
| OddsPapi Free (v4) | `11a6…65d8` | spent | 250 / 250 | 0 | provider, read 6 h ago | Dec 1 00:00:00 UTC |
| OddsPapi Free (v4) | `dea8…2ee4` | ▶ active | 10 / 250 | 240 | provider, read 6 h ago | — |
| OddsPapi Free (v4) | `1850…cd9a` | standby | 0 / 250 | 250 | provider, read 6 h ago | — |
| OddsPapi Free (v4) | `4e91…cc8e` | standby | 0 / 250 | 250 | provider, read 6 h ago | — |
| Pinnacle (pinnapi) | `c24a…6908` | refused by provider | 0 / 100 | 100 | count | Nov 8 00:00:00 UTC |
| PropLine | `b08b…ee26` | ▶ active | 6,921 / 250,000 | 243,079 | provider, read 65 min ago | — |
| PropLine | `7671…2805` | standby | 216 / 1,000 | 784 | provider, read 65 min ago | — |
| PropLine | `28e7…8eab` | standby | 216 / 1,000 | 784 | provider, read 65 min ago | — |
| ParlayAPI | `378f…8bcf` | spent | 1,000 / 1,000 | 0 | provider, read 66 min ago | Dec 1 00:00:00 UTC |
| ParlayAPI | `dbe1…b552` | ▶ active | 171,483 / 1,000,000 | 828,517 | provider, read 66 min ago | — |
| ParlayAPI | `7969…6028` | standby | 905 / 1,000 | 95 | provider, read 66 min ago | — |
| ParlayAPI | `3a90…adcc` | standby | 812 / 20,000 | 19,188 | provider, read 66 min ago | — |
| ParlayAPI | `6713…3dff` | standby | 899 / 1,000 | 101 | provider, read 66 min ago | — |
| ParlayAPI | `b30c…c097` | standby | 804 / 1,000 | 196 | provider, read 66 min ago | — |
| ParlayAPI | `6612…6ae5` | standby | 0 / 1,000 | 1,000 | provider, read 66 min ago | — |
| ParlayAPI | `9c5d…94bc` | standby | 0 / 1,000 | 1,000 | provider, read 66 min ago | — |
| ParlayAPI | `f85a…9ade` | standby | 0 / 1,000 | 1,000 | provider, read 66 min ago | — |
| ParlayAPI | `7dc4…d6cb` | standby | 0 / 1,000 | 1,000 | provider, read 66 min ago | — |
| The Odds API | `738b…7c44` | ▶ active | 110 / 500 | 390 | provider, read 64 min ago | — |
| The Odds API | `3015…5ed6` | standby | 103 / 500 | 397 | provider, read 64 min ago | — |
| The Odds API | `8257…3a13` | standby | 64 / 500 | 436 | provider, read 64 min ago | — |
| The Odds API | `a1b8…6b82` | standby | 64 / 500 | 436 | provider, read 64 min ago | — |
| The Odds API | `72f6…bb0a` | standby | 64 / 500 | 436 | provider, read 64 min ago | — |
| The Odds API | `69fe…5aa7` | standby | 64 / 500 | 436 | provider, read 64 min ago | — |
| The Odds API | `a258…c0f2` | standby | 64 / 500 | 436 | provider, read 64 min ago | — |
| The Odds API | `e4d5…b03d` | standby | 64 / 500 | 436 | provider, read 64 min ago | — |
| The Odds API | `fa28…c5ab` | standby | 64 / 500 | 436 | provider, read 64 min ago | — |
| The Odds API | `5159…32a3` | standby | 63 / 500 | 437 | provider, read 64 min ago | — |
| The Odds API | `8474…764e` | standby | 63 / 500 | 437 | provider, read 64 min ago | — |
| The Odds API | `3ee2…e928` | standby | 62 / 500 | 438 | provider, read 64 min ago | — |
| The Odds API | `0a75…85c4` | standby | 62 / 500 | 438 | provider, read 64 min ago | — |
| The Odds API | `07c4…7afe` | standby | 62 / 500 | 438 | provider, read 64 min ago | — |
| The Odds API | `7950…f471` | standby | 62 / 500 | 438 | provider, read 64 min ago | — |
| The Odds API | `144e…cabd` | standby | 62 / 500 | 438 | provider, read 64 min ago | — |
| The Odds API | `1729…3dcb` | standby | 64 / 500 | 436 | provider, read 64 min ago | — |
| The Odds API | `38d0…6fff` | standby | 63 / 500 | 437 | provider, read 64 min ago | — |
| The Odds API | `5ded…4d56` | standby | 63 / 500 | 437 | provider, read 64 min ago | — |
| The Odds API | `1c7d…9c86` | standby | 63 / 500 | 437 | provider, read 64 min ago | — |
| The Odds API | `9f78…0966` | standby | 63 / 500 | 437 | provider, read 64 min ago | — |
| SX Bet | `fe2a…3d41` | ▶ active | 0 | — | count | — |
| SharpAPI | `0c67…be20` | ▶ active | 6,902 | — | count | — |

## Order the engine asks the APIs (best first, measured)

- ParlayAPI (p90 3 min) > SGO (not measured) > ParlayAPI (p90 12 min) > PropLine (p90 21 min) > The Odds API (p90 26 s) > OddsPapi Free (not measured)
- PropLine (p90 2 min) > ParlayAPI (p90 3 min) > SGO (not measured) > OddsPapi Free (not measured) > The Odds API (not measured)
- always asked, not ranked: Kalshi (p90 <1 s); Pinnacle feed (p90 12 min); Polymarket (p90 <1 s); PropLine (p90 2 min); sharpapi (not measured); SX Bet (p90 <1 s)

_Rules this page follows: only the engine's own calls are counted; an API the phone switched off is never called; no key or call time is ever written to this public repository._
