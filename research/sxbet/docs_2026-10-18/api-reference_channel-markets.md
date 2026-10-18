> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Market Updates

> Subscribe to real-time market changes

Subscribe to all changes in markets on sx.bet. You will get updates when:

* A new market is added
* A market is removed (set to `INACTIVE`)
* A market's fields have changed (for example, game time has changed or the market has settled)

**CHANNEL NAME**

`markets:global`

**MESSAGE PAYLOAD FORMAT**

See [the markets section](/api-reference/get-markets-active) for the format of the message.

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const sub = client.newSubscription("markets:global");

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
      sub = client.new_subscription("markets:global", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
[
  {
    "status": "ACTIVE",
    "marketHash": "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb",
    "outcomeOneName": "Cf Montreal",
    "outcomeTwoName": "The Field",
    "outcomeVoidName": "NO_CONTEST",
    "teamOneName": "Cf Montreal",
    "teamTwoName": "The Field",
    "participantOneId": 53730576,
    "participantTwoId": null,
    "type": 274,
    "gameTime": 2208988800,
    "line": null,
    "reportedDate": null,
    "outcome": null,
    "teamOneScore": null,
    "teamTwoScore": null,
    "sportXeventId": "L12003787",
    "liveEnabled": false,
    "sportLabel": "Soccer",
    "sportId": 5,
    "leagueId": 1115,
    "leagueLabel": "Major League Soccer",
    "group1": "Major League Soccer"
  }
]
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.