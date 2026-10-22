> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# API Changelog

> This changelog is specific to our API users and LPs

**October 22, 2026 - Boxing, NBA Combo Ladders, NBA Futures, and Live First Halves**

**Boxing.** Every boxing match lists the same markets as UFC: `MONEY`, `TOTAL`, `GO_THE_DISTANCE`, `VICTORY_BY_KO_KTO_OR_DQ`, `VICTORY_BY_DECISION`, and `DRAW`.

**NBA combo ladders.** `POINTS_REBOUNDS_ASSISTS`, `POINTS_REBOUNDS`, `POINTS_ASSISTS`, and `REBOUNDS_ASSISTS` now list alternate lines as well as the main line.

**NBA steals + blocks.** `STEALS_BLOCKS` is back. For now, each player has only the main line.

**NBA futures.** You can trade `TO_MAKE_THE_PLAYOFFS` for each team and `NBA_CUP_WINNER`.

**Live first halves in NFL and NCAAF.** First-half markets now stay open once the game goes live. `GET /v3/types/markets/live` lists them for each league.

These go live with our production release on October 22. If your client filters by league or market type, add these to see the new markets.

**October 22, 2026 - Maker Credit Drops to 25% Live and 50% on Futures**

**On Thursday, October 22, 2026, the Maker Credit drops on every straight contract.** On live trading in every sport, it falls from 50% to 25% of the taker fee. On eligible futures it falls from 70% to 50%. Eligible futures are the futures in NFL, NCAAF, MLB, NBA, WNBA and NHL. A series winner market is not eligible.

The taker fee doesn't change. Each market's `fee.makerCredit` carries the new rate from the release. See [Trading fees](/api/concepts/fees.md) and the [Maker Credit Program](/maker-credit-program.md).

**October 21, 2026 - Maker Fill IDs in Trade History**

**A row from `GET /v3/history/trades` now carries `makeIds`.** It lists the `tradeId` of each tape print for the trade, one for each resting order, oldest first. Each print already carries the row's `tradeId` as `takeId`, so you can join the two from either side. See [Trade history](/api/catalog#trade-history).

**October 21, 2026 - Join the Tape to Trade History**

**A trade on the `trades` channel and from `GET /v3/catalog/markets/{id}/trades` now carries `takeId`.** It matches `tradeId` in `GET /v3/history/trades`, so you can match each tape print to its history row.

The two report one trade from opposite sides. The tape prints the resting side, once for each resting order. History has one row from the taker's side, with the taker's outcome and average price. See [Tape](/api/streaming/tape.md).

**October 21, 2026 - Taker Flag on the Private Stream**

**A `fill` event on the private `orders` channel now carries `taker`.** It is `true` when your order took liquidity and `false` when it rested. It matches `taker` from `GET /v3/portfolio/fills`. See [Private stream](/api/streaming/private.md).

**October 20, 2026 - Filter v2 Fills by Market**

**`GET /nbx/v2/emm/fills/all` now takes `marketId`.** It lists only your fills on that market, whatever their dates.

A `beg` and `end` window can catch only part of a market whose fills span weeks. `marketId` returns every fill you have on the market, and it combines with every other filter. See [Get all my fills](/deprecated/api-reference/positions/get-all-my-fills.md).

**October 20, 2026 - Fill IDs on the Private Stream**

**A `fill` event on the private `orders` channel now carries `fillId`.** It matches the `fillId` from `GET /v3/portfolio/fills`, so you can match a stream fill to its REST row. Two fills of the same size on one order have different IDs. See [Private stream](/api/streaming/private.md).

**A `trades` channel trade carries `tradeId` and `outcomeId`.** The docs showed `outcome` and no ID, but the wire hasn't changed. `tradeId` is the `fillId` of the resting order's fill. See [Tape](/api/streaming/tape.md).

**October 20, 2026 - Market Data**

**Trade history.** `GET /v3/history/trades` lists trades across every market, newest first. You can narrow it by `event`, `market`, `outcome`, `startsAfter`, and `startsBefore`.

**Candles.** `GET /v3/history/markets/{id}/candles` returns price candles for any market, at `1m`, `5m`, `1h`, or `1d`. You get one series per outcome.

**Participants.** `GET /v3/catalog/participants` lists each event's competitors and players, with the IDs of the markets about each. You can map a market to a team or a player without parsing its description.

**Consensus markets.** `GET /v3/catalog/consensus` lists the main line of each market family: one market per event and market type, and per player or competitor where the family has one.

**Live market types.** `GET /v3/types/markets/live` lists, for each league, the market types you can trade once an event goes live.

**Ticker fields.** A catalog market now carries `volume` and `openInterest`. Each of its outcomes carries `last`, its latest traded price, and `bid`, its best resting bid.

**Server time and exchange status.** `GET /v3/time` returns our clock, so you can measure your clock skew. `GET /v3/exchange/status` tells you whether trading is open. Both still answer while trading is paused.

**October 18, 2026 - Trades Snapshots Replay Only on Request**

**A v3 `trades` snapshot now carries only `seq` by default.** It used to carry the last 64 batches of trades for each market.

To get recent batches, set `replay` on `subscribe` or `snapshot`, from 0 to 64. A value outside that range returns `TRADE_REPLAY_OUT_OF_RANGE`.

If you recover a small gap from the snapshot, send `"replay": 64` to keep the old behavior. See [Tape](/api/streaming/tape.md#gaps).

**October 17, 2026 - Open Order Cost Is Always 0**

**`GET /nbx/v2/emm/account/open-costs-and-credit` now returns `openOrderCost` as `0`.** The field stays in the response, so your parser keeps working.

`openPositionCost` now lags by at most five seconds. It used to lag by up to five minutes.

To total your resting orders, list them with `GET /nbx/v2/emm/orders/all?status=OPEN`.

**October 15, 2026 - RFQ Executions No Longer Take offset**

**`GET /rfq/executions` no longer takes `offset`.** A request that sends `offset` now returns `400 Bad Request`.

Page with `after` instead. Pass the `rfq_id` of the last execution you read, and the next page starts after it. `after` reaches any depth, and a deep page takes as long as the first one. See [Executions](/api-reference/rfq/executions.md#page-through-your-history).

**October 15, 2026 - Live Taker Fee Rises to 0.06**

On Thursday, October 15, 2026, the live taker fee coefficient will rise from `0.03` to `0.06` on every straight contract, including NFL. The fee is still charged only while the event is in-game, and pregame trading stays free. The Maker Credit stays at 50% of the taker fee, so makers earn twice as much per in-game fill. See [Trading fees](/api/concepts/fees.md) and the [Maker Credit Program](/maker-credit-program.md).

**October 14, 2026 - Soccer Team Totals and 1H Totals**

**New soccer totals.** Every soccer match now lists team totals (`TEAM_TOTAL`) and first-half totals (`TOTAL_1H`).

These go live with our production release on October 14. If your client filters by market type, add `TEAM_TOTAL` and `TOTAL_1H` to see these markets.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.