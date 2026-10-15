# CHECKPOINT 3313 — read me first, then TASKS.md

**Written:** 2026-10-15T02:25:12Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `4bc157db8` (this checkpoint is the commit after it)

## Just done
TCL14: usage meters merged (engine usage.json by key id, seed across handovers, UsageMerge, figureAtMs, GitHub share + figure age on meters, Diagnostics, page auto-refresh in cloud mode); v0.104.0 recorded

## Do this next
TCL14d: full floor + ship v0.105.0 (code 283), release, record, restart engine; verify usage.json on cloud-feed has no keys (grep), then Tj installs v0.105.0 + taps Send a Novig read-only key

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md
    ?? research/cloud_usage_2026-10-15.md

## Last ten checkpoints
```
  da6b9cf15 ckpt 3312: TCL14 written (usage meters accurate with scans on GitHub); v0.104.0 releas
  926e29cfc ckpt 3311: pre-release: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine 
  a50f88791 ckpt 3310: pre-ship: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a 
  810737e67 ckpt 3309: TCL13: engine Novig read-only key (CloudNovigKey mint+store, CloudNovig wir
  671773ac5 ckpt 3308: pre-release: v0.103.0: the bid desk's data runs on GitHub: the engine publi
  257a52f09 ckpt 3307: pre-ship: v0.103.0: the bid desk's data runs on GitHub: the engine publishe
  6a010ec91 ckpt 3306: TCL12: Diagnostics BIDS FROM GITHUB section, Bids tab wording, CLAUDE.md+de
  2ebaf483c ckpt 3305: TCL12: dropped-candidate lines become unavailable (desk takes the bid down)
  a2e65b47a ckpt 3304: TCL12: engine bid tests (5), fetchBids client test; engine gates on bidsNee
  aa4ad8bbf ckpt 3303: TCL12: CloudMain wiring (bidLines+publishBids, injuries via CloudSources), 
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
