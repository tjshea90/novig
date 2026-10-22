# BoltOdds Pro — permanent research memory (Tj, 2026-10-12)

Tj's request (2026-10-12, verbatim): "I got a new api. It is boltodds pro. Set it up for the vigilant app. Integrate it just like all the other apis ... set up vigilant to take full advantage of everything boltodds pro tier offers, including the web socket. Thoroughly study the docs to make sure the app is taking full advantage of the pro api and using all the correct commands and usage."

Source: https://boltodds.com/docs (one static page, saved raw as `research/boltodds_2026-10-12/docs_index.html`, text in `docs_index.txt`), https://boltodds.com/pricing (`page_pricing.txt`), the home page (`page_home.txt`). Operator: Spro Technologies Inc., support@boltodds.com. **Nothing in sections 1-5 has been run against Tj's key**: they are the docs plus the probes in §6. Section 8 lists what the first Test-key sample must settle (TBO5).

## 1. The Pro plan (pricing page, 2026-10-12)

$349 a month ($3,348 a year), "for serious bettors, developers, and growing startups":
- real-time odds over a WebSocket; **all sportsbooks, all leagues, all markets**; advanced WebSocket filters (sports, sportsbooks, games, markets);
- **3 concurrent WebSocket connections**;
- the **pre-built parlays endpoint** (`GET /api/get_parlays`);
- priority support.

NOT in Pro (Custom $499, or the separate "Live Play-by-Play and Scores API", both by contact): SGP Pricing Engine (`POST /api/fanduel/get_parlay_prices`), historical odds, custom sportsbooks/endpoints, **play-by-play** (`wss://spro.agency/api/playbyplay`), **live scores** (`wss://spro.agency/api/livescores`), **box scores / match stats / match info** (POST endpoints). The docs say those scores features are "available to Trial and Custom users". Vigilant therefore builds on the odds WebSocket, the snapshot and the info endpoints; the Test key also asks the two cheap `.../user` endpoints so the sample says whether this key carries the scores feeds (§7).

Cheaper tiers, for the record: Starter $99 (1 league, 1 market per connection, 1 connection), Growth $219 (3 leagues, all markets, 1 connection). Supported data (home page): NHL/AHL/SHL/…, F1/NASCAR/IndyCar, MLB/KBO/NPB/NCAA baseball, tennis (Slams, ATP, WTA, Challenger, ITF), NFL/NCAAF/CFL, UFC/boxing, esports, golf, NBA/NCAAB/WNBA/Euroleague/…, soccer (MLS, EPL, La Liga, …), table tennis.

## 2. Connection and authentication

- WebSocket: `wss://spro.agency/api?key=YOUR_TOKEN`. The key goes in the query string (REST too: `?key=`), not a header. "Once ack_message is received, you have been validated": the first frame is `{'action': 'socket_connected'}`.
- Send a subscription "soon after connecting" (troubleshooting): `{"action":"subscribe","filters":{"sports":[...],"sportsbooks":[...],"games":[...],"markets":[...]}}`. A filter left out = all of that kind. Sports, sportsbooks, games, markets MUST be spelled exactly as `get_info` / `get_games` / `get_markets` return them; anything else is ignored.
- Re-sending a subscribe changes the filters on the same connection; it is rate limited: `{"action":"error","message":"Re-subscription rate limit hit. Please wait 5 seconds before re-subscribing."}`. Acknowledged by `{"action":"subscription_updated","message":"You have successfully updated your subscription filters."}`.
- Frames: the docs print single objects but the example loops `for data in msg`, so a frame may be a JSON ARRAY of messages. Vigilant accepts both. Max message size 64 KB (FAQ). "Message format can be changed or modified in the future."
- Reconnect: the examples wait 5 s and reconnect forever. Close codes listed: 1000, 1001, 1002, 1003, 1011.

## 3. Messages (`action`)

| action | meaning |
|---|---|
| `socket_connected` | key accepted |
| `game_update` | ALL odds of one game at one book replaced (`data.outcomes` is the full set) |
| `game_added` | named only in the example code (new game at a book); treated like `game_update` |
| `line_update` | some outcomes of a game at a book changed; `odds` `None`/`''` = suspended/deleted/unavailable (drop that outcome) |
| `game_removed` | game left the book (finished, suspended, …) |
| `sport_clear` | every game of `data.sport` at `data.sportsbook` cleared ("connection drops on our backend") |
| `book_clear` | every game of `data.sportsbook` cleared |
| `ping` | keep-alive |
| `subscription_updated` / `error` | see §2 |

