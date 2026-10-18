> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Event Order Book Updates

> Subscribe to real-time order book updates for every market on an event

Subscribe to order book changes for every market on a single event. The payloads are identical to [`orderbook_v3:{marketHash}`](/api-reference/channel-orderbook-v3), including `marketHash` — this channel exists so that watching a whole event costs one channel instead of one per market.

This namespace keeps the last 100 publications for 5 minutes, so pass `recoverable: true` to recover missed messages on reconnect. If a gap comes back `recovered: false`, re-seed from [`GET /orderbook-v3/snapshot/event`](/api-reference/get-event-orderbook-snapshot) — follow `nextKey` until it is omitted.

**CHANNEL NAME FORMAT**

`orderbook_v3_event:{eventId}`

| Name | Type | Description |
| - | - | - |
| eventId | string | The event to subscribe to. Receives updates for all markets under this event. The same identifier markets report as `sportXeventId` |

**MESSAGE PAYLOAD FORMAT**

Identical to [`orderbook_v3:{marketHash}`](/api-reference/channel-orderbook-v3), including the `version` field. `version` is per market, so key your state by `marketHash` and apply a publication only when its `version` is strictly greater than the one you hold for that market — see [book versioning](/developers/book-versioning).

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const eventId = "L12003787";
  const sub = client.newSubscription(`orderbook_v3_event:${eventId}`, { recoverable: true });

  sub.on("publication", (ctx) => {
    const data = ctx.data;
    // message handler logic — key state by data.marketHash
  });

  sub.subscribe();
  ```

  ```python Python theme={null}
  import asyncio
  from centrifuge import Client, PublicationContext, SubscriptionEventHandler

  async def on_publication(ctx: PublicationContext) -> None:
      print(ctx.data)

  async def main():
      event_id = "L12003787"
      client = Client(
          "wss://realtime.sx.bet/connection/websocket",
          token="YOUR_TOKEN",  # from /user/realtime-token-v3/api-key
      )
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      sub = client.new_subscription(f"orderbook_v3_event:{event_id}", handler)
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