> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Trading Fees

> How Novig prices trading fees, and when they apply

> [!NOTE]
>
> This page carries the RFQ fee schedule. For straight and futures contracts on v3,
> read [Trading fees](/api/concepts/fees.md), which prices each market from its own
> `market.fee` object.

> [!NOTE]
>
> Since Thursday, October 15, 2026, the live taker fee has been `0.06` on every straight contract, including NFL.

Novig charges a single trading fee, on one side of a trade. On straight contracts it applies only while the underlying event is live; [RFQ trades](#rfq-trades) are priced on their own schedule.

## The Formula

$$
\text{Fee} = c \cdot P\,(1 - P) \cdot N
$$

| Term | Meaning |
| - | - |
| $P$ | The **execution price** of the fill, in $(0, 1)$ — not your limit price |
| $c$ | The coefficient. Currently **0.06** for live taker fills |
| $N$ | Contracts in the fill, where one contract pays \$1.00 at settlement |

> [!WARNING]
>
> **Watch the units.** $N$ counts \$1.00-payout contracts, but the API's `qty` field is denominated in minimal units: $N = \text{qty} / 100$. Divide before you put a `qty` into the formula, or you will overstate the fee by 100×.

Each fill is priced independently, so a partially filled order accrues a fee per fill rather than one fee on the parent order.

**Fees are charged exactly, including sub-cent amounts** — there is no rounding up to the cent and no minimum fee. The only quantization is the ledger's own precision, 5 decimal places (\$0.00001), applied with standard half-up rounding. A fill small enough to owe a fraction of a cent is charged that fraction.

The formula is symmetric around $P = 0.5$: $P$ and $1 - P$ produce the same fee, so a fill at \$0.30 costs exactly what a fill at \$0.70 costs. The fee peaks at even money,

$$
\max_{P} \; c \cdot P\,(1 - P) = \frac{c}{4} = 0.015 \text{ per contract}
$$

— 1.5% of payout, or 3% of notional — and falls toward zero at both tails.

### Worked Examples

At the current 0.06 coefficient:

| Fill | `qty` on the wire | Calculation | Fee charged |
| - | - | - | - |
| 100 contracts @ \$0.50 | 10,000 | `0.50 × 0.50 × 0.06 × 100` | \$1.50 |
| 100 contracts @ \$0.30 | 10,000 | `0.30 × 0.70 × 0.06 × 100` | \$1.26 |
| 100 contracts @ \$0.70 | 10,000 | `0.70 × 0.30 × 0.06 × 100` | \$1.26 |
| 100 contracts @ \$0.10 | 10,000 | `0.10 × 0.90 × 0.06 × 100` | \$0.54 |
| 500 contracts @ \$0.45 | 50,000 | `0.45 × 0.55 × 0.06 × 500` | \$7.425 |
| 1 contract @ \$0.50 | 100 | `0.50 × 0.50 × 0.06 × 1` | \$0.015 |
| 0.01 contract @ \$0.50 | 1 | `0.50 × 0.50 × 0.06 × 0.01` | \$0.00015 |

The last three rows are the sub-cent rule: \$7.425 is charged as \$7.425, not rounded to \$7.43, and a one-`qty` fill owes \$0.00015, charged exactly. A cent floor on that fill would be a \~67× overcharge, which is why there isn't one.

## Who Pays

| Side | Fee |
| - | - |
| **Taker** | Pays the fee above. Debited from your balance at match time |
| **Maker** | Pays **no fee**, and may earn a [Maker Credit](/maker-credit-program.md) on the same fill |

There is no maker fee, and no opt-in or contract is required to collect Maker Credits. Effective October 22, 2026, the Maker Credit is 25% of the taker fee actually collected on the trade. At the 0.06 live coefficient, that works out to:

$$
\text{Maker credit} = \tfrac{1}{4}\,\text{Fee} = 0.015 \cdot P\,(1 - P) \cdot N
$$

See the [Maker Credit Program](/maker-credit-program.md) for the full terms, eligibility, and crediting schedule.

> [!WARNING]
>
> **Maker Credits are straights only. RFQ makes do not earn a Maker Credit.** Quoting an RFQ makes you the maker on a combination contract, and combination contracts are excluded from the Program — see [Eligible Markets and Scope](/maker-credit-program.md#2-eligible-markets-and-scope). No amount of RFQ volume accrues Maker Credits; only straight-contract makes matched in-game do.

## RFQ Trades

RFQ executions are priced on their own coefficient and their own form of the same quadratic.

| | Straight contracts | RFQ (combination contracts) |
| - | - | - |
| Taker coefficient | 0.06 | **0.10** |
| Maker fee | none | none |
| Maker Credit | 25% of the taker fee, when matched in-game | **not eligible** |
| Charged only while live | yes | no — assessed on execution |

A combination contract has no single contract price, so the taker fee is expressed in stake form. With $w$ the taker's stake and $k$ the pricer's collateral, the implied probability is $P = w / (w + k)$ and the pot is $w + k$, so $c \cdot P\,(1 - P) \cdot (w + k)$ reduces to

$$
\text{RFQ fee} = 0.10 \cdot \frac{w \, k}{w + k}
$$

Two differences from the straight schedule worth calling out. The RFQ taker fee **is not gated on event liveness** — RFQs execute before the event begins, so the charge applies on execution rather than only during live play, and it is not a Live Trading fee for Maker Credit purposes. And the maker side, the EMM pricing the RFQ, pays **no fee** but likewise earns **no credit**.

## When Fees Apply

> [!WARNING]
>
> **Straight-contract fees are charged only on fills matched while the underlying event is live** — that is, while the event's status is `OPEN_INGAME`. A fill matched at any other time is not charged, on either side, and generates no Maker Credit. ([RFQ trades](#rfq-trades) are the exception: they are charged on execution, live or not.)
>
> Liveness is evaluated **at match time**, not when the order was placed. A resting order placed hours before kickoff that fills in the second quarter is a live fill and is charged; a live-priced order that fills during a suspension is not.

Because the charge follows the event's status at the moment of the match, knowing whether an event is live is a fee question, not a cosmetic one. There are two ways to know:

> **[WebSocket: the transitions](/api/streaming/lifecycle.md)**
>
> The `lifecycle` channel publishes `GOLIVE` when a market enters live play and `UNLIVE` when it leaves. Fees begin on `GOLIVE` and stop on `UNLIVE`.

> **[REST: the snapshot](/api/catalog)**
>
> `GET /v3/catalog/events/{id}` returns the event's `status`. `OPEN_INGAME` is exactly the fee-charged condition. Use it to bootstrap on connect and after any reconnect.

> [!NOTE]
>
> **The live window can open more than once.** A game that is delayed or suspended mid-play goes `OPEN_INGAME → DELAYED → OPEN_INGAME`, so fees switch on, off, and on again. Each `GOLIVE` also voids every order resting across that event's markets. Track the edges rather than assuming one live window per event — see [Event states](/api/concepts/event-lifecycle.md#event-states).

## Reading Fees from the API

Every fill on `GET /v3/portfolio/fills` reports what it cost you. A charged fill carries `fee`, the total debited from your wallet for that fill; it is absent on a fill that was not charged, so a maker fill or a non-live fill has no `fee` field. The ledger rows behind it — the collateral debit and, on a charged fill, the fee debit — are on `GET /v3/portfolio/transactions`.

Real-time `fill` events on the [private stream](/api/streaming/private.md) do not carry a fee. Fetch the fill history.

## Scope and Notes

* **Two schedules.** The formula at the top of this page prices straight contracts. Combination contracts (multi-leg or "parlay" contracts, including everything traded via RFQ) are priced by the [RFQ schedule](#rfq-trades) and are not eligible for Maker Credits.
* **Trading fees only.** "Fees" here means Novig's trading fees. Clearing, banking, and payment processing fees are not included.
* **The coefficient can change.** The coefficient above is the current live trading fee schedule, posted pursuant to Rule 3.6 of the Ludlow Rulebook. Read it from this page rather than hardcoding a rate you can't update.
* **No cent floor.** Charges carry to 5 decimal places (\$0.00001), so sub-cent fees are charged as sub-cent amounts.

> [!TIP]
>
> Liquidity providers (LPs) with USD 200,000 or more deposited can request a dedicated Slack channel with the Novig team.

Questions on fees or the Maker Credit Program: [caleb.henry@novig.co](mailto:caleb.henry@novig.co).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.