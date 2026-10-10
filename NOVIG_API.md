# NOVIG_API — the official Novig API (v3), as verified 2026-09-25

**Read this before touching any Novig data code.** It is the project's
permanent memory of Novig's official API: what it is, how auth works, which
routes the EV scanner needs, and what was checked against the live API vs.
only read in the docs. A fresh session on any account should be able to work
from this file alone.

**Source:** `https://docs.novig.com` — the Overview page carries a "Private preview,
open to select members" banner, but every doc page and the OpenAPI spec could
be fetched **anonymously** on 2026-09-25. To re-read a page, start from
`https://docs.novig.com/llms.txt` (index of every page, each as `.md`) and
the spec at `https://docs.novig.com/api-reference/spec-files/openapi-v3-target.json`
(OpenAPI 3.1, ~500KB). Signing test vectors:
`https://docs.novig.com/api-reference/spec-files/signing-vectors.json`.
This repo is **public**, so the docs are summarized here, not copied in. Refetch
them when you need exact field names.

**Status (2026-09-25, later):** Vigilant v0.4.0 uses the public routes for all Novig
data (`data/.../novig/NovigPublicClient.kt`). Tj has beta access but has **not yet**
created a key, so no signed route has been exercised. §9 is now history: the stale
v2/GraphQL clients it lists were deleted in v0.4.0.

**Status (2026-09-28):** Tj connected a key; keyed REST book reads work on his phone ("much faster").
v0.19.0 adds the websocket for keyed scans (§6, §11.1), not yet seen working live.

---

## 0. The one-paragraph version

Novig's official API is `https://api.novig.com/v3/...`. There are two tiers.
(1) **Public routes** (`/v3/public/...`) need **no key and no signature**. They
return the full catalog (events, markets, outcomes), a market's live order book,
and its recent trades. They are rate-limited per IP at the edge. **Verified live
from this container: they return real production data right now.**
(2) **Signed routes** need a keypair (Ed25519 or P-256) that you register with
Novig, and every request is signed with the `NOVIG-V3` scheme. These routes are
the real-time **websocket** (order book, best bid/offer, and trade pushes), orders,
positions, and balances. They require a key from Tj's profile, a subaccount, and
passing Novig's location checks (no VPN or proxy).
**No proxies, no GraphQL scraping, no third-party reseller needed.**

---

## 1. Environments

| Env        | REST + websocket host        | Money | Keys created at |
| ---------- | ---------------------------- | ----- | --------------- |
| Production | `https://api.novig.com` (ws: `wss://api.novig.com/v3/ws`) | Real | novig.com → Profile → Settings → Novig API |
| QA         | `https://api.qa.novig.com` (ws: `wss://api.qa.novig.com/v3/ws`) | Test | `https://novig-mobile-app--qa.expo.app` → Profile → Settings → Novig API |

- Keys are per-environment. A QA key on prod gets `api key not found`.
- QA signup uses Google, then fixed test values for the identity check. The docs
  publish them: DOB April 1 1975, phone `+14257789900`, code `123456`, card
  `4242 4242 4242 4242`, CVV `123`, expiry `12/30`. Useful for testing order
  placement risk-free if the app ever places orders.
- The old v1/v2 "NBX" API (`/nbx/v2`, OAuth2 `emm-token`, `wss://.../tape`,
  GraphQL docs) is under `docs.novig.com/deprecated/...`. **Do not build on it.**

## 2. Account model and key scopes

- **Trader** is Tj. A trader has up to **5 live subaccounts**, and each is a
  separate wallet (balance, positions, orders).
- A **key** is a keypair. Novig stores only the public half. Each key has one
  scope, fixed at creation:

| Scope              | Reaches         | Orders | Money | Limit            | Created by |
| ------------------ | --------------- | ------ | ----- | ---------------- | ---------- |
| `management`       | All subaccounts | No     | Yes   | 1 per trader     | Web: Profile → Settings → Novig API (browser generates the keypair and downloads `novig-api-key-<nickname>.pem`, PKCS#8) |
| `management::read` | All subaccounts | No     | No    | Unlimited        | `POST /v3/keys` (signed by management) |
| `trading`          | One subaccount  | Yes    | No    | 1 per subaccount | `POST /v3/account/subaccounts` (opens the subaccount and its trading key together) |
| `trading::read`    | One subaccount  | No     | No    | Unlimited        | `POST /v3/account/subaccounts/{keyId}/keys` with `scope: "trading::read"` |

- **Only `trading` or `trading::read` keys can read the signed catalog or open the
  websocket.** A management key can't. So real-time streaming needs at
  least one subaccount. **The subaccount doesn't need to be funded.**
- **The scanner should hold a `trading::read` key.** It can read
  the catalog and stream, but it can't place orders or move money. That is the
  safest key to keep on a phone.
- Key creation body: `{name, publicKey (SPKI PEM with newlines), algorithm:
  "Ed25519"|"P-256", expiresAt?}`. Returns `{keyId, algorithm, fingerprint}`.
  `keyId` is **not** a secret. A public key can be registered **only once**
  across all of Novig (409 if reused).
- Never set `expiresAt` on a management key. An expired one still holds the only
  management slot and can't sign its own revocation.
- Revocation takes effect within 60s. Revoking a key does **not** cancel its
  resting orders.
- Subaccount routes address the subaccount by its **trading key ID** (`{keyId}`).

## 3. Signing: the `NOVIG-V3` scheme (every signed request, GETs included)

Headers:
- `Novig-Key-Id`: the key's UUID.
- `Novig-Timestamp`: Unix **milliseconds**, unpadded decimal, within **±30s**
  of Novig's clock.
- `Novig-Signature`: **standard padded base64** of the raw signature bytes.
- `Content-Type: application/json`. Without it the server hashes zero bytes
  instead of your body.

String to sign: six lines joined by `\n`, **no trailing newline**:
```
NOVIG-V3
{unix_millis}            <- identical to Novig-Timestamp
{METHOD}                 <- uppercase
{path}                   <- e.g. /v3/keys ; never the full URL; never normalized/decoded; trailing slash matters
{canonical_query}        <- line always present, empty string if no query
{lowercase_hex(sha256(raw_body_bytes))}   <- empty body = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```
Canonical query: split on `&`. Split each pair at the first `=` (a bare `flag`
becomes `flag=`). Percent-decode (`%XX` as UTF-8, a bare `+` stays a literal `+`). Re-encode
everything except `A-Z a-z 0-9 - . _ ~` as uppercase `%XX`. Sort bytewise by name, then
value, keeping repeats. Join with `&`. The edge strips `Expires`, `Key-Pair-Id`,
`Policy`, and `Signature` query params, so never use those names.

Algorithms:
- **Ed25519**: signs the string directly. 64-byte signature.
- **P-256**: ECDSA prime256v1 + SHA-256, **DER-encoded** (70–72 bytes). Java's
  `Signature.getInstance("SHA256withECDSA")`, including Android Keystore,
  already outputs DER. That makes P-256 the natural fit for a hardware-backed,
  non-exportable Android Keystore key. WebCrypto's raw `r‖s` output would NOT verify.
- Hash the **exact bytes sent**. Re-serializing JSON after hashing breaks it.
- `signing-vectors.json` has 30 vectors plus two published test keypairs. **Never
  register a published keypair.** Unit-test the signer against these vectors
  offline. It's a pure function, so it belongs in the plain-JVM `data` module.
- Live check: `POST /v3/echo` returns the body with a `200` if host, key,
  clock, and signature are all correct. It costs 0 throttle tokens.

## 4. Location checks (signed routes only), which matter for the phone app

Every **signed** route runs two checks and returns **`451`** if either fails:
1. **IP check.** Refuses restricted states and **any VPN, proxy, or Tor exit**. A
   data-center IP is fine. → The v0.3.x rotating-proxy approach is fundamentally
   incompatible with signed routes, and Tj's VPN must be **off** (or split-tunnel
   Vigilant out) whenever the app uses a signed route.
2. **Companion window.** The key holder's device must have geolocated from a
   permitted state in the **last 3 days**, by **opening the Novig app**. The websocket
   closes with `1008 GEOLOCATION_EXPIRED` when it lapses mid-connection.

`451` error codes: `AnonymizedNetwork`, `RestrictedGeolocationRegion`,
`GeolocationNotFound`, `GeolocationFailed`, `InvalidGeolocationRegion`,
`GeolocationExpired`. The public (no-key) routes showed no location check from
this container's data-center IP.

## 5. Public routes: no key, no signature. **Verified live 2026-09-25.**

All `GET`, base `https://api.novig.com`, throttled **per IP at the edge**. The exact
public rate isn't published. Responses carry `access-control-allow-origin: *` and
are CloudFront-cached.

