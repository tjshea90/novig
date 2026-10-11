# CHECKPOINT 2917 — read me first, then TASKS.md

**Written:** 2026-10-11T02:47:19Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `b9e2b689` (this checkpoint is the commit after it)

## Just done
UA2-UA4 built: per-API switches (usePinnwire/usePinnapi/usePinnodds + gating), Settings APIs page (switch+Test+usage per API, Test active APIs with one-league source reads, Refresh usage), ApiProbe usage prompts into the meter; tests ApiProbeTest(12), APIs page tests

## Do this next
Run the full floor, update docs (CLAUDE.md/UNABATED/ODDSPAPI notes, RESEARCH §129 addendum), BUILDLOG via ship, release, report to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/test/kotlin/com/tjshea/vigilant/app/SettingsPagesTest.kt

## Last ten checkpoints
```
  9636ce1d ckpt 2916: UA1 done: Unabated per-key 5s gate + key rotation (refused/429 -> next key),
  57e2e312 ckpt 2915: UN3 wired: VigilantApp (Unabated last-in-line sources, OddsPapi Free closes 
  cf5a29a1 ckpt 2914: UN3: Unabated + OddsPapi v4 (Op4 client/budget/store/Op4Closes) done in data
  4a8f4dbf ckpt 2913: UN3 part 1: Unabated client/parser/books/convert/sources compile (data modul
  071cc44d ckpt 2912: UN1 done: raw Unabated/OddsPapi(v4) docs saved to research/unabated_oddspapi
  639e07b2 ckpt 2911: v0.88.11 released+recorded (tennis: Tennis switch, auto-bet from Vigilant sc
  5d8c65b9 ckpt 2910: pre-release: v0.88.11: tennis in the auto-bet (from Vigilant's scan) and the
  93119d04 ckpt 2909: pre-ship: v0.88.11: tennis in the auto-bet (from Vigilant's scan) and the bi
  506a68de ckpt 2908: TQ1/TQ2 written: autoBetTennis setting+migration, AutoBet.judgeScan/scanStak
  ad32dd40 ckpt 2907: TQ: wrote tennis auto-bet/auto-bid task into TASKS.md
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
