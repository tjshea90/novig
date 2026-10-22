> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Perps Changelog

> Recent changes to the Polymarket Perps API and platform

Notable changes to the Polymarket Perps API.

**Oct 22, 2026**

Builder stats, proxy management rules, and read-only key restrictions

No removed endpoints, no removed or renamed fields, no new error identifiers,
no rate-limit changes, and no WebSocket message changes. One behaviour change
to trading-key (proxy) management applies from the deploy — read it if you
create or delete proxies programmatically.

**Changed**

* **Proxy (trading key) management on `POST` / `DELETE /v1/account/proxy`.**
  Another account's main wallet can no longer be registered as a proxy.
  Create-proxy returns `400` `proxy is an account address`; a request that
  reaches the engine anyway is rejected with the existing
  `proxy_already_exists`. Registering your own main address as your own proxy
  still works. An account's main wallet always manages its own account's
  proxies, even if another account has listed that wallet as a proxy. The old
  `400` `must be signed by main address, not proxy` no longer fires in that
  case. A signer that owns no account and is only listed as some account's
  proxy is now accepted on create-proxy: it onboards a new account of its own
  (whitelist still applies) and registers the proxy there — never on the
  account that listed it. Consequence: if you sign create-proxy with a trading
  key instead of the main wallet, you now get a separate, empty account owned
  by that key instead of a `400`. Delete is unchanged: a signer with no
  account still gets `400` `must be signed by main address, not proxy`. Sign
  create-proxy and delete-proxy with the account's main wallet, as documented.
  Never with a trading key.
* **Read-only API keys.** A proxy whose label starts with `read-only:` now
  gets `403` on `POST /v1/account/invite`, `POST /v1/account/referral`,
  `POST /v1/blp/enroll`, and `POST /v1/account/notifications/read`. Every
  private `GET` and the private WebSocket channels are unchanged. Only if you
  have labelled a trading key `read-only:…`: rename it or use another key.
  Self-service read-only key creation ships separately.
* **Competition tie-break timestamp.** `GET /v1/info/competition/leaderboard`
  entries and `GET /v1/info/competition/account` gain optional
  `first_fill_at` (ms): the account's first counted fill; absent when unknown
  or when there are no counted fills.
* **Order-path latency.** Since 20 October the order path carried an extra
  \~1 ms at the median; tail latencies were unaffected. This rollout removes
  it. If you alert on latency baselines, expect the median to drop back by
  about 1 ms after the window.

**Added**

* **Public builder statistics.** `GET /v1/info/builder-stats?address=…&period=…`
  is public, with no authentication. `period` is one of `1d`, `1w`, `1m`,
  `1y`, or `alltime` (rolling 24 h / 7 d / 30 d / 365 d, or all history).
  Returns `address`, optional `name`, `period`, `start_timestamp`,
  `end_timestamp` (ms), `as_of_sequence`, `user_count`, and `data[]` with one
  entry per `fee_asset`: `volume`, `exchange_fee` (signed; includes negative
  maker rebates), `builder_fee`, and `volume_by_asset[]` per market. Maker
  and taker sides count separately, zero-builder-fee fills included; disabled
  builders still report history. Cached 30 s; a miss costs 10 rate-limit
  units.
* **Builder display names.** `GET /v1/info/builder` gains nullable `name`
  (live since 20 Oct, now part of this release). `PATCH /v1/account/builder`
  renames a registered builder — signed by the builder account's owner wallet,
  `op.type`: `"updateBuilder"`, `op.args.name` only; trimmed, 1–100
  characters, no control characters; last write wins; repeating the saved
  name succeeds; disabled builders can rename. Nothing else about the builder
  changes.

**Clarified**

* **OpenAPI spec.** `portfolio.cumulative_funding` and `fee_tier` are now
  typed nullable in the HTTP and WebSocket specs. v1 still always returns a
  value for both; generated clients will see them as optional. The spec adds
  `GET /v1/info/builder-stats`, `PATCH /v1/account/builder`, and the new
  create/delete-proxy rules.

**Not Yet Active**

* **Odds orders.** `POST /v1/trade/odds-orders` returns `404` and
  `GET /v1/info/odds-references` returns an empty list. This release updates
  odds documentation and the odds-references fields (`acct_cap`, `class`,
  `underlying`); none of it affects you until odds orders launch, which we'll
  announce separately.

**Rollout**

Order placement pauses during the window and cancels keep working. Retail
WebSocket connections drop once when the gateway restarts; reconnect and
resubscribe.

**Oct 20, 2026**

Builder self-registration, total-PnL history, and recently-traded instruments

No breaking changes to existing request or response fields. No authentication
changes.

**Added**

* **Builder self-registration.** `POST /v1/account/builder` lets a builder
  register a display name without admin credentials. Sign with the owner
  wallet of an existing account using the standard signed-operation format
  and supply the name in `args.name`. Repeating registration with the same
  name is safe. A different name for an already-named builder returns `409`
  and is not overwritten. An admin-disabled builder receives `400`
  `builder_not_enabled`.
* **Lifetime total-PnL history.** `GET /v2/info/pnl?address=…&interval=…`
  is public. Total PnL is equity (including unrealized) minus cumulative net
  capital: deposits, withdrawals, and internal transfers, including reward
  and referral payouts. Fees, funding, and builder income stay inside PnL, so
  do not subtract them again. A bucket whose capital history is unknown is
  returned as unavailable, not zero.
* **Recently traded instruments.** `GET /v1/account/recently-traded`
  (authenticated) returns up to 10 perpetual instruments the account acted
  on most recently, newest first, each with the time of the latest action.
  Actions that count include placing, modifying, or filling an order
  (including closing a position), placing or cancelling a TP/SL, changing
  leverage or margin mode, and adding or removing isolated margin. Activity
  older than 90 days is not listed, and only currently enabled instruments
  are returned. The ranking builds up from activity after the release, so an
  account with no activity since then gets an empty list.

**Changed**

* **Portfolio coverage.** `GET /v1/account/portfolio` and public portfolio
  responses gain `coverage.collateral`. On v1 it always returns
  `"configured"`.
* **Pricing status on tickers and index.** Ticker rows (REST and the
  `tickers` WebSocket channel) and index entries gain an optional
  `pricing_status` object describing the instrument's engine pricing mode. It
  is omitted for instruments outside the pricing protocol.
* **Public trades paging.** `GET /v1/info/trades` accepts `cursor` and
  `sort` (`desc` default, `asc`) and returns a cursor for the next page.
  Every trade in a dense millisecond is now reachable.
