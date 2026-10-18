> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Initialization

> Connect to the SX Bet real-time WebSocket API using Centrifuge.

## Install

<CodeGroup>
  ```bash npm theme={null}
  npm install centrifuge
  ```

  ```bash pip theme={null}
  pip install centrifuge-python aiohttp
  ```
</CodeGroup>

Centrifugo provides official client SDKs for most platforms:

| SDK | Language / Platform |
| - | - |
| [centrifuge-js](https://github.com/centrifugal/centrifuge-js) | JavaScript — browser, Node.js, React Native |
| [centrifuge-python](https://github.com/centrifugal/centrifuge-python) | Python (asyncio) |
| [centrifuge-go](https://github.com/centrifugal/centrifuge-go) | Go |
| [centrifuge-dart](https://github.com/centrifugal/centrifuge-dart) | Dart / Flutter |
| [centrifuge-swift](https://github.com/centrifugal/centrifuge-swift) | Swift (iOS) |
| [centrifuge-java](https://github.com/centrifugal/centrifuge-java) | Java / Android |
| [centrifuge-csharp](https://github.com/centrifugal/centrifuge-csharp) | C# (.NET, MAUI, Unity) |

For the full list including community SDKs, see the [Centrifugo client SDK docs](https://centrifugal.dev/docs/transports/client_sdk).

## Connect

Fetch a token using your API key, then instantiate and connect the Centrifuge client. You only need one client instance — all channel subscriptions are multiplexed over the single connection. If you need more than 512 subscriptions, create additional client instances (each connection supports up to 512 channels). See [Limits](/developers/realtime-reference#global-limits) for details.

<CodeGroup>
  ```javascript JavaScript theme={null}
  import { Centrifuge } from "centrifuge";

  const RELAYER_URL = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet
  const WS_URL = "wss://realtime.sx.bet/connection/websocket"; // Mainnet — use wss://realtime.toronto.sx.bet/connection/websocket for testnet

  async function fetchToken(apiKey) {
    const res = await fetch(`${RELAYER_URL}/user/realtime-token-v3/api-key`, {
      headers: { "x-sx-api-key": apiKey },
    });
    if (!res.ok) {
      const body = await res.text();
      throw new Error(`Token endpoint returned ${res.status}: ${body}`);
    }
    const { token } = await res.json();
    return token;
  }

  const client = new Centrifuge(WS_URL, {
    getToken: () => fetchToken(YOUR_API_KEY),
  });

  client.connect();
  ```

  ```python Python theme={null}
  import asyncio
  import os
  import aiohttp
  from centrifuge import Client

  RELAYER_URL = "https://api.sx.bet"  # Mainnet — use https://api.toronto.sx.bet for testnet
  WS_URL = "wss://realtime.sx.bet/connection/websocket"  # Mainnet — use wss://realtime.toronto.sx.bet/connection/websocket for testnet

  # Must be `async def`, and use an async HTTP client. A blocking call here stalls the
  # event loop, and with it every subscription on the connection.
  async def fetch_token(ctx=None):
      async with aiohttp.ClientSession() as session:
          async with session.get(
              f"{RELAYER_URL}/user/realtime-token-v3/api-key",
              headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
          ) as resp:
              if not resp.ok:
                  body = await resp.text()
                  raise Exception(f"Token endpoint returned {resp.status}: {body}")
              data = await resp.json()
              return data["token"]

  async def main():
      client = Client(WS_URL, get_token=fetch_token)
      await client.connect()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

## Subscribe to a channel

Once connected, create a subscription for the channel you want. All channel pages in this section use this same pattern — replace `"channel:name"` with the channel name format documented on each page.

<CodeGroup>
  ```javascript JavaScript theme={null}
  const sub = client.newSubscription("channel:name", { recoverable: true });

  sub.on("publication", (ctx) => {
    const data = ctx.data;
    // handle incoming message
  });

  sub.subscribe();
  ```

  ```python Python theme={null}
  import asyncio
  from centrifuge import Client, PublicationContext, SubscriptionEventHandler, SubscriptionOptions

  async def on_publication(ctx: PublicationContext) -> None:
      print(ctx.data)

  async def main():
      client = Client(WS_URL, get_token=fetch_token)
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      options = SubscriptionOptions(recoverable=True)
      sub = client.new_subscription("channel:name", handler, options)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

<Tip>
  Pass `recoverable: true` on channels whose namespace has history to get at-least-once delivery across reconnects. You do not need `positioned: true` — requesting recovery grants a stream position. See [Namespace history capabilities](/developers/realtime-reference#history-and-recovery-per-namespace) for which channels support this, and [Recovery & reliability](/developers/realtime-reliability) for how to handle recovery outcomes.
</Tip>

Each publication event exposes a `ctx` object with the following structure:

```json theme={null}
{
  "channel": "parlay_markets:global",
  "data": { },
  "tags": {
    "publishedAt": "1773869618503",
    "messageId": "5db68f78-8442-4530-8476-62495333e9ee"
  }
}
```

`ctx.data` is the channel payload documented on each channel's reference page. `ctx.tags.messageId` is a UUID present on every publication — use it to deduplicate messages (see [Recovery & reliability → Dedup](/developers/realtime-reliability#dedup)).

## Snapshot + subscribe pattern

Don't rely on the live feed alone to build initial state. Subscribe first, then seed from REST inside
the `subscribed` handler. This closes the gap between "when you fetched the snapshot" and "when your
first publication arrives":

1. Create the subscription with `recoverable: true` (which also grants a stream position — you do not
   need `positioned: true`).
2. In the `subscribed` handler, fetch current state from REST.
3. Apply live updates as publications arrive.

On reconnects, the same `subscribed` handler fires — check `wasRecovering` and `recovered` to decide
whether you need to re-seed (see
[Recovery & reliability](/developers/realtime-reliability)).

<CodeGroup>
  ```javascript JavaScript theme={null}
  const sub = client.newSubscription(`orderbook_v3:${marketHash}`);

  // Every publication runs through the version rule; stale ones are discarded.
  sub.on("publication", (ctx) => applyUpdate(ctx.data));

  sub.on("subscribed", async (ctx) => {
    // recovered: true means the book was replayed — no REST seed needed.
    if (ctx.recovered) return;

    const { data } = await fetch(
      `https://api.sx.bet/orderbook-v3/snapshot/market?marketHash=${marketHash}`,
      { headers: { "x-sx-api-key": process.env.SX_API_KEY } }
    ).then((r) => r.json());

    // The snapshot goes through the same version rule, so a newer live update
    // that arrived during the fetch is not clobbered. `data` is flat — the same
    // shape a publication carries, with the level arrays beside `version`.
    applySnapshot(marketHash, data.version, data);
  });

  sub.subscribe();
  client.connect();
  ```

  ```python Python theme={null}
  import os
  import aiohttp
  from centrifuge import (
      PublicationContext,
      SubscribedContext,
      SubscriptionEventHandler,
  )

  class BookHandler(SubscriptionEventHandler):
      def __init__(self, market_hash: str) -> None:
          self.market_hash = market_hash

      async def on_subscribed(self, ctx: SubscribedContext) -> None:
          # recovered: True means the book was replayed — no REST seed needed.
          if ctx.recovered:
              return
          async with aiohttp.ClientSession() as session:
              async with session.get(
                  f"https://api.sx.bet/orderbook-v3/snapshot/market?marketHash={self.market_hash}",
                  headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
              ) as res:
                  body = await res.json()
          data = body["data"]
          # The snapshot runs through the same version rule as live updates, and
          # carries the same flat shape a publication does.
          apply_snapshot(self.market_hash, data["version"], data)

      async def on_publication(self, ctx: PublicationContext) -> None:
          apply_update(ctx.data)

  async def subscribe_book(client, market_hash: str) -> None:
      sub = client.new_subscription(
          f"orderbook_v3:{market_hash}", BookHandler(market_hash)
      )
      await sub.subscribe()
  ```
</CodeGroup>

## Cleanup

When you no longer need a subscription, clean it up to free resources:

<CodeGroup>
  ```javascript JavaScript theme={null}
  sub.unsubscribe();
  sub.removeAllListeners();
  client.removeSubscription(sub);
  ```

  ```python Python theme={null}
  await sub.unsubscribe()
  client.remove_subscription(sub)
  ```
</CodeGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.