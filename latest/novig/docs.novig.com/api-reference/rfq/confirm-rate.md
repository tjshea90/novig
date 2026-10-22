> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Confirm rate

> How many last looks you must confirm, and what happens when you fall short.

When your quote wins and the user accepts, we ask you to confirm the trade.
That 1-second step is the last look. The [overview](/api-reference/rfq/overview.md) shows where it fits in a round.

You must confirm at least 60% of your last looks.
If you fall below that, we lock you out of RFQ quoting for 5 minutes.

## How we count

We count your last looks from the past 30 minutes.

| How the last look ended | Counts toward your rate |
| - | - |
| You confirmed in time | Yes, as a confirm |
| You rejected | Yes, against you |
| You didn't answer within 1 second | Yes, against you |
| Your webhook returned an error or bad JSON | Yes, against you |
| We failed on our side | No |

We judge you only after at least 20 last looks count.
If fewer than 60% of them are confirms, we lock you out.

## While you're locked out

We refuse every quote, decline, and confirm you send.

Over the [WebSocket](/api-reference/rfq/websocket.md), each one gets a `FORBIDDEN` error.
Its `message` says when the lock ends and that your confirm rate caused it:

```json theme={"dark"}
{
  "event": "error",
  "data": {
    "code": "FORBIDDEN",
    "message": "Maker is locked out of RFQ quoting until 2026-10-06T18:28:00Z for a low confirm rate.",
    "rfq_id": "0193abcd-0000-7000-8000-000000000002"
  },
  "timestamp": "2026-10-06T18:24:10.000Z"
}
```

Over a [webhook](/api-reference/rfq/webhooks.md), we keep sending you RFQs but drop your quotes.
Check `GET /rfq/pricer` to see the lock.

The lock ends on its own after 5 minutes. You don't need to do anything to lift it.

## After a lock ends

We start counting from zero.
Last looks from before the lock ends never count again, so one bad stretch can't lock you twice.

## Check where you stand

`GET /rfq/pricer` returns your current count and any live lock.
Here the maker from the example above is still locked out:

```json theme={"dark"}
{
  "pricerId": "0193abcd-0000-7000-8000-000000000001",
  "webhookUrl": "https://pricer.example.com/novig",
  "confirmStanding": {
    "lookbackMinutes": 30,
    "counted": 0,
    "confirmed": 0,
    "threshold": "0.60",
    "minSample": 20
  },
  "lock": {
    "until": "2026-10-06T18:28:00Z",
    "source": "confirm_rate"
  }
}
```

| Field | Holds |
| - | - |
| `confirmStanding.lookbackMinutes` | How far back we count, in minutes |
| `confirmStanding.counted` | Last looks that count toward your rate |
| `confirmStanding.confirmed` | Last looks you confirmed |
| `confirmStanding.threshold` | The share you must confirm, as a decimal string |
| `confirmStanding.minSample` | The count we need before we judge you |
| `lock` | Your live lock, or `null` |
| `lock.until` | When the lock ends, or `null` if it has no set end |
| `lock.source` | `confirm_rate` for a lock from this check, `admin` for one we set by hand |

While a confirm-rate lock is live, `counted` is `0`, because counting restarts when it ends.

> [!NOTE]
>
> We can also lock a pricer by hand. That lock can last longer, and its `source` is `admin`. Contact us if you see one.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.