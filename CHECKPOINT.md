# CHECKPOINT 444 — read me first, then TASKS.md

**Written:** 2026-09-27T00:26:35Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `050bb84` (this checkpoint is the commit after it)

## Just done
K1/K2/K6/K9 data layer: CnoNetwork (DoH+remembered DNS, 20s keep-alive, 12s read timeout, retry once, CnoPace 1s), CnoFeed link lane + cno_links.json, lighter books lane waiting on errors, NovigBetFinder; data tests 277 + CnoNetworkTest 4 green

## Do this next
App side: VM keepLinksFresh + openBet (cache->CNO 5s->NovigBetFinder->toast), K7 x hide, K8 only-agreed setting, K3 stale notification, K4 narrow bar + 360dp, K9 RESEARCH 20.2, then tests, v0.15.2 ship; then K10 full test

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1d2da36 ckpt 443: Logged K9 (reliable CNO refresh research); CnoFeed link lane + lighter books l
  3f2758e ckpt 442: Logged K8 (only-agreed-bets option); CnoNetwork (RememberingDns, keep-alive 20
  17ba6f1 ckpt 441: v0.15.1 released + recorded; logged K6 (DNS/timeouts) and K7 (✕ hide button)
  5c9fe1c ckpt 440: Logged Tj's K1-K5 (bet slip taps, CNO unreachable, Milwaukee notification, nar
  3b2a13c ckpt 439: pre-release: v0.15.1: floating widget resizes with two fingers (spread/pinch, 
  3cf811b ckpt 438: J5 tests green: 409 tests 0 failed; FloatingWidget handles touches in dispatch
  a79a462 ckpt 437: J1-J4, J6 done: framed widget with corner handles, pinch/spread + two-finger m
  4cfc2e5 ckpt 436: Logged Tj's request: pinch/corner resize, easier move, cut-off bottom-right gr
  cd1f331 ckpt 435: SHIPPED v0.15.0 (code 20): floating CNO widget, placed bets, teams, green chec
  0d37c74 ckpt 434: pre-release: v0.15.0: CNO widget you can touch (floating over Novig): Up/Down 
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
