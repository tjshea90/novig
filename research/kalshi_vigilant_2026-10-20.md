# Kalshi inside Vigilant (KV) — design, evidence, open items (2026-10-20, Tj's request in the app session)

## Request
Put Kalshi beside Novig: +EV bets, bids, the same features, kept separate (Novig must not change), venue toggles (Novig / Kalshi / both), reuse the scanning and the GitHub engine, and an arbitrage finder between the two. Research the Kalshi trading API; the app sends the key to GitHub if needed.

## What the research found (docs.kalshi.com, 2026-10-20; details in KALSHI_API.md TRADING)
- Orders: V2 `POST /portfolio/events/orders` (YES-leg `bid`/`ask`, string dollars and counts, required `time_in_force` and `self_trade_prevention_type`), cancel `DELETE /portfolio/events/orders/{id}`, read order/fills/positions/settlements/balance. Legacy `/portfolio/orders` is kept as a fallback.
- Fees: taker `ceil_cent(0.07 x C x P x (1-P))`, makers 0.0175 only on series with maker fees; rounding overpayment is refunded by an accumulator. Not verified on a live fill.
- A Kalshi book is two-sided bids on YES and NO (a bid on one is an offer on the other), the same shape as Novig's, which is what lets Novig's bid desk run on it.

## Architecture (all additive)
1. `KalshiClient.venueOdds` + `KalshiVenueSource`: the existing Kalshi read, parsed in "venue mode" (ask prices, ticker, size) behind a default-off flag, so the Kalshi fair-line source is byte-identical.
2. `SportsbookScanner`/`BookBoard`/`Sportsbook.KALSHI`: the MGM scanner generalised by constructor parameters whose defaults are the old behaviour (the Novig build never runs it).
3. `KalshiBetPlacer` reuses `ApiBetPlanner` (pure) and the tracker; `KalshiAutoBettor` reuses `AutoBet`; `KalshiAsNovig` lets `MakerDesk` post bids; `KalshiArb` reuses `Planner` to match lines across venues.
4. App: `KalshiHub` (one object, own stores), the Kalshi tab, the venue card in Settings.

## Open / next
- KV9 the GitHub engine lane (scan from the engine's held boards, auto-bet/bids/arb with the key already sent as `KALSHI_KEY_ID`/`KALSHI_KEY_PEM`, behind a phone-only OFF/PAPER/LIVE switch; status on `cloud-feed`).
- Verify with a first real order: fee, `price_ranges` ticks (bids are floored to the cent), fractional counts, V2 vs legacy route.
- Props: Kalshi lists few player props; the arbitrage lists them only with the risky switch (a player who does not play settles differently).
- Bids read no Kalshi orderbook yet (no competition view): a bid is priced from the fair alone; Kalshi refuses one that would take.
