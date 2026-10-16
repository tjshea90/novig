# PropLine Pro: speed and freshness of odds, measured (TCL60b; Tj asked 2026-10-16)

Measured by `tools/propline.py speed` (workflow `propline.yml`, task `speed`) on Tj's Pro key at 20:30-20:32 UTC from a GitHub runner; raw output `research/propline/speed_2026-10-16.txt`. One slate (a heavy one: 216 NCAAF games, a Friday evening), two runs, so read it as a snapshot, not a law; the task can be re-run any time and Claude's runs should repeat it on different slates before leaning on one number.

## Speed (response time, milliseconds)
| call | p50 | p90 |
|:--|--:|--:|
| tiny read (`/v1/sports`) | 126-532 | 1,265-1,847 |
| a league's games (`/events`) | 163-206 | 1,564-2,115 |
| bulk game lines, 216 games x 10 books (one call) | 2,838-3,295 | 3,314-5,353 |
| props of ONE game, 6 markets, 6 books | 295-431 | 3,897-6,405 |
| `/ev` of one game, all markets, at Novig | 343-3,827 | 1,097-4,360 |
| `/best-line` of one game, all markets | 1,390-1,568 | 3,267-3,560 |
A bulk call for a whole slate is worth it (216 games in 3 s); a per-game call is quick (0.3-0.4 s) but its tail is 4-6 s, and the first Pro scan hit timeouts and 504s on the heaviest calls (fixed 2026-10-16: 75 s call, two retries, 6-market chunks). Sustained rate allowed: 20 a second, 50 burst, 20 in flight (the client uses 10 a second, 6 at once).

## Freshness of the odds (seconds since each book last DELIVERED the outcome to PropLine = how recently PropLine confirmed the price is still on the board; `last_seen_at` on `/odds`)
Game lines: Novig 62 s (p90 70) · Pinnacle 71 · Hard Rock 72 · BetMGM 94 · Kalshi 106 · Bovada 119 · Polymarket 243 · **FanDuel 275 · DraftKings 331**.
Player props: Pinnacle 84 · BetMGM 103 · Bovada 134 · **FanDuel 303 · DraftKings 374** (small samples).
Plain reading: PropLine's own docs say five books (DraftKings, FanDuel, Fanatics, Kalshi, Polymarket) arrive over push feeds in about a second and the other 25 by polling every 30-90 s. On this heavy slate it was the other way round for DraftKings, FanDuel and Polymarket (4.5-5.5 minutes between deliveries) while the polled sharp books (Novig, Pinnacle, Hard Rock) were confirmed about a minute ago. "Seconds since the price changed" is much older (median 2-7 hours for a quiet line) and means nothing by itself: a line that has not moved is not stale.
What it means for Vigilant: a fair line built from Pinnacle/Novig/BetMGM/Hard Rock through PropLine is about 1-1.7 minutes old when read, inside the 5-minute (10 for far games) freshness rule; DraftKings and FanDuel quotes through PropLine are 5-6 minutes old on a heavy slate and are right at the edge: Vigilant's freshness gate already drops a quote older than its limit, so they simply do not price a bet when stale. The PropLine +EV list's Novig price is as old as Novig's delivery (about a minute for game lines; props 15-45 minutes per PropLine's docs), shown on each line.

## Pace (requests)
The Pro key spent about 1,400 requests an hour on the first day (props for every game on a 2-minute reuse, the +EV sweep every 15 minutes with a cross-check request per game). Now: the day's 25,000 (quota day 00:00-00:00 UTC) is spread over the engine's working hours (08:00-01:00 ET = 17 hours of the UTC day) by `PropLineClient.activeDayFraction`; props for every game run while the key has used no more than 70% of that pace (plus 5% headroom), the +EV sweep while under 90%; over the line, props fall back to the setting's few games a scan and the sweep waits. Props of a game more than 3 hours away are reused 5 minutes instead of 2 (a far game's quote is then about 6.5 minutes old, inside the 10-minute far-game limit). Game lines (a handful of requests a scan) are never held back. The app's key meter shows the key's use of its 25,000.
