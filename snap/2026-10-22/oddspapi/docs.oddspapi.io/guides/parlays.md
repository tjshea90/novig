> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlays & SGP - Building a Betslip

> A workflow guide for multi-leg betting on OddsPapi: choosing between the parlay and SGP endpoints, keeping a live slip priced, comparing bookmakers, and degrading gracefully when a leg suspends.

[Parlays & SGP](/api-reference/parlays.md) is the field-by-field reference for the
two multi-leg endpoints. This guide is the other half: **how to wire them into a
betslip that stays correct while a user is still building it.**

***

## Which endpoint

The choice is made by the legs, not by preference.

**1. All legs in one fixture?**

Use `GET /fixtures/odds/sgp`. The bookmaker prices it, so `price` reflects
the real correlation between the legs — "over 2.5 goals" and "home win" are
not independent events, and a naive product would overprice the slip.

**2. Legs across several fixtures?**

Use `GET /fixtures/odds/parlay`. Legs are independent, so the price is the
product of the decimal odds, computed locally and instantly.

**3. A mix?**

`/sgp` rejects it — all legs must share one fixture. Either split the slip
or price the whole thing with `/parlay` and accept the uncorrelated number.

The two answers for the same two legs, side by side:

**/parlay — a plain product**

```json theme={null}
"parlay": {
  "bet365": {
    "id1400003160574217:bet365:141:0,id1400003160574217:bet365:146:0": {
      "bookmaker": "bet365",
      "active": true,
      "price": 7.98,
      "singlesPrice": 7.98,
      "changedAt": 1766667441097
    }
  }
}
```

**/sgp — the bookmaker's quote**

```json theme={null}
"sgp": {
  "bet365": {
    "id1400003160574217:bet365:141:0,id1400003160574217:bet365:146:0": {
      "bookmaker": "bet365",
      "active": true,
      "price": 6.5,
      "singlesPrice": 7.98,
      "changedAt": 1766667441097,
      "betslip": "https://www.bet365.com/dl/sportsbookredirect?bs=..."
    }
  }
}
```

Same legs, same `parlayId`, two different prices. `singlesPrice` is the naive
product in both; on `/sgp` the gap down to `price` is the correlation margin.
Showing both numbers is what makes an SGP legible to a user.

***

## Comparing bookmakers

Legs are grouped **by bookmaker**, and `parlayId` is built the same way on both
endpoints (sorted, deduped `oddsIds` joined by `,`). Two consequences you can
build on:

* Request the same selection at several books in one call and you get every
  book's price for that slip side by side, under the same `parlayId`.
* A `/parlay` result and an `/sgp` result for an identical selection are
  directly comparable — same key, different pricing model.

A book that cannot price the slip is simply absent from the section. Absence is
not an error; see below.

***

## Keeping a live slip priced

A betslip is a long-lived object and prices move under it.

* **Re-price on every leg change.** `parlayId` changes the moment the selection
  changes, so cached prices are keyed correctly for free.
* **Re-price on a clock, not on every tick.** Both endpoints are capped at
  **10 requests/second**. Poll a live slip on a fixed interval rather than
  chasing every odds update — an `/sgp` call can round-trip the bookmaker, so
  it is the slower of the two even where the budget is the same.
* **Cap at 20 legs.** Both endpoints reject more.

> [!WARNING]
>
> Do not fan a language sweep or a bookmaker sweep out in parallel against
> `/parlay`. Ten requests per second is per key, and the endpoint answers
> quickly enough that a burst trips the limit before you notice — the symptom
> looks like empty parlay sections, not like a rate-limit error.

***

## Degrading gracefully

Three different things can make a slip unpriceable, and a good UI distinguishes
them:

| What happened | How you see it | What to tell the user |
| - | - | - |
| A leg was never found | the `oddsId` appears in `missingOddsIds` | the selection is invalid — drop it |
| A leg is temporarily off | the leg is present but inactive, with `meta.inactiveReason` | suspended — the slip returns when it reopens |
| The book could not quote | the bookmaker is absent from `sgp`, and `missingOddsIds` is **not** set | try another book |

That third row is the one that surprises people: every leg was found on our
side, but the bookmaker's own pricing service could not resolve the parlay. The
section is empty and nothing is reported as missing, because nothing *was*
missing. Read [suspended vs missing
legs](/api-reference/parlays.md#suspended-vs-missing-legs) for the full
`inactiveReason` list.

The `odds` section is never filtered for you — inactive legs come back
included, deliberately, so you can render a suspended leg in place instead of
having it vanish from the slip.

***

## Periods and legs

Legs from different periods of the same fixture combine normally — a `fulltime`
1X2 and a `p1` totals line are two legs like any other. If you are building
market groupings to select from, read [Periods per sport](/guides/periods.md)
first: whether a sport even offers `fulltime` depends on its structure, and
assuming it does is the most common source of empty market buckets.

***

## Related

> **[Parlays & SGP reference](/api-reference/parlays.md)**
>
> Every field, error and `inactiveReason`.

> **[Rate limits](/api-reference/rate-limits.md)**
>
> Authoritative per-endpoint budgets.

> **[Periods per sport](/guides/periods.md)**
>
> Which periods exist where, and why.

> **[Powering a sportsbook](/guides/sportsbook.md)**
>
> The full pipeline this slip sits inside.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.