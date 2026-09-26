# CHECKPOINT 426 — read me first, then TASKS.md

**Written:** 2026-09-26T20:28:57Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `97b73f9` (this checkpoint is the commit after it)

## Just done
H1-H3 data layer: PlacedBets, CnoBooks agreement (SPLIT) + CnoFeed.keepBooksFresh lane, PlayerTeams (ESPN), CnoFeed.appLink; 41 tests in 5 classes green

## Do this next
H5: VM watcher set (tab/pip/overlay) running watch + books lane + teams lane together; wire AppContainer (placed.json, teams.json); then H6 overlay widget

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  3ba6ab1 ckpt 425: H0 research done: PiP can't take touches -> overlay widget; CNO deeplink = Nov
  b55d6be ckpt 424: Recorded v0.14.1 release (G3 done); logged Tj's 6-item CNO widget request as H
  775ebaa ckpt 423: pre-release: v0.14.1: the mini window shows each pick clearly (names were draw
  7e9707d ckpt 422: G1-G2: mini window pick names readable (own Surface + full-contrast bold name;
  27d02b7 ckpt 421: Logged Tj's request: the mini window doesn't show what the pick is (screenshot
  7b222d1 ckpt 420: SHIPPED v0.14.0 (code 18): accurate, standalone CNO scanner; CI 36264649883 gr
  c727840 ckpt 419: pre-release: v0.14.0: CNO scanner made accurate and standalone: CNO-only mode 
  85c0abd ckpt 418: F6 full test of the CNO scanner: fixed UW-WC label, judged-book check, stuck p
  662465f ckpt 417: F2-F5 app side built: ScannerMode (both/vigilant/cno) w/ migration from miniSo
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
