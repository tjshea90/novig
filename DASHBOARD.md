# Vigilant GitHub engine: live status

**Scanning** · updated Oct 17 19:46:33 UTC (0 s ago) · run `39063659169` · pass 0

_Reload the page for the latest. Files: [status.json](https://github.com/tjshea90/novig/blob/cloud-feed/status.json) · [usage.json](https://github.com/tjshea90/novig/blob/cloud-feed/usage.json) · [trades.json](https://github.com/tjshea90/novig/blob/cloud-feed/trades.json) · [engine-log.txt](https://github.com/tjshea90/novig/blob/cloud-feed/engine-log.txt)_

## Engine

| | |
|:--|:--|
| Scan now | Fair odds 69/79 · Novig prices (8904/10936) · running 66 s |
| Last scan | none finished yet |
| List age | Novig prices median 11 s (oldest 56 s) · fair odds median 6 min (oldest 9 min) |
| Novig | Novig read-only key active: 16 reads a second, websocket on |
| Novig and this machine's address | accepted by Novig |
| Runner address | accepted by Novig (no refusal; 0 replaced runners in a row) |
| Trading | LIVE: live: looked 0, passed 0, ordered 0 ($0.00), stopped: Novig took the order (01a14b58-b8ed-7f40-ab4e-52738c801b17) but had not finished it after 12s: kept as at risk, not sent again · **STOPPED: Novig took the order (01a14b58-b8ed-7f40-ab4e-52738c801b17) but had not finished it after 12s: kept as at risk, not sent again** |
| Wallet | cash $58.59 · equity $58.59 · read 66 s ago |

## APIs: working or not, and what each has left

Health is from the **last finished scan** (no report yet: this engine build is older than the page, the next job fills it in): leagues answered fresh+reused, Novig games matched. An API the phone has switched off, or with no key on GitHub, is never called and shows as off.

| API | Health | Leagues (fresh+reused) | Games matched | Left of allowance | Resets | Calls today | Throttled | Note |
|:--|:--|--:|--:|--:|:--|--:|--:|:--|
| SportsGameOdds | … no scan report yet | — | — | no allowance published | — | 14,830 | 0 |  |
| BoltOdds | … no scan report yet | — | — | no allowance published | — | 1,519 | 1275 |  |
| OddsPapi | … no scan report yet | — | — | no allowance published | — | 0 | 0 |  |
| OddsPapi Free (v4) | … no scan report yet | — | — | 250 of 250 requests (100%) | Nov 1 00:00:00 UTC | 0 | 0 |  |
| Unabated | … no scan report yet | — | — | no allowance published | — | 555 | 0 |  |
| Pinnacle (PinnWire) | … no scan report yet | — | — | 449 of 600 requests (74%) | Oct 18 00:00:00 UTC | 151 | 0 |  |
| Pinnacle (pinnapi) | … no scan report yet | — | — | 300 of 300 requests (100%) | Oct 18 00:00:00 UTC | 0 | 0 |  |
| PropLine | … no scan report yet | — | — | 7,791 of 27,000 requests (28%) | Oct 18 00:00:00 UTC | 18,371 | 0 |  |
| ParlayAPI | … no scan report yet | — | — | 27,139 of 48,000 credits (56%) | Nov 1 00:00:00 UTC | 823 | 0 |  |
| The Odds API | … no scan report yet | — | — | 3,707 of 7,000 credits (52%) | Nov 1 00:00:00 UTC | 498 | 0 |  |
| Novig | … no scan report yet | — | — | no key, not metered | — | 549,413 | 524 |  |
| Polymarket | … no scan report yet | — | — | no key, not metered | — | 10,224 | 0 |  |
| Kalshi | … no scan report yet | — | — | no key, not metered | — | 9,754 | 0 |  |

## Usage meters per key

A key is shown by an id (never the key). **provider** = the provider's own figure, read at the time shown; **count** = the engine's own tally. The active key is the one the next call uses.

| API | Key | State | Used | Left | Figure | Until |
|:--|:--|:--|--:|--:|:--|:--|
| SportsGameOdds | `fd1c…a36f` | ▶ active | 14,830 | — | count | — |
| BoltOdds | `d158…7404` | ▶ active | 89 | — | count | — |
| BoltOdds | `1b46…70df` | standby | 152 | — | count | — |
| OddsPapi Free (v4) | `11a6…65d8` | ▶ active | 0 / 250 | 250 | count | — |
| Unabated | `fd8c…e236` | ▶ active | 555 | — | count | — |
| Pinnacle (PinnWire) | `f4ba…4f76` | spent | 99 / 100 | 0 | provider | Oct 18 00:00:01 UTC |
| Pinnacle (PinnWire) | `e17e…591a` | ▶ active | 51 / 100 | 49 | count | — |
| Pinnacle (PinnWire) | `af6f…d5ca` | standby | 0 / 100 | 100 | count | — |
| Pinnacle (PinnWire) | `ac68…573f` | standby | 0 / 100 | 100 | count | — |
| Pinnacle (PinnWire) | `e3a0…0650` | standby | 0 / 100 | 100 | count | — |
| Pinnacle (PinnWire) | `d568…77fe` | standby | 0 / 100 | 100 | count | — |
| Pinnacle (pinnapi) | `9393…275a` | ▶ active | 0 / 100 | 100 | count | — |
| Pinnacle (pinnapi) | `5d04…aff8` | standby | 0 / 100 | 100 | count | — |
| Pinnacle (pinnapi) | `5e9a…aed2` | standby | 0 / 100 | 100 | count | — |
| PropLine | `b08b…ee26` | ▶ active | 19,109 / 25,000 | 5,891 | provider, read 7 s ago | — |
| PropLine | `7671…2805` | standby | 50 / 1,000 | 950 | provider, read 44 s ago | — |
| PropLine | `28e7…8eab` | standby | 50 / 1,000 | 950 | provider, read 43 s ago | — |
| ParlayAPI | `378f…8bcf` | ▶ active | 9,941 / 20,000 | 10,059 | provider, read 14 s ago | — |
| ParlayAPI | `dbe1…b552` | standby | 992 / 1,000 | 8 | provider, read 65 s ago | — |
| ParlayAPI | `7969…6028` | standby | 932 / 1,000 | 68 | provider, read 61 s ago | — |
| ParlayAPI | `3a90…adcc` | standby | 3,385 / 20,000 | 16,615 | provider, read 56 s ago | — |
| ParlayAPI | `6713…3dff` | standby | 946 / 1,000 | 54 | provider, read 56 s ago | — |
| ParlayAPI | `b30c…c097` | standby | 940 / 1,000 | 60 | provider, read 54 s ago | — |
| ParlayAPI | `6612…6ae5` | standby | 941 / 1,000 | 59 | provider, read 53 s ago | — |
| ParlayAPI | `9c5d…94bc` | standby | 932 / 1,000 | 68 | provider, read 48 s ago | — |
| ParlayAPI | `f85a…9ade` | standby | 927 / 1,000 | 73 | provider, read 47 s ago | — |
| ParlayAPI | `7dc4…d6cb` | standby | 925 / 1,000 | 75 | provider, read 46 s ago | — |
| The Odds API | `738b…7c44` | spent | 500 / 500 | 0 | provider, read 65 h ago | Nov 1 00:00:00 UTC |
| The Odds API | `3015…5ed6` | spent | 500 / 500 | 0 | provider, read 65 h ago | Nov 1 00:00:00 UTC |
| The Odds API | `8257…3a13` | ▶ active | 499 / 500 | 1 | provider, read 22 h ago | — |
| The Odds API | `a1b8…6b82` | spent | 0 / 500 | 0 | provider | Nov 1 00:00:00 UTC |
| The Odds API | `72f6…bb0a` | standby | 499 / 500 | 1 | provider, read 27 h ago | — |
| The Odds API | `69fe…5aa7` | spent | 500 / 500 | 0 | provider, read 23 h ago | Nov 1 00:00:00 UTC |
| The Odds API | `a258…c0f2` | standby | 69 / 500 | 431 | provider, read 22 h ago | — |
| The Odds API | `e4d5…b03d` | standby | 34 / 500 | 466 | provider, read 12 s ago | — |
| The Odds API | `fa28…c5ab` | standby | 30 / 500 | 470 | provider, read 13 h ago | — |
| The Odds API | `5159…32a3` | standby | 30 / 500 | 470 | provider, read 7 h ago | — |
| The Odds API | `8474…764e` | standby | 33 / 500 | 467 | provider, read 6 h ago | — |
| The Odds API | `3ee2…e928` | standby | 33 / 500 | 467 | provider, read 5 h ago | — |
| The Odds API | `0a75…85c4` | standby | 33 / 500 | 467 | provider, read 4 h ago | — |
| The Odds API | `07c4…7afe` | standby | 33 / 500 | 467 | provider, read 78 min ago | — |

## Order the engine asks the APIs (best first, measured)

- ParlayAPI (p90 41 s, 1.2 pts off Pinnacle) > OddsPapi (not measured) > SGO (p90 8 min, 1.3 pts off Pinnacle) > ParlayAPI (p90 8 min) > PropLine (p90 15 min, 1.4 pts off Pinnacle) > OddsPapi Free (not measured) > Unabated (p90 4 h, 7.4 pts off Pinnacle, INACCURATE) > The Odds API (p90 <1 s, 11.3 pts off Pinnacle, INACCURATE)
- ParlayAPI (p90 105 s) > PropLine (p90 4 min) > OddsPapi (not measured) > SGO (p90 7 min) > Unabated (p90 12 h) > The Odds API (p90 30 s) > OddsPapi Free (not measured)
- always asked, not ranked: BoltOdds (p90 <1 s, 1.3 pts off Pinnacle); BoltOdds (p90 <1 s); Kalshi (p90 <1 s); Pinnacle feed (p90 <1 s); Polymarket (p90 <1 s)

_Rules this page follows: only the engine's own calls are counted; an API the phone switched off is never called; no key or call time is ever written to this public repository._
