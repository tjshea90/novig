> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Self-Trade Prevention

> How the matching engine stops an account, or a group of accounts run by one entity, from trading with itself

Self-trade prevention (STP) stops your orders from filling against your own
resting orders. It runs on the matching engine for every order, it is not an
order parameter, and it cannot be disabled or changed per order.

## How it works

Before a taker matches, the engine checks whether the resting maker it would
hit belongs to the same party. Every order submitted through the API runs in
**CancelMaker** mode: the conflicting resting order is canceled and the taker
keeps matching against the rest of the book. The canceled resting order is
reported with status `stp_cancelled`, the same status you see in the order
tables under [Trading](/perps/trading.md).

The engine also implements **CancelTaker** and **CancelBoth** modes, which are
used internally by the exchange and are not available to API orders.

STP acts on resting orders that are eligible to match. A resting order priced
outside the instrument's mark-relative price band cannot be matched by anyone,
so a crossing order of yours does not cancel it; it stays on the book, keeps
its margin reserved, and counts toward your open-order limit until you cancel
it or the mark moves back into range.

## Account groups

Polymarket can group several accounts that are controlled by one entity, for
example a market maker running separate accounts for separate strategies.
Groups are assigned by Polymarket on request. There is no public API to create,
inspect, or change them, and group membership is never visible to other users
or in public endpoints.

For STP, **accounts in a group count as the same party**. An order from one
member that would match another member's resting order cancels that resting
order and continues matching against the rest of the book, exactly as if both
orders were on one account. Resting take-profit and stop-loss exits behave like
any other resting order of yours: a group-mate's crossing order cancels them
just as your own would, and a canceled exit does not re-arm.

Grouping changes nothing else about how the accounts trade. Balances, margin,
positions, liquidation, rate limits, open-order limits and auto-cancel remain
strictly per account. Two things do follow group membership besides STP:

* **Default leverage and margin mode.** A group can carry defaults that apply to
  any member with no explicit per-instrument setting, including future listings.
  Your own explicit settings always take precedence. See
  [Margin](/perps/learn-about-trading/margin.md).
* **Internal transfers between members.** A trading key registered to one
  member can move collateral to another member of the same group, without the
  owner key. Owner keys are never restricted by groups, and withdrawals always
  require the owner key. See
  [Transfer Between Your Accounts](/perps/fund-your-account.md#transfer-between-your-accounts).

When Polymarket changes a group's membership, orders that were being matched
at that instant are re-evaluated by the engine against the new membership
before any result is published, so a fill or cancellation can never be based
on stale membership. You do not see a distinct error for this; the order
simply resolves under the new membership.

To have accounts grouped, contact Polymarket with the list of accounts that
belong to the entity.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.