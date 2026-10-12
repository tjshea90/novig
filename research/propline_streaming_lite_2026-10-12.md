# PropLine Streaming Lite ($39/mo): is it good for Vigilant? (Tj, 2026-10-12)

Source: https://prop-line.com/llms.txt (the vendor's own page; nothing below has met a live key, and the page contradicts itself on how many books are polled: 109 vs 22).

What it is: REST 250,000/day (free tier is 1,000/day, what the app is built around), plus push of line_movement, steam, resolution, market_suspended, line_added/line_pulled, and `ev` events by HMAC webhook or WebSocket (`wss://ws.prop-line.com/v1/stream`, 2 connections, 5 webhooks, auth frame with `since_seq`, replay cursor with `truncated` flag, per-subscription sequence numbers for gap detection). Delivered events are not metered. Every tier has the same data freshness.

Latency (vendor's measurements): only FIVE books push directly: DraftKings, FanDuel, Fanatics, Kalshi, Polymarket (in play p50 0.1-1.1 s, p90 < 2 s, plus a few seconds to write the DB and emit line_movement). Every other book, **Pinnacle included**, is polled: 30-45 s in play, 60-90 s pregame.

Verdict for the app
- NOT a Pinnodds replacement: the sharp anchor (Pinnacle) is the slow one. For live fair it would be Kalshi/Polymarket pushes (sharp-ish on moneylines/some props, thin elsewhere) vs the soft DK/FD/Fanatics pushes. BoltOdds is the same shape (soft books, no sharp push), so this is a second soft-book + prediction-market push source, not a new kind of edge.
- Real value: (1) `market_suspended` / `line_pulled` events are an immediate "this line closed" signal the live lane can use to drop a bet; (2) Kalshi and Polymarket pushes with sub-2 s lag as a live sanity check against the soft average; (3) 250k REST/day removes PropLine's 1,000/day ceiling for scans, bids and closes; (4) `since_seq` replay makes the socket gap-free after drops, which Novig/BoltOdds sockets do not offer.
- Weaker: it duplicates DK/FD that BoltOdds already pushes; the `ev` event is computed by PropLine against its (polled) Pinnacle, so it must not drive bets; relayed Novig prices are ~20 s old (RESEARCH.md 23.6), useless for the Novig side.
- Cost-benefit: $39/mo is small next to BoltOdds ($349) and Pinnodds ($198+). Worth buying only for the pushed Kalshi/Polymarket + suspend events and the REST ceiling, not as the live engine's core. Recommend trying one month AFTER the BoltOdds live feed is shown to work on a real live slate (TBL7), so we know whether a second soft source adds anything.

If built (not started, needs Tj's word and a key via Settings, never a chat): `PropLineStream` (one socket, auth + since_seq), events -> a push listener for the live lane's close signal, Kalshi/Polymarket quotes into `BoltLiveConsensus`-style votes as one company each (BookCompany groups), Diagnostics line per socket (seq gaps, truncated, lag by book), a switch with fallback to REST. One test run against recorded frames. Check `PropLineClient` plan limits (`QuotaPolicy`) for the 250k tier.
