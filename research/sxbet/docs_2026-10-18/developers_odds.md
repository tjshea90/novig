> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Fetching odds

> Read the order book and best odds over REST, then stream both in realtime.

| | [`GET /orderbook-v3/snapshot/market`](/api-reference/get-market-orderbook-snapshot) | [`GET /orderbook-v3/snapshot/event`](/api-reference/get-event-orderbook-snapshot) | [`GET /orders-v3/odds/best`](/api-reference/get-best-odds-v3) |
| - | - | - | - |
| Use it to | get the book for one event | get the book for every market in an event | scan a watchlist or a board |
| Markets per call | **1** | **all** on one event | **100** hashes, or **5** leagues |
| Levels per side | **all** | **all** | **1** — top of book |
| Realtime | [`orderbook_v3:{marketHash}`](/api-reference/channel-orderbook-v3) | [`orderbook_v3_event:{eventId}`](/api-reference/channel-orderbook-v3-event) | [`best_odds_v3:global`](/api-reference/channel-best-odds-v3) |

## Fetching the order book

```bash theme={null}
curl "https://api.sx.bet/orderbook-v3/snapshot/market?marketHash=0x5be2a55e4b7dec1cec1b75af93b3fdef178ec1473866da95b3c68c1d4c99d4fd" \
  --header "x-sx-api-key: YOUR_API_KEY"
```

```json theme={null}
{
  "status": "success",
  "data": {
    "marketHash": "0x5be2a55e…d4fd",
    "outcomeOne": [{ "percentageOdds": "52000000000000000000", "size": "2000000" }],
    "outcomeTwo": [{ "percentageOdds": "46750000000000000000", "size": "1000000" }],
    "version": "00100000000000002210000"
  }
}
```

`size` is the resting maker's stake, not what you can bet against it. To turn a level into taker
capacity and price a bet across levels, see [The order book](/developers/order-book).

## Fetching best odds

Top of book for select markets. A side is `null` when it has no liquidity.

```bash theme={null}
curl "https://api.sx.bet/orders-v3/odds/best?marketHashes=0x5be2a55e…d4fd,0xbf06c637…a2eb" \
  --header "x-sx-api-key: YOUR_API_KEY"
```

```javascript seed_best_odds.mjs theme={null}
const API = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet
const marketHashes = [
  "0x5be2a55e4b7dec1cec1b75af93b3fdef178ec1473866da95b3c68c1d4c99d4fd",
  "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb",
];

// One request per <=100 hashes. Comma-separated — never repeated params.
const url = `${API}/orders-v3/odds/best?marketHashes=${marketHashes.join(",")}`;
const { status, data } = await fetch(url, {
  headers: { "x-sx-api-key": process.env.SX_API_KEY },
}).then((r) => r.json());
if (status !== "success") throw new Error(`best-odds returned status=${status}`);

for (const row of data.bestOdds) {
  // row is { marketHash, outcomeOne, outcomeTwo }; each side is a level or null.
  console.log(row.marketHash, row.outcomeOne, row.outcomeTwo);
}
```

## Seeding and then subscribing to realtime best odds

Seed from `GET /orders-v3/odds/best` and treat publications as updates on top of that seed. Note that the channel is a global channel

```javascript theme={null}
const sub = client.newSubscription("best_odds_v3:global");

sub.on("publication", (ctx) => {
  if (watchlist.has(ctx.data.marketHash)) applyBestOdds(ctx.data);
});

sub.on("subscribed", async () => {
  // Unconditional — this channel has no recovery, so seed on every connect.
  const { status, data } = await fetch(
    `${API}/orders-v3/odds/best?marketHashes=${marketHashes.join(",")}`,
    { headers: { "x-sx-api-key": process.env.SX_API_KEY } }
  ).then((r) => r.json());
  if (status !== "success") return; // keep prior state; retry next tick
  for (const row of data.bestOdds) applyBestOdds(row);
});

sub.subscribe();
```

A publication goes out only when a market's top of book **changes**.

## Seeding and subscribing realtime to the order book

`orderbook_v3:{marketHash}` carries the same shape the
snapshot serves, and each publication is a full replacement, not a delta. Subscribe first, then seed from REST, and run both the snapshot and every publication through
the version [apply rule](/developers/book-versioning#the-apply-rule).

<Tip>
  Watching many markets on the same events? Prefer
  [`orderbook_v3_event:{eventId}`](/api-reference/channel-orderbook-v3-event) and seed from
  [`GET /orderbook-v3/snapshot/event`](/api-reference/get-event-orderbook-snapshot).
</Tip>

```javascript theme={null}
const sub = client.newSubscription(`orderbook_v3:${marketHash}`);

// Every publication runs through the version rule; stale ones are discarded.
sub.on("publication", (ctx) => applyUpdate(ctx.data));

sub.on("subscribed", async (ctx) => {
  // recovered: true means the book was replayed — no REST seed needed.
  if (ctx.recovered) return;
  const { data } = await fetch(
    `${API}/orderbook-v3/snapshot/market?marketHash=${marketHash}`,
    { headers: { "x-sx-api-key": process.env.SX_API_KEY } }
  ).then((r) => r.json());
  // The snapshot goes through the same version rule, so a newer live update
  // that arrived during the fetch is not clobbered. `data` is flat — the level
  // arrays sit directly beside `marketHash` and `version`.
  applySnapshot(marketHash, data.version, data);
});

sub.subscribe();
```

## Related

<CardGroup cols={2}>
  <Card title="The order book" icon="book-open" href="/developers/order-book">
    Depth, sizing, and pricing a bet across levels.
  </Card>

  <Card title="Book versioning" icon="code-compare" href="/developers/book-versioning">
    Applying live book updates in the right order.
  </Card>

  <Card title="Real-time data" icon="tower-broadcast" href="/developers/realtime-overview">
    Tokens, snapshot + subscribe, and reconnect handling.
  </Card>

  <Card title="Best odds" icon="bolt" href="/api-reference/get-best-odds-v3">
    Parameters, caps, and validation rules.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.