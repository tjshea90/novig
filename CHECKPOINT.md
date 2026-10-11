# CHECKPOINT 2975 — read me first, then TASKS.md

**Written:** 2026-10-11T16:52:46Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `5a6ba8775` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.19: every API toggled on is used for any data and one toggled off is never called (SGO/OddsPapi no longer rest the others, tennis no longer overrides a switch, Pinnodds stands alone); conservative worst-case devig everywhere; closing line made logical (real start times, sharp Pinnacle close preferred, independent CLV shown first) (versionCode 204, v0.89.19)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.19), then run: bash tools/record-release.sh v0.89.19 204 "v0.89.19: every API toggled on is used for any data and one toggled off is never called (SGO/OddsPapi no longer rest the others, tennis no longer overrides a switch, Pinnodds stands alone); conservative worst-case devig everywhere; closing line made logical (real start times, sharp Pinnacle close preferred, independent CLV shown first)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5a6ba8775 ckpt 2974: TL/TM/TN done, tests green (data, app); docs updated
  2b986156d ckpt 2973: TL2: removed OutsideSgo/OutsideOp/TennisOnly, every API on is asked, off ne
  f2ecdb21d ckpt 2972: TN code + tests green in data (real start sync, sharp-history close preferr
  bae53b9ee ckpt 2971: TM done in data: worst-case devig default + migration (schema 17) + CloseDe
  2fae65f9c ckpt 2970: wrote TM tasks (conservative devig); TL audit found: OutsideSgo/OutsideOp r
  03149e103 ckpt 2969: wrote TL tasks (all APIs on are used, off are never called)
  5b5f266b3 ckpt 2968: v0.89.18 shipped (share all bets, archive, stats filters, closing-odds fixe
  0778c1fda ckpt 2967: pre-release: v0.89.18: Share all bets with Claude (README + parts, every be
  8264b0eb4 ckpt 2966: pre-ship: v0.89.18: Share all bets with Claude (README + parts, every bet a
  085decdb5 ckpt 2965: Saved the closing-odds audit (research/closing_audit_2026-10-11.md, 25 unve
```
