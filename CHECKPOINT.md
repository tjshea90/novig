# CHECKPOINT 330 — read me first, then TASKS.md

**Written:** 2026-09-25T02:52:57Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `24defa3` (this checkpoint is the commit after it)

## Just done
Read Novig's official v3 API docs + OpenAPI spec, verified the public no-key routes live against production, wrote NOVIG_API.md (permanent API memory) and pointed CLAUDE.md/BRIEF.md/RESEARCH.md at it

## Do this next
Wait on Tj: go-ahead for Stage 1 (public v3 client, retire proxies), management key for Stage 2 (websocket), and the reference-leg budget decision -- see TASKS.md 'Proposed next build'

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  9c728ab ckpt 329: Logged Tj's 2026-09-25 Novig API beta access message into TASKS.md
  9aeda4b ckpt 328: v0.3.3 shipped and confirmed green (CI run 35695873352, release run 3569566794
  30a5637 ckpt 327: Bumped versionCode 6->7 / versionName 0.3.2->0.3.3 for the OkHttp Authenticato
  cb8e8e5 ckpt 326: Diagnosed and fixed a real OkHttp Authenticator bug found from Tj's own device
  736058e ckpt 325: Shipped v0.3.2 for real (confirmed green: run 35693512531, release published).
  68871a7 ckpt 324: Extended the release.yml self-heal fix: the actual failure was a leftover DRAF
  982ee12 ckpt 323: Diagnosed and fixed a real release.yml bug hit for real shipping v0.3.2: cance
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
