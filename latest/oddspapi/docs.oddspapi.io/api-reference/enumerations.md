> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Enumerations - Status IDs, Periods & Settlement Statuses

> Complete reference of all fixed enum values in the OddsPapi API: statusId lifecycle values, period keys, and settlement statuses.

This page enumerates every **fixed vocabulary** used on the wire. These values are part of the public contract: existing values are **frozen** (they never change meaning or disappear), and new values are only ever **appended** — treat unknown values gracefully in your client.

***

## statusId (fixture / future lifecycle)

The lifecycle of every fixture and future. Frozen set:

| statusId | Slug | live | Meaning |
| -: | - | :-: | - |
| `0` | `pregame` | no | Scheduled, has not begun. |
| `1` | `live` | yes | Currently in play. |
| `2` | `finished` | no | Finished; final scores/results available. |
| `3` | `cancelled` | no | Cancelled / will not be played. |

Notes:

* `statusName` is the translated display label (follows the language prefix); `statusId` is the stable key — always branch on the ID.
* Status only ever moves **forward** (`0 → 1 → 2`, or any state → `3`); it never goes backwards.

***

## period

Score, stats, and market data are keyed by a **generic period vocabulary**, so the same keys work across all sports. What a period *means* depends on the sport's structure — use the fixture's `expectedPeriods` and `periodLength` to interpret it (e.g. `p1` is a half in soccer, a quarter in NBA basketball, a set in tennis, a map in esports).

### Whole-match keys

| Value | Meaning |
| - | - |
| `result` | The overall match score — the headline scoreline including everything played so far. |
| `fulltime` | Regulation time only (excludes overtime and penalties). Not every sport has it — see [Periods per sport](/guides/periods.md). |
| `overtime` | Overtime / extra time segment only. |
| `penalties` | Penalty shootout. **Current behavior:** carries the score *after* penalties (cumulative), not the shootout segment alone — this is inconsistent with the other whole-match keys and will change in a future release to be segment-only. |

### Numbered periods

| Value | Meaning |
| - | - |
| `p1` … `p12` | Period *N* of the sport's natural structure: half, quarter, set, map, inning, or round (e.g. `p10`–`p12` cover boxing rounds). |

### Combined segments

Sums of consecutive periods, used where markets are offered on a combined segment:

| Value | Typical use |
| - | - |
| `p1+p2` | First half in quarter-based sports (e.g. basketball). |
| `p3+p4` | Second half in quarter-based sports. |
| `p1+p2+p3+p4+p5` | Combined multi-period segment. |
| `fulltime+overtime` | Regulation plus overtime. |
| `p3+p4+overtime` | Second half including overtime. |
| `p4+overtime` | Final period including overtime. |
| `p6+p7+p8+p9+overtime` | Combined late-innings segment (e.g. baseball). |

### Sub-period keys (grids within a period)

Sub-period keys follow one shape: `p{X}{axis}{Y}` — unit *Y* within period *X*. Two axes exist today.

| Value | Meaning |
| - | - |
| `p1g1` … `p5g13` | **`g` = game.** Game *Y* within period/set *X* — tennis first: sets 1–5, games 1–12, and `g13` = the tiebreak. Market periods use only `g2`, `g4` and `g6` (the `correctscore` "after *Y* games" lines); score periods use the full grid. |
| `p1r1` … `p5r36` | **`r` = round.** Round *Y* within map *X* — esports. |
| `currentgame` | Rolling pointer to the **in-progress** game (e.g. the current tennis game score). Only ever the live pointer — settled per-game facts use the `pXgY` keys. |

> [!NOTE]
>
> The period vocabulary is **append-only**: new sports may introduce additional keys, and new axes may join `g` and `r`
> (darts legs are a likely next one). Never hard-code an exhaustive list — handle unknown period keys gracefully.
> Parsing \`p{X}
>
> {axis}
>
> {Y}\` structurally is safer than matching a fixed set of strings.

***

## settlementStatus

Per-outcome grading returned by the settlement endpoints:

| Value | Meaning |
| - | - |
| `WIN` | The outcome won — full payout. |
| `LOSE` | The outcome lost. |
| `PUSH` | Void / tie on the line — stake returned. |
| `HALFWIN` | Half the stake wins, half is returned (Asian quarter lines). |
| `HALFLOSS` | Half the stake loses, half is returned (Asian quarter lines). |
| `CANCELLED` | Market voided (fixture cancelled, market not actionable) — stake returned. |
| `UNDECIDED` | Cannot be graded (yet) — result data insufficient or pending. |

For non-clean grades (`CANCELLED` / `UNDECIDED`) the settlement row carries a `reason` string explaining why.

***

## Open taxonomies (not enums)

These vocabularies are **string sets that grow over time** — stable per value, but never exhaustive:

* **`marketType`** — market kind (`1x2`, `totals`, `spreads`, `players-shots`, …). See [Concepts → Markets](/api-reference/concepts.md#markets-and-outcomes).
* **`eventType`** — in-play action types on the events channel (`goal`, `card_yellow`, `corner_taken`, `substitution`, `var`, …).
* **`statType`** — countable stat aggregates on the stats channel (`score`, `corners`, `cards`, `aces`, …).

Treat these as opaque identifiers: match on the values you support and ignore the rest.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.