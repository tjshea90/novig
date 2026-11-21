# Vigilant GitHub engine: live status

**Idle (the phone's switch is off)** · updated Nov 21 12:34:42 UTC (0 s ago) · run `44887358200` · pass 95

_Reload the page for the latest. Files: [status.json](https://github.com/tjshea90/novig/blob/cloud-feed/status.json) · [usage.json](https://github.com/tjshea90/novig/blob/cloud-feed/usage.json) · [trades.json](https://github.com/tjshea90/novig/blob/cloud-feed/trades.json) · [engine-log.txt](https://github.com/tjshea90/novig/blob/cloud-feed/engine-log.txt)_

## Engine

| | |
|:--|:--|
| Last scan | ended Nov 21 12:32:27 UTC (2 min ago), took 91 s, 98 bets listed |
| List age | Novig prices median 0 s (oldest 19 s) · fair odds median 4 min (oldest 5 min) |
| Novig | Novig read-only key active: 16 reads a second, tier BASE (up to 2,048 open orders), websocket on |
| Novig and this machine's address | accepted by Novig |
| Runner address | accepted by Novig (no refusal; 0 replaced runners in a row) |
| Trading | LIVE: live: looked 2, passed 0, ordered 0 ($0.00); held back: it starts in more than 48 h (trap guard: bets this early lost to the close) x2 |
| Wallet | cash $29.84 · equity $99.54 · read 65 s ago |

## APIs: working or not, and what each has left

Health is from the **last finished scan** (Nov 21 12:32:27 UTC, 2 min ago): leagues answered fresh+reused, Novig games matched. An API the phone has switched off, or with no key on GitHub, is never called and shows as off.

| API | Health | Leagues (fresh+reused) | Games matched | Left of allowance | Resets | Calls today | Throttled | Note |
|:--|:--|--:|--:|--:|:--|--:|--:|:--|
| SportsGameOdds | ⛔ off / no key | — | — | no allowance published | — | 0 | 0 |  |
| BoltOdds | ⛔ off / no key | — | — | no allowance published | — | 0 | 0 |  |
| OddsPapi Free (v4) | ⛔ off / no key | — | — | 652 of 1,000 requests (65%) | Dec 1 00:00:00 UTC | 136 | 0 |  |
| Pinnacle (PinnWire) | ⛔ off / no key | — | — | no allowance published | — | 0 | 0 |  |
| Pinnacle (pinnapi) | ⛔ off / no key | — | — | 100 of 100 requests (100%) | Nov 22 00:00:00 UTC | 0 | 0 |  |
| PropLine | ✅ working | propline 7+0 · propline_props 4+0 · propline_1h 0+4 | 408 | 242,487 of 252,000 requests (96%) | Nov 22 00:00:00 UTC | 12,093 | 0 |  |
| ParlayAPI | ⏸ held back | parlay 0+0 · parlay_1h 0+0 · parlay_props 0+0 | 0 | 581 of 10,000 credits (5%) | Dec 1 00:00:00 UTC | 3,006 | 0 | All 10 ParlayAPI keys are down to the last 100 credits, kept for closing lines: back at th |
| The Odds API | 💤 standing by | oddsapi 0+0 · oddsapi_props 0+0 | 0 | 4,587 of 10,500 credits (43%) | Dec 1 00:00:00 UTC | 7,112 | 0 |  |
| Novig | — not asked | — | — | no key, not metered | — | 39,405 | 11 |  |
| Polymarket | ✅ working | polymarket 4+0 | 92 | no key, not metered | — | 28,290 | 0 |  |
| Kalshi | ✅ working | kalshi 7+0 | 192 | no key, not metered | — | 24,166 | 0 |  |
| SX Bet | ✅ working | sxbet 7+0 | 113 | no allowance published | — | 27,651 | 0 |  |
| SharpAPI | ✅ working | sharpapi 7+0 | 0 | no allowance published | — | 7,650 | 0 |  |

## What to refill, buy or fix

- 🟠 **ParlayAPI is running low**: 581 of 10000 credits left (5%), resets Dec 1. Add a key or move to a bigger plan before it runs out.

## Resting to save usage

- 💤 OddsPapi Free (v4) until 13:36 UTC: answered but matched no game for 6 scans in a row

## Usage meters per key

A key is shown by an id (never the key). **provider** = the provider's own figure, read at the time shown; **count** = the engine's own tally. The active key is the one the next call uses.

| API | Key | State | Used | Left | Figure | Until |
|:--|:--|:--|--:|--:|:--|:--|
| SportsGameOdds | `f8bf…a824` | ▶ active | 0 | — | count | — |
| BoltOdds | `1b46…70df` | ▶ active | 0 | — | count | — |
| BoltOdds | `d9ad…19a9` | standby | 0 | — | count | — |
| BoltOdds | `958c…b636` | standby | 0 | — | count | — |
| OddsPapi Free (v4) | `11a6…65d8` | spent | 254 / 250 | 0 | provider, read 2 h ago | Dec 1 00:00:00 UTC |
| OddsPapi Free (v4) | `dea8…2ee4` | ▶ active | 98 / 250 | 152 | provider, read 2 h ago | — |
| OddsPapi Free (v4) | `1850…cd9a` | standby | 0 / 250 | 250 | provider, read 2 h ago | — |
| OddsPapi Free (v4) | `4e91…cc8e` | standby | 0 / 250 | 250 | provider, read 2 h ago | — |
| Pinnacle (pinnapi) | `8132…e63b` | ▶ active | 0 / 100 | 100 | count | — |
| PropLine | `b08b…ee26` | ▶ active | 9,221 / 250,000 | 240,779 | provider, read 70 s ago | — |
| PropLine | `7671…2805` | standby | 145 / 1,000 | 855 | provider, read 2 min ago | — |
| PropLine | `28e7…8eab` | standby | 147 / 1,000 | 853 | provider, read 2 min ago | — |
| ParlayAPI | `378f…8bcf` | spent | 1,000 / 1,000 | 0 | provider, read 2 min ago | Dec 1 00:00:00 UTC |
| ParlayAPI | `dbe1…b552` | spent | 377,303 / 1,000 | 0 | provider, read 2 min ago | Dec 1 00:00:00 UTC |
| ParlayAPI | `7969…6028` | ▶ active | 938 / 1,000 | 62 | provider, read 2 min ago | — |
| ParlayAPI | `3a90…adcc` | spent | 10,506 / 1,000 | 0 | provider, read 2 min ago | Dec 1 00:00:00 UTC |
| ParlayAPI | `6713…3dff` | standby | 913 / 1,000 | 87 | provider, read 2 min ago | — |
| ParlayAPI | `b30c…c097` | standby | 911 / 1,000 | 89 | provider, read 2 min ago | — |
| ParlayAPI | `6612…6ae5` | standby | 916 / 1,000 | 84 | provider, read 2 min ago | — |
| ParlayAPI | `9c5d…94bc` | standby | 920 / 1,000 | 80 | provider, read 2 min ago | — |
| ParlayAPI | `f85a…9ade` | standby | 906 / 1,000 | 94 | provider, read 2 min ago | — |
| ParlayAPI | `7dc4…d6cb` | standby | 915 / 1,000 | 85 | provider, read 2 min ago | — |
| The Odds API | `738b…7c44` | ▶ active | 315 / 500 | 185 | provider, read 63 s ago | — |
| The Odds API | `3015…5ed6` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `8257…3a13` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `a1b8…6b82` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `72f6…bb0a` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `69fe…5aa7` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `a258…c0f2` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `e4d5…b03d` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `fa28…c5ab` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `5159…32a3` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `8474…764e` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `3ee2…e928` | standby | 286 / 500 | 214 | provider, read 2 min ago | — |
| The Odds API | `0a75…85c4` | standby | 283 / 500 | 217 | provider, read 2 min ago | — |
| The Odds API | `07c4…7afe` | standby | 271 / 500 | 229 | provider, read 2 min ago | — |
| The Odds API | `7950…f471` | standby | 272 / 500 | 228 | provider, read 2 min ago | — |
| The Odds API | `144e…cabd` | standby | 271 / 500 | 229 | provider, read 2 min ago | — |
| The Odds API | `1729…3dcb` | standby | 271 / 500 | 229 | provider, read 2 min ago | — |
| The Odds API | `38d0…6fff` | standby | 271 / 500 | 229 | provider, read 2 min ago | — |
| The Odds API | `5ded…4d56` | standby | 272 / 500 | 228 | provider, read 2 min ago | — |
| The Odds API | `1c7d…9c86` | standby | 271 / 500 | 229 | provider, read 2 min ago | — |
| The Odds API | `9f78…0966` | standby | 270 / 500 | 230 | provider, read 2 min ago | — |
| SX Bet | `fe2a…3d41` | ▶ active | 0 | — | count | — |
| SharpAPI | `0c67…be20` | ▶ active | 7,183 | — | count | — |

## Order the engine asks the APIs (best first, measured)

- ParlayAPI (p90 74 s) > PropLine (p90 2 min) > ParlayAPI (p90 2 min) > SGO (not measured) > The Odds API (p90 17 s) > OddsPapi Free (not measured)
- PropLine (p90 79 s) > SGO (not measured) > ParlayAPI (p90 6 min) > OddsPapi Free (not measured) > The Odds API (not measured)
- always asked, not ranked: Kalshi (p90 <1 s); Polymarket (p90 <1 s); PropLine (p90 95 s); sharpapi (not measured); SX Bet (p90 <1 s)

_Rules this page follows: only the engine's own calls are counted; an API the phone switched off is never called; no key or call time is ever written to this public repository._
