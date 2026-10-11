# CHECKPOINT 2910 — read me first, then TASKS.md

**Written:** 2026-10-11T00:20:17Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eba05906-u8ocwe` · **builds on:** `7cd35670` (this checkpoint is the commit after it)

## Just done
pre-release: v0.88.11: tennis in the auto-bet (from Vigilant's scan) and the bids, with one Tennis switch (versionCode 184, v0.88.11)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.88.11), then run: bash tools/record-release.sh v0.88.11 184 "v0.88.11: tennis in the auto-bet (from Vigilant's scan) and the bids, with one Tennis switch"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  93119d04 ckpt 2909: pre-ship: v0.88.11: tennis in the auto-bet (from Vigilant's scan) and the bi
  506a68de ckpt 2908: TQ1/TQ2 written: autoBetTennis setting+migration, AutoBet.judgeScan/scanStak
  ad32dd40 ckpt 2907: TQ: wrote tennis auto-bet/auto-bid task into TASKS.md
  96b30ba1 ckpt 2906: v0.88.10 released+recorded (taker 'passed on' line on the Pinnodds live page
  8e064504 ckpt 2905: pre-release: v0.88.10: Pinnodds live page shows why the taker has bought not
  dca9aadb ckpt 2904: pre-ship: v0.88.10: Pinnodds live page shows why the taker has bought nothin
  19e7e08e ckpt 2903: pre-release: v0.88.9: live bids and Autopilot use a 2% margin under Pinnacle
  2816b7e4 ckpt 2902: pre-ship: v0.88.9: live bids and Autopilot use a 2% margin under Pinnacle's 
  d47da744 ckpt 2901: Tj's 4:45 screenshots: live bids post for real on v0.88.8, 0 fills so far (T
  42c0e068 ckpt 2900: v0.88.8 released+recorded (live engine shares the one Novig stream)
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