* **Public fills by market.** `GET /v1/info/fills?instrument_id=…` now
  returns only that market's fills. Before, the parameter was accepted and
  ignored. Filtered reads scan at most 250,000 rows; larger scans return
  `413` — narrow the time window. With `instrument_id`, the end time is
  capped at now. Without `start_timestamp`, the window starts 30 days before
  that end. With `instrument_id`, paging needs the returned keyset cursor;
  the legacy last-trade-ID cursor returns `400`. Unfiltered requests behave
  as before.

**Fixed**

* **Pathological JSON depth.** Request bodies and WebSocket messages nested
  more deeply than JSON parsing allows are now rejected as a normal parse
  error. Before, they could crash the connection's gateway process. No real
  client sends anything close to this depth.

**Clarified**

* **OpenAPI spec alignment.** `docs/http/http.json` now describes exactly
  the routes the production gateway serves. Removed:
  `/v1/info/executions` and other v2-only routes that were never served by
  v1. Added: `/v1/info/account-tiers` and `/v1/info/perps-whitelist`, which
  were missing. Fills, position-fills, and trades match production shapes
  again. Integers carry `x-js-safe-integer`; all v1 IDs fit in a JavaScript
  number.

**Rollout**

Order placement pauses during the window and cancels keep working, as usual.
Retail WebSocket connections drop once when the gateway restarts; reconnect
and resubscribe.

**Oct 6, 2026**

Concurrent WebSocket posts on the retail gateway

No breaking changes for clients that match WebSocket responses by `id`.
The rollout reconnected each active retail WebSocket connection once.

**Changed**

* **Concurrent WebSocket posts (retail gateway).** Signed posts that carry
  an `id` (order create, modify, cancel, cancel by client order id,
  leverage, and margin) now run up to 16 at a time per connection on
  `wss://ws.perpetuals.polymarket.com`, matching the behavior the MM gateway
  has had since Sep 1. Responses return in completion order: match them by `id`,
  not by position. Posts naming the same client order id or order id keep
  their send order. `cancelAll`, leverage, and margin updates act as
  connection-wide barriers. Posts without an `id` stay serial among
  themselves but are not ordered after earlier `id`-carrying posts still in
  flight. For strict ordering, await each response before sending the next
  post, or omit `id` on every post.

**Oct 6, 2026**

Leaderboard entries carry ROI

No breaking changes for clients that ignore unknown fields; strict schema
validators must add one required field.

**Changed**

* **Leaderboard entries gain a required `roi` field.** Every ranked entry
  and the optional `address=` row on `GET /v1/info/leaderboard` now
  carries `roi`: the window's return on investment as a fraction string
  (`"0.4527"` is 45.27%), computed as
  `pnl / max(100, starting_equity + max_net_deposits)`. `pnl` is the same
  value served on the row, so the two always agree; `starting_equity` is
  the account's equity just before the window start (0 for `all`);
  `max_net_deposits` is the peak cumulative net deposit during the window,
  never negative, so withdrawing profits cannot inflate the ratio. Values
  are rounded half-even to 6 decimal places with trailing zeros removed,
  and `"0"` when `pnl` is 0. Display-only: `sort_by` accepts no new
  values.

**Sep 30, 2026**

Statistics retain the current hour, and WebSocket klines publish the newest candle

No breaking changes. Field names, wire types, and examples are
unchanged; only 24-hour statistics values and kline selection move.

**Fixed**

* **Statistics now include the in-progress hour.** `GET
  /v1/info/statistics` and the WebSocket `statistics` channel
  previously dropped the current hourly candle whenever the rolling
  24-hour window spanned 25 populated hours. The `klines` series
  stays oldest-first with at most 24 buckets, but now keeps the
  in-progress hour and drops the oldest populated bucket instead, so
  a 24-hour high or low derived from the returned buckets reflects
  the current hour. `open_price` still reports the open of the
  earliest populated candle in the full window — its start rounded
  down to the minute — even when that bucket is omitted from the
  returned series, and is `"0"` when the window has no candles.
* **WebSocket `klines` subscriptions publish the newest populated
  candle.** The poller previously could emit an older hourly bucket;
  it now selects the newest populated candle in its lookback. REST
  klines pagination is unchanged.

**Docs**

* **`volume` was always quote-asset notional.** The statistics
  `volume` field description previously said "in contracts"; it is
  the sum of price times quantity over the window, as a numeric
  string. Values are unchanged.

**Sep 29, 2026**

Earlier WebSocket order replies and steadier matching under floods

**Fixed**

* **WebSocket order replies can arrive earlier.** The gateway now
  yields between broadcast batches, so the reply to an order you just
  sent is no longer held behind a long run of market-data frames.
  Payloads and notification IDs are unchanged. Correlate replies by
  request id; do not assume a burst of broadcasts always arrives
  before a later reply on the same socket. Public snapshot updates
  may spread across a few more batches in busy periods; content and
  cadence targets are unchanged.
* **Matching stays responsive under order floods.** Order
  acknowledgement latency should stay flat during market-wide
  re-quote bursts.

**Sep 27, 2026**

Required fill fields, rewards date params, and instrument close-only statuses

No breaking changes to order placement, cancellation, or authentication.
Fill objects gain three required fields, and `GET /v1/account/rewards`
now rejects `start_timestamp` / `end_timestamp` with `400`. Parsers
should accept two new terminal order statuses and two new instrument
fields for delisting. TWAP controls, position share-card snapshots,
display symbols, and the builder-codes read surface are usable.
TWAP order entry, chase limits, GTD, partial TP/SL closes, builder
attribution, trailing stops, and synthetic pricing ship switched off.
Expect one WebSocket reconnect and a short cancel-only window.

**Changed**

* **Fills gain three required fields.** Every fill on
  `GET /v1/account/fills`, `GET /v1/info/fills`, `GET /v1/info/trades`,
  and the WebSocket `fills` channel now includes `settlement` (boolean
  — `true` only for a position closed by instrument settlement),
  `builder_fee` (string, `"0"` until builder codes are enabled), and
  `total_fee` (string — exchange fee plus builder fee). `pnl` is
  unchanged: realized PnL before fees; subtract `total_fee` once for
  the net result. Strict schema validators must add the three fields.
* **`GET /v1/account/rewards` no longer accepts `start_timestamp` or
  `end_timestamp`.** They were previously ignored; they now return
  `400` with `invalid start_timestamp` / `invalid end_timestamp`. Use
  `date`, `start_date`, and `end_date`.
