> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Line Changes

> Subscribe to real-time line changes

Subscribe to all line changes. Messages are sent for particular combinations of event IDs and market types. Note that only market types with lines will have updates sent. See [the market types section](/api-reference/market-types) for details on which types have lines.

**CHANNEL NAME**

`main_line:global`

**MESSAGE PAYLOAD FORMAT**

| Name | Type | Description |
| - | - | - |
| marketHash | string | The market which is now the main line for this event ID |
| marketType | number | The type of market this update refers to |
| sportXeventId | string | The event ID for this update |
| updateTime | string | Millisecond epoch of the change, stringified. Compare as an integer |

To get the actual line, fetch the market using the `marketHash`.

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const sub = client.newSubscription("main_line:global");

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
      client = Client(
          "wss://realtime.sx.bet/connection/websocket",
          token="YOUR_TOKEN",  # from /user/realtime-token-v3/api-key
      )
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      sub = client.new_subscription("main_line:global", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
[
  {
    "sportXeventId": "L12003787",
    "marketType": 3,
    "marketHash": "0x38cceead7bda65c18574a34994ebd8af154725d08aa735dcbf26247a7dcc67bd",
    "updateTime": "1785765520299"
  }
]
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.