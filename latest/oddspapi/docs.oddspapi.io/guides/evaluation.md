> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Evaluating OddsPapi - Measure Latency, Coverage & Recovery Yourself

> A trial protocol for OddsPapi: log per-book observation delay against the published numbers, drill the recovery paths, verify coverage from the API, and settle the speed question with CLV.

We publish measured per-book delay numbers and every update carries the timestamps to check them. So don't take the numbers on trust — this page is the protocol for a trial that ends with your own distributions instead of an impression.

***

## What every update lets you measure

Two metrics, judged separately because they have different owners:

| Metric | Compute it as | Who owns it |
| - | - | - |
| Observation delay | `changedAt − bookmakerChangedAt` | **Us**, per book — this is the number we publish |
| Delivery hop | `your receipt − ts` | **You** — it depends on where you deploy |

A slow reading on the second metric says nothing about the first; measure both before attributing anything. Both compare your clock to ours, so run NTP before trusting sub-100 ms readings. The full stamp chain is in [Latency and freshness](/api-reference/reliability.md#latency-and-freshness), and deployment placement in [Server location](/api-reference/concepts.md#server-location-matters).

***

## The protocol

**1. Scope it to what you trade**

Log in with `sportIds`, `tournamentIds`, and `bookmakers` filters for the books and sports that will actually carry your volume. Subscribe to [`bookmakers`](/websocket/channels/bookmakers) alongside `odds` — you will want the staleness signal in the data you collect.

**2. Log raw, aggregate later**

Per update, record the bookmaker, `oddsId`, both deltas, and whether the fixture was live. Aggregate into per-book percentiles (p50 / p95), split live vs. pregame — the published numbers are per delivery mode, so your comparison should be too.

**3. Compare against what we publish**

Per book: your measured p50 against the representative p50s in [Latency and freshness](/api-reference/reliability.md#latency-and-freshness), and your tail against the `maxDelayLiveInSec` / `maxDelayPregameInSec` bounds on [`GET /bookmakers`](/api-reference/common/get-bookmakers). Those bounds are the claim; hold us to them.

**4. Drill the failure paths**

Speed differences between feeds show up in outages more than in steady state — see the drills below.

**5. Race it against your incumbent**

Map fixtures once via `externalProviders`, compare on the selection key, and time both feeds on the same line moves — the mapping and method are in [running a second feed](/guides/second-feed.md#measure-which-feed-is-faster).

***

## Drill the failure paths

Steady-state latency is the easy part; recovery behavior is what actually differs between feeds in production. Three drills, all safe to run during a trial:

* **Resume** — kill the connection for a few seconds, reconnect with your `serverEpoch` + per-channel `lastSeenId`, and verify the missed updates replay with no gap. This is the designed path for short interruptions.
* **Re-snapshot** — stay down past the replay window (`resumeWindowMs`) so the gateway emits `snapshot_required`, then time a full recovery: one REST snapshot, merged by newest `changedAt` per key. That timing is your worst-case bound — confirm it is one you can live with.
* **Staleness accounting** — over the trial, count `staleOdds` episodes and their durations per book. Loss of upstream contact is reported explicitly rather than hidden; a feed that never shows staleness is not necessarily fresher, it may just not be telling you.

Mechanics for the first two are in [Resume & Replay](/websocket/resume-replay.md); the flags are in [Status signals](/coverage/bookmakers.md#status-signals).

***

## Coverage, from the API

Verify coverage against your requirements list from live responses rather than a brochure:

* **Books your key can see** — enumerate [`GET /bookmakers`](/api-reference/common/get-bookmakers); access is permissioned per key, so this is the authoritative list for *your* contract.
* **Markets per sport** — `GET /markets?sportId=…` returns every market and outcome you can be quoted on, line by line.
* **Schedule depth** — pull `GET /fixtures` for a representative week in the competitions you need and diff it against your own required schedule.

The catalog shape is described in [Coverage](/coverage.md), [Bookmaker coverage](/coverage/bookmakers.md), and [Market coverage](/coverage/markets.md).

***

## The outcome-level verdict

Per-update stopwatch races answer "who saw it first"; closing line value answers whether that speed pays. At the end of the trial, join the prices you would have traded on against the closing line:

```json theme={null}
"id1400003160574219:pinnacle:14198:0": {
  "olv": { "price": 1.952, "changedAt": 1765975761422 },
  "clv": { "price": 1.952, "changedAt": 1766361894121 }
}
```

`GET /fixtures/odds/clv` returns the opening and closing price per `oddsId` — the same key as the live stream, so the join with your trial log is free. Beating the close on the lines you flagged during the trial is the evaluation result that survives contact with production. Method in [sharp-line trading & CLV](/guides/sharp-line-trading.md).

***

## What a finished evaluation looks like

* Per-book observation-delay distributions, live and pregame, next to the published p50s and bounds — matching or not.
* Measured resume and re-snapshot timings, so worst-case recovery is a number, not a hope.
* A coverage diff against your requirements, from live API responses.
* A CLV join on the lines you would have traded.

If your measurements disagree with the published numbers, we want to know: send the affected books, UTC timestamps, and your `serverEpoch` / `entryId` values to [contact@55-tech.com](mailto:contact@55-tech.com) so we can trace the session. Trial access is arranged through the same address.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.