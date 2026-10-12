# Books that are one company: Entain family + BetMGM (2026-10-12)

Trigger: Tj's screenshot (Dalton Kincaid Over 56.5): bwin, partysports, BetMGM and sportsinteraction all showed -120 / -111.
(`research/uploads/2026-10-12/ef0b1781-image.png`.) Four identical two-sided prices from four "different" books is one desk counted four times.

## Evidence
- Entain plc owns bwin, Party Casino/PartyPoker brands, Ladbrokes, Coral, Neds, Sportingbet, Gamebookers, Gala, Eurobet, Sports Interaction (Ontario), BetCity, Crystalbet, SuperSport.
- BetMGM is a joint venture of MGM Resorts and Entain; Entain's trading desk prices it (its odds come from Entain's platform).
- Screenshot: four of those names carry the identical line and price on the same prop, which is what one desk behind several brands looks like.
## Change
- `engine/BookCompany.kt`: the `entain` group now holds ladbrokes, coral, neds, sportingbet, bwin, gamebookers, gala, eurobet, sportsinteraction/si, partysports/party, betcity, crystalbet, supersport and betmgm/mgm/betmgmuk. The separate `mgm` group is gone.
- Book counts that bypassed `BookCompany` now use it: `Planner.lineStats`, `AltLineScan`, `MakerQuote` bookFairs. (Fair line, Agreement, stars already used it.)
- Test: `BookCompanyTest`.
## Not done / caveats
- BetMGM was merged on the screenshot plus the JV fact; if Tj finds BetMGM prices differ from bwin often, split it.
- The pair-match measurement over the scan studies could not be run: the samples in the repo are too small. No other group was merged without a documented provider or a measured 80%+ match over 100+ lines (the repo's rule).
