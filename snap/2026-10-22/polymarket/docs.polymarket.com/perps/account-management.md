> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Account Management

> Monitor balances, positions, and account history

Use account reads to turn Perps activity into a reliable local view of account
health, trading history, and performance.

> [!NOTE]
>
> Owner account reads use an [authenticated
> session](/perps/authenticated-sessions.md). Position snapshots also support
> public reads.

## Review Account Health

Start with the account's current collateral and exposure when showing portfolio
health or checking whether a trade fits the account's risk state.

### Balances

Use balances to show collateral by asset and account value.

**TypeScript**

```ts theme={null}
const balances = await session.fetchBalances();
```

Use this shape to render collateral balances and account value by asset.

> **Output: PerpsBalance\[]**
>
> ```ts PerpsBalance Type theme={null}
> type PerpsBalance = {
>   asset: string;
>   balance: string;
>   value: string;
> };
>
> type Output = PerpsBalance[];
> ```
>
> ```json PerpsBalance Example theme={null}
> [
>   {
>     "asset": "pUSD",
>     "balance": "1000",
>     "value": "1000"
>   }
> ]
> ```

**Python**

```python theme={null}
balances = await session.fetch_balances()
```

Use this shape to render collateral balances and account value by asset.

> **Output: tuple\[PerpsBalance, ...]**
>
> ```json theme={null}
> [
>   {
>     "asset": "pUSD",
>     "balance": "1000",
>     "value": "1000"
>   }
> ]
> ```

**API**

```bash theme={null}
curl "https://api.perpetuals.polymarket.com/v1/account/balances" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>"
```

Use this shape to render collateral balances and account value by asset.

> **Output: Balances**
>
> ```json theme={null}
> [
>   {
>     "asset": "pUSD",
>     "balance": "1000",
>     "value": "1000"
>   }
> ]
> ```

### Portfolio

Use the portfolio to show open positions, margin usage, withdrawable collateral,
and liquidation state.

**TypeScript**

```ts theme={null}
const portfolio = await session.fetchPortfolio();
```

Use this shape to render positions, margin usage, and liquidation state.

> **Output: PerpsPortfolio**
>
> ```ts PerpsPortfolio Type theme={null}
> type PerpsPortfolio = {
>   positions: Array<{
>     instrumentId: number;
>     symbol: string;
>     size: string;
>     entryPrice: string;
>     leverage: number;
>     cross: boolean;
>     initialMargin: string;
>     maintenanceMargin: string;
>     positionValue: string;
>     liquidationPrice: string;
>     unrealizedPnl: string;
>     returnOnEquity: string;
>     cumulativeFunding: string;
>     adlIndex: 0 | 1 | 2 | 3;
>   }>;
>   margin: {
>     totalAccountValue: string;
>     availableOrderMargin: string;
>     totalInitialMargin: string;
>     totalMaintenanceMargin: string;
>     totalPositionValue: string;
>   };
>   withdrawable: string;
>   inLiquidation: boolean;
>   feeTier: number;
>   timestamp: number;
> };
>
> type Output = PerpsPortfolio;
> ```
>
> ```json PerpsPortfolio Example theme={null}
> {
>   "positions": [
>     {
>       "instrumentId": 1,
>       "symbol": "BTC-PERP",
>       "size": "0.01",
>       "entryPrice": "65000",
>       "leverage": 5,
>       "cross": false,
>       "initialMargin": "130",
>       "maintenanceMargin": "65",
>       "positionValue": "650",
>       "liquidationPrice": "52000",
>       "unrealizedPnl": "0",
>       "returnOnEquity": "0",
>       "cumulativeFunding": "0",
>       "adlIndex": 0
>     }
>   ],
>   "margin": {
>     "totalAccountValue": "1000",
>     "availableOrderMargin": "870",
>     "totalInitialMargin": "130",
>     "totalMaintenanceMargin": "65",
>     "totalPositionValue": "650"
>   },
>   "withdrawable": "870",
>   "inLiquidation": false,
>   "feeTier": 0,
>   "timestamp": 1767000000000
> }
> ```

`margin.availableOrderMargin` is the exact decimal amount available to margin
new orders after positions, open orders, and pending account operations.
`feeTier` is the zero-based index into the fee schedule's tiers.
`positions[].adlIndex` ranges from `0` to `3`. Higher values indicate higher
auto-deleveraging risk. The same fields appear in session `"portfolio"` events.

**Python**

```python theme={null}
portfolio = await session.fetch_portfolio()
```

Use this shape to render positions, margin usage, and liquidation state.

> **Output: PerpsPortfolio**
>
> ```json theme={null}
> {
>   "positions": [
>     {
>       "instrument_id": 1,
>       "symbol": "BTC-PERP",
>       "size": "0.01",
>       "entry_price": "65000",
>       "leverage": 5,
>       "cross": false,
>       "initial_margin": "130",
>       "maintenance_margin": "65",
>       "position_value": "650",
>       "liquidation_price": "52000",
>       "unrealized_pnl": "0",
>       "return_on_equity": "0",
>       "cumulative_funding": "0",
>       "adl_index": 0
>     }
>   ],
>   "margin": {
>     "total_account_value": "1000",
>     "available_order_margin": "870",
>     "total_initial_margin": "130",
>     "total_maintenance_margin": "65",
>     "total_position_value": "650"
>   },
>   "withdrawable": "870",
>   "in_liquidation": false,
>   "fee_tier": 0,
>   "timestamp": "2025-12-29T09:20:00Z"
> }
> ```

