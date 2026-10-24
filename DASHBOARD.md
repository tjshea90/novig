# Vigilant GitHub engine: live status

**Scanning** · updated Oct 24 22:01:00 UTC (0 s ago) · run `40122566459` · pass 94

_Reload the page for the latest. Files: [status.json](https://github.com/tjshea90/novig/blob/cloud-feed/status.json) · [usage.json](https://github.com/tjshea90/novig/blob/cloud-feed/usage.json) · [trades.json](https://github.com/tjshea90/novig/blob/cloud-feed/trades.json) · [engine-log.txt](https://github.com/tjshea90/novig/blob/cloud-feed/engine-log.txt)_

## Engine

| | |
|:--|:--|
| Scan now | Fair odds 35/52 · Novig prices (9187/9187) · running 40 s |
| Last scan | ended Oct 24 21:59:53 UTC (66 s ago), took 32 s, 4 bets listed |
| List age | Novig prices median 90 s (oldest 14 min) · fair odds median 4 min (oldest 18 min) |
| Novig | Novig read-only key active: 16 reads a second, websocket off (reads by request) |
| Novig and this machine's address | accepted by Novig |
| Runner address | accepted by Novig (no refusal; 0 replaced runners in a row) |
| Trading | LIVE: live: looked 1, passed 0, ordered 0 ($0.00); held back: the other books' prices behind it are too old x1 |
| Wallet | cash $59.62 · equity $158.89 · read 66 s ago |

## APIs: working or not, and what each has left

Health is from the **last finished scan** (Oct 24 21:59:53 UTC, 67 s ago): leagues answered fresh+reused, Novig games matched. An API the phone has switched off, or with no key on GitHub, is never called and shows as off.

**Silent feeds (answered nothing for 3+ scans in a row):** pinnacle (94 scans in a row)

| API | Health | Leagues (fresh+reused) | Games matched | Left of allowance | Resets | Calls today | Throttled | Note |
|:--|:--|--:|--:|--:|:--|--:|--:|:--|
| SportsGameOdds | ⛔ off / no key | — | — | no allowance published | — | 18,856 | 0 |  |
| BoltOdds | ⛔ off / no key | — | — | no allowance published | — | 214 | 0 |  |
| OddsPapi | ⛔ off / no key | — | — | no allowance published | — | 0 | 0 |  |
| OddsPapi Free (v4) | ⛔ off / no key | — | — | 759 of 1,000 requests (75%) | Nov 1 00:00:00 UTC | 328 | 0 |  |
| Unabated | ⛔ off / no key | — | — | no allowance published | — | 0 | 0 |  |
| Pinnacle (PinnWire) | ⛔ off / no key | — | — | no allowance published | — | 378 | 0 |  |
| Pinnacle (pinnapi) | ❌ failing | pinnacle 0+0 | 0 | no allowance published | — | 21 | 0 | Your Pinnacle (Pinnodds) key is used up for another 2h 0m. |
| PropLine | ✅ working | propline 0+6 · propline_props 6+0 | 78 | 12,348 of 27,000 requests (45%) | Oct 25 00:00:00 UTC | 7,147 | 0 |  |
| ParlayAPI | ✅ working | parlay 0+6 · parlay_1h 3+0 · parlay_props 0+2 | 93 | 63,317 of 147,000 credits (43%) | Nov 1 00:00:00 UTC | 1,421 | 0 |  |
| The Odds API | ⛔ off / no key | — | — | 4,920 of 10,500 credits (46%) | Nov 1 00:00:00 UTC | 9,967 | 0 |  |
| Novig | — not asked | — | — | no key, not metered | — | 33,074 | 114 |  |
| Polymarket | ✅ working | polymarket 6+0 | 57 | no key, not metered | — | 57,138 | 0 |  |
| Kalshi | ✅ working | kalshi 6+0 | 57 | no key, not metered | — | 30,543 | 0 |  |
| SX Bet | ✅ working | sxbet 6+0 | 45 | no allowance published | — | 53,505 | 0 |  |
| SharpAPI | ⛔ off / no key | — | — | no allowance published | — | 308,376 | 0 |  |

## What to refill, buy or fix

- 🟢 **ParlayAPI: using more of the plan you pay for**: 44% of its paid credits are left with 23% of the month to go, so the engine now asks it as a second opinion for each league now and then (credits that expire unused are the only waste).

## Usage meters per key

A key is shown by an id (never the key). **provider** = the provider's own figure, read at the time shown; **count** = the engine's own tally. The active key is the one the next call uses.

| API | Key | State | Used | Left | Figure | Until |
|:--|:--|:--|--:|--:|:--|:--|
| SportsGameOdds | `f8bf…a824` | ▶ active | 18,435 | — | count | — |
| BoltOdds | `1b46…70df` | ▶ active | 7 | — | count | — |
| BoltOdds | `d9ad…19a9` | standby | 0 | — | count | — |
| BoltOdds | `958c…b636` | standby | 0 | — | count | — |
| OddsPapi Free (v4) | `11a6…65d8` | ▶ active | 241 / 250 | 9 | provider, read 2 h ago | — |
| OddsPapi Free (v4) | `dea8…2ee4` | standby | 0 / 250 | 250 | provider, read 2 h ago | — |
| OddsPapi Free (v4) | `1850…cd9a` | standby | 0 / 250 | 250 | provider, read 2 h ago | — |
| OddsPapi Free (v4) | `4e91…cc8e` | standby | 0 / 250 | 250 | provider, read 2 h ago | — |
| Unabated | `fd8c…e236` | ▶ active | 0 | — | count | — |
| Pinnacle (PinnWire) | `f4ba…4f76` | refused by provider | 0 / 100 | — | count | Oct 24 22:05:22 UTC |
| Pinnacle (PinnWire) | `e17e…591a` | refused by provider | 0 / 100 | — | count | Oct 24 22:05:22 UTC |
| Pinnacle (PinnWire) | `af6f…d5ca` | refused by provider | 0 / 100 | — | count | Oct 24 22:05:22 UTC |
| Pinnacle (PinnWire) | `ac68…573f` | refused by provider | 0 / 100 | — | count | Oct 24 22:05:26 UTC |
| Pinnacle (PinnWire) | `e3a0…0650` | refused by provider | 0 / 100 | — | count | Oct 24 22:05:27 UTC |
| Pinnacle (PinnWire) | `d568…77fe` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:49 UTC |
| Pinnacle (PinnWire) | `1c3f…3321` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:49 UTC |
| Pinnacle (PinnWire) | `f3ca…49bf` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:49 UTC |
| Pinnacle (PinnWire) | `37a5…b801` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:50 UTC |
| Pinnacle (PinnWire) | `c188…e9b9` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:50 UTC |
| Pinnacle (PinnWire) | `b6a4…9ee5` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:50 UTC |
| Pinnacle (PinnWire) | `4669…8479` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:50 UTC |
| Pinnacle (PinnWire) | `629e…7948` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:51 UTC |
| Pinnacle (PinnWire) | `30d1…0559` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:51 UTC |
| Pinnacle (PinnWire) | `f1ca…a4d0` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:51 UTC |
| Pinnacle (PinnWire) | `9b63…fa5c` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:51 UTC |
| Pinnacle (PinnWire) | `c68a…35bb` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:51 UTC |
| Pinnacle (PinnWire) | `7e11…3723` | refused by provider | 0 / 100 | — | count | Oct 24 22:07:51 UTC |
| Pinnacle (pinnapi) | `c24a…6908` | refused by provider | 0 / 100 | 100 | count | Oct 24 22:05:28 UTC |
| PropLine | `b08b…ee26` | ▶ active | 14,016 / 25,000 | 10,984 | provider, read 1 s ago | — |
| PropLine | `7671…2805` | standby | 318 / 1,000 | 682 | provider, read 1 min ago | — |
| PropLine | `28e7…8eab` | standby | 318 / 1,000 | 682 | provider, read 1 min ago | — |
| ParlayAPI | `378f…8bcf` | ▶ active | 14,432 / 20,000 | 5,568 | provider, read 2 min ago | — |
| ParlayAPI | `dbe1…b552` | standby | 49,969 / 100,000 | 50,031 | provider, read 17 s ago | — |
| ParlayAPI | `7969…6028` | standby | 963 / 1,000 | 37 | provider, read 2 min ago | — |
| ParlayAPI | `3a90…adcc` | standby | 12,708 / 20,000 | 7,292 | provider, read 2 min ago | — |
| ParlayAPI | `6713…3dff` | standby | 946 / 1,000 | 54 | provider, read 2 min ago | — |
| ParlayAPI | `b30c…c097` | standby | 940 / 1,000 | 60 | provider, read 2 min ago | — |
| ParlayAPI | `6612…6ae5` | standby | 941 / 1,000 | 59 | provider, read 2 min ago | — |
| ParlayAPI | `9c5d…94bc` | standby | 932 / 1,000 | 68 | provider, read 2 min ago | — |
| ParlayAPI | `f85a…9ade` | standby | 927 / 1,000 | 73 | provider, read 2 min ago | — |
| ParlayAPI | `7dc4…d6cb` | standby | 925 / 1,000 | 75 | provider, read 2 min ago | — |
| The Odds API | `738b…7c44` | spent | 500 / 500 | 0 | provider, read 2 h ago | Nov 1 00:00:00 UTC |
| The Odds API | `3015…5ed6` | spent | 500 / 500 | 0 | provider, read 2 h ago | Nov 1 00:00:00 UTC |
| The Odds API | `8257…3a13` | ▶ active | 499 / 500 | 1 | provider, read 2 h ago | — |
| The Odds API | `a1b8…6b82` | standby | 499 / 500 | 1 | provider, read 2 h ago | — |
| The Odds API | `72f6…bb0a` | standby | 499 / 500 | 1 | provider, read 2 h ago | — |
| The Odds API | `69fe…5aa7` | spent | 500 / 500 | 0 | provider, read 2 h ago | Nov 1 00:00:00 UTC |
| The Odds API | `a258…c0f2` | standby | 366 / 500 | 134 | provider, read 2 h ago | — |
| The Odds API | `e4d5…b03d` | standby | 257 / 500 | 243 | provider, read 2 h ago | — |
| The Odds API | `fa28…c5ab` | standby | 253 / 500 | 247 | provider, read 2 h ago | — |
| The Odds API | `5159…32a3` | standby | 252 / 500 | 248 | provider, read 2 h ago | — |
| The Odds API | `8474…764e` | standby | 246 / 500 | 254 | provider, read 2 h ago | — |
| The Odds API | `3ee2…e928` | standby | 246 / 500 | 254 | provider, read 2 h ago | — |
| The Odds API | `0a75…85c4` | standby | 240 / 500 | 260 | provider, read 2 h ago | — |
| The Odds API | `07c4…7afe` | standby | 210 / 500 | 290 | provider, read 2 h ago | — |
| The Odds API | `7950…f471` | standby | 123 / 500 | 377 | provider, read 2 h ago | — |
| The Odds API | `144e…cabd` | standby | 123 / 500 | 377 | provider, read 2 h ago | — |
| The Odds API | `1729…3dcb` | standby | 123 / 500 | 377 | provider, read 2 h ago | — |
| The Odds API | `38d0…6fff` | standby | 123 / 500 | 377 | provider, read 2 h ago | — |
| The Odds API | `5ded…4d56` | standby | 21 / 500 | 479 | provider, read 2 h ago | — |
| The Odds API | `1c7d…9c86` | standby | 0 / 500 | 500 | provider, read 2 h ago | — |
| The Odds API | `9f78…0966` | standby | 0 / 500 | 500 | provider, read 2 h ago | — |
| SX Bet | `fe2a…3d41` | ▶ active | 0 | — | count | — |
| SharpAPI | `0c67…be20` | ▶ active | 307,706 | — | count | — |

## Order the engine asks the APIs (best first, measured)

- ParlayAPI (p90 101 s) > ParlayAPI (p90 3 min) > SGO (p90 4 min) > OddsPapi (not measured) > Unabated (not measured) > PropLine (p90 12 min) > The Odds API (p90 <1 s) > OddsPapi Free (not measured)
- SGO (p90 3 min) > ParlayAPI (p90 4 min) > OddsPapi (not measured) > Unabated (not measured) > PropLine (p90 5 min) > OddsPapi Free (not measured) > The Odds API (not measured)
- always asked, not ranked: BoltOdds (p90 <1 s); BoltOdds (p90 <1 s); Kalshi (p90 <1 s); Pinnacle feed (p90 <1 s); Polymarket (p90 <1 s); sharpapi (p90 13 min); SX Bet (p90 <1 s)

_Rules this page follows: only the engine's own calls are counted; an API the phone switched off is never called; no key or call time is ever written to this public repository._
