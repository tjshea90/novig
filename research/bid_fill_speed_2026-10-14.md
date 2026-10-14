# Which bids fill fastest (2026-10-14; for Tj's Volume mode: "hundreds or thousands of bids ... most likely to be filled first ... 1.5% / 2.5% EV")

Source: every bid record in `research/uploads/*/*bids*` (24,267 bids with a start and an end time, merged by clientId). Rate = fills per 100 bid-hours (bid-hours = how long the bids rested). A bid that fills ends at the fill, so this is a fill hazard, not a fill probability. Rerun the inline script over the same files for a newer picture.

| cut | bids | fills / 100 bid-h | note |
| :- | -: | -: | :- |
| Player props, <6 h to start | 3,806 | 30.1 | best |
| Player props, 6 h+ | 8,124 | 11.3 | |
| Team totals, <6 h | 3,945 | 9.9 | |
| Team totals, 6 h+ | 5,569 | 4.1 | |
| 1st half / period lines | 1,259 | 3.1 | |
| Spreads | 616 | 1.5 | |
| Game totals | 810 | 1.3 | |
| WNBA | 1,303 | 33.0 | |
| NHL | 3,617 | 22.6 | |
| MLB | 2,100 | 16.1 | |
| NFL | 10,808 | 10.3 | |
| NCAAF | 4,191 | 3.4 | |
| ATP / WTA | 2,242 | 1.5 / 0.0 | |
| lead < 1 h | 1,486 | 25.7 | |
| 1-3 h / 3-6 h | 2,318 / 4,834 | 16.9 / 15.7 | |
| 6-12 h / 12-24 h | 7,459 / 5,342 | 8.9 / 5.6 | |
| 24-72 h | 2,828 | 1.0 | |
| price above the best bid (leads its side) | 11,578 | 16.2 | |
| price level with / behind the best bid | 4,080 / 6,274 | 8.6 / 2.2 | |
| EV at post 2-3% | 2,891 | 16.6 | thinner edge fills faster |
| EV at post 3-4% | 14,303 | 10.5 | |
| EV at post 4-6% | 7,073 | 7.5 | |

Reading: fill speed is driven by (1) how close the game is, (2) player props over every other kind, (3) the busy leagues (WNBA, NHL, MLB, NFL), (4) leading the side, and (5) a thinner edge (2-3% EV fills twice as fast as 4-6%). The EV the filled bids kept at post was +3.3 to +3.9% in every cut: a bid at 1.5-2.5% fills faster and keeps about that. Game lines (spreads, totals) and tennis fill almost never.

Used by `maker/BidVolume.kt` (`score`) to order bids and by Volume mode (EV floors 1.5% safest types / 2.5% riskier).
