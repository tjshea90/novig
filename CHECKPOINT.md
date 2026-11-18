# CHECKPOINT 4635 — read me first, then TASKS.md

**Written:** 2026-11-18T04:00:34Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `203158128` (this checkpoint is the commit after it)

## Just done
MKV: parlay maker in the app (Engine > Parlays: status, evidence counter, OFF/SHADOW/LIVE with a confirm, limits), engine publishes status.rfq, return-on-collateral pricing (4%/1pt per leg/2% floor, 2-5 legs, stake as asked), pricing study section 9; 4309 tests green; v1.0.137

## Do this next
ship v1.0.137: ci.yml green on the commit, ship.sh, release.yml, record-release.sh; then engine reset so the new pricing runs in SHADOW; MK5 grading; MK6 same-game

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  35071ac65 ckpt 4634: PLQ: answered Tj PropLine health+feature list
  3b6279c74 ckpt 4633: Handoff manifest saved (.handoff/). The handoff tool had replaced the marke
  b2e2a0f37 ckpt 4632: MK3: research index rebuilt for the RFQ leg/catalog studies (index test gre
  21bd5babd ckpt 4631: MK3: warm-run read (TALLY rows + 164 priced trades): market pays median +1.
  eb2082199 ckpt 4630: MK3: rfq_legs.py (what the legs of RFQs are, via Novig's public catalog) + 
  d15c145fe ckpt 4629: MK3: CHANGES.md line for the sampled RFQ journal; engine reset issued (cc b
  c855ae799 ckpt 4628: MK3: first real SHADOW run read: production serves RFQ v3, maker registered
  0f0039298 ckpt 4627: MK: maker landed 02:48, runner replaced again (the 02:46 job predated it); 
  f34f0e681 ckpt 4626: MK: Novig parlay maker built (protocol, pricer, maker, socket, engine wirin
  f0ff790a4 ckpt 4625: NVR done: verified live (device pass until Thu 03:00 ET; locks 37 games/160
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
