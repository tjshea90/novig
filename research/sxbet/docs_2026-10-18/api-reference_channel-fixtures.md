> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Live Score & Fixture Updates

> Subscribe to real-time live scores and fixture updates

Two channels carry fixture-related data. Subscribe to one or both depending on your use case.

***

## Live Scores

Subscribe to live score updates across all active events.

**CHANNEL NAME**

`fixtures:live_scores`

**MESSAGE PAYLOAD FORMAT**

| Name | Type | Description |
| - | - | - |
| teamOneScore | number | The current score for the home team (`teamOneName` in the `Market` object) |
| teamTwoScore | number | The current score for the away team (`teamTwoName` in the `Market` object) |
| sportXeventId | string | The event ID for this update |
| currentPeriod | string | An identifier for the current period |
| periodTime | string | The current time for the period. `"-1"` if not applicable (e.g. tennis) |
| sportId | number | The sport ID for this market |
| leagueId | number | The league ID for this market |
| periods | `Period[]` | Individual period information |
| extra | string | JSON-encoded extra data for this live score update |

Where a `Period` object looks like:

| Name | Type | Description |
| - | - | - |
| label | string | The period name |
| isFinished | boolean | `true` if the period is over |
| teamOneScore | string | The score of team one |
| teamTwoScore | string | The score of team two |

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const sub = client.newSubscription("fixtures:live_scores");

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
      sub = client.new_subscription("fixtures:live_scores", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "teamOneScore": 2,
  "teamTwoScore": 1,
  "sportXeventId": "L7178624",
  "currentPeriod": "4th Set",
  "periodTime": "-1",
  "sportId": 6,
  "leagueId": 1263,
  "periods": [
    {
      "label": "1st Set",
      "isFinished": true,
      "teamOneScore": "4",
      "teamTwoScore": "6"
    },
    {
      "label": "4th Set",
      "isFinished": false,
      "teamOneScore": "1",
      "teamTwoScore": "2"
    }
  ],
  "extra": "..."
}
```

***

## Fixture Updates

Subscribe to fixture state changes across all events.

**CHANNEL NAME**

`fixtures:global`

**MESSAGE PAYLOAD FORMAT**

| Name | Type | Description |
| - | - | - |
| sportXeventId | string | The event ID for this update |
| status | number | The fixture status. See [fixture statuses](/api-reference/fixture-statuses) |

If a status change means you need the affected markets, look them up by event ID.

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const sub = client.newSubscription("fixtures:global");

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
      sub = client.new_subscription("fixtures:global", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "sportXeventId": "L12003787",
  "status": 2
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.