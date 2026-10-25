# CHECKPOINT 4226 — read me first, then TASKS.md

**Written:** 2026-10-25T18:58:14Z · **tests:** all 9 fast checks green
**Branch:** `ccr-27fee639-d3opru` · **builds on:** `dce25e0c2` (this checkpoint is the commit after it)

## Just done
PX9 shipped to main + engine reset (run 40223571078); NBA/NHL/MLB altprobe: PropLine median 11 books at Novig's alternate numbers (NBA 502 lines, NHL 86), 19/18 on mains; ParlayAPI NBA/NFL alternates + 1H = Pinnacle alone

## Do this next
when run 40223571078 publishes: read engine-log for 'list quality', 'heartbeat:', quote census crossSame/crossDiff (push vs polled); decide PX9b (date push-book quotes current) and PX9c; tell Tj the numbers

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  dce25e0c2 ckpt 4225: PX9: propline.yml altprobe takes sports (NBA/NHL/MLB probe of books at Novi
  91eb83777 ckpt 4224: PX9: real-time list no longer adopts partial-scan rows of leagues still wai
  be57f4981 ckpt 4223: PX8: ParlayAPI stream skips period rows + counts unread by market; parlay.p
  b20bd61f3 ckpt 4222: PX study linked in LEVERS
  39f01d546 ckpt 4221: PX done: books per bet median 5 (from 1-2), ParlayAPI pricing, scans unfroz
  4b75311ae ckpt 4220: PX7a: engine keeps quotes inside the plain 5-min limit (no 2-min headroom, 
  1a1f1c5cd ckpt 4219: PX census v2 + SharpApiSourceTest shutdown fix (full suite green)
  d5e872588 ckpt 4218: PX census v2 + SharpApiSourceTest shutdown fix
  7d0b702eb ckpt 4217: PX census split by feed id + PropLine unconfirmed/uncovered quotes
  57b1594aa ckpt 4216: PX: QuoteCensus (per-feed counts where fair-odds quotes are lost) logged af
```
