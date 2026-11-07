> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Combinatorial Positions

> Multi-leg positions built from existing Polymarket outcomes

Combinatorial positions let traders express a single view across multiple
Polymarket outcomes. Instead of trading one market at a time, a combinatorial
position combines existing outcome tokens into one new YES/NO pair.

## What They Represent

A combinatorial **YES** position represents a conjunction of legs:

```text theme={null}
YES(
  YES(Market A) and YES(Market B) and NO(Market C)
)
```

It pays out when every leg in the combination pays out -- in above example, if
Market A resolves YES AND Market B resolves YES AND Market C resolves NO. The
matching combinatorial **NO** position is the complement:

```text theme={null}
NO(
  YES(Market A) and YES(Market B) and NO(Market C)
)
```

It pays out when the full conjunction does not pay out. In this example, it
will pay out if Market A resolves NO OR Market B resolves NO OR Market C resolves
YES.

## How Tokens Work

A combinatorial condition represents a conjuction of legs, but not a side. The
combinatorial condition from above would be:

```text theme={null}
YES(Market A) and YES(Market B) and NO(Market C)
```

Each combinatorial condition has two **combinatorial positions**, the YES and
the NO, which each have their own ERC 1155 token ID:

| Position | Meaning |
| - | - |
| YES | The full combination pays out |
| NO | The full combination does not pay out |

Like standard CTF positions, the YES and NO pair is fully collateralized.
Splitting collateral creates matching YES and NO combinatorial tokens, and
merging a matching pair returns collateral.

These positions use Polymarket Protocol V2 rather than the Conditional Tokens Framework.

## Resolution

A combinatorial YES position pays the product of its legs' payouts. A leg that
wins pays `1`, a leg that loses pays `0`, and a leg whose market
[resolves 50/50](https://docs.polymarket.com/concepts/resolution.md) pays `0.5`. The matching NO position pays
`1` minus the YES payout.

| Leg Results | YES Payout per Token | NO Payout per Token |
| - | - | - |
| Every leg wins | \$1.00 | \$0.00 |
| Any leg loses | \$0.00 | \$1.00 |
| One leg resolves 50/50, others win | \$0.50 | \$0.50 |
| Two legs resolve 50/50, others win | \$0.25 | \$0.75 |

A leg that resolves 50/50 stays in the combination. It is not dropped, so a YES
position bought above its final payout returns less than it cost. Whether a
canceled or postponed event resolves 50/50 depends on the rules of that leg's
market.

If some legs are already resolved and others remain open, the position can be
compressed into a simpler position that keeps only the unresolved legs and
realizes any resolved collateral value.

## Related Pages

> [card] [CTF Overview](https://docs.polymarket.com/trading/positions/how-positions-work.md)
>
> Minimal overview of Conditional Tokens

> [card] [Split Tokens](https://docs.polymarket.com/trading/positions/manage.md#split-a-position)
>
> Create YES and NO token pairs

> [card] [Combos](https://docs.polymarket.com/trading/combos/overview.md)
>
> Quote combinatorial positions through RFQ


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.