`margin.available_order_margin` is a `Decimal` amount available to margin new
orders after positions, open orders, and pending account operations.
`fee_tier` is the zero-based index into the fee schedule's tiers.
`positions[].adl_index` ranges from `0` to `3`. Higher values indicate higher
auto-deleveraging risk. The same fields appear in session `"portfolio"` events.
The example serializes `Decimal` values as strings and the UTC `datetime` as ISO 8601.

**API**

```bash theme={null}
curl "https://api.perpetuals.polymarket.com/v1/account/portfolio" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>"
```

Use this shape to render positions, margin usage, and liquidation state.

> **Output: Portfolio**
>
> ```json theme={null}
> {
>   "positions": [
>     {
>       "instrument_id": 1,
>       "symbol": "BTC-PERP",
>       "size": "0.01",
>       "entry_price": "65000",
>       "leverage": 5,
>       "cross": false,
>       "initial_margin": "130",
>       "maintenance_margin": "65",
>       "position_value": "650",
>       "liquidation_price": "52000",
>       "unrealized_pnl": "0",
>       "return_on_equity": "0",
>       "cumulative_funding": "0",
>       "adl_index": 0
>     }
>   ],
>   "margin": {
>     "total_account_value": "1000",
>     "available_order_margin": "870",
>     "total_initial_margin": "130",
>     "total_maintenance_margin": "65",
>     "total_position_value": "650"
>   },
>   "withdrawable": "870",
>   "in_liquidation": false,
>   "fee_tier": 0,
>   "timestamp": 1767000000000
> }
> ```

`positions[].adl_index` ranges from `0` to `3`. Higher values indicate higher
auto-deleveraging risk. These fields are also present in private `portfolio`
WebSocket snapshots.

`margin.available_order_margin` is the collateral available to margin new
orders, after existing positions, open orders, and any orders, isolated-margin
additions, withdrawals, or transfers still being processed.

