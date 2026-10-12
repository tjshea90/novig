# CHECKPOINT 3083 — read me first, then TASKS.md

**Written:** 2026-10-12T09:21:56Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `b0b157802` (this checkpoint is the commit after it)

## Just done
Bet-sheet lag question (Tj): added timing line to every bet check (BET event: total, before-book, book read, rest) so the lag source is measured; v0.92.1 release pending CI (trigger trig_01L5WW9JyCPvaWb4LqrCAc9X)

## Do this next
Release v0.92.1 after CI; then ship the timing as v0.92.2 and read Tj's BET timing lines; candidate fixes: read the sheet's book from the stream when held on book, deepen on Feed render, bypass the REST gate backlog

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  064babb71 ckpt 3082: pre-release: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried t
  2c979489e ckpt 3081: pre-ship: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried the 
  0b6e32333 ckpt 3080: pre-release: v0.92.0: orders over Novig's websocket (trading-key connection
  26748a4b4 ckpt 3079: TNS5 stream history in event log; docs: NOVIG_API.md §27 (orders over the 
  18fee1961 ckpt 3078: TNS4: market status acted on: NovigBook.lifecycle from the stream; Pricing 
  c312ddb79 ckpt 3077: TNS3: socket compression on the market socket (header X-Novig-WS-Compress o
  a412e1130 ckpt 3076: Read Tj's v0.91.0 diagnostics + Test key screenshot (research/novig_v0.91.0
  14b1640eb ckpt 3075: TNS2 wired: ApiGates.novigSocketOrders, Settings switch + index entry, orde
  8288377fb ckpt 3074: TNS1: NovigOrderSocket (trading key, own connection, place verb, private or
  06a654e4c ckpt 3073: Tj asked: orders over the socket, socket compression, and acting on market 
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