Shape of `data` (game_update / line_update / game_removed / snapshot `initial_state`): `timestamp` (UTC ISO, outside `data`), `sport` (e.g. "MLB", "NBA", "Wimbledon (M)"), `sportsbook` (lower-case slug: "draftkings", "betmgm", "ballybet", "hardrock", "neobet"), `game` (id string `"<Team A> vs <Team B>, YYYY-MM-DD, HH"`; HH is the 12-hour hour of the start), `home_team`, `away_team`, `info` `{id, game, when: "YYYY-MM-DD, hh:mm AM/PM", link}`, `outcomes`.
`outcomes` is a map keyed by a readable label ("New York Mets Moneyline", player props "Auston Matthews Over 0.5 Goals" in `get_parlays`) of `{odds: "-3333" (American, as a string), link (deep link), outcome_name ("Moneyline", "Goals", "Home Runs"…), outcome_line ("0.5" or null), outcome_over_under ("Over"/"Under"/null), outcome_target (team or player name)}`.

**Time zone of `when`**: the docs' tennis example (`match_info`) has the match scheduled 20:00 UTC (started 20:09 UTC) on 2026-02-26 and BoltOdds' id for it is `..., 2026-02-26, 03` = 3 PM, i.e. US Eastern (EST, UTC-5). So `when` is read as America/New_York (inferred, not stated; the same assumption as Kalshi's Eastern dates). Start times are only to the minute.

## 4. REST endpoints (Pro)

All `GET`, key as `?key=`:
- `/api/get_info` → `{"sports":[...], "sportsbooks":[...]}` (the exact filter spellings).
- `/api/get_games` → map of game id → `{when, game, orig_teams, universal_id, sport}`.
- `/api/get_markets[&sports=A,B&sportsbooks=x,y]` → `{sportsbook: {sport: [market names…]}}`.
- `/api/snapshot[&sports=&sportsbooks=&games=&markets=]` (filters as repeated or comma parameters in the docs' `requests.get(params=...)`; lists) → JSON array of `{timestamp, action:"initial_state", data:{…as §3…}}`. "Intended to retrieve the initial state when establishing a connection or reconnecting ... not intended to be polled continuously."
- `/api/get_parlays?sportsbooks=<one slug>` → array of `{odds, total_bets, type ("SGP_PLUS"…), legs:{label:{game, sport, universal_id, outcome_name, outcome_line, outcome_over_under, outcome_target}}}`: popular parlays at a book with how many bets were placed. Not used by Vigilant (Novig has no parlay market; the counts are public-money signals, not prices): read only by the Test key for the sample.

## 5. Limits ("subject to change")

- WebSocket connections: 12 a minute per IP. GET endpoints: 12 a minute per IP (3 in quick succession). `/api/snapshot`: 6 a minute per IP **plus a per-key token quota per 24 hours** ("larger requests use more tokens", the amount is not published). Pro: 3 concurrent WebSocket connections. Exceeding limits "temporarily throttles".
- The docs' advice: do not poll GET endpoints for odds (that is what the WebSocket is for); avoid frequent calls to rarely-changing endpoints (info, games).

## 6. Probes run from the Claude container (2026-10-12, no real key)

- `GET https://spro.agency/api/get_info` (no key) → HTTP 403 `{"detail":"Missing API token"}`; `?key=INVALID` → HTTP 403 `{"detail":"Invalid API token"}` (same for `/api/snapshot`).
- WebSocket `wss://spro.agency/api?key=INVALID` → upgrade accepted (101), then the server closes with **code 1008, reason "Invalid token"** after ~2 s; with no key at all → the upgrade is refused (non-101).
- So a bad key is told apart from an outage by the 403 `detail` text / the 1008 close. The host is reachable from this container's proxy. No real key has been used by a session (Tj offered it; the app's own Test button is the route: three connections are allowed, but a session's connection would still sit in the app's allowance, and the 12-a-minute connection limit is shared by IP).

## 7. Vigilant's wiring (v0.90.0)

Code: `data/.../boltodds/` (tests: `BoltOddsTest`, `BoltOddsLiveTest`, app `BoltOddsWiringTest`, `ApiProbeTest`). Settings › APIs & keys › **BoltOdds Pro**: the key list (rotated; a refused key rests, the next is tried), the switch (`ApiGates.useBoltOdds`, default off), "Every sportsbook BoltOdds lists" (`boltAllBooks`), Test key and Test and share sample.

- **Two sockets, one per kind of data** (Pro allows 3; the third is left free for Test key's listening probe): the **lines** socket (moneylines, spreads, totals, team totals, alternates, 1st-half / first-5-innings / 1st-inning lines) and the **props** socket (player props). A `game_update` replaces a game's outcomes at a book, so the two kinds of market cannot share a board; each socket parses half the traffic. `BoltOddsStream` owns one: connect with `?key=`, wait for `socket_connected`, send the subscription, read the snapshot as a STREAM (`BoltJsonArray`, never held whole), apply `game_update` / `line_update` / `game_removed` / `sport_clear` / `book_clear`. Opened only while a scan uses it (closed 10 minutes after the last use, and at once when the switch goes off or a key is gone: `VigilantApp.syncSgo`).
- **Subscription**: `sports` = the BoltOdds sport names of the leagues picked in Settings (matched by letters, "NBA Preseason" counts as NBA); `sportsbooks` = `get_info`'s slugs that are the books Vigilant prices with (or all); `markets` = `get_markets`' names that `BoltMarkets` can use (lines socket: line names; props socket: stat names with a Novig market). `get_info` / `get_markets` are cached 6 hours (12 GETs a minute are allowed). A changed list is re-sent on the same connection no sooner than every 5.6 s (the server refuses sooner) and re-reads the snapshot only when it ADDS a sport, book or market.
- **Reconnects**: any close empties the board (a quote from before a gap is never served), waits 6 s doubling to 2 minutes (12 new connections a minute per IP are allowed), tries again; a connection that lived a minute starts the wait over. Close 1008 "Invalid token" refuses that key. OkHttp pings every 20 s and drops a dead link.
- **The scan reads the boards, not the API**: `BoltGamesSource` (id `bolt`) and `BoltPropsSource` (`bolt-props`) are `ReferenceSource`s answered from memory, so asking costs nothing however often. They are NOT on an `ApiChain` (nothing rests for them, they rest for nothing) and sit after SportsGameOdds, OddsPapi and OddsPapi Free in `Scanner.SOURCE_ORDER`: for a book several send, the earlier source's quote wins, so an unverified feed only fills gaps and adds books until its first sample has been checked (TBO7).
- **Quote time**: BoltOdds sends only changes, so a steady line is hours old and still current. A quote counts as seen when its book last spoke about the sport (`BoltOddsStream.aliveAt`: the socket is live, its snapshot was read, and that book was heard within 30 minutes), else it is as old as the message that set it (so a dead or silent feed ages out by `Freshness`). `BoltConvert` builds markets from the structured fields only and drops, never repairs, a pair that does not close (moneyline both teams; total Over and Under on one number; spread both teams on numbers that are each other's negative; prop Over and Under of one player on one number); a book that names home and away the other way round is flipped.
- **Diagnostics** (`== BOLTODDS PRO ==`): switch, keys, REST health, each socket's state, connects, drops, last close, frames (arrays, biggest), snapshot read, board size by book and sport, message counts, the names NOT priced (sport | name ×count), the first message of every kind.
- **Not used**: `get_parlays` (popular parlays with bet counts: public-money signals, and Novig has no parlay market; Test key reads it for the sample), `get_games` (only for the sample), play-by-play / live scores / box scores (not Pro), SGP pricing and deeplinks (not Pro / no use), historical odds (Custom), tennis (BoltOdds names tennis per tournament, "Wimbledon (M)"; the other feeds keep it).

## 8. Not known until a real key answers (TBO5)

1. The sportsbook slugs in `get_info` — above all whether **Pinnacle, Circa, bet365** are there (they decide whether BoltOdds can be a sharp fair line or only a soft-book one) and the exact spelling of the books Vigilant prices with.
2. The exact `outcome_name` / `outcome_over_under` / `outcome_target` for game totals and spreads (docs show only moneyline and player props: "Goals", "Home Runs"), and how alternates and periods (1st half, F5) are named in `get_markets`.
3. Whether a WebSocket subscription is answered with the current state or only with changes (the docs send you to `/snapshot` first), the `ping` interval, whether frames are arrays.
4. The snapshot's token cost, its answer size for a whole sport, and any quota header.
5. Whether this key carries play-by-play / live scores / `get_parlays`.
6. Whether `timestamp` is the book's change time or BoltOdds' processing time, and how late a quote that has not changed is re-sent.

Settings › BoltOdds › "Test and share sample" saves the answers of all of the above to Downloads/Vigilant (`vigilant-boltodds-sample-*.txt`); when Tj sends it, check `BoltParser`/`BoltConvert`/`BoltBooks`/`BoltMarkets` against it and correct this file.

## 9. Sample #1 (Tj's v0.90.0 Test key, 2026-10-12) and v0.90.1

Full table: `research/boltodds_sample_2026-10-12.md`. Facts the real key gave: 34 sportsbooks (no Pinnacle / Circa / bet365 / Bookmaker / SuperBook; exchanges prophetx, kalshi, polymarket, novig, "dk predictions", "fd predicts"); 78 sports, the preseason is "NBA Pre" / "NFL Pre"; a game's name ends in a 12-character id, `when` is Eastern time; `get_parlays` for draftkings: HTTP 403 "Not offering parlays for this sportsbook just yet"; `/playbyplay/user` and `/livescores/user` answer 200 `{"connections":0,"total_games":0,"games":[]}`; the snapshot of NBA (offseason names) was `[]`. **The odds WebSocket sent no frame in 15 s** and was not refused.

v0.90.1 answers that without guessing which way the server is odd: binary frames are read (UTF-8, gzip, zlib), a connection that opened and said nothing for 4 s is subscribed anyway, any frame makes it live, and the Test (and Diagnostics) record the upgrade answer, frame kinds, the first raw frames and every close code. Test key v2 closes the app's own sockets, snapshots the sport with the most market names across up to 10 books (the Vigilant books plus the exchanges), keeps two raw games per book, and opens `/livescores` and `/playbyplay` with a two-game subscription to say whether those sockets are part of this key. When sample #2 arrives, read its "websocket probe", "websocket /livescores" and "snapshot" sections first.

## 10. Live games and the soft-book live fair (v0.95.0, 2026-10-12)
- BoltOdds DOES carry live: each game entry has `info.is_live` (parsed since v0.95.0 into `BoltBookGame.live`); live lines arrive on the same odds socket as `line_update`. The pregame fair now leaves a flagged game out (TBP9). `/livescores` and `/playbyplay` accepted a subscription on this key (sample #2) but their updates have not been read: no scores yet.
- No sharp book among the 34 (v0.94.1 file), so the live fair is a CONSENSUS (`BoltLiveConsensus`): devig each book, one vote per company, trimmed mean (5+ votes), >= N companies pushed inside M seconds and within X points of each other, else the line is closed. `BoltLiveFeed` speaks it as Pinnodds frames (push: `BoltBoard.addGameListener`, 100 ms debounce, 1 s look that closes lost lines) so the Pinnodds live lane runs on it unchanged (`PinnFeedChoice.BOLT`). Paper only until `ApiGates.boltLiveReal`. Trigger forced off SCORE (no scores); edge floor 4%.
- Unproven on a live key: whether a trailing consensus beats Novig's own live repricing. Read the paper journal before allowing real orders.

## 11. BoltOdds on the GitHub engine (2026-10-15; Tj: "allow me to send my ... boltodds key to GitHub for rapid scanning")
The BoltOdds key is sent to the repository secret `BOLTODDS_API_KEY` with the other API keys; the engine (`CloudSources`) opens the same two odds sockets (lines, props) as the app and reads their boards from memory. The plan allows 3 connections (12 new a minute per IP): the engine uses 2, the app's own sockets (live lane, streaming engine) and the Test key's probe share the third, so with the cloud scan on the app normally holds none. Not on an `ApiChain` (free memory read, no sharp books): `research/cloud_novig_bolt_2026-10-15.md`. **Unverified from a runner:** the connection limit per IP and the snapshot size; read `scanNote` (`bolt`, `bolt-props`) in `cloud-feed/status.json` first.


## 12. Docs re-read 2026-10-22 (AF; verbatim on the `api-docs` branch)
- **`GET /api/odds/user?key=`** (new): `{plan, connections, subscriptions[{subscribed, sports, sportsbooks, games, markets}], snapshot{daily_tokens, tokens_used, resets_at}}`, 6 a minute per IP. Used: the key check and every "too many concurrent connections" refusal say how many sockets the key has open and the snapshot tokens (`BoltOddsUser`).
- Its llms.txt now lists **Circa, Bet365, BookMaker** and a **"sharp consensus line" (Pro)**. The key's `get_info` decides what flows; with "every book" on the engine subscribes to all of it. `consensus`, `dk predictions` and `fd predicts` are never a fair-line vote (derived / same company: `OpBooks.EXCLUDED`).
- Play-by-play and live-scores sockets now have documented error codes (subscription and close codes per feed); plans "start at $119/month billed annually".