* **Two new terminal order statuses: `instrument_close_only` and
  `instrument_settled`.** Operators can put an instrument into
  close-only — orders that would increase exposure are rejected with
  `instrument_close_only`, resting increase-exposure orders are
  canceled with that status, reduce-only trading continues — and later
  settle and delist it. Remaining positions close at the trailing
  30-minute time-weighted index average with no fee; remaining orders
  cancel with `instrument_settled`; further orders are rejected with
  `instrument_settled`. Settlement fills arrive as normal fills with
  `settlement: true`. Nothing is delisted by this release. Add the
  statuses now so strict parsers do not fail the first time it happens.
* **Two new fields on `GET /v1/info/instruments`.** `close_only`
  (boolean, always present) and `settlement` (object with `sequence`,
  `timestamp`, `price`, `insurance_debit`; present only after
  settlement). Instruments may also carry an optional `display_symbol`;
  `symbol` is unchanged.
* **SBE schema version 49.** Market-maker binary streams stamp schema
  49\. All changes are appended fields or new messages; decoders that
  ignore fields beyond their compiled version keep working. Anything
  that asserts the schema version must accept `49`.
* **`exp` on signed requests is now capped instead of rejected.** A
  value beyond now + 5 s is clamped to now + 5 s (previously refused
  with `command expiry too far in future`). A value at or before the
  server clock is still rejected with `400` `command expired`. Omitted
  still defaults to 5 s.
* **Fee-tier volume can be aggregated across an account group.**
  Opt-in per group, set by us on request. Your 14-day volume for tier
  assignment becomes the group's combined volume. Nothing changes
  unless you ask for aggregation.

**Fixed**

* **Insolvent shells are settled automatically.** An account left with
  negative cash and no open positions is now settled against the
  insurance fund by the liquidator instead of sitting negative
  indefinitely. Backstop Liquidity Providers are never named for these.
* **Fewer spurious sign-outs on the web identity path.** A stalled
  session-cookie verification is retried once instead of being cached
  as logged out. Proxy authentication is unchanged.
* **`GET /v1/info/registered` on the public host** now adds
  `must-revalidate` to its existing cache lifetime.

**Added**

* **TWAP controls.** `DELETE /v1/trade/twaps`, `PATCH /v1/trade/twaps`
  (pause/resume), and `GET /v1/account/twaps` are live. Creating a
  TWAP (`POST /v1/trade/twaps`) returns `404` until we announce it.
* **Position share-card snapshots.** `POST /v1/info/position-snapshots`
  returns active and historical position snapshots for an address;
  public, or authenticated if you send proxy headers.
* **Builder codes — read surface only.** `approveBuilder`,
  `GET /v1/account/builder-approvals`, `/builder-earnings`,
  `/builder-earnings-summary`, `GET /v1/info/builder`, an optional
  `builder` `{address, fee_rate}` on order requests, and a private
  `builderFills` WebSocket channel are deployed. Attribution is not
  enabled: sending `builder` today returns `builder_codes_disabled`.
* **Public market-data cache lifetime.** Public host `200`s carry
  `Cache-Control: public, max-age=…` matching the route's server-side
  cache window (live since Sep 22). Market-maker hosts send no cache
  lifetime.

**Rollout**

The market-maker gateway is briefly unavailable while its pods roll, so
WebSocket connections to the MM gateway drop once and need to reconnect.
The exchange is in cancel-only mode for the duration: cancels,
liquidations, deposits, withdrawals, and funding keep working; new
orders are rejected until the window closes. While cancel-only is on, a
cancel that races an order's acceptance is answered `order_in_flight`;
held cancels resume when the window closes.

**Sep 21, 2026**

Reversal margin at worst executable price, rounded volume strings, and daily reward rows

No breaking changes to order placement, cancellation, authentication, or
market data. Orders that flip a position now reserve margin at the worst
executable price, so some reversals that used to pass can return
`insufficient_margin`. Volume, notional, and candle strings are correctly
rounded, and daily liquidity and open-interest reward rows appear after
12:05 UTC. Trailing stop-loss, TWAP, and good-till-date are in this build
but not enabled. Expect one WebSocket reconnect and a short cancel-only
window.

**Changed**

* **Reversing a position reserves more margin.** An order that, together
  with your resting and in-flight same-side orders, would take your
  position through zero is now margined for its closing leg at the worst
  price it could fill at — its own limit, or the trade price band edge
  for a market order — instead of at mark. Isolated: the closing leg
  reserves the loss from your entry to that executable price; a
  profitable close at that price reserves nothing extra. Cross: an
  adverse-side order larger than your position adds mark-to-executable
  distance across the whole order on top of normal initial margin.
  Two-sided quotes that straddle the mark add nothing. Orders that used
  to pass can now be refused with `insufficient_margin`, and
  `available_order_margin` plus withdrawable amount read lower while
  such a ladder rests. Non-reversing orders are unchanged. If you flip
  via market orders or aggressive limits, check
  `available_order_margin` first or size the reversal in two steps.
* **Rounded volume, notional, and candle strings.** `high`, `low`, and
  `volume` on `GET /v1/info/klines` and `klines::*`; volume and notional
  on `GET /v1/info/statistics` and `statistics::*`,
  `GET /v1/info/exchange-stats`, `GET /v1/account/stats`,
  `GET /v1/info/leaderboard`, and referral history/children are now
  rounded to at most six decimals (`"23.923000000000002"` becomes
  `"23.923"`). `open` and `close` are unchanged. Numeric consumers see
  no change; byte-comparing cached strings will see diffs. Fee-tier
  volume inputs are not rounded. All-time leaderboard and
  referral-children volumes above roughly \$9B may still carry float
  artefacts.
* **Daily reward rows arrive automatically.** `GET /v1/account/rewards`
  gains a new date row for liquidity and open-interest rewards shortly
  after 12:05 UTC, with `reward_distributed: false`. Distribution is
  unchanged. Poll after 12:05 UTC. Referral accrual is unchanged.

**Fixed**

* **Fewer order-path errors.** A stalled risk evaluator now releases
  and re-evaluates decisions in place instead of restarting. The
  post-deploy history/info probe runs from one process instead of every
  gateway pod, which removes the ClickHouse 5xx burst after a rollout.
  The MM REST pool keeps idle upstream connections open for 300 s
  instead of 60 s.
* **Liquidations after an outage resume gradually.** Escalation and
  sizing clocks no longer advance while the risk evaluator is
  unavailable or an instrument is held, so an in-progress liquidation
  resumes with normal partial clips instead of a full-size close.
* **Isolated-margin backing accounting is now in effect.** An
  isolated-position backstop transfers the position's live backing
  amount rather than its legacy margin amount. No request or response
  shape changes; recipients keep reading the transferred amount from
  `GET /v1/account/backstops`.
