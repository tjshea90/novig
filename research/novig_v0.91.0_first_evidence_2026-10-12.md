# Novig live feed: first evidence from a real key (v0.91.0, Tj's diagnostics 2026-10-12 04:24 EDT)

Source: `research/uploads/2026-10-12/e9c47b13-vigilant-diagnostics-v0.91.0-2026-10-12-0424.txt.gz`. v0.91.0 had been installed 6 minutes, so
its numbers are one short stretch; the rest of the file (counters, 793k calls) is mostly v0.89.x. TNV11.

## What worked (first contact with a live key)
- **bbo works.** `held: 621 markets (574 on bbo, 47 on book, 156 deepened)`, `bbo: 1091 snapshots this connection, 0 unreadable`. No fallback fired.
- **Scans got much faster.** v0.89.34 (04:14): 115 s, 1,464 prices through the key, feed held 0 of 1,344. v0.91.0 (04:15-04:22): 13-34 s, 120-270 through the key,
  1,150-1,200 pushed. Scan p95 106,733 ms -> 34,272 ms. REST book reads since the app opened: 1,829 in 6 min (scan 1,182, scan-deepen 502), the first scan after the process start included.
- **Pushed order results work.** `orders: pushed (410 events heard, 0 gaps)`.
- One connection kept open: `connects 2, drops 0, upgrade 429s 0`.

## What to look at (not bugs yet)
1. **Stream allowance shows `512 refilling 4 a second`, room 2000.** Those are the documented DEFAULTS. Either this key's `/v3/limits` says 4 a second (then the 32 a
   second in the docs is not this key's) or `tune` never ran. The report did not say which: v0.92.0 prints the source. At 4 a second a bbo market costs 2 s and a book market 4 s
   of refill, so every market added after the first big subscribe is slow: that is why `live feed held 621 of 759 asked` (138 waiting) and why 502 scan-deepen reads went to REST
   (the 2 s wait cannot be met when a deepen is 16 tokens a market).
2. **Canary: `no reply to the book check in 10 s (inconclusive)`.** The canary asks `book` for a few bbo markets; at 4 tokens a second with the bucket just drained by the big
   subscribe, 10 s is too short to get its 16-token subscribes through. Inconclusive is safe (bbo stays on, the unreadable/empty nets are quiet); v0.92.0 makes the canary wait for the
   bucket instead of timing out.
3. **429s (2,679, Retry-After 1) are the keyed/public REST route** (`/v3/public/catalog/markets/...`), from the cold scan after a process start and from older versions; with the feed holding
   the books they are gone from steady state (the last block of scans has none).
4. **400 INVALID_QUERY "invalid segment 'LALIGA' / 'PREMIER LEAGUE': the string is not in the vocabulary"** on `/v3/public/catalog/events`: 62 in one hour (00:17-00:46, none since). The
   Soccer league names are not Novig segment names. Stopped by itself (soccer unpicked?); worth a guard that never sends a segment Novig has not listed (TNV13).
5. 451 x157 (carrier address), dns x1881 (phone network): not the app.

## Consequence for the work
- Print where the allowance came from (done, v0.92.0). Judge TNV12 (raise "Novig prices per scan") only after Tj's next file says whether the key refills 32 a second.
- If the key really refills at 4 a second, **deepening through the stream is slower than a REST read**; the deepen must not wait on a 16-token subscribe it cannot afford. Plan: when
  the bucket cannot cover the deepen now, go straight to REST for those markets and subscribe `book` in the background (TNV14).
