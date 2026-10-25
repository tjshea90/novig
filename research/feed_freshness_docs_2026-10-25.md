# Feed freshness and matching: what the docs say vs what the feeds do (PX9f-h, 2026-10-25)

acct-7542bf (tj outlook), resuming acct-27a37e (tj Gmail)'s cut-off session. Tj: "Shouldn't you review the docs thoroughly for mapping and matching bets and figuring out the freshness problem?"
and "I give Claude full permission to use the apis directly with my API keys and unlimited usage to figure out how to get full books coverage, speed, freshness of odds, and accuracy".
Keys stay in GitHub secrets; every keyed measurement below ran on Actions (`propline.yml` bookparam, `sgo-probe.yml`).

## 1. PropLine: an undocumented 60-book limit took the whole feed down (FIXED, PX9f)

- PX9c (19:37 UTC) asked PropLine for 69 books + Novig = 70 `bookmakers` keys. PropLine answers more than 60 with HTTP 400 `Too many bookmakers keys: at most 60 per request. Split the request into several calls.`
  Measured on the key (`propline.yml` task `bookparam`): 36, 40 ... 60 keys -> 200; 65 and 70 -> 400. The limit is NOT in llms-full.txt, docs.txt or recipes.txt of 2026-10-25 (`research/propline/docs_2026-10-25/`),
  which say only "Unknown keys match nothing (no error)".
- Effect 19:37-20:53 UTC: every PropLine source (games, props, 1st half) and the other-books sheet failed; the list was priced from SGO, ParlayAPI and Kalshi (list_audit: 0 PropLine quotes).
- Fix: `PropLineClient.bookParts` (even parts of at most `MAX_BOOKS` = 60) + `merge`. First scans after: PropLine sent 430,491 game-line quotes a scan; 20 listed, 14 with 5+ companies (was 7 of 15), none with 1;
  Clemson @ Florida State ML 34 companies (was 13), totals 24-25.
- Still to watch: the stream's `filter_bookmaker_key` has a DOCUMENTED cap of 1,000 characters (422 above it); the 69-book list is 631.

## 2. PropLine's ages are true poll ages; its polled books are slower than its docs say

- Docs: `last_seen_at` = "the last delivery this outcome appeared in ... whether or not the price moved"; `last_change_at` = when the price last moved. Vigilant dates a quote by `last_seen_at`
  (`PlBook.seen`), the right field: an unmoved price is current as of the poll.
- Docs: five books arrive by push (DraftKings, FanDuel, Fanatics, Kalshi, Polymarket: seconds); "the other 25 books are polled ... 60-90s pre-match".
- Measured (engine heartbeat, 2026-10-25 20:34 UTC): NFL books re-read every 125-276 s, NCAAF every ~740 s, MLB ~637 s, NBA 61 s, NHL 73 s. So a polled NCAAF book is up to 12 minutes old by
  PropLine's own read, which no client can make fresher. That is where the listed bets' 680-727 s "STALE" books come from.

## 3. The 15-minute limit was letting those in; now only SGO keeps it (PX9d)

- Tj's SGO Pro setting (15 min) was one global limit. `Freshness.plainFastFeeds = true` on the engine: SGO's own quotes keep 15 min, every other feed the plain 5 (10 past 3 h).
- Census before the switch (run 40232545799): near lines with 3+ companies 59,947 under the long limit vs 59,787 plain (-0.3%); 8+ companies 37,529 vs 37,216.
- Outlier screen (`badOdds`, tj Gmail's in-flight counter): 1,155 soft-book quotes and 53 of Matchbook dropped in a scan; Pinnacle never. The soft-book median does not screen out the sharp books.

## 4. SportsGameOdds' lastUpdatedAt: undefined in its docs (measuring)

- SGO's docs (sportsgameodds.com/docs/llms-full.txt, 2026-10-25) list `lastUpdatedAt` per book and alternate line but never say whether it is the last price change or SGO's last read;
  they say data "may not update more than every 30 seconds to 1 minute" by plan. SGO supplied 60 of 75 quotes behind the listed bets at a median 452 s.
- `tools/sgo_stamps.py` (`sgo-probe.yml`) reads SGO's main lines twice 2 minutes apart (does the stamp move while the price holds?) and compares SGO's push-book prices with PropLine's push copy.
  First run: SGO's Cloudflare refuses Python's default User-Agent (error 1010); fixed. Result: below when it lands.

## 5. Matching

- `list_audit` SIDE flags on player props were Pricing's deliberate `PropHaircut` (Under 0.03, Over 0.07; `ThinEarlyHaircut` 0.025 on NHL shots), not orientation errors; the audit now allows them.
- No DUPLICATE-COMPANY (one company counted twice) on the listed bets after the new books.
- crossDiff (one book, two feeds, same line, different price): push books far 88,938, polled near 18,068 a scan. The fresher stamp wins a book (`freshestWins`), so which stamp SGO's is (section 4) decides
  whether SGO's copy of a push book is ever the one used.
