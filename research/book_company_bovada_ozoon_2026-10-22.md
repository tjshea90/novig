# Bovada = Ozoon (= Bodog), Borgata = BetMGM (2026-10-22)

Tj (chat 17:29 UTC): treat Bovada and Ozoon as one book, not "2 books agree"; research other books in his APIs that are one company or share a provider.

## Evidence
- Bodog rebranded as Ozoon in Canada (Feb 2026), same sportsbook, same accounts: https://www.covers.com/industry/bodog-run-comes-to-end-as-offshore-gaming-operator-rebrands-feb-16-2026 and https://lcb.org/news/bodog-rebrands-to-ozoon-in-canada-with-official-launch ; Bovada is Bodog's US brand (LCB review of Ozoon).
- Borgata Online runs on the BetMGM platform (the r/algobetting list Tj sent, 2026-10-22).
## Change
`engine/BookCompany.kt`: `bodog` group gains ozoon, ozooncanada, bodog88, bodogeu; the `entain` group (BetMGM) gains borgata, borgataonline, borgatacasino. Test: `BookCompanyTest`.
## Looked at, NOT merged (no documented provider, no 80%+ measured match)
BetUS, MyBookie, Bookmaker, Heritage, BetAnySports (offshore, ownership not documented in what a search finds); Hard Rock Bet (the Reddit list says Kambi, unconfirmed); FanDuel vs Fanatics (list says Amelco for Fanatics; FanDuel is Flutter in-house). BetOnline/LowVig/SportsBetting.ag were already one company. Measured pair-match over the line datasets is the way to add any of these.
