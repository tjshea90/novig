# Vigilant GitHub engine: live status

**Scanning** · updated Oct 31 18:15:49 UTC (0 s ago) · run `41227054200` · pass 4

_Reload the page for the latest. Files: [status.json](https://github.com/tjshea90/novig/blob/cloud-feed/status.json) · [usage.json](https://github.com/tjshea90/novig/blob/cloud-feed/usage.json) · [trades.json](https://github.com/tjshea90/novig/blob/cloud-feed/trades.json) · [engine-log.txt](https://github.com/tjshea90/novig/blob/cloud-feed/engine-log.txt)_

## Engine

| | |
|:--|:--|
| Scan now | Fair odds 64/98 · Novig prices (13071/13071) · running 1 min |
| Last scan | ended Oct 31 18:13:09 UTC (2 min ago), took 110 s, 99 bets listed |
| List age | Novig prices median 0 s (oldest 7 s) · fair odds median 2 min (oldest 6 min) |
| Novig | Novig read-only key active: 16 reads a second, websocket on |
| Novig and this machine's address | accepted by Novig |
| Runner address | accepted by Novig (no refusal; 0 replaced runners in a row) |
| Trading | LIVE: live: looked 0, passed 0, ordered 0 ($0.00) |
| Wallet | cash $9.59 · equity $242.49 · read 24 s ago |

## APIs: working or not, and what each has left

Health is from the **last finished scan** (Oct 31 18:13:08 UTC, 2 min ago): leagues answered fresh+reused, Novig games matched. An API the phone has switched off, or with no key on GitHub, is never called and shows as off.

| API | Health | Leagues (fresh+reused) | Games matched | Left of allowance | Resets | Calls today | Throttled | Note |
|:--|:--|--:|--:|--:|:--|--:|--:|:--|
| SportsGameOdds | ⛔ off / no key | — | — | no allowance published | — | 0 | 0 |  |
| BoltOdds | ⛔ off / no key | — | — | no allowance published | — | 204 | 0 |  |
| OddsPapi Free (v4) | 💤 standing by | oddspapi_free 0+0 · oddspapi_free-props 0+0 | 0 | 759 of 1,000 requests (75%) | Nov 1 00:00:00 UTC | 176 | 0 |  |
| Pinnacle (PinnWire) | ⛔ off / no key | — | — | no allowance published | — | 0 | 0 |  |
| Pinnacle (pinnapi) | ✅ working | pinnacle 2+0 | 0 | 100 of 100 requests (100%) | Nov 1 00:00:00 UTC | 0 | 0 | No Pinnacle key. Add a free PinnWire or pinnapi key in Settings. |
| PropLine | ✅ working | propline 8+0 · propline_props 6+0 · propline_1h 4+0 | 251 | 243,266 of 252,000 requests (96%) | Nov 1 00:00:00 UTC | 8,329 | 0 |  |
| ParlayAPI | ✅ working | parlay 8+0 · parlay_1h 3+0 · parlay_props 5+0 | 173 | 738,663 of 1,028,000 credits (71%) | Nov 1 00:00:00 UTC | 7,643 | 0 | ParlayAPI's 1st-half lines are busy |
| The Odds API | 💤 standing by | oddsapi 0+0 · oddsapi_props 0+0 | 0 | 506 of 10,500 credits (4%) | Nov 1 00:00:00 UTC | 9,128 | 0 |  |
| Novig | — not asked | — | — | no key, not metered | — | 2,400 | 6 |  |
| Polymarket | ✅ working | polymarket 6+0 | 54 | no key, not metered | — | 21,500 | 0 |  |
| Kalshi | ✅ working | kalshi 8+0 | 117 | no key, not metered | — | 17,627 | 0 |  |
| SX Bet | ✅ working | sxbet 8+0 | 81 | no allowance published | — | 24,427 | 0 |  |
| SharpAPI | ✅ working | sharpapi 8+0 | 0 | no allowance published | — | 6,912 | 0 |  |

## What to refill, buy or fix

- 🟠 **The Odds API is running low**: 509 of 10500 credits left (4%), resets in 5h 46m. Add a key or move to a bigger plan before it runs out.
- 🟢 **ParlayAPI: using more of the plan you pay for**: 72% of its paid credits are left with 0% of the month to go, so the engine now asks it as a second opinion for each league now and then (credits that expire unused are the only waste).

## Usage meters per key

A key is shown by an id (never the key). **provider** = the provider's own figure, read at the time shown; **count** = the engine's own tally. The active key is the one the next call uses.

| API | Key | State | Used | Left | Figure | Until |
|:--|:--|:--|--:|--:|:--|:--|
| SportsGameOdds | `f8bf…a824` | ▶ active | 0 | — | count | — |
| BoltOdds | `1b46…70df` | ▶ active | 3 | — | count | — |
| BoltOdds | `d9ad…19a9` | standby | 0 | — | count | — |
| BoltOdds | `958c…b636` | standby | 1 | — | count | — |
| OddsPapi Free (v4) | `11a6…65d8` | ▶ active | 241 / 250 | 9 | provider, read 15 min ago | — |
| OddsPapi Free (v4) | `dea8…2ee4` | standby | 0 / 250 | 250 | provider, read 15 min ago | — |
| OddsPapi Free (v4) | `1850…cd9a` | standby | 0 / 250 | 250 | provider, read 15 min ago | — |
| OddsPapi Free (v4) | `4e91…cc8e` | standby | 0 / 250 | 250 | provider, read 15 min ago | — |
| Pinnacle (pinnapi) | `c24a…6908` | ▶ active | 0 / 100 | 100 | count | — |
| PropLine | `b08b…ee26` | ▶ active | 8,278 / 250,000 | 241,722 | provider, read 0 s ago | — |
| PropLine | `7671…2805` | standby | 228 / 1,000 | 772 | provider, read 8 s ago | — |
| PropLine | `28e7…8eab` | standby | 228 / 1,000 | 772 | provider, read 8 s ago | — |
| ParlayAPI | `378f…8bcf` | spent | 16,597 / 1,000 | 0 | provider, read 4 min ago | Nov 1 00:00:00 UTC |
| ParlayAPI | `dbe1…b552` | ▶ active | 269,050 / 1,000,000 | 730,950 | provider, read 8 s ago | — |
| ParlayAPI | `7969…6028` | standby | 963 / 1,000 | 37 | provider, read 4 min ago | — |
| ParlayAPI | `3a90…adcc` | standby | 12,713 / 20,000 | 7,287 | provider, read 4 min ago | — |
| ParlayAPI | `6713…3dff` | standby | 946 / 1,000 | 54 | provider, read 4 min ago | — |
| ParlayAPI | `b30c…c097` | standby | 940 / 1,000 | 60 | provider, read 4 min ago | — |
| ParlayAPI | `6612…6ae5` | standby | 941 / 1,000 | 59 | provider, read 10 min ago | — |
| ParlayAPI | `9c5d…94bc` | standby | 932 / 1,000 | 68 | provider, read 4 min ago | — |
| ParlayAPI | `f85a…9ade` | standby | 927 / 1,000 | 73 | provider, read 10 min ago | — |
| ParlayAPI | `7dc4…d6cb` | standby | 925 / 1,000 | 75 | provider, read 4 min ago | — |
| The Odds API | `738b…7c44` | spent | 500 / 500 | 0 | provider, read 8 s ago | Nov 1 00:00:00 UTC |
| The Odds API | `3015…5ed6` | spent | 500 / 500 | 0 | provider, read 8 s ago | Nov 1 00:00:00 UTC |
| The Odds API | `8257…3a13` | ▶ active | 499 / 500 | 1 | provider, read 8 s ago | — |
| The Odds API | `a1b8…6b82` | standby | 499 / 500 | 1 | provider, read 7 s ago | — |
| The Odds API | `72f6…bb0a` | standby | 499 / 500 | 1 | provider, read 7 s ago | — |
| The Odds API | `69fe…5aa7` | spent | 500 / 500 | 0 | provider, read 7 s ago | Nov 1 00:00:00 UTC |
| The Odds API | `a258…c0f2` | standby | 479 / 500 | 21 | provider, read 7 s ago | — |
| The Odds API | `e4d5…b03d` | standby | 478 / 500 | 22 | provider, read 7 s ago | — |
| The Odds API | `fa28…c5ab` | standby | 480 / 500 | 20 | provider, read 7 s ago | — |
| The Odds API | `5159…32a3` | standby | 480 / 500 | 20 | provider, read 7 s ago | — |
| The Odds API | `8474…764e` | standby | 479 / 500 | 21 | provider, read 6 s ago | — |
| The Odds API | `3ee2…e928` | standby | 478 / 500 | 22 | provider, read 6 s ago | — |
| The Odds API | `0a75…85c4` | standby | 478 / 500 | 22 | provider, read 6 s ago | — |
| The Odds API | `07c4…7afe` | standby | 478 / 500 | 22 | provider, read 6 s ago | — |
| The Odds API | `7950…f471` | standby | 478 / 500 | 22 | provider, read 6 s ago | — |
| The Odds API | `144e…cabd` | standby | 478 / 500 | 22 | provider, read 6 s ago | — |
| The Odds API | `1729…3dcb` | standby | 474 / 500 | 26 | provider, read 6 s ago | — |
| The Odds API | `38d0…6fff` | standby | 434 / 500 | 66 | provider, read 6 s ago | — |
| The Odds API | `5ded…4d56` | standby | 435 / 500 | 65 | provider, read 6 s ago | — |
| The Odds API | `1c7d…9c86` | standby | 433 / 500 | 67 | provider, read 6 s ago | — |
| The Odds API | `9f78…0966` | standby | 435 / 500 | 65 | provider, read 8 s ago | — |
| SX Bet | `fe2a…3d41` | ▶ active | 0 | — | count | — |
| SharpAPI | `0c67…be20` | ▶ active | 6,534 | — | count | — |

## Order the engine asks the APIs (best first, measured)

- PropLine (p90 2 min, 2.5 pts off Pinnacle) > SGO (not measured) > OddsPapi Free (not measured) > The Odds API (not measured)
- PropLine (p90 2 min) > SGO (not measured) > OddsPapi Free (not measured) > The Odds API (not measured)
- always asked, not ranked: Kalshi (p90 <1 s); ParlayAPI (p90 13 min, 1.9 pts off Pinnacle); ParlayAPI (p90 6 min); ParlayAPI (p90 4 min); Pinnacle feed (p90 10 min); Polymarket (p90 <1 s); PropLine (p90 2 min); SX Bet (p90 <1 s)

_Rules this page follows: only the engine's own calls are counted; an API the phone switched off is never called; no key or call time is ever written to this public repository._
