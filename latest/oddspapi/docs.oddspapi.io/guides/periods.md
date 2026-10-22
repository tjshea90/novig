> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Periods per Sport - When fulltime and result Differ

> Understand the OddsPapi period vocabulary per sport: when fulltime is a distinct fact, when it collapses into result, and how to group markets by marketType and period without hard-coding assumptions.

Every market and every score line carries a `period`. The vocabulary is generic
and shared across all sports — `result`, `fulltime`, `p1`, `p1+p2`,
`fulltime+overtime`, `p1g6`, and 200-odd more. [Enumerations →
period](/api-reference/enumerations.md#period) is the reference list.

This guide answers the question the reference list does not: **which periods a
given sport actually has, and why `fulltime` exists for soccer but not for MMA.**

***

## The two whole-match periods

| Value | What it is |
| - | - |
| `result` | The score that decides the fixture — everything cumulated, including any overtime, extra time or shootout. Always present, for every sport. |
| `fulltime` | The score frozen at the end of regulation, **before** any phase that can still change it. |

`result` is never withheld and never derived. If you only ever consume one
whole-match period, consume `result`.

From [2026-10-05](/changelog/index.md) every market carries a non-empty `period`.
Before that date a blank value is possible on a handful of markets.

***

## When does `fulltime` exist?

> [!NOTE]
>
> **The rule:** ask *"does this sport have a defined end of regulation, with a
> further phase after it that can still add to the score?"*
>
> **Yes** → `fulltime` is a distinct fact, and markets are offered on both.
> **No** → `fulltime` and `result` would be two names for one number, so only
> `result` is published.

Soccer is the reason the period exists at all. A cup tie that ends 1–1 after 90
minutes and 2–1 after extra time has two genuinely different answers to "who
won": a draw at `fulltime`, a home win at `result`. A 90-minute 1X2 market
deliberately excludes what comes after, and that exclusion is the product.

### The discriminator is not "is the sport time-bounded"

This is the common mistake. The question is about **a scoring phase after the
boundary**, not about clocks.

> **Baseball — no clock, but fulltime exists**
>
> Baseball has no timer. It still has a defined end of regulation — nine
> innings — and extra innings after it that add runs. So `fulltime` (through
> nine) and `result` (including extras) are different numbers, and both are
> published.

> **MMA and boxing — bounded rounds, but no fulltime**
>
> A bout has a fixed round count, so it looks time-bounded. But nothing after
> the final round adds to the score: the judges' decision *names a winner*, it
> does not extend the contest. The score at the end of the final round **is**
> the result. Publishing `fulltime` there would be a second label for one
> number, so MMA and boxing markets sit on `result` only.

> **Tennis, snooker, darts — the match ends when it ends**
>
> Play-to-target sports have no regulation cutoff at all. There is no moment
> where the score is provisional and a later phase might move it — the match
> simply ends. Only `result` (plus the set/leg/frame periods `p1`, `p2`, …).

### Sports where both are published

These sports have a scoring phase after regulation — overtime, extra time,
a shootout, extra innings or a golden point — so `fulltime` and `result` are
distinct:

Soccer, Basketball, Baseball, American Football, Ice Hockey, Handball, Rugby,
Cricket, Waterpolo, Futsal, Aussie Rules, Field Hockey, Floorball, Beach Soccer,
Pesapallo, Lacrosse, Bandy, Kabaddi, Rink Hockey, Gaelic Football, Netball,
Gaelic Hurling, Softball, eSoccer, eBasketball — 25 sports.

Everywhere else — MMA, Boxing, Tennis, Darts, Snooker, Table Tennis, Volleyball,
Squash, Badminton, Curling, Padel, Golf, Cycling, Athletics, Judo, Speedway,
Bowls, Cross-Country, Beach Volley, Beach Handball, Basketball 3x3, and every
esports title — whole-match markets are on `result`, and there is no `fulltime`
bucket to look for.

> [!WARNING]
>
> Do not hard-code this list. It is what ships today, and sports get added.
> Read the `period` off the market itself; treat "this sport has no `fulltime`"
> as an observation, not a constant.

***

## Soccer's shootout, specifically

Soccer is the one sport where the shootout tally is folded into the `result`
score rather than being reported as a separate segment. A tie decided 4–3 on
penalties after 1–1 is carried as a 1–1 `fulltime` and a shootout-inclusive
`result`. Other sports that go to a shootout report the phase separately.

This is also why the `penalties` *score* period is cumulative today rather than
segment-only — see the note in
[Enumerations → period](/api-reference/enumerations.md#period).

***

## Periods inside the match

Beyond the two whole-match values, `period` also names segments. What `p1` means
is sport-dependent, which is the whole point of a generic vocabulary:

| `p1` is a… | in |
| - | - |
| half | Soccer, Rugby, Handball |
| quarter | Basketball, American Football |
| set | Tennis, Volleyball, Table Tennis |
| map | Counter-Strike, Valorant, Dota, League of Legends |
| inning | Baseball, Softball |
| period | Ice Hockey |

Use the fixture's `expectedPeriods` and `periodLength` to interpret them rather
than branching on `sportId`.

Combined segments (`p1+p2` = first half in a quarter sport, `p3+p4+overtime` =
second half including OT) and sub-period grids (`p1g6` = game 6 of set 1,
`p1r13` = round 13 of map 1) follow the same principle.

***

## Grouping markets in your client

Markets are keyed by `marketType` × `period`. Two rules keep that grouping
stable:

**1. Never synthesise a period**

Do not assume a sport offers `fulltime` and fall back to `result` when the
lookup misses. If a market is published on `result`, that *is* the
whole-match market — an empty `fulltime` bucket is not a gap.

**2. Pin to marketId where you can**

`marketId` is sticky: it never changes for a market and never gets reused.
`marketType` and `period` are descriptive and can be corrected — as they
are on [2026-10-05](/changelog/index.md), when 26 sports move their `1x2` from
`fulltime` to `result` under exactly the rule above. Clients keyed on
`marketId` see nothing; clients keyed on `marketType` × `period` have to
regroup.

The period vocabulary is **append-only** — new sports introduce new keys
(esports round grids are the most recent). Pass unknown period strings through
rather than rejecting them.

***

## Related

> **[Enumerations → period](/api-reference/enumerations.md#period)**
>
> The full reference list of period values.

> **[Market coverage](/coverage/markets.md)**
>
> Which market types exist per sport, and how they are scoped.

> **[Parlays & SGP](/api-reference/parlays.md)**
>
> Combining legs across periods and fixtures.

> **[Changelog](/changelog/index.md)**
>
> Registry changes, including period corrections.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.