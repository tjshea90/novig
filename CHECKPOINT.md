# CHECKPOINT 3666 — read me first, then TASKS.md

**Written:** 2026-10-18T20:05:45Z · **tests:** all 5 fast checks green
**Branch:** `claude/optimistic-curie-r3n3xj` · **builds on:** `796dcc1ed` (this checkpoint is the commit after it)

## Just done
pre-release: Engine asks Pinnodds REST board first for Pinnacle, PinnWire backup (versionCode 336, v0.117.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.117.3), then run: bash tools/record-release.sh v0.117.3 336 "Engine asks Pinnodds REST board first for Pinnacle, PinnWire backup"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  796dcc1ed ckpt 3665: Pinnodds REST first in engine, pinnapi off
  7d889e28b ckpt 3664: pinnapi-first order + test + engine usePinnapi=true
  c6ca99eff ckpt 3663: wrote handoff-novig.md (active-memory handoff #1)
  d6cdfd5a3 ckpt 3662: pre-release: v0.117.3: the engine's Novig outage alert and retry wait now c
  ea474c7ae ckpt 3661: pre-ship: v0.117.3: the engine's Novig outage alert and retry wait now coun
  1c07c27c7 ckpt 3660: audited all restart/alert paths: only NovigErrorWatch mixed APIs (fixed); r
  5640a3e51 ckpt 3659: NovigErrorWatch: only Novig-lines count (ParlayAPI 502 no longer raises the
  c17c06549 ckpt 3658: built Sportmicro probe: tools/sportmicro_probe.py + sportmicro-probe.yml (n
  df851d160 ckpt 3657: tested Flashscore scraper and Sportmicro from docs (no key/egress here); Fl
  da772ea50 ckpt 3656: reviewed 9 sports-data sites; findings in research/api_sites_review_2026-10
```
