> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Settlement & Grading - Per-Outcome Grades, Quarter Lines & Periods

> Grade bets on OddsPapi: per-outcome settlement grades on frozen IDs, Asian quarter-line half-stake math, period-market grading, and handling UNDECIDED and CANCELLED.

Settlement closes the pipeline: the same IDs you priced and bet on come back graded, so grading is a lookup on keys you already store — not a rules engine you build and maintain per sport.

> [!NOTE]
>
> OddsPapi aggregates bookmaker prices; it is not an official or licensed league-data provider. Settlement is derived from resolved cross-source facts, and **futures settlement is rolling out** — the endpoint exists but does not yet serve results.

***

## One request, every grade

`GET /fixtures/settlement` takes a `fixtureId` (plus optional `outcomeId` / `playerId` to narrow) and returns the fixture's final state with one row per outcome:

```json theme={null}
"scores": {
  "result": { "period": "result", "participant1Score": 23, "participant2Score": 30 }
},
"settlements": [
  {
    "marketId": 141,
    "marketType": "1x2",
    "outcomeId": 142,
    "playerId": 0,
    "status": "WIN",
    "team1Score": 23,
    "team2Score": 30,
    "periods": ["fulltime"],
    "margin": 7.0
  },
  {
    "marketId": 141,
    "marketType": "1x2",
    "outcomeId": 141,
    "playerId": 0,
    "status": "LOSE",
    "team1Score": 23,
    "team2Score": 30,
    "periods": ["fulltime"],
    "margin": -7.0
  }
]
```

Every row carries its evidence: `team1Score` / `team2Score` are the scores the grade was computed from, `periods` names the period rows used, and `margin` is the winning margin where one applies — enough to audit any grade without re-deriving it.

Call it once the fixture reaches `statusId` `2` (finished). Rows that cannot be graded yet come back as `UNDECIDED` rather than being omitted — see [below](#undecided-and-cancelled-are-states-not-errors).

***

## Grade on the selection, pay on the grade

Bets grade on the bookmaker-independent **selection** key — `{fixtureId}:{outcomeId}:{playerId}` — because a selection resolves identically regardless of who quoted it. Drop the bookmaker segment from the `oddsId` you stored at bet time and the join is done.

What each grade pays, for a 100 stake at 2.00:

| Grade | Meaning | Returns |
| - | - | -: |
| `WIN` | Full win | 200 |
| `LOSE` | Full loss | 0 |
| `PUSH` | Void / tie on the line — stake returned | 100 |
| `HALFWIN` | Half wins, half returned | 150 |
| `HALFLOSS` | Half loses, half returned | 50 |
| `CANCELLED` | Market voided — stake returned | 100 |
| `UNDECIDED` | Not gradable yet — carries a `reason` | — |

The vocabulary is defined in [Enumerations → settlementStatus](/api-reference/enumerations.md#settlementstatus); new enum values are appended, never repurposed, so treat unknown grades as "hold", not as errors.

***

## Quarter lines: where the half-stake grades come from

An Asian quarter line is two bets in one — Over 2.25 is half your stake on Over 2.0 and half on Over 2.5. The settlement endpoint grades the composite directly instead of making you split it:

* **Over 2.25, match ends with 2 goals** — the Over 2.0 half pushes, the Over 2.5 half loses → `HALFLOSS`. A 100 stake gets 50 back.
* **Under 2.25, match ends with 2 goals** — the Under 2.0 half pushes, the Under 2.5 half wins → `HALFWIN`. A 100 stake at 1.90 returns 50 + 50 × 1.90 = **145**.

Each quarter line is its own `marketId` like every other line value, so Over 2.25 and Over 2.5 never mix in one market. See [sharp-line trading](/guides/sharp-line-trading.md) for pricing these markets and [Market coverage](/coverage/markets.md) for where they appear.

***

## Period markets grade on period rows

Period markets — first-half totals, first-set winner, race to a score — grade against the per-period score rows (`p1`, `p2`, …), not against the full-time `result`. The `periods` array on each settlement row names which rows were used.

Interpret periods against the sport's structure: `p1` is a half in soccer, a quarter in NBA basketball, a set in tennis — `expectedPeriods` and `periodLength` on the fixture tell you which, and the full key vocabulary (including combined segments like `fulltime+overtime` and tennis sub-period keys) is in [Enumerations → period](/api-reference/enumerations.md#period).

***

## `UNDECIDED` and `CANCELLED` are states, not errors

Both carry a human-readable `reason` explaining why, and they mean different things operationally:

* **`UNDECIDED`** — the result data is insufficient or pending. Keep the bet in your settlement queue and re-check later; do not retry in a tight loop — re-poll on a schedule or when the fixture updates.
* **`CANCELLED`** — the market is void and the stake is returned. This is terminal: pay out the refund and close the bet. A fixture moving to `statusId` `3` (cancelled) is the common cause, but individual markets can cancel on their own.

Treat both as explicit states in your settlement queue rather than retry-until-success, and log the `reason` — it is the audit trail for why a bet paid the way it did.

***

## Futures

`GET /futures/settlement` exists but returns `501` — futures settlement is rolling out and not yet serving results. Fixture settlement is unaffected. Until it lands, outright markets need grading from your own result source.

***

## What you can rely on

* **Grades land on frozen IDs.** The `marketId` / `outcomeId` you priced is the one that comes back graded — no name matching, no per-book translation, no remapping when a book renames a market.
* **Quarter lines settle exactly.** `HALFWIN` / `HALFLOSS` are explicit grades with the split-stake math above — never rounded to the nearest full grade.
* **Nothing disappears silently.** Ungradable rows come back `UNDECIDED` with a `reason`; voids come back `CANCELLED` with a `reason`. Every bet in your queue reaches a terminal, explained state.
* **The pipeline closes on one key.** The same `oddsId` addresses the live price, its [history and closing line](/api-reference/concepts.md#historical-odds-and-clv), and — minus the bookmaker segment — its grade.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.