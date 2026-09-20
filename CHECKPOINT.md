# CHECKPOINT 278 — read me first, then TASKS.md

**Written:** 2026-09-20T21:13:10Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `0775071` (this checkpoint is the commit after it)

## Just done
Diagnosed and fixed Tj's 'Scan failed — rate-limited or invalid' report (v0.2.0, SharpAPI leg). Ruled out the request shape (X-API-Key header, base URL, sportsbook=novig — all match SharpAPI's own docs per his screenshots). Found and fixed two real bugs: (1) SettingsScreen's 'Add a key' field had no keyboardOptions, so autocorrect was ON by default for a raw API-key input — fixed with explicit capitalization=None/autoCorrect=false/keyboardType=Password (text stays visible, no visualTransformation). (2) KeyRotator's AllKeysExhaustedException message was generic, giving no way to tell rate-limit vs. invalid-key apart without screenshots — KeyAttemptResult.RateLimited/Invalid now carry a reason (the real HTTP status), surfaced in the exception message; new test locks it in. Most likely root cause of THIS specific incident: SharpAPI's free tier is 12 req/min and Tj's own Playground screenshots show him firing several test Recipes around the same time as the app's refresh — a real 429 from combined usage on the single free-tier key, which is correct behavior, not a bug. engine+data tests green.

## Do this next
Push, confirm CI green for the app module (the Settings keyboard-options change is new app-module surface this container can't compile locally), then report the diagnosis and fixes to Tj plainly.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5417b81 ckpt 266: v0.2.0 shipped: sport-selection picker request is fully done end to end — da
  9c59997 ckpt 256: Confirmed CI green for real on the sport-picker + fix push (run 35536752615, c
  d4ea339 ckpt 249: Fixed a real CI failure caught on the first push of the sport picker (run 3553
  2c1f31c ckpt 241: Built the sport-selection picker per Tj's explicit instruction: no odds load f
  92edb91 ckpt 229: Wrote Tj's new sport-picker request into TASKS.md before writing any code, per
  1fe3119 ckpt 226: Confirmed CI green for the app module (run 35535815614, conclusion=success) �
  8be34bf ckpt 220: Wired Settings navigation into MainActivity (state-based, no nav library): Set
  aadab85 ckpt 208: Rewired TheOddsApiClient onto KeyRotator (was a single apiKey string) — auto
  8e70e49 ckpt 205: Built KeyRotator (data module) — provider-agnostic multi-key rotation with r
  b663452 ckpt 198: Wrote Tj's 'make the app functional' request into TASKS.md with the architectu
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