`fee_tier` is the account's current fee tier: a zero-based index into the
`tiers` of the fee schedule from [Trading Fees](/perps/trading.md#trading-fees).
Accounts without a current tier assignment report `0`.

### Account Stats

Use account stats to review trailing 7-day trading activity such as volume and
maker share. Stats are cached by UTC day and may be stale by up to 24 hours. See
[Fee Metrics](/perps/learn-about-trading/fees.md#fee-metrics) for what each metric
means.

**TypeScript**

```ts theme={null}
const stats = await session.fetchStats();
```

Use this shape to render trailing 7-day volume and maker-share activity.

> **Output: PerpsAccountStats**
>
> ```ts PerpsAccountStats Type theme={null}
> type PerpsAccountStats = {
>   volume7d: string;
>   takerVolume7d: string;
>   makerVolume7d: string;
>   accountMakerShare7d: string;
>   entityMakerShare7d?: string;
>   entityId?: number;
>   entityName?: string;
> };
> ```
>
> ```json PerpsAccountStats Example theme={null}
> {
>   "volume7d": "5000000",
>   "takerVolume7d": "3500000",
>   "makerVolume7d": "1500000",
>   "accountMakerShare7d": "0.35",
>   "entityMakerShare7d": "0.40",
>   "entityId": 42,
>   "entityName": "desk"
> }
> ```

**Python**

```python theme={null}
stats = await session.fetch_stats()
```

Use this shape to render trailing 7-day volume and maker-share activity.

> **Output: PerpsAccountStats**
>
> ```json theme={null}
> {
>   "volume_7d": "5000000",
>   "taker_volume_7d": "3500000",
>   "maker_volume_7d": "1500000",
>   "account_maker_share_7d": "0.35",
>   "entity_maker_share_7d": "0.40",
>   "entity_id": 42,
>   "entity_name": "desk"
> }
> ```

**API**

```bash theme={null}
curl "https://api.perpetuals.polymarket.com/v1/account/stats" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>"
```

Use this shape to render trailing 7-day volume and maker-share activity.

> **Output: Account Stats**
>
> ```json theme={null}
> {
>   "volume_7d": "5000000",
>   "taker_volume_7d": "3500000",
>   "maker_volume_7d": "1500000",
>   "account_maker_share_7d": "0.35",
>   "entity_maker_share_7d": "0.40",
>   "entity_id": 42,
>   "entity_name": "desk"
> }
> ```

## Reconcile Orders and Fills

Use order state and fills to connect submitted orders with resting liquidity,
executions, fees, and exposure changes.

### Open Orders

Use open orders to show what is still resting on the book.

**TypeScript**

```ts theme={null}
const openOrders = await session.fetchOpenOrders({
  instrumentId: instrument.id,
});
```

Use this shape to render resting orders that can still fill or be canceled.

> **Output: PerpsOrder\[]**
>
> ```json theme={null}
> [
>   {
>     "id": 1234567890,
>     "instrumentId": 1,
>     "buy": true,
>     "price": "65000",
>     "quantity": "0.01",
>     "timeInForce": "gtc",
>     "postOnly": false,
>     "status": "open",
>     "restingQuantity": "0.01",
>     "filledQuantity": "0",
>     "createdTimestamp": 1767000010000,
>     "updatedTimestamp": 1767000010000
>   }
> ]
> ```

**Python**

```python theme={null}
open_orders = await session.fetch_open_orders(
    instrument_id=instrument.id,
)
```

Use this shape to render resting orders that can still fill or be canceled.

> **Output: tuple\[PerpsOrder, ...]**
>
> ```json theme={null}
> [
>   {
>     "id": 1234567890,
>     "instrument_id": 1,
>     "side": "BUY",
>     "price": "65000",
>     "quantity": "0.01",
>     "time_in_force": "gtc",
>     "post_only": false,
>     "status": "open",
>     "resting_quantity": "0.01",
>     "filled_quantity": "0",
>     "created_at": 1767000010000,
>     "updated_at": 1767000010000
>   }
> ]
> ```

**API**

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/open-orders" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "instrument_id=1"
```

Use this shape to render resting orders that can still fill or be canceled.

> **Output: Open Orders**
>
> ```json theme={null}
> [
>   {
>     "order_id": 1234567890,
>     "instrument_id": 1,
>     "buy": true,
>     "price": "65000",
>     "quantity": "0.01",
>     "tif": "gtc",
>     "post_only": false,
>     "ro": false,
>     "status": "open",
>     "resting_quantity": "0.01",
>     "filled_quantity": "0",
>     "created_timestamp": 1767000010000,
>     "updated_timestamp": 1767000010000
>   }
> ]
> ```

### Orders

Use orders to inspect the latest known state for submitted orders.

**TypeScript**

```ts theme={null}
const orders = await session.fetchOrders({
  instrumentId: instrument.id,
});
```

Use this shape to reconcile submitted orders with their latest known state.

> **Output: PerpsOrder\[]**
>
> ```json theme={null}
> [
>   {
>     "id": 1234567890,
>     "instrumentId": 1,
>     "buy": true,
>     "price": "65000",
>     "quantity": "0.01",
>     "timeInForce": "ioc",
>     "postOnly": false,
>     "status": "filled",
>     "restingQuantity": "0",
>     "filledQuantity": "0.01",
>     "createdTimestamp": 1767000010000,
>     "updatedTimestamp": 1767000010500
>   }
> ]
> ```

**Python**

```python theme={null}
orders = await session.fetch_orders(
    instrument_id=instrument.id,
)
```

Use this shape to reconcile submitted orders with their latest known state.

> **Output: tuple\[PerpsOrder, ...]**
>
> ```json theme={null}
> [
>   {
>     "id": 1234567890,
>     "instrument_id": 1,
>     "side": "BUY",
>     "price": "65000",
>     "quantity": "0.01",
>     "time_in_force": "ioc",
>     "post_only": false,
>     "status": "filled",
>     "resting_quantity": "0",
>     "filled_quantity": "0.01",
>     "created_at": 1767000010000,
>     "updated_at": 1767000010500
>   }
> ]
> ```

**API**

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/orders" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "instrument_id=1"
```

Use this shape to reconcile submitted orders with their latest known state.

> **Output: Orders**
>
> ```json theme={null}
> [
>   {
>     "order_id": 1234567890,
>     "instrument_id": 1,
>     "buy": true,
>     "price": "65000",
>     "quantity": "0.01",
>     "tif": "ioc",
>     "post_only": false,
>     "ro": false,
>     "status": "filled",
>     "resting_quantity": "0",
>     "filled_quantity": "0.01",
>     "created_timestamp": 1767000010000,
>     "updated_timestamp": 1767000010500
>   }
> ]
> ```

### Fills

Use fills to reconcile executions, fees, realized PnL, and exposure changes.

**TypeScript**

```ts theme={null}
const pages = session.listFills({
  instrumentId: 1,
  start: Date.now() - 24 * 60 * 60 * 1000,
  end: Date.now(),
});

for await (const page of pages) {
  // page.items: PerpsAccountFill[]
}
```

Each page returns execution records you can use to reconcile fees, PnL, and exposure.

> **Output: PerpsAccountFill\[]**
>
> ```json theme={null}
> [
>   {
>     "tradeId": 987654321,
>     "orderId": 1234567890,
>     "instrumentId": 1,
>     "side": "long",
>     "price": "65000",
>     "quantity": "0.01",
>     "taker": true,
>     "fee": "0.26",
>     "feeAsset": "pUSD",
>     "previousSize": "0",
>     "previousEntryPrice": "0",
>     "pnl": "0",
>     "liquidation": false,
>     "adl": false,
>     "settlement": false,
>     "timestamp": 1767000010500,
>     "hash": "0x1111111111111111111111111111111111111111111111111111111111111111"
>   }
> ]
> ```

`instrumentId` accepts an integer from `0` through `4294967295`. Omit it to read all
instruments. Keep the same filter while paging. `adl` identifies an
auto-deleveraging fill and is separate from the liquidation flag.

For a backstop liquidation, the optional details object has this shape:

```json PerpsLiquidationDetails Example theme={null}
{
  "mark": "65000.00",
  "method": "backstop"
}
```

When present, `liquidationDetails` contains `mark`, `method`, and an optional
`liquidatedUser` address. `mark` is the decimal string mark price captured when the
liquidation was issued. The fill's `price` remains its accounting execution
price. `method` is `"backstop"` or `"market"`. Only backstop fills currently
include these details. The same data is available in private fill updates.

`settlement: true` identifies a position close at the final instrument-settlement
price. The same flag is available on each fill in a session `"fill"` event
payload; it defaults to `false` on older responses.

**Python**

```python theme={null}
from datetime import datetime, timedelta, timezone

end = datetime.now(timezone.utc)
start = end - timedelta(days=1)

pages = session.list_fills(instrument_id=1, start=start, end=end)

async for page in pages:
    # page.items: tuple[PerpsFill, ...]
    pass
```

Each page returns execution records you can use to reconcile fees, PnL, and exposure.

> **Output: tuple\[PerpsFill, ...]**
>
> ```json theme={null}
> [
>   {
>     "trade_id": 987654321,
>     "order_id": 1234567890,
>     "instrument_id": 1,
>     "side": "long",
>     "price": "65000",
>     "quantity": "0.01",
>     "taker": true,
>     "fee": "0.26",
>     "fee_asset": "pUSD",
>     "previous_size": "0",
>     "previous_entry_price": "0",
>     "pnl": "0",
>     "liquidation": false,
>     "adl": false,
>     "settlement": false,
>     "timestamp": "2025-12-29T09:20:10.500000Z",
>     "hash": "0x1111111111111111111111111111111111111111111111111111111111111111"
>   }
> ]
> ```

`instrument_id` accepts an integer from `0` through `4294967295`. Omit it to read all
instruments. Keep the same filter while paging. `adl` identifies an
auto-deleveraging fill and is separate from the liquidation flag.

For a backstop liquidation, the optional details object has this shape:

```json PerpsLiquidationDetails Example theme={null}
{
  "mark": "65000.00",
  "method": "backstop"
}
```

When present, `liquidation_details` contains `mark`, `method`, and an optional
`liquidated_user` address. `mark` is the `Decimal` mark price captured when the
liquidation was issued. The fill's `price` remains its accounting execution
price. `method` is `"backstop"` or `"market"`. Only backstop fills currently
include these details. The same data is available in private fill updates.

`settlement=True` identifies a position close at the final instrument-settlement
price. The same flag is available on each `PerpsFill` in a session `"fill"` event
payload; it defaults to `False` on older responses. Fill timestamps are UTC
`datetime` values; the JSON above shows their serialization.

**API**

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/fills" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "start_timestamp=1766913600000" \
  --data-urlencode "end_timestamp=1767000000000" \
  --data-urlencode "instrument_id=1"
```

Use the response's `more` field to continue fetching older or newer records.
Pass `instrument_id` to narrow the pages to one instrument's fills.

Each page returns execution records you can use to reconcile fees, PnL, and exposure.

> **Output: Fills**
>
> ```json theme={null}
> {
>   "data": [
>     {
>       "trade_id": 987654321,
>       "order_id": 1234567890,
>       "instrument_id": 1,
>       "side": "long",
>       "price": "65000",
>       "quantity": "0.01",
>       "taker": true,
>       "fee": "0.26",
>       "fee_asset": "pUSD",
>       "previous_size": "0",
>       "previous_entry_price": "0",
>       "pnl": "0",
>       "liquidation": false,
>       "settlement": false,
>       "adl": false,
>       "timestamp": 1767000010500,
>       "hash": "0x1111111111111111111111111111111111111111111111111111111111111111"
>     }
>   ],
>   "more": false
> }
> ```

`instrument_id` accepts an integer from `0` through `4294967295`. Omit it to read all
instruments. Keep the same filter while paging. `adl` identifies an
auto-deleveraging fill and is separate from the liquidation flag.

For a backstop liquidation, the optional details object has this shape:

```json LiquidationDetails Example theme={null}
{
  "mark": "65000.00",
  "method": "backstop"
}
```

When present, `liquidation_details` contains `mark`, `method`, and an optional
`liquidated_user` address. `mark` is the decimal string mark price captured when the
liquidation was issued. The fill's `price` remains its accounting execution
price. `method` is `"backstop"` or `"market"`. Only backstop fills currently
include these details. The same data is available in private fill updates.

`settlement: true` identifies a position close at the final instrument-settlement
price. The same flag appears on each entry in `data` on the private `fills`
WebSocket channel.

Settlement fills carry no fees or liquidation penalties. Reconcile them as
position closures, separately from ordinary market executions and ADL.

## Inspect Position Snapshots

Inspect a position now or immediately after a selected fill. Each snapshot covers
one position cycle, from opening until the position becomes flat or changes side.
Select up to 20 active instruments and 4 historical fills in one request, with at
least one selection. Results keep the selection order within each group.

**TypeScript**

Use the existing authenticated `session` to read your own positions. The example
selects instrument `1`; replace it with the instrument you want to inspect.

```ts theme={null}
import { PerpsPositionSnapshotStatus } from "@polymarket/client";

const snapshots = await session.fetchPositionSnapshots({
  activeInstrumentIds: [1],
});

for (const item of snapshots.active) {
  if (item.status === PerpsPositionSnapshotStatus.Ok) {
    console.log(item.snapshot.pnl, item.snapshot.leverage);
  }
}
```

> **Output: PerpsPositionSnapshots**
>
> A successful active selection returns:
>
> ```json theme={null}
> {
>   "historyAsOfAt": 1767000010500,
>   "active": [
>     {
>       "instrumentId": 1,
>       "status": "ok",
>       "snapshot": {
>         "positionCycleId": "opaque:cycle",
>         "side": "long",
>         "startedAt": 1767000000000,
>         "asOfAt": 1767000010500,
>         "isClosed": false,
>         "sizeAfter": "1",
>         "entryPrice": "100",
>         "asOfPrice": "110",
>         "pnl": "10",
>         "pnlPercent": "50",
>         "leverage": 5,
>         "chart": {
>           "candles": [
>             {
>               "position": 0,
>               "open": 100,
>               "high": 110,
>               "low": 100,
>               "close": 110
>             }
>           ],
>           "markers": [
>             {
>               "position": 0,
>               "kind": "increase"
>             }
>           ]
>         }
>       }
>     }
>   ],
>   "history": []
> }
> ```
>
> For `Ok`, `snapshot` is a `PerpsPositionSnapshot`. Financial values such as `pnl`
> use `DecimalString`; timestamps are Unix milliseconds. `pnlPercent` and `leverage`
> can be `null`.

To inspect the fill shown in the Fills example, add `historyFills`. Keep the
original decimal trade ID as a string; do not round it through a JavaScript number.

```ts theme={null}
import type { PerpsPositionSnapshotFill } from "@polymarket/client";

const fill: PerpsPositionSnapshotFill = {
  instrumentId: 1,
  tradeId: "987654321",
  timestamp: 1767000010500,
};
const historical = await session.fetchPositionSnapshots({
  historyFills: [fill],
});
// historical.history preserves selection order and includes canonical string tradeId.
```

For a public read, call `client.fetchPerpsPositionSnapshots({ address,
    activeInstrumentIds: [1] })` with a `createPublicClient()` instance and the
registered account's address. This method does not add session credentials,
including when inherited by a secure client.

**Python**

Use the existing authenticated `session` to read your own positions. The example
selects instrument `1`; replace it with the instrument you want to inspect.

```python theme={null}
snapshots = await session.fetch_position_snapshots(active_instrument_ids=(1,))

for item in snapshots.active:
    if item.status == "ok":
        print(item.snapshot.pnl, item.snapshot.leverage)
```

> **Output: PerpsPositionSnapshots**
>
> A successful active selection, serialized with
> `snapshots.model_dump(mode="json")`, returns:
>
> ```json theme={null}
> {
>   "history_as_of_at": "2025-12-29T09:20:10.500000Z",
>   "active": [
>     {
>       "instrument_id": 1,
>       "trade_id": null,
>       "status": "ok",
>       "snapshot": {
>         "position_cycle_id": "opaque:cycle",
>         "side": "long",
>         "started_at": "2025-12-29T09:20:00Z",
>         "as_of_at": "2025-12-29T09:20:10.500000Z",
>         "is_closed": false,
>         "size_after": "1",
>         "entry_price": "100",
>         "as_of_price": "110",
>         "pnl": "10",
>         "pnl_percent": "50",
>         "leverage": 5,
>         "chart": {
>           "candles": [
>             {
>               "position": 0.0,
>               "open": 100.0,
>               "high": 110.0,
>               "low": 100.0,
>               "close": 110.0
>             }
>           ],
>           "markers": [
>             {
>               "position": 0.0,
>               "kind": "increase"
>             }
>           ]
>         }
>       }
>     }
>   ],
>   "history": []
> }
> ```
>
> For `status="ok"`, `snapshot` is a `PerpsPositionSnapshot`. Financial values such
> as `pnl` are `Decimal`; timestamps, including `history_as_of_at`, are UTC
> `datetime` values. `pnl_percent` and `leverage` can be `None`.

To inspect the fill shown in the Fills example, select its instrument, decimal
trade ID, and original timestamp. `timestamp` accepts an aware `datetime` with
whole milliseconds or an integer Unix millisecond timestamp.

```python theme={null}
from polymarket import PerpsPositionSnapshotFill

fill = PerpsPositionSnapshotFill(
    instrument_id=1,
    trade_id="987654321",
    timestamp=1767000010500,
)
historical = await session.fetch_position_snapshots(history_fills=(fill,))
# historical.history preserves selection order and includes canonical string trade_id.
```

For a public read, use
`await client.fetch_perps_position_snapshots(address=address, active_instrument_ids=(1,))`
with an `AsyncPublicClient` and the registered account's address. This method does
not add session credentials, including when inherited by an async secure client.

**API**

Send a `POST` to `/v1/info/position-snapshots`. Supply your registered account
address and owner credentials for private leverage and PnL percentage.

```bash theme={null}
curl "https://api.perpetuals.polymarket.com/v1/info/position-snapshots" \
  -H "Content-Type: application/json" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  -d '{"address":"<account_address>","active_instrument_ids":[1]}'
```

> **Output: Position Snapshots**
>
> A successful active selection returns:
>
> ```json theme={null}
> {
>   "history_as_of_at": 1767000010500,
>   "active": [
>     {
>       "instrument_id": 1,
>       "status": "ok",
>       "snapshot": {
>         "position_cycle_id": "opaque:cycle",
>         "side": "long",
>         "started_at": 1767000000000,
>         "as_of_at": 1767000010500,
>         "is_closed": false,
>         "size_after": "1",
>         "entry_price": "100",
>         "as_of_price": "110",
>         "pnl": "10",
>         "pnl_percent": "50",
>         "leverage": 5,
>         "chart": {
>           "candles": [
>             {
>               "position": 0,
>               "open": 100,
>               "high": 110,
>               "low": 100,
>               "close": 110
>             }
>           ],
>           "markers": [
>             {
>               "position": 0,
>               "kind": "increase"
>             }
>           ]
>         }
>       }
>     }
>   ],
>   "history": []
> }
> ```
>
> Only `status: "ok"` includes `snapshot`. Financial values such as `pnl` are decimal
> strings; timestamps are Unix milliseconds. `pnl_percent` and `leverage` can be `null`.

For historical selections, add `history_fills` containing `instrument_id`,
`trade_id` as a decimal string, and `timestamp` in Unix milliseconds. IDs can span
the full UInt64 range; returned IDs omit leading zeroes. Duplicate instruments or
fill identities and explicit `null` arrays return HTTP 400. Unknown accounts also
return HTTP 400. Each fill timestamp must be at most `18446744073709`.

Omit both credential headers for a public read. Invalid supplied credentials
return HTTP 401; credentials for another account do not reveal the selected
account's private values. HTTP 503 indicates admission or shared dependency
failure; retry with backoff.

### Interpret a Snapshot

While a cycle remains open, PnL is gross unrealized profit or loss on its remaining
size. After it closes, PnL is the gross realized total for that cycle. Both exclude
fees, funding, and previous cycles. A fill that closes or flips a position returns
the closed cycle; the new cycle appears in the active selection or its later fills.

Leverage and PnL percentage are visible only to the authenticated owner. Historical
leverage uses the value captured at the selected fill and can remain unavailable.
PnL percentage uses nominal entry margin, so it can differ from portfolio return
on equity. Chart candles and markers use numeric coordinates and approximate
prices; the selected minute's candle includes the whole minute's aggregate.

### Handle Unavailable Selections

Only successful selections contain a snapshot. The completed-history horizon
indicates how far the serving gateway has processed history; gateways can report
different horizons. Handle each selection independently instead of discarding the
whole batch when one selection is unavailable.

| Status | Next Action |
| - | - |
| `ok` | Use the snapshot. |
| `not_found` | No current position or owned fill was found within the reported horizon; check the selection and horizon. |
| `history_pending` | Retry with backoff while history catches up or historical reconstruction becomes available. |
| `history_limit` | The available reconstruction window does not include the cycle's opening fill. |
| `resource_limit` | Reconstruction exceeded a row, memory, or chart bound. |
| `temporarily_unavailable` | Retry with backoff after a deadline, pricing, or dependency failure. |
| `unavailable` | The cycle cannot be reconstructed safely, for example after a self-match or an unsupported position adjustment. |

Reconstruction limits and unsafe-accounting results are not signals to poll
indefinitely.

## Reconcile Funding and Transfers

Funding, deposits, and withdrawals explain collateral changes that did not come
from order fills. Use [Fund Your Account](/perps/fund-your-account.md) for deposit
and withdrawal submission workflows.

### Funding Payments

Use funding payments to explain periodic funding debits or credits for a market.

**TypeScript**

```ts theme={null}
const fundingPayments = session.listFundingPayments({
  instrumentId: instrument.id,
});

for await (const page of fundingPayments) {
  // page.items: PerpsAccountFundingPayment[]
}
```

Each page returns funding records you can use to explain non-trade PnL changes.
Each record has a unique `id` for storing or deduplicating funding payments.

> **Output: PerpsAccountFundingPayment\[]**
>
> ```json theme={null}
> [
>   {
>     "id": 3055723280187747,
>     "instrumentId": 1,
>     "size": "0.01",
>     "fundingRate": "0.0001",
>     "fundingAsset": "pUSD",
>     "funding": "0.05",
>     "timestamp": 1767000010000
>   }
> ]
> ```

**Python**

```python theme={null}
funding_payments = session.list_funding_payments(
    instrument_id=instrument.id,
)

async for page in funding_payments:
    # page.items: tuple[PerpsFundingPayment, ...]
    pass
```

Each page returns funding records you can use to explain non-trade PnL changes.
Each record has a unique `id` for storing or deduplicating funding payments.

> **Output: tuple\[PerpsFundingPayment, ...]**
>
> ```json theme={null}
> [
>   {
>     "id": 3055723280187747,
>     "instrument_id": 1,
>     "size": "0.01",
>     "funding_rate": "0.0001",
>     "funding_asset": "pUSD",
>     "funding": "0.05",
>     "timestamp": 1767000010000
>   }
> ]
> ```

**API**

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/funding" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "instrument_id=1" \
  --data-urlencode "start_timestamp=1766913600000" \
  --data-urlencode "end_timestamp=1767000000000"
```

Use the response's `more` field to continue fetching older or newer records.

Each page returns funding records you can use to explain non-trade PnL changes.
Each record has a unique `id` for storing or deduplicating funding payments.

> **Output: Funding Payments**
>
> ```json theme={null}
> {
>   "data": [
>     {
>       "id": 3055723280187747,
>       "instrument_id": 1,
>       "size": "0.01",
>       "funding_rate": "0.0001",
>       "funding_asset": "pUSD",
>       "funding": "0.05",
>       "timestamp": 1767000010000
>     }
>   ],
>   "more": false
> }
> ```

### Deposits

Use deposits to reconcile collateral added to the Perps account.

**TypeScript**

```ts theme={null}
const deposits = session.listDeposits();

for await (const page of deposits) {
  // page.items: PerpsDeposit[]
}
```

Each page returns deposits you can match against collateral arriving in the account.

> **Output: PerpsDeposit\[]**
>
> ```json theme={null}
> [
>   {
>     "hash": "0x2222222222222222222222222222222222222222222222222222222222222222",
>     "asset": "pUSD",
>     "amount": "100",
>     "status": "confirmed",
>     "from": "0x1234567890abcdef1234567890abcdef12345678",
>     "to": "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
>     "confirmations": 12,
>     "requiredConfirmations": 12,
>     "createdTimestamp": 1767000010000,
>     "confirmedTimestamp": 1767000030000
>   }
> ]
> ```

**Python**

```python theme={null}
deposits = session.list_deposits()

async for page in deposits:
    # page.items: tuple[PerpsDeposit, ...]
    pass
```

Each page returns deposits you can match against collateral arriving in the account.

> **Output: tuple\[PerpsDeposit, ...]**
>
> ```json theme={null}
> [
>   {
>     "hash": "0x2222222222222222222222222222222222222222222222222222222222222222",
>     "asset": "pUSD",
>     "amount": "100",
>     "status": "confirmed",
>     "from_address": "0x1234567890abcdef1234567890abcdef12345678",
>     "to": "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
>     "confirmations": 12,
>     "required_confirmations": 12,
>     "created_at": 1767000010000,
>     "confirmed_at": 1767000030000
>   }
> ]
> ```

**API**

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/deposits" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "start_timestamp=1766913600000" \
  --data-urlencode "end_timestamp=1767000000000"
```

Use the response's `more` field to continue fetching older or newer records.

Each page returns deposits you can match against collateral arriving in the account.

> **Output: Deposits**
>
> ```json theme={null}
> {
>   "data": [
>     {
>       "hash": "0x2222222222222222222222222222222222222222222222222222222222222222",
>       "asset": "pUSD",
>       "amount": "100",
>       "status": "confirmed",
>       "from": "0x1234567890abcdef1234567890abcdef12345678",
>       "to": "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
>       "confirmations": 12,
>       "required_confirmations": 12,
>       "created_timestamp": 1767000010000,
>       "confirmed_timestamp": 1767000030000
>     }
>   ],
>   "more": false
> }
> ```

### Withdrawals

Use withdrawals to reconcile collateral leaving the Perps account.

**TypeScript**

```ts theme={null}
const withdrawals = session.listWithdrawals();

for await (const page of withdrawals) {
  // page.items: PerpsWithdrawal[]
}
```

Each page returns withdrawals you can match against collateral leaving the account.

> **Output: PerpsWithdrawal\[]**
>
> ```json theme={null}
> [
>   {
>     "withdrawalId": 1234567891,
>     "asset": "pUSD",
>     "amount": "50",
>     "fee": "1.5",
>     "status": "confirmed",
>     "to": "0x1234567890abcdef1234567890abcdef12345678",
>     "hash": "0x3333333333333333333333333333333333333333333333333333333333333333",
>     "confirmations": 12,
>     "requiredConfirmations": 12,
>     "createdTimestamp": 1767000010000,
>     "confirmedTimestamp": 1767000030000
>   }
> ]
> ```

Branch on `status` using the `PerpsKnownWithdrawalStatus` enum.

| `status` | Meaning |
| - | - |
| `PerpsKnownWithdrawalStatus.Pending` | The withdrawal was accepted and is awaiting on-chain confirmation. |
| `PerpsKnownWithdrawalStatus.Confirmed` | The on-chain transaction reached the required confirmations and the funds were sent to the destination. |
| `PerpsKnownWithdrawalStatus.Removed` | A chain reorganization removed the on-chain transaction after it was observed. |
| `PerpsKnownWithdrawalStatus.Failed` | The withdrawal could not be executed on-chain and was reverted; the amount was credited back to the account. |

The status set can grow between SDK releases. `status` is typed
`PerpsWithdrawalStatus`, so a status added after your release arrives as a
plain string instead of failing the response parse.

**Python**

```python theme={null}
withdrawals = session.list_withdrawals()

async for page in withdrawals:
    # page.items: tuple[PerpsWithdrawal, ...]
    pass
```

Each page returns withdrawals you can match against collateral leaving the account.

> **Output: tuple\[PerpsWithdrawal, ...]**
>
> ```json theme={null}
> [
>   {
>     "withdrawal_id": 1234567891,
>     "asset": "pUSD",
>     "amount": "50",
>     "fee": "1.5",
>     "status": "confirmed",
>     "to": "0x1234567890abcdef1234567890abcdef12345678",
>     "hash": "0x3333333333333333333333333333333333333333333333333333333333333333",
>     "confirmations": 12,
>     "required_confirmations": 12,
>     "created_at": 1767000010000,
>     "confirmed_at": 1767000030000
>   }
> ]
> ```

Branch on `status` using the `PerpsKnownWithdrawalStatus` literals.

| `status` | Meaning |
| - | - |
| `"pending"` | The withdrawal was accepted and is awaiting on-chain confirmation. |
| `"confirmed"` | The on-chain transaction reached the required confirmations and the funds were sent to the destination. |
| `"removed"` | A chain reorganization removed the on-chain transaction after it was observed. |
| `"failed"` | The withdrawal could not be executed on-chain and was reverted; the amount was credited back to the account. |

The status set can grow between SDK releases. `status` is typed
`PerpsWithdrawalStatus`, so a status added after your release arrives as a
plain string instead of failing validation.

**API**

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/withdrawals" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "start_timestamp=1766913600000" \
  --data-urlencode "end_timestamp=1767000000000"
```

Use the response's `more` field to continue fetching older or newer records.

Each page returns withdrawals you can match against collateral leaving the account.

> **Output: Withdrawals**
>
> ```json theme={null}
> {
>   "data": [
>     {
>       "withdrawal_id": 1234567891,
>       "asset": "pUSD",
>       "amount": "50",
>       "fee": "1.5",
>       "status": "confirmed",
>       "to": "0x1234567890abcdef1234567890abcdef12345678",
>       "hash": "0x3333333333333333333333333333333333333333333333333333333333333333",
>       "confirmations": 12,
>       "required_confirmations": 12,
>       "created_timestamp": 1767000010000,
>       "confirmed_timestamp": 1767000030000
>     }
>   ],
>   "more": false
> }
> ```

| `status` | Meaning |
| - | - |
| `pending` | The withdrawal was accepted and is awaiting on-chain confirmation. |
| `confirmed` | The on-chain transaction reached the required confirmations and the funds were sent to the destination. |
| `removed` | A chain reorganization removed the on-chain transaction after it was observed. |
| `failed` | The withdrawal could not be executed on-chain and was reverted; the amount was credited back to the account. |

## Track Equity and PnL

Use equity and PnL history to explain how the account's value changed over time.

### Equity

Use equity history to chart account value over time. Each point is the last
equity sample in its `interval` bucket, stamped with that sample's own
timestamp. A coarser interval lets each 1000-point page cover a longer window
before `more` is set.

**TypeScript**

```ts theme={null}
const equity = session.listEquityHistory({
  interval: "1h",
  start: Date.now() - 7 * 24 * 60 * 60 * 1000,
  end: Date.now(),
});

for await (const page of equity) {
  // page.items: PerpsEquityPoint[]
}
```

Each page returns points you can plot as account equity over time.

> **Output: PerpsEquityPoint\[]**
>
> ```json theme={null}
> [
>   {
>     "timestamp": 1767000000000,
>     "equity": "1000"
>   }
> ]
> ```

**Python**

```python theme={null}
from datetime import datetime, timedelta, timezone

end = datetime.now(timezone.utc)
start = end - timedelta(days=7)

equity = session.list_equity_history(
    interval="1h",
    start=start,
    end=end,
)

async for page in equity:
    # page.items: tuple[PerpsEquityPoint, ...]
    pass
```

Each page returns points you can plot as account equity over time.

> **Output: tuple\[PerpsEquityPoint, ...]**
>
> ```json theme={null}
> [
>   {
>     "timestamp": 1767000000000,
>     "equity": "1000"
>   }
> ]
> ```

**API**

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/equity" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "interval=1h" \
  --data-urlencode "start_timestamp=1766395200000" \
  --data-urlencode "end_timestamp=1767000000000"
```

Use the response's `more` field to continue fetching older or newer records.

Each page returns points you can plot as account equity over time.

> **Output: Equity**
>
> ```json theme={null}
> {
>   "data": [[1767000000000, "1000"]],
>   "more": false
> }
> ```

### PnL

Use PnL history to chart account profit and loss over the same interval. Each
point is the PnL realized inside its `interval` bucket — not a running total —
and buckets in which nothing was realized are omitted, so the series is
sparse.

**TypeScript**

```ts theme={null}
const pnl = session.listPnlHistory({
  interval: "1h",
  start: Date.now() - 7 * 24 * 60 * 60 * 1000,
  end: Date.now(),
});

for await (const page of pnl) {
  // page.items: PerpsPnlPoint[]
}
```

Each page returns points you can plot as account PnL over time.

> **Output: PerpsPnlPoint\[]**
>
> ```json theme={null}
> [
>   {
>     "timestamp": 1767000000000,
>     "pnl": "12.5"
>   }
> ]
> ```

**Python**

```python theme={null}
from datetime import datetime, timedelta, timezone

end = datetime.now(timezone.utc)
start = end - timedelta(days=7)

pnl = session.list_pnl_history(
    interval="1h",
    start=start,
    end=end,
)

async for page in pnl:
    # page.items: tuple[PerpsPnlPoint, ...]
    pass
```

Each page returns points you can plot as account PnL over time.

> **Output: tuple\[PerpsPnlPoint, ...]**
>
> ```json theme={null}
> [
>   {
>     "timestamp": 1767000000000,
>     "pnl": "12.5"
>   }
> ]
> ```

**API**

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/pnl" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "interval=1h" \
  --data-urlencode "start_timestamp=1766395200000" \
  --data-urlencode "end_timestamp=1767000000000"
```

Use the response's `more` field to continue fetching older or newer records.

Each page returns points you can plot as account PnL over time.

> **Output: PnL**
>
> ```json theme={null}
> {
>   "data": [[1767000000000, "12.5"]],
>   "more": false
> }
> ```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.