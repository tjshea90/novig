> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Powering a Sportsbook - Schedule, Prices, In-Play & Settlement

> Build a full betting product on OddsPapi: discover sports and tournaments, load schedules, model markets and outcomes, stream prices and in-play state, and settle bets.

Clients build entire betting products on OddsPapi, not just price displays. This guide walks the full pipeline — discovery, schedule, markets, prices, in-play, settlement — and names the endpoint or channel for each stage.

> [!NOTE]
>
> OddsPapi aggregates bookmaker prices; it is not an official or licensed league-data provider. Settlement is derived from resolved cross-source facts, and **futures settlement is rolling out** — the endpoint exists but does not yet serve results.

***

## The pipeline

**1. Discover the catalog**

`GET /sports`, `/tournaments`, `/seasons`, `/participants`, `/players`, `/venues` — the static tree your product is built on. Cache it; it changes slowly.

**2. Load the schedule**

`GET /fixtures`, `/fixtures/today`, `/fixtures/live` for snapshots, then the [`fixtures`](/websocket/channels/fixtures) channel for status and schedule changes.

**3. Model markets**

`GET /markets?sportId=…` gives every market and its outcomes for a sport — the offer you can present.

**4. Price it**

The [`odds`](/websocket/channels/odds) channel for realtime updates, hydrated from `GET /fixtures/odds` (or `/fixtures/odds/main` for main lines only) once the stream is flowing — see Operating notes for the order.

**5. Run it in-play**

[`scores`](/websocket/channels/scores) and [`clocks`](/websocket/channels/clocks) for live state, plus [`bookmakers`](/websocket/channels/bookmakers) for suspension and staleness.

**6. Settle**

`GET /fixtures/settlement` for per-outcome grades, final scores, and margins.

***

## Markets: address by coordinates, not by name

Every selection decomposes the same way on every bookmaker and every fixture:

```
marketType → period → handicap (line value) → side   [+ optional player]
```

That decomposition is baked into the `outcomeId`, which means the same bet always resolves to the same `marketId` / `outcomeId`. Two rules matter when you build the offer:

* **Each distinct line value is its own `marketId`.** Within one `marketId` the outcomes are only the sides of that exact line.
* **`marketId` equals the market's first `outcomeId`**, and `marketLength` is the number of sides — together a complete probability space, which is what you need for margin application and completeness checks.

```json theme={null}
{
  "marketId": 113,
  "marketLength": 3,
  "sportId": 11,
  "playerProp": false,
  "handicap": 0.0,
  "period": "fulltime",
  "marketType": "1x2",
  "marketName": "Regular Time Result",
  "marketNameShort": "1X2",
  "outcomes": [
    { "outcomeId": 113, "outcomeName": "1" },
    { "outcomeId": 114, "outcomeName": "X" },
    { "outcomeId": 115, "outcomeName": "2" }
  ]
}
```

Use `marketName` / `marketNameShort` for display and `marketId` / `outcomeId` for logic. Full detail in [Markets and outcomes](/api-reference/concepts.md#markets-and-outcomes) and [Market coverage](/coverage/markets.md).

***

## Localization

All endpoints are prefixed with a language code (`/en/…`, `/de/…`, `/fr/…`), and the WebSocket takes `lang` at login. Translated fields — `sportName`, `marketName`, `statusName`, participant names — follow the prefix; identifiers never do. Build your UI on IDs and let names follow the user's locale.

For crypto or multi-currency display, the [`currencies`](/websocket/channels/currencies) channel streams fiat and crypto rates against USD.

***

## In-play

* **Scores are per period**, keyed `result`, `p1`, `p2`, … — `result` is the authoritative current score, period rows let you settle period markets.
* **`statusId`** moves forward only: `0` pregame → `1` live → `2` finished, or any state → `3` cancelled. Branch on the ID, not the name.
* **`clocks`** carries `currentPeriod`, `currentTime`, `remainingTime`, and `stopped` for live display and for holding bets during stoppages.
* **Suspension** comes from the `bookmakers` channel (`suspended`, `staleOdds`) and from `marketActive` / `active` on the odds themselves. Wire all of them into one "can this be bet right now" decision.

Interpret periods against the sport's structure — `p1` is a half in soccer, a quarter in NBA basketball, a set in tennis. `expectedPeriods` and `periodLength` on the fixture tell you which. The vocabulary is shared across all sports and is append-only; see [Enumerations → period](/api-reference/enumerations.md#period).

***

## Settlement

`GET /fixtures/settlement` takes a `fixtureId` and optional `outcomeId` / `playerId`, and returns the fixture's final state alongside per-outcome grades:

| Value | Meaning |
| - | - |
| `WIN` / `LOSE` | Clean win or loss |
| `PUSH` | Void / tie on the line — stake returned |
| `HALFWIN` / `HALFLOSS` | Asian quarter-line split stake |
| `CANCELLED` | Market voided — stake returned |
| `UNDECIDED` | Not gradable yet; carries a `reason` |

Grade on the **selection** key — `{fixtureId}:{outcomeId}:{playerId}`, without the bookmaker — since a selection resolves identically regardless of who quoted it. Handle `UNDECIDED` and `CANCELLED` as explicit states in your settlement queue rather than retry-until-success. Full treatment — quarter-line math, period grading, and queue handling — in [Settlement & grading](/guides/settlement.md).

***

## Operating notes

* **Connect first, then snapshot, then re-snapshot on signal.** Log in and start applying updates before you fetch the REST snapshot, and merge by newest `changedAt` per key — that overlap is what makes hydration gapless. `snapshot_required` means your cursor left the replay window; it is a designed path, not an error. See [Resume & Replay](/websocket/resume-replay.md).
* **Filter at login.** `sportIds`, `tournamentIds`, and `bookmakers` cut message volume far more effectively than client-side filtering.
* **Use `since` for backfills** rather than full refetches, and key storage by `oddsId` for clean dedup.
* **Prefer `receiveType: "zstd"`** on `odds` at scale ([Compression](/websocket/compression.md)).
* **New enum values are appended, never repurposed** — ignore values you do not recognize instead of failing.

For pricing history, closing lines, and post-hoc analysis of your own book, see [Historical odds and CLV](/api-reference/concepts.md#historical-odds-and-clv).

***

## What you can rely on

* **Markets are addressed, not matched.** `marketType → period → handicap → side` resolves to the same `marketId` / `outcomeId` for every bookmaker, so there is no per-book name-string matching layer for you to own and repair each time a book renames a market.
* **Lines never merge.** Each distinct line value is its own `marketId`, so "Over 2.5" and "Over 3.5" cannot collapse into one market and quietly mis-price your book.
* **The pipeline closes.** The same `oddsId` addresses the live price, its historical timeline, its closing line, and its settlement result — one key from pricing through to grading.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.