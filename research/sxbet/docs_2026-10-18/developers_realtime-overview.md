> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Realtime overview

> Subscribe to live updates on markets, orders, trades, odds, and scores using the SX Bet WebSocket API.

## Overview

SX Bet's WebSocket API delivers real-time updates on orderbook changes, trade executions, market
status, and live scores. All channels are powered by [Centrifugo](https://centrifugal.dev/) over a
single WebSocket connection and require a short-lived token. Centrifugo provides
[official client SDKs](https://centrifugal.dev/docs/transports/client_sdk) for JavaScript, Python,
Go, Dart, Swift, Java, and C#; the examples here use the JavaScript and Python ones.

Rather than polling REST endpoints, subscribe to the channels relevant to your workflow. The
recommended pattern for most use cases is: [fetch current state via REST, then subscribe to stay
updated](/developers/realtime-initialization#snapshot-+-subscribe-pattern) — this avoids gaps
between your initial snapshot and the live feed.

<Steps>
  <Step title="Authenticate">
    Pass your API key via the `getToken` callback. Token refresh is handled automatically.
  </Step>

  <Step title="Connect">
    Create a `Centrifuge` client pointed at the WebSocket URL.
  </Step>

  <Step title="Subscribe">
    Create a subscription for each channel you need and attach a publication handler.
  </Step>
</Steps>

***

## Getting started

Install a client SDK, fetch a token, connect, and subscribe — see
[Initialization](/developers/realtime-initialization).

***

## Channels

| Channel | What you receive | Payload reference |
| - | - | - |
| `orderbook_v3:{marketHash}` | The full aggregated book for one market, on every change | [orderbook\_v3 →](/api-reference/channel-orderbook-v3) |
| `orderbook_v3_event:{eventId}` | The same book bodies for every market on an event | [orderbook\_v3\_event →](/api-reference/channel-orderbook-v3-event) |
| `best_odds_v3:global` | Top-of-book odds changes across all markets | [best\_odds\_v3 →](/api-reference/channel-best-odds-v3) |
| `account:orders_v3_#{address}` | Your order state changes, and why an order ended | [orders\_v3 →](/api-reference/channel-orders-v3) |
| `account:trades_v3_#{address}` | Your bets, at bet grain | [trades\_v3 →](/api-reference/channel-trades-v3) |
| `account:fills_v3_#{address}` | Your individual fills | [fills\_v3 →](/api-reference/channel-fills-v3) |
| `recent_trades_v3:global` | The anonymized public tape, one message per taker bet | [recent\_trades\_v3 →](/api-reference/channel-recent-trades-v3) |
| `markets:global` | Market create, update, and settlement | [markets →](/api-reference/channel-markets) |
| `main_line:global` | Which market is now an event's main line | [line changes →](/api-reference/channel-line-changes) |
| `fixtures:global` | Fixture metadata updates | [fixtures →](/api-reference/channel-fixtures) |
| `fixtures:live_scores` | Live match scores | [fixtures →](/api-reference/channel-fixtures) |
| `parlay_markets:global` | Incoming parlay quote requests | [parlay requests →](/api-reference/channel-parlay-requests) |

### Subscribing to your own channels

Account channels use Centrifugo's **user-limited channel** form: a literal `#` followed by the
checksummed address the channel belongs to, which must byte-match your token's `sub`. For example:
`account:orders_v3_#0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5`.

<CodeGroup>
  ```javascript JavaScript theme={null}
  const address = "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5";   // checksummed

  client.newSubscription(`account:orders_v3_#${address}`, { recoverable: true })
    .on("publication", ({ data }) => onOrder(data.order))
    .subscribe();
  ```

  ```python Python theme={null}
  import asyncio
  from centrifuge import Client, PublicationContext, SubscriptionEventHandler

  ADDRESS = "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5"  # checksummed

  async def on_publication(ctx: PublicationContext) -> None:
      on_order(ctx.data["order"])

  async def main():
      client = Client(WS_URL, get_token=fetch_token)
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      sub = client.new_subscription(f"account:orders_v3_#{ADDRESS}", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

<Warning>
  Ensure you checksum the address passed in and do not use your proxy wallet address, use your **account address**
</Warning>

***

## Recovery & reliability

Recovery, history, deduplication, and the snapshot-plus-subscribe seed pattern live on the
[Recovery & reliability](/developers/realtime-reliability) reference. In short: pass
`recoverable: true` on channels whose namespace has history, then check `recovered` in the
`subscribed` handler to decide whether to re-seed from REST.

***

## Connection & Subscription Lifecycle

The client connection and each subscription have separate lifecycles. The key rule is:

* `connecting` and `subscribing` are non-terminal states. They fire on the initial connect or
  subscribe and also on automatic retry paths.
* `disconnected` and `unsubscribed` are terminal states for automatic retry.

### Client lifecycle

The client connection moves through these states:

* `disconnected -> connecting -> connected`: initial connect
* `connected -> connecting -> connected`: retryable disconnect, then successful reconnect
* `connecting/connected -> disconnected`: terminal disconnect

Use the client events to understand what happened:

* `connecting`: fired on the initial `connect()` and on retryable reconnects. The event includes a
  `code` and `reason`.
* `connected`: fired when the transport is established and the client is ready.
* `disconnected`: fired only when the client reaches terminal `disconnected` state. After this, the
  SDK will not reconnect automatically.
* `error`: fired for internal errors that do not necessarily cause a state transition, such as
  transport errors during initial connect or reconnect, or connection token refresh errors.

```javascript theme={null}
client.on("connecting", (ctx) => console.log("connecting", ctx.code, ctx.reason));
client.on("connected", () => console.log("connected"));
client.on("disconnected", (ctx) => console.log("disconnected", ctx.code, ctx.reason));
client.on("error", (ctx) => console.error("client error", ctx));
```

To reconnect after a terminal disconnect, call `client.connect()` explicitly.

### Subscription lifecycle

Each client-side subscription moves through its own state machine:

* `unsubscribed -> subscribing -> subscribed`: initial subscribe
* `subscribed -> subscribing -> subscribed`: retryable interruption, reconnect, or resubscribe
* `subscribing/subscribed -> unsubscribed`: terminal subscription stop

Use subscription events to understand what happened:

* `subscribing`: fired on the initial `subscribe()` and on retryable resubscribe paths.
* `subscribed`: fired when the subscription becomes active.
* `unsubscribed`: fired only when the subscription reaches terminal `unsubscribed` state. After this,
  the SDK will not resubscribe automatically.
* `publication`: fired whenever a new message arrives on the subscription while it is active.
* `error`: fired for internal subscription errors that do not necessarily cause a state transition,
  such as temporary subscribe errors or subscription token related errors.

```javascript theme={null}
sub.on("subscribing", (ctx) => console.log("subscribing", ctx.code, ctx.reason));
sub.on("subscribed", (ctx) => console.log("subscribed", ctx.wasRecovering, ctx.recovered));
sub.on("unsubscribed", (ctx) => console.log("unsubscribed", ctx.code, ctx.reason));
sub.on("publication", (ctx) => console.log("publication", ctx.data));
sub.on("error", (ctx) => console.error("subscription error", ctx.error?.code, ctx.error?.message));
```

To start a terminally unsubscribed subscription again, call `sub.subscribe()` explicitly. Handle `unsubscribed` on every account channel.

***

## Examples

### Consume a global feed

<CodeGroup>
  ```javascript JavaScript theme={null}
  import { Centrifuge } from "centrifuge";

  const client = new Centrifuge("wss://realtime.sx.bet/connection/websocket", {
    getToken: () => fetchToken(YOUR_API_KEY), // see Initialization
  });

  const sub = client.newSubscription("markets:global");

  sub.on("publication", (ctx) => {
    for (const market of ctx.data) {
      console.log(`${market.marketHash}: status=${market.status}`);
    }
  });

  sub.subscribe();
  client.connect();
  ```

  ```python Python theme={null}
  import asyncio
  from centrifuge import Client, PublicationContext, SubscriptionEventHandler

  async def on_publication(ctx: PublicationContext) -> None:
      for market in ctx.data:
          print(f"{market['marketHash']}: status={market['status']}")

  async def main():
      client = Client(
          "wss://realtime.sx.bet/connection/websocket",
          get_token=fetch_token,
      )
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      sub = client.new_subscription("markets:global", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

### Maintain a recoverable order book

Subscribe to `orderbook_v3:{marketHash}` with `recoverable: true`. Apply only newer versions, and
seed from the REST snapshot on subscribe so you never miss updates between your snapshot and the live
feed:

<CodeGroup>
  ```javascript JavaScript theme={null}
  let book = null;

  function isNewer(incoming, current) {
    if (!current) return true;
    return incoming > current;   // version strings order lexicographically
  }

  async function watchMarket(client, marketHash) {
    const sub = client.newSubscription(`orderbook_v3:${marketHash}`, {
      recoverable: true,
    });

    sub.on("publication", ({ data }) => {
      if (!isNewer(data.version, book?.version)) return;
      book = data;
    });

    sub.on("subscribed", async () => {
      const { data } = await fetch(
        `https://api.sx.bet/orderbook-v3/snapshot/market?marketHash=${marketHash}`,
        { headers: { "x-sx-api-key": process.env.SX_API_KEY } }
      ).then((r) => r.json());
      if (isNewer(data.version, book?.version)) {
        book = data;   // REST data is flat — same shape a publication carries
      }
    });

    sub.subscribe();
  }
  ```

  ```python Python theme={null}
  import os
  from centrifuge import (
      PublicationContext,
      SubscribedContext,
      SubscriptionEventHandler,
      SubscriptionOptions,
  )

  book: dict | None = None

  def is_newer(incoming: str, current: str | None) -> bool:
      # version strings order lexicographically.
      return current is None or incoming > current

  class BookHandler(SubscriptionEventHandler):
      def __init__(self, market_hash: str) -> None:
          self.market_hash = market_hash

      async def on_publication(self, ctx: PublicationContext) -> None:
          global book
          d = ctx.data
          if not is_newer(d["version"], book["version"] if book else None):
              return
          book = d

      async def on_subscribed(self, ctx: SubscribedContext) -> None:
          global book
          snapshot = await fetch_json(
              f"https://api.sx.bet/orderbook-v3/snapshot/market?marketHash={self.market_hash}",
              headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
          )
          d = snapshot["data"]                 # REST data is flat — same shape a publication carries
          if is_newer(d["version"], book["version"] if book else None):
              book = d

  async def watch_market(client, market_hash: str) -> None:
      options = SubscriptionOptions(recoverable=True)
      sub = client.new_subscription(
          f"orderbook_v3:{market_hash}", BookHandler(market_hash), options
      )
      await sub.subscribe()
  ```
</CodeGroup>

See [Book versioning](/developers/book-versioning#the-apply-rule) for the full apply rule.

### Monitor your active orders

Subscribe to `account:orders_v3_#{address}` to receive fills, cancellations, and new posts for your
address in real time. This channel is the only place an order's terminal reason appears:

<CodeGroup>
  ```javascript JavaScript theme={null}
  import { getAddress } from "viem";

  const address = getAddress(myAddress);   // checksummed, must match your token's `sub`
  const orders = new Map();
  const seen = new Set();

  const sub = client.newSubscription(`account:orders_v3_#${address}`, {
    recoverable: true,
  });

  sub.on("publication", (ctx) => {
    const id = ctx.tags?.messageId;
    if (id !== undefined) {
      if (seen.has(id)) return;   // drop replayed duplicates
      seen.add(id);
    }
    const o = ctx.data.order;
    orders.set(o.id, o);   // subscribe-first ordering means the latest arrival wins
    if (o.status === "INACTIVE") console.log(`${o.id} died: ${o.inactiveReason}`);
  });

  sub.on("subscribed", async (ctx) => {
    if (ctx.wasRecovering && ctx.recovered) return;
    // Fresh connect or failed recovery — seed active orders from REST
    const { data } = await fetch(`https://api.sx.bet/orders-v3`, authed).then((r) => r.json());
    orders.clear();
    for (const o of data.orders) orders.set(o.id, o);
  });

  sub.on("unsubscribed", (ctx) => console.error("refused", ctx.code, ctx.reason));

  sub.subscribe();
  client.connect();
  ```

  ```python Python theme={null}
  from eth_utils import to_checksum_address
  from centrifuge import (
      PublicationContext,
      SubscribedContext,
      SubscriptionEventHandler,
      SubscriptionOptions,
  )

  address = to_checksum_address(my_address)   # must match your token's `sub`
  orders: dict = {}
  seen: set = set()

  class OrdersHandler(SubscriptionEventHandler):
      async def on_publication(self, ctx: PublicationContext) -> None:
          msg_id = (ctx.tags or {}).get("messageId")
          if msg_id is not None:
              if msg_id in seen:   # drop replayed duplicates
                  return
              seen.add(msg_id)
          o = ctx.data["order"]
          orders[o["id"]] = o   # subscribe-first ordering means the latest arrival wins
          if o["status"] == "INACTIVE":
              print(o["id"], "died:", o["inactiveReason"])

      async def on_subscribed(self, ctx: SubscribedContext) -> None:
          if ctx.was_recovering and ctx.recovered:
              return
          body = await fetch_json("https://api.sx.bet/orders-v3")
          orders.clear()
          for o in body["data"]["orders"]:
              orders[o["id"]] = o

  options = SubscriptionOptions(recoverable=True)
  sub = client.new_subscription(
      f"account:orders_v3_#{address}", OrdersHandler(), options
  )
  await sub.subscribe()
  ```
</CodeGroup>

`GET /orders-v3` returns active orders only, so polling can tell you an order is gone but never why.
See [Tracking your orders](/developers/my-orders).

***

## Common failures

In most cases, you do not need to write custom retry logic around these errors. The SDK already
handles reconnect and resubscribe automatically when the condition is retryable. The codes below are
most useful for telemetry, debugging, and contacting support if an issue persists.

### Auth

The `getToken` callback is called on initial connect and whenever the token needs to be refreshed.
How you throw from it controls what the SDK does next:

<CodeGroup>
  ```javascript JavaScript theme={null}
  import { Centrifuge, UnauthorizedError } from "centrifuge";

  const client = new Centrifuge(WS_URL, {
    getToken: async () => {
      const res = await fetch(`${RELAYER_URL}/user/realtime-token-v3/api-key`, {
        headers: { "x-sx-api-key": apiKey },
      });
      if (res.status === 401 || res.status === 403) {
        throw new UnauthorizedError(); // permanent — stops all reconnect attempts
      }
      if (!res.ok) throw new Error(`Status ${res.status}`); // transient — retries with backoff
      const { token } = await res.json();
      return token;
    },
  });
  ```

  ```python Python theme={null}
  import os
  import aiohttp
  from centrifuge import Client, UnauthorizedError

  async def fetch_token(ctx=None):
      async with aiohttp.ClientSession() as session:
          async with session.get(
              f"{RELAYER_URL}/user/realtime-token-v3/api-key",
              headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
          ) as resp:
              if resp.status in (401, 403):
                  raise UnauthorizedError()  # permanent — stops all reconnect attempts
              if not resp.ok:
                  raise Exception(f"Status {resp.status}")  # transient — retries with backoff
              data = await resp.json()
              return data["token"]

  client = Client(WS_URL, get_token=fetch_token)
  ```
</CodeGroup>

If your realtime-token endpoint returns `401` or `403`, throw `UnauthorizedError` so the connection
stops retrying and moves to terminal `disconnected`. For transient failures like `429` or `5xx`,
throw a normal error so the SDK keeps retrying with backoff. See [Rate limits](/developers/rate-limits).

The server may also issue a terminal auth disconnect such as code `3500` (`"invalid token"`). In
that case, the client stops reconnecting automatically.

### Subscribe errors

Retryable subscription errors emit the subscription `error` event. Terminal subscription errors move
the subscription to `unsubscribed`.

<CodeGroup>
  ```javascript JavaScript theme={null}
  sub.on("error", (ctx) => {
    console.error(ctx.error.code, ctx.error.message);
  });
  ```

  ```python Python theme={null}
  from centrifuge import SubscriptionErrorContext, SubscriptionEventHandler

  async def on_error(ctx: SubscriptionErrorContext) -> None:
      print(ctx.error.code, ctx.error.message)

  handler = SubscriptionEventHandler(on_error=on_error)
  ```
</CodeGroup>

| Code | Meaning | What happens next |
| - | - | - |
| `100` | Internal server error | The subscription stays in `subscribing` and the SDK retries. |
| `101` | Unauthorized | The subscription moves to terminal `unsubscribed`. |
| `102` | Unknown channel | The subscription moves to terminal `unsubscribed`. |
| `103` | Permission denied — an account-channel suffix that does not match your token's `sub` | The subscription moves to terminal `unsubscribed`. |
| `106` | Limit exceeded — the connection is at the 512-channel cap | The subscription moves to terminal `unsubscribed`. |
| `108` | No history on this channel | Returned by `history()`. |
| `109` | Token expired | The subscription stays in `subscribing`; the SDK refreshes the token and retries. |
| `111` | Too many requests | The subscription stays in `subscribing` and the SDK retries. |

For the full list of built-in client error codes, see
[Centrifugo client protocol codes](https://centrifugal.dev/docs/server/codes).

### Recovery lost / insufficient state

If Centrifugo detects that recovery cannot continue from the current stream position, it may either
resubscribe the affected subscription or reconnect the client, depending on where the problem is
detected. This can surface as unsubscribe code `2500` or disconnect code `3010`, both with reason
`"insufficient state"`.

This is not terminal by itself. The next `subscribed` event tells you whether the replay succeeded:

* `wasRecovering: true, recovered: true`: replay filled the gap
* `wasRecovering: true, recovered: false`: replay could not fill the gap, so re-seed from REST

If you see `insufficient state` frequently, it usually indicates a stream continuity problem rather
than a client bug.

### Terminal disconnects

The client reconnects automatically after most disconnects. It does **not** reconnect for built-in
terminal disconnect codes in the `3500-3999` range.

Common terminal examples include:

* `3500` `invalid token`
* `3501` `bad request`
* `3503` `force disconnect`
* `3507` `permission denied`

For the full list of built-in disconnect codes, see
[Centrifugo client protocol codes](https://centrifugal.dev/docs/server/codes).

### Slow consumer

The server buffers about 1 MB per connection. If your `publication` handler is slow, that buffer
fills faster than it drains and the server closes the connection. In Centrifugo this surfaces as
disconnect code `3008` (`"slow"`), which is reconnectable but indicates your consumer cannot keep up.

Keep handlers fast: receive the message and hand it off to a queue or async task immediately.

***

## Related

<CardGroup cols={2}>
  <Card title="Market Making →" icon="chart-line" href="/developers/market-making">
    Using `account:orders_v3` to monitor your open orders in real time.
  </Card>

  <Card title="Taking Liquidity →" icon="bolt" href="/developers/taking-liquidity">
    How to submit fills and monitor your trade history.
  </Card>

  <Card title="Initialization →" icon="plug" href="/developers/realtime-initialization">
    Install, connect, and subscribe with the Centrifuge client.
  </Card>

  <Card title="Market Making Parlays →" icon="layer-group" href="/developers/market-making-parlays">
    Responding to parlay quote requests via `parlay_markets:global`.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.