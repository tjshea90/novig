# Streaming engine, first evidence (Tj's v0.94.1 file, 2026-10-12 ~19:30Z)
File: research/uploads/2026-10-12/beca2c17-vigilant-diagnostics-v0.94.1-2026-10-12-1530.txt.gz

## BoltOdds (item 4)
- Socket works: `socket_connected`, `"plan":"Trial"` (Tj's key is on a TRIAL plan), HTTP 101 permessage-deflate, 1533 games / 103,597 outcomes on the lines socket, 116 games / 26,452 outcomes on props.
- 34 books pushing (caesars, fanduel, draftkings, hardrock, betonline, bet105, bet99, bwin, partysports, sportsinteraction, betmgm, ...). **No sharp book among them** ("sharp books among them: none"): no Pinnacle, Circa, bookmaker, betcris, ProphetX, Kalshi, Polymarket pushed.
- Push lag (our clock minus the message's timestamp) median 0-54 ms: the timestamps are BoltOdds' own, so this proves their socket is fast, not that the book behind it moved recently.
- Verdict: BoltOdds cannot be a LIVE fair (no sharp book). Live stays Pinnodds. It stays the free pregame fair/consensus source.
- Names not priced: NFL/NCAAF "1st Half Team Total", "1st Half Moneyline", MLB "1st 5 Innings Team Total", "Spread" for NCAAF/NBA (check BoltMarkets: TBP10-style follow-up).

## Streaming engine v0.94.1 (the bugs this showed)
- Updates: 28 in the window (1 Novig-line, 27 BoltOdds refresh), 111,792 Novig signals but 1 fast update; slowest 153 s, refresh median 11 s, p95 115 s.
- Cause 1: `Scanner.streamUpdate` waited on the scanner's mutex, which a full scan holds for 43-119 s; the interval cycles (every 15 s) kept a scan running almost always (488 busy skips).
- Cause 2: a BoltOdds refresh ran first and every Novig push waited behind it; refresh gap was 1 s.
- Cause 3: the bid desk's pass (60 posts + 73 cancels, 20 s+) was awaited inside the update.
- Cause 4: lines judged 74,612 = ~2,700 rows handed to the auto-bet per refresh.
- Fixes (v0.94.2): streamUpdate never waits (tryLock, null when a scan holds it; engine counts it); Novig lines first, refresh gap = max(setting, 3x last refresh); bid desk pass started on its own, never awaited; while the engine runs the full scan gap is 300 s; timing split (priced / auto-bet) in Diagnostics and the event log.
- Unproven: how long a refresh takes once it stops waiting. Read "priced N ms, auto-bet N ms" in the next file.

## Other findings in the file
- `INVALID_QUERY ... invalid segment 'LALIGA'` x39: a league name saved by an older version was sent to `/v3/public/catalog/events`; fixed (only leagues this version knows are sent; ScannerTest).
- Repeat guard works: bids "36 waiting: the repeat guard holds it back".
- Bet by hand: "order sent and answered 56 ms, waiting for the end and reading the fills 5467 ms" (the ~5 s Novig delay is the wait, not the app).
- Bid desk passes regularly exceed 20 s (cycle.step.maker p95 20,004): look at why (60 posts per pass, rate gate, BetPriority).