* **Fee tier floors.** `fee_tier` on the portfolio and the fees you
  are charged now respect an account floor when Polymarket sets one.
  No account has a floor today, and a floor can only raise your tier.

**Clarified**

* **Market session categories.** The sessions page now lists `Weekend`
  and `Closed` alongside Regular, Premarket, Postmarket, Overnight,
  Halted, and Unknown, and drops `Disrupted`, which never existed in
  the engine. No instrument uses the new categories yet.

**Rollout**

The market-maker gateway is briefly unavailable while its pods roll, so
WebSocket connections to the MM gateway drop once and need to reconnect.
The exchange is in cancel-only mode for the duration: cancels,
liquidations, deposits, withdrawals, and funding keep working; new
orders are rejected until the window closes. While cancel-only is on, a
cancel that races an order's acceptance is answered `order_in_flight`;
held cancels resume when the window closes.

**Sep 17, 2026**

Held cancels during accept, 503 on engine timeouts, and reduce-only displacement

No breaking changes to order placement, authentication, or market data.
Cancels that arrive while an order is still being accepted now succeed,
several cancel and account routes return `503` instead of `500` when the
engine times out, a more aggressive reduce-only order can displace your
own less aggressive resting reduce-only orders, and isolated-margin
accounts get a stricter check when a ladder could reverse a position.
Market makers should see fewer connection errors on the MM REST host.
Expect one WebSocket reconnect and a short cancel-only window.

**Changed**

* **Cancel while an order is still being accepted now works.** A cancel
  that reached the matching engine a few milliseconds after
  `createOrders` returns used to be refused with `order_in_flight`. The
  cancel is now held and applied when the accept lands: success if the
  order was removed, or `order_already_terminal` if it filled or was
  rejected first. You will still see `order_in_flight` for an order in
  the taker-delay queue, while the account is being liquidated, or while
  the exchange is in cancel-only mode. Do not treat
  `order_already_terminal` as a failure to retry, and do not send the
  same cancel twice in that window — the second returns
  `order_not_pending_engine`. Ordinary resting-cancel/fill races still
  return `order_not_found`.
* **Engine time-outs on cancel and account routes now return `503`.**
  When the engine does not answer in time, cancel, modify, leverage,
  margin, auto-cancel, withdraw, internal transfer, and delete-trading-key
  routes return `503` with `{"status":"err","error":"service_unavailable"}`
  instead of `500` `internal_error`. The WebSocket forms carry the same
  `error`. Treat it as retryable with a short backoff. Unlike other
  `503`s, this one does not prove the request was not applied — a retry
  may find the cancel already done. These routes are safe to repeat.
  `createOrders` and trading-key creation still return `500` on a
  time-out so a blind retry cannot duplicate an order.
* **A more aggressive reduce-only order now cancels your own less
  aggressive resting reduce-only orders.** A restable reduce-only order
  priced more aggressively (higher bid or lower offer) displaces your
  strictly less aggressive resting reduce-only orders on that side,
  least aggressive first, until it fits. Displaced orders are reported
  with status `reduce_only_expired`. At equal prices the resting order
  keeps priority. If the new order still does not fit, the old
  trim/`reduce_only_invalid` behavior applies. IOC and FOK reduce-only
  orders are unaffected. A fired stop-loss can displace a resting
  take-profit on a GTC bracket. Displaced orders cancel attached TP/SL
  children too. Handle `reduce_only_expired` as a normal cancellation.
* **Isolated margin: stricter check when a ladder could reverse a
  position.** On isolated-margin accounts, an order that could flip your
  position — counting resting and in-flight same-side orders — is now
  margined at the worst case across those orders. Orders that used to
  pass can now be rejected with `insufficient_margin`, and
  available-margin figures show the larger reservation while such a
  ladder rests. This mostly affects desks quoting far from the mark
  against a small position. Canceling the far leg or adding collateral
  clears it. Spurious `invalid_leverage` rejections when two same-side
  orders arrived a few milliseconds apart are gone. Cross-margin
  accounts are unaffected.
* **Cancel-by-client-order-id wait is now about 100 ms** (was 50 ms),
  so a cancel racing its create is applied more often. If the order
  never appears, `order_unknown` arrives after about 100 ms.

**Fixed**

* **Far fewer connection errors on the MM REST host.** Per-server
  ceilings are doubled (per-IP in-flight 128 → 256) and MM edge
  connection pools stay warm. If you throttled yourself to about 128
  in-flight requests per IP, you can raise that to 256.
* **Funding after a service restart** no longer carries a previous,
  already-settled hour's premium into the new hour's rate. Steady-state
  funding is unchanged.

**Added**

* **`fee_tier` on the portfolio.** `GET /v1/account/portfolio` and the
  private WebSocket `portfolio` channel now include `fee_tier`, a
  zero-based index into the `tiers` array of `/v1/info/fees`. It is
  required, so clients with strict schema validation must add it. A
  tier change shows on the next portfolio push (every 5 s).
* **Backstop history.** `GET /v1/account/backstops` lists transfers
  where your account received or provided backstop liquidity. Newest
  first, 100 per page, cursor pagination, 90-day default window. For a
  short time after the deploy it may return an empty list with
  `coverage.notes` containing `account_backstop_history not yet
  migrated`.
* **Server identity for support.** Application-generated HTTP responses
  and WebSocket handshakes now carry an `x-pmp-pod` header, and a
  successful WebSocket auth reply includes `data.pod`. Log it alongside
  the `ref` of any error you report; do not route on it. Treat
  `data.pod` as optional during rolling deploys.
* **Instrument logos.** Instrument objects on `/v1/info/instruments`
  and `/v1/account/favorites` may gain an optional `logo` object with
  `light` and `dark` SVG URLs. Instruments without a logo omit the key.

**Clarified**

* **Margin-mode changes.** Switching an instrument between isolated and
  cross requires no position and no open orders on that instrument.
  A position blocks the change with `position_exists`; open orders
  block it with `open_orders_exist`. Both codes already existed.
* **Per-account limits** on signed actions per minute and open orders
  have been in force since May and are assigned by 14-day volume. Caps
  and usage are on `GET /v1/account/limits`. REST request rate is
  limited per IP, not per account.

**Rollout**

The market-maker gateway is briefly unavailable while its pods roll, so
WebSocket connections to the MM gateway drop once and need to reconnect.
The exchange is in cancel-only mode for the duration: cancels,
liquidations, deposits, withdrawals, and funding keep working; new
orders are rejected until the window closes. While cancel-only is on, a
cancel that races an order's acceptance is answered `order_in_flight`
as before; held cancels resume when the window closes.

