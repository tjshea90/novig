> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Latency Methodology - How OddsPapi Measures Per-Book Delay

> How the published per-bookmaker delay numbers are produced: what is measured, what counts as measured vs. estimated, what the numbers exclude, and how to reproduce them.

We publish per-book delay numbers in [Latency and freshness](https://docs.oddspapi.io/api-reference/reliability.md#latency-and-freshness) and as live bounds on [`GET /bookmakers`](https://docs.oddspapi.io/api-reference/common/get-bookmakers). This page is the methodology behind them — what exactly is measured, where the numbers come from, and how to reproduce them yourself. A number without a methodology is marketing; held to one, it is a claim.

***

## The quantity

One metric per odds update:

```
observation delay = changedAt − bookmakerChangedAt
```

* `bookmakerChangedAt` — the bookmaker's own change timestamp, as published by the book.
* `changedAt` — when OddsPapi observed the change at the bookmaker.

This is the delay **we own**: from the price changing at the source to us having it. It deliberately excludes everything downstream of observation — see [what the numbers exclude](#what-the-numbers-exclude).

Delays are aggregated **per bookmaker**, split by delivery mode — live, pregame, and pregame main lines — because the modes run on different collection paths and their delays differ. A single blended number would hide exactly the difference that matters.

***

## Measured vs. estimated

Every published number carries one of two modes:

| Mode | When | How the number is produced |
| - | - | - |
| **measured** | The book publishes usable change timestamps | Rolling p50 of `changedAt − bookmakerChangedAt` across that book's updates, per delivery mode |
| **estimated** | The book publishes no usable timestamp | Derived from the collection cycle for that book — stated as an approximation, never presented as measured |

The mode is printed next to every number we publish. If a book's row says *estimated*, treat it as our honest upper-bound guess, not a measurement — and note that `bookmakerChangedAt` is the book's clock, not ours, so even measured values inherit the source's timestamping quality.

***

## Where the numbers live

Two artifacts, with different jobs:

* **[`GET /bookmakers`](https://docs.oddspapi.io/api-reference/common/get-bookmakers)** — the live source of truth. `maxDelayLiveInSec`, `maxDelayPregameInSec`, and `maxDelayPregameMainInSec` state the maximum expected refresh delay per delivery mode, per book, maintained as a rolling value. These bounds are the claim; hold us to them.
* **The [docs table](https://docs.oddspapi.io/api-reference/reliability.md#latency-and-freshness)** — a representative snapshot of rolling p50s for books trialists ask about most. It illustrates; the endpoint governs.

When the two disagree, the endpoint wins — it moves with the feed, the docs page moves with editors.

***

## What the numbers exclude

Observation delay ends at `changedAt`. Downstream of it:

* **Internal pipeline** — observation to gateway egress adds \~20 ms (p50).
* **Conflation** — updates are batched on a fixed \~6 ms window, newest value wins; nothing is dropped, see [delivery semantics](https://docs.oddspapi.io/websocket/channels/odds.md#delivery-semantics).
* **Delivery hop** — gateway `ts` to your receipt depends on where you deploy; as low as \~5 ms colocated (see [server location](https://docs.oddspapi.io/api-reference/concepts.md#server-location-matters)).

These are excluded on purpose: the delivery hop is dominated by *your* deployment, so folding it into a published number would make the number unverifiable. Each update carries the full stamp chain (`bookmakerChangedAt`, `changedAt`, `ts`), so every segment is measurable separately.

***

## Reproduce it

Nothing in this methodology is privileged — the same timestamps we aggregate arrive on every update you receive. Log `changedAt − bookmakerChangedAt` per book over your trial, split live vs. pregame, take the p50, and put it next to the published row. The full protocol, including recovery drills and a CLV join, is in [evaluating the feed](https://docs.oddspapi.io/guides/evaluation.md); racing an incumbent feed on the same line moves is in [running a second feed](https://docs.oddspapi.io/guides/second-feed.md#measure-which-feed-is-faster).

If your measurements disagree with ours, send the affected books and UTC timestamps to [contact@55-tech.com](mailto:contact@55-tech.com) — a reproducible disagreement is a bug report, and we treat it as one.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.