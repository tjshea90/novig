> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Event lifecycle

> The states an event moves through, and how its markets settle.

## Event states

Event states: OPEN\_PREGAME to OPEN\_INGAME to FINAL along the top. DELAYED and CANCELED sit below.

The top row is the normal path. `DELAYED` pauses an event before or during the game.

| Status | Tradable | Fees | Terminal |
| - | - | - | - |
| `OPEN_PREGAME` | ✓ | | |
| `OPEN_INGAME` | ✓ | `taker` | |
| `DELAYED` | ✓ | | |
| `FINAL` | | | ✓ |
| `CANCELED` | | | ✓ |

> [!WARNING]
>
> When an event goes live (`GOLIVE`), we void every resting order on its markets. Each order's `cancel` event gives the reason in `cancel.reason`.

`DELAYED` is a pause before or during the game. When the event reopens, its book is cleared.

The Fees column applies to a market whose `fee.charged` is `WHEN_LIVE`, which includes every game market.
A futures event never reaches `OPEN_INGAME`, and its league sets its fee schedule.
See [Which schedule a market is on](/api/concepts/fees.md#which-schedule-a-market-is-on).

## Settlement

When an event ends, we grade each of its markets.
The grade sets the status of every outcome in the market at once.
After a market closes, read its grade from [market history](/api/catalog#market-history).
To collect new grades as they land, [fetch new settlements](/api/catalog#fetch-new-settlements) with `settledAfter`.

| Grade | Outcome status | A contract pays |
| - | - | - |
| `Winner` | `WIN` on one, `LOSS` on the rest | Full value on `WIN`, nothing on `LOSS` |
| `Pushes` | `PUSH` on every outcome | Its collateral back |
| `FMV(price)` | One price per outcome | `price` × full value |

Market status: OPEN to CLOSED to SETTLED, with a dashed remediation edge back to CLOSED. Outcome status: TBD to WIN, LOSS, PUSH, or FMV(price), with a dashed remediation edge back to TBD.

A grade moves every outcome out of `TBD` at once. Remediation is the only way back.

Remediation undoes a settlement.
The market returns to `CLOSED`, every outcome returns to `TBD`, and we grade the market again.
A market normally stays `SETTLED`, but remediation can move it out.

> [!WARNING]
>
> **An FMV outcome's `status` is a bare decimal string, not the word `FMV`.** Treat any `status` other than `TBD`, `WIN`, `LOSS`, or `PUSH` as a price between `"0.000"` and `"1.000"`.

The generated TypeScript enum lists only the four words, so a price arrives outside its members.

* A market's FMV prices sum to `1.000`, so the other side of a price is `1 − price`.
* Each market voids one way, by push or by FMV, never both. We refuse a grade of the other kind.
* `PUSH` and a price are both voids, not wins. Only `WIN` and `LOSS` pay full value or nothing.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.