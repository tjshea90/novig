> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# API Changelog

> This changelog is specific to our API users and LPs

**October 29, 2026 - Participation Protection on NFL Player Props**

**Protected NFL player props are coming, starting in QA on Thursday, October 29, 2026.** If the player records no snap in the third quarter, fourth quarter, or overtime, a protected market resolves at its pregame fair market price.

Each protected market is a twin of a standard prop, with the same event, player, and `strike`. Its `marketType` ends in `_PROTECTED`, like `RECEIVING_YARDS_PROTECTED`. Fifteen NFL offense prop types get a twin.

Protected markets trade pregame only, on their own order book. A parlay RFQ can include protected legs, but protection must cover every leg that could be protected. See [Participation Protection](https://docs.novig.com/api/concepts/participation-protection.md).

**October 29, 2026 - Larger Request Bodies**

**A request body can now be up to 64 KiB, up from 8 KiB.** A full batch of 256 orders now fits. A larger body still gets a `403` with an HTML page from our edge. See [Edge 403](https://docs.novig.com/api/errors.md#edge-403).

**October 28, 2026 - Earn a Rate-Limit Tier**

**Your subaccount now earns its rate-limit tier from its monthly volume.** Early each month, we set each subaccount's tier from its volume over the last calendar month, in Eastern time. Tier 1 takes \$100,000 of volume, and each tier above it takes 10 times more.

**Each tier also raises how many orders you may keep open,** from 1,024 at Base to 16,384 at Tier 4. An order over the limit answers `OPEN_ORDER_LIMIT_EXCEEDED`, and `GET /v3/limits` now reports yours as `maxOpenOrders`.

Tier 4 is now the top tier. See [Tiers](https://docs.novig.com/api/throttling.md#tiers).

**October 28, 2026 - New Parlay Pricing Route**

**`POST /nbx/v1/parlay/request/unauthenticated` is gone and answers `404`.** Parlay prices now come from `POST /rfq/v2/quote/unauthenticated`.

Send `outcomeIds`, a list of outcome IDs. You get back one `price` and the time it expires. See [Parlay Pricing](https://docs.novig.com/affiliates/parlay-pricing.md).

**October 28, 2026 - Only Quotes Keep an RFQ Webhook Active**

**A `204` no longer keeps your RFQ webhook active.** We now stop sending RFQs to a webhook that hasn't returned a quote for 7 days. A `204` still passes on an RFQ, but it no longer counts toward the 7 days.

To restart a paused webhook, call `POST /rfq/pricer/ping`. See [Keep your webhook active](https://docs.novig.com/api-reference/rfq/webhooks.md#keep-your-webhook-active).

**October 27, 2026 - Geolocation Expiry**

**`GET /v3/account/geolocation` tells you when your keys stop placing orders.** `expiresAt` is when your last device check expires. After that, a placement answers `451 GEOLOCATION_EXPIRED`.

`code` is the `451` code a placement gets right now, or `null` while placements pass. The route answers even while the geolocation check refuses your other requests, and any key can call it.

To refresh the check, open the Novig app in a permitted state. See [Get the geolocation status](https://docs.novig.com/api-reference/accounts/get-the-geolocation-status.md).

**October 26, 2026 - Move from v2 to v3**

**If you trade on v2, you can now move to v3.** [Move from v2 to v3](https://docs.novig.com/deprecated/migrate-to-v3.md) walks you through it, from registering a key with your v2 token to mapping each v2 route to its v3 match.

You don't have to move, and v2 keeps working. You can also run both at once, for example quoting the order book on v3 and answering RFQs on v2.

Check two limits first: v3 has lower rate limits today, and it has no RFQ routes. The guide covers both.

**October 25, 2026 - Rate-Limit Tiers**

**Each subaccount now has a rate-limit tier.** A higher tier multiplies every throttle. Every subaccount starts at Base, which keeps today's limits.

`GET /v3/limits` now reports the limits of your subaccount's tier. See [Tiers](https://docs.novig.com/api/throttling.md#tiers).

**October 23, 2026 - Unrealized PnL on Open Positions**

**`GET /v3/portfolio/pnl` values your open positions at the last trade.** Each position carries `cost` and `last`, the price of the outcome's last trade. The response sums `basis`, what your positions cost, and `value`, what they would pay at those prices. Your cash plus `value` is your net liquidation value, and your unrealized PnL is `value` minus `basis`.

If you hold both outcomes of a market, we pay part of the payout to your cash right away. The sums leave that part out, so it isn't counted twice. A position on an outcome that hasn't traded yet counts at its cost.

You can narrow the read by `event` or `market`, and page it with `limit` and `after`. The sums cover every market your filter matches, not only the page.

The read covers open markets only. Once a market closes, its positions move to `GET /v3/history/positions` until it settles. See [Orders](https://docs.novig.com/api/execution/orders).

**October 22, 2026 - Throttles Apply per Subaccount**

**Throttles now apply per subaccount, not per key.** A subaccount's `trading` key and all of its `trading::read` keys draw from the same throttles, so adding a key no longer adds capacity.

A management key still has its own throttles. See [Throttling](https://docs.novig.com/api/throttling.md#keys-that-share-throttles).

**October 22, 2026 - Boxing, NBA Combo Ladders, NBA Futures, and Live First Halves**

**Boxing.** Every boxing match lists the same markets as UFC: `MONEY`, `TOTAL`, `GO_THE_DISTANCE`, `VICTORY_BY_KO_KTO_OR_DQ`, `VICTORY_BY_DECISION`, and `DRAW`.

**NBA combo ladders.** `POINTS_REBOUNDS_ASSISTS`, `POINTS_REBOUNDS`, `POINTS_ASSISTS`, and `REBOUNDS_ASSISTS` now list alternate lines as well as the main line.

**NBA steals + blocks.** `STEALS_BLOCKS` is back. For now, each player has only the main line.

**NBA futures.** You can trade `TO_MAKE_THE_PLAYOFFS` for each team and `NBA_CUP_WINNER`.

**Live first halves in NFL and NCAAF.** First-half markets now stay open once the game goes live. `GET /v3/types/markets/live` lists them for each league.

These go live with our production release on October 22. If your client filters by league or market type, add these to see the new markets.

**October 22, 2026 - Maker Credit Drops to 25% Live and 50% on Futures**

**On Thursday, October 22, 2026, the Maker Credit drops on every straight contract.** On live trading in every sport, it falls from 50% to 25% of the taker fee. On eligible futures it falls from 70% to 50%. Eligible futures are the futures in NFL, NCAAF, MLB, NBA, WNBA and NHL. A series winner market is not eligible.

The taker fee doesn't change. Each market's `fee.makerCredit` carries the new rate from the release. See [Trading fees](https://docs.novig.com/api/concepts/fees.md) and the [Maker Credit Program](https://docs.novig.com/maker-credit-program.md).

**October 21, 2026 - Maker Fill IDs in Trade History**

**A row from `GET /v3/history/trades` now carries `makeIds`.** It lists the `tradeId` of each tape print for the trade, one for each resting order, oldest first. Each print already carries the row's `tradeId` as `takeId`, so you can join the two from either side. See [Trade history](https://docs.novig.com/api/catalog#trade-history).

**October 21, 2026 - Join the Tape to Trade History**

**A trade on the `trades` channel and from `GET /v3/catalog/markets/{id}/trades` now carries `takeId`.** It matches `tradeId` in `GET /v3/history/trades`, so you can match each tape print to its history row.

The two report one trade from opposite sides. The tape prints the resting side, once for each resting order. History has one row from the taker's side, with the taker's outcome and average price. See [Tape](https://docs.novig.com/api/streaming/tape.md).

**October 21, 2026 - Taker Flag on the Private Stream**

**A `fill` event on the private `orders` channel now carries `taker`.** It is `true` when your order took liquidity and `false` when it rested. It matches `taker` from `GET /v3/portfolio/fills`. See [Private stream](https://docs.novig.com/api/streaming/private.md).

**October 20, 2026 - Filter v2 Fills by Market**

**`GET /nbx/v2/emm/fills/all` now takes `marketId`.** It lists only your fills on that market, whatever their dates.

A `beg` and `end` window can catch only part of a market whose fills span weeks. `marketId` returns every fill you have on the market, and it combines with every other filter. See [Get all my fills](https://docs.novig.com/deprecated/api-reference/positions/get-all-my-fills.md).

**October 20, 2026 - Fill IDs on the Private Stream**

**A `fill` event on the private `orders` channel now carries `fillId`.** It matches the `fillId` from `GET /v3/portfolio/fills`, so you can match a stream fill to its REST row. Two fills of the same size on one order have different IDs. See [Private stream](https://docs.novig.com/api/streaming/private.md).

**A `trades` channel trade carries `tradeId` and `outcomeId`.** The docs showed `outcome` and no ID, but the wire hasn't changed. `tradeId` is the `fillId` of the resting order's fill. See [Tape](https://docs.novig.com/api/streaming/tape.md).

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

If you recover a small gap from the snapshot, send `"replay": 64` to keep the old behavior. See [Tape](https://docs.novig.com/api/streaming/tape.md#gaps).

**October 17, 2026 - Open Order Cost Is Always 0**

**`GET /nbx/v2/emm/account/open-costs-and-credit` now returns `openOrderCost` as `0`.** The field stays in the response, so your parser keeps working.

`openPositionCost` now lags by at most five seconds. It used to lag by up to five minutes.

To total your resting orders, list them with `GET /nbx/v2/emm/orders/all?status=OPEN`.

**October 15, 2026 - RFQ Executions No Longer Take offset**

**`GET /rfq/executions` no longer takes `offset`.** A request that sends `offset` now returns `400 Bad Request`.

Page with `after` instead. Pass the `rfq_id` of the last execution you read, and the next page starts after it. `after` reaches any depth, and a deep page takes as long as the first one. See [Executions](https://docs.novig.com/api-reference/rfq/executions.md#page-through-your-history).

**October 15, 2026 - Live Taker Fee Rises to 0.06**

On Thursday, October 15, 2026, the live taker fee coefficient will rise from `0.03` to `0.06` on every straight contract, including NFL. The fee is still charged only while the event is in-game, and pregame trading stays free. The Maker Credit stays at 50% of the taker fee, so makers earn twice as much per in-game fill. See [Trading fees](https://docs.novig.com/api/concepts/fees.md) and the [Maker Credit Program](https://docs.novig.com/maker-credit-program.md).

**October 14, 2026 - Soccer Team Totals and 1H Totals**

**New soccer totals.** Every soccer match now lists team totals (`TEAM_TOTAL`) and first-half totals (`TOTAL_1H`).

These go live with our production release on October 14. If your client filters by market type, add `TEAM_TOTAL` and `TOTAL_1H` to see these markets.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.