**Sep 15, 2026**

Trading-key internal transfers within account groups; Retry-After on internal-transfer 503s

No breaking changes. Trading keys can now move collateral between accounts
in the same exchange-managed account group. Owner-signed internal transfers
are unchanged.

**Changed**

* **Trading-key internal transfers within account groups.** A non-expired
  trading key registered to the source account can sign
  `POST /v1/account/internal-transfer` when source and destination share the
  same nonzero account group. The key must belong to the source account,
  and an ungrouped source or destination does not qualify. Owner signatures
  keep working regardless of groups, and withdrawals still require the
  owner key. An expired trading key on this route receives `proxy_expired`
  instead of `signer_does_not_match_account`. A gateway that cannot prove
  its state is fresh enough to authorize the transfer returns `503`
  `service_unavailable` with `Retry-After: 1` without submitting anything.
* **Every handler-produced `503` on `POST /v1/account/internal-transfer`
  now carries `Retry-After: 1`.** Load-shed `503`s already did. A freshness
  or load-shed rejection happens before submission, but an engine response
  timeout can occur after submission. Retry after the header's delay using
  the exact original signed request; do not generate a new signature, salt,
  or timestamp. A replay rejection does not establish the original transfer
  outcome.

**Sep 15, 2026**

Gateway pod identity: x-pmp-pod response header and pod field on auth acknowledgements

No changes to request shapes, authentication inputs, or existing response
fields. Responses gain correlation metadata identifying the gateway pod
that produced them, so your request logs can be matched against exchange
logs.

**Added**

* **`x-pmp-pod` response header.** Every application-generated HTTP
  response — including errors and overload responses — and every WebSocket
  handshake response (the successful `101` upgrade and handshake
  rejections) carries the identity of the gateway pod that produced it.
  Browsers can read it through CORS. Responses to malformed HTTP carry no
  header, and a cached response keeps the identity of the pod that
  originally produced it. The value is correlation metadata only; pods are
  not addressable.
* **`pod` in successful WebSocket auth acknowledgements.** The `data`
  object of a successful `auth` acknowledgement now includes `pod` with the
  same identity for the pod serving the connection. Auth failures and all
  other acknowledgements are unchanged. Treat the header and the field as
  optional: older pods omit them during a rolling deploy.

**Sep 14, 2026**

Non-paginated history routes reject cursor; 50-level book and leaderboard account-value sort

No breaking changes to order placement, cancellation, authentication, or
market data. Fourteen history and info routes now reject a `cursor` query
parameter they used to ignore. Order-path latency should come back down
after the September 8 increase, exact order lookups should return far fewer
`503`s, and the index price for Hyperliquid-sourced instruments is
corrected. Optional: a 50-level order book channel and an account-value
sort on the leaderboard. Expect one WebSocket reconnect on the
market-maker gateway during the maintenance window.

**Changed**

* **Sending `cursor` to a non-paginated route now returns `400`.** These
  routes page by time window, never by cursor, and never returned one.
  Until now a `cursor` key on them was silently ignored and you got the
  first page. After this release any value — including an empty
  `cursor=` — is refused with `400` and the error `invalid cursor`:
  `GET /v1/account/deposits`, `/v1/account/equity`, `/v1/account/funding`,
  `/v1/account/internal-transfers`, `/v1/account/pnl`,
  `/v1/account/referral-history`, `/v1/account/rewards`,
  `/v1/account/withdrawals`, `GET /v1/info/equity`, `/v1/info/funding`,
  `/v1/info/klines`, `/v1/info/mark-history`, `/v1/info/pnl`,
  `/v1/info/trades`. If your client has a generic pagination helper that
  forwards `cursor` to every history call, stop it from doing so on these
  routes. Routes that do paginate by cursor (fills, orders, trades
  history, referral children, favorites) are unchanged.
* **Leaderboard `account_value` is now a cached census value.**
  `GET /v1/info/leaderboard` entries and address lookups already carried
  an `account_value` string. It is now produced by a background census
  that advances by at most 8,192 accounts per 60-second tick, so on a
  large population a full pass spans several ticks and the value can be a
  few minutes old. When an account's unrealized PnL cannot be computed (a
  stale mark on one of its instruments), the field shows collateral value
  only, the same way `/v1/info/portfolio` does. Treat it as indicative
  equity. Use the response timestamp to judge freshness. PnL and notional
  rankings, and every request parameter, are unchanged.
* **Open-interest reward floor.** OI rewards for reward dates from
  2026-09-07 require a combined entity daily-average gross open interest
  of at least \$5M (previously \$1M). Earlier dates keep the \$1M floor if
  recomputed. Reward periods run 12:00 UTC to 12:00 UTC and are labeled
  by their end date, so the first period under the new floor ended
  2026-09-07 12:00 UTC. The 6% APR, the daily averaging, the uncapped
  reward math, and the `/v1/account/rewards` response shape are
  unchanged. The release aligns the API documentation with terms that
  have applied since that period.

**Fixed**

* **Index price for Hyperliquid-sourced instruments.** On instruments
  whose index is sourced from Hyperliquid, the index lane was consuming
  the venue's `markPx` (a perpetual mark, carrying that venue's basis)
  instead of its `oraclePx`. It now consumes `oraclePx`, and the mark
  lane continues to consume `markPx`. The effect is largest where the two
  diverge most — WTI, Brent, and other commodity and equity markets,
  where the basis ran roughly 60–70 bps. Expect the published index on
  those instruments to step to the corrected value on deploy; mark,
  funding, and margin all follow the index, so a small one-time move in
  funding rate on those markets is normal. No request or response shape
  changes.
* **Lower, steadier order and cancel round-trip times.** Since the
  September 8 release, market-data publication sat on the same path that
  sequences orders and stopped coalescing requests, which several desks
  measured as higher REST latency. This release moves publication off
  that path and batches requests that arrive together. Order-path latency
  should come back down and stay flatter under load.
* **Fewer `503`s on exact order lookups.** An exact
  `GET /v1/account/orders` lookup by `order_id` or `client_order_id` that
  the gateway does not hold in memory now opens with a single combined
  existence probe instead of a full aggregate on every miss. If your
  client polls for client order ids in bursts, particularly ids the
  exchange never received, you should see far fewer `503` responses on
  this route under load. Results are unchanged.
* **Fewer read errors right after a deployment.** Leaderboard data is
  now rebuilt with a bounded, shared query, which removes the source of
  the brief 5xx burst on public read routes that followed the last
  deployment.

