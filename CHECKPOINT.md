# CHECKPOINT 308 — read me first, then TASKS.md

**Written:** 2026-09-20T21:50:17Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `8a85470` (this checkpoint is the commit after it)

## Just done
Researched and confirmed: there is no $0/mo path to real Novig odds data from any provider right now. SharpAPI's free tier was wrongly recorded as including Novig (a marketing-copy misread, not verified) — its own Novig product page says 'Available on Hobby plan and above' ($79/mo), and the free tier is actually scoped to DraftKings+FanDuel only, which also resolves the old open question about Pinnacle (it's not in the free tier either, same reason). Re-verified The Odds API directly: confirmed no Novig anywhere in their docs, direct answer to Tj's question. Checked OpticOdds/Betstamp/MetaBet/odds-api.io fresh: none has a standing free tier that includes Novig (sales-gated trials, undisclosed pricing, or free tier scoped to different books). Corrected RESEARCH.md (§1, §4.2, new §4.2.2, §10) and BRIEF.md's architecture-decision entry that had recorded the wrong premise as settled.

## Do this next
Tell Tj plainly: no free Novig source exists among researched providers; Novig's own API (still awaiting their reply) is the only remaining lead that could be free, everything else found clears $30/mo. Nothing further queued unless he asks for something new or Novig replies.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  32c58c9 ckpt 298: v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (H
  8fe4bd2 ckpt 296: v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (auto
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
  e074e62 ckpt 284: Confirmed CI green for real on the Scan-failed diagnosis/fix push (run 3553801
  68c4176 ckpt 278: Diagnosed and fixed Tj's 'Scan failed — rate-limited or invalid' report (v0.
  5417b81 ckpt 266: v0.2.0 shipped: sport-selection picker request is fully done end to end — da
  9c59997 ckpt 256: Confirmed CI green for real on the sport-picker + fix push (run 35536752615, c
  d4ea339 ckpt 249: Fixed a real CI failure caught on the first push of the sport picker (run 3553
  2c1f31c ckpt 241: Built the sport-selection picker per Tj's explicit instruction: no odds load f
  92edb91 ckpt 229: Wrote Tj's new sport-picker request into TASKS.md before writing any code, per
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
