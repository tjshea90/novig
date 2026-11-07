# Reputable books only in the fair (BK1, Tj chat 2026-11-07 04:44 UTC)

Tj: "go through the list of sports books which calculate fair odds for the scanner. remove and never show or use in the future any book which is very small and soft. only include reputable books in the scanning."

## What was done
`engine/ReputableBooks` (a banned list, matched on normalised key, state/country tag tolerant) is applied first in `FairValue.compute` (every feed, phone and engine), and in `PropLineClient.books/carries/DISPLAY_BOOKS`
(PropLine is no longer asked for them). Fliff is off the default book list and the Settings picker; MyBookie and BetUS are off the picker.
Banned: sweepstakes/social (Fliff, Sportzino, Thrillzz, Rebet, Strendus); tiny offshore/crypto (BetUS, MyBookie, 1xBet, Bet25, TonyBet, Bet99, Courtside, Jazz Sports, GTBets, Everygame, Four Winds, Hotstreak, LVBet);
small regional with no sharpness record (Betika, SportyBet, Star Sports, Soccerbet, NetBet RO, PSK, TotalBet, PlayUp, Betr AU, Synot, Sportium, Bet9ja, SupaBets, Wplay, Meridianbet, Codere, PalmerBet, Teapuesto, Placard, Solverde, Olybet, Jouez Sport, PMU).
Kept: US majors, Pinnacle/Circa/Bookmaker/BetOnline/LowVig/Bovada, Heritage, exchanges (Betfair, Matchbook, Smarkets, SX Bet, Kalshi, Polymarket), state lotteries, major licensed EU/AU operators (Tipico, Winamax, Betclic, Stoiximan/Betano, TAB, Sazka, Novibet, Veikkaus, Norsk Tipping, Danske Spil, Loto-Quebec, PlayNow).
Judged from the book studies already in the repo (propline_books_2026-10-25.md, propline_book_accuracy_2026-10-24.md: the 34 added books were added for coverage only; Fliff was the most frequent outlier book in the engine's census) and general knowledge of each operator; no live web search was run this session (not available in the routine), so the borderline keeps (Novibet, Sazka, Admiralbet DE, Toto, Danske Spil) should be re-checked.
Effect: PropLine's book list now fits one request (was two parts), so fewer credits.
Revert: remove the names from `ReputableBooks.BANNED`. Test: ReputableBooksTest.
