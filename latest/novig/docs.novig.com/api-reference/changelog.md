> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# API Changelog

> This changelog is specific to our API users and LPs

**November 16, 2026 - New Parlay Taker Fee**

**On Thursday, November 19, 2026, the parlay taker fee changes to `0.055 × (−ln P) × stake`,** where `P` is the parlay's price. A \$100 parlay at 0.50 pays \$3.81.

The maker side doesn't change. See [RFQ trades](https://docs.novig.com/fees.md#rfq-trades).

**November 12, 2026 - Markets That Leave Drop Out of Your Subscriptions**

**A slow connection no longer closes with `MARKET_NOT_FOUND` or `EVENT_NOT_FOUND`.** When your connection falls behind, we resend a snapshot. That snapshot used to fail, and close the connection, if you still subscribed to a market or event that had left the exchange. Now we drop those and resend the rest.

**We tell you what we drop, in a message with no `nonce`:** `{"unsubscribed": {...}}`, the same shape as the reply to `unsubscribe`, just before the snapshot. See [Connection](https://docs.novig.com/api/streaming/connection.md#markets-that-leave-drop-out-of-your-subscriptions).

**November 12, 2026 - RFQ on v3**

**You can now price parlay RFQs on v3.** Register a subaccount as a maker with `POST /rfq/v3/maker`, then quote and confirm on the `GET /rfq/v3/ws` socket. [RFQ](https://docs.novig.com/api/rfq/overview.md) walks through a round.

**A v3 maker quotes from its subaccount's balance.** You sign every RFQ route with that subaccount's `trading` key. There's no webhook and no signature to check on your side.

**`GET /rfq/v3/executions` lists the parlays you filled.** `GET /rfq/v3/collateral` returns your collateral in parlays that haven't settled. `GET /rfq/v3/maker` returns your confirm rate and any lock.

**During the beta, you must confirm 99% of your last looks.** Below that, we lock you out of quoting for 5 minutes. See [Confirm rate](https://docs.novig.com/api/rfq/confirm-rate.md).

**v2 RFQ keeps working, and its pages are now under Deprecated.**

**November 11, 2026 - Participation Protection on NBA Player Props**

**Protected NBA player props are coming, starting in QA on Thursday, November 12, 2026.** The production date is not set yet. If the player plays no minutes in the third quarter, fourth quarter, or overtime, a protected market resolves at its pregame fair market price.

Eight NBA prop types get a twin: points, rebounds, assists, three-pointers made, and the four points, rebounds, and assists combos. Each twin's `marketType` ends in `_PROTECTED`, like `POINTS_PROTECTED`.

Protected NBA markets follow the same trading and parlay RFQ rules as protected NFL markets. See [Participation Protection](https://docs.novig.com/api/concepts/participation-protection.md).

**November 11, 2026 - Protect Some Legs of a Parlay**

**A parlay RFQ can now mix protected and standard legs.** You no longer have to protect every leg that could be protected. See [Participation Protection](https://docs.novig.com/api/concepts/participation-protection.md#protected-legs-in-a-parlay-rfq).

**November 11, 2026 - The Competitor on Each Outcome**

**Each outcome in a v3 market now carries `subjectId`, the ID of the team it's about.** A moneyline or spread outcome names its own side. Both outcomes of a team total name the team whose total it is.

The field is left out when an outcome isn't about one team, like a game total or a player prop. To get the team's name, look up the ID in [Participants](https://docs.novig.com/api/catalog#participants).

**November 10, 2026 - Locked Events and Markets**

**`GET /v3/exchange/locks` lists the events and markets that refuse new orders.** `eventIds` holds each locked event, and `marketIds` each market locked on its own. A new order in one gets `EVENT_LOCKED` or `MARKET_LOCKED`. Cancels still work.

**On a websocket, send `query_locks` for the same lists.** The reply comes under `locks`. Both cost one `read` token. See [Connection](https://docs.novig.com/api/streaming/connection.md).

**November 2, 2026 - More Watched Markets at Each Tier**

**Your tier now raises how many markets one websocket connection may watch,** from 8,192 at Base to 40,960 at Tier 4. A subscribe over your cap still answers `SUBSCRIPTION_LIMIT_EXCEEDED`. `GET /v3/limits` reports your cap as `maxWatchedMarkets`. See [Tiers](https://docs.novig.com/api/throttling.md#tiers).

**`GET /v3/limits` also reports the caps of your account's tier,** as `accountMaxOpenOrders` and `accountMaxWatchedMarkets`. With a management key, `maxOpenOrders` and `maxWatchedMarkets` are the Base caps of the key itself. These two fields are what your trading keys get.

**October 30, 2026 - Your Account's Tier on GET /v3/limits**

**`GET /v3/limits` now reports your account's tier as `accountTier`,** with any key.

`tier` still reports the tier of the calling key's own limits. A management key is always at Base, so it now sees `"tier": "BASE"` beside your account's real tier. See [Tiers](https://docs.novig.com/api/throttling.md#tiers).

**October 29, 2026 - One Tier for Your Whole Account**

**Your rate-limit tier now belongs to your account, not to each subaccount.** We add up the volume of all your subaccounts, and every subaccount runs at the tier that total reaches.

Each subaccount still keeps its own throttles and its own open-order limit, at your account's tier. A new subaccount starts at that tier, not at Base. See [Tiers](https://docs.novig.com/api/throttling.md#tiers).

**October 29, 2026 - Twice the Open Orders, and Your Tier on GET /v3/limits**

**Every tier now keeps twice as many open orders,** from 2,048 at Base to 32,768 at Tier 4. See [Tiers](https://docs.novig.com/api/throttling.md#tiers).

**`GET /v3/limits` now reports your tier as `tier`,** one of `BASE`, `TIER1`, `TIER2`, `TIER3`, and `TIER4`.

**October 29, 2026 - Participation Protection on NFL Player Props**

**Protected NFL player props are coming, starting in QA on Thursday, October 29, 2026.** If the player records no snap in the third quarter, fourth quarter, or overtime, a protected market resolves at its pregame fair market price.

Each protected market is a twin of a standard prop, with the same event, player, and `strike`. Its `marketType` ends in `_PROTECTED`, like `RECEIVING_YARDS_PROTECTED`. Fifteen NFL offense prop types get a twin.

Protected markets trade pregame only, on their own order book. A parlay RFQ can include protected legs, but protection must cover every leg that could be protected. See [Participation Protection](https://docs.novig.com/api/concepts/participation-protection.md).

**October 29, 2026 - No Edge Body Cap on the API**

**Our edge no longer caps the request body on the API.** The exchange accepts a body up to 100 KiB and answers a larger one with a `413` and `PAYLOAD_TOO_LARGE`. See [413](https://docs.novig.com/api/errors.md#413-content-too-large).

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