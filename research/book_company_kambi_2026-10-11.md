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

## Part 2: every book in the APIs Tj uses, against its odds provider (TAD, 2026-10-11)
Books reviewed: SGO's 82 (`SgoBooks.KNOWN`), ParlayAPI's 36 (PARLAY_API.md), OddsPapi / Unabated / The Odds API / PropLine books (the same names), and CNO's columns. Two kinds of evidence, both required to merge a book into another's company beyond what a book's own parent already says:

1. **Documented provider.** Kambi (the only B2B odds provider that publishes its client list): Kambi Group's Wikipedia article and 2021/2025 annual reports and releases (RSI = BetRivers/SugarHouse/RushBet, Greenwood = betPARX, Bally's = Bally Bet, Kindred/FDJ = Unibet/Maria/32Red, Rank = Grosvenor/YoSports/Enracha, RWWA = TABtouch, Casumo, LiveScore Group = LiveScore Bet/Virgin Bet, LeoVegas = LeoVegas/BetUK/Expekt, Svenska Spel, ATG, BetPlay). Odds Feed+ (Kambi's traded odds sold as a feed, 2025): Coolbet, FDJ United (Unibet), Superbet, LeoVegas. Not Kambi (own or other platforms): DraftKings (left Kambi after 2018), FanDuel/Flutter, BetMGM, Caesars, Hard Rock, Fanatics, bet365, theScore/ESPN BET (Penn), Pinnacle, Circa, Fliff, Bovada, BetOnline group. For Sportradar/Genius/OpenBet/Altenar/SBTech clients there is no public client list, so none is grouped on provider alone.
2. **Measured.** From Tj's scan studies (research/uploads/2026-10-08 and 2026-10-10: 780 distinct bets, each with every book's two-sided price) and SGO's raw samples (27 lines, 60 books): how often two books quote the identical line.
   - Same-company sister sites: Hard Rock and its states 98.7-99.7%, BetMGM / BetMGM (ON) 100%, theScore Bet / ESPN BET 100%, BetRivers / SugarHouse 100%, betPARX / SugarHouse 92%, betPARX / BetRivers 92%.
   - **Bally Bet / BetRivers 47% of 109 lines** (Kambi clients get one trader's line plus a margin of their own, so the match is not always exact): unrelated books match 2-4% (DraftKings / Caesars 2.7%, FanDuel / DraftKings 2.3%, Caesars / theScore 2.2%).
   - No other pair outside a known group matched above 30% at 25 or more lines (Circa / Hard Rock 46% on 28 lines, Fanatics / Hard Rock 29%: shared round numbers, not a desk; no provider links them).
   - The books in the screenshot (Casumo, Grosvenor, TABtouch, Unibet) are not in the CNO columns or the scan study; their identity is the documented Kambi link and the screenshot itself (+100 / -134 on all six; Unibet -133).
**Merged ("kambi" in `BookCompany`):** the Kambi and Odds Feed+ clients above that appear in any of the APIs' lists. **Not merged:** every book whose provider is unknown or documented as its own.
**To find more:** a pair that matches the identical line on >= 80% of 100+ lines is one desk; Diagnostics' scan study has the data (`parse.py` in research/scan_study_2026-10-10).
