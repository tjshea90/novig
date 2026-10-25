# Why bets showed 1 book and no ParlayAPI odds; old fair odds (PX, Tj 2026-10-25 ~17:00-18:00 UTC)

Tj: "Investigate why I don't see any parlayapi provided odds in the scanned list and why many results only have 1 book behind the fair odds for alternate markets that are popular and available at many books";
"Why are these odds so old if they are from a socket?"; "Use propline Pinnacle ... get the most odds from Pinnacle including props using both propline and parlayapi"; "Use as many sports books as possible for all
markets including alternate lines ... not double counting same companies or same odds providers".

## Measured
- `propline.yml` task `altprobe` (Release propline-samples altprobe.md/json + raw boards): at 16:59 UTC PropLine carried a median **13** of Vigilant's books at Novig's exact NFL alternate numbers (20-21 on the Broncos-Cardinals totals),
  all re-read within 5 min; NCAAF alternates median 2 (PropLine itself is thin there). The engine's own parser keeps them all (replayed locally on the raw board).
- Engine `QuoteCensus` (new, logged after every scan report): where each feed's quotes are lost.
- Pinnacle (`parlay.yml` pinprobe, `propline.yml` proppin): PropLine has Pinnacle at Novig's numbers on NFL main 24/50, NFL alternates 152/649, NCAAF none; quotes ~150 s old (the website's "4 s ago" is the
  newest write among ~195,000 Pinnacle markets). Pinnacle props at Novig's numbers: PropLine NFL 319 (~8 min old), NBA 112 (130 s), NHL 187 (67 s); ParlayAPI NFL 99, NBA 111, NHL 166; ParlayAPI's REST Pinnacle
  game lines are 14-16 min old (its hot stream is the fresh path).

## Causes and fixes (all on the engine; research/review/CHANGES.md has the revert rules)
1. **ParlayAPI asleep behind PropLine** (the 2026-10-11 chain rule): it priced none of the list. Off the chain on a Business key (`ApiChain.alwaysAsked`, ApiChainTest); its alternates always asked (they deferred to a Pinnacle
   feed whose keys are spent).
2. **PropLine pulls applied to boards read after them**: a rung pulled and back at the next read was dropped for 20 min (PropLineLiveTest).
3. **SharpAPI's Free tier held every scan** at "Fair odds 68/74" until it was cut after 150-270 s: the list and its fair odds were 5-12 min old. SharpApiSource no longer waits (SharpApiSourceTest); SharpAPI off on Tj's word
   (the phone's 17:11 settings write put it back on; re-applied 17:5x).
4. **Two age rules tighter than PropLine's polling** (census: 38% of PropLine game-line quotes "too old", 28% of the rest dropped by fresh-first): on the engine (list re-priced every 3 s) a scan keeps no 2-minute headroom
   and fresh-first leaves out only quotes past 5 min (`Freshness.continuous`, FreshFirstTest). After: too old 16%, fresh-first 14% (what remains is past 5 min: books PropLine re-reads slowly), ParlayAPI's priced
   quotes about tripled.
5. One book a company still applies (`BookCompany`): the devig step's left-outs (~25%) are mostly Kambi / Entain / Super Group skins collapsing to one vote, as Tj asked.

## Result
Listed bets at 18:00 UTC: median **5** books (was 1-2), PropLine and ParlayAPI both pricing; NBA/NHL team totals 6-10 books.

## Open
- PX7b: ParlayAPI's stream-only books are non-US books with unknown desks (kept out); nothing to add until a desk is documented.
- PS2b: Novig's alternate numbers no book posts (NCAAF) stay thin unless priced from another number (a model).

## PX8/PX9 (same evening, after Tj's screenshot: DEN@ARI 1st-half total Over 22.5, six books, all PropLine, 3-6 min old, no ParlayAPI)
- The row is `TOTAL_1H` priced by `propline_1h` alone. `parlay.yml gameprobe` (new, one game, every book and number with ages) on DEN@ARI at 18:37 UTC: ParlayAPI's REST `/odds` main spread (-4.5, 11 books) and total (43.5, 12 books) are priced by 11-14 books about 0-20 s old,
  but its **alternate spreads/totals carry Pinnacle alone** (252 s old) and its **`/live/period_markets` 1H is Pinnacle alone** (77 s old). So for 1st-half and alternate lines PropLine is the only source of more books; ParlayAPI adds only Pinnacle there, and loses that book to PropLine's
  confirmed stamp when PropLine's is fresher.
- Why PropLine's quotes show minutes: its docs say five books are pushed (DraftKings, FanDuel, Fanatics, Kalshi, Polymarket) and "the other 25 books are polled, 60-90 s pre-match" and that a price with no `line_movement` has not changed. An unchanged price can only be dated to PropLine's last
  READ of the book (`pregame_read_at`, the heartbeat), which for NFL books on a Sunday slate is 40-280 s ago (the row's quotes at the engine: Betway 44 s, DraftKings 104 s, theScore 156 s, Pinnacle 168 s, Bovada 183 s, Hard Rock 279 s: reread gaps of 100-250 s, not the 60-90 s in the docs). The new heartbeat log line gives the real numbers per sport; the new cross census tells whether PropLine's old
  stamps on the push books are still the same price ParlayAPI sees (then they may be dated as current).
- **A real bug found beside it** (the "1 book behind the fair" rows): `RealtimeList.adopt` took the rows of a partial scan whose league was still waiting for a fair-odds source (`ScanResult.waitingFor`). GB@DET's spread (Kalshi alone) and the same game's 1st-half total (7 feeds) were listed together;
  the Kalshi-only row's merged game id `kalshi:...` had no other feed. 5 of the 20 listed rows at 18:55 UTC were Kalshi-only. Fixed in `RealtimeList.adopt` (CHANGES.md PX9).

