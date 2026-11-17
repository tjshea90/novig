# College basketball method backtest

Seasons 2024, 2025, 2026, 2027: 14684 games with both teams rated before tip-off (3+ usable games each), judged on the CLOSING spread and total. Winning needs 52.38% at -110.

Baselines on these games: home covers 49.6%, the favourite covers 49.7%, the Over hits 50.0%. The sd of a result around its line is 11.1 points (spread) and 16.5 (total).

## Averages vs the spread

The teams' scoring margins say one side should be favoured by more than the line does. Value unit: points. Meaning: back the side it points to.

| size | games | followed side won | first half | second half | this season |
|-|-|-|-|-|-|
| 0-3 | 6443 | 49.9% | 48.7% (3050) | 51.0% (3393) | 44.4% (9) |
| 3-6 | 4482 | 51.3% | 49.4% (2216) | 53.1% (2266) | 75.0% (4) |
| 6-9 | 2064 | 50.2% | 50.0% (1087) | 50.5% (977) | - (0) |
| 9-12 | 842 | 49.6% | 50.0% (490) | 49.1% (352) | 100.0% (1) |
| 12-+ | 842 | 47.1% | 46.9% (492) | 47.4% (350) | 0.0% (1) |

## Against-the-spread streak (green plus / red minus)

The gap between the two teams' average tiers; the history decides whether to back or fade the team that has covered more. Value unit: tiers. Meaning: FADE the side it points to (the history says that side loses).

| size | games | followed side won | first half | second half | this season |
|-|-|-|-|-|-|
| 0.0-0.4 | 8130 | 52.0% | 51.8% (3778) | 52.2% (4352) | 75.0% (4) |
| 0.4-0.8 | 4153 | 51.8% | 51.8% (2138) | 51.7% (2015) | 25.0% (4) |
| 0.8-1.2 | 1400 | 48.6% | 46.2% (822) | 52.1% (578) | 50.0% (2) |
| 1.2-+ | 689 | 52.8% | 52.6% (430) | 53.3% (259) | 100.0% (3) |

## Your rule: ahead on scoring AND on covering

The team that scores more per game and has covered more, backed. Value unit: points. Meaning: back the side it points to.

| size | games | followed side won | first half | second half | this season |
|-|-|-|-|-|-|
| 0-3 | 699 | 50.5% | 52.1% (397) | 48.3% (302) | 0.0% (1) |
| 3-6 | 733 | 49.8% | 52.1% (401) | 47.0% (332) | - (0) |
| 6-9 | 676 | 50.0% | 53.3% (379) | 45.8% (297) | - (0) |
| 9-12 | 502 | 47.2% | 46.4% (274) | 48.2% (228) | - (0) |
| 12-+ | 856 | 47.8% | 48.9% (470) | 46.4% (386) | 0.0% (2) |

## Total vs the teams' scoring

Each side's scoring and the other's points allowed, against the line: Over when it is higher, Under when lower. Value unit: points. Meaning: back the side it points to.

| size | games | followed side won | first half | second half | this season |
|-|-|-|-|-|-|
| 0-3 | 6743 | 48.9% | 48.8% (3305) | 48.9% (3438) | 37.5% (8) |
| 3-6 | 4643 | 48.8% | 48.6% (2307) | 49.0% (2336) | 100.0% (3) |
| 6-9 | 2278 | 48.2% | 48.6% (1178) | 47.8% (1100) | 100.0% (2) |
| 9-12 | 751 | 50.1% | 47.9% (413) | 52.7% (338) | 100.0% (1) |
| 12-+ | 257 | 47.9% | 50.0% (130) | 45.7% (127) | 0.0% (1) |

## Pooled, a bet on each signal's side whatever its size

| signal | games | won | profit per $1 staked at -110 |
|-|-|-|-|
| Averages vs the spread | 14673 | 50.2% | -4.2% |
| Against-the-spread streak (green plus / red minus) | 14372 | 51.7% | -1.4% |
| Your rule: ahead on scoring AND on covering | 3466 | 49.1% | -6.3% |
| Total vs the teams' scoring | 14672 | 48.8% | -6.9% |
