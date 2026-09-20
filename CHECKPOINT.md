# CHECKPOINT 26 — read me first, then TASKS.md

**Written:** 2026-09-20T05:09:30Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `69099c3` (this checkpoint is the commit after it)

## Just done
Deep-dived Odds Assist Pro against Novig, hands-on (not just secondhand): loaded the live +EV tool in the pre-installed Chromium via Playwright, filtered it to Novig-only, and confirmed it surfaces real, current, dated +EV opportunities on Novig for free right now (captured examples in RESEARCH.md §8.1: Dolphins ML +809/18.73% edge, Toronto Tempo ML +733/12.22%, South Carolina ML +400/11.47%). Also found a real substantive concern worth flagging rather than just reporting 'it works': its largest edges cluster on extreme-longshot lines, exactly where simple multiplicative devigging is known to overstate underdog value (per RESEARCH.md's own §5 math) — so those edge numbers shouldn't be trusted blindly, since Odds Assist discloses neither its devig method nor a source-of-truth book the way OddsJam does. Verdict: real and free, not a full OddsJam replacement — the gaps (disclosed/tunable devig, bet tracking/CLV) are what the app should prioritize over matching its UI. Updated RESEARCH.md §8/§8.1/§10 and TASKS.md accordingly.

## Do this next
Tj now has an honest, hands-on-verified answer on Odds Assist Pro. Next real step per RESEARCH.md §10 remains unchanged: confirm with Novig directly whether official API credentials (§4.1) are actually free to obtain — that's still the single biggest unknown gating the app's own architecture. No app code started.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4ce2d00 ckpt 21: Wrote Tj's Odds Assist Pro deep-dive request into TASKS.md (raw message already
  5787e5b ckpt 18: Deep research on positive-EV betting for Novig, written to RESEARCH.md (new per
  1c3d806 ckpt 15: Wrote Tj's positive-EV research request into TASKS.md in his own words as untic
  e870ad3 ckpt 12: Stood up the full checkpoint/handoff system for novig, adapted from fantasy-foo
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
