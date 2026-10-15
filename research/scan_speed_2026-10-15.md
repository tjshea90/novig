# Scan speed on GitHub: where the time goes, and what helps (Tj, 2026-10-15)

Tj asked twice: "See if there is any way to speed up scanning. Split processing between phone and GitHub? Multiple GitHub actions simultaneously scanning sections of the novig list?" and, with a screenshot, "Is this speed too slow for pre game betting and bidding? If so, can it be sped up or optimized". (TASKS.md TCL15, TCL16.)

## Measured (the engine's own status and log, run 38670627114 and 38672214775, read key on, websocket on)
| | before the Novig read key (public routes) | with the key (v0.104.0) |
|---|---|---|
| Novig books read per scan | ~1,300-1,600 of ~8,200 planned ("6,500-6,900 left out as too late") | **all 9,619**, 0 left out |
| Novig reads a second | ~1.8 | **16-18** (the key's 16 a second plus pushes) |
| a cold full scan | ~860 s, partial | **537 s**, complete |
| Novig side of the listed rows at publication | minutes (stale) | median 2 s after the end-of-scan re-read; **7.5 min median while the scan is running** (screenshot) |
| fair odds behind the listed rows | not measured | **median 14.7 min, oldest 19 min** (the screenshot: 13m 21s / 17m 56s) |

Where the fair age comes from: (1) the fair feeds are read ONCE, at the scan's start; the scan then spends ~9 minutes on Novig's books, so every row priced late has fair odds 9 minutes older than when they were read; (2) the feeds' own quotes are already old when read: SportsGameOdds p90 7 min and PropLine p90 8 min by the books' own stamps (their pregame boards refresh about every 5 minutes), while ParlayAPI, BoltOdds, Kalshi and Polymarket are under a second. A fair line's age is the age of the OLDEST quote behind it (Tj's rule: "odds MUST be current"), so one old soft-book quote makes the whole line old.

## Where the games are (candidate bid lines at 02:41Z, 4,035 markets)
Within 3 h: 1% · 6 h: 2% · 12 h: 4% · 24 h: 17% · 48 h: 19% · 96 h: 90% (a Thursday-night view of a weekend slate). So most of what is scanned is a game 2-4 days away, where Tj's own measurement says lines barely move (2.7% of Kalshi's fair lines moved a point or more in 10 minutes), and where the freshness rule allows 10-30 minutes. The auto-bet's trap guard (`trapEarlyHours`, default 6) only bets games within 6 h because "bets placed that early lost to the close". The games that matter for pregame BETTING are the ~2-4% that start within 6-12 hours, and for them 14.7 minutes is too slow (his own rule for a game within 3 hours is 5 minutes). For far games and for far bids it is inside the rule.

## Is it too slow?
- Far games (about 90% of markets): within the rules; the price of the far scan is a 9-10 minute cycle.
- Games starting within ~12 hours (the ones auto-bet and the fastest bids go on): **yes, too slow**: the fair odds are older than a bid or a bet on them should rest on, which also shrinks bid volume (a bid's life is capped by its fair's remaining freshness).
- Novig's own side is no longer the problem.

## What was built (TCL16a, `ScanGuards.hotRefresh`, GitHub engine only)
While the long scan reads Novig, the games within 12 hours get (1) their fair odds read again from the feeds that cost nothing per read or are flat-rate (BoltOdds, Kalshi, Polymarket, SportsGameOdds, OddsPapi v5; never a credit-metered or daily-capped feed) every 45 s, and (2) the Novig books of their likeliest +EV lines (within -3% of zero, or an open bet; at most 120 a step) read again between two batches. Expected: the fair behind those rows drops from ~15 min toward the feeds' own age (BoltOdds/Kalshi/Polymarket seconds, SGO ~5 min); the extra Novig reads are a few percent of the key's 16 a second. `HotRefreshTest`. The scan note now carries the scan's timing, hot counts included.

## Ideas weighed, with what the numbers say
| Idea | Gain | Cost / risk | Verdict |
|---|---|---|---|
| The engine's own Novig read key | 1.8 -> 16 reads/s (done) | none once sent | **done, working** |
| Hot refresh in the long scan | near games' fair 15 -> ~5-8 min (feed-bound), Novig side seconds | a few % of the key's reads, a few flat-rate feed requests a minute | **built (v0.106.0)** |
| Quick scans between full ones (laned: 12 h / 48 h / full) | the phone had it (`FarLane`); on the engine the full scan blocks for 9 min, so lanes only help if the full scan gets shorter | the Novig book age limit (10 min) means a full scan cannot run less often than about every 10 min unless the stream holds the markets | **not yet**: needs the next two |
| Do not re-read hopeless markets (preview EV from the NEW fair against the held book; re-read when it is within -4%, or every Nth scan) | ~40-50% fewer reads: full scan ~9 -> ~5 min | a Novig-side-only move on a market that was far from +EV is found a scan or two later | **proposed**, needs Tj's word (it changes "every priced line is read each scan") |
| The Novig websocket holding the soonest ~1,800 markets | their prices are live, no REST | 4 tokens/s: ~1 hour to subscribe them (bbo 8 tokens each); churn wastes tokens | worth it for the 48 h set; check `liveFeedHeld` in the new timing first |
| Parallel Novig reader jobs / several runners | reads x K | every runner has its own public IP (~5 a second each) or needs its own read key (16 a second each, separate bucket); git is the only channel between jobs (latency ~10-15 s); multiplying keys to multiply a rate limit is Tj's call on his Novig account | **ask Tj** before building |
| The phone reads part of the list | the phone's key is a second 16/s bucket on another IP | the books would have to travel back to GitHub; the phone already reads the bid candidates and the listed rows | no: wrong direction |
| Weight the fair by its freshest sharp books (not the oldest quote) | fair age -> seconds for rows with a Kalshi/Pinnacle/Bolt quote | changes Tj's "oldest quote decides" freshness rule | **ask Tj** |

## Read first after a change
`cloud-feed/status.json` (read it through the contents API, not raw.githubusercontent.com, which caches for minutes): `scanNote` (timing: board, fair odds, Novig prices a second by route, hot refresh counts), `novigAgeMedianS`/`fairAgeMedianS` (measured from the rows), `ranking` (each feed's p90 quote age), `engine-log.txt`.
