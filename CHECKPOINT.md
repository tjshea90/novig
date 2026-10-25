# CHECKPOINT 4230 — read me first, then TASKS.md

**Written:** 2026-10-25T20:46:22Z · **tests:** all 9 fast checks green
**Branch:** `ccr-e58c5a58-3qyzld` · **builds on:** `60bccbac9` (this checkpoint is the commit after it)

## Just done
PX9f fixed: PropLine's 60-bookmaker limit found by the bookparam probe; requests split into even parts and merged per game (PropLineClientTest); CHANGES.md logged

## Do this next
when tools/test.sh (full) is green: push.sh lands on main, engine_set.py --reset, then list_audit.py to confirm PropLine quotes are back (PX9g)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  452894c6d ckpt 4229: PX9f: found every PropLine source answering HTTP 400 on the engine since PX
  184068b3d ckpt 4228: research index rebuilt (propline_books study); PX9c landing
  92b5123b0 ckpt 4227: PX9c: 34 more PropLine books (measured), Superbet/Napoleon + Fortuna groups
  85919bf5a ckpt 4226: PX9 shipped to main + engine reset (run 40223571078); NBA/NHL/MLB altprobe:
  dce25e0c2 ckpt 4225: PX9: propline.yml altprobe takes sports (NBA/NHL/MLB probe of books at Novi
  91eb83777 ckpt 4224: PX9: real-time list no longer adopts partial-scan rows of leagues still wai
  be57f4981 ckpt 4223: PX8: ParlayAPI stream skips period rows + counts unread by market; parlay.p
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
