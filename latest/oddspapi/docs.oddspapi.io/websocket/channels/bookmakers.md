> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Bookmakers Channel - Sportsbook Status & Metadata

> Stream bookmaker status per fixture via WebSocket. Track which sportsbooks offer odds, detect stale/suspended markets, and monitor participant rotation flags.

## What it streams

Metadata about which bookmakers are offering odds for a given fixture, and whether those odds are active, stale, suspended, or rotated.

This stream complements the `odds` channel by giving you per-bookmaker status for each `fixtureId`.

***

## Routing

* Entity key: `payload.fixtureId`
* Filters: `sportIds`, `tournamentIds`, `fixtureIds`, `bookmakers`
* Access: determined by your `apiKey`
* Bookmaker-gated: ✅ Yes

***

## Payload structure

| Field | Type | Description |
| - | - | - |
| `fixtureId` | `string` | The fixture this update applies to |
| `bookmakers` | `object` | Map of `<bookmaker>` → metadata object |
| `bookmakers.<bk>.bookmaker` | `string` | Bookmaker slug (e.g. `"stake"`, `"pinnacle"`) |
| `bookmakers.<bk>.bookmakerFixtureId` | `string \| null` | Optional bookmaker-side ID (may be a slug or compound string) |
| `bookmakers.<bk>.fixturePath` | `string \| null` | Optional bookmaker path or UI route (if supported) |
| `bookmakers.<bk>.hasOdds` | `boolean` | True if the bookmaker currently offers any odds |
| `bookmakers.<bk>.staleOdds` | `boolean` | **Critical for trading.** True if the connection to this bookmaker was lost and odds freshness can no longer be guaranteed |
| `bookmakers.<bk>.staleOddsResponseCode` | `number \| null` | **Deprecated.** HTTP status code returned during staleness check — rely on `staleOdds` instead |
| `bookmakers.<bk>.suspended` | `boolean` | True if this bookmaker's odds are suspended |
| `bookmakers.<bk>.participantsRotated` | `boolean` | True if this bookmaker's home/away assignment differs from the OddsPapi baseline (see below) |
| `bookmakers.<bk>.meta` | `object \| null` | Optional metadata (bookmaker-specific) |
| `bookmakers.<bk>.updatedAt` | `string` | Last update timestamp (ISO 8601) |

***

## Example message

```json theme={null}
{
  "channel": "bookmakers",
  "type": "UPDATE",
  "payload": {
    "fixtureId": "id1000070367118324",
    "bookmakers": {
      "draftkings": {
        "bookmaker": "draftkings",
        "bookmakerFixtureId": "33999242",
        "fixturePath": "https://sportsbook.draftkings.com/event/33999242",
        "hasOdds": true,
        "staleOdds": false,
        "staleOddsResponseCode": null,
        "suspended": false,
        "participantsRotated": false,
        "meta": null,
        "updatedAt": "2026-04-21T00:51:48.283641+00:00"
      }
    }
  },
  "ts": 1776729708000,
  "entryId": "1776729708000-1"
}
```

***

## Critical fields for automated trading

### `staleOdds` — odds freshness guarantee

> [!WARNING]
>
> If you are running automated trading or arbitrage strategies on top of OddsPapi, `staleOdds` is one of the most important fields you must check.

When `staleOdds = true`, the connection between OddsPapi and this bookmaker has been **lost or interrupted**. This means:

* Odds you received earlier **may no longer reflect the bookmaker's current prices**
* No further updates can be guaranteed until the connection is restored
* You should **immediately pause** any automated betting or arbitrage logic for this bookmaker
* Treat all existing odds from this bookmaker as **unvalidated** until `staleOdds` returns to `false`

The flag is always keyed **per fixture × bookmaker** and set deliberately — lost contact (block, maintenance, downtime), unusual inactivity across the book, or a fixture mismapping. The same `bookmakers` map is also carried on the REST responses (`/fixtures`, `/fixtures/odds`, and related endpoints), so snapshot and stream always agree on staleness. Causes, fan-out behavior, and treatment in full: [Bookmaker staleness](/api-reference/reliability.md#bookmaker-staleness-staleodds).

Recovery is automatic: when contact with the bookmaker is restored the flag returns to `false`, and because every odds update is full latest state, the next updates re-validate your store on their own — there is nothing to replay or reconcile manually.

`staleOdds` is also distinct from `suspended`: `suspended` is the **bookmaker's own action** on the fixture, while `staleOdds` means **our view of the bookmaker** is interrupted. Either can be true without the other, and both independently mean "do not trade this book right now".

> [!NOTE]
>
> `staleOddsResponseCode` is **deprecated**: it exposed the HTTP status code (if any) seen during the staleness check — do not build new logic on it.

### `participantsRotated` — home/away mapping

When `participantsRotated = true`, the bookmaker treats a **different team as the home team** than OddsPapi does. In other words, `participant1` (home) and `participant2` (away) are **swapped** at this bookmaker compared to the OddsPapi baseline fixture.

This is critical for:

* **Moneyline / 1x2 markets** — the "home" and "away" outcome meanings are reversed
* **Spread / handicap markets** — the sign of the spread is flipped
* **Any automated strategy** that maps outcomes by participant position rather than participant ID

If you build on top of OddsPapi, always check this flag and **swap your participant mapping** for this bookmaker when it is `true`.

***

## Notes

* If a bookmaker is not present in `bookmakers`, assume **no current odds available** for that fixture.
* Use this channel as a **pre-filter** before processing any odds from the `odds` channel.
* Combine `staleOdds`, `suspended`, and `hasOdds` to determine whether a bookmaker's odds should be trusted at any given moment.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.