**Added**

* **50-level order book over WebSocket.** Subscribe to `book::{iid}::50`
  for the top 50 levels per side, pushed every 100 ms like the existing
  channel. `book::{iid}` and `book::{iid}::20` are the same subscription
  and keep delivering the top 20 exactly as today; frames for both carry
  the canonical `ch: "book::{iid}"`, while frames for the 50-level form
  carry `ch: "book::{iid}::50"`. Frame shape is otherwise identical, and
  each push is a full snapshot, not a delta. Any other suffix —
  including `::100`, `::0`, and `::050` — is refused at subscribe time
  with `invalid channel`. You can subscribe to both depths on one
  connection; each counts as one subscription toward the per-connection
  limit.
* **Web app favorites.** The web app's favorites feature turns on with
  this deploy; it uses the Polymarket web session cookie and does not
  affect trading integrations.
* **Leaderboard sorted by current account value.**
  `GET /v1/info/leaderboard?sort_by=account_value` ranks the accounts
  active in the requested window by their current equity. An account
  whose equity cannot be computed has no rank under this sort (a
  collateral-only display value does not establish one), and `total`
  counts only accounts with a published value. Right after a deployment
  each API server answers this sort with `503` for a few minutes while
  its first census completes — back off and retry; PnL and notional
  sorts stay available throughout.

**Clarified**

* **`order_not_found` is returned only when canceling a TP/SL trigger**
  by an id that does not exist or is not yours. Canceling a regular
  order never returns it: an unknown or foreign order id gives
  `order_not_in_orderbook`, and a client order id that does not resolve
  gives `order_unknown`.
* **Withdrawing an asset that does not count as collateral.** Such a
  balance sits outside every margin calculation, so withdrawing it
  cannot itself trigger a margin call. If your account carries a
  cross-basis shortfall, the balance is treated as recoverable value
  backing that shortfall. For a priced asset, the shortfall is
  subtracted from the combined value of every priced non-collateral
  asset you hold — not just the one you are withdrawing — and what
  remains is converted back at price and capped at your balance, so the
  amount released can be less than you expect if other non-collateral
  holdings draw on the same shortfall. For an unpriced asset there is
  nothing to value a partial hold-back at, so the request is refused
  with `insufficient_balance` until the shortfall clears. The same
  all-or-nothing refusal applies when the shortfall itself cannot be
  established, for example when a cross position has no mark price.
  This rule is not new; it is now written down on the margin docs page.

**Not Yet Active**

* **Isolated-margin backing accounting** (backstop liquidity providers
  and the insurance fund). Once activated, an isolated-position backstop
  transfers the position's live backing amount rather than its legacy
  margin amount to the recipient. No request or response shape changes;
  recipients keep using the amount actually transferred. We will
  announce the activation date.
* **Account groups for self-trade prevention**, the stricter
  liquidation-stop check, and per-account rate-limit tiers remain
  inactive. Nothing changes unless you ask to be grouped, and we will
  notify you before enabling tiers.

**Rollout**

The market-maker gateway is briefly unavailable while its pods roll, so
WebSocket connections to the MM gateway drop once and need to reconnect.
The exchange is in cancel-only mode for the duration: cancels,
liquidations, deposits, withdrawals, and funding keep working; new
orders are rejected until the window closes. This release also moves the
matching engine's sequencer to the new build, so plan for one reconnect
and a short cancel-only period.

**Sep 8, 2026**

Cancel-by-coid grace window, stricter filter validation, and faster rejected-order lookups

Cancel-by-coid grace window, stricter filter validation, and faster
rejected-order lookups. No breaking changes.

**Added**

* **`GET /v1/info/exchange-stats`.** Public, unauthenticated exchange-wide
  statistics for all pUSD-quoted perpetuals: matched USD volume, gross maker
  plus taker fees, and one-sided open interest with its sample timestamp.
  Half-open window `[start_timestamp, end_timestamp)` up to 31 days. Request
  weight 10, or 1 when served from cache; responses cached 5 minutes.
* **`instrument_id` filter on `GET /v1/account/fills`.** Optional, returns
  only that instrument's fills. Window, cursor, and sort are unchanged.

**Changed**

* **Cancel-by-coid waits for a racing create.** A cancel by client order id
  arriving just before its create is sequenced previously returned
  `order_unknown` and needed a retry. The gateway now waits up to 50ms of
  sequencer time for the create to appear and applies the cancel if it does
  — which covers the full range of races we see in production, where the
  create lands 10–49ms behind the cancel. You'll see fewer `order_unknown`
  rejects, and you can drop client-side retry loops for the sub-50ms case. A
  `coid` that never becomes an order still returns `order_unknown`, but up
  to 50ms later than before, so reject latency on those rises accordingly.
  In a batch mixing a racing `coid` with a never-created one, the racing
  cancel is forwarded when the batch resolves rather than the instant its
  create lands. Single-`coid` cancels, cancels of already-live orders, and
  `order_already_terminal` are unaffected.
* **`GET /v1/account/internal-transfers` accepts a direction filter.**
  `direction=in` or `direction=out`, relative to your account. Any other
  value now returns `400` with `unknown variant …, expected in or out`;
  previously an unrecognized value was silently ignored. Omitting the
  parameter changes nothing.
* **Unsupported WebSocket kline intervals are refused at subscribe.**
  `klines::<iid>::<interval>` accepts `1s` `1m` `5m` `15m` `30m` `1h` `4h`
  `6h` `12h` `1d` `1w`, unchanged. Any other interval now returns
  `{"status":"err","error":"invalid channel: …"}` instead of acknowledging
  the subscription and then never delivering data. If your client treats a
  non-ok ack as fatal, subscribe only with supported intervals.

**Fixed**

* **Exact order lookups for rejected orders no longer shed under load.**
  Looking up a single order by `order_id` or `client_order_id` where risk
  rejected it before it reached the book is now answered from the gateway's
  recent-outcome memory rather than a database read. Same empty page,
  faster, and no longer returns `503` when the gateway is busy. If you poll
  for a rejected order's status, this removes those `503`s. One caveat, and
  it only applies if you reuse client order ids. Where a `client_order_id`
  was booked earlier, then reused and rejected, a lookup by that id can
  return `[]` for up to 120 seconds after the rejection even though the
  earlier order still exists in history. Don't treat an empty page inside
  that window as proof the earlier booking never happened — look it up by
  `order_id` instead. Clients that never reuse client order ids are
  unaffected.

**Not Yet Active**

* **Favorites API.** `/v1/account/favorites` is a Polymarket web-session
  endpoint for the web app's market selector. Returns `404` until enabled
  and is not relevant to trading integrations.