| Route | Params | Notes (verified) |
| ----- | ------ | ---------------- |
| `/v3/public/types/sports` | none | `FOOTBALL, BASKETBALL, BASEBALL, ICE_HOCKEY, SOCCER, TENNIS, BOXING, MIXED_MARTIAL_ARTS, GOLF, ENTERTAINMENT, ESPORTS` |
| `/v3/public/types/leagues` | none | `NFL, NBA, MLB, NHL, NCAAF, NCAAB, NCAAWB, NCAABSB, WNBA, UFC, Boxing, CFL, MLS, EPL, Bundesliga, Serie A, La Liga, Ligue 1, Champions League, Europa League, WTA, ATP, PGA, TGL, KBO, NPB, …` (exact strings, mixed case) |
| `/v3/public/types/markets` | none | ~100 market types: `MONEY, SPREAD, TOTAL, TEAM_TOTAL, 1X2, MONEYLINE_3_WAY_WIN/_DRAW, MONEY_1H, SPREAD_1H, TOTAL_1H`, plus many player props (`PASSING_YARDS`, `POINTS`, `PITCHER_STRIKEOUTS`, …) |
| `/v3/public/types/event-statuses` | none | `OPEN_PREGAME, CLOSED_PREGAME, OPEN_INGAME, SETTLED, FINAL, DELAYED, CANCELED` |
| `/v3/public/catalog/events` | `league, status, startsAfter, startsBefore, limit, after` | `{items:[{eventId, sport, league, status, description, startsTs}], next?}`. `description` is e.g. `"Atlanta Falcons @ New Orleans Saints"` (away @ home, full names). The docs say "don't parse it", but it's the only full-team-name field. |
| `/v3/public/catalog/events/{id}` | none | one event |
| `/v3/public/catalog/markets` | `league, marketType, eventStatus, event, startsAfter, startsBefore, limit (1–5000, default 500), after` | Comma-separated lists are allowed (`marketType=MONEY,SPREAD,TOTAL`). Each market: `{marketId, eventId, marketType, status, voids (PUSH/FMV), description, startsTs, fee{coefficient, makerCredit, charged}, outcomes:[{outcomeId, name, status}]}`. `max-age=10`. |
| `/v3/public/catalog/markets/{id}` | none | one market. Each outcome's `status` is `TBD` until settled, then `WIN`/`LOSS`/`PUSH`, or a decimal payout per $1 contract for a fair-market-value void: the Tracker's auto-settle reads it (`BetSettler`, 2026-09-27; the exact settled strings are per the docs' `outcomes[].status`, not yet seen live on a finished game). |
| `/v3/public/catalog/markets/{id}/book` | header `If-None-Match` | `{marketId, seq, orders:{<outcomeId>:[{orderId, price, qty}]}}`, best price first, queue order within a price. Returns `ETag` (`"<marketId>-<seq>"`), and a match gives `304`. `max-age=5`. |
| `/v3/public/catalog/markets/{id}/trades` | `limit, after` | `{items:[{tradeId, outcomeId, price, qty, ts}], next?}`, newest first. **Verified 2026-10-03 against the trade file (RESEARCH.md §71):** `outcomeId`/`price` are the RESTING (maker) order's: `{LAR, 0.515, 1030}` is the file's TAKER buying BUF at 0.485 for $4.9955 (`qty` = contracts of $0.01, `ts` = epoch ms). `limit` up to at least 500 (then `next` pages). Read by the trap guard before a game-line auto-bet (`NovigPublicClient.trades`). |

**Scale observed (one NFL game, Ravens @ Cowboys, 2026-09-25):** 500+ markets
per game, mostly player props. `marketType=MONEY,SPREAD,TOTAL` alone returned
**54 markets** (the main line plus many alternate spreads/totals). A week of NFL
game lines is about **850 book requests** per full REST scan. That's fine for
the main lines only (≈1 MONEY + the main spread/total per game), and wasteful for
everything. The websocket (§6) is the right tool for broad coverage.

**Observed quirks, not in the docs:**
- Book prices sometimes come back unpadded (`"0.38"`, `"0.615"`) while trade
  prices are padded (`"0.380"`). Always parse prices as decimals and never
  string-compare them.
- MONEY outcome `name`s are team **abbreviations** (`"DAL"`, `"BAL"`, `"NO"`,
  `"ATL"`). SPREAD names look like `"DAL +20.5"`. Map them to full names through the event
  `description`, and to the reference leg by team, never by array position (the
  outcomes array has **no fixed order**).

### 5.1 Measured live, 2026-09-25 (not in the docs)

- **Burst limit on public routes, measured properly (2026-09-25, later):** a burst of distinct
  book requests at ~21/s got the first `429` (`Retry-After: 1`, "Error from cloudfront") after
  ~100 requests. A steady 10/s saw ~6% 429s starting at ~36 requests. Tj's phone hit it with
  ~44 books every 15s (4 at a time), plausibly on a shared carrier IP (CGNAT). v0.6.0 paces
  public book reads at ≤4/s (burst ≤10, 2 at a time), pauses everything on Retry-After, and
  only fetches on a manual scan.
- **v0.10.0 pacing** (RESEARCH.md §15): 4/s rising to at most 6/s after clean runs, 3 in flight;
  any 429 halves the pace and restarts the ramp.
- **Burst limit on public routes (first observation).** A client that had just pulled a large catalog got
  `429` + `Retry-After: 1` on book requests at 4-way concurrency. The same pattern
  measured cold ran 30 books in 1.7s (~17/s) and 25 sequential books (~5/s) with no 429s.
  The limit is short and forgiving. `NovigPublicClient.books()` pauses for Retry-After and
  retries each book up to twice. A Retry-After over 5s (or an HTML 403) stops the batch
  and serves cached books.
- **Outcome naming.** Across NFL/NCAAF/MLB/MLS/EPL/UFC, 3,871 of 3,871 live game-line
  outcomes resolved to the correct team with `TeamMatcher` (abbreviations like DAL/MSST/
  NMSU/UNM/UK/USA, fighter initials like "L. Hernandez", 3-way "Newcastle
  MONEYLINE_3_WAY_WIN"). Re-check with `VIGILANT_LIVE=1 ./gradlew :data:test --tests
  '*LiveNovigSmokeTest'`.
- **TOTAL descriptions** read "BAL @ NYY t10.5". Outcomes are "Over 10.5" / "Under 10.5".
- **Catalog size.** NCAAF game lines are ~5,000+ markets (2.3MB raw, ~375KB gzipped).
  `startsBefore` (days-ahead window) cuts this a lot.
- **The Odds API now lists Novig** as bookmaker `novig` (region `us_ex`), as of its
  bookmaker page on 2026-09-25. It isn't needed, since we read Novig's own book directly. It must
  never be used as a *reference* book (it's the thing being priced).
  `TheOddsApiClient` filters it out.

## 6. Websocket: `GET /v3/ws` (signed; `trading` or `trading::read` key)

- Sign the upgrade request like any other route. Throttle `stream`:
  capacity 512, refill 4/s. The upgrade costs 32 tokens, then each subscription
  costs `weight × subjects`.
- Subjects: `market:<id>`, `event:<id>` (covers **every market of the event,
  including ones that open later**), `PRIVATE`.
- Channels and weights: `lifecycle` 1, `trades` 4, **`bbo` 8** (best bid/offer),
  `book` 16 (full depth), `orders` 1, `positions` 1. `trades`, `bbo`, and `book` include
  `lifecycle`.
- Verbs (JSON frames with an increasing `nonce` starting at 1): `subscribe`,
  `unsubscribe`, `snapshot`, `status`, `query_markets`, `query_events` (the
  last two page the catalog over the socket and debit the `read` throttle).
  Example: `{"nonce":1,"subscribe":{"events":{"<eventId>":"bbo"}}}`.
- Every subscription replies with a snapshot carrying `seq`, then deltas with
  `seq+1, +2, …` **per market, per channel**. On a gap, send `snapshot` (it keeps
  your subscriptions). After a reconnect, subscribe again. Never compare seq across
  connections.
- Book delta: `{kind:"add", order, outcome, price, qty}` or `{kind:"remove",
  order, reason:"fill"|"cancel"}`. A partial fill arrives as a remove followed by an add of
  the remainder at the same queue position.
- Novig pings every **15s**. Answer every Ping, or the connection is dropped (`1006`).
  Close `1008 SLOW_CONSUMER` means reconnect. `1008 GEOLOCATION_EXPIRED` means
  open the Novig app, then reconnect.
- **The charge is capped (docs re-read 2026-09-28):** a request is charged `weight × subjects` but at most
  the 512-token bucket; one over 512 passes only when the bucket is **full**, and empties it. A connection
  may watch at most **2,048 markets**; an event counts as all its markets; a subscribe past the cap
  answers `SUBSCRIPTION_LIMIT_EXCEEDED` and subscribes nothing. `unsubscribe` costs 1 per subject,
  `status` 1, `snapshot` the same as `subscribe`; an unresolvable subject still costs. A frame that fails
  to parse or hits the throttle is answered with no nonce. So: one `subscribe` of up to 2,048 markets on
  `book`, sent once the bucket is full (~8 s after the 32-token upgrade), loads a whole scan. That's what
  Vigilant does (§11.1). Error frames' exact shape isn't documented; Vigilant reads `code` at the top
  level or under `error`.
- **Efficiency:** subscribing to a whole NFL Sunday slate's events on `bbo`
  costs ~16 events × 8 = 128 tokens once (but those events hold far more than 2,048 markets: subscribe
  markets, not events). After that, every price change on
  every market in those games is pushed. Compare roughly 850 REST book polls per scan.
  This is the real-time path, and it's also the battery-friendly one: one socket,
  no polling. RESEARCH.md §7 already chose "single WebSocket, foreground service
  while scanning."

## 7. Prices, the book, and what "the price I'd get" means

- A price is a probability string on a **279-step grid**: `0.001–0.050` in steps of
  0.001, `0.055–0.945` in steps of 0.005, and `0.950–0.999` in steps of 0.001. Orders
  off the grid are rejected with `INVALID_PRICE`.
- One contract pays **1¢** if it wins. Cost = `P × N × 1¢`. The counterparty buys the
  opposite outcome at `1 − P`. Quantities are whole contracts. Balances are
  5-decimal strings. Parse money as a decimal, never as a float.
- **Every order is a buy.** Each outcome's ladder lists resting **bids** for that
  outcome. **A bid at P on outcome A is liquidity for outcome B at `1 − P`.** So:
  - **Taker price to buy outcome A now = `1 − (best bid on the other outcome)`.**
  - Size available at that price = the `qty` sum at that best opposing level.
  - Verified example (DAL @ BAL moneyline, 2026-09-25): bids DAL 0.38 /
    BAL 0.615, so you pay 0.385 to buy DAL and 0.62 to buy BAL. That's a
    half-cent spread, with 516k+ contracts (~$5k payout) at the top.
- The EV engine should use this **executable taker price** (with depth), not
  the last trade and not a mid. The v0.3.x GraphQL client used last-trade,
  which RESEARCH.md §4.4 flagged as a best-effort guess.
- Three-way soccer lines are **three separate Yes/No markets**
  (`MONEYLINE_3_WAY_WIN` home, `…_WIN` away, `…_DRAW`). **"No" is not the other
  team.** It's any other result.
- `voids: FMV` markets settle at fair market value if voided. Don't assume a refund.

## 8. Fees: read them from each market's `fee` object, not a table

`Fee = coefficient × P × (1 − P) × N × 1¢`, and **only the taker pays**. Makers never pay.
- Game markets: `coefficient 0.03`, `makerCredit 0.5`, `charged: WHEN_LIVE`.
  **Pregame taker fills are fee-free.** Fees apply only while the event is
  `OPEN_INGAME`, decided at match time.
- Futures in `NFL`/`MLB`/`NCAAF`: `coefficient 0.06`, `makerCredit 0.7`,
  `charged: ALWAYS`. **These charge pregame too.** `Fees.kt` models this (`FeeCharge.ALWAYS`,
  `MarketFee.FUTURES`), and Vigilant's pricing reads each market's own `fee` object.
- Futures in PGA/ATP/WTA/UFC: `WHEN_LIVE`, which in practice means never charged.
- `GOLIVE` **voids every resting order** and turns taker fees on. `UNLIVE` drains the
  book and turns them off. Both can repeat.
- RFQs/parlays are **not available through the API** (per Novig's access email),
  so the parlay fee in `Fees.kt` doesn't matter for anything the API can do.

## 9. How this changes the existing code (as of v0.3.3)

| File | Status now |
| ---- | ---------- |
| `data/.../novig/NovigApiClient.kt` + `NovigAuth.kt` | **Stale.** Built on the deprecated `/nbx/v2` API with OAuth2 `emm-token`. Replace them with a v3 client. |
| `data/.../novig/NovigLiveFeed.kt` | **Stale.** It targets `wss://api.novig.com/tape`. v3 is `wss://api.novig.com/v3/ws`, signed, with the channel model in §6. |
| `data/.../novig/NovigGraphQlClient.kt` + proxy settings | **Superseded.** The official public routes (§5) give the same data or better (a real book instead of last trade), for $0, with no proxy, no ToS gray area, and no IP blocking from a rotating pool. Retire the proxy UI once v3 is wired in. |
| `engine/.../Fees.kt` | Partly stale. Take the coefficient and `charged` from each market's `fee` object. Futures on NFL/MLB/NCAAF charge 0.06 **pregame**. |
| `EvScanner` / `EventMatcher` | Still valid in shape. Feed them executable taker prices from the book (§7), and team names from event `description` plus outcome abbreviations (§5 quirks). |

## 9.1 Novig's app links (verified 2026-09-26 from app.novig.us's JS bundle)

Novig's app is Expo / React Native; app.novig.us is the same code for the web. Its React
Navigation linking config (prefixes `novigapp://` and `https://novig.onelink.me/JHQQ`):

| Path | Screen | Params |
| ---- | ------ | ------ |
| `events/:orderslip_outcomes?/:partner_id?/:amount?` | Home, with the bet slip | outcome ids, comma-separated; a partner tag; a wager in dollars (docs.novig.com/affiliates/deeplinking: "Optional pre-filled wager amount (requires partner_id)"; used since v0.19.4 when Settings asks, `NovigLinks`; native form not checked on a device) |
| `event-markets/:event_id` | one event's markets | |
| `tournament-event-markets/:event_id` | a futures event | |
| `players/:player_id` | a player | |
| `autofill/:market_id` | a market, prefilled | query `outcome_id`, `wager` |
| `deposit/...`, `bonus`, `purchase`, `rewards` | account screens | |

CrazyNinjaOdds' deeplink for a Novig row answers `novigapp://events/<outcomeId>/cno` to a phone
browser (`https://novig.com/events/<outcomeId>/cno` to a desktop one): the id is the **outcome**
(checked: CNO's Dalton Schultz Over 5.5 receptions line → that outcome in
`/v3/public/catalog/markets?event=…`). So opening it puts that exact bet in Novig's bet slip.
Vigilant's own rows use `novigapp://events/<outcomeId>` the same way. Not checked on a device
(there's none here): the screen it lands on is inferred from the linking config.

## 10. Historical data: free, public, no key (useful for CLV and backtests)

`https://data.novig.com/reporting/trade-data/index.json` lists dates.
`/<date>/trades.csv` has the columns `timestamp, outcomeId, marketId, contractSeries,
league, marketType, tradeType, legs, cost, qty, side`. `/<date>/markets.csv` has
`date, marketId, reportTicker, openInterest, dailyVolume, open, high, low, close,
status`. Each file covers one Eastern-time day and publishes around 5am ET the next day.
`markets.csv` lists EVERY market listed that day, zero-volume ones included (`reportTicker` like `NFL-RECEIVING_YARDS`, `dailyVolume` in dollars): the denominator for how much takers trade each kind of market (RESEARCH.md §81.4, `tools/research/novig_popularity_study.py`). Data runs from 2026-08-03 through the latest day checked (2026-09-28, checked 2026-09-30). The data is anonymized. Observed: in
`trades.csv`, `cost`/`qty` look like **dollars** (e.g. `qty 33.53`), not
contract counts. Confirm before relying on it. Read the header row; Novig says
columns may be added.

**Verified 2026-09-30 and used for closing lines (RESEARCH.md §42, `NovigTradeCloses`):** a day's file is ~36 MB / ~240k rows, strictly
sorted by `timestamp` (ISO, with or without milliseconds), served with `Accept-Ranges: bytes` (a `Range` request answers 206 with
`Content-Range: bytes a-b/<size>`). A STRAIGHT (`legs` 1) trade is one TAKER row on the outcome bought at `cost/qty` (the price per $1 of
payout) and MAKER rows on the other outcome at `1 − price`; parlays are `COMBO` rows keyed by a parlay id. `markets.csv`'s `close` is the
day's last trade (in-play or settled), not the pregame close. Published ~09:00Z for the Eastern day before.

**Used for research (RESEARCH.md §62, 2026-10-02):** a STRAIGHT trade's TAKER and MAKER rows together give both sides of
every fill with its size, so CLV by trade size (who's sharp: big takers, big resting makers) can be measured from these files
alone (`tools/research/novig_size_study.py`). The files carry no start time and no result; the settled market leaves the
public catalog (`/markets/{id}` answers `MARKET_NOT_FOUND`, and `eventStatus=FINAL` lists only futures), so a game's start has to
be inferred from its trades. Accounts are anonymized, so one bettor's record can't be followed.

## 11. Throttles (signed routes, per key; `GET /v3/limits` returns live numbers)

| Bucket  | Capacity | Refill/s | Used by |
| ------- | -------- | -------- | ------- |
| place   | 256 | 8  | orders |
| cancel  | 256 | 16 | cancels |
| read    | 64  | 16 | catalog reads, ws `query_*` |
| account | 64  | 8  | positions, balances |
| stream  | 512 | 4  | ws upgrade + subscriptions |
| history | 512 | 4  | fills, transactions, settled orders (base + 1/page) |

`/v3/keys` and `/v3/account` are limited to 600 per 60s per key, method, and route. Transfers are limited to 5/s and 60/min.
A `429` carries `Retry-After` in seconds. Separately, the **edge throttles per IP**, and an
edge refusal is a **`403` with an HTML body** (no `code`) that never reaches Novig's
servers. Treat an HTML 403 as "slow down", not "bad key". **Seen live 2026-10-02 (Tj's v0.44.1 Diagnostics):** from 01:33 every SIGNED
route answered `423` (`/v3/limits`, `/v3/catalog/markets`, book reads, orders) while the public routes answered normally: an account-level lock
(the app reads Novig's `code` and names it; "A 423 doesn't clear on its own. Contact support."). Scans then fall back to the public routes and
are about a third as fast. `423` means locked,
self-excluded, or trading halted, and it doesn't clear on its own.

### 451 codes (docs.novig.com/api/errors, read 2026-09-28)

| `code` | What Novig judged | What helps |
| --- | --- | --- |
| `ANONYMIZED_NETWORK` | The request's **internet address** is on its VPN / proxy / Tor list ("A data-center address is fine") | Another connection (Wi-Fi ↔ mobile data); Novig support to review the address |
| `RESTRICTED_NETWORK_REGION` | The request's **address** is in a restricted state | Another connection |
| `GEOLOCATION_NOT_FOUND` / `_FAILED` / `INVALID_GEOLOCATION_REGION` | The key holder's **device** location check | Open the Novig app |
| `RESTRICTED_GEOLOCATION_REGION` | The last device check is in a restricted state | Be in an allowed state |
| `GEOLOCATION_EXPIRED` | Placements only (no check in 3 days); "A read admits a stale geolocation" | Open the Novig app |

`503 GEOLOCATION_SCREENING_UNAVAILABLE`: the screen itself is down. **Until v0.19.3 Vigilant told Tj "Turn the VPN off"
for `ANONYMIZED_NETWORK` though he had none** (2026-09-28): it's a verdict on an address, and shared Wi-Fi/carrier
addresses get listed. Test key now names the connection used, checks the phone for a real VPN (`TRANSPORT_VPN`), and
tries the other connection (`NovigKeyTest`, `app/PhoneNetworks`). `api.novig.com` has no IPv6 (A records only).
A refused key sends every scan to the public routes for 10 minutes at a time (much slower).

## 11.1 How Vigilant uses the signed API

**v0.60.0 (RESEARCH.md §81.1): a 404 on a book is a closed market, not a verdict on the key.** `GET /v3/catalog/markets/{id}/book` answers 404 (`MARKET_NOT_FOUND`) for a market whose game has just kicked off or that closed while a scan was still reading its plan (Tj's v0.59.1 file: 13 of them in three days, at kickoffs). Until v0.60.0 any non-429 answer on the key route stood the whole route down for ten
minutes (the scan went to the public routes at 2-4 a second); now a 404 is that book's own news (`BookFetch.Gone`: not served from the cache either), one 5xx fails only its book, six in a row stand the key down 30 s, and 200 404s in a row (a dead route, not closed markets) stand it down like any refusal. A game that has started is no longer read. Every stand-down (when, how long, why) is in Diagnostics
(`Novig key route:` line, the timeline's `NOVIG` lines, a health check). The public route's 404 is handled the same way.

**v0.44.0 (current, RESEARCH.md §63.3):** the websocket is OPENED at a scan's first plan (its bucket refills while the fair odds load) and
HANDED its markets once per scan, when the plan has filled in (every source answered, more unread lines than it holds, or 30 s): what it already
holds first, then the unread lines. Tj's v0.43.0 file showed why: "113 by live feed, 4475 through the key" in a 318 s, 4,588-price scan, the one
bulk subscribe having gone at ~8 s with the first source's lines. Unsubscribes are charged at most the bucket; a fresh connection forgets the last
scan's list. The scan's timing line says "live feed asked for N at X s".

**v0.19.3 (current):** the key's REST reads run 10 at a time in 30-price batches through an OkHttp client that allows
16 requests per host (OkHttp's default is 5, and an open websocket holds one: the key had 4 lanes; RESEARCH.md §29).
Refusals that arrive together (a whole wave in flight answered 429) slow the pace once, not once each. Pacing still
follows `GET /v3/limits`, whose shape was checked against the OpenAPI spec (2026-09-28). Every scan's timing is shown
under Settings › Novig API (`ScanTiming`).

**v0.19.0: the websocket loads each keyed scan** (RESEARCH.md §27). Tj connected a key on
2026-09-28 and reported scans "much faster", so the signed REST route works on his phone (signing,
location, trading::read scope). Each scan hands its whole plan (up to the budget, likeliest first) to
`NovigStream`: it connects (`GET /v3/ws`, signed like REST), waits until the `stream` bucket is full
again, then sends ONE `subscribe {markets: {<id>: "book", …}}` for everything wanted (≤2,000); later
additions go when their tokens are back; dropped markets are unsubscribed. Meanwhile the REST keyed
route (below) reads the likeliest lines; once the snapshot lands, every held book is taken with no
request. Closed 2 minutes after a scan last used it (at once off screen); on failure, REST for 5 minutes and the scan says
why. **The socket path is not yet verified against the real API** (the key can't leave the phone):
verified against a mock speaking the documented protocol only. If Tj's scan says "Novig live feed: …",
that message is the first real evidence of what differs.

**v0.10.0 pacing (still the REST route's):** public book reads 4/s rising 0.5/s per 40 clean requests to at most
6/s (a 429 halves it for a minute and restarts the ramp; 5 idle minutes restart it), 3 in flight;
keyed reads 14/s, burst 40, 6 in flight (the `read` bucket is 64/16 per second). Books are read
while the fair odds load and in most-promising-first order, a few at a time (RESEARCH.md §15).
Still no websocket: it's the only way to OddsJam-class speed and needs Tj's `trading::read` key.

**v0.6.0:** no websocket. Tj asked for manual-only scans, so a scan with a key
connected reads each book through the signed REST route `GET /v3/catalog/markets/{id}/book`
(same shape and ETag as the public one; `If-None-Match` is added after signing since it isn't
part of the NOVIG-V3 string). That draws on the key's own `read` bucket (64 burst, 16/s),
paced at 8/s burst 16, 4 at a time. Any refusal other than 429 (451 VPN/location, 401, 403
JSON) switches the rest of that scan to the paced public route, reports Novig's advice once,
and skips the key route for 10 minutes. `NovigStream`/`StreamBooks` remain in the code,
tested but unwired. **Still unverified against the real API** (no key yet).

**v0.5.0 (superseded):**

- **Setup** (`data/.../novig/signing/NovigSetup.kt`): management key → `POST /v3/echo` →
  reuse or open subaccount labeled `Vigilant` → mint `trading::read` key from a P-256
  keypair generated in the Android Keystore → echo with it. The management key is never
  stored. Only key IDs and Keystore aliases are saved (`NovigConnectionStore`).
- **Stream** (`.../novig/stream/NovigStream.kt`): signed `GET /v3/ws`, `book` channel per
  planned event, subscribed in chunks of ≤28 events under a local model of the `stream`
  bucket. Gaps trigger `snapshot`. `HybridNovigSource` serves stream books and falls back to
  paced REST. The app re-prices every 2s while live, and the socket closes whenever the app
  leaves the screen.
- **Verified offline only:** all 30 signing vectors, Novig's published signatures, and a mock
  websocket speaking the documented protocol. **Not yet verified against the real API:** no
  real key exists yet. The first real connect is the test. If it fails, the error text is
  Novig's own `code`/`message` translated (`NovigApiException.advice`).

## 12. Still unknown / unverified

1. The exact per-IP rate limit on public routes. Only the "throttled at edge" wording
   and CloudFront `max-age` (10s markets, 5s book) are known. Poll no faster
   than the cache anyway.
2. Whether Tj's beta access is prod-only or also covers QA, and whether his
   profile shows the **Novig API** screen yet. He has to look.
3. ~~No signed route has been exercised yet~~ The signed REST book route works on Tj's phone
   (2026-09-28). Still unverified live: the websocket (`/v3/ws`), its error-frame shape, and whether
   Novig's `stream` bucket starts full for a key that has never streamed.
4. Whether Android's built-in providers sign Ed25519 on every device Vigilant
   targets (minSdk 30). Sidestep it: use **P-256** in Android Keystore for the
   app's own key (§3).


## Finished games leave the public catalog (verified 2026-09-27 ~07:45Z)

`GET /v3/public/catalog/markets/{id}` answers **404** for a market whose game ended a few hours
earlier, and `/v3/public/catalog/events?league=MLB&startsAfter=<14 h ago>` (any `status` filter,
SETTLED and FINAL included) lists only open games and futures: the night's finished MLB games were
gone. So the public routes can't settle a bet after the fact. Vigilant's Tracker settles from final
scores instead (ESPN's scoreboard/box scores, MLB's Stats API; `data/tracker/Scores.kt`).

## 13. Full docs re-read, 2026-09-28 ~07:10–08:00Z (Tj: "use it efficiently and as the docs describe")

All 140 pages of `llms.txt` (86 current, 54 under `deprecated/`) plus the OpenAPI spec, compared with every Novig call
Vigilant makes. What changed in the app (v0.19.1), and what was checked and left alone:

| Docs say | Vigilant before | Now |
|---|---|---|
| `Market.strike`: "the line the market settles against … a spread's line is the home side's handicap"; `description` and outcome `name` are display text, "don't parse" | Lines read from outcome names only | Names still read (no structured team field exists), but a line must equal `strike`, a spread's on the side the names say is home, or the market is skipped. Live: 6,541 of 6,541 lines this week agree (`LiveNovigSmokeTest`) |
| `GET /v3/limits` (free, 0 tokens) reports the key's buckets and `maxWatchedMarkets`; "Model each throttle in your client" | Documented defaults hard-coded (read 64/16, stream 512/4, 2,048) | Read once per key per process; keyed REST paces at 90% of `read` refill, 70% of capacity as burst; the websocket takes `stream` and the watch cap. Defaults if Novig doesn't answer |
| Signed catalog (`/v3/catalog/events|markets`) for trading/trading::read keys; public routes "throttled per IP at the edge" | Board always from the public routes | With a key: the signed catalog (the key's `read` bucket, not the per-IP public throttle a carrier shares); public if it fails, for 10 minutes |
| Event status `DELAYED` is tradable (event-lifecycle) | Only `OPEN_PREGAME` (+ `OPEN_INGAME`) requested | `DELAYED` requested; a game held before its start is scanned like pregame, one held mid-game isn't (the catalog doesn't say whether its taker fee is on) |
| `limit` 1–5,000 (default 500) on events and markets; `after` cursor opaque | events 1,000 / markets 5,000, cursor passed through | Same; the cursor is percent-encoded exactly as signed (NOVIG-V3 canonical query) |
| Websocket channels: `lifecycle`, `trades`, `book`, `orders`, `positions` (the route's own page; `bbo` appears only in the connection page's cost table) | `book` | Unchanged: `book` is the documented one |
| Websocket errors: `{code, message, nonce}` | Read `code` top level or under `error` | Unchanged (covers it) |
| Fees: read `fee` per market, `WHEN_LIVE` charges only `OPEN_INGAME` | Per-market `fee` | Unchanged |
| Deep links (affiliates page): `novigapp://events/<outcome_ids>/<partner_id>`, web `novig.com/events/<outcome>/<partner>/<amount>` | `novigapp://events/<outcome>` | Unchanged (the documented form; a partner id is for affiliates) |

**Not usable:** the "odds screens" GraphQL API (`POST https://api.novig.com/v1/graphql`, one query per league returning every
outcome's offered price) answers anonymous queries with `{"errors":[{"message":"query is not allowed"}]}` (an
allow-list; checked 2026-09-28), needs keys "from your representative" (affiliates), and the docs say it's being migrated to
REST. No batch book route exists in v3; the websocket is the bulk path (§6, §11.1).

## 14. The trading side, and what a full re-read found (2026-09-29 ~08:00–09:30Z; Tj: "it can show the bets I actually placed and grade them and I can place bets through the API")

Re-read: all 86 current pages of `llms.txt` (54 more under `deprecated/`, left alone) and the OpenAPI spec (`openapi-v3-target.json`, 51 routes),
downloaded to disk and read from the spec's own schemas, not from page summaries. **No page changed the scanning design in §11.1.**
What is new here is the account/execution half of the API, which Vigilant has never used.

### 14.1 Every route, its key scope, its throttle and its cost
| Group | Route | Key scopes | Throttle · cost |
|---|---|---|---|
| Public (no key) | `GET /v3/public/{types/*, catalog/events[/{id}], catalog/markets[/{id}], catalog/markets/{id}/book, catalog/markets/{id}/trades}` | none | per-IP edge ("public") |
| Signed catalog | the same seven under `/v3/catalog/…` and `/v3/types/…` | `trading`, `trading::read` | `read` (64 burst, 16/s), 1 token a request |
| Auth | `POST /v3/echo` (0 tokens, any key), `POST/GET /v3/keys`, `GET/DELETE /v3/keys/{id}` | management (create/revoke), management::read (list) | `account` |
| Accounts | `POST/GET /v3/account/subaccounts`, `POST …/{keyId}/keys`, `GET …/{keyId}/balance`, `POST …/{keyId}/transfer`, `GET …/{keyId}/transfers/{id}`, `PATCH …/{keyId}`, **`GET …/{keyId}/transactions`** | management for opening, funding, keys, labels; balance also for trading keys; **transactions for management, management::read, trading and trading::read** | `account` (transactions: `history`, 6 + 1 per 100 rows) |
| Execution | `POST /v3/orders`, `POST /v3/orders/batch` (≤256), `DELETE /v3/orders`, `DELETE /v3/orders/batch`, `DELETE /v3/orders/{id}` | `trading` only | `place` (256 burst, 8/s) and `cancel` (256, 16/s) |
| Execution (reads) | **`GET /v3/orders`** (`status` filter, default OPEN; event/market/outcome filters), `GET /v3/orders/{id}`, **`GET /v3/portfolio/fills`**, **`GET /v3/portfolio/positions`** | **`trading` and `trading::read`** | `read`; settled orders and fills: `history` (fills 8 + 1 per 50 rows; settled orders 4 + 1 per 100) |
| Execution (snapshots) | `GET /v3/account/orders`, `GET /v3/account/positions` (ETag/304, carry the private stream's `seq`) | `trading` | `account` |
| Streaming | `GET /v3/ws` | `trading`, `trading::read` | `stream` (512 burst, 4/s) |
| Throttle | `GET /v3/limits` (0 tokens, any key) | any | free |
| RFQ (parlays) | separate LP-only pages | LP accounts (W-9, ~$30,000 deposit) | not for us |

### 14.2 The bets you place: what the API can and can't see
- **A trading key reaches ONE subaccount, forever** ("Each trading key reaches only its own subaccount, and that never changes"). A subaccount is a
  **separate wallet**: its own balance, positions, orders and fills. Money moves between the trader's **cash wallet** and a subaccount only with the
  management key's `POST …/transfer` (`fund` / `defund`, an idempotency `clientTransferId`, 5/s and 60/min).
- **So the API cannot list bets placed in the Novig app.** By the docs' own account model, the Novig app trades from the cash wallet, and no route reads it
  (no route lists the cash wallet's orders, fills or positions). The docs never say whether the Novig app also shows a subaccount's trades; unverified.
- **What a `trading::read` key CAN read, for its own subaccount:** every order (open and settled: `GET /v3/orders?status=FILLED|CANCELED|REJECTED|OPEN`),
  every fill (`fillId`, `orderId`, `clientId`, `marketId`, `outcomeId`, `qty`, `cost` in dollars (price = 100 × cost / qty), `taker`, `fee`, `ts`),
  positions (`qty` and `cost` per outcome: average price = 100 × cost / qty), and the **ledger** (`GET …/transactions`, `kind` = `FILL`, `FEE`,
  `MAKER_CREDIT`, **`SETTLEMENT`**, `TRANSFER_IN`, `TRANSFER_OUT`; signed `amount`, `ref` = the fill, transfer or market it settles). The ledger's
  SETTLEMENT rows are Novig's own grade of every bet in that subaccount, including fair-market-value voids (a payout, not a refund).
- **The Vigilant subaccount exists and is empty.** `NovigSetup.connect` opened it (label "Vigilant", never funded), registered a `trading` key whose
  private half sits in the Android Keystore under `vigilant_novig_trading_<stamp>` (same `<stamp>` as the saved read key's alias
  `vigilant_novig_read_<stamp>`; the app doesn't store the trading alias, but the read alias gives it), and minted the `trading::read` key the scanner uses.
  So today the key sees nothing of Tj's betting: no order has ever been placed through it.
- **Settled markets leave the catalog** (re-verified 2026-09-29: three `finalized` markets from data.novig.com's 2026-09-26 `markets.csv` answer 404 on
  `/v3/public/catalog/markets/{id}`; the spec calls the catalog "the open set"). The signed catalog's answer for a finished market is untested (it needs the
  key; the phone is the only place to try). `data.novig.com`'s CSVs carry `status` (`finalized` = settled) but **no winner**, so they can't grade a bet.
- **Settlement semantics (event-lifecycle page):** an event ends, each market is graded `Winner` (WIN on one outcome, LOSS on the rest), `Pushes` (PUSH on
  all: collateral back) or `FMV(price)` (each outcome's `status` is a bare decimal string, a payout per $1 contract; the prices sum to 1.000). A market voids
  one way only: `voids` says `PUSH` or `FMV`, "the exchange never pushes an FMV market". Remediation can undo a settlement (market back to CLOSED, outcomes to
  TBD, graded again): a graded bet can change.

### 14.3 Placing bets through the API (not built; needs Tj's approval, see RESEARCH.md §36.9)
- `POST /v3/orders {outcomeId, price, qty, tif, ttl?, clientId?}` with `tif` = `GTC`, `GTT` (needs `ttl` ms), `IOC`, `FOK`, `PO` (post-only; `ttl` optional).
  `price` is on the 279-step grid (§7) and is what you're willing to pay for that outcome; `qty` counts 1¢ contracts (so a $10 stake at 0.50 is `qty` 2000; the fees
  page says "N = qty / 100"). The answer `201 {orderId, clientId}` means queued, **not** filled: the private stream's `open` / `fill` / `cancel` / `reject` events say
  what happened (an unfilled IOC/FOK/PO is a `reject` with no HTTP status; if no `201` came, match `clientId` before re-sending: it is NOT checked for uniqueness).
- A taker bet is an `IOC` or `FOK` at `1 − best opposing bid` (§7): pregame it pays no fee (`WHEN_LIVE`), the fill's `price` can be better than the limit.
  `GOLIVE` voids every resting order (their `cancel` carries `reason: GO_LIVE`); a resting maker order pays no fee (and earns a maker credit only when a fee was charged).
- Refusals: `403` (scope, or KYC not passed), `404` (no such outcome), `422` (wallet doesn't cover it, or a position cap), `423` (locked or self-excluded), `451`
  (anonymized network, restricted region, and, **for a placement only, no device geolocation in the last 3 days**: open the Novig app), `413`/HTML `403` (body too big).
- Money never moves through a trading key ("a leaked trading key can lose its balance through trades, but it can't withdraw"). Only the management key funds
  a subaccount. **Since v0.23.0 Vigilant saves it** (Tj, 2026-09-29: "I only input the API key and file one time ... persist even through app
  updates"): `ManagementKeyStore` writes `files/novig_management_key.json` with the key ID in the clear and the PEM sealed by an Android Keystore
  AES/GCM key (alias `vigilant_novig_management_seal`, `KeystoreSecretBox`). Saved only after Novig accepted it (Connect, Enable betting, a
  transfer, or Settings' "Save key" = one signed echo); survives every update (files and Keystore are kept for the same app and certificate);
  left out of backups (it can't be opened on another phone); gone on uninstall / Clear storage, like the phone's read and trading keys.
  Settings shows "••••last4 saved on this phone" with Replace and Forget; a 401 on the saved key says "Tap Replace".
- QA (`api.qa.novig.com`, test money, published test identity in §1) exists for trying all of this without real money.

### 14.4 The websocket's private and lifecycle channels (unused so far)
`subscribe {private: ["orders","positions"]}` (1 token each) streams the subaccount's own `open` / `fill` / `cancel` / `reject` and position changes with per-subaccount
`seq` (heartbeat every 15 s repeats the last `seq`; a gap means `snapshot`); the market channels' `lifecycle` transitions are `OPEN`, `CLOSE`, `GRADE` (→ SETTLED),
`START`, `END`, `GOLIVE`, `UNLIVE`. `bbo` (8 tokens) exists in the cost table but no page documents its message shape: don't build on it unseen.
`events:` subjects cost one pair per event but count as every market the event holds against the 2,048 cap.

### 14.5 What this means for speed (with the measured numbers)
- The key's REST reads top out at the `read` bucket, 16 a second: 1,200 prices = 75 s by REST alone; the websocket is the only bulk path (§6).
- A fresh connection's first bulk subscribe can't go until the `stream` bucket is full again after the 32-token upgrade (512 ÷ 4/s ≈ 8 s); nothing in the docs shortens it.
  A connection kept open avoids it, and 8 s hides inside the free fair-odds sources' time.
- **Measured live 2026-09-29 from this container: Kalshi's game lines and props take ~27 s per scan** (57 series at the 2/s Vigilant paces anonymous reads to;
  429s came at ~3/s) and **Polymarket ~13 s**. A league's first bets wait for all its fair-odds sources (v0.19.2), so those free sources, not Novig, set
  "first bet at": the timing line now names them (v0.20.2).

## 15. Key rules learned for API betting (2026-09-29, docs.novig.com/api/api-keys + the OpenAPI schemas; Tj: "Build the betting through the API function")

- **One live `trading` key per subaccount.** `POST /v3/account/subaccounts/{keyId}/keys` "mints only `trading::read`" while the subaccount's `trading` key is live (`409` if
  you ask for `trading`); after the trading key is **revoked** (`DELETE /v3/keys/{id}`, management key; "takes effect within 60 s") the same route creates its replacement. The
  subaccount list's `keyId` is the *live* trading key; **a revoked trading key's ID still addresses the subaccount** (balance, transactions, transfers). So a phone whose Keystore
  lost the trading key's private half must revoke and re-mint; a phone that still has it just signs.
- **Balance** (`GET …/{keyId}/balance`): management, management::read and **trading**, not `trading::read`. Transactions (the ledger): all four scopes.
- **Funding** is only the management key: `POST …/{keyId}/transfer {direction: fund|defund, amount: "10.00000", clientTransferId}` → `202 Requested`, then poll
  `GET …/transfers/{id}` until `Applied` (money moved) or `Rejected`. 5 a second, 60 a minute. No route lists transfers.
- **Order fields** (`PlaceOrder`): `outcomeId` (uuid), `price` (string on the grid), `qty` (int32, min 1, 1¢ contracts), `tif` (`GTC`, `GTT` + `ttl`, `IOC`, `FOK`, `PO`), optional
  **`clientId`, which MUST be a UUID** (`format: uuid` in the spec; **verified live 2026-09-29**: Tj's first two real orders were refused `400` with
  "clientId: UUID parsing failed … found `v`" for `vigilant-<uuid>`, nothing placed; fixed in v0.21.2, and `placeOrder` now refuses a non-UUID before sending). It is never checked for
  uniqueness (a replay places a second order). `clientTransferId` on a transfer is a plain string (≤ 64), sent as a UUID too. Order `status` is `PENDING` (queued) / `OPEN` / `FILLED`
  / `CANCELED` / `REJECTED`, and "a partly filled order stays `OPEN`: track `remaining`, not the status". `GET /v3/orders` filters: `event`, `market`, `outcome`, `status` (default
  `OPEN`), `limit` 1–5000 (default 500), `after` (the `next` cursor). **Every request field of the placing path was re-checked against the spec on 2026-09-29** (orders, fills,
  positions, ledger, balance, transfer, key mint): the only mismatch was `clientId`. Answers `201 {orderId, clientId}` = queued only. `GET /v3/orders/{id}` "can answer 404 right after a 201".
  An unfilled `IOC`/`FOK`/`PO` ends `REJECTED`; a partly filled `IOC` ends `CANCELED` with what filled kept. Fills: `qty`, `cost` (dollars, `qty × price × 1¢`), `taker`, `fee`.
- **Placement location check:** as for any signed route, plus the device geolocation must be **under 3 days old** (open the Novig app); a read admits a stale one.
- **What a settled bet looks like** (event-lifecycle page, ledger schema): `GET …/transactions?kind=SETTLEMENT` rows (`amount` signed, `ref` = the market the row settles): a win
  credits `qty × 1¢`, a push credits the collateral back, a fair-market-value void credits `price × qty × 1¢`; a **loss moves no money** (the cost left the wallet at the fill), so it has
  no row: a loss is "the position is gone and no settlement row paid it". `GET /v3/portfolio/positions` lists what's still held (`qty`, `cost`). **Not yet seen on the real API**: the
  shape above is the documented one; Vigilant cross-checks every ledger grade against the score feeds and leaves a disagreement to a tap.

## 16. Locks and Novig-only pricing (2026-10-02 ~19:00Z; RESEARCH.md §67; Tj: "arbitrage bet my own bets in novig based on timing")
- **A lock** buys the other outcome of the same market so both outcomes are held equally: every contract pays $0.01 whichever wins, and an FMV void's
  prices sum to 1, so equal holdings pay the same either way (§7, §14.2). Vigilant places it as ONE `FOK` order (§14.3: "an unfilled FOK ... is a
  `reject`") at a limit worked out as the worst case (`LockIn.plan`, fee included when live), only after `GET /v3/portfolio/positions?market=` matches
  the Tracker's contracts exactly on both outcomes. Not yet seen live: the FOK path and positions' shape are the documented ones (§15); the first real
  lock is the test. Bets placed in the Novig app (cash wallet) can't be locked: the API can't see them (§14.2).
- **Novig's own price for a side** (the Tracker's "Novig only" filter, `NovigNow`; changed 2026-10-02 ~21:35Z, Tj: "compare only the novig current odds
  to the novig odds I placed the bets at … no data from any other sports book"): its **odds on Novig now**, the offer (`1 − best bid on the other
  outcome`, §7), what Novig shows and what buying it costs; none when nothing is offered. Not the bid/offer middle: on a thin prop (+122 offered, −223
  the other side) the middle was +163 and read as −15.6% EV with no move at all. The same American odds as bet = 0% EV (`NovigNow.asBet`: +122 logged
  from American odds is 0.4505, Novig's grid +122 is 0.450). In the filter the fair when bet is the price paid (EV at bet 0) and no other book's line,
  fair, close or second opinion is used. Read from the same book reads the scanner already makes (public or keyed, with the websocket and ETag
  cache), never another book's API. The Lock card's "worth at Novig's middle price" is separate and unchanged.
- **Tracked bets without Novig's ids** (2026-10-02 20:06Z, Tj: "many open bets are not finding the current novig odds"): a CNO/ParlayAPI ✓, an alert ✓
  or an imported mark keeps the bet's words, not always its market/outcome ids, and a bet with no market id can't be read. `NovigIds` looks each one up
  again in Novig's public catalog (`/v3/public/catalog/events?league=…` then `/markets?event=…`, cached 5 min, 350 ms apart: `NovigBetFinder.locate`;
  the outcome id from CNO's Novig link names its market directly) before every Novig-only read and Check odds now, 10 min between misses. What's
  truly not offered says why on the bet (`TrackedBet.novigWhy`): game not listed, exact line not offered, market no longer listed (§ "settled markets
  leave the catalog"), nothing bid or offered, or Novig didn't answer.
- **A locked market never loses both legs** (2026-10-05, RESEARCH.md §87): one of two held outcomes wins, so "no SETTLEMENT row and no position" is never a loss there; Tj's Ollie Gordon lock (Over 29.5, 100 yards) had no row found by the ledger query and was wrongly graded lost 6 h after the start. Why the row was missing is not known.
- **Locked markets in the Tracker** (`LockedBets`): API bets holding both outcomes with equal contracts (from the bets' own fills, settled ones too) are
  "locked": hidden from the Tracker's lists and stats while "Hide locked bets" is on (default), counted on their own card (bets locked and their
  share, profit locked = contracts × $0.01 − everything spent on both sides, its % of that).


## 17. Make (post) orders: resting bids, expiry, cancel, fills (2026-10-03 ~02:00Z, docs re-read: Orders, Order lifecycle, Private stream, Book, Lifecycle, Fees, Maker Credit Program, Money; Tj: "figure out how to do make orders through the novig API … how long the make orders should be placed before they expire, and how to set this option in the novig API")

- **Place a bid that only rests:** `POST /v3/orders {outcomeId, price, qty, tif: "PO", ttl, clientId}` (`trading` key, `place` bucket 256 burst / 8 a second).
  `PO` = post only: "Rejected instead of taking": a price that would match a resting bid on the other outcome (at or above this outcome's offer,
  `1 − best bid on the other side`, §7) is refused whole and nothing fills (it ends `REJECTED`; the refusal is a `reject` event, never an HTTP status).
  **`ttl` (milliseconds) is "optional for `PO`"**: the order cancels itself when it runs out (a `cancel` with no `reason`; the `open` event carries
  `expiresAt`, a listed order `expiresTs`). `GTT` + `ttl` also expires but can take; `GTC` rests until filled, cancelled or voided. So a maker bid is
  **`PO` with a `ttl`**: it can never take by accident and it can't outlive the app watching it.
- **What `201 {orderId, clientId}` means:** queued. `open` (private stream) = resting; `fill {price, qty, remaining}` (the traded price, can be better
  than the limit; `remaining 0` = FILLED); a partly filled order stays `OPEN` ("track `remaining`"). `cancel` reasons: none (you, expiry, an admin),
  `GO_LIVE`, `MARKET_CLOSED`, `SETTLED`, `NEUTRALIZED` (a cash-out).
- **`GOLIVE` voids every resting order** (lifecycle channel; it can repeat). A pregame bid can't fill in play.
- **No amend.** "Cancel the order. Then place a replacement. The replacement joins the back of its price level. A fill can land between the cancel and
  the place. Size the replacement from the `cancel` event's `remaining`." Re-pricing a bid always loses its place in the queue.
- **The queue is price, then time:** the book "lists the best price first. Within a price, it lists the earlier order first. The array order is the
  queue." The public book (§5) lists every resting order (`order`, `price`, `qty`), so what sits ahead of a bid is readable before posting it.
- **Read them back:** `GET /v3/orders?status=OPEN` (`market` / `event` / `outcome` filters; `read` bucket, 1 token; `trading::read` works),
  `GET /v3/orders/{id}` (can 404 just after the 201), `GET /v3/account/orders` (the resting snapshot with the stream's `seq`, ETag; `trading` key,
  `account` bucket), fills from `GET /v3/portfolio/fills?order=` (`taker: false` = a maker fill; `history` bucket, 8 + 1 per 50 rows), or live on
  the private `orders` channel (1 token).
- **Cancel:** `DELETE /v3/orders/{id}`; `DELETE /v3/orders?event=|market=|outcome=` cancels every resting order in that scope (`{canceled: n}`);
  `DELETE /v3/orders/batch` (207 lists the ones already `FILLED` / `NOT_FOUND`). `cancel` bucket 256 burst / 16 a second. A `200` is queued: the
  `cancel` event confirms. No route cancels by `clientId`.
- **Fees:** the maker never pays. Pregame game markets charge the taker nothing either (`WHEN_LIVE`, §8). The Maker Credit (50% of the taker's fee)
  is paid only on fills **in play**, so pregame bids earn none. **Re-checked 2026-10-03 ~18:15Z (Tj: "Reconsider whether novig pays maker credit
  pregame"):** the Maker Credit Program terms §2 ("Trading fees assessed at any other time — including before the event begins — are not Live Trading
  fees and do not generate Maker Credits"), the Trading Fees page ("A fill matched at any other time is not charged, on either side, and generates no
  Maker Credit"), and the live catalog (every NFL/NCAAF/MLB game market read: moneylines, spreads, totals, props, 1st halves, team totals carry
  `{coefficient 0.03, makerCredit 0.5, charged WHEN_LIVE}`; `makerCredit` is a share of a taker fee that is zero pregame). And `GOLIVE` voids every
  resting order, so a pregame bid can't become a live fill (the fees page's example of "a resting order placed hours before kickoff that fills in the
  second quarter" contradicts the void and can't happen). Eligible: every member except Exchange affiliates and members with a Market Maker
  Agreement; nothing in the terms excludes API or "bot" orders (a third-party report's claim, RESEARCH.md §73). MLB futures (awards) also carry
  `{0.06, 0.7, ALWAYS}` in the catalog, but the Program's Notice designates only NFL and NCAAF futures; Vigilant trades no futures (BRIEF.md). **NFL and NCAAF futures:** taker fee 0.06·P(1−P) on every fill and a **70% maker credit
  on every fill** (0.042·P(1−P) a contract: ~2% of the cost at even money), paid within 7 days. Members with a Market Maker Agreement are excluded.
- **Money:** a bid the wallet can't cover is refused (`422 INSUFFICIENT_BALANCE`). **Verified 2026-10-03 (Tj's v0.53.0 Diagnostics, RESEARCH.md §70.9): a
  resting bid's cost is NOT held from the balance** ("wallet $8.32 → $8.32 with $12.54 resting": bids worth more than the wallet rested and the balance
  didn't move), whatever marketing or third-party write-ups say about escrow (§73). Vigilant counts every bid not yet ended against the wallet itself
  (the sum of resting bids ≤ the wallet). Every order is a buy, so a bid on each outcome of one market is two buys: both
  filling holds both sides (a lock, §16). A bid of yours on one outcome and one on the other at prices summing to 1 or more would self-match (a wash).
- **Not yet seen live:** the `PO` reject, `ttl` expiry and maker fills on Tj's subaccount. The first real bid is the test (QA, §1, can try it first).
- **How Vigilant uses it (v0.51.0, the Bids tab; RESEARCH.md §70.6):** `data/novig/trading/maker/` (`MakerQuote`, `MakerPlan`, `MakerDesk`, `MakerStore`) and
  `app/MakerRunner`: every bid `PO` with `ttl` = min(30 min, until the start); each pass reads `GET /v3/orders?status=OPEN`, an order that left the list is read with
  `GET /v3/orders/{id}` (a 404 or `PENDING` just after placing is asked about again, not called ended) and its fills with `GET /v3/portfolio/fills?order=`; moves are
  `DELETE /v3/orders/{id}` then a new `POST` (no amend), the order read once more after the cancel so a fill in between is recorded and that side isn't re-posted; Cancel all
  is `DELETE /v3/orders`. Not yet seen live (the first real bid is the test).
- **v0.52.0:** a cancel's `200` is only "queued": the bid is CANCELING until `GET /v3/orders/{id}` says the order ended (up to 4 looks 400 ms apart, then
  each pass), and only then is its side bid again; `DELETE /v3/orders` (cancel all) is followed by the same confirmation. Fills are read with
  `GET /v3/portfolio/fills?order=` whenever an order leaves the open list, whatever its record says (a 404 included). A lost answer's order is looked for
  in `GET /v3/orders?status=FILLED|CANCELED|REJECTED|PENDING&outcome=` by its `clientId` before it's called lost. The `ttl` sent is the bid's whole life:
  never past the start minus the stop window or the fair's freshness (a minute at least).
- **v0.53.0:** passes run on a scan still in progress (every 20 s), so bids go up as each league's fair odds arrive, not at the scan's end. Each pass that
  posts logs the wallet just before and just after (Diagnostics' timeline, "wallet $A → $B with $C resting"): the first real evidence of whether Novig
  holds a resting bid's cost from the balance (still assumed held). After a `451 ANONYMIZED_NETWORK` (or a restricted region) the key route is tried
  again after 2 minutes, not 10 (the verdict on a carrier's address flaps; `NovigPublicClient.keyRetryAfter`), and after 30 s when the request never
  reached Novig. The public routes' pacer remembers the pace a `429` came at for 10 minutes and climbs back only to a step under it (`RateGate`; the
  v0.52.0 file had a 429 about once a minute, 1,163 in all, each time the minute's slow-down ended).
- **VERIFIED on Tj's subaccount (v0.53.0 Diagnostics, 2026-10-03 02:35-02:37 EDT, 289 real `PO` bids):**
  - **Novig does NOT hold a resting bid's cost from the balance**, and takes bids past it: "wallet $8.32 → $8.32 with $12.54 resting" (no `422`).
    Only each order's own cost is checked against the balance at placing. So the app counts the bids up against the wallet itself (v0.54.0:
    budget = min(wallet, day's limit left) − every bid not yet ended).
  - **`GET /v3/orders/{id}` answers `404 ORDER_NOT_FOUND` once an order is off the book** (cancelled or expired), not only just after the `201`:
    ~200 such 404s in the timeline, each right after a cancel or an expiry. Its record can't say how an order ended; its fills can.
  - **`GET /v3/portfolio/fills` costs the `history` bucket (512, refilled 4 a second) 8 + 1 per 50 rows**, and one read per ended order ran it dry
    (429s, `Retry-After` 1-5 s). It takes `startsAfter` (Unix ms, exclusive, on the event's scheduled start; also `startsBefore`, `event`, `market`,
    `outcome`, `order`), so **one read covers every bid at once** (v0.54.0: `NovigTradingClient.fillsStartingAfter`, the earliest bid's start less a day).
  - The cancel's `200` carries the order's status when the cancel arrived (`OPEN` = it was resting; `FILLED` = nothing left to cancel).
  - `ttl` expiry and `PO` posting work as documented (46 bids ended by their `ttl`, 0 refused). No maker fill yet (0 of 289): see RESEARCH.md §70.9.
- **v0.54.0:** each pass reads the open orders once; a bid seen there before and missing now is off the book, finished with one fills read for all such bids
  (its record is read only if it never showed open: a `PO` refusal says `REJECTED` there); cancels are confirmed by one more read of the open orders
  400 ms later and one fills read; a fills read that fails finishes nothing (the bids stay on their way down, no new bid on their sides, read again
  next pass). An expiring bid is re-posted only when the new one would rest a minute longer (a fresher fair). A line's best bid leaves out Vigilant's
  own bids that were in the book read (the book's levels less our contracts), and bids that would lead their side go up first.
- **v0.58.3 (Tj, 2026-10-04: "Vigilant wallet $8.98 · 7 bids up ($16.14)"):** because Novig holds nothing for a resting bid and checks only an order's own cost when it's placed, bids posted
  under the wallet became bids over it as bets, auto-bets and fills took money out (his v0.58.2 file: "wallet $11.55 → $11.55 with $18.71 resting"). The app now takes the least valuable bids down whenever
  the bids up (every one not yet ended) are worth more than the wallet or the day's limit (RESEARCH.md §78). **Not known:** what Novig does when a resting bid fills with the balance short of its cost
  (never seen; the app's rule exists so it never happens).


## 18. In play: what the docs and the live books say (2026-10-05; docs.novig.com read in full through `llms.txt` (130 `.md` pages); RESEARCH.md §83)
- **Statuses**: `OPEN_PREGAME` ✓ tradable; `OPEN_INGAME` ✓ tradable, **taker fee charged** (`WHEN_LIVE` markets, every game market); `DELAYED` ✓ tradable (a pause before or during the game; **its book is cleared when the event reopens**); `FINAL`, `CANCELED` terminal. `GOLIVE` voids every resting order (`cancel.reason: GO_LIVE`; the book channel sends a `remove` with `reason: cancel` for each, then `GOLIVE`); the live window can open more than once; the fee is decided at match time (the live side of a `GOLIVE`).
- **Batch**: `POST /v3/orders/batch` (`trading` key, `place` throttle, 1 token an order, **up to 256 orders**, `201` = every order accepted and queued; `400` = nothing placed with `rejected: [{index, outcomeId, reason}]`; `413`/HTML `403` when the raw body is too big, split it). "All or nothing. A resend places the batch again": not idempotent. **The acceptance is all or nothing; "the exchange judges each order on its own": fills are independent**, so a two-leg batch can fill one leg only. **Vigilant's use (v0.66.0, 2026-10-05)**: the bid desk (`MakerDesk`) posts 2+ bids in one batch and takes them down with one `DELETE /v3/orders/batch` (`NovigTradingClient(batchOrders = true)` in the app; the tests' fakes default to singles). A `400` leaves nothing placed, so the batch's records are removed and singles are sent; a lost answer is resolved by client order id before anything is re-sent; an unreadable `201` turns batches off for the run. **Verified 2026-10-07 (Tj's v0.71.2 diagnostics, four batches of 4, 10, 15 and 20 bids): the `201` reply is a BARE ARRAY `[{clientId, orderId} x N]`, not the documented `{accepted: [...]}`; read since v0.72.0 (before it, the first batch of every run was "unreadable" and bids went one at a time).** Cancel is partial and idempotent: `notCanceled[{orderId, reason FILLED|CANCELED|NOT_FOUND}]` names the ones already gone (a `FILLED` one is a fill, not a failure).
- **Refusals not seen before** (`api__errors.md`): `NOT_LIVE_TRADABLE`, `EVENT_NOT_TRADABLE` (400), `MARKET_CLOSED`, `MARKET_INACTIVE` (409), `EVENT_LOCKED`, `MARKET_LOCKED` (423), `PRICE_BAND_VIOLATION`, `ORDER_TOO_SMALL`, `ORDER_TOO_LARGE` (400), `BATCH_TOO_LARGE`, `BATCH_REJECTED`, `EMPTY_BATCH` (400). **No page says which markets are `NOT_LIVE_TRADABLE`**; no in-play order has been sent by Vigilant, so what the API does with a live prop is unverified (QA first).
- **Streams** (`api__streaming__*.md`): `book` ("every order-book change", 16 tokens a market, includes `lifecycle`; snapshot `{seq, orders: {outcomeId: [{order, price, qty}]}}`, delta `{kind: add|remove, order, outcome, price, qty, reason: fill|cancel}`; a partial fill is a `remove` then an `add` of the rest at the same queue position); `trades` ("every execution", 4 tokens a market; `{outcome, price, qty, ts}` with the engine timestamp in ms, no order ids); `bbo` is 8 tokens a (channel, subject) pair, still no page for its frames; **a wash (self-match) sends `fill` on the private stream but nothing on `trades`, and the position does not change**.
- **Fees page**: taker `0.03·P·(1−P)` a contract in play (10,000 @ 0.50 = $0.75; 100 @ 0.50 = $0.0075), `P` the fill's price; the maker of that fill is credited 50% ($0.375 on the first); the private stream's `fill` carries no fee (read `GET /v3/portfolio/fills`).
- **Maker Credit Program terms**: "You may not enter into wash trades, prearranged trades, self-matching orders, or any other trade or course of conduct designed to generate Maker Credits without bona fide market risk"; members with a Market Maker Agreement (Chapter 4 of the Ludlow Rulebook) are excluded; credits are cash within 7 days. **LP onboarding**: contact developers@novig.com, a W-9, QA access in 2 business days, **a generally required $30,000 minimum deposit**, then production on the standard fee schedule; "no market-making contract to negotiate".
- **Measured live (2026-10-05 ~02:38-02:50Z, public routes, this container)**: live books are not cached (`x-cache: Miss from cloudfront`), 158 ms median to read; `/v3/public/catalog/markets/{id}/trades` serves up to 1,000 rows a page with `ts` in ms (the public route's `outcomeId`/`price` are the RESTING order's, the taker bought the other outcome at `1 − price`); a market's `strike` is the line (`"75.5"`, `"-12.5"`), a TOTAL's outcomes are `Over x`/`Under x`, a SPREAD's `CAR -12.5`/`DET +12.5`, a MONEY's the two team abbreviations. 73 two-sided live game-line books: none crossed, spread median 4.0¢ (thin alternate lines included), narrowest 0.5¢.

- **Tape facts (2026-10-05, one NFL game's ladder, RESEARCH.md §84)**: the public `/v3/public/catalog/markets/{id}/trades` rows carry the engine's millisecond `ts`; `limit` runs 1-5000 (500 by default); a taker order that sweeps several resting orders prints one row per resting order, at the same millisecond; a taker program placing one 10,000-contract order at a time prints about 8 rows a second (the default `place` refill). An NFL game offers ~56 game-line markets with trades (27 on the margin ladder, 29 on the totals ladder). **ESPN's `summary?event=<id>` carries a wall-clock stamp (to the second) for every play and a `winprobability` series per play id**: the market reprices 5-13 s after the stamp.

## 19. The burst recorder's use of the API (2026-10-06; RESEARCH.md §95)
- It opens its OWN websocket on the READ key (`trading::read`), `book` on every `MONEY`, `SPREAD` and `TOTAL` market of the live (`OPEN_INGAME`, `DELAYED`) games of the picked leagues: one NFL game has **82** such markets (checked 2026-10-06), 16 tokens each against the 512-token `stream` bucket (one bulk subscribe passes when the bucket is full, §6). Whether Novig allows a second concurrent connection on one key is NOT documented: the recorder says why if it refuses (`StreamState.Failed`, shown in its status line).
- Delays it measures: **signed round trip** = `POST /v3/echo` (0 tokens, signed like an order) every 20 s; **push delay** = the phone's clock at a fill's removal from the book against the same trade's engine `ts` on the public `/v3/public/catalog/markets/{id}/trades` (matched on market, outcome, price and size; includes any difference between the phone's clock and Novig's).
- Real names (2026-10-06, ATL @ NO): spread outcomes `NO +27.5` / `ATL -27.5` (the market's `strike` is the home side's handicap), totals `Over 70.5` / `Under 70.5`, fee `{coefficient 0.03, makerCredit 0.5, charged WHEN_LIVE}` on every game market.


## 20. The burst trader's use of the API (2026-10-06; RESEARCH.md §95; Tj: "make it good enough so that if it is proven I can just turn it on for actual money betting") — NOT YET VERIFIED AGAINST NOVIG
- Nothing here has met the real API: the container has no Novig key. Everything below is built from §12-§17 and the docs and is tested against a fake order port only. **The first real order is the test**; update this section from that first Diagnostics / share file.
- It sends ONE `POST /v3/orders/batch` per cover with two `IOC` orders (YES at the lower line, NOT at the higher line), at the asks it saw (`price` = 1 - the best bid on the other outcome, thousandths), `qty` contracts of 1 cent each, each with its own UUID `clientId`. The batch is all-or-nothing (§17): a `NovigApiException` = none placed; any other failure = a lost answer, and the trader HALTS (nothing assumed).
- The two fills are independent (RESEARCH.md §83.2): one leg can fill and the other not. After both orders end (`GET /v3/orders/{id}`, terminal states, up to 2.5 s) it reads `GET /v3/fills?order=` for the real `qty`, `cost` and `fee` (dollars). A leg alone is bought out once with an `IOC` at the break-even price (the dearest at which the cover still pays nothing after both fees); failing that it is a held bet, counted and limited (2 in a row, or Tj's loss limit, halt it).
- Refusals it handles: **451 `ANONYMIZED_NETWORK` / 423** (the account or network judged: stand down 10 min), `NOT_LIVE_TRADABLE`, `EVENT_NOT_TRADABLE`, `MARKET_CLOSED`, `PRICE_BAND_VIOLATION`, `ORDER_TOO_SMALL` and the like (the two markets are left alone for 10 min). **Unknown until a real order:** the in-play delay Novig applies to an order (`DELAYED` events), whether `IOC` orders are allowed in play on every game line, the price band, and whether a carrier address gets 451s.
- It never rests or cancels an order (a test greps the source for `PO`, `GTT`, `GTC`, `FOK`, `cancel`), keeps clear of Vigilant's own resting bids (a buy that could trade with one is a wash and pays two fees), and shares the app's one-order-at-a-time lock with the auto-bet and the bid desk.
- Its legs are not Tracker bets. Their record is `files/burst-trades/burst-trades-<ET day>.jsonl` (one `TradeRecord` a line) and the "REAL-MONEY TRADES" section of the share file.

## 21. Docs re-read for the Pinnodds live feature (2026-10-08 ~01:00Z; Tj: "review the novig API docs for web socket use and proper usage and see if anything has changed or updated")
`docs.novig.com/llms.txt` now lists **138** `.md` pages (130 on 2026-10-05). The **API Changelog page is empty** ("This changelog is specific to our API users and EMM's"), so changes are found by reading, not by a log. Re-read: Connection, Lifecycle, Orders (Execution), Errors, Throttling, Private, Book, Tape. What is **new or not yet recorded here**:
- **Trading over the websocket** (`api/streaming/connection`): the same `/v3/ws` connection takes three more verbs, `place`, `cancel`, `cancel_all`, with a `trading` key (a `trading::read` key gets `SCOPE_INSUFFICIENT`). Payloads match the REST bodies:
  `{"nonce":17,"place":{"orders":[{"outcomeId":"…","price":"0.54","qty":10,"tif":"GTC"}]}}` (a batch, all or none, **1 `place` token an order**; ack `placed`, one entry per accepted order),
  `{"nonce":18,"cancel":{"orderIds":["…"]}}` (partial; ack `canceled` naming the ids that did and did not), `{"nonce":19,"cancel_all":{"market":"…"}}` (1 `cancel` token; filter by `market`, `event` or `outcome`; empty = all; ack `canceled_all`).
  They debit the `place`/`cancel` throttles, **not** `stream`. "An ack is our intent, not the fill": fills and final states arrive on the `orders` channel, so subscribe `private`. **No per-request signature**: the upgrade authenticated the connection.
  **Not used yet by Vigilant** (it places through REST `POST /v3/orders`, 120 ms signed round trip measured): a place over the already-open socket saves the HTTP request and its signing; **unverified on a real order**, so a follow-up (TASKS.md PW9), not v0.76.0.
- **Compression**: `X-Novig-WS-Compress: deflate` on the upgrade makes every message (snapshots, deltas, acks) arrive as one **binary** frame of raw DEFLATE (RFC 1951); inflate each frame alone, then parse. Not used (CPU for no gain on a phone link that is not the bottleneck; Pinnodds' own measurement is that compression adds a latency tail).
- **Events subscriptions**: "A market that opens under an `events` subscription arrives with no snapshot. That's not a gap. Its first delta is `OPEN`, and its `book`, `bbo` and `trades` channels start at seq 0, so each one's first delta carries seq 1." (Vigilant subscribes markets, not events, so it never meets this.)
- **Unchanged and re-confirmed**: ping every 15 s (no pong between two pings = dropped, usually `1006`); `1008 SLOW_CONSUMER` (a write stalled for 15 s) and `1008` + a 451 code (geolocation) close the socket; `seq` per market per channel, per subaccount for `orders`/`positions`; a gap is repaired with `snapshot`; costs `lifecycle` 1, `trades` 4, `bbo` 8, `book` 16, `orders` 1, `positions` 1, capped at the 512-token bucket; upgrade 32; `tif` values `GTC GTT IOC FOK PO`; `clientId` is a label, "a replayed place always places again"; a `201` is "accepted and queued", "a `reject` event can still follow, with no HTTP status".
- **Still undocumented**: any **in-play order delay** (the lifecycle page has none; only `GOLIVE`/`UNLIVE` voiding resting orders), the `bbo` frame shape (no page), and which markets are `NOT_LIVE_TRADABLE`. The first real in-play order is the test (RESEARCH.md §116).

## 22. Live catalog read, 2026-10-10 ~03:15-03:30Z (public routes, 462 live books; RESEARCH.md §123)
- **`fee.coefficient` is 0.06 on every NCAAF game market** (MONEY, SPREAD, TOTAL, TEAM_TOTAL, the 1H markets; 390 read, `makerCredit 0.5`, `charged WHEN_LIVE`) and 0.03 on WNBA (72 read). §8's "game markets 0.03" and the simulations of RESEARCH.md §116-§122 assumed 0.03. Read `fee` per market (the app does).
- Live books are thin off the main lines: moneylines are tight (0.7 points), only 47% of SPREAD and 26% of TOTAL markets are two-sided within 6 points, 84% of TEAM_TOTAL sides have no price; two-sided spreads average 30 points on totals and 19 on spreads.
- A resting order stays on the book a median of tens of seconds (80% still there after 5-8 s, 36% after 60 s; top of book 69% / 24%); the public book lists each order by id, so a quote's age is readable.
- The app's "order time" for live IOC orders (median 5,330 ms, slowest 5,716) is send-to-terminal with 80/400 ms polling: about 4.9 s is Novig's, against 94 ms pregame. Undocumented in-play delay; whether `PO` and cancel share it is unmeasured (TASKS.md SV6).
