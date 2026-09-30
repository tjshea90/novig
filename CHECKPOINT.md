# CHECKPOINT 2133 — read me first, then TASKS.md

**Written:** 2026-09-30T01:35:27Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `7426d06f` (this checkpoint is the commit after it)

## Just done
MID-CHANGE (not yet compiled): KeyPool.execute(reserve) + OddsFeed.reserve (PARLAY 300 credits kept for closes); ParlayCloses now takes a KeyPool (metered calls, key rotation, Depleted/Invalid/429) instead of a keys lambda. Earlier this session: ParlayCloses + CloseSource.active, ParlayClosesTest 7 + TheOddsApiClientTest +2 green, Diagnostics 'ParlayAPI close calls', meter test tall screen; floor was 1068/1 fail before that fix

## Do this next
1) VigilantApp.kt:227 build ParlayCloses(http, parlayOdds' KeyPool, json): expose the pool (e.g. make TheOddsApiClient.pool val or create one parlayPool val shared by both). 2) ParlayClosesTest: replace { listOf("k") } lambdas with KeyPool(QuotaPolicy.PARLAY, {keys}, UsageMeter(JsonFileStore(tmp, UsageBook.serializer(), { UsageBook() }))); keyless test uses emptyList. 3) add a KeyPool reserve test (scan refused with 'kept for closing lines' msg while closes still run). 4) compile + bash tools/test.sh. 5) RESEARCH.md §43: sources verdicts (Apps Script=Odds API historical paid-only 10cr/market/region, final commence time; TheRundown free 3 books 5-min delay no props/history, Starter $49 60s delay, closing lines only Pro $149; r/ParlayAPI post unreadable (Reddit blocks), vendor's own sub, evaluated ParlayAPI directly; edge-scanner = multiplicative devig + Pinnacle ref + Kelly + middles, Vigilant already has all; skills.rest = Claude skill for Odds API historical: snapshot 30-60 min pre-commence, 10 cr each, paid only). ParlayAPI pricing verified 2026-09-30: free 1000cr/48h hist, Starter $5 20K/7d, Pro $20 100K/30d, Business $40 1M/90d+WS; alternatives SportsGameOdds $99+, theoddsapi.com Business $99, Pinnacle-only feed $299. Recommend: free key now, Starter $5 once closes show; Pro $20 only for heavy scans. 6) tick F1-F3 in TASKS.md, v0.27.0 code 55, ship.sh, CI, release.yml, record-release, answer Tj with plain Release link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0613045f ckpt 2132: F2 tests green: ParlayClosesTest 7 (props file/game lines parse, HTTP+cache,
  89d19720 ckpt 2131: F2b ParlayCloses compiles: Pinnacle prop closes (daily file, UTC+ET date), g
  40fda8c4 ckpt 2130: v0.26.0 (code 54) released and recorded; E1-E4 ticked; F2a (ParlayAPI as a f
  5fe7de23 ckpt 2129: F2a in progress: OddsFeed (ODDS_API/PARLAY) in TheOddsApiClient + OddsApiPro
  25f31ac8 ckpt 2128: pre-release: v0.26.0: closing lines are found after the game starts, even da
  286d721d ckpt 2127: F0 done: Wi-Fi gate removed, rule written into BRIEF.md + CLAUDE.md
  ba5aac97 ckpt 2126: Recorded Tj's mid-turn request as F0-F4 (research 4 sources, implement what 
  f9dfe964 ckpt 2125: E1-E3 ticked; RESEARCH.md §42; NOVIG_API §10 verified note; CLV card text 
  45dfb5b8 ckpt 2124: E2/E3: HistoricalClosesTest 10 green on recorded ESPN/Novig payloads (fixtur
  7822290d ckpt 2123: E2/E3 code in and compiling: HistoricalCloses.kt (EspnCloses: scoreboard→g
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