* **Stricter liquidation-stop check.** Operator-enabled later. A liquidation
  stop will be refused unless the account is provably recovered at the
  moment it applies, so an account may stay in the liquidating state
  slightly longer during adverse price moves. No API shape change.
* **Per-account rate-limit tiers.** Still inactive, carried over from an
  earlier release. We'll notify you before enabling. Default tier when
  live: 5,000 actions/min with burst 250, 1,000 WS messages/min, 1,000 open
  orders, 30 connects/min.

**Rollout**

The market-maker gateway rolls pod by pod during the window, so MM
WebSocket connections will drop once and need to reconnect. The exchange is
in cancel-only mode for the duration: cancels, liquidations, deposits,
withdrawals, and funding continue; new orders are rejected until the window
closes.

**Sep 4, 2026**

OI reward eligibility threshold increased to \$5M

The OI reward eligibility threshold is now $5M of combined daily average gross
    OI per rewards entity, up from $1M. Accounts without an entity mapping qualify
independently. The 6% APR rate and calculation on the account's full daily
average gross OI across all instruments are unchanged.

**Sep 2, 2026**

Exchange statistics endpoint added

Added `GET /v1/info/exchange-stats`, a public endpoint returning aggregate
statistics for all pUSD-quoted perpetual markets over a required
`[start_timestamp, end_timestamp)` window of up to 31 days: matched USD
volume, gross positive maker and taker trading fees (rebates, incentives,
and referral payments excluded), and one-sided open interest in USD notional
with its sample time, taken from the latest complete sample before the
window end — both null when no complete sample is available. Responses are
cached for five minutes; a request costs weight 10, and a request served
from cache costs 1.

**Sep 1, 2026**

Concurrent WebSocket posts and HTTP overload shedding

Concurrent WebSocket posts and HTTP overload shedding. No breaking changes.

**Added**

* **Concurrent WebSocket posts (MM gateway).** Up to 16 signed writes per
  connection now run concurrently. Posts touching the same order, by client
  order id or engine order id, still reach the engine in send order.
  `cancelAll` and leverage or margin updates act as barriers. Responses can
  arrive out of order across different orders: send a unique `id` and match
  on it. Ordering is per connection.
* **HTTP overload shedding.** Requests over the server or per-IP concurrency
  limit are shed before execution: `503`,
  `{"status":"err","error":"service_unavailable"}`, with `Retry-After`.
  Batch routes return a one-element array. The request did not execute, so
  retry with backoff. The per-IP cap is 128 in-flight on the MM API against
  healthy usage under 10. Tell us if you NAT many bots through one IP.

**Changed**

* **Cancel by client order id.** A recently completed order now returns
  `order_already_terminal`, not `order_unknown`. A cancel racing its own
  order's creation waits briefly (\~1ms, capped at 150ms) and succeeds
  instead of answering `order_unknown`. TP/SL `coid`s are unchanged.
* **Funding.** Funding for a just-closed window now settles even if you
  flattened or flipped just before settlement was sequenced; previously the
  window could be skipped for the whole instrument. Drop any assumption that
  a closed position means no funding.
* **Reduce-only market closes.** These now fill only within the price band
  around the last mark. Expect partial fills or `ioc_no_fill` on a
  dislocated book. Quotes resting far outside the band will no longer be
  hit by them.
* **Balances.** REST `value` is now the USD equivalent at the asset's index
  price, not the raw amount: same for pUSD, different for other collateral.
  `balance` uses asset-native decimals, matching WebSocket.
* **`429` shape on modify routes.** `PATCH /v1/trade/orders` and
  `PATCH /v1/trade/orders-coid` return the one-element array instead of a
  bare object.
* **Idle connections.** HTTP connections with no complete header for 120s
  are closed. A per-pod ceiling resets excess connections rather than
  serving a `503`.

**Fixed**

* **Deposits.** Many-per-transaction deposits credit reliably; a batching
  edge case could previously delay or drop credits.
* **Price continuity.** The index survives a full outage of one oracle
  source, with marks, margin, and funding still updating. Don't assume
  prices freeze when a provider goes down.
* **Referrals.** Concurrent binds for one account resolve to one winner; the
  loser gets HTTP `400`, `account already has a referrer`.

**Not Yet Active**

* **Pending-modify expiry.** A modify left risk-undecided for 200ms will be
  swept: the order stays open and you retry with a new `modify_id`. We'll
  notify before enabling.
* **Per-account rate-limit tiers.** Still inactive. We'll notify before
  activation.

**Rollout**

The public gateway fleet moves to dedicated hardware during this window.
WebSocket connections drop once; standard reconnects cover it.

**Aug 24, 2026**

Response timestamps, rejection references, and liquidation metadata

Rejections and acknowledgements now carry inspectable timing, every WebSocket
push is stamped with engine event time, and backstop liquidation fills include
optional metadata.

**Added**

* **Timestamps on rejections.** All rejection responses — orders, cancels,
  modifies, withdrawals, and transfers — now include `ts` (engine decision
  time), `arts` (gateway arrival time), and `ref` (a support reference for
  locating the server-side trace). All three are nullable, except on cancel
  rejections where `ts` is always present.
* **Timestamps on acknowledgements.** Accepted create-order items now include
  `ts` and `arts`. Accepted cancels, which already carried `ts`, now also
  include `arts`.
* **Event time on every WebSocket push.** Push envelopes across all channels
  now carry `ets`, the engine event time for the update. This field is always
  present and is the one to use for event-time analysis.
* **Liquidation metadata on fills.** Fills and trades from backstop
  liquidations carry an optional `liquidation_details` object with `mark`,
  `method`, and `liquidated_user`. Available on private WebSocket fills,
  `GET /v1/account/fills`, position fills, and public trades. The anonymous
  trade tape omits `liquidated_user`. Ordinary order-book liquidations do not
  carry this object yet — only backstop liquidations, where `method` is
  `backstop`. The object is absent where metadata was not captured.

**Changed**

* **WebSocket envelope `ts` is now server send time.** Push frames previously
  stamped the envelope `ts` with internal stream time, which could lag actual
  send time on quiet channels or during catch-up. Engine event time is now
  carried separately in `ets`. Per-item `ts` inside `data` is unchanged. If
  you measure latency as `local_receive − envelope_ts`, your values will
  decrease after this release. Earlier measurements included server-internal
  lag and are not comparable — re-baseline at the deploy timestamp.
