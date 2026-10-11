# Six "agreeing" books with identical odds: one trading desk (2026-10-11)

Tj's screenshot: Casper Ruud +0.5 games spread, "6 of 6 books agree": Bally Bet, betparx, casumo, grosvenor, tabtouch, unibet, all +100 / -134 (unibet -133).

Web research (2026-10-11), each book's owner and where its odds come from:

| Book | Owner | Odds / sportsbook provider | Evidence |
| :- | :- | :- | :- |
| Bally Bet | Bally's Corporation | Kambi (exclusive long-term sportsbook deal, May 2023; replaced Bally's own Bet.Works tech; White Hat runs the player accounts) | sbcnews.co.uk/sportsbook/2023/05/02/kambi-ballys-sportsbook, agbrief.com (Kambi release, 3 May 2023) |
| betPARX | Greenwood Gaming & Entertainment (Parx Casino) | Kambi (since Nov 2018, extended) | legalsportsreport.com/25848/kambi-parx-pa-sports-betting, sbcamericas.com 2018-11-13 |
| Casumo | Casumo (Malta) | Kambi (2018 deal; UK sportsbook back end by Kambi) | gamingintelligence.com 39017, gamblinginsider.com 8381 |
| Grosvenor (Sport) | Rank Group | Kambi (since 2016, renewed 2019 and Aug 2023; Enracha and YoSports on it too) | sbcnews.co.uk/sportsbook/2023/08/10/kambi-rank-group, gamblinginsider.com 2654 |
| TABtouch | Racing and Wagering Western Australia (WA TAB) | Kambi (Jan 2021 long-term deal: odds compiling, risk management) | europeangaming.eu 2021-01-21, gamblinginsider.com 10889 (current status after 2021 not confirmed) |
| Unibet | Kindred Group (FDJ United) | Kambi (Kambi was spun out of Unibet in 2014; Kindred renewed to end of 2026 and is moving to an in-house sportsbook, Kambi's Odds Feed+ continuing) | Wikipedia Kambi Group, sbcnews.co.uk 2022-02-09, gamingintelligence.com 151886 |

**Verdict:** six DIFFERENT owners, ONE trading desk. Kambi's traders compile the odds ("odds compiling, customer intelligence and risk management" in the RWWA deal), so the books quote the same line (Unibet's one-tick difference, -133, is the only variation: its own margin). That is one opinion, not six, so a count of "6 of 6 books agree" overstated the agreement. Not independent even though the companies are.

**Change:** `engine/BookCompany.kt` group `kambi` replaces `rushstreet`, `kindred` and `bally`: BetRivers, SugarHouse, RushBet, betPARX/Parx, Bally Bet, Unibet (every region), Maria, 32Red, Casumo, Grosvenor/Enracha/YoSports, TABtouch/RWWA, LiveScore Bet, Virgin Bet, BetUK, Expekt, Svenska Spel, ATG, BetPlay. The books-agree count, the star rating's coverage, the fair line (one devigged book a company) and every list count them once. Test: `BookCompanyTest`.

**Caveat:** a Kambi operator that moves to its own sportsbook (Kindred plans this for 2026) stops sharing the desk; if Unibet's odds begin to differ from the rest on the same line over many bets, take it out of the group.
