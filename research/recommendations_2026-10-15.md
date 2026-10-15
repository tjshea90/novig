# Keys and services to renew, add or buy (read from the engine's own record, 2026-10-15)

Source: `cloud-feed` `usage.json` and `engine-log.txt` (last engine run, 04:00-05:30 UTC), CLAUDE.md rules. Nothing here spends money: each line is for Tj to decide.

## Needs action (a feed that answers nothing, scan after scan)
1. **ParlayAPI: out of credits or refused.** Every scan shows `parlay 0+0, parlay_1h 0+0, parlay_props 0+0` while its key 1 shows 9,073 used and key 2 923 (Starter, $5). **Renew or upgrade the plan, or add a key**; until then it is dead weight on the chain, and the ranking line still lists it first by its old "p90 <1 s". (PARLAY_API.md has the plan limits.)
2. **The Odds API: both keys at 500 of 500 (the free monthly allowance).** `oddsapi 0+0, oddsapi_props 0+0` every scan. **A paid tier ($30 a month) or two more free keys** would give it back; or leave it off, since SGO, PropLine and Kalshi answer the same games.
3. **BoltOdds: the lines socket drops** ("no usable key (every saved key is r..."), 2 of the last 4 scans had no BoltOdds at all. It was fine in the others, so the key may be rate-limited by the 3-connection limit when the phone's own sockets are also open. Check Settings › APIs › BoltOdds › Test key; if it is refused, the key needs renewing (Pro, $349 a month).

## Worth watching
4. **Unabated (free) quotes are the stalest** (p90 about 14 h on one line; one request per 5 s). It only fills lines no better feed sent. Not worth paying for; fine as the last resort.
5. **SGO Pro (p90 12 min) and PropLine (p90 9-10 min)** carry most of the fair odds; their quotes are 10 minutes old when read. A faster sharp-book source (Pinnacle through a second Pinnodds-style feed, or OddsPapi v5 with a live key) would shorten the fair-odds age that dominates the list's freshness (median fair age 16 min vs Novig 9 min). OddsPapi v5 is built but has no key: it is the cheapest way to add a sharp, fresh feed.
6. **Novig itself:** 15.8 reads a second on the read-only key is the engine's ceiling (key rate 14 plus the stream). One `RATE_LIMIT_EXCEEDED` in the log; nothing refused otherwise. No proxy is needed: the runner's address was accepted. A proxy is only worth it if `RESTRICTED_NETWORK_REGION` returns often (`status.addressHandovers` says how often).

## Services that might help (not needed yet)
- A **second Novig read-only key** (one more subaccount key made on the phone) would double the book-read rate and halve a full scan's 11 minutes; the engine must be changed to use two (TASKS.md TCL16e, option "parallel readers"). Free, but Tj's tap.
- Residential proxy: no evidence it is needed.

## How the app keeps this current
The phone's Diagnostics › API usage and the Health checks already project each metered key to its reset (`Runway`) and warn when it will run short. What was missing, and is queued in TASKS.md (ENGINEER): a **silent-feed detector** on the engine, so a feed that answers `0+0` for three scans in a row becomes a line in `status.json` ("ParlayAPI answered nothing for 3 scans: renew or add a key") that the phone shows. Until then this file is the list, and every ANALYST run refreshes it from `usage.json` and the log.
