> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Reliability & Operations - Uptime, Status & Incident Response

> How OddsPapi delivers reliable realtime data for trading: redundant architecture, live status page, recovery design, support response, and incident handling.

OddsPapi is built for teams that trade on the feed, so reliability is a first-class concern. This page explains how the platform is designed to stay available, how to monitor it, and how to reach us when something goes wrong.

> [!NOTE]
>
> Specific uptime targets, support response times, and remedies are defined per agreement for B2B customers. Contact [contact@55-tech.com](mailto:contact@55-tech.com) to discuss SLA terms for your use case.

***

## Live status & incident history

Real-time platform status, ongoing incidents, and historical uptime are published on our status page:

> **[OddsPapi Status Page](https://oddspapi-v5.instatus.com)**
>
> Live availability, incident reports, and uptime history — subscribe for updates.

Subscribe on the status page to be notified of incidents and scheduled maintenance.

***

## Architecture for availability

The platform is designed so that no single failure should interrupt your data flow:

* **WebSocket-first delivery** — a persistent gateway streams updates with minimal latency, avoiding the overhead and gaps of REST polling.
* **REST + WebSocket redundancy** — REST snapshots and the realtime stream are independent paths to the same data. If the stream drops, REST keeps your state current.
* **Resume & replay** — after a brief disconnect, clients resume from their last cursor and replay missed updates without a full refresh. See [Resume & Replay](/websocket/resume-replay.md).
* **Deterministic recovery** — when replay isn't possible, the gateway emits `snapshot_required` so clients rebuild state from a fast REST snapshot. Recovery is an explicit, designed path — not an error state.

***

## Latency and freshness

OddsPapi is built to compete on **measured end-to-end delay** — not throughput — and every odds update carries the timestamps to verify it:

| Stamp | Field | Meaning |
| - | - | - |
| t0 | `bookmakerChangedAt` | The bookmaker's own change timestamp (when provided) |
| t1 | `changedAt` | When OddsPapi observed the change at the bookmaker |
| t2 | `ts` (message envelope) | When the gateway sent the WebSocket message |
| t3 | — | Client receipt — measured on your side |

`changedAt − bookmakerChangedAt` gives the observation delay per update; comparing `ts` to your receipt time gives the delivery hop.

Per-bookmaker freshness bounds are published on [`GET /bookmakers`](/api-reference/common/get-bookmakers): `maxDelayLiveInSec`, `maxDelayPregameInSec`, and `maxDelayPregameMainInSec` state the maximum expected refresh delay per delivery mode for each book — measured from observed vs. bookmaker change times (currently the rolling p50) where the book publishes usable timestamps, estimated otherwise.

Representative rolling p50 observation delays (live values per book on `GET /bookmakers`):

| Bookmaker | In-play | Pregame | Mode |
| - | -: | -: | - |
| Polymarket | 0.05 s | 0.05 s | measured |
| Betfair Exchange | 0.10 s | 0.10 s | measured |
| BC.Game | 0.25 s | 0.50 s | measured |
| Pinnacle | 0.38 s | 0.38 s | measured |
| Singbet | \~1 s | \~8 s | estimated |

Downstream of observation, the internal pipeline adds \~30 ms (p50) to gateway egress; the delivery hop from `ts` to client receipt depends on where you deploy — as low as \~5 ms when colocated (see [server location guidance](/api-reference/concepts.md#server-location-matters)).

To verify these numbers yourself during a trial, see [evaluating the feed](/guides/evaluation.md); to benchmark them against a feed you already run, see [running OddsPapi as a second feed](/guides/second-feed.md); to trade on them, see [sharp-line trading & CLV](/guides/sharp-line-trading.md).

***

## Bookmaker staleness: `staleOdds`

Two different things can fail: the platform (gateway, your connection) and our link to each upstream bookmaker. `staleOdds` covers the second — the flag that says **this bookmaker's odds on this fixture can no longer be trusted**, even while everything else is healthy. Which is why a price can look immaculate and be untradeable at the same time — here in a REST snapshot (`GET /fixtures/odds`), where one response carries both maps; the [`bookmakers`](/websocket/channels/bookmakers) channel streams the same meta:

```json theme={null}
{
  "odds": {
    "singbet": {
      "id1100013273682356:singbet:111470:0": {
        "price": 1.952, "active": true, "marketActive": true,
        "changedAt": 1776717657402
      }
    }
  },
  "bookmakers": {
    "singbet": { "bookmaker": "singbet", "hasOdds": true, "suspended": false, "staleOdds": true }
  }
}
```

The price row is perfectly normal; the verdict sits in the map next to it. Whether you hydrate over REST or react to the stream, the flag is in hand at the same moment as the price.

#### What trips it

The flag is always set deliberately, in three situations:

1. **Lost contact** — a block, maintenance, or downtime at the bookmaker. We can no longer observe its prices at all.
2. **Unusual inactivity** — a watcher per bookmaker: if an entire book produces no odds movement for longer than its per-book threshold, all of its fixtures are marked stale out of caution. A genuinely quiet stretch (e.g. a pregame-only book overnight) can trip this legitimately — the flag then means we cannot currently distinguish "quiet" from "broken", which is exactly when you should not be trading it.
3. **Mismapping** — a fixture found mapped to the wrong bookmaker event is marked stale until it is remapped and the old prices are removed. In this case the prices are not old, they are **wrong** — which is why `staleOdds` is a hard block and not an advisory signal.

#### The level, and how incidents fan out

One rule: `staleOdds` lives at exactly **one level — fixture × bookmaker**. Never per market or selection (the flag covers *all* odds of that bookmaker on that fixture), and never as a single bookmaker-wide message (a book-wide incident is expressed as the flag on *every* fixture of that book). What you receive per cause:

| Underlying event | What arrives |
| - | - |
| One fixture mismapped | **1 message** — that fixture × that bookmaker |
| Book-wide block, maintenance, or inactivity | **One message per fixture of that book** — easily \~1,000 updates announcing the same incident |
| Platform issue (gateway, your connection) | Not signalled via `staleOdds` at all — that is `reconnect` / the status page |

Store and evaluate it keyed per `fixtureId:bookmaker` pair, exactly as delivered. If you want a book-wide view (e.g. "this book is down"), derive it on your side — say, the share of that book's fixtures currently flagged — rather than waiting for a book-level message that will never come.

#### What it is not

Not a suspension. `suspended` is the bookmaker's own action on the fixture, and `active` / `marketActive` on the odds row are the bookmaker's selection- and market-level stops. `staleOdds` is about **our visibility** — it can be true while all of those read fine, and either can be true without the other. Both independently mean "do not trade this book right now".

#### The treatment

* **Pause** trading and quoting on that bookmaker for that fixture.
* **Treat held prices as unvalidated.** Do not re-price from stale values.
* **Resume when the flag returns to `false`.** Recovery is automatic: every update is full latest state, so the next updates re-validate your store — nothing to replay or reconcile.
* **Or fall back.** If the book is a reference, not a venue — risk measurement at a sportsbook, pricing input for market making — switch to a still-live book of similar sharpness, and back when the flag clears.

The alternative — silently serving the last known price — is the failure mode that costs money. A feed that never shows staleness is not fresher; it is just not telling you.

***

## Designed for trading-grade failover

Recovery is bounded and predictable rather than best-effort:

* The gateway buffers a replay window (`resumeWindowMs`) so short interruptions resume with **no data loss**.
* If your cursor falls outside that window, a single REST snapshot restores full state in one call.
* Bookmaker connectivity is surfaced explicitly via the [`staleOdds` flag](#bookmaker-staleness-staleodds), so you always know when a price may no longer be current.

This means worst-case recovery is a **fast, well-defined REST snapshot**, not an open-ended outage. See [Resume & Replay](/websocket/resume-replay.md) for the full recovery flow.

***

## Monitoring & operational responsibility

OddsPapi gives you the signals to operate safely; your client should act on them:

* Track the [`staleOdds` flag](#bookmaker-staleness-staleodds) per bookmaker before trading on a price.
* Handle `snapshot_required` and reconnect logic (see the [Resume & Replay](/websocket/resume-replay.md) template).
* Respect [rate limits](/api-reference/rate-limits.md) and [WebSocket connection limits](/websocket/overview.md); contact support to raise limits for distributed systems.
* Watch the status page for platform-level incidents.

***

## Support & incident response

| Channel | Use for |
| - | - |
| [contact@55-tech.com](mailto:contact@55-tech.com) | Technical support, integration help, incident reports |
| [contact@55-tech.com](mailto:contact@55-tech.com) | API access, SLA terms, raising limits |
| [Status page](https://oddspapi-v5.instatus.com) | Live availability and incident updates |

When reporting an issue, include your `apiKey` group, affected channels or endpoints, timestamps (UTC), and any `serverEpoch` / `entryId` values — these let us trace your session quickly.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.