# CHECKPOINT 4227 — read me first, then TASKS.md

**Written:** 2026-10-25T19:37:07Z · **tests:** 1 RED: test_research_index.sh (8 green)
**Branch:** `ccr-27fee639-d3opru` · **builds on:** `0274da69b` (this checkpoint is the commit after it)

## Just done
PX9c: 34 more PropLine books (measured), Superbet/Napoleon + Fortuna groups, duplicate keys across SGO/PropLine, ParlayAPI streams date quotes before the age filter, ParlayAPI team totals, held rows age-checked, full scans every 6 min; census/diagnostics; bookstudy/bookprobe/list_audit tools (4,206 tests green on the final tree)

## Do this next
reset engine, then monitor with tools/list_audit.py + engine-log (list quality, quote census linesNow/linesPlain, crossDiff samples, heartbeat, push books); decide plainFastFeeds and push-book dating from the census

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  85919bf5a ckpt 4226: PX9 shipped to main + engine reset (run 40223571078); NBA/NHL/MLB altprobe:
  dce25e0c2 ckpt 4225: PX9: propline.yml altprobe takes sports (NBA/NHL/MLB probe of books at Novi
  91eb83777 ckpt 4224: PX9: real-time list no longer adopts partial-scan rows of leagues still wai
  be57f4981 ckpt 4223: PX8: ParlayAPI stream skips period rows + counts unread by market; parlay.p
  b20bd61f3 ckpt 4222: PX study linked in LEVERS
  39f01d546 ckpt 4221: PX done: books per bet median 5 (from 1-2), ParlayAPI pricing, scans unfroz
  4b75311ae ckpt 4220: PX7a: engine keeps quotes inside the plain 5-min limit (no 2-min headroom, 
  1a1f1c5cd ckpt 4219: PX census v2 + SharpApiSourceTest shutdown fix (full suite green)
  d5e872588 ckpt 4218: PX census v2 + SharpApiSourceTest shutdown fix
  7d0b702eb ckpt 4217: PX census split by feed id + PropLine unconfirmed/uncovered quotes
```

(29 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