* **Backstop liquidations publish fills to both parties.** The liquidated
  account and each absorbing account now receive private WebSocket fills for
  backstop liquidations. The liquidated account previously received no fills
  event. These fills are system-generated, with order id `0` and no client
  order id, matching the shape of ADL fills.
* **`order_already_terminal` replaces `order_not_in_orderbook` for terminal
  orders.** Canceling an order that has already filled or canceled now
  returns `order_already_terminal` to the order's owner. Unknown and foreign
  order ids continue to return `order_not_in_orderbook`. Treat unrecognized
  error codes as non-retryable.

**Fixed**

* **`FillsUpdate.data` and `TradesUpdate.data` are documented as arrays.**
  The specification previously described objects. The wire format is
  unchanged; regenerate your client/bindings if you generated the object form.

| Field | Meaning |
| - | - |
| Request `ts` | Client send time |
| `arts` | Gateway arrival time |
| Item `ts` | Engine decision time |
| Envelope `ts` | Server send time |
| `ets` | Engine event time |

**Aug 16, 2026**

Current position fills endpoint added

Added `GET /v1/info/position-fills`, a public endpoint returning every fill
in a registered account's current open position cycle for one instrument. A
cycle begins when the position opens from flat or flips direction, and a
multi-leg flip stays in one cycle. Pages of up to 100 fills are linked by an
opaque `cursor` returned alongside the data; the cursor is validated against
the live position on every page and returns `400` if the position changed
mid-pagination. `GET /v1/account/fills` has returned the same opaque
`cursor` field since Jul 24 — passing the last fill's trade ID as `cursor`
still works there.

**Aug 14, 2026**

Equity and PnL history honour the requested interval

`GET /v1/account/equity` and `GET /v1/account/pnl` now bucket the returned
series by the `interval` query parameter. Previously the parameter was
validated but ignored: every accepted value returned the same
fixed-granularity series (per minute for equity, per hour for PnL), and long
windows were truncated at 1000 rows instead of aggregated. Each equity point
is now the last sample in its interval bucket; each PnL point is the PnL
realized inside its bucket — not a running total — and empty buckets are
omitted. Buckets are aligned to the Unix epoch, points keep real sample
timestamps, and the 1000-entry cap now counts buckets, so a coarser interval
covers a longer window before `more` is set. Responses for the finest
intervals (`1m` equity, `1h` PnL) are unchanged.

**Aug 13, 2026**

Deposit and withdrawal history amounts are always decimal token units

`GET /v1/account/deposits` and `GET /v1/account/withdrawals` now serialize
every `amount` (and withdrawal `fee`) in decimal token units, e.g. `"10"` for
10 pUSD. Previously, deposit rows in `pending` or `removed` status reported
raw on-chain base units (`"10000000"` for the same 10 pUSD), and pending
withdrawal rows could serve base-unit amounts and fees as well, so rows for
the same transfer disagreed on units across statuses. Clients that divided
pending amounts by `10^decimals` to compensate must drop that conversion.
Confirmed rows and the WebSocket `deposits` and `withdrawals` channels are
unchanged — they were already decimal. Signed operation inputs (`POST
    /v1/account/withdraw`) still take base-unit amounts matching the EIP-712
signature.

**Aug 11, 2026**

Fill history flags maker fills executed under liquidation

`GET /v1/account/fills` and account trade history previously reported
`liquidation: false` on every maker fill, even when the maker's own account
was under liquidation on the instrument — while the WebSocket `fills` channel
already reported `liq: true` for the same fill. The two surfaces now agree:
any maker or taker fill on an instrument in the account's active liquidation
scope reports `liquidation: true`. Rows written before the change are
unaffected. Such maker legs are also excluded from leaderboard win counts.

**Aug 10, 2026**

Fills gain an adl flag; liq no longer set on ADL counterparty legs

Fill entries now carry a required boolean `adl` field on both the WebSocket
`fills` channel and `GET /v1/account/fills`, set on both legs of an
auto-deleveraging match. Behavior change: the counterparty leg of an ADL match
previously reported `liq: true` on the WebSocket `fills` channel; it now
reports `liq: false`. `liq` marks only the leg whose own position is being
liquidated. Clients that detect forced closes via `liq` alone will no longer
see ADL counterparty fills — check `adl` as well.

**Aug 8, 2026**

Position deleveraged notification added

Added the `position_deleveraged` notification, sent to the
counterparty of an auto-deleveraging match when its profitable position is
closed or reduced to settle a liquidation on the other side. Delivered on the
WebSocket `notifications` channel and in the notifications history.

**Aug 7, 2026**

Exchange info reports engine version and cancel-only state

`GET /v1/info/exchange` now includes `engine_version`, the engine release
version of the build serving the response. The response also documents
`cancel_only`, which reports whether the exchange is in cancel-only
(maintenance) mode; the flag has been returned since maintenance mode shipped
on Jul 15.

**Aug 6, 2026**

Portfolio margin summary includes available order margin

The portfolio response and `portfolio` WebSocket channel now
include `margin.available_order_margin`: the collateral available
for additional order initial margin after existing exposure, open orders,
orders and isolated-margin additions awaiting risk processing, and pending
withdrawals or transfers.

**Jul 6, 2026**

Cancel all orders added

Added `DELETE /v1/trade/orders/all` to cancel all open orders in
one request, optionally scoped to a single instrument. Available in the SDKs
as `cancelAllOrders` (TypeScript) and&#x20;
`cancel_all_orders` (Python).

**Jun 11, 2026**

Cancel responses include order IDs

Cancel responses now include `oid` and `coid` fields.

**Jun 10, 2026**

Taker delay added for immediately matching orders

Added a 20ms taker delay for orders that immediately match on entry.

**Jun 9, 2026**

Reduce-only orders added

Added the reduce-only field to order submission and order updates.

**Jun 8, 2026**

Auto-cancel and rate-limit updates

<ul>
  <li>
    Added `PATCH /v1/trade/auto-cancel` to arm or clear a dead
    man's switch that cancels all open orders at a specified time.
  </li>

  <li>
    Added `GET /v1/account/auto-cancel` to check the current
    auto-cancel status, trigger count, and daily reset time.
  </li>

  <li>Auto-cancel is limited to 1000 triggers per UTC day per account.</li>

  <li>
    Added `updateLeverage` and `autoCancel` WebSocket
    post messages.
  </li>

  <li>
    `portfolio` and `balances` WebSocket channels no
    longer push updates on every order or fill, only periodically.
  </li>

  <li>
    Rate limit error messages now distinguish between&#x20;
    `ip_rate_limited`, `action_rate_limited`, and&#x20;
    `message_rate_limited`.
  </li>
</ul>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.