# CHECKPOINT 3661 — read me first, then TASKS.md

**Written:** 2026-10-18T19:00:52Z · **tests:** all 5 fast checks green
**Branch:** `ccr-28648a70-j699u7` · **builds on:** `d8809f5bb` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.117.3: the engine's Novig outage alert and retry wait now count only Novig's own server errors (another API's 502, e.g. ParlayAPI, no longer shows 'Novig is failing' or slows the scan loop)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1c07c27c7 ckpt 3660: audited all restart/alert paths: only NovigErrorWatch mixed APIs (fixed); r
  5640a3e51 ckpt 3659: NovigErrorWatch: only Novig-lines count (ParlayAPI 502 no longer raises the
  c17c06549 ckpt 3658: built Sportmicro probe: tools/sportmicro_probe.py + sportmicro-probe.yml (n
  df851d160 ckpt 3657: tested Flashscore scraper and Sportmicro from docs (no key/egress here); Fl
  da772ea50 ckpt 3656: reviewed 9 sports-data sites; findings in research/api_sites_review_2026-10
  7d016453e ckpt 3655: ANALYST 2026-10-18: WATCH only, analysis skipped by gate; recorded v0.117.2
  ba3df931c ckpt 3654: pre-release: v0.117.2: PinnWire keys are spent only on PinnWire's own word 
  892bb2881 ckpt 3653: Live tab Sockets list gets an SX Bet row (gates.sxSocket)
  d4b6f019d ckpt 3652: pre-release: v0.117.2: PinnWire keys are spent only on PinnWire's own word 
  d88a82190 ckpt 3651: ApiProbe: PinnWire 429 reads window (minute vs day) + retry_after_ms instea
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
