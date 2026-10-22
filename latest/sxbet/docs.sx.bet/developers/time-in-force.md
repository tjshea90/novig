> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Time in force

> The order time-in-force options available on SX Bet: GTC, IOC, and FOK.

Every order carries a required `timeInForce`. It decides how your order behaves in the order book.

| `timeInForce` | Unmatched size | Use it for |
| - | - | - |
| **`GTC`** — good till cancelled | **Rests on the book** until filled, cancelled, or expired | Quoting. Providing liquidity. |
| **`IOC`** — immediate or cancel | **Discarded.** Whatever matched is yours | Taking what is available now, accepting a partial fill. |
| **`FOK`** — fill or kill | **Nothing executes.** The whole order is cancelled unless it fills completely | All-or-nothing execution. |

A `GTC` order can still be a taker. If it crosses the book on arrival it matches immediately, and only
the remainder rests.

## Post-only

`postOnly: true` is a modifier on a `GTC` order, not a fourth `timeInForce`. It makes the order add
liquidity only: if it would cross the book on arrival it is cancelled with
`inactiveReason: "POST_ONLY_WOULD_CROSS"` instead of matching. An order that does not cross rests as
usual.

Because it can never take, no [betting delay](/developers/betting-delays.md) applies.

## Which one to use

| You want to | Use |
| - | - |
| Quote a two-sided market and get filled over time | `GTC` |
| Quote without ever crossing into a taker fill | `GTC` with `postOnly: true` (`IOC`/`FOK` is rejected with `POST_ONLY_REQUIRES_GTC`) |
| Bet at the best available price, taking whatever size is there | `IOC` |
| Bet a specific amount or nothing | `FOK` |
| Take a parlay | `FOK` or `IOC` |
| Provide parlay liquidity | `GTC`, with a real `expiry` no later than the parlay's |
| Use bet credits | `IOC` or `FOK` (a resting bet-credit order is rejected with `BET_CREDITS_REQUIRE_NON_RESTING`) |

## Related

> **[Posting orders](/developers/posting-orders.md)**
>
> Build, sign and submit an order of any type.

> **[Taking liquidity](/developers/taking-liquidity.md)**
>
> The immediate-execution path, end to end.

> **[Order lifecycle](/developers/order-lifecycle.md)**
>
> `PENDING`, `ACTIVE`, `INACTIVE`, and every terminal reason.

> **[Tracking your orders](/developers/my-orders.md)**
>
> `timeInForce` and `postOnly` are write-only — tag them in your own store when you post.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.