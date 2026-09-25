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

**Status (2026-09-25):** Tj has beta access. As of this writing he has **not yet**
created any key. Nothing in `app/`/`data/` uses v3 yet (see §9).

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
| `/v3/public/catalog/markets/{id}` | none | one market |
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
- **Efficiency:** subscribing to a whole NFL Sunday slate's events on `bbo`
  costs ~16 events × 8 = 128 tokens once. After that, every price change on
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
  `charged: ALWAYS`. **These charge pregame too.** `Fees.kt` currently doesn't model this.
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

## 12. Still unknown / unverified

1. The exact per-IP rate limit on public routes. Only the "throttled at edge" wording
   and CloudFront `max-age` (10s markets, 5s book) are known. Poll no faster
   than the cache anyway.
2. Whether Tj's beta access is prod-only or also covers QA, and whether his
   profile shows the **Novig API** screen yet. He has to look.
3. No signed route has been exercised yet (no key exists). The echo/signing path is
   documented but still unverified end to end for this project.
4. Whether Android's built-in providers sign Ed25519 on every device Vigilant
   targets (minSdk 30). Sidestep it: use **P-256** in Android Keystore for the
   app's own key (§3).
