# PropLine Streaming Lite: back on, docs re-audit, what the two sockets are for (PLS, Tj 2026-11-17)

Tj (typed, with three screenshots: PropLine's Webhooks page #939/#940 and the Streaming Lite welcome e-mail) resubscribed to Streaming Lite and asked for the page
https://prop-line.com/llms-full.txt to be checked against the app and engine, Streaming Lite turned on and used fully, and for the sockets to be explained ("One of the sockets is dedicated to novig. Why?").
Docs saved: `research/propline/docs_2026-11-17/` (llms-full.txt dated 2026-11-16, openapi.json). Earlier: `PROPLINE_STREAMING_2026-10-25.md` (the plan and the design), `PROPLINE_PRO_2026-10-16.md`.

## 1. Why it was not on (PLS1)
- The key in the e-mail screenshot has key id `b08b80ee26` (SHA-256 prefix, the engine's own id scheme): it IS the paid key the engine already holds in `PROPLINE_API_KEY`, so nothing new has to be sent to GitHub.
- When the plan lapsed the engine's ledger recorded `plan: free, 1000/1000, depleted until 00:00Z` for all three keys. PropLine REST then answered nothing ("All 3 PropLine keys are used up", `propline_1h` silent for 6 scans) and
  the stream was `off · no Streaming key read yet`: the engine opens the sockets only for a key whose last plan read showed 250,000 a day or more, and it read plans once an hour.
- Resubscribing changed nothing on the engine until its next hourly read.
- **Fix**: `PropLineAccount.watchUpgrade()`: a key whose last read was the Free plan and which the ledger holds as spent is read again every 5 minutes (a refused call costs nothing; the first answer with a bigger
  `X-Daily-Limit` clears the rest and the key becomes the Streaming key), run every minute by `CloudMain`; a pull to refresh / `engine_set.py --check-apis` also forces a read of every PropLine key. Keys with allowance left
  keep the hourly read (a free key read every 5 minutes would spend 29% of its day). Test: `PropLineUpgradeWatchTest`.

## 2. What the 2026-11-16 page says that the code had not met (PLS2)
| Docs | Code |
|:--|:--|
| Pinnacle caches its public feed up to ~15 min (since early Nov); `book_updated_at` = when that copy was made, `last_seen_at` = when PropLine read it | A Pinnacle price's age was `last_seen_at` (always seconds): PropLine's cached Pinnacle could win `freshestWins` over a genuinely fresh source and set the fair. **Fixed**: `PlOutcome.seenAt` (older of read and copy time, Pinnacle only), and neither a push nor a heartbeat raises a Pinnacle quote's age (`PropLineLive.market`). Test: `PropLinePinnacleCacheTest`. |
| `game_state` (MLB/NBA/NHL/NFL/NCAAF), `stat_update` (NBA 5 s), `play` (NBA play-by-play), all opt-in on the same subscription/socket | `game_state` is a score source and `stat_update` + `game_state` are journaled, on socket 2 (below; real delay 18 s, see there). `play` not used (24 s late, 24 MB a game; ParlayAPI's play-by-play is the live lane's NBA source when its plan is on). |
| heartbeat `caught_up_at` (how far behind PropLine's sending is, even with no events) | Read: the stream's health says `PropLine Ns behind`. |
| `ping_interval` 5-25 s in the auth frame | Asked for 5 s: a dead socket and a quiet score are told apart in seconds. |
| `4403` paused / plan lapsed | The "re-activate once" flag was never reset, so a second lapse would have waited 30 min each time: reset on every `ready`. |
| Concurrency: 20 requests in flight per key (503 + `Retry-After: 1` on the 21st); burst 100 / sustained 35 a s on Lite | Already within: 10 games at once, 25 a s, 429/503 + `Retry-After` honoured (`PropLineClient`, `tools/propline.py`). |
| History calls (`/odds/history`, `/odds/closing`, `/movement`, `/odds/depth/history`): 1,000 distinct events a key a day, 4 in flight | Only `tools/propline*.py` use them (a few hundred events a day, one or two at a time): inside. `/odds/closing` there passes `markets=` (without it only game lines come back). |
| `X-API-Key` header preferred over `?apiKey=` | Header used everywhere. |
| New: `/odds/depth/history` (Kalshi/Polymarket book depth, Hobby+), `/bets/settle` (settle any bet incl. half results), `/parlay/price`, `/bet-links/parlay`, esports and horse/greyhound grading, shot-volume props | Not needed by the pregame Novig engine (Novig has its own books and grading; parlays are Tj's tap). `/bets/settle` could cross-check the grader: noted, not built. |

## 3. The two sockets (PLS3, PLS4)
What the screenshot shows: #939 = the BOARD subscription (line_movement, line_added, line_pulled, market_suspended; 30 books; all scanned sports); #940 = the SIGNALS subscription (steam, resolution, ev; books: novig; min steam 40;
player props on; EV 1-15%). Both are the engine's, both active.
- **Why #940 was filtered to Novig:** not as a Novig price feed (the engine reads Novig's own socket and stream, which is faster and carries the order book): the filter only limits `resolution` (PropLine's grade of a prop) and
  `ev` (PropLine's +EV list) to the exchange the engine bets at; `steam` ignores book filters. **You are right that it was low value:** everything it carried was journaled for study (`propline-push-<day>`) and acted on by nothing
  (PropLine's +EV prop Overs lost in the 25,909-bet test; steam never had a rule).
- **Better use, built:** socket 2 now also carries `game_state` (the live score, MLB, NBA, NHL, NFL, NCAAF, from each league's own live feed) and `stat_update` (live player stats; journaled). **Measured on PropLine's own recorded game** (propline-live-recorder sample, Magic at Celtics 2026-11-16, 358 deliveries): a `game_state` arrives a median 18 s (p90 47 s) AFTER the league's play, one every 15 s in play, `event_id` is a number, and the status stays `in_progress` through "Game End" (all handled, `PropLineGameStateTest` replays six real frames): a delayed score for context, not a trigger for the seconds after a score. It is the live lane's THIRD score source (after ParlayAPI's play-by-play
  and SharpAPI's game state, before SX Bet's; `CloudSources.altScore`). SharpAPI's game state needs its add-ons and ParlayAPI's play-by-play needs its paid plan, both lapsed, so this is currently the lane's only league-feed
  score beside SX Bet's. A score's age is the socket's last word (pings every 5 s), a live row unchanged for 10 min is not believed (`PropLineGameState`). Live Board shows a "PropLine live scores" socket row.
- The existing steam/resolution/ev journals stay (free: pushes are not metered and socket 2 was open anyway).
- **Subscriptions on PropLine's side:** the earlier #940 (steam, resolution, ev) is brought up to date IN PLACE (`PATCH events`); if PropLine refuses to change events the new one is made and the earlier one of ours (exactly that
  event set on the websocket transport) is deleted, never left active beside it; a subscription of any other shape is never touched. Every subscription PropLine holds is logged at connect (`PropLine holds #939 websocket
  active events=... sports=N books=N`) so the engine log shows PropLine's side without anyone opening the dashboard.
- **Considered and not done:** (a) splitting BOARD over both sockets: the first run had 0 gaps and a 1.6-3 s queue delay with 900,000 `line_added` in 15 min on one socket; revisit if `PropLine Ns behind` grows;
  (b) a live soft-book consensus from PropLine's five push books (DraftKings, FanDuel, Fanatics, Kalshi, Polymarket, receipt 0.1-1.1 s) to replace BoltOdds (its key is refused): a real project with live-money consequences,
  logged as PLS6; (c) `stat_update`/`play`: no pregame use; (d) `availability` (FanDuel in play) and `/availability/*` (Streaming tier, not Lite).

## 4. How to read it tomorrow
- Engine log: `PropLine plans: #1 streaming-lite ...`, `PropLine holds #939 ...`, `PropLine stream signals: subscription 940 brought up to date (game_state,steam,resolution,ev)` (or `made` + `earlier subscription 940 removed`), `ready`.
- `status.json` `propLineStream`: both sockets `live`, `PropLine Ns behind`, `game state: N games in play of M held, K pushes`.
- **If `game_state` stays at 0 pushes while games are live**, PropLine applies the Novig book filter to game_state too: then drop `filter_bookmaker_key` from SIGNALS and move `resolution`/`ev` off it (journal only).
- Revert: `PropLineStream.Sub.SIGNALS` events back to steam/resolution/ev; delete `PropLineGameState` and the `propLineScore` link in `CloudSources.altScore`; Pinnacle: `PlOutcome.seenAt` back to `last_seen_at` and `PropLineLive.market` `cached` to false.
