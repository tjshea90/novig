# The bid desk's data on GitHub (design, Tj 2026-10-15)

Question: can GitHub provide the data that recommends the +EV bids so that the phone only posts them? **Yes, with one honest boundary: Novig's order books.** A bid is priced from two things: the FAIR odds (every reference API, matching, per-book devig, sharp flags, injuries) and NOVIG's book (the price to take, the best bid to beat, the ladder behind it, our own bids in it). The first is heavy, slow-moving and needs the API keys: it belongs on GitHub. The second is light but must be current to the second and needs Novig's key and websocket: it belongs on the phone. The desk's own interface already splits there: `MakerLine` (fair fields + book fields).

## What runs where

| Step | Where | Why |
|---|---|---|
| reading every fair-odds API, matching games, devig, per-book fairs, sharp books, injuries | GitHub (engine, done for the list) | keys, bandwidth, CPU |
| the fair-side checks of `MakerQuote.precheck` with the phone's mirrored bid rules (kind, start window, trap window, popularity, fair age, price window) -> the candidate lines | GitHub | pure, cheap; shrinks 14,000 priced sides to the few thousand worth a book read |
| Novig's order book per candidate (offer, best bid, bid ladder) | phone, over its key and stream | needs the key; must be seconds old to lead a book; GitHub's public reads run at ~2 a second |
| `Pricing.requote(candidate, book)` -> `MakerLines.from` -> `MakerQuote.decide` -> `MakerPlan` -> post/cancel/fills | phone (unchanged code) | wallet, exposure, repeat guard, orders, notifications |

## Safety argument
No bid logic is rewritten: the phone runs the same `MakerLines.from` / `decideAll` / `MakerDesk.cycle` on lines built from the candidate's own `Opportunity` (carried whole by `CloudCodec`, fair line and per-book odds included) re-priced against a fresh Novig book by the existing `Pricing.requote` ("the same arithmetic as `price` for one outcome"). `BidCandidatesTest` proves the equivalence: for one priced board and the same books, the posts decided from the candidates equal the posts decided from the full local scan, and every side the candidates leave out is a skip locally.

## Limits to say plainly
- The phone still reads Novig's books for the candidates (the same Novig reads a local scan makes), so the phone's Novig traffic does not vanish; the fair-odds half (the API fan-out, matching, devig, injuries) does.
- Candidates are judged at the engine's clock plus 15 minutes ahead and the phone re-judges them at its own clock: a line entering a window between two lists waits for the next list (minutes).
- The engine stays ~15 min per cold scan on public Novig reads; fair odds are as fresh as the scan that read them (the bid's own life is capped by its fair's age, as always).

## What was built (2026-10-15, v0.103.0)
- Engine: `BidCandidates.from(result, settings, now, unavailable)` (precheck of every fair-side rule at now and now+15 min; the line carries the market, fair, per-book and sharp fairs, basis, and the engine's own Novig side) -> `CloudBids` (`bids.json.gz` on `cloud-feed`, every 45 s while a scan runs and at its end; `null` = bids off, the file is removed; a bid-rule change (`CloudScanKey.Kind.BIDS`) republishes from the held list at once, no scan). Injuries: `CloudSources.askInjuries` (ParlayAPI's list, awaited) + `unavailable` (`PlayerOut`).
- Phone: `CloudBidLane` holds the newest list, reads Novig's books for candidates whose side is older than 90 s (stream's complete books free, the rest through the key's paced reads, 120 a round), `MakerLines.withBook` gives a line the new book (same four fields `Pricing.requote` sets), `lines(now, rules, keep)` judges each line at the phone's own clock (book age, fair age) and keeps/drops the sides it has bids on. `MakerRunner` uses it for `run`, `preview` and `post`; `CloudLink` loop 4 runs the rounds and the desk's passes (every 20 s at most).
- Safety: `CloudBidsTest` (equivalence of decisions, `withBook` = pricer, wire round trip, lane reads/ages/keep/drop), `CloudEngineTest` (publish only with bids on, withdrawn when off, rule change republishes without a scan, a failing candidate pass never costs the Feed), `CloudClientTest.fetchBids`. Decoding streams the lines (`JsonSplit`) so a list of thousands never becomes one tree on the phone.
- Read first when the bids look wrong: Diagnostics `== BIDS FROM GITHUB ==` (list age, lines, Novig sides ready, last read problem), `cloud-feed` `status.json` (`bidsAtMs`, `bidsCount`, `bidsBytes`) and `engine-log.txt` ("candidate lines failed").
