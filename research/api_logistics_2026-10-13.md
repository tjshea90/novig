# API logistics: which API for which data, best to worst (Tj, 2026-10-13)
How the app now decides (v0.95.8): (1) the sharp anchor and exchanges are always read: Pinnacle (PinnWire / pinnapi / Pinnodds REST, read at request time), Kalshi, Polymarket. (2) The aggregators below are a CHAIN per kind of data. The best one is asked first; the others stand by and are asked only if the better ones failed or ran out of keys, did not carry a game or a line, or carry a book you switched on. (3) The chain order is MEASURED after every scan (Diagnostics › API ranking): fresher first (90th-percentile age by the books' own times, 1-minute steps), feeds that sit more than 3 points from Pinnacle on DraftKings/FanDuel go last, the shipped order breaks ties. A feed that stands by is auditioned once every 6 h so it can earn the top. (4) Two feeds sending the same book for the same line: the quote stamped >20 s newer wins. (5) Keys of one API rotate when one is spent (KeyPool); when every key is spent, or the API does not answer, the next one on the chain takes over for that league. (6) Every bet shows "Fair odds from: <book> via <API> <age>".

| Data | First choice | Then | Notes |
| :- | :- | :- | :- |
| Sharp price (the fair line's anchor) | Pinnacle: PinnWire / pinnapi free keys (real time) | Pinnodds REST, Polymarket / Kalshi | never stale; keep as many free keys as you can |
| Game lines (ML, spread, total) of the soft books | measured best of PropLine, ParlayAPI, SGO, OddsPapi, Unabated (PropLine and ParlayAPI fresher than SGO in the first two files) | The Odds API last (scarce credits) | ordering is re-measured every scan |
| Player props | SGO props / BoltOdds props / ParlayAPI props (one call a league) / PropLine props (per game) | The Odds API props | PropLine props matched no Novig game in the v0.95.6 file (to fix) |
| 1st half / periods | ParlayAPI periods, SGO | | |
| Exchanges (Kalshi, Polymarket, ProphetX) | direct feeds, free | | the only free prop ladders besides Pinnacle |
| Closing lines (CLV) | SGO closes (Pinnacle, Circa), OddsPapi, Pinnacle's own | ParlayAPI closes, ESPN | |
| Live in play | Pinnodds socket (when you have it), BoltOdds soft average (paper) | | needs a push feed; PropLine Streaming Lite is the cheap candidate (not built) |

Free keys to collect: PinnWire, pinnapi (Pinnacle) and PropLine (1,000/day each). Paid, $50/month or less: ParlayAPI Starter $5 only; the rest is not worth it until the ranking says so.
