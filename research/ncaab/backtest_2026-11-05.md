# College basketball method backtest

Seasons 2024, 2025, 2026: 14669 games with both teams rated before tip-off (3+ usable games each), judged on the CLOSING spread and total. Winning needs 52.38% at -110.

Baselines on these games: home covers 49.6%, the favourite covers 49.7%, the Over hits 50.0%. The sd of a result around its line is 11.1 points (spread) and 16.5 (total).

## Averages vs the spread

The teams' scoring margins say one side should be favoured by more than the line does. Value unit: points. Meaning: back the side it points to.

| size | games | followed side won | first half | second half | this season |
|-|-|-|-|-|-|
| 0-3 | 6434 | 49.9% | 48.6% (3043) | 51.1% (3391) | - (0) |
| 3-6 | 4478 | 51.2% | 49.5% (2212) | 53.0% (2266) | - (0) |
| 6-9 | 2064 | 50.2% | 50.0% (1087) | 50.5% (977) | - (0) |
| 9-12 | 841 | 49.6% | 49.9% (489) | 49.1% (352) | - (0) |
| 12-+ | 841 | 47.2% | 46.9% (492) | 47.6% (349) | - (0) |

## Against-the-spread streak (green plus / red minus)

The gap between the two teams' average tiers; the history decides whether to back or fade the team that has covered more. Value unit: tiers. Meaning: FADE the side it points to (the history says that side loses).

| size | games | followed side won | first half | second half | this season |
|-|-|-|-|-|-|
| 0.0-0.4 | 8126 | 52.0% | 51.8% (3771) | 52.2% (4355) | - (0) |
| 0.4-0.8 | 4149 | 51.8% | 51.8% (2133) | 51.7% (2016) | - (0) |
| 0.8-1.2 | 1398 | 48.6% | 46.2% (822) | 52.1% (576) | - (0) |
| 1.2-+ | 686 | 52.6% | 52.6% (430) | 52.7% (256) | - (0) |

## Your rule: ahead on scoring AND on covering

The team that scores more per game and has covered more, backed. Value unit: points. Meaning: back the side it points to.

| size | games | followed side won | first half | second half | this season |
|-|-|-|-|-|-|
| 0-3 | 698 | 50.6% | 52.0% (396) | 48.7% (302) | - (0) |
| 3-6 | 733 | 49.8% | 52.1% (401) | 47.0% (332) | - (0) |
| 6-9 | 676 | 50.0% | 53.4% (378) | 45.6% (298) | - (0) |
| 9-12 | 502 | 47.2% | 46.2% (273) | 48.5% (229) | - (0) |
| 12-+ | 854 | 47.9% | 48.9% (470) | 46.6% (384) | - (0) |

## Total vs the teams' scoring

Each side's scoring and the other's points allowed, against the line: Over when it is higher, Under when lower. Value unit: points. Meaning: back the side it points to.

| size | games | followed side won | first half | second half | this season |
|-|-|-|-|-|-|
| 0-3 | 6735 | 48.9% | 48.8% (3298) | 48.9% (3437) | - (0) |
| 3-6 | 4640 | 48.8% | 48.6% (2305) | 48.9% (2335) | - (0) |
| 6-9 | 2276 | 48.2% | 48.6% (1177) | 47.8% (1099) | - (0) |
| 9-12 | 750 | 50.0% | 47.7% (411) | 52.8% (339) | - (0) |
| 12-+ | 256 | 48.0% | 50.0% (130) | 46.0% (126) | - (0) |

## Pooled, a bet on each signal's side whatever its size

| signal | games | won | profit per $1 staked at -110 |
|-|-|-|-|
| Averages vs the spread | 14658 | 50.2% | -4.2% |
| Against-the-spread streak (green plus / red minus) | 14359 | 51.6% | -1.4% |
| Your rule: ahead on scoring AND on covering | 3463 | 49.1% | -6.2% |
| Total vs the teams' scoring | 14657 | 48.8% | -6.9% |
