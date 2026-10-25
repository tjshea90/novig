# More books behind each bet, matched and counted once (PX9, Tj 2026-10-25)

Tj: "Make sure odds come from as many books as possible and are matched correctly. You may need to consult the docs ... figure out how to get odds for many books matching on the bets and both propline and
parlayapi streams feeding fresh odds ... restart the engine and monitor the scanned bets for many sources, correct mapping, and fresh odds." (earlier: "Use as many sports books as possible for all markets
including alternate lines ... not double counting same companies or same odds providers. This may need matching".)

## What was measured (all on GitHub, keyless in the log; tools and workflows are in the repo)
- **PropLine, every book** (`propline.yml` task `bookstudy`, `tools/propline_books.py`, 2026-10-25 19:18-19:24 UTC, NBA/NHL/NFL/NCAAF/MLB, no `bookmakers=` filter): about 100 books; per book the two-way lines it posts (alternates
  included), margin, how far its no-vig chance sits from the other books' median, quote age; per pair the share of 150+ shared lines where the two no-vig chances agree within 0.2 points. The method reproduces the known
  desks (BetRivers = Unibet 100%, BetRivers/Unibet = LiveScore Bet 94-96%, BetOnline = LowVig 97%, Placard = Solverde 97%) so it can be trusted on the unknown ones.
- **ParlayAPI** (`parlay.yml` tasks `gameprobe`, `bookprobe`): its active books are the 15 Vigilant already asks for plus non-US ones; its **alternate lines and 1st halves are Pinnacle alone** (NFL and NBA games probed:
  alternate_spreads/totals 1 book, `/live/period_markets` 1H 1 book), its full-game main lines have 11-14 books at 0-20 s; its **team totals** (`/props?markets=team_totals`) are Pinnacle (all three leagues, the
  whole ladder), DraftKings (NFL) and a few ProphetX/Fliff rows. The REST board dates a book's quotes by its OLDEST price on the game (Pinnacle 8-16 minutes), the streams hold them to the second.
- **PropLine's own read cadence** (heartbeat, engine log 19:04 UTC): NBA every 61 s, NHL 73 s, NFL 276 s, NCAAF / MLB / WNBA 600 s. Football quotes from PropLine are minutes old by construction; nothing on our side speeds that.

## Decisions (CHANGES.md PX9, each with its revert rule)
1. **36 more PropLine books** (`PropLineClient.ADDED_2026_10_25_MEASURED`): Tonybet, Courtside, STS, Pamestoixima, SportyBet NG, Rebet, Jazz Sports, Sportzino, Bet25, Thrillzz, Bet99, Fortuna CZ/PL, Totalbet, Sportium, PSK,
   Danske Spil, Mise-o-jeu, Napoleon, PlayNow, Veikkaus, Admiralbet DE, Synot, Betr AU, Ladbrokes BE, Novibet, Sazka, Strendus, Soccerbet, Betika, Norsk Tipping, Star Sports, Toto, PlayUp, LVBet, NetBet RO. Each posts 100-2,000
   lines on the scanned leagues, mean distance from the market 0.4-1.3 points (DraftKings 1.2, FanDuel 1.5), margin 4-10%, median quote age under 5 minutes. Not added: margin over 10% (Placard, Solverde, Olybet, Jouez Sport,
   PMU, Teapuesto), median quote over 5 minutes old (Bet9ja, SupaBets, Marca Apuestas, Wplay, Meridianbet, Betathome, PalmerBet, PlayAlberta, Codere MX), SX Bet (its own source). 71 books in all; the stream's filter is 646 characters
   (limit 1,000).
2. **Companies** (`engine/BookCompany`): Superbet PL, Superbet RO and Napoleon are ONE desk (80-83% of 550-920 shared lines) and **not Kambi's** (under 25% with every Kambi book; the earlier grouping followed Kambi's
   announcement for tennis): moved out of the Kambi group. Fortuna CZ and PL are one operator (71% of 498). A book that tracks nothing above 77% stays its own company (the 80% rule of 2026-10-11). The regional cluster
   Betika / SportyBet / Tonybet / Meridianbet / Betway ZA / Pamestoixima tracks at 73-77% (a common upstream feed is likely, not proven): counted as separate books until a pair crosses 80%.
3. **ParlayAPI's streams date its quotes before the age filter** (`OddsScanner.planOverlay`, set by the engine): its REST quotes were cut or beaten by PropLine's because of the oldest-price stamp.
4. **ParlayAPI team totals** (`ParlayProps.teamTotal`, asked in the same 3-credit props call): Pinnacle's whole team-total ladder and DraftKings' were in no fair line; the stream's `team_totals` rows now re-date them.
5. The held rows of the real-time list are age-checked every pass (`Pricing.dropOld`), full scans run every 6 minutes instead of 15 (`CloudLanes.FULL_EVERY_MS`), and a partial scan's rows are not adopted while a fair source
   is still to answer (the Kalshi-alone rows: PX9 first half).

## Open
- `Freshness.plainFastFeeds` (OFF): SGO Pro's 15-minute guard still applies to every feed; the census `linesNow` / `linesPlain` says what the plain 5-minute limit would cost (about half of a football line's PropLine quotes
  are older than 5 minutes at any moment).
- PropLine's push books (DraftKings, FanDuel, Fanatics): dated as current only if the census supports it (`crossSame` / `crossDiff` near vs far, `pushNote`).
- Re-run `bookstudy` after a week: the 70-77% cluster, new books, and any pair that crosses 80%.
