> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Realtime Updates

> Stream public Perps market data

Stream public market data as prices, books, trades, candles, tickers, and market
statistics change.

> [!NOTE]
>
> Private order, fill, and account-state reconciliation is covered in [Reconcile
> Trade State](/perps/trading.md#reconcile-trade-state).

Start with the stream that matches the view you are building. Subscribe to the
updates you need, read events while the view is active, then stop the stream when
the view no longer needs live data.

**TypeScript**

Subscribe from a `PublicClient` or `SecureClient` and iterate over the merged
event stream.

```ts theme={null}
import { createPublicClient } from "@polymarket/client";

const client = createPublicClient();

const stream = await client.subscribe([
  { topic: "perps.bbo", instrumentId: 1 },
  { topic: "perps.trades", instrumentId: 1 },
]);

for await (const event of stream) {
  switch (event.type) {
    case "bbo":
      // event: PerpsBboEvent
      break;
    case "trade":
      // event: PerpsTradeEvent
      break;
  }

  if (shouldClose) {
    await stream.close();
  }
}
```

The stream yields typed events for each subscription and can be closed when the
live view no longer needs updates.

**Python**

Subscribe from an `AsyncPublicClient` or `AsyncSecureClient` and iterate over
the merged event stream.

```python theme={null}
from polymarket import AsyncPublicClient
from polymarket.streams import PerpsBboSpec, PerpsTradesSpec

client = AsyncPublicClient()

stream = await client.subscribe(
    [
        PerpsBboSpec(instrument_id=1),
        PerpsTradesSpec(instrument_id=1),
    ]
)

async for event in stream:
    if event.type == "bbo":
        # event: PerpsBboEvent
        pass
    elif event.type == "trade":
        # event: PerpsTradeEvent
        pass

    if should_close:
        await stream.close()
```

The stream yields typed events for each subscription and can be closed when the
live view no longer needs updates.

**API**

Connect to the Perps WebSocket production URL.

```text theme={null}
wss://ws.perpetuals.polymarket.com/v1/ws
```

The example below opens a JavaScript WebSocket client, subscribes to public
market-data updates, then unsubscribes and closes the connection.

```ts theme={null}
const ws = new WebSocket("wss://ws.perpetuals.polymarket.com/v1/ws");
const channels = ["bbo::1", "trades::1", "klines::1::1m"];

ws.addEventListener("open", () => {
  ws.send(
    JSON.stringify({
      id: 1,
      req: "sub",
      chs: channels,
    }),
  );
});

ws.addEventListener("message", (event) => {
  const message = JSON.parse(event.data);
  // message: public market data frame
});

function unsubscribeAndClose() {
  ws.send(
    JSON.stringify({
      id: 2,
      req: "unsub",
      chs: channels,
    }),
  );
  ws.close();
}
```

Use `req: "sub"` to subscribe and `req: "unsub"` with the same `chs` values to
unsubscribe. Each request may include an `id` for request-response matching.

## Best Bid and Offer

Use best bid and offer updates for top-of-book quotes.

**TypeScript**

Subscribe to best bid and offer updates for one instrument.

```ts theme={null}
const bbo = await client.subscribe([{ topic: "perps.bbo", instrumentId: 1 }]);

for await (const event of bbo) {
  // event: PerpsBboEvent
}
```

After subscribing, the stream yields `PerpsBboEvent` objects like this.

```ts PerpsBboEvent Type theme={null}
import type { EpochMilliseconds } from "@polymarket/client";

type PerpsBboEvent = {
  topic: "perps.bbo";
  type: "bbo";
  channel: string;
  timestamp: number;
  eventTimestamp: EpochMilliseconds | null;
  sequence: number;
  payload: {
    instrumentId: number;
    bidPrice: string;
    bidQuantity: string;
    askPrice: string;
    askQuantity: string;
  };
};
```

```json PerpsBboEvent Example theme={null}
{
  "topic": "perps.bbo",
  "type": "bbo",
  "channel": "bbo::1",
  "timestamp": 1767225600000,
  "eventTimestamp": 1767225600000,
  "sequence": 1234567890,
  "payload": {
    "instrumentId": 1,
    "bidPrice": "99.50",
    "bidQuantity": "10.00",
    "askPrice": "100.50",
    "askQuantity": "10.00"
  }
}
```

**Python**

Subscribe to best bid and offer updates for one instrument.

```python theme={null}
from polymarket.streams import PerpsBboSpec

bbo = await client.subscribe(PerpsBboSpec(instrument_id=1))

async for event in bbo:
    # event: PerpsBboEvent
    pass
```

After subscribing, the stream yields `PerpsBboEvent` objects like this.

```json Example theme={null}
{
  "topic": "perps.bbo",
  "type": "bbo",
  "channel": "bbo::1",
  "timestamp": "2026-01-01T00:00:00Z",
  "event_timestamp": "2026-01-01T00:00:00Z",
  "sequence": 1234567890,
  "payload": {
    "instrument_id": 1,
    "bid_price": "99.50",
    "bid_quantity": "10.00",
    "ask_price": "100.50",
    "ask_quantity": "10.00"
  }
}
```

**API**

Subscribe to best bid and offer updates for one instrument.

```json Subscribe theme={null}
{
  "id": 1,
  "req": "sub",
  "chs": ["bbo::<instrument_id>"]
}
```

After subscribing, the stream emits BBO update frames like this.

```json theme={null}
{
  "ch": "bbo::1",
  "ts": 1767225600000,
  "ets": 1767225600000,
  "sq": 1234567890,
  "data": {
    "iid": 1,
    "bp": "99.50",
    "bq": "10.00",
    "ap": "100.50",
    "aq": "10.00"
  }
}
```

## Interpret Update Timing

Use update timing to understand how recent the exchange's reflected activity is.
Keep sequence-based reconciliation separate from timing measurements.

**TypeScript**

`timestamp` is the server send time in Unix milliseconds. `eventTimestamp` is
the newest event reflected in the update as `EpochMilliseconds | null`.
Check for `null` before measuring the time spent inside the exchange after that
event:

```ts theme={null}
for await (const event of stream) {
  if (event.eventTimestamp !== null) {
    const exchangeDelayMs = event.timestamp - event.eventTimestamp;
  }
}
```

`eventTimestamp: null` means the event horizon is unknown. Local reconnect or
sequence-gap `"resync"` events have no `eventTimestamp`. Do not substitute their
`timestamp` for an exchange event horizon. Server-originated session updates and
resyncs carry the server's horizon through the same field.

**Python**

`timestamp` is the server send time as a UTC `datetime`. `event_timestamp` is
the newest event reflected in the update as a UTC `datetime`, or `None` when
the server reports an unknown horizon. When present, subtract it from
`timestamp` to measure the time spent inside the exchange after that event.

Local reconnect or sequence-gap `"resync"` events also have
`event_timestamp=None`. Do not substitute their send or local timestamp for an
exchange event horizon. The same timing fields are available on private session
updates and server resyncs.

**API**

Every update envelope includes `ts` (server send time), `ets` (newest reflected
event), and `sq` (sequence). Both timestamps are Unix milliseconds. For a
nonzero `ets`, `ts - ets` measures the time spent inside the exchange after the
event. `ets: 0` means no event horizon is attested. Treat it as unknown.

The same envelope applies to private account updates. Request acknowledgements
are a separate response shape and do not acquire update timing merely because
they arrive on the same connection.

For example, subscribe to BBO updates on the open Perps WebSocket connection:

```json Subscribe theme={null}
{
  "id": 1,
  "req": "sub",
  "chs": ["bbo::1"]
}
```

An update can arrive with an unknown event horizon:

```json Update theme={null}
{
  "ch": "bbo::1",
  "ts": 1767225600000,
  "ets": 0,
  "sq": 1234567890,
  "data": {
    "iid": 1,
    "bp": "99.50",
    "bq": "10.00",
    "ap": "100.50",
    "aq": "10.00"
  }
}
```

Here `ets: 0` means unknown. Do not calculate an exchange delay from it.

## Order Book

Use order book updates for depth across bid and ask price levels.

**TypeScript**

Subscribe to order book updates for one instrument.

```ts theme={null}
const book = await client.subscribe([{ topic: "perps.book", instrumentId: 1 }]);

for await (const event of book) {
  // event: PerpsBookEvent
}
```

After subscribing, the stream yields `PerpsBookEvent` objects like this.

```ts PerpsBookEvent Type theme={null}
import type { EpochMilliseconds } from "@polymarket/client";

type PerpsBookEvent = {
  topic: "perps.book";
  type: "book";
  channel: string;
  timestamp: number;
  eventTimestamp: EpochMilliseconds | null;
  sequence: number;
  payload: {
    instrumentId: number;
    bids: Array<{ price: string; quantity: string }>;
    asks: Array<{ price: string; quantity: string }>;
  };
};
```

```json PerpsBookEvent Example theme={null}
{
  "topic": "perps.book",
  "type": "book",
  "channel": "book::1",
  "timestamp": 1767225600000,
  "eventTimestamp": 1767225600000,
  "sequence": 1234567890,
  "payload": {
    "instrumentId": 1,
    "bids": [{ "price": "100.00", "quantity": "10.00" }],
    "asks": [{ "price": "101.00", "quantity": "8.00" }]
  }
}
```

**Python**

Subscribe to order book updates for one instrument.

```python theme={null}
from polymarket.streams import PerpsBookSpec

book = await client.subscribe(PerpsBookSpec(instrument_id=1))

async for event in book:
    # event: PerpsBookEvent
    pass
```

After subscribing, the stream yields `PerpsBookEvent` objects like this.

```json Example theme={null}
{
  "topic": "perps.book",
  "type": "book",
  "channel": "book::1",
  "timestamp": "2026-01-01T00:00:00Z",
  "event_timestamp": "2026-01-01T00:00:00Z",
  "sequence": 1234567890,
  "payload": {
    "instrument_id": 1,
    "bids": [{ "price": "100.00", "quantity": "10.00" }],
    "asks": [{ "price": "101.00", "quantity": "8.00" }]
  }
}
```

**API**

Subscribe to order book updates for one instrument.

```json Subscribe theme={null}
{
  "id": 2,
  "req": "sub",
  "chs": ["book::<instrument_id>"]
}
```

After subscribing, the stream emits book update frames like this.

```json theme={null}
{
  "ch": "book::1",
  "ts": 1767225600000,
  "ets": 1767225600000,
  "sq": 1234567890,
  "data": {
    "b": [["100.00", "10.00"]],
    "a": [["101.00", "8.00"]]
  }
}
```

## Trades

Use trades to update recent-print lists, last-trade displays, or execution-based
analytics.

**TypeScript**

Subscribe to public trade updates for one instrument.

```ts theme={null}
const trades = await client.subscribe([
  { topic: "perps.trades", instrumentId: 1 },
]);

for await (const event of trades) {
  // event: PerpsTradeEvent
}
```

After subscribing, the stream yields `PerpsTradeEvent` objects like this.

```ts PerpsTradeEvent Type theme={null}
import type { EpochMilliseconds } from "@polymarket/client";

type PerpsTradeEvent = {
  topic: "perps.trades";
  type: "trade";
  channel: string;
  timestamp: number;
  eventTimestamp: EpochMilliseconds | null;
  sequence: number;
  payload: Array<{
    tradeId: number;
    instrumentId: number;
    side: "long" | "short";
    settlement: boolean;
    price: string;
    quantity: string;
    timestamp: number;
    hash?: string;
  }>;
};
```

```json PerpsTradeEvent Example theme={null}
{
  "topic": "perps.trades",
  "type": "trade",
  "channel": "trades::1",
  "timestamp": 1767225600000,
  "eventTimestamp": 1767225600000,
  "sequence": 1234567890,
  "payload": [
    {
      "tradeId": 1,
      "instrumentId": 1,
      "side": "long",
      "price": "100.00",
      "quantity": "10.00",
      "timestamp": 1767225600000,
      "hash": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
      "settlement": false
    }
  ]
}
```

`payload` contains an array of trades. Each trade exposes `settlement`, which is
`false` for ordinary executions and defaults to `false` on older responses.

**Python**

Subscribe to public trade updates for one instrument.

```python theme={null}
from polymarket.streams import PerpsTradesSpec

trades = await client.subscribe(PerpsTradesSpec(instrument_id=1))

async for event in trades:
    # event: PerpsTradeEvent
    pass
```

After subscribing, the stream yields `PerpsTradeEvent` objects like this.

```json PerpsTradeEvent Example theme={null}
{
  "topic": "perps.trades",
  "type": "trade",
  "channel": "trades::1",
  "timestamp": "2026-01-01T00:00:00Z",
  "event_timestamp": "2026-01-01T00:00:00Z",
  "sequence": 1234567890,
  "payload": [
    {
      "trade_id": 1,
      "instrument_id": 1,
      "side": "long",
      "price": "100.00",
      "quantity": "10.00",
      "timestamp": "2026-01-01T00:00:00Z",
      "hash": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
      "settlement": false
    }
  ]
}
```

`payload` contains a list of `PerpsTrade` objects. Each trade exposes
`settlement`, which is `False` for ordinary executions and defaults to `False`
on older responses. Timestamps are UTC `datetime` values; the JSON above shows
their serialization.

**API**

Subscribe to public trade updates for one instrument.

```json Subscribe theme={null}
{
  "id": 3,
  "req": "sub",
  "chs": ["trades::<instrument_id>"]
}
```

After subscribing, the stream emits trade update frames like this.

```json theme={null}
{
  "ch": "trades::1",
  "ts": 1767225600000,
  "ets": 1767225600000,
  "sq": 1234567890,
  "data": [
    {
      "tid": 1,
      "iid": 1,
      "side": "long",
      "p": "100.00",
      "qty": "10.00",
      "ts": 1767225600000,
      "hash": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
      "settlement": false
    }
  ]
}
```

`data` contains an array of trades. Each entry includes `settlement`, which is
`false` for ordinary executions.

## Tickers

Use ticker updates for the current mark, index, last price, open interest, and
funding state.

**TypeScript**

Subscribe to ticker updates for one instrument or all active instruments.

```ts Subscribe to one market theme={null}
const tickers = await client.subscribe([
  { topic: "perps.tickers", instrumentId: 1 },
]);
```

```ts Subscribe to all markets theme={null}
const tickers = await client.subscribe([{ topic: "perps.tickers" }]);
```

```ts theme={null}
for await (const event of tickers) {
  // event: PerpsTickerEvent
}
```

Omit `instrumentId` to subscribe to ticker updates for all active instruments.

After subscribing, the stream yields `PerpsTickerEvent` objects like this.

```ts PerpsTickerEvent Type theme={null}
import type { EpochMilliseconds } from "@polymarket/client";

type PerpsTickerEvent = {
  topic: "perps.tickers";
  type: "ticker";
  channel: string;
  timestamp: number;
  eventTimestamp: EpochMilliseconds | null;
  sequence: number;
  payload: {
    instrumentId: number;
    indexPrice: string;
    markPrice: string;
    lastPrice: string;
    midPrice: string;
    openInterest: string;
    fundingRate: string;
    nextFunding: number;
  };
};
```

```json PerpsTickerEvent Example theme={null}
{
  "topic": "perps.tickers",
  "type": "ticker",
  "channel": "tickers::1",
  "timestamp": 1767225600000,
  "eventTimestamp": 1767225600000,
  "sequence": 1234567890,
  "payload": {
    "instrumentId": 1,
    "indexPrice": "100.00",
    "markPrice": "100.00",
    "lastPrice": "100.00",
    "midPrice": "100.00",
    "openInterest": "10.00",
    "fundingRate": "0.0001",
    "nextFunding": 1767225600000
  }
}
```

**Python**

Subscribe to ticker updates for one instrument or all active instruments.

```python Subscribe to one market theme={null}
from polymarket.streams import PerpsTickersSpec

tickers = await client.subscribe(PerpsTickersSpec(instrument_id=1))
```

```python Subscribe to all markets theme={null}
from polymarket.streams import PerpsTickersSpec

tickers = await client.subscribe(PerpsTickersSpec())
```

```python theme={null}
async for event in tickers:
    # event: PerpsTickerEvent
    pass
```

Omit `instrument_id` to subscribe to ticker updates for all active instruments.

After subscribing, the stream yields `PerpsTickerEvent` objects like this.

```json Example theme={null}
{
  "topic": "perps.tickers",
  "type": "ticker",
  "channel": "tickers::1",
  "timestamp": "2026-01-01T00:00:00Z",
  "event_timestamp": "2026-01-01T00:00:00Z",
  "sequence": 1234567890,
  "payload": {
    "instrument_id": 1,
    "index_price": "100.00",
    "mark_price": "100.00",
    "last_price": "100.00",
    "mid_price": "100.00",
    "open_interest": "10.00",
    "funding_rate": "0.0001",
    "next_funding": 1767225600000
  }
}
```

**API**

Subscribe to ticker updates for one instrument or all active instruments.

```json Subscribe to one market theme={null}
{
  "id": 4,
  "req": "sub",
  "chs": ["tickers::<instrument_id>"]
}
```

```json Subscribe to all markets theme={null}
{
  "id": 5,
  "req": "sub",
  "chs": ["tickers::all"]
}
```

After subscribing, the stream emits ticker update frames like this.

```json theme={null}
{
  "ch": "tickers::1",
  "ts": 1767225600000,
  "ets": 1767225600000,
  "sq": 1234567890,
  "data": {
    "iid": 1,
    "idx": "100.00",
    "mark": "100.00",
    "last": "100.00",
    "mid": "100.00",
    "oi": "10.00",
    "fr": "0.0001",
    "nxf": 1767225600000
  }
}
```

## Statistics

Use statistics for 24-hour volume, opening price, and the rolling kline window.

**TypeScript**

Subscribe to 24-hour statistics for one instrument or all active instruments.

```ts Subscribe to one market theme={null}
const statistics = await client.subscribe([
  { topic: "perps.statistics", instrumentId: 1 },
]);
```

```ts Subscribe to all markets theme={null}
const statistics = await client.subscribe([{ topic: "perps.statistics" }]);
```

```ts theme={null}
for await (const event of statistics) {
  // event: PerpsStatisticEvent
}
```

Omit `instrumentId` to subscribe to statistics updates for all active instruments.

After subscribing, the stream yields `PerpsStatisticEvent` objects like this.

```ts PerpsStatisticEvent Type theme={null}
import type { EpochMilliseconds } from "@polymarket/client";

type PerpsStatisticEvent = {
  topic: "perps.statistics";
  type: "statistic";
  channel: string;
  timestamp: number;
  eventTimestamp: EpochMilliseconds | null;
  sequence: number;
  payload: {
    instrumentId: number;
    volume: string;
    openPrice: string;
    klines: Array<{
      timestamp: number;
      open: string;
      high: string;
      low: string;
      close: string;
      volume: string;
      trades: number;
    }>;
  };
};
```

```json PerpsStatisticEvent Example theme={null}
{
  "topic": "perps.statistics",
  "type": "statistic",
  "channel": "statistics::1",
  "timestamp": 1767225600000,
  "eventTimestamp": 1767225600000,
  "sequence": 1234567890,
  "payload": {
    "instrumentId": 1,
    "volume": "1000.00",
    "openPrice": "100.50",
    "klines": [
      {
        "timestamp": 1767225600000,
        "open": "100.00",
        "high": "105.00",
        "low": "99.00",
        "close": "102.00",
        "volume": "500.00",
        "trades": 42
      }
    ]
  }
}
```

**Python**

Subscribe to 24-hour statistics for one instrument or all active instruments.

```python Subscribe to one market theme={null}
from polymarket.streams import PerpsStatisticsSpec

statistics = await client.subscribe(PerpsStatisticsSpec(instrument_id=1))
```

```python Subscribe to all markets theme={null}
from polymarket.streams import PerpsStatisticsSpec

statistics = await client.subscribe(PerpsStatisticsSpec())
```

```python theme={null}
async for event in statistics:
    # event: PerpsStatisticEvent
    pass
```

Omit `instrument_id` to subscribe to statistics updates for all active
instruments.

After subscribing, the stream yields `PerpsStatisticEvent` objects like this.

```json Example theme={null}
{
  "topic": "perps.statistics",
  "type": "statistic",
  "channel": "statistics::1",
  "timestamp": "2026-01-01T00:00:00Z",
  "event_timestamp": "2026-01-01T00:00:00Z",
  "sequence": 1234567890,
  "payload": {
    "instrument_id": 1,
    "volume": "1000.00",
    "open_price": "100.50",
    "klines": [
      {
        "timestamp": 1767225600000,
        "open": "100.00",
        "high": "105.00",
        "low": "99.00",
        "close": "102.00",
        "volume": "500.00",
        "trades": 42
      }
    ]
  }
}
```

**API**

Subscribe to 24-hour statistics for one instrument or all active instruments.

```json Subscribe to one market theme={null}
{
  "id": 6,
  "req": "sub",
  "chs": ["statistics::<instrument_id>"]
}
```

```json Subscribe to all markets theme={null}
{
  "id": 7,
  "req": "sub",
  "chs": ["statistics::all"]
}
```

After subscribing, the stream emits statistics update frames like this.

```json theme={null}
{
  "ch": "statistics::1",
  "ts": 1767225600000,
  "ets": 1767225600000,
  "sq": 1234567890,
  "data": {
    "iid": 1,
    "vol": "1000.00",
    "open": "100.50",
    "klines": [
      [1767225600000, "100.00", "105.00", "99.00", "102.00", "500.00", 42]
    ]
  }
}
```

## Candles

Use candles to update charts with live OHLCV data.

**TypeScript**

Subscribe to candle updates for one instrument and interval.

```ts theme={null}
import { PerpsKlineInterval } from "@polymarket/client";

const candles = await client.subscribe([
  {
    topic: "perps.candles",
    instrumentId: 1,
    interval: PerpsKlineInterval.OneMinute,
  },
]);

for await (const event of candles) {
  // event: PerpsCandleEvent
}
```

The public stream supports `1m`, `5m`, `15m`, `1h`, `4h`, `1d`, and `1w`
candle intervals.

After subscribing, the stream yields `PerpsCandleEvent` objects like this.

```ts PerpsCandleEvent Type theme={null}
import type { EpochMilliseconds } from "@polymarket/client";

type PerpsCandleEvent = {
  topic: "perps.candles";
  type: "candle";
  channel: string;
  timestamp: number;
  eventTimestamp: EpochMilliseconds | null;
  sequence: number;
  payload: {
    instrumentId: number;
    interval: "1m" | "5m" | "15m" | "1h" | "4h" | "1d" | "1w";
    candles: Array<{
      timestamp: number;
      open: string;
      high: string;
      low: string;
      close: string;
      volume: string;
      trades: number;
    }>;
  };
};
```

```json PerpsCandleEvent Example theme={null}
{
  "topic": "perps.candles",
  "type": "candle",
  "channel": "klines::1::1m",
  "timestamp": 1767225600000,
  "eventTimestamp": 1767225600000,
  "sequence": 1234567890,
  "payload": {
    "instrumentId": 1,
    "interval": "1m",
    "candles": [
      {
        "timestamp": 1767225600000,
        "open": "100.00",
        "high": "105.00",
        "low": "99.00",
        "close": "102.00",
        "volume": "500.00",
        "trades": 42
      }
    ]
  }
}
```

**Python**

Subscribe to candle updates for one instrument and interval.

```python theme={null}
from polymarket.streams import PerpsCandlesSpec

candles = await client.subscribe(
    PerpsCandlesSpec(
        instrument_id=1,
        interval="1m",
    )
)

async for event in candles:
    # event: PerpsCandleEvent
    pass
```

The public stream supports `1m`, `5m`, `15m`, `1h`, `4h`, `1d`, and `1w`
candle intervals.

After subscribing, the stream yields `PerpsCandleEvent` objects like this.

```json Example theme={null}
{
  "topic": "perps.candles",
  "type": "candle",
  "channel": "klines::1::1m",
  "timestamp": "2026-01-01T00:00:00Z",
  "event_timestamp": "2026-01-01T00:00:00Z",
  "sequence": 1234567890,
  "payload": {
    "instrument_id": 1,
    "interval": "1m",
    "candles": [
      {
        "timestamp": 1767225600000,
        "open": "100.00",
        "high": "105.00",
        "low": "99.00",
        "close": "102.00",
        "volume": "500.00",
        "trades": 42
      }
    ]
  }
}
```

**API**

Subscribe to candle updates for one instrument and interval.

```json Subscribe theme={null}
{
  "id": 8,
  "req": "sub",
  "chs": ["klines::<instrument_id>::1m"]
}
```

The public stream supports `1s`, `1m`, `5m`, `15m`, `30m`, `1h`, `4h`, `6h`,
`12h`, `1d`, and `1w` candle intervals. Any other interval is refused in the
subscribe acknowledgement with an `invalid channel` error.

After subscribing, the stream emits kline update frames like this.

```json theme={null}
{
  "ch": "klines::1::1m",
  "ts": 1767225600000,
  "ets": 1767225600000,
  "sq": 1234567890,
  "data": [[1767225600000, "100.00", "105.00", "99.00", "102.00", "500.00", 42]]
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.