# CHECKPOINT 241 — read me first, then TASKS.md

**Written:** 2026-09-20T20:47:08Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `3b20fbf` (this checkpoint is the commit after it)

## Just done
Built the sport-selection picker per Tj's explicit instruction: no odds load for any sport until he selects it and presses refresh or pull-to-refresh. data-module: Sport/SportsCatalog (curated The Odds API sport-key list), EvScanner now takes a List<String> of sport keys and short-circuits to an empty result WITHOUT touching either repository when the list is empty (new EvScannerTest proves this with call-tracking fakes). app-module: ScannerViewModel no longer auto-scans on init (starts Idle), holds selectedSports as separate state via toggleSport(), rescan() is a no-op with zero sports selected and is the single path both the FAB and the new PullToRefreshBox call; MainActivity's Settings onBack no longer auto-rescans either, since that would silently violate the same 'nothing loads without an explicit refresh' rule. OpportunitiesScreen: FilterChip sport picker (LazyRow) above the list, PullToRefreshBox wrapping the content area, and a new Idle state with sport-aware hint text. Verified PullToRefreshBox's and FilterChip's real signatures against Kotlin/Android docs before writing calls against them (both match what was written). engine+data tests all green.

## Do this next
Push, confirm CI green for the app module (new Compose Material3 pull-to-refresh API surface this container can't compile-check locally), then trigger the release workflow via workflow_dispatch, confirm it goes green, and send Tj the new Release link as plain tappable text.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  92edb91 ckpt 229: Wrote Tj's new sport-picker request into TASKS.md before writing any code, per
  1fe3119 ckpt 226: Confirmed CI green for the app module (run 35535815614, conclusion=success) �
  8be34bf ckpt 220: Wired Settings navigation into MainActivity (state-based, no nav library): Set
  aadab85 ckpt 208: Rewired TheOddsApiClient onto KeyRotator (was a single apiKey string) — auto
  8e70e49 ckpt 205: Built KeyRotator (data module) — provider-agnostic multi-key rotation with r
  b663452 ckpt 198: Wrote Tj's 'make the app functional' request into TASKS.md with the architectu
  54208cf ckpt 195: Updated RESEARCH.md §4.1.1 with the real API-access process Tj found (email d
  b3b36e3 ckpt 191: Wrote Tj's request into TASKS.md: he found the real API-access process (email 
  44e6c69 ckpt 188: Second research pass on Novig's NBX API pricing/access: found strong convergin
  73d4460 ckpt 183: Wrote Tj's Novig-trading-API-pricing research request into TASKS.md (raw messa
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
