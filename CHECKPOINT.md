# CHECKPOINT 188 — read me first, then TASKS.md

**Written:** 2026-09-20T19:48:38Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `156a73f` (this checkpoint is the commit after it)

## Just done
Second research pass on Novig's NBX API pricing/access: found strong converging evidence it's an institutional/B2B product, not a free individual developer signup — Novig's own Developer Relations job posting names the API's target users as market makers/liquidity providers/trading firms/B2B partners with high-touch relationship-based onboarding, and Novig's business model explicitly charges institutional market makers for order-flow access (that's what funds commission-free retail trading), plus a separate gated Market Maker approval program requiring a signed agreement. No public pricing, waitlist, or self-serve signup found anywhere. Updated RESEARCH.md (new §4.1.1, §1 bottom line rewritten to lead with this while keeping original framing for context, §10 item 1 narrowed). Updated TASKS.md with findings.

## Do this next
Tell Tj the honest, less-optimistic answer: probably not free, probably a sales conversation not a signup form, but still worth asking directly. SharpAPI's free 60s-delayed tier is now the most realistic $0 fallback for real Novig data.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  73d4460 ckpt 183: Wrote Tj's Novig-trading-API-pricing research request into TASKS.md (raw messa
  35a13a2 ckpt 179: Release v0.1.0 published: build, signature verification, server-side tag creat
  4cf8327 ckpt 168: First release.yml run: assembleRelease itself succeeded (proves the app builds
  afbb1cf ckpt 155: Tj switched the repo's default branch to main. Confirmed via the API, confirme
  013f762 ckpt 145: Tried to trigger release.yml — got a 404, workflow not found. Diagnosed why:
  a50ac5e ckpt 143: Switched Vigilant's release signing to match fantasy-football's precedent: gen
  200a6bd ckpt 137: Logged Tj's course-correction on release signing: checked Portfolio and fantas
  3bbf78a ckpt 134: Fixed a real regression from the ship.sh rewrite: tools/test_resume.sh's herme
  d115f2b ckpt 132: Built the real signed-release pipeline: generated Vigilant's permanent signing
  3fddc9e ckpt 126: Wrote Tj's 'where is the apk' request into TASKS.md. Confirmed this container'
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
