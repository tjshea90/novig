> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Fixture Market Coverage - marketType x period per Sport

> The OddsPapi fixture-market coverage map: every marketType x period combination published per sport, and which of them settle from a collected counter.

**These are the FIXTURE markets** — the markets of a single match, bout or game. Outright and season-long markets are a separate taxonomy with its own ids; see [future markets and outcomes](https://docs.oddspapi.io/api-reference/concepts.md#future-markets-and-outcomes).

Markets are not a fixed list per sport — they are generated from a deterministic decomposition, so the same bet resolves to the same identifiers everywhere. **This page is only the coverage map: which combinations exist.** How a market is addressed, `marketLength`, id structure and the odds keys are in [Markets and outcomes](https://docs.oddspapi.io/api-reference/concepts.md#markets-and-outcomes); the live catalogue for your key is [`GET /markets`](https://docs.oddspapi.io/api-reference/common/get-markets).

Every `marketType` we publish, per sport, against the period scopes it covers. Pick a sport, then open a market group.

> [!NOTE]
>
> **✓** covered, and settles from a counter we collect · **◦** covered, but settlement is not counter-derived · **✓◦** covered at several scopes inside a collapsed grid column, only some settleable · blank — not covered. A mark means the combination exists, not how many line values it carries — every distinct line is its own `marketId`, so read the ladder from [`GET /markets`](https://docs.oddspapi.io/api-reference/common/get-markets).
>
> `…-team{1,2}` is **one row for two market types** — the `-team1` and `-team2` siblings, which cover the same scopes. Expand the brace to get the real `marketType`.
>
> **Columns** are ordered by how many market types cover them, densest first. **Rows** follow the market's own id, so the base markets come before the long tail of props.

> [tab] Soccer
>
> **110** market types · **6** period scopes · `sportId` `10`
>
> > [accordion] Match outcome (23)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `overtime` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `1x2` | ✓ | | ✓ | ✓ | ✓ | |
> > | `bothteamsscore` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `drawnobet` | ✓ | | ✓ | ✓ | ✓ | |
> > | `oddeven` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `toscore-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `toscoreinbh-team{1,2}` | ✓ | | | | | |
> > | `cleansheet-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `wintonil-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `nobet-team{1,2}` | ✓ | | ✓ | ✓ | ✓ | |
> > | `oddeven-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `correctscore` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `1x2-corners` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `1x2-bookings` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven-bookings` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven-bookings-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven-corners` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven-corners-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `doublechance` | ✓ | | ✓ | ✓ | ✓ | |
> > | `halftime-fulltime` | ✓ | | | | | |
> > | `winningmargin` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `exactscore` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `exactscore-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `1x2-shotsongoal` | ✓ | ✓ | ✓ | ✓ | ✓ | |
>
> > [accordion] Laddered lines (27)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `overtime` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads-european` | ✓ | | ✓ | ✓ | ✓ | |
> > | `teamtotals-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `totals-corners` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads-corners` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `totals-bookings` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads-bookings` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-bookings-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-corners-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `totals-saves` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `teamtotals-saves-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `totals-substitutions` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-substitutions-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `totals-penalties-awarded` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `teamtotals-penalties-awarded-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `totals-penalties-missed` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `teamtotals-penalties-missed-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `totals-own-goals` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-own-goals-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `racetocorners` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `totals-shots` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-shots-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `totals-red-cards` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `totals-yellow-cards` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-red-cards-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-yellow-cards-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
>
> > [accordion] Player props (Yes/No) (13)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `overtime` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `players-anytimegoalscorer` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `players-firstgoalscorer` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `players-lastgoalscorer` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `players-goals` | | ✓ | | | | |
> > | `players-assists` | | ✓ | | | | |
> > | `players-shots` | | ◦ | | | | |
> > | `players-shotsongoal` | | ✓ | | | | |
> > | `players-tackles` | | ◦ | | | | |
> > | `players-foulscommitted` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `players-offsides` | | ✓ | | | | |
> > | `players-cards` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `players-saves` | | ◦ | | | | |
> > | `players-firstcard` | ◦ | ◦ | ◦ | ◦ | ◦ | |
>
> > [accordion] Player totals (laddered) (9)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `overtime` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-goals` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `playertotals-assists` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `playertotals-shots` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `playertotals-shotsongoal` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `playertotals-tackles` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `playertotals-foulscommitted` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `playertotals-offsides` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `playertotals-cards` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `playertotals-saves` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
>
> > [accordion] Specials and order (15)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `overtime` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `firstgoal` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `lastgoal` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `wineitherh-team{1,2}` | ✓ | | | | | |
> > | `winbothh-team{1,2}` | ✓ | | | | | |
> > | `toqualify` | ◦ | ◦ | | | | |
> > | `2up` | ◦ | | | | | |
> > | `highestscoringh` | ✓ | | | | | |
> > | `highestscoringh-team{1,2}` | ✓ | | | | | |
> > | `winfrombehind` | ◦ | | | | | |
> > | `1up` | ◦ | | | | | |
> > | `firstcorner` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `lastcorner` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `penaltyawarded` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `highestscoringh-bookings` | ◦ | | | | | |
> > | `highestscoringh-corners` | ◦ | | | | | |

> [tab] Basketball
>
> **36** market types · **10** period scopes · `sportId` `11`
>
> > [accordion] Match outcome (3)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p1+p2` | `p3+p4` | `p3+p4+overtime` | `p4+overtime` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | |
> > | `1x2` | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | ✓ |
> > | `oddeven` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
>
> > [accordion] Laddered lines (3)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p1+p2` | `p3+p4` | `p3+p4+overtime` | `p4+overtime` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
>
> > [accordion] Player props (Yes/No) (16)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p1+p2` | `p3+p4` | `p3+p4+overtime` | `p4+overtime` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `players-points` | ✓ | | | | | | | | | |
> > | `players-rebounds` | ✓ | | | | | | | | | |
> > | `players-3pointers` | ✓ | | | | | | | | | |
> > | `players-ptsassreb` | ✓ | | | | | | | | | |
> > | `players-assists` | ✓ | | | | | | | | | |
> > | `players-assreb` | ✓ | | | | | | | | | |
> > | `players-ptsreb` | ✓ | | | | | | | | | |
> > | `players-ptsass` | ✓ | | | | | | | | | |
> > | `players-steals` | ✓ | | | | | | | | | |
> > | `players-blocks` | ✓ | | | | | | | | | |
> > | `players-turnovers` | ✓ | | | | | | | | | |
> > | `players-stlblo` | ✓ | | | | | | | | | |
> > | `players-doubledouble` | ◦ | | | | | | | | | |
> > | `players-tripletriple` | ◦ | | | | | | | | | |
> > | `players-firstpoint` | ◦ | | | | | | | | | |
> > | `players-first3pointer` | ◦ | | | | | | | | | |
>
> > [accordion] Player totals (laddered) (13)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p1+p2` | `p3+p4` | `p3+p4+overtime` | `p4+overtime` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-points` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-rebounds` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-3pointers` | ✓ | | | | | | | | | |
> > | `playertotals-ptsassreb` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-assists` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-assreb` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-ptsreb` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-ptsass` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-steals` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-blocks` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-turnovers` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-stlblo` | ✓ | ✓ | | | | | | | | |
> > | `playertotals-3pointer` | | ✓ | | | | | | | | |

> [tab] Tennis
>
> **61** market types · **8** period scopes · `sportId` `12`
>
> > [accordion] Match outcome (14)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `games` | `game-spans` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `moneyline-aces` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `oddeven-games` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `correctscore` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ |
> > | `oddeven-sets` | ✓ | | | | | | | |
> > | `correctscore-points` | | | | | | | ◦ | |
> > | `oddeven-points` | ✓ | | | | | | ✓ | |
> > | `drawnobet-aces` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `drawnobet-doublefaults` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `moneyline-doublefaults` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `drawnobet-breaks` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `moneyline-breaks` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `moneyline-games` | ✓ | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | |
>
> > [accordion] Laddered lines (23)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `games` | `game-spans` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals-games` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `spreads-games` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `totals` | ✓ | | | | | | | |
> > | `spreads` | ✓ | | | | | | | |
> > | `totals-tiebreaks` | ✓ | | | | | | | |
> > | `totals-aces` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `teamtotals-games-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `totals-points` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads-points` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-aces-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `spreads-aces` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `totals-doublefaults` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `teamtotals-doublefaults-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `spreads-doublefaults` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `totals-breaks` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `teamtotals-breaks-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `spreads-breaks` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `totals-breakpoints` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `teamtotals-breakpoints-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `racetogames` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | |
> > | `totals-servicegames` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `teamtotals-servicegames-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `totals-duration` | ◦ | | | | | | | |
>
> > [accordion] Specials and order (14)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `games` | `game-spans` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `winaset-team{1,2}` | ✓ | | | | | | | |
> > | `tiebreak` | | ✓ | ✓ | ✓ | ✓ | ✓ | | |
> > | `exactsets` | ✓ | | | | | | | |
> > | `anysettonil` | ✓ | | | | | | | |
> > | `bothwinset` | ✓ | | | | | | | |
> > | `exactsets-team{1,2}` | ✓ | | | | | | | |
> > | `gamewinner` | | | | | | | ✓ | |
> > | `deuce` | | | | | | | ◦ | |
> > | `firstbreak` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | |
> > | `winfrombehind` | ◦ | | | | | | | |
> > | `winfirstservicegame-team{1,2}` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | |
> > | `anysettonil-team{1,2}` | ◦ | | | | | | | |
> > | `exactaces` | ✓ | | | | | | | |
> > | `exactdoublefaults` | ✓ | | | | | | | |

> [tab] Baseball
>
> **45** market types · **13** period scopes · `sportId` `13`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1+p2+p3+p4+p5` | `p6+p7+p8+p9+overtime` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | | | | | | | | | | |
> > | `1x2` | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `oddeven` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven-team{1,2}` | ✓ | ✓ | ✓ | | | | | | | | | | |
> > | `correctscore` | ✓ | ✓ | ✓ | | | | | | | | | | |
>
> > [accordion] Laddered lines (3)
> >
> > | `marketType` | `result` | `p1+p2+p3+p4+p5` | `p6+p7+p8+p9+overtime` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-team{1,2}` | ✓ | ✓ | ✓ | | | | | | | | | | |
>
> > [accordion] Player props (Yes/No) (15)
> >
> > | `marketType` | `result` | `p1+p2+p3+p4+p5` | `p6+p7+p8+p9+overtime` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `players-hits` | ✓ | | | | | | | | | | | | |
> > | `players-runs` | ✓ | | | | | | | | | | | | |
> > | `players-bases` | ✓ | | | | | | | | | | | | |
> > | `players-runsbattedin` | ◦ | | | | | | | | | | | | |
> > | `players-strikeouts` | ✓ | | | | | | | | | | | | |
> > | `players-homeruns` | ✓ | | | | | | | | | | | | |
> > | `players-stolenbases` | ✓ | | | | | | | | | | | | |
> > | `players-earnedruns` | ◦ | | | | | | | | | | | | |
> > | `players-hitsallowed` | ◦ | | | | | | | | | | | | |
> > | `players-outs` | ✓ | | | | | | | | | | | | |
> > | `players-walks` | ✓ | | | | | | | | | | | | |
> > | `players-singles` | ✓ | | | | | | | | | | | | |
> > | `players-doubles` | ✓ | | | | | | | | | | | | |
> > | `players-triples` | ✓ | | | | | | | | | | | | |
> > | `players-hitsrunsunsbti` | ◦ | | | | | | | | | | | | |
>
> > [accordion] Player totals (laddered) (15)
> >
> > | `marketType` | `result` | `p1+p2+p3+p4+p5` | `p6+p7+p8+p9+overtime` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-hits` | ✓ | | | | | | | | | | | | |
> > | `playertotals-runs` | ✓ | | | | | | | | | | | | |
> > | `playertotals-bases` | ✓ | | | | | | | | | | | | |
> > | `playertotals-runsbattedin` | ◦ | | | | | | | | | | | | |
> > | `playertotals-strikeouts` | ✓ | | | | | | | | | | | | |
> > | `playertotals-homeruns` | ✓ | | | | | | | | | | | | |
> > | `playertotals-stolenbases` | ✓ | | | | | | | | | | | | |
> > | `playertotals-earnedruns` | ◦ | | | | | | | | | | | | |
> > | `playertotals-hitsallowed` | ◦ | | | | | | | | | | | | |
> > | `playertotals-outs` | ✓ | | | | | | | | | | | | |
> > | `playertotals-walks` | ✓ | | | | | | | | | | | | |
> > | `playertotals-singles` | ✓ | | | | | | | | | | | | |
> > | `playertotals-doubles` | ✓ | | | | | | | | | | | | |
> > | `playertotals-triples` | ✓ | | | | | | | | | | | | |
> > | `playertotals-hitsrunsrunsbi` | ◦ | | | | | | | | | | | | |
>
> > [accordion] Specials and order (4)
> >
> > | `marketType` | `result` | `p1+p2+p3+p4+p5` | `p6+p7+p8+p9+overtime` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` | `fulltime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `firsttoscorearun` | ◦ | | | | | | | | | | | | |
> > | `extrainning` | | | | | | | | | | | | | ✓ |
> > | `exactruns` | ✓ | ✓ | ✓ | | | | | | | | | | |
> > | `exactruns-team{1,2}` | ✓ | ✓ | ✓ | | | | | | | | | | |

> [tab] American Football
>
> **200** market types · **12** period scopes · `sportId` `14`
>
> > [accordion] Match outcome (25)
> >
> > | `marketType` | `result` | `p1+p2` | `p3+p4` | `p1` | `p2` | `p3` | `p4` | `fulltime` | `p3+p4+overtime` | `p4+overtime` | `overtime` | `p1+p2+p3+p4` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | |
> > | `1x2` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | |
> > | `oddeven-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | |
> > | `halftime-fulltime` | | | | | | | | ✓ | | | | |
> > | `doublechance` | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `drawnobet` | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `winningmargin` | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `toscore-team{1,2}` | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `cleansheet-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `wintonil-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `exactscore` | ✓ | ✓ | ✓ | | | | | | | | | |
> > | `exactscore-team{1,2}` | ✓ | ✓ | ✓ | | | | | | | | | |
> > | `toscoreinbh-team{1,2}` | | | | | | | | ✓ | | | | |
> > | `oddeven-touchdowns` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `1x2-touchdowns` | | | | | | | | ✓ | | | | |
> > | `1x2-fieldgoals` | | | | | | | | ✓ | | | | |
> > | `drawnobet-touchdowns` | ✓ | ✓ | ✓ | | | | | | | | | |
> > | `drawnobet-fieldgoals` | ✓ | ✓ | ✓ | | | | | | | | | |
> > | `moneyline-turnovers` | ◦ | | | | | | | | | | | |
> > | `moneyline-sacks` | ◦ | | | | | | | | | | | |
> > | `moneyline-totalyards` | ◦ | | | | | | | | | | | |
> > | `moneyline-firstdowns` | ◦ | | | | | | | | | | | |
> > | `moneyline-penalties` | ◦ | | | | | | | | | | | |
> > | `nobet-team{1,2}` | | | | | | | | ✓ | | | | |
>
> > [accordion] Laddered lines (41)
> >
> > | `marketType` | `result` | `p1+p2` | `p3+p4` | `p1` | `p2` | `p3` | `p4` | `fulltime` | `p3+p4+overtime` | `p4+overtime` | `overtime` | `p1+p2+p3+p4` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | |
> > | `spreads` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | |
> > | `teamtotals-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | |
> > | `totals-touchdowns` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `totals-fieldgoals` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads-european` | | ✓ | ✓ | | | | | ✓ | ✓ | | | |
> > | `bothteamsscore` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `teamtotals-touchdowns-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `teamtotals-fieldgoals-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `racetopoints` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | ◦ | | | |
> > | `totals-turnovers` | ◦ | | | | | | | | | | | |
> > | `totals-sacks` | ◦ | | | | | | | | | | | |
> > | `totals-interceptions` | ◦ | | | | | | | | | | | |
> > | `totals-fumbles` | ◦ | | | | | | | | | | | |
> > | `totals-defensivetd` | ◦ | | | | | | | | | | | |
> > | `totals-firstdowns` | ◦ | | | | | | | | | | | |
> > | `totals-penalties` | ◦ | | | | | | | | | | | |
> > | `totals-punts` | ◦ | | | | | | | | | | | |
> > | `totals-thirddownconversions` | ◦ | | | | | | | | | | | |
> > | `totals-redzonetd` | ◦ | | | | | | | | | | | |
> > | `totals-extrapoints` | ◦ | | | | | | | | | | | |
> > | `totals-totalyards` | ◦ | | | | | | | | | | | |
> > | `totals-penaltyyards` | ◦ | | | | | | | | | | | |
> > | `totals-puntyards` | ✓ | | | | | | | | | | | |
> > | `teamtotals-turnovers-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-sacks-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-interceptions-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-fumbles-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-defensivetd-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-firstdowns-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-penalties-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-punts-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-thirddownconversions-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-redzonetd-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-extrapoints-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-totalyards-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-penaltyyards-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `teamtotals-puntyards-team{1,2}` | ✓ | | | | | | | | | | | |
> > | `firstdriverunyards-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `firstdrivepassyards-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `firstdriveplayyards-team{1,2}` | ◦ | | | | | | | | | | | |
>
> > [accordion] Player props (Yes/No) (34)
> >
> > | `marketType` | `result` | `p1+p2` | `p3+p4` | `p1` | `p2` | `p3` | `p4` | `fulltime` | `p3+p4+overtime` | `p4+overtime` | `overtime` | `p1+p2+p3+p4` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `players-td` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ | | |
> > | `players-firsttd` | ✓ | | | | | | | | | | | |
> > | `players-secondtd` | ✓ | | | | | | | | | | | |
> > | `players-thirdtd` | ✓ | | | | | | | | | | | |
> > | `players-td-other` | ✓ | ✓ | | ✓ | | | | | ✓ | | | |
> > | `players-passyards` | ✓ | | | | | | | | | | | |
> > | `players-rushyards` | ✓ | | | | | | | | | | | |
> > | `players-rushattempts` | ✓ | | | | | | | | | | | |
> > | `players-rushtd` | ✓ | | | | | | | | | | | |
> > | `players-sacks` | ✓ | | | | | | | | | | | |
> > | `players-assists` | ◦ | | | | | | | | | | | |
> > | `players-tacass` | ✓ | | | | | | | | | | | |
> > | `players-fieldgoals` | ✓ | | | | | | | | | | | |
> > | `players-kickingpoints` | ✓ | | | | | | | | | | | |
> > | `players-receptions` | ✓ | | | | | | | | | | | |
> > | `players-passattempts` | ✓ | | | | | | | | | | | |
> > | `players-passcompletions` | ✓ | | | | | | | | | | | |
> > | `players-tdpasses` | ✓ | | | | | | | | | | | |
> > | `players-interceptions` | ✓ | | | | | | | | | | | |
> > | `players-receivingyards` | ✓ | | | | | | | | | | | |
> > | `players-longestpasscomp` | ✓ | | | | | | | | | | | |
> > | `players-longestrushyards` | ✓ | | | | | | | | | | | |
> > | `players-lasttd` | ◦ | | | | | | | | | | | |
> > | `players-longestreception` | ◦ | | | | | | | | | | | |
> > | `players-extrapoints` | ◦ | | | | | | | | | | | |
> > | `players-interceptionsthrown` | ◦ | | | | | | | | | | | |
> > | `players-rushrec` | ◦ | | | | | | | | | | | |
> > | `players-top-passer` | ◦ | | | | | | | | | | | |
> > | `players-top-rusher` | ◦ | | | | | | | | | | | |
> > | `players-top-receiver` | ◦ | | | | | | | | | | | |
> > | `players-passyardsinbh` | | | | | | | | ◦ | | | | |
> > | `players-rushyardsinbh` | | | | | | | | ◦ | | | | |
> > | `players-receivingyardsinbh` | | | | | | | | ◦ | | | | |
> > | `players-firstpoint` | ◦ | | | | | | | | | | | |
>
> > [accordion] Player totals (laddered) (22)
> >
> > | `marketType` | `result` | `p1+p2` | `p3+p4` | `p1` | `p2` | `p3` | `p4` | `fulltime` | `p3+p4+overtime` | `p4+overtime` | `overtime` | `p1+p2+p3+p4` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-tdpasses` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-interceptions` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-receptions` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-passyards` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-passattempts` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-passcompletions` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-longestpasscomp` | ✓ | | | | | | | | | | | |
> > | `playertotals-rushyards` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-rushattempts` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-longestrushyards` | ✓ | | | | | | | | | | | |
> > | `playertotals-rushtd` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-sacks` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-assists` | ◦ | | | | | | | | | | | |
> > | `playertotals-tacass` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-fieldgoals` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-kickingpoints` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-td` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-receivingyards` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | |
> > | `playertotals-longestreception` | ◦ | | | | | | | | | | | |
> > | `playertotals-extrapoints` | ◦ | | | | | | | | | | | |
> > | `playertotals-interceptionsthrown` | ◦ | | | | | | | | | | | |
> > | `playertotals-rushrec` | ◦ | | | | | | | | | | | |
>
> > [accordion] Specials and order (36)
> >
> > | `marketType` | `result` | `p1+p2` | `p3+p4` | `p1` | `p2` | `p3` | `p4` | `fulltime` | `p3+p4+overtime` | `p4+overtime` | `overtime` | `p1+p2+p3+p4` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `overtime` | | | | | | | | ✓ | | | | |
> > | `exacttd` | ✓ | ✓ | ✓ | | | | | | | | | |
> > | `exactfieldgoals` | ✓ | ✓ | ✓ | | | | | | | | | |
> > | `highestscoringh` | | | | | | | | ✓ | | | | |
> > | `highestscoringh-team{1,2}` | | | | | | | | ✓ | | | | |
> > | `highestscoringperiod` | | | | | | | | ✓ | | | | |
> > | `highestscoringperiod-team{1,2}` | | | | | | | | ✓ | | | | |
> > | `coin-toss` | ◦ | | | | | | | | | | | |
> > | `wineitherh-team{1,2}` | | | | | | | | ✓ | | | | |
> > | `winbothh-team{1,2}` | | | | | | | | ✓ | | | | |
> > | `exacttd-team{1,2}` | ✓ | ✓ | ✓ | | | | | | | | | |
> > | `firstscore` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | ◦ | | | |
> > | `lastscore` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | ◦ | | | |
> > | `firsttd` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | | | | |
> > | `firstfieldgoal` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | | | | |
> > | `touchdown` | | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | | | | | |
> > | `twopointconversion` | ◦ | ◦ | ◦ | | | | | | | | | |
> > | `scorelessquarter` | | | | | | | | ◦ | | | | |
> > | `firstteamtoscorewins` | ◦ | | | | | | | | | | | |
> > | `safety` | ◦ | ◦ | ◦ | | | | | | | | | |
> > | `safety-team{1,2}` | ◦ | ◦ | ◦ | | | | | | | | | |
> > | `firstdrivescore-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `winanyperiod-team{1,2}` | | | | | | | | | | | | ✓ |
> > | `winallperiods-team{1,2}` | | | | | | | | | | | | ✓ |
> > | `scoreinallperiods-team{1,2}` | | | | | | | | | | | | ✓ |
> > | `bothwinperiod` | | | | | | | | | | | | ✓ |
> > | `anyperiodtonil` | | | | | | | | | | | | ✓ |
> > | `bothscoreinallperiods` | | | | | | | | | | | | ◦ |
> > | `firstdrivefirstdown-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `firstdrivesack-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `firstdrivefourthdown-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `firstdrivecross-team{1,2}` | ◦ | | | | | | | | | | | |
> > | `firstscoringplay` | ◦ | ◦ | ◦ | | | | | | | | | |
> > | `winfrombehind` | | | | | | | | ◦ | | | | |
> > | `tied-match` | ✓ | | | | | | | | | | | |
> > | `firsttd-team{1,2}` | ◦ | | | | | | | | | | | |

> [tab] Ice Hockey
>
> **52** market types · **6** period scopes · `sportId` `15`
>
> > [accordion] Match outcome (8)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `p3` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | | ✓ | ✓ | ✓ | ✓ | |
> > | `1x2` | ✓ | | ✓ | ✓ | ✓ | |
> > | `correctscore` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `winningmargin` | ✓ | ✓ | | | | |
> > | `oddeven` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `toscore-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `bothteamsscore` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `wintonil-team{1,2}` | ✓ | ✓ | | | | |
>
> > [accordion] Laddered lines (4)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `p3` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads-european` | ✓ | ✓ | ✓ | ✓ | ✓ | |
>
> > [accordion] Player props (Yes/No) (19)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `p3` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `players-assists` | | | | | | ✓ |
> > | `players-goals` | | | ✓ | ✓ | ✓ | ✓ |
> > | `players-points` | | | ◦ | ◦ | ◦ | ◦ |
> > | `players-shotsongoal` | | | ✓ | ✓ | ✓ | ✓ |
> > | `players-anytimegoalscorer` | ✓ | | ✓ | ✓ | ✓ | |
> > | `players-firstgoalscorer` | ◦ | | ◦ | ◦ | ◦ | |
> > | `players-secondgoalscorer` | ◦ | | | | | |
> > | `players-thirdgoalscorer` | ◦ | | | | | |
> > | `players-nextgoalscorer` | ◦ | | | | | |
> > | `players-lastgoalscorer` | ◦ | | | | | |
> > | `players-shots` | | | | | | ◦ |
> > | `players-powerplaypoints` | | | | | | ◦ |
> > | `players-blocks` | | | | | | ✓ |
> > | `players-saves` | | | | | | ✓ |
> > | `players-goalsagainst` | | | | | | ◦ |
> > | `players-goalieshutouts` | | | | | | ◦ |
> > | `players-shorthandedgoals` | | | | | | ◦ |
> > | `players-shorthandedgoalscorer` | ◦ | | | | | |
> > | `players-commitpenalty` | | | | | | ✓ |
>
> > [accordion] Player totals (laddered) (11)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `p3` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-assists` | | ✓ | | | | |
> > | `playertotals-goals` | | ✓ | ✓ | ✓ | ✓ | |
> > | `playertotals-points` | | ◦ | ◦ | ◦ | ◦ | |
> > | `playertotals-shotsongoal` | | ✓ | ✓ | ✓ | ✓ | |
> > | `playertotals-shots` | | | | | | ◦ |
> > | `playertotals-powerplaypoints` | | | | | | ◦ |
> > | `playertotals-blocks` | | ✓ | | | | |
> > | `playertotals-saves` | | ✓ | | | | |
> > | `playertotals-goalsagainst` | | | | | | ◦ |
> > | `playertotals-goalieshutouts` | | | | | | ◦ |
> > | `playertotals-shorthandedgoals` | | | | | | ◦ |
>
> > [accordion] Specials and order (6)
> >
> > | `marketType` | `fulltime` | `result` | `p1` | `p2` | `p3` | `fulltime+overtime` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `exactgoals` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `exactgoals-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `firstgoal` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `lastgoal` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `highestscoringperiod` | ✓ | | | | | |
> > | `overtime` | ✓ | | | | | |

> [tab] ESport Dota
>
> **32** market types · **6** period scopes · `sportId` `16`
>
> > [accordion] Match outcome (8)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `correctscore` | ✓ | | | | | |
> > | `winningmargin` | ✓ | | | | | |
> > | `1x2-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `drawnobet-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `oddeven-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | |
> > | `oddeven` | ✓ | | | | | |
>
> > [accordion] Laddered lines (15)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | |
> > | `spreads` | ✓ | | | | | |
> > | `totals-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `spreads-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `teamtotals-kills-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `totals-towers` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `spreads-towers` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `teamtotals-towers-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `totals-barracks` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `spreads-barracks` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `teamtotals-barracks-team{1,2}` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `totals-roshans` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `teamtotals-roshans-team{1,2}` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `spreads-roshans` | | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `racetokills` | | ◦ | ◦ | ◦ | ◦ | ◦ |
>
> > [accordion] Player totals (laddered) (1)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-kills` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
>
> > [accordion] Specials and order (4)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `firstblood` | | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `firsttower` | | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `firstbarracks` | | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `firstroshan` | | ◦ | ◦ | ◦ | ◦ | ◦ |

> [tab] ESport Counter-Strike
>
> **20** market types · **7** period scopes · `sportId` `17`
>
> > [accordion] Match outcome (7)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `correctscore` | ✓ | | | | | | |
> > | `winningmargin` | ✓ | | | | | | |
> > | `winningmargin-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `1x2` | ✓ | | | | | | |
> > | `oddeven` | ✓ | | | | | | |
>
> > [accordion] Laddered lines (8)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | |
> > | `spreads` | ✓ | | | | | | |
> > | `totals-rounds` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `totals-kills` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `teamtotals-kills-team{1,2}` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `totals-headshots` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `spreads-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-rounds-team{1,2}` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
>
> > [accordion] Player totals (laddered) (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-kills` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `playertotals-headshots` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | |
>
> > [accordion] Specials and order (1)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `overtime` | | ✓ | ✓ | ✓ | ✓ | ✓ | |

> [tab] ESport League of Legends
>
> **37** market types · **6** period scopes · `sportId` `18`
>
> > [accordion] Match outcome (8)
> >
> > | `marketType` | `p1` | `p2` | `p3` | `p4` | `p5` | `result` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `correctscore` | | | | | | ✓ |
> > | `winningmargin` | | | | | | ✓ |
> > | `1x2-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `drawnobet-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `oddeven-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | | | | | | ✓ |
> > | `oddeven` | | | | | | ✓ |
>
> > [accordion] Laddered lines (17)
> >
> > | `marketType` | `p1` | `p2` | `p3` | `p4` | `p5` | `result` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | | | | | | ✓ |
> > | `spreads` | | | | | | ✓ |
> > | `totals-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `spreads-kills` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `teamtotals-kills-team{1,2}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `totals-turrets` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `spreads-turrets` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `teamtotals-turrets-team{1,2}` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `totals-dragons` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `spreads-dragons` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `teamtotals-dragons-team{1,2}` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `totals-barons` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `teamtotals-barons-team{1,2}` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `totals-inhibitors` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `spreads-inhibitors` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `teamtotals-inhibitors-team{1,2}` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
> > | `racetokills` | ◦ | ◦ | ◦ | ◦ | ◦ | |
>
> > [accordion] Player totals (laddered) (1)
> >
> > | `marketType` | `p1` | `p2` | `p3` | `p4` | `p5` | `result` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-kills` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ |
>
> > [accordion] Specials and order (6)
> >
> > | `marketType` | `p1` | `p2` | `p3` | `p4` | `p5` | `result` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `firstblood` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `firstbaron` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `firstinhibitor` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `triplekill` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `quadrakill` | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `pentakill` | ◦ | ◦ | ◦ | ◦ | ◦ | |

> [tab] Darts
>
> **5** market types · **1** period scopes · `sportId` `19`
>
> > [accordion] Match outcome (3)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `moneyline-180s` | ✓ |
> > | `oddeven` | ✓ |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `totals` | ✓ |
> > | `spreads` | ✓ |

> [tab] MMA
>
> **12** market types · **1** period scopes · `sportId` `20`
>
> > [accordion] Match outcome (3)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |
> > | `oddeven` | ✓ |
>
> > [accordion] Laddered lines (1)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `totals` | ✓ |
>
> > [accordion] Specials and order (5)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `methodofvictory` | ◦ |
> > | `gothedistance` | ◦ |
> > | `winbyko-team{1,2}` | ◦ |
> > | `winbysubmission-team{1,2}` | ◦ |
> > | `winbydecision-team{1,2}` | ◦ |

> [tab] Boxing
>
> **6** market types · **1** period scopes · `sportId` `21`
>
> > [accordion] Match outcome (3)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |
> > | `oddeven` | ✓ |
>
> > [accordion] Laddered lines (1)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `totals` | ✓ |
>
> > [accordion] Specials and order (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `methodofvictory` | ◦ |
> > | `gothedistance` | ◦ |

> [tab] Handball
>
> **5** market types · **2** period scopes · `sportId` `22`
>
> > [accordion] Match outcome (3)
> >
> > | `marketType` | `fulltime` | `result` |
> > | - | :-: | :-: |
> > | `moneyline` | | ✓ |
> > | `1x2` | ✓ | |
> > | `oddeven` | ✓ | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `fulltime` | `result` |
> > | - | :-: | :-: |
> > | `totals` | ✓ | |
> > | `spreads` | ✓ | |

> [tab] Volleyball
>
> **4** market types · **6** period scopes · `sportId` `23`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `oddeven` | ✓ | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | |
> > | `spreads` | ✓ | | | | | |

> [tab] Snooker
>
> **4** market types · **1** period scopes · `sportId` `24`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `oddeven` | ✓ |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `totals` | ✓ |
> > | `spreads` | ✓ |

> [tab] Table Tennis
>
> **3** market types · **1** period scopes · `sportId` `25`
>
> > [accordion] Match outcome (1)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `totals-points` | ✓ |
> > | `spreads-points` | ✓ |

> [tab] Rugby
>
> **5** market types · **2** period scopes · `sportId` `26`
>
> > [accordion] Match outcome (3)
> >
> > | `marketType` | `fulltime` | `result` |
> > | - | :-: | :-: |
> > | `moneyline` | | ✓ |
> > | `1x2` | ✓ | |
> > | `oddeven` | ✓ | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `fulltime` | `result` |
> > | - | :-: | :-: |
> > | `totals` | ✓ | |
> > | `spreads` | ✓ | |

> [tab] Cricket
>
> **48** market types · **7** period scopes · `sportId` `27`
>
> > [accordion] Match outcome (4)
> >
> > | `marketType` | `result` | `p1` | `p2` | `fulltime` | `over-spans` | `overs` | `over-deliveries` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | | | | | | |
> > | `1x2` | | | | ✓ | | | |
> > | `oddeven-1st-over` | | ◦ | | | | | |
> > | `oddeven` | | ✓ | ✓ | | | | |
>
> > [accordion] Laddered lines (11)
> >
> > | `marketType` | `result` | `p1` | `p2` | `fulltime` | `over-spans` | `overs` | `over-deliveries` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals-fours` | ◦ | ◦ | ◦ | | | | |
> > | `totals-sixes` | ◦ | ◦ | ◦ | | | | |
> > | `totals` | | ✓ | ✓ | | ◦ | ◦ | |
> > | `1st-wicket-fall` | ◦ | | | | | | |
> > | `highest-individual-score` | ✓ | | | | | | |
> > | `teamtotals-team{1,2}` | ✓ | | | | ◦ | ◦ | ◦ |
> > | `teamtotals-fours-team{1,2}` | ✓ | | | | | | |
> > | `teamtotals-sixes-team{1,2}` | ✓ | | | | | | |
> > | `teamtotals-match-fours-team{1,2}` | ◦ | | | | | | |
> > | `teamtotals-match-sixes-team{1,2}` | ◦ | | | | | | |
> > | `totals-match-run-outs` | ◦ | | | | | | |
>
> > [accordion] Player props (Yes/No) (9)
> >
> > | `marketType` | `result` | `p1` | `p2` | `fulltime` | `over-spans` | `overs` | `over-deliveries` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `players-top-batter` | ✓ | ✓ | ✓ | | | | |
> > | `players-top-bowler` | ◦ | ◦ | ◦ | | | | |
> > | `players-top-batter-team{1,2}` | ✓ | ✓ | ✓ | | | | |
> > | `players-top-bowler-team{1,2}` | ◦ | ◦ | ◦ | | | | |
> > | `players-skores-10-run` | ✓ | ✓ | ✓ | | | | |
> > | `players-skores-20-run` | ✓ | ✓ | ✓ | | | | |
> > | `players-skores-30-run` | ✓ | ✓ | ✓ | | | | |
> > | `players-skores-40-run` | ✓ | ✓ | ✓ | | | | |
> > | `players-skores-50-run` | ✓ | ✓ | ✓ | | | | |
>
> > [accordion] Player totals (laddered) (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `fulltime` | `over-spans` | `overs` | `over-deliveries` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-runs` | ✓ | ✓ | ✓ | | | | |
> > | `playertotals-performance` | ◦ | | | | | | |
> > | `playertotals-fours` | ✓ | | | | | | |
> > | `playertotals-sixes` | ✓ | | | | | | |
> > | `playertotals-wickets` | ◦ | | | | | | |
>
> > [accordion] Specials and order (12)
> >
> > | `marketType` | `result` | `p1` | `p2` | `fulltime` | `over-spans` | `overs` | `over-deliveries` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `tied-match` | | | | ✓ | | | |
> > | `coin-toss` | | | | ◦ | | | |
> > | `completed-match` | | | | ◦ | | | |
> > | `mvp` | ◦ | | | | | | |
> > | `1st-wicket-method` | ◦ | | | | | | |
> > | `1st-wicket-method-caught` | ◦ | | | | | | |
> > | `highest-opening-partnership` | ◦ | | | | | | |
> > | `most-run-outs` | ◦ | | | | | | |
> > | `most-match-fours` | ◦ | | | | | | |
> > | `most-match-sixes` | ◦ | | | | | | |
> > | `score-50-in-match` | ✓ | | | | | | |
> > | `score-100-in-match` | ✓ | | | | | | |

> [tab] Waterpolo
>
> **2** market types · **2** period scopes · `sportId` `28`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Futsal
>
> **2** market types · **2** period scopes · `sportId` `29`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Beach Volley
>
> **2** market types · **1** period scopes · `sportId` `30`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Aussie Rules
>
> **2** market types · **2** period scopes · `sportId` `31`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Field hockey
>
> **2** market types · **2** period scopes · `sportId` `32`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Floorball
>
> **2** market types · **2** period scopes · `sportId` `33`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Squash
>
> **2** market types · **1** period scopes · `sportId` `34`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Basketball 3x3
>
> **2** market types · **1** period scopes · `sportId` `35`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Beach Soccer
>
> **2** market types · **2** period scopes · `sportId` `36`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Pesapallo
>
> **2** market types · **2** period scopes · `sportId` `37`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Lacrosse
>
> **2** market types · **2** period scopes · `sportId` `38`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Curling
>
> **2** market types · **1** period scopes · `sportId` `39`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Padel
>
> **2** market types · **1** period scopes · `sportId` `40`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Bandy
>
> **2** market types · **2** period scopes · `sportId` `41`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Kabaddi
>
> **2** market types · **2** period scopes · `sportId` `42`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Rink Hockey
>
> **2** market types · **2** period scopes · `sportId` `43`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Soccer Specials
>
> **2** market types · **1** period scopes · `sportId` `44`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Gaelic Football
>
> **2** market types · **2** period scopes · `sportId` `45`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Netball
>
> **2** market types · **2** period scopes · `sportId` `46`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Beach Handball
>
> **2** market types · **1** period scopes · `sportId` `47`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Athletics
>
> **2** market types · **1** period scopes · `sportId` `48`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Badminton
>
> **2** market types · **1** period scopes · `sportId` `49`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Bowls
>
> **2** market types · **1** period scopes · `sportId` `50`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Cross-Country
>
> **2** market types · **1** period scopes · `sportId` `51`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Gaelic Hurling
>
> **2** market types · **2** period scopes · `sportId` `52`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] Softball
>
> **2** market types · **2** period scopes · `sportId` `53`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] eSoccer
>
> **2** market types · **2** period scopes · `sportId` `54`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] eBasketball
>
> **2** market types · **2** period scopes · `sportId` `55`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` | `fulltime` |
> > | - | :-: | :-: |
> > | `moneyline` | ✓ | |
> > | `1x2` | | ✓ |

> [tab] ESport Call of Duty
>
> **7** market types · **10** period scopes · `sportId` `56`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | | | | |
> > | `correctscore` | ✓ | | | | | | | | | |
> > | `winningmargin` | ✓ | | | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | | | | |
> > | `spreads` | ✓ | | | | | | | | | |

> [tab] ESport Overwatch
>
> **7** market types · **8** period scopes · `sportId` `57`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | | |
> > | `correctscore` | ✓ | | | | | | | |
> > | `winningmargin` | ✓ | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | | |
> > | `spreads` | ✓ | | | | | | | |

> [tab] ESport Rainbow Six
>
> **14** market types · **7** period scopes · `sportId` `58`
>
> > [accordion] Match outcome (7)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | |
> > | `correctscore` | ✓ | | | | | | |
> > | `winningmargin` | ✓ | | | | | | |
> > | `winningmargin-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven` | ✓ | | | | | | |
>
> > [accordion] Laddered lines (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | |
> > | `spreads` | ✓ | | | | | | |
> > | `totals-rounds` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-rounds-team{1,2}` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
>
> > [accordion] Specials and order (1)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `overtime` | | ✓ | ✓ | ✓ | ✓ | ✓ | |

> [tab] ESport Rocket League
>
> **7** market types · **10** period scopes · `sportId` `59`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | | | | |
> > | `correctscore` | ✓ | | | | | | | | | |
> > | `winningmargin` | ✓ | | | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` | `p8` | `p9` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | | | | |
> > | `spreads` | ✓ | | | | | | | | | |

> [tab] ESport StarCraft
>
> **7** market types · **8** period scopes · `sportId` `60`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | | |
> > | `correctscore` | ✓ | | | | | | | |
> > | `winningmargin` | ✓ | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | | |
> > | `spreads` | ✓ | | | | | | | |

> [tab] ESport Valorant
>
> **16** market types · **7** period scopes · `sportId` `61`
>
> > [accordion] Match outcome (7)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | |
> > | `correctscore` | ✓ | | | | | | |
> > | `winningmargin` | ✓ | | | | | | |
> > | `winningmargin-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `oddeven` | ✓ | | | | | | |
>
> > [accordion] Laddered lines (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | |
> > | `spreads` | ✓ | | | | | | |
> > | `totals-rounds` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `spreads-rounds` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
> > | `teamtotals-rounds-team{1,2}` | | ✓ | ✓ | ✓ | ✓ | ✓ | |
>
> > [accordion] Player totals (laddered) (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `playertotals-kills` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | |
> > | `playertotals-headshots` | ◦ | ◦ | ◦ | ◦ | ◦ | ◦ | |
>
> > [accordion] Specials and order (1)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `rounds` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `overtime` | | ✓ | ✓ | ✓ | ✓ | ✓ | |

> [tab] ESport Arena of Valor
>
> **7** market types · **8** period scopes · `sportId` `62`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | | |
> > | `correctscore` | ✓ | | | | | | | |
> > | `winningmargin` | ✓ | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | | |
> > | `spreads` | ✓ | | | | | | | |

> [tab] ESport King of Glory
>
> **7** market types · **8** period scopes · `sportId` `63`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | | |
> > | `correctscore` | ✓ | | | | | | | |
> > | `winningmargin` | ✓ | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | | |
> > | `spreads` | ✓ | | | | | | | |

> [tab] Judo
>
> **2** market types · **1** period scopes · `sportId` `64`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] ESport Honor of Kings
>
> **7** market types · **8** period scopes · `sportId` `65`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `1x2` | ✓ | | | | | | | |
> > | `correctscore` | ✓ | | | | | | | |
> > | `winningmargin` | ✓ | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | | |
> > | `spreads` | ✓ | | | | | | | |

> [tab] Speedway
>
> **2** market types · **1** period scopes · `sportId` `66`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Golf
>
> **2** market types · **1** period scopes · `sportId` `67`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Cycling
>
> **2** market types · **1** period scopes · `sportId` `68`
>
> > [accordion] Match outcome (2)
> >
> > | `marketType` | `result` |
> > | - | :-: |
> > | `moneyline` | ✓ |
> > | `1x2` | ✓ |

> [tab] Formula 1
>
> No fixture markets yet for `sportId` `82`. The sport is covered for fixtures and futures, but its market list is still being decided, so there is nothing to show here — [`GET /markets`](https://docs.oddspapi.io/api-reference/common/get-markets) returns an empty set for it rather than an error.

> [tab] ESport Mobile Legends
>
> **7** market types · **8** period scopes · `sportId` `83`
>
> > [accordion] Match outcome (5)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `moneyline` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
> > | `correctscore` | ✓ | | | | | | | |
> > | `winningmargin` | ✓ | | | | | | | |
> > | `1x2` | ✓ | | | | | | | |
> > | `oddeven` | ✓ | | | | | | | |
>
> > [accordion] Laddered lines (2)
> >
> > | `marketType` | `result` | `p1` | `p2` | `p3` | `p4` | `p5` | `p6` | `p7` |
> > | - | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
> > | `totals` | ✓ | | | | | | | |
> > | `spreads` | ✓ | | | | | | | |

> [tab] Chess
>
> No fixture markets yet for `sportId` `84`. The sport is covered for fixtures and futures, but its market list is still being decided, so there is nothing to show here — [`GET /markets`](https://docs.oddspapi.io/api-reference/common/get-markets) returns an empty set for it rather than an error.

✓ is an estimate from the counters we collect, not a settlement guarantee for an individual match — coverage varies by tournament. A per-fixture endpoint derived from past settlement results is coming (working name `/fixtures/markets/settleable`).

## How to read it

* **A market's scope lives in `period`, never in its `marketType`.** This is why the cricket over markets moved from `12-overs-3-delivery-team1` to a plain `teamtotals-team1` at period `p1o12b3` — see the [changelog](https://docs.oddspapi.io/changelog.md).
* **`players-…` is Yes / No; `playertotals-…` is Over / Under plus a `handicap`.** A counter question is always the `playertotals-` form.
* **A period's *meaning* is per sport** — `p1` is a half in soccer, a quarter in NBA basketball, a set in tennis, a map in esports. Interpret it with the fixture's `expectedPeriods` and `periodLength`, never by assuming a sport's structure. See [Periods per sport](https://docs.oddspapi.io/guides/periods.md).
* **`marketType` is append-only** — new values appear as coverage grows and never change meaning. Match on the types you support and ignore the rest rather than failing on unknown values.
* **A ◦ is not a defect.** Order and timing questions (first/last scorer), awards, match circumstances and every market at a cricket over scope need more than a `(stat, period)` counter, so no settle bridge is published rather than an approximate one.

> [!NOTE]
>
> This is the **catalogue**, not a price feed and not a settlement promise. It says a combination exists, and estimates whether it is gradeable — not that a bookmaker is quoting it right now. For what is priced on a given fixture, read `GET /fixtures/odds` and group by `marketId`; `mainLine` marks each book's headline line.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.