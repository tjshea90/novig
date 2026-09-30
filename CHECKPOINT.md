# CHECKPOINT 2146 — read me first, then TASKS.md

**Written:** 2026-09-30T03:18:51Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `fb8cba8f` (this checkpoint is the commit after it)

## Just done
Pacing bugs fixed (stale out-of-order answers ignored, STALE_WINDOW/SLACK; firstSeenMs paces a mid-month plan from its first day; CreditPaceTest 3 new failing-first tests green); INBOX key redaction (tools/redact_keys.py + test_redact_keys.sh) so Tj can paste his ParlayAPI key safely; H4/H5 recorded

## Do this next
H1 remaining fixes: B CNO read reason shown (pre-rescue counts + pause/error in Diagnostics), C reopen closeFinal bets when a new close source (ParlayAPI) is active, D Runway PinnWire+pinnapi combined; H5 ParlayAPI docs/Postman examples survey

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  4901bda3 ckpt 2145: Recorded Tj's diagnostics-review request as H1-H3
  18e92257 ckpt 2144: v0.27.0 (code 55) released and recorded (CI 36659840921 + release.yml 366601
  07ce7847 ckpt 2143: pre-release: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle'
  c5b40f47 ckpt 2142: pre-ship: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's c
  2e0a6863 ckpt 2141: Full test findings #2-#3 fixed: CLV card copy now names Pinnacle's ParlayAPI
  ebc4a263 ckpt 2140: Full test finding #1 fixed: background auto-scans could spend ParlayAPI's wh
  fda54162 ckpt 2139: RESEARCH.md §43 written (5 sources, ParlayAPI tested endpoints/costs, what 
  bac95a85 ckpt 2138: ParlayAPI UI + switch: meter line (today's scan share / free = closes only),
  9178cca7 ckpt 2137: G1/G2 tests green (67): CreditPaceTest 5, ParlayPropsTest 7 (bulk props pars
  90b0a29f ckpt 2136: G1/G2 code in, compiling: CreditPace (day's share, unused carries over, 300 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
