# CHECKPOINT 4524 — read me first, then TASKS.md

**Written:** 2026-11-16T08:04:03Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-gq85mu` · **builds on:** `43feec9e4` (this checkpoint is the commit after it)

## Just done
pre-ship: The Tracker no longer re-reads Novig's wallet in the background every minute; only a pull to refresh (or every 15 minutes) does, which removes the lag

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
     M app/src/main/kotlin/com/tjshea/vigilant/app/CloudLink.kt

## Last ten checkpoints
```
  3e8fcd776 ckpt 4523: pre-release: Daily profit and loss counts lost bets Novig no longer holds, 
  4841d2f84 ckpt 4522: pre-ship: Daily profit and loss counts lost bets Novig no longer holds, so 
  7d8d07e21 ckpt 4521: pre-ship: Daily profit and loss counts lost bets Novig no longer holds, so 
  d2174abee ckpt 4520: pre-ship: Daily profit and loss counts lost bets Novig no longer holds, so 
  7a7ebd063 ckpt 4519: answered Tj numbers vs wallet; logged LD4
  064c4f76b ckpt 4518: v1.0.119 release.yml triggered; answered chat; logged BP1
  6ffb64685 ckpt 4517: pre-release: Daily profit/loss, charts and all-time come from Novig's own w
  55ec49fff ckpt 4516: pre-ship: Daily profit/loss, charts and all-time come from Novig's own wall
  f0704cd3a ckpt 4515: pre-ship: Daily profit/loss, charts and all-time come from Novig's own wall
  338d25680 ckpt 4514: LD1-3 built: LedgerDays (daily P/L from Novig wallet), pull reads wallet; L
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
