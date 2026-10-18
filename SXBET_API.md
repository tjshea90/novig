# SX Bet API — permanent research memory (Tj, 2026-10-18, chat t1792294037332)

Docs read in full 2026-10-18: `research/sxbet/docs_2026-10-18/` (90 pages from docs.sx.bet/llms.txt, verbatim). SX Bet is a peer-to-peer sports EXCHANGE (maker orders on a two-outcome book), USDC on the SX Network. Mainnet `https://api.sx.bet`, realtime `wss://realtime.sx.bet/connection/websocket` (testnet: `api.toronto.sx.bet`).

## What needs a key
- **Public (no key):** `/sports`, `/leagues`, `/leagues/active`, `/markets/active` (`leagueId`, `sportIds`, `eventId`, `type`, `betGroup`, `onlyMainLine`, `liveOnly`, `pageSize`, `paginationKey`; the reply is `data.markets` + `data.nextKey`), `/markets/find`, `/markets/popular`, `/teams`, `/fixture/*`, `/live-scores?sportXEventIds=a,b` (1-20 ids, REQUIRED), `/search`, `/metadata/obv3`, `/trades-v3/public`. Verified keyless from this container 2026-10-18 (sports, leagues, 100 NFL markets).
- **Key (header `x-sx-api-key`, created at sx.bet > Account > API, scope "Market data: read"):** `GET /orders-v3/odds/best` (100 hashes), `GET /orderbook-v3/snapshot/market|event`, `GET /user/realtime-token-v3/api-key` (the WebSocket token), and **every WebSocket channel** (the token comes from that route). Orders/positions need other scopes; Vigilant never asks for them.
- Vigilant needs ONLY "Market data: read". Never give it order/withdraw scopes.

## Limits (docs: developers/rate-limits)
Public markets/fixtures/leagues/sports/teams/live-scores/search share **500 requests / 60 s per IP**; best odds 5,500/60 s; metadata 500/60 s; all unauthenticated traffic 35,000 per 10 min. 429 carries `Retry-After`. Subscriptions do not use the request budget. WebSocket: Centrifugo v6, 512 channels per connection, ping 30 s, pong timeout 8 s, 1 MB outbound queue.

## Odds (developers/unit-conversions)
`percentageOdds` is an implied probability x 10^20, a string, **in the maker's frame**: a maker resting at 40% offers the taker 60% on the other side (`takerOdds = 1 - p`). `size` is the maker's stake in USDC base units (6 decimals). `showTakerPerspective=true` (best odds and snapshots only) swaps sides, inverts odds and restates `size` as the taker stake that consumes the level; Vigilant reads with it, then insists `ask1 + ask2` lies in [1, 1.06] — a pair below 1 would be the makers' frame and is dropped, so a misread frame can never become a fair line. NOT YET RUN WITH A KEY: the Test key button must save a real answer.

## Markets (api-reference/market-types)
Ids used: 226 12-incl-OT (moneyline, team sports), 52 12 (one-on-one), 28 Under/Over incl OT, 342 Asian Handicap incl OT, 166/201 tennis games total/spread, 29 rounds (MMA); also 1 (1X2), 2/3 (regulation totals/handicap), 53/77/63 (halves), 21/64 (1st period), 236/281/1618 (first 5 innings), 995 anytime TD (the only player prop type listed), 17 BTTS. Not yet used: halves, periods, F5, soccer 1X2. `line` is the FIRST outcome's handicap (`Dallas Cowboys +2.5` has line 2.5). `teamOneName` is the AWAY team in US sports (Dallas at Philadelphia, verified); `gameTime` is Unix seconds. NFL main line + alternates are separate markets (`mainLine`). Quarter lines (2, 3, 28) split a line into two markets: see developers/quarter-line-markets before pricing a .25/.75 line (Vigilant skips whole-number lines and takes half-point lines only).

## Realtime channels (all public once connected; need the token)
`best_odds_v3:global` (top-of-book change of ANY market, no history: seed from REST on every connect), `orderbook_v3:{marketHash}` / `orderbook_v3_event:{eventId}` (full book per publication, `version` string, apply only if strictly greater), `fixtures:live_scores` (score, period, `extra` JSON), `fixtures:global` (status), `main_line:global` (new main line market per event+type), `markets:global` (create/update/settle), `recent_trades_v3:global` (public tape). Centrifugo JSON protocol: connect command with the token, `sub` commands, server pings answered with pongs. Recovery: `recoverable` on channels with history (orderbook_v3_event, markets, main_line, fixtures).

## Fees
Per account, on PROFIT at settlement (taker/maker payout fee, `GET /user/fees-v3`, null = 0). Vigilant only reads prices here; fees matter if SX is ever bet on.

## How Vigilant uses it (built 2026-10-18)
`data/.../reference/SxBetConvert.kt` (pure: markets + books -> `RefEvent`s; 1/ask decimal odds, the planner de-vigs the pair), `SxBetClient.kt` (the `ReferenceSource` "sxbet": paged public markets per league, then best odds 100 hashes a call, 3 at a time; no key = no quotes; 429 rests the source). `League.sxLeagueIds` maps leagues (NFL 243, NCAAF 246, MLB 171/1180/1190, WNBA 1384, NHL 3, NBA 1, NCAAB 2, UFC 34/10009, Boxing 1403/1490; ATP/WTA by sport 6 + label prefix, as SX has one league per tournament). Test: `SxBetTest`. Next: wire the switch and key into the app and the engine, the WebSocket (best_odds_v3 + live scores), the live lane, the Test-key sample. See TASKS.md TCL111.
