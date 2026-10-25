# ParlayAPI lists one game as two events (PX11f, 2026-10-25)

**Tj, 22:44 UTC:** "Are the odds really this old? It is a socket. Is it configured correctly? Shouldn't there be some parlayapi results?" (bet sheet: NCAAF Northwestern @ Oregon Over 49.5, 20+ books, all PropLine, 4-13 min old).

## Evidence
- `parlay.yml` gameprobe 23:26 UTC (Release `parlay-samples`, `gameprobe.md`), ParlayAPI `/v1/sports/americanfootball_ncaaf/odds`:
  - event `2041135474ea`: **Northwestern (#20) @ Oregon**, 2026-10-31T19:30Z, books betonline, bovada, pinnacle, prophetx
  - event `7534192b9dd1`: **Northwestern Wildcats @ Oregon Ducks**, same start, books bet365, betmgm, betrivers, caesars, draftkings, fanatics, fanduel, parx
  - Over 49.5 was quoted by the second event's 7 US books, stamps 0-22 s (several 5-22 s in the FUTURE: ParlayAPI's clock).
- The engine's list at 23:2x: Northwestern @ Oregon Over 49.5 priced by 22 PropLine books (median 523 s old), no ParlayAPI; other NCAAF games in the same list had 2-5 ParlayAPI books.

## Cause
`Planner.matchOne` paired each Novig game with ONE event per feed (one-to-one), so the second listing, and every quote in it, was discarded.

## Fix (engine at once, phone with the next APK)
A feed's event that no Novig game took joins the game it matches when it starts within 15 min of that game's first match and names both teams with similarity >= 1.8 (`Planner.SAME_GAME_START_MS`, `MIN_EXTRA_SIMILARITY`). Tests (`PlannerPricingTest`): the two Oregon listings both price the line; Texas Tech @ Oklahoma State in the same slot never joins Texas @ Oklahoma; a doubleheader's later game never joins.

## Still true after the fix
- PropLine's polled books on a game six days out are re-read about every 12 min (its own stream reports a p50 delay of 77 s for the books it pushes); far games are allowed older quotes (Freshness.FAR_OFF_AGE_MS). Near games are what the hot refresh keeps young.
- Pinnacle's own copy at ParlayAPI was 604-652 s old on this game (PX10g).
