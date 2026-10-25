> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Bookmakers Futures Channel - Outright Market Status

> Stream bookmaker availability for futures markets via WebSocket. Monitor which sportsbooks offer outright and season-long betting odds.

## What it streams

Metadata per bookmaker describing whether a bookmaker offers odds for a given future, and whether those odds are active, stale, or suspended.

This is the **future equivalent** of the `bookmakers` channel for fixtures.

***

## Routing

* Entity key: `payload.futureId`
* Filters: `sportIds`, `tournamentIds`, `futureIds`, `bookmakers`
* Access: determined by your `apiKey`
* Bookmaker-gated: ✅ Yes

***

## Payload structure

| Field | Type | Description |
| - | - | - |
| `futureId` | `string` | The future this update applies to |
| `bookmakers` | `object` | Map of `<bookmaker>` → metadata object |
| `bookmakers.<bk>.bookmaker` | `string` | Bookmaker slug (e.g. `"stake"`, `"pinnacle"`) |
| `bookmakers.<bk>.bookmakerFutureId` | `string \| null` | Optional bookmaker-side identifier for this future |
| `bookmakers.<bk>.futurePath` | `string \| null` | Optional deeplink or path to the future at the bookmaker |
| `bookmakers.<bk>.hasOdds` | `boolean` | True if the bookmaker currently offers odds |
| `bookmakers.<bk>.staleOdds` | `boolean` | **Critical for trading.** True if the connection to this bookmaker was lost and odds freshness can no longer be guaranteed |
| `bookmakers.<bk>.suspended` | `boolean` | True if the bookmaker has suspended this future |
| `bookmakers.<bk>.meta` | `object \| null` | Optional bookmaker-specific metadata |
| `bookmakers.<bk>.updatedAt` | `string` | Last update timestamp (ISO 8601) |

***

## Example message

```json theme={null}
{
  "channel": "bookmakersFutures",
  "type": "UPDATE",
  "payload": {
    "futureId": "id11028543137888",
    "bookmakers": {
      "stake": {
        "bookmaker": "stake",
        "bookmakerFutureId": "285431-nbl-new-zealand",
        "futurePath": null,
        "hasOdds": true,
        "staleOdds": false,
        "suspended": false,
        "meta": null,
        "updatedAt": "2025-12-28T18:37:13.719926+00:00"
      }
    }
  },
  "ts": 1766947033889,
  "entryId": "1766947033889-501"
}
```

***

## Critical fields for automated trading

### `staleOdds` — odds freshness guarantee

> [!WARNING]
>
> If you are running automated trading strategies on top of OddsPapi, `staleOdds` is one of the most important fields you must check.

When `staleOdds = true`, the connection between OddsPapi and this bookmaker has been **lost or interrupted**. This means:

* Odds you received earlier **may no longer reflect the bookmaker's current prices**
* No further updates can be guaranteed until the connection is restored
* You should **immediately pause** any automated betting logic for this bookmaker
* Treat all existing odds from this bookmaker as **unvalidated** until `staleOdds` returns to `false`

***

## Notes

* Odds data itself is streamed via **`oddsFutures`**
* If a bookmaker is missing from `bookmakers`, assume **no current odds**
* Use this channel as a **pre-filter** before processing any odds from the `oddsFutures` channel
* Combine `staleOdds`, `suspended`, and `hasOdds` to determine whether a bookmaker's odds should be trusted
* Semantics are intentionally identical to `bookmakers` (fixture-level), just scoped to `futureId`


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.