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
| `/v3/public/catalog/markets/{id}/trades` | `limit, after` | `{items:[{tradeId, outcomeId, price, qty, ts}]}`, newest first |

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
Data runs from 2026-08-03 through the latest day checked (2026-09-23). The data is anonymized. Observed: in
`trades.csv`, `cost`/`qty` look like **dollars** (e.g. `qty 33.53`), not
contract counts. Confirm before relying on it. Read the header row; Novig says
columns may be added.

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
servers. Treat an HTML 403 as "slow down", not "bad key". `423` means locked,
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
