# CHECKPOINT 311 — read me first, then TASKS.md

**Written:** 2026-09-20T21:51:57Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `22dfec4` (this checkpoint is the commit after it)

## Just done
Answered Tj's question about whether anything else in SharpAPI's free tier is worth keeping wired into the app. Confirmed the free tier's actual scope (12 req/min, exactly DraftKings+FanDuel, 60s-delayed, pre-match/REST only) directly from their pricing page. Its book coverage is a strict subset of The Odds API's already-free reference leg (which also has Pinnacle), so nothing free from SharpAPI adds real capability right now. Noted one contradictory claim from a docs-page summary and didn't trust it since it's directly contradicted by the live 403 Tj already hit.

## Do this next
Nothing further queued unless Tj asks for something new. No code changes this round — pure research/answer.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ebb2cb7 ckpt 308: Researched and confirmed: there is no $0/mo path to real Novig odds data from 
  32c58c9 ckpt 298: v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (H
  8fe4bd2 ckpt 296: v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (auto
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
  e074e62 ckpt 284: Confirmed CI green for real on the Scan-failed diagnosis/fix push (run 3553801
  68c4176 ckpt 278: Diagnosed and fixed Tj's 'Scan failed — rate-limited or invalid' report (v0.
  5417b81 ckpt 266: v0.2.0 shipped: sport-selection picker request is fully done end to end — da
  9c59997 ckpt 256: Confirmed CI green for real on the sport-picker + fix push (run 35536752615, c
  d4ea339 ckpt 249: Fixed a real CI failure caught on the first push of the sport picker (run 3553
  2c1f31c ckpt 241: Built the sport-selection picker per Tj's explicit instruction: no odds load f
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
