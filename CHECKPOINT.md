# CHECKPOINT 442 — read me first, then TASKS.md

**Written:** 2026-09-27T00:16:19Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `5c2e55c` (this checkpoint is the commit after it)

## Just done
Logged K8 (only-agreed-bets option); CnoNetwork (RememberingDns, keep-alive 20 s, 12 s read timeout, CnoPace) written

## Do this next
Wire CnoNetwork into AppContainer/CnoClient (+ one retry), lighter lanes, link pre-resolve lane + disk cache, NovigBetFinder fallback, K7 ✕, K8 filter, K3 notification, K4 narrow bar

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  17ba6f1 ckpt 441: v0.15.1 released + recorded; logged K6 (DNS/timeouts) and K7 (✕ hide button)
  5c9fe1c ckpt 440: Logged Tj's K1-K5 (bet slip taps, CNO unreachable, Milwaukee notification, nar
  3b2a13c ckpt 439: pre-release: v0.15.1: floating widget resizes with two fingers (spread/pinch, 
  3cf811b ckpt 438: J5 tests green: 409 tests 0 failed; FloatingWidget handles touches in dispatch
  a79a462 ckpt 437: J1-J4, J6 done: framed widget with corner handles, pinch/spread + two-finger m
  4cfc2e5 ckpt 436: Logged Tj's request: pinch/corner resize, easier move, cut-off bottom-right gr
  cd1f331 ckpt 435: SHIPPED v0.15.0 (code 20): floating CNO widget, placed bets, teams, green chec
  0d37c74 ckpt 434: pre-release: v0.15.0: CNO widget you can touch (floating over Novig): Up/Down 
  df36b8b ckpt 433: H9 full test: fixes + forced full rerun 388 tests 0 failed 3 skipped exit 0; v
  d43058c ckpt 432: full test: ESPN 403s a Vigilant User-Agent (live) -> OkHttp default UA; capped
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
