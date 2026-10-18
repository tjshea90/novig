> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Order Book Updates

> Subscribe to real-time changes in a market's order book

Subscribe to changes in a particular order book. You will receive updates when orders are posted, cancelled, expire, or are filled. Every publication is the complete resting book for that market, not a delta — replace your state for that market rather than patching it.

Each payload carries a `version` (see below). Apply a publication only when its `version` is strictly greater than the one you hold for that market — see [book versioning](/developers/book-versioning).

<Tip>
  Watching many markets on the same events? Subscribe to
  [`orderbook_v3_event:{eventId}`](/api-reference/channel-orderbook-v3-event) instead.
</Tip>

**CHANNEL NAME FORMAT**

`orderbook_v3:{marketHash}`

| Name | Type | Description |
| - | - | - |
| marketHash | string | The market to subscribe to |

**MESSAGE PAYLOAD FORMAT**

| Name | Type | Description |
| - | - | - |
| marketHash | string | The market this book belongs to |
| eventId | string | The event this market belongs to |
| version | string | A single string that increases as the book changes. Apply an update only when it is strictly greater than the one you hold — see [book versioning](/developers/book-versioning) |
| outcomeOne | `Level[]` | Resting levels on outcome one, sorted best first |
| outcomeTwo | `Level[]` | Resting levels on outcome two, sorted best first |

Where a `Level` object looks like:

| Name | Type | Description |
| - | - | - |
| percentageOdds | string | The odds the `maker` receives in the sx.bet protocol format. To convert to implied odds divide by 10^20. To get taker implied odds: `takerOdds = 1 - percentageOdds / 10^20`. See [unit conversions](/developers/unit-conversions). |
| size | string | Aggregate remaining maker size at that level, in base units. See [unit conversions](/developers/unit-conversions). |

There is no `showTakerPerspective` parameter on this channel — levels are always the maker frame, unlike [`GET /orderbook-v3/snapshot/market`](/api-reference/get-market-orderbook-snapshot), which accepts it.

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const marketHash = "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb";
  const sub = client.newSubscription(`orderbook_v3:${marketHash}`, { recoverable: true });

  sub.on("publication", (ctx) => {
    const data = ctx.data;
    // message handler logic
  });

  sub.subscribe();
  ```

  ```python Python theme={null}
  import asyncio
  from centrifuge import Client, PublicationContext, SubscriptionEventHandler

  async def on_publication(ctx: PublicationContext) -> None:
      print(ctx.data)

  async def main():
      market_hash = "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb"
      client = Client(
          "wss://realtime.sx.bet/connection/websocket",
          token="YOUR_TOKEN",  # from /user/realtime-token-v3/api-key
      )
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      sub = client.new_subscription(f"orderbook_v3:{market_hash}", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "marketHash": "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb",
  "eventId": "L13058397",
  "version": "00100000000000010015000",
  "outcomeOne": [{ "percentageOdds": "40000000000000000000", "size": "1000000" }],
  "outcomeTwo": []
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.