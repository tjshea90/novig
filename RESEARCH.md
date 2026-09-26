# RESEARCH — positive-EV betting on Novig

Living research file. This is where findings about the app's actual purpose
(finding +EV opportunities on Novig, at $0–$30/mo instead of OddsJam's
$199.99/mo Gold plan) get recorded permanently, the way `BRIEF.md` records
platform decisions. Append to this file as research continues — don't
overwrite prior findings, since a later session needs to see what an earlier
one already ruled out and why.

**Status as of 2026-09-20:** first research pass, done in response to Tj's
request (see `TASKS.md` / `INBOX.md`, 2026-09-20T04:36:03Z), plus a second
pass (2026-09-20T05:01:28Z) deep-diving Odds Assist Pro specifically (§8).
Nothing in this file has been built yet. Several load-bearing facts below
(marked **UNVERIFIED**) come from third-party docs/blog summaries, not
hands-on testing against Novig's real API — confirm them before
architecture is locked in. §8's Odds Assist Pro findings, by contrast,
**are** hands-on verified (live browser session against the real site,
2026-09-20) rather than secondhand — noted there explicitly.

---

## 1. Bottom line

**Updated 2026-09-25 — supersedes §4.4's proxy/GraphQL path and the §4.1
"official API" guesses below. Full detail: [`NOVIG_API.md`](NOVIG_API.md).**
Tj got Novig's official API beta. Reading the real v3 docs (not doc
summaries) and hitting the API live from this container showed two things.
First, Novig has **public, unauthenticated, officially documented** REST routes
(`https://api.novig.com/v3/public/catalog/...`). They return the full catalog,
live order books, and recent trades for **$0, with no key, no proxy, and no
ToS gray area**. Verified returning real production NFL data on 2026-09-25.
Second, the signed routes add a real-time websocket (`bbo`/`book`/`trades`
pushes), plus orders and balances. They need an Ed25519/P-256 keypair that
Tj registers from his Novig profile, and they **refuse VPNs and proxies**
(HTTP 451). So the Novig leg is solved officially. The remaining cost and
freshness bottleneck is the **reference (sharp-line) leg**. The Odds API
free tier is 500 credits a month (§4.3). Everything below this paragraph
predates the v3 docs and is kept for context.

**Updated 2026-09-22 (§4.4) — supersedes the "no $0/mo path exists" framing
directly below.** Tj supplied a working, third-party, MIT-licensed Python
package (`novig-liquidity`) that a briefing document built around it, and
this session verified directly against the package's actual source code
(not just the briefing's summary): it reverse-engineers **unauthenticated
read access to Novig's own internal GraphQL backend**
(`gql.novig.us/v1/graphql`). No account, no API key, no OAuth — genuinely
$0, right now, for the Novig leg specifically. The real cost isn't Novig
data itself, it's that reliable access requires a **paid rotating-proxy
subscription** to avoid IP-based rate-limiting/anti-bot blocking, and this
sits in a real ToS gray area (§4.4/§9) now that Novig is a CFTC-regulated
exchange. This is now the app's actual Novig-leg data source
(`NovigGraphQlClient`), opt-in only (empty proxy list → sample data, same
pattern as every other provider) — see §4.4 for the full verified findings
and §9 for the honest risk posture. Everything below this point in §1
predates that discovery and is kept for context, not because it's still the
live plan.

**Updated 2026-09-20, second research pass — this is less optimistic than
the first pass below and should be read first.** Novig does publish an
official developer API (REST/WebSocket/GraphQL, §4.1) — but §4.1.1's
research strongly suggests it's built and gated for **institutional market
makers, liquidity providers, and B2B partners**, not individual retail
developers, and is plausibly how Novig actually gets paid (institutions
fund the commission-free retail product). There's no public signup form,
no published pricing, and Novig's own Developer Relations job posting
describes "high-touch," relationship-based onboarding, not self-serve.
Going straight to Novig's API may still be the cheapest path *if* Tj asks
and it turns out to be reachable — but plan for the realistic case that it
isn't, rather than treating it as the default. **Correction, 2026-09-20
(confirmed live, see §4.2.2): SharpAPI's free tier does NOT include
Novig** — that was a misreading of their marketing copy, not a verified
fact, and it's since been proven wrong by a real HTTP 403 against Tj's
actual key. **There is currently no confirmed $0/mo path to real Novig
data from any researched provider** — SharpAPI's own product page states
Novig needs its Hobby plan ($79/mo) or above; OpticOdds/Betstamp offer
Novig only via a sales-gated "trial," not a standing free tier; MetaBet has
Novig but undisclosed pricing. Novig's own official API (§4.1, still
pending their reply) is the only lead left that could plausibly still be
free. The math/architecture below (§5-§7) is unaffected either way — only
which data source actually supplies the Novig leg changes, and right now
that source is sample data, with the app honest about it (BRIEF.md).

*(Original framing from the first research pass, kept for context — the
core insight, that a direct-from-Novig data source beats paying a
reseller's markup, is still right in principle; §4.1.1 above is what
changed is how *reachable* that direct source actually is):* A
free-or-near-free, better-than-OddsJam +EV tool **for Novig specifically**
looked achievable, for one reason that most "OddsJam alternative" writeups
never consider because they're built for generic multi-book arbing: **Novig
publishes its own official developer API** — REST, WebSocket, and GraphQL,
OAuth 2.0, sub-second live order-book data — built explicitly for
"developers and quantitative traders to automate strategies." That is the
single biggest cost lever available: every third-party odds aggregator
(SharpAPI, OpticOdds, Betstamp, SportsGameOdds, odds-api.io) that resells
Novig data is, per their own pages, pulling from this same surface (or
scraping the public site) and marking it up to $79–$399/mo. Going straight
to Novig's own API instead of through a reseller would be the difference
between "under $30/mo" and "free," **if** API credentials turn out to be
free to obtain — now looking unlikely for an individual, see §4.1.1.

Recommended shape of the app, in one paragraph: pull Novig's own live board
directly from its WebSocket (`wss://api.novig.com/tape`) for the tradable
price — this is the leg that must be real-time, and Novig already pushes it
event-driven, so no polling is needed. Separately, pull a slower-moving
"what's this actually worth" reference (devigged consensus or a sharp book
like Pinnacle) from a cheap or free multi-book odds API, and devig it
ourselves rather than paying a provider's markup for a pre-computed
fair-odds field. Compare the two, net out Novig's own trading fee (only
charged on live/in-game and parlay taker fills — pre-game straight trades
are free on both sides, see §3), and surface anything with a real edge.
Nothing about this shape requires OddsJam-tier spend.

## 2. What "positive EV" means on Novig specifically (this is NOT the same
   question as on a traditional book)

Most +EV explainers (OddsJam included) assume the target book has a
built-in vig, and the "edge" is that one soft book hasn't repriced as fast
as a sharp reference like Pinnacle. **Novig doesn't work that way.** It's a
peer-to-peer exchange: users post prices (Make) or accept posted prices
(Take), Novig takes no cut on the price itself, and there's no bookmaker
margin baked into Novig's own board to strip out
([how it works](https://www.oddsshopper.com/articles/prediction-markets/how-does-novig-work),
[legalsportsreport.com](https://www.legalsportsreport.com/prediction-markets/novig-promo-code/)).

So the source of edge on Novig isn't "beat the house's stale vig" — it's
**"the crowd hasn't converged to true probability yet."** That happens
because Novig is newer and thinner than the retail-book market as a whole,
so a market can sit away from fair value until enough traders (or an arb
bot) correct it. This means the app's actual job is: compute a fair
probability from *outside* Novig (devigged consensus / sharp reference),
compare it to whatever price is currently sitting on Novig's book, and
flag the gap — the same comparison shape as traditional +EV tools, but the
"edge" being measured is convergence lag on a thin exchange, not a stale
vig on a soft book. Worth stating explicitly in the app's own docs/UI so a
future session (or Tj) doesn't quietly drift into building a devig-Novig's-
own-book tool, which would be measuring the wrong thing.

Novig quotes prices as **decimal probabilities**, not American/decimal
odds: a price of `0.524` means $0.524 staked to win $1.00 total
([data model](https://docs.novig.com/api-reference/data-model)). That
actually simplifies the EV math (§5) — no odds-format conversion needed,
Novig's own price *is* already an implied probability; the only conversion
work is on the external reference-odds leg.

## 3. Novig's fee structure (must be netted into EV, or the numbers lie)

Per [Novig's own fee docs, summarized via search](https://support.novig.com/en/articles/16195057-fees-on-novig)
and [oddsassist's explainer](https://oddsassist.com/prediction-markets/novig-fees/)
(**UNVERIFIED against Novig's page directly** — corroborate before shipping
the fee constant into the app):

- **Pre-game straight trades: $0 fee**, for both the Maker and the Taker.
- **Live (in-game) straight trades:** Taker pays `0.03 × P × (1 − P)` per
  $1 of contract, where `P` is the price in dollars (so ~$0.0075 per $1 at
  a 50¢ price — roughly 0.75% of stake at even money, less at extreme
  prices). Maker side is still $0.
- **Parlays:** fees apply; exact structure not yet pulled from source —
  TODO next research pass.
- **Maker Credit Program:** makers whose resting live orders get filled
  earn back 50% of the taker's fee as a credit.

Implication for the app: EV on **pre-game straight bets is fee-free** — the
simplest and highest-priority case to build first. EV on **live/parlay**
bets must subtract the taker fee from raw edge before calling something
+EV, or the app will surface false positives that look profitable but
aren't once the fee is paid.

## 4. Data sources compared

### 4.1 Novig's own official API — the key finding, needs confirmation

Full public docs at **docs.novig.com** (also indexed at
[docs.novig.com/llms.txt](https://docs.novig.com/llms.txt) for a fast
site-map read). Confirmed from the docs:

- **REST**, base URL `https://api.novig.com/nbx/v2` — market data, order
  book, positions, order management.
  ([rate limits page](https://docs.novig.com/api-reference/rest-api))
- **WebSocket**, `wss://api.novig.com/tape` — channels: `tape` (global
  order-book updates across all markets), `lifecycle` (market state:
  OPEN/END/CLOSE/START, EVENT_GOLIVE/EVENT_UNLIVE), `private` (your own
  order fills), and a per-market channel `{marketId}`. Server pings every
  15s; a missed pong drops the connection.
  ([WSS overview](https://docs.novig.com/api-reference/WSS/overview))
- **GraphQL** — ad hoc queries across markets/players/history, with a
  playground.
- **Auth:** OAuth 2.0. Docs state you "request your client ID and secret
  from Novig," then `POST https://api.novig.com/nbx/v1/auth/emm-token` to
  get an access token, which **expires every 30 minutes** (build token
  refresh in from day one, not as an afterthought).
- **Rate limits (generous — nowhere near a constraint for a single
  personal app):** standard endpoints 256 req/s, order placement 256/s,
  cancellation 512/s, event screener 1,024/s, batch/strike 64/s, kill
  switch 1 per 30s. 429 responses carry `Retry-After`/`X-RateLimit-*`
  headers in **milliseconds**, not seconds — easy off-by-1000 bug to watch
  for when implementing backoff.
- **REST endpoints relevant to a read-only scanner** (no trading needed to
  just watch the board): get market, get open markets, get order book, get
  markets by event, get markets by events **batch (up to 50 events at
  once)**, get valid tick levels.
- Separately, **data.novig.com** publishes free daily historical
  trade/market-snapshot data files (volume, open interest, price range) —
  useful for backtesting a devig/EV strategy against Novig's real
  historical convergence behavior, not useful for live scanning since it's
  daily-batch, not streaming.

### 4.1.1 Second research pass, 2026-09-20T19:45:42Z: pricing/access model — not encouraging

Tj asked directly: is the NBX API free, and how do you actually get it?
This pass found real, converging evidence (not proof — Novig has never
published pricing publicly) that the honest answer is **probably not free
for an individual retail trader, and probably not a quick self-serve
signup**:

- **Novig's own Developer Relations Engineer job posting** (the role that
  owns onboarding new API users) describes the API's target users as
  "market makers and liquidity providers, trading firms, and B2B or
  embedded partners" — an institutional/B2B list, retail traders not
  mentioned. The role is described as providing "detailed, high-touch
  support" to partners integrating the API — relationship-managed
  onboarding, not a self-serve dashboard. No pricing, rate limits, or
  formal signup process appears anywhere in the posting.
  ([Dreamwork listing](https://www.dreamworkhq.com/job/aa4d9582-c6f9-4c36-83ab-3e19d76ac7b0))
- **Novig's actual business model explains why:** multiple funding-round
  writeups describe Novig as charging *institutional market makers and
  liquidity providers* for access to retail order flow — that fee is
  explicitly what funds commission-free trading for retail users. API
  access to the live book is the product institutions pay for; giving it
  away free to individual retail traders would work against the platform's
  own monetization model, not just be an oversight in the docs.
- **There is a formal, gated "Market Maker" program**, separate from
  simple account signup: per Novig's CFTC exchange-rule filings, an
  individual, group, or corporation must be **approved** and **sign an
  agreement** with the exchange before being allowed to provide liquidity
  across markets — this is the kind of relationship the API is built for,
  not a form anyone fills out.
- **No public waitlist, application form, or self-serve API-key dashboard**
  was found anywhere via web search — not on novig.com, not in the help
  center (support.novig.us), not in press coverage. Every third-party
  reseller that already has Novig data (SharpAPI, OpticOdds, Betstamp,
  etc.) is, per the business-model finding above, most plausibly itself
  one of these paying institutional/data-partner customers — i.e., they
  already did the relationship-based onboarding Tj would need to do, at a
  scale that supports reselling it.

**Correction, same day (2026-09-20T19:49:13Z) — Tj found the real process,
which this session's web search never surfaced:** Novig's own in-app/site
support widget states plainly: *"API access is handled by our developer
team. Send an email to **developers@novig.com**. Include: who you are (and
your company/product, if applicable), what you're building, what data or
functionality you want, expected scale or usage. They review requests and
guide next steps from there."* So there **is** a real, concrete, always-
available path in — a real email address, a real intake process — it's
just not indexed anywhere web search reaches (likely gated behind the
in-app help widget rather than a public page). This doesn't resolve
whether individual/personal requests actually get approved or what it
costs — that's still unknown until Novig replies — but it fully answers
"what's the actual process": **email developers@novig.com** with those
four things. A draft covering all four was written for Tj this session
(not stored in this repo — it's a one-off communication, not project
documentation) for him to review and send himself.

**Revised recommendation:** once Tj sends it, the next step is simply
waiting for Novig's reply and updating this file with whatever comes back
(free vs. paid, approved vs. not, any conditions) — that's the one thing
that actually resolves this open item for good. Until then, plan around
the same fallback as before: **SharpAPI's free, 60-second-delayed tier**
is the most realistic $0 path to *some* real Novig data, with paid
resellers ($79+/mo) as the next fallback if delayed data isn't good
enough.

### 4.2 Third-party resellers (fallback if 4.1 turns out gated/paid)

All of these sit on top of the same underlying Novig data (either the
official API or scraping the public site) and add a markup for
normalization across many books:

| Provider | Free tier | Cheapest paid tier w/ Novig real-time | Notes |
|---|---|---|---|
| **SharpAPI** | $0/mo, 12 req/min, **DraftKings + FanDuel only** — see §4.2.2, Novig is NOT in the free tier despite earlier marketing-copy misreading | Hobby $79/mo — "Available on Hobby plan and above" per SharpAPI's own Novig product page | Pro $229/mo adds +EV/middles/splits specifically for Novig. Fair-odds/Pinnacle no-vig field is also paid-only. |
| **odds-api.io** | 100 req/hour, no card — but scoped to **2 recreational bookmakers**, Novig not among them; also **new free signups are currently paused indefinitely** (checked 2026-09-20) | Paid tiers | Sub-second via WebSocket on paid; explicitly states it **scrapes** Novig's public site ("not affiliated with NoVig"), not the official API — same legal footing as any scraper, see §7. |
| **SportsGameOdds** | Free tier exists, limits unclear | $99–$499/mo | Object-counted billing (each returned item = 1 object). Not deep-dived for Novig specifically. |
| **OpticOdds** | **No standing free tier for Novig** — its own product page offers Novig only "on a trial basis," sales-gated (Book a Demo / Get Started → contact form), no public pricing | Not disclosed publicly | Checked 2026-09-20. |
| **Betstamp** | **No standing free tier** — "Trial keys available for evaluation" via a demo request, no public pricing | Not disclosed publicly | Checked 2026-09-20. Delivered via API or their "PRO" odds screen — PRO strongly implies paid. |
| **MetaBet** | Confirmed to include Novig (named alongside Kalshi/Polymarket) but **no pricing or free-tier info published** on their product page; their `/pricing` page 404'd | Unknown | Checked 2026-09-20 — would need direct contact to learn cost. |

**Conclusion, corrected 2026-09-20 (superseding the original conclusion
below, which was based on an unverified reading of SharpAPI's free tier):
there is currently no $0/mo path to real Novig odds data from any
researched provider, confirmed or otherwise.** Every reseller that has
Novig either requires a paid plan outright (SharpAPI Hobby $79/mo,
confirmed with an exact HTTP 403 and the vendor's own "Available on Hobby
plan and above" wording) or gates it behind a sales-demo "trial" with no
public pricing (OpticOdds, Betstamp) or undisclosed pricing entirely
(MetaBet). The entire "under $30/mo and real-time" goal for the Novig leg
specifically now depends on §4.1 (Novig's own official API) turning out to
be free for individual/personal use — worth continuing to wait on their
reply, since every paid alternative found clears $30/mo (SharpAPI Hobby
alone is $79/mo). The Odds API/Pinnacle-consensus *reference* leg (§4.3) is
unaffected and remains genuinely free — this only concerns the Novig leg
itself.

*(Original conclusion, kept for the record rather than deleted — this is
exactly the mistake §4.2.2 documents, left visible on purpose:)* ~~No
third-party reseller gets real-time Novig data under $30/mo... the
fallback is SharpAPI's free 60s-delayed tier.~~ — **wrong**: SharpAPI's
free tier never included Novig at all, at any delay.

### 4.2.1 Verified real endpoint shapes (2026-09-20, confirmed via
docs.sharpapi.io directly before writing any client code against them —
not guessed, per the lesson from an earlier session's CI failure)

- **SharpAPI odds endpoint:** `GET https://api.sharpapi.io/api/v1/odds`,
  query param `sportsbook=novig` to scope to Novig, auth via `X-API-Key`
  header (not a bearer token). Response is a flat JSON array of
  per-selection rows (`{data: [...]}`), not pre-grouped into markets —
  each row carries `sportsbook`, `home_team`, `away_team`, `market_type`,
  `selection`, `odds_decimal` (and sometimes `odds_american`/
  `odds_probability`), `is_live`. `SharpApiClient` groups rows by
  (sportsbook, home_team, away_team, market_type, is_live) into 2-outcome
  markets itself, skipping any group that isn't exactly 2 rows (Novig
  markets are always exactly 2-sided — a group of any other size is a
  parsing mismatch, not a market to render). Rate limiting: **HTTP 429**
  with `Retry-After` (seconds) and/or `X-RateLimit-Reset` (epoch seconds)
  headers when present; **401/403** for an invalid or missing key. Free
  tier is 12 req/min, ~40 books including Novig (confirmed — this is the
  reason SharpAPI was picked for the Novig leg specifically, see §4.2).
- **The Odds API:** confirmed the documented `x-requests-remaining`/
  `x-requests-used`/`x-requests-last` response headers are present on
  every successful call (already-established v4 REST format, higher
  confidence than SharpAPI's going in). **401** on an invalid/exhausted
  key, **429** on true rate limiting (distinct from quota exhaustion) —
  `TheOddsApiClient` maps 401→treat the key as exhausted (never
  auto-recovers) and 429→cooldown (auto-recovers after `Retry-After` or a
  60s default), matching `KeyRotator`'s general rate-limited-vs-invalid
  distinction (BRIEF.md's "Locked architecture decisions").
- Whether Pinnacle specifically is included in SharpAPI's **free** raw-odds
  set (as opposed to just its paid fair-odds field) is still the one thing
  from §4.3 below that was **not** resolved by this pass — this research
  only confirmed the odds-endpoint shape and auth/rate-limit behavior
  needed to build `SharpApiClient`, not every book included in the free
  response payload.

### 4.2.2 Correction, 2026-09-20 (same day, later): SharpAPI's free tier
does NOT include Novig — a real mistake in §4.2.1 above, found by Tj
hitting it live

§4.2.1 stated "Free tier is 12 req/min, ~40 books including Novig
(confirmed...)" — that "confirmed" was wrong. It came from SharpAPI's
general marketing copy ("43 sportsbooks with real-time odds aggregation,
including exchanges and prediction markets") without checking whether that
full catalog applies to every *tier*, not just the *platform overall*. It
doesn't. This is exactly the "verify real facts before building against
them" lesson this project has hit before (the EncryptedSharedPreferences
deprecation, the SharpAPI endpoint shape itself) — this time the shape was
right but the tier-gating wasn't checked, and it shipped into
`SharpApiClient`/BRIEF.md's architecture decision anyway.

**What actually happened:** `SharpApiClient`'s real request
(`GET /odds?sportsbook=novig`, real `X-API-Key`) returned **HTTP 403**
against Tj's real free-tier key (2026-09-20, via the app's own diagnostic
error message added in v0.2.1 — see TASKS.md). Confirmed independently,
not just from the app: Tj reproduced the identical 403 directly in
SharpAPI's own Playground with the Sportsbook dropdown set to Novig
specifically (his earlier Playground screenshots had it set to DraftKings,
which is why they looked successful and didn't catch this).

**Root cause, confirmed from SharpAPI's own product page**
(`sharpapi.io/sportsbooks/novig-odds-api`): *"Available on Hobby plan and
above."* The free tier is explicitly scoped to **DraftKings and FanDuel
only** — not the ~40-book catalog the general marketing page describes.
Novig specifically requires the **Hobby plan ($79/mo)** at minimum for raw
odds; a separate mention elsewhere suggests Novig's +EV-detection feature
specifically may need **Pro ($229/mo)** — the two are different features
(raw odds vs. computed opportunities) and could plausibly have different
tier floors; only the Hobby-plan raw-odds requirement was confirmed
against Novig's own SharpAPI product page directly.

**Practical effect:** `SharpApiClient` itself needs no code fix — the
request shape, auth, and parsing were all correct (borne out by getting a
real, well-formed 403 rather than a parse error or a 400). The Novig leg
simply has no working data source right now on Tj's current (free) key,
and correctly falls back to sample data with the per-leg banner saying so
— this is `KeyRotator`/`SampleNovigRepository`'s fallback behavior working
exactly as designed, not a bug surfacing a bug.

See §4.2's corrected table and conclusion for what was researched as
alternatives (OpticOdds, Betstamp, MetaBet, odds-api.io) — none found to
have a genuine standing free tier that includes Novig, as of this check.

### 4.3 Reference-odds leg (fair-probability comparison, not Novig itself)

Doesn't need to be sub-second — line consensus moves far slower than an
individual exchange order book, so a 30–60s-delayed free tier is fine here:

- **The Odds API** — free tier: 500 credits/mo (~16 req/day at 1
  credit/call); paid $30/mo for 20,000 credits. Confirmed to include
  **Pinnacle** plus ~40 books (US: DraftKings/FanDuel/BetMGM/Caesars/
  Bovada/MyBookie; EU: 1xBet/Betfair/Unibet/etc.) — does **not** include
  Novig itself.
  ([the-odds-api.com](https://the-odds-api.com/))
- **SharpAPI free tier** ($0/mo, 12 req/min, 60s delay) — raw (non-devigged)
  odds across ~40 books including Novig. If Pinnacle is in that free
  coverage (**UNVERIFIED — need to check directly**, the search summary
  only confirmed Pinnacle-sourced fair odds are a *paid* SharpAPI feature,
  not whether raw Pinnacle odds are in the free raw-odds set), this alone
  could supply both legs' non-Novig reference data for $0/mo.
- Either option comfortably fits under $30/mo, and the free tiers plausibly
  fit under $0/mo — **do our own devigging** (§5) rather than paying for a
  provider's precomputed fair-odds field, since a provider's is a black box
  and the app should let the edge be inspected/tuned the way OddsJam lets
  users pick a "source of truth."

### 4.4 The actual verified Novig access method, 2026-09-22 — resolves §10 item 1

Tj supplied three attachments: a project briefing PDF, and the actual PyPI
source distribution + wheel for `novig-liquidity` v1.1.20
(`github.com/Hurteau101/Novig_Liquidity_Template`, author "Devon H", MIT
licensed, 18+ releases Oct 2025 → Sep 2026). This session extracted and
read the real package source directly (`novig_base.py`, `novig_api.py`,
`models.py`) rather than trusting the briefing's summary alone — every
claim below is verified against working code, not documentation or a
third-party writeup.

**Endpoint and auth:**

- `POST https://gql.novig.us/v1/graphql` — Novig's own internal backend
  (Hasura GraphQL Engine — query syntax uses `_eq`/`_in`/`_or`/`_and`
  filters, a Hasura convention).
- **Zero authentication of any kind.** The only header sent is
  `Content-Type: application/json` — no API key, no OAuth, no Novig
  account or login, no bearer token. This means using it **cannot get
  Tj's own Novig account banned** the way violating an authenticated
  endpoint's ToS could — no account is ever involved.
- **This directly contradicts the earlier assumption (§4.1/§4.1.1) of an
  official OAuth/developer-portal system at docs.novig.com.** That
  assumption came from a doc-summarizing fetch, a weaker source than
  working code read directly. It's not disproven — Novig's own DevRel job
  posting and Market Maker program (§4.1.1) are independent evidence a
  real credentialed API might still exist for institutional partners — but
  `NovigApiClient`/`NovigLiveFeed`'s field shapes should be treated as
  unconfirmed pending an actual reply to Tj's still-unanswered
  developers@novig.com email, not as the working path. This session's
  `NovigGraphQlClient` (the class now wired into `ScannerViewModel`) is a
  **separate, independently-verified path** — the unauthenticated GraphQL
  backend above, not the doc-summarized OAuth REST API.

**Requires rotating proxies.** `novig_api.py` hard-fails at import time
without a `PROXIES` env var (comma-separated `username:password@host:port`,
HTTP Basic Auth to the proxy). This is a real signal of anti-bot/rate-limit
protection on Novig's side, not an incidental implementation detail — see
§9 for the risk posture this implies. **This is a real, ongoing cost**: a
paid rotating-proxy subscription (residential or datacenter), not free
infrastructure. Tj sources this himself; the app has no opinion on which
provider, and ships with zero proxies configured by default.

**Pregame only, as shipped.** Both GraphQL queries hardcode
`status: "OPEN_PREGAME"`. The live/in-play status value is unknown and
unconfirmed — matches this app's `NovigGraphQlClient`, which is pregame-
only for the same reason (open item, see §10).

**Read-only.** No order-placement endpoint exists anywhere in the package.
Placing trades would require separately reverse-engineering authenticated,
account-linked write access — a materially bigger lift and a materially
bigger step up in both ToS risk and consequence (an account actually is
involved this time) than reading public market data. Out of scope, not
attempted.

**The two verified GraphQL queries** (league query: list of `OPEN_PREGAME`
events for a league; market query: full order book for one event, exact
strings in `NovigGraphQlClient.kt`'s `LEAGUE_QUERY`/`MARKET_QUERY`
constants) and the `price_to_american`/`calculate_liquidity` formulas the
package uses were copied verbatim from the working code, not re-derived.

**What `NovigGraphQlClient` had to solve that the reference package never
needed to:** `novig-liquidity` is a single-source liquidity filter — it
never needed to match a Novig event against a *different* provider's event,
so it never had to solve team-name extraction. This app does (to line up
against the reference-odds leg), and Novig's schema has **no explicit
home/away team fields** — only a free-text event `description` and,
per-market, per-outcome `description` strings. `NovigGraphQlClient` prefers
a moneyline market's two outcome descriptions (almost certainly the literal
team names) and falls back to splitting the event description on a common
matchup separator (` @ `, ` at `, ` vs`/` vs.`) — genuinely best-effort,
unconfirmed against a real live response, and exactly the
"matching/normalization is the hard problem, not the API calls" risk the
briefing called out. `EventMatcher` was also made order-independent
(RESEARCH.md-adjacent code change) since which of the two names Novig's
API returns first isn't a documented convention either.

**Market-type classification is similarly best-effort.** Novig's raw
`market.type` string values aren't documented anywhere; `NovigGraphQlClient`
tries the raw string first (in case it happens to be human-readable) and
falls back to a strike/outcome-label heuristic (over/under keywords → total,
a strike present with no over/under → spread, no strike → moneyline) modeled
on the reference package's own handling of the same ambiguity.

**Outcome pricing:** each outcome exposes both a `last` (most recent trade
price) and an `orders` list (currently OPEN resting orders, sorted by
price). `NovigGraphQlClient` prefers `last` when present — it's the field
Novig's own schema dedicates to "the current price" — falling back to the
highest-price OPEN order for a market with no trade history yet. The
reference package's own `highest_order` picks by *largest notional*, not
best price — that's solving a different problem (liquidity summary, not
"what's a fair current-price quote"), so this app didn't copy it directly;
documented as a genuine best-effort choice, not a confirmed convention.

#### 4.4.1 A free "direct, no proxy" mode, 2026-09-22T05:38:31Z

Tj asked whether a free alternative to paid proxies existed — he has a VPN,
and floated toggling airplane mode for a new carrier IP. Answered honestly,
then acted on the real gap the question exposed:

- **A VPN gives one non-rotating IP, not a pool.** It may work, may not —
  commercial VPN IP ranges are commonly *already* on anti-bot blocklists
  precisely because they're used for exactly this kind of thing. No way to
  know without trying; costs nothing extra if Tj already has one.
- **Airplane-mode IP cycling** only helps if the carrier assigns a fresh
  public IP on reconnect — many carriers use CGNAT, where toggling may not
  change the externally visible IP at all. It's also a manual, one-at-a-
  time action outside the app's control; nothing to automate here.
- **The bigger finding:** the reference package's hard `PROXIES`
  requirement was written for its own continuous, high-frequency polling
  use case. This app only calls Novig on a manual refresh — a much lighter
  request volume that may not need a rotating pool at all. Worth trying
  with zero proxies first, completely free, before assuming any of the
  above is even necessary.

**The real gap:** `NovigGraphQlClient` was built requiring at least one
proxy, mirroring the reference package's own hard requirement — so none of
this (VPN, home network, airplane-mode-cycled IP) was actually testable.
Fixed: the client now accepts zero proxies and connects directly over
whatever network Android is currently routed through — a system-wide VPN,
if active, is picked up automatically with no per-app configuration, since
that's just how the OS's default network route works. A new Settings
toggle ("Try direct access (no proxy)") is the explicit opt-in for this,
separate from the proxy list itself — same "never a silent default"
posture as every other provider. Direct mode has no pool to fall back to,
so a rate-limit or rejection there is a real, immediate failure
(`NovigDirectAccessException`), not something silently retried.

**First real-world data point, 2026-09-22 (Tj's own device, screenshot):**
direct mode's very first attempt got **HTTP 503** from Novig — consistent
with the reference package's own "hard-fails without proxies" finding
(§4.4), though a single data point doesn't prove it's a hard block rather
than a transient origin issue; worth Tj trying again, and trying with his
VPN on, before concluding proxies are strictly required. Real bug found
diagnosing this: 502/503/504 were being lumped into the same bucket as a
401/403 hard rejection, when they more accurately mean "temporarily
unavailable" (very likely a CDN/anti-bot layer in front of
`gql.novig.us`, per §9) — fixed to classify as rate-limited/retryable
instead, and the direct-mode error message now says "temporarily
rejected" for these instead of "rejected." Added real HTTP-layer tests via
MockWebServer (`NovigGraphQlClientTest`'s new "direct mode over real HTTP"
section) — a gap explicitly flagged as untested when §4.4 first shipped,
now closed for the direct-mode path (proxy mode still isn't, since a mock
HTTP proxy would need HTTPS CONNECT tunneling to test for real).

**Second real-world data point, 2026-09-22 (Tj tried a free residential-
proxy trial, screenshot):** progress — the error changed from a 503 to
`ProtocolException: Too many tunnel connections attempted: 21`, meaning a
proxy was actually configured and being used (the error names "Novig
(direct) key(s)," i.e. the proxy pool) rather than direct mode's zero-
proxy path. This is a real, confirmed bug in `NovigGraphQlClient`'s own
`Authenticator`, not a Novig-side or proxy-provider issue: it blindly
re-attached the same `Proxy-Authorization` credentials on every 407
challenge with no check for "already tried this," so once the proxy
rejected the credentials once, OkHttp's own tunnel-building safety limit
(`MAX_TUNNEL_ATTEMPTS = 21`) eventually tripped instead of a clean,
diagnosable auth failure — a well-documented OkHttp gotcha (their own
Authenticator recipe explicitly warns about it). Fixed: the authenticator
now gives up after one rejected attempt instead of retrying forever, so a
genuinely-bad/expired-trial credential now surfaces as a normal HTTP 407
failure (which `KeyRotator` already handles — marks that proxy invalid,
tries the next one) instead of this confusing exception. Two new unit
tests exercise the authenticator directly (attaches credentials once,
gives up on a repeat challenge) without needing a live HTTPS CONNECT
tunnel. **Still open:** whether Tj's specific trial credentials are
actually valid/active is unconfirmed — this fix makes a real credential
problem visible, it doesn't fix a bad credential.

## 5. The math: devigging methods (with actual formulas)

Devigging = stripping a book's margin/overround out of quoted odds to
recover the market's implied "true" probability per outcome, so
probabilities sum to exactly 1 instead of >1
([betherosports.com overview](https://betherosports.com/blog/devigging-methods-explained)).
Using decimal odds `O_i` for outcome `i` of `n` outcomes in a market
(from [applied-probability-institute/no-vig-fair-odds](https://github.com/applied-probability-institute/no-vig-fair-odds),
**UNVERIFIED against a primary academic source — re-derive/sanity-check
before shipping any of these into a bet-sizing decision**):

- **Multiplicative (proportional):** the standard/simplest method, and
  what most books' own margin resembles.
  ```
  P_fair,i = (1/O_i) / Σ_k(1/O_k)
  ```
  Known bias: overstates dogs' true probability / understates favorites',
  because it spreads margin proportionally regardless of where the vig
  actually got added.

- **Additive:** spreads the overround evenly across outcomes instead of
  proportionally.
  ```
  P_fair,i = (1/O_i) − [Σ_k(1/O_k) − 1] / n
  ```
  Simpler, generally considered less accurate in practice than
  multiplicative or power.

- **Power method:** solves for a single exponent `k` such that the
  power-transformed implied probabilities sum to 1, then applies that `k`
  to each side. Corrects multiplicative's favorite/dog bias by assuming
  books skew more margin onto favorites (where public money concentrates).
  ```
  solve k such that: Σ_i (1/O_i)^k = 1
  P_fair,i = (1/O_i)^k
  ```
  Requires a numerical solver (bisection/Newton's method) for `k` — no
  closed form.

- **Shin's method:** models the overround as arising from a fraction `z`
  of "insider"/sharp money, and solves for `z` and the resulting fair
  probabilities simultaneously. Considered the most theoretically grounded
  for correcting favorite-longshot bias, at the cost of being iterative
  (no closed form) and more complex to implement correctly.
  ```
  let π̂_i = 1/O_i  (raw implied probabilities)
  solve for z such that:
    Σ_i [ sqrt(z² + 4(1−z)·π̂_i²/Σπ̂) − z ] / [2(1−z)]  =  1
  P_fair,i = [ sqrt(z² + 4(1−z)·π̂_i²/Σπ̂) − z ] / [2(1−z)]
  ```

Both OddsJam and Sharp Lines let the user pick which method (and which
book as "source of truth," e.g. Pinnacle) computes the reference fair
odds — worth doing the same rather than hard-coding one method, since
different methods disagree meaningfully on favorite-heavy lines and this
is exactly the kind of tunable-not-hardcoded decision a +EV bettor cares
about.

## 6. EV calculation, adapted for Novig's probability-price format

Since a Novig price `P_novig` for outcome `i` already *is* a probability
(§2), and a devigged reference gives a fair probability `P_fair,i` for the
same outcome from step 5:

```
raw_edge   = P_fair,i − P_novig        (positive = Novig is underpriced, i.e. +EV to buy)
EV_per_$1  = P_fair,i × 1 − P_novig    (expected profit per $1 of eventual $1 payout)
ROI        = EV_per_$1 / P_novig       (expected return on capital staked)
net_EV     = EV_per_$1 − fee(context)  (§3 — 0 for pre-game straight, taker-fee formula for live)
```

Flag `net_EV > 0` (with whatever confidence threshold Tj wants — OddsJam
users commonly filter to a minimum ROI% to account for reference-line
noise, not literally flag anything net-positive by a penny).

## 7. Real-time architecture on Android (Moto G 2026, battery-aware)

This ties directly into `BRIEF.md`'s "one cheap-tier phone" constraint and
the "Resource/battery waste" full-test checklist item in `CLAUDE.md` — get
this right from the start rather than retrofitting it:

- **Novig's own leg is naturally event-driven** (§4.1's WebSocket push) —
  this is the *good* case for battery: no polling loop needed for the
  price data that actually needs to be fresh, Novig pushes only on real
  changes.
- **Single persistent WebSocket connection**, not one per market/event —
  multiple simultaneous sockets meaningfully increases battery drain;
  subscribe to the `tape` channel (or targeted `{marketId}` channels for
  whatever's being watched) over one connection.
- **Foreground service with a persistent notification** while actively
  scanning — Android restricts background network access outside a
  foreground service or WorkManager, and a live scanner is squarely a
  "noticeable to the user" ongoing operation, which is also the honest UX
  answer (the user should be able to see the app is live-watching, not
  have it silently draining battery invisibly).
- **Respect Doze mode** — don't fight Android's background restrictions
  when the app is actually backgrounded/screen-off for a while. A
  reasonable default: full live WebSocket scanning only while the app is
  foregrounded or the persistent-notification service is explicitly on;
  fall back to Firebase Cloud Messaging (or just stop entirely) rather
  than trying to hold a wake lock indefinitely — the CLAUDE.md full-test
  protocol will explicitly check for exactly this kind of drain.
- **Exponential backoff with jitter on reconnect** — the WebSocket *will*
  drop (network changes, Doze, server restarts); reconnect logic needs to
  back off rather than hammer Novig's endpoint, both to be a good API
  citizen (relevant if credentials are tied to Tj's real account, §4.1)
  and because tight retry loops are themselves a battery/network drain.
- **The reference-odds leg (§4.3) should poll, not stream** — it doesn't
  need to be real-time, and polling on a longer interval (whatever the
  free/cheap tier's rate limit comfortably allows) is both cheaper on
  quota and lighter on battery than holding a second always-on connection
  open for data that isn't the time-critical leg.
- Recommended library note (not yet decided — belongs in `BRIEF.md`'s
  toolchain section once picked): OkHttp's WebSocket client is the common
  Android-native choice, with built-in listener callbacks and background
  dispatching, over hand-rolling socket management.

## 8. Competitive landscape — what we're actually trying to beat/match

For context on what "equal to or better than OddsJam" is actually
competing against, and to sanity-check the target isn't already solved by
an existing free tool covering Novig:

| Tool | Novig coverage | Price | Notes |
|---|---|---|---|
| **OddsJam** | Yes (per its own site) | Gold $199.99/mo (EV+arb+middles); Trends $19.99/mo; Platinum ~$400–500/mo | The incumbent being replaced. Lets users pick devig method + source-of-truth book. |
| **Odds Assist Pro** | **Yes, confirmed hands-on — see §8.1** | **Free** (no card; a free account unlocks more rows) | 30s refresh (claimed, unverified), undisclosed devig method. Real, live, currently-active Novig edges confirmed by direct testing 2026-09-20 — see §8.1 for the verdict on whether it's "as good as OddsJam." |
| **CrazyNinjaOdds** | **Yes, with liquidity per row — see §16.1** | Free (donations) | Disclosed method (worst case of mean/median, min books). Real Novig edges, but $5–$15 deep. |
| **Sharp Lines** | Free tier: DraftKings + FanDuel only, **no Novig** | Free (limited) / paid unclear | Free tier capped at 2% EV, 60s delay — not a Novig source. |
| **AVO** | 70+ books claimed, Novig unconfirmed | Free "Explorer" tier (up to 2% edges) + paid | Worth checking directly for explicit Novig support. |
| **RebelBetting** | Unconfirmed | $99+/mo | Not price-competitive with the $30/mo ceiling; deprioritize. |

### 8.1 Odds Assist Pro deep-dive (2026-09-20, hands-on verified)

Tj asked specifically: *does it actually find +EV on Novig, and is it as
good as OddsJam?* Answered directly rather than restated from marketing
copy — this required actually loading the live tool in a browser
(pre-installed Chromium via Playwright) and reading real, current output,
not just reading vendor pages about it.

**Who runs it:** Upper 9 Media LLC (`oddsassist.com`, contact
`hello@oddsassist.com`) — a small operation ("small team," "passionate
sports fans" per their own about copy), not a funded/VC-backed company the
way OddsJam is. Public review evidence is thin: exactly **one** Trustpilot
review as of this research (5 stars, praises accuracy and says the
reviewer "became a profitable bettor" using it for over a year) — a single
data point is not a track record, and should be weighted as such.

**Does it actually find +EV on Novig? Yes — confirmed live, not just
claimed.** Loaded `pro.oddsassist.com/advantages/plus-ev` directly (no
account, no signup), opened the "Sportsbooks" filter, and isolated the
book list down to **Novig only** (deselecting the other 10 available-in-
California books one at a time — the filter UI requires this, there's no
single-click "Novig only" shortcut). With only Novig selected, the tool
surfaced real, dated, currently-live opportunities, e.g. (captured
2026-09-20, California region — results are state-filtered and will differ
by state):

| Event | Bet | Novig price (Am. odds) | No-Vig reference | Claimed edge |
|---|---|---|---|---|
| Miami Dolphins @ SF 49ers, 09/20 8:25 PM | Miami Dolphins ML | +809 | +666 | 18.73% |
| New York Liberty @ Toronto Tempo, 09/20 7:00 PM | Toronto Tempo ML | +733 | +643 | 12.22% |
| South Carolina @ Alabama, 09/26 11:00 PM | South Carolina ML | +400 | +349 | 11.47% |

This is real: current games, a working Novig-specific filter, a
distinct Novig logo tag on each row, live-updating numbers across repeated
loads. **So yes — it works, and it's free.** Confirms/upgrades §10's old
item 4 from "should trial" to "trialed, functional, positive result."

**But read the edge numbers with real skepticism before trusting them —
this is the substantive finding, not just "it works":** an 18.73% edge on
a +809 (extreme-underdog) moneyline is a *huge* number — multiples of what
a mature market like OddsJam's typically flags (real +EV edges are usually
low single digits to maybe 5–8%; anything materially higher on a stale or
thin line is a yellow flag, not a green one). §5 of this file already
covers *why* that's suspicious: **multiplicative devigging — the simplest
and most common method — specifically overstates underdog value**, exactly
the favorite-longshot bias problem. Odds Assist doesn't disclose which
devig method feeds its "No Vig Odds" column or which book(s) it references
(no "source of truth" picker the way OddsJam has, per §5's close). A large
apparent edge concentrated on extreme-longshot lines is at least partly
consistent with "simple devig method run on a thin, longshot-heavy
market," not necessarily "real, capturable mispricing." This doesn't mean
the tool is fake or the numbers are wrong — it means **the edge number by
itself isn't trustworthy without knowing the methodology**, which is
exactly the kind of thing a from-scratch app can do better by *disclosing*
its devig method and letting it be tuned (§5's recommendation already
independent of this finding, now with a concrete reason why it matters).

**Is it as good as OddsJam? No — but "as good" is the wrong frame for what
it actually is.** Feature-by-feature, from what's directly observable:

- **Coverage:** Odds Assist's own filter list shows ~12 books available in
  California (BetOnline, BetUS, Bovada, Fliff, Kutt, MyBookie, Novig,
  OG.com, Polymarket, ProphetX, Underdog, Kalshi) plus a longer
  "unavailable in your state" list (BetMGM, Caesars, DraftKings, ESPN Bet,
  Fanatics, FanDuel, Hard Rock, Pinnacle, BetParx, BetRivers, Bally Bet) —
  book availability is **state-gated** on this tool, worth remembering when
  judging results (a different state, Tj's actual one, may unlock more).
  OddsJam scans 50+ books with no such regional gate on the tool itself
  (regional legality is the sportsbook's problem, not the scanner's).
- **Transparency:** OddsJam lets the user pick the devig method and
  "source of truth" book; Odds Assist Pro discloses neither — a real gap
  for a serious bettor who wants to trust the number, not just the flag.
- **Free-tier row cap:** without an account, only ~3–4 rows are unblurred
  at a time ("Sign up for free to see more positive ev bets" gates the
  rest) — signup is stated to be a **free** account (no card mentioned),
  not a paid unlock, but this wasn't verified by actually creating an
  account (didn't create one on Tj's behalf without asking first).
- **Depth:** the product has Arbitrage Bets, Live Arb Bets, Low Hold Bets,
  Middles, and Moving Lines as separate free tools in its nav — broader
  than a bare EV scanner — but no visible bet tracker or CLV tracking on
  the pages checked, which OddsJam has natively.
- **Novig sits in the free "SPORTS BETTING" section of the product, not
  the paid "Prediction Markets Pro" ($19.99/mo) tier** — that paid tier is
  a separate product for Kalshi/Polymarket-style event contracts (its own
  nav items are "Earnings Mentions," "Politics Mentions," "Entertainment
  Mentions" — corporate-earnings and politics contracts, nothing
  sports-related). This resolves an apparent contradiction from the first
  research pass (an Odds Assist review page said Novig is "missing an API
  for traders" — that page reads as stale/uninformed marketing copy about
  Novig in general, not evidence against Odds Assist's own free +EV tool,
  which demonstrably works against Novig right now regardless of what that
  one review page claims about Novig's API situation).

**Verdict for Tj:** Odds Assist Pro is real, free, and does currently
surface live Novig +EV opportunities — worth running alongside anything
else while deciding what to build, and a legitimate reason to not rush
into building a Novig scanner from zero if the goal is just "see +EV
Novig bets today." It is not a full OddsJam replacement (undisclosed
methodology, no source-of-truth control, no bet tracking/CLV, capped free
rows, state-gated book list) — and it's exactly those specific gaps, not
"build something free," that should define what "equal to or better than
OddsJam" needs to mean for the app this repo is building: **methodology
transparency (disclosed, tunable devig — §5) and real bet
tracking/CLV are the two concrete features to prioritize over Odds Assist
Pro, not just matching its edge-list UI.**

**Action item:** before writing any app code, actually run Odds Assist
Pro for a few real days (Tj's own state, his own account once he decides
to sign up) to see what fraction of its flagged Novig edges are real vs.
longshot-devig noise — this is a much stronger design input than a single
research-session snapshot.

## 9. Legal / ToS posture

**Updated 2026-09-22 (§4.4) — the actual data source in the app today is
the direct GraphQL client, not Novig's official API, so this section's
framing needs to be read against that, not against the "clean case"
sub-bullet below, which describes a different (unconfirmed, dormant) path.**

- **`NovigGraphQlClient` (§4.4, the leg actually wired in today) is a real,
  disclosed gray area — be honest about this, don't round it up to
  "clean."** It queries Novig's internal backend directly, unauthenticated,
  through rotating proxies specifically chosen to avoid IP-based rate-
  limiting/anti-bot detection. That's a materially different risk category
  from a published, sanctioned public API — especially now that Novig is a
  CFTC-regulated exchange with its own published rulebook (see below). Two
  things meaningfully limit the downside, though, and are worth stating
  plainly alongside the risk: (1) **no account or login is ever involved**
  — there is nothing here that can get Tj's actual Novig account banned,
  unlike a ToS violation on an authenticated endpoint would; (2) it's
  **read-only** — no orders are ever placed through this path. What Novig
  *can* do is block or blacklist the proxy IPs at any time without notice;
  that's a real, standing possibility, not a hypothetical. This mirrors
  what odds-aggregator/scraping tools commonly do industry-wide (see the
  scraping-based-fallback bullet below, which already accepted this same
  tradeoff for the *reference* leg before §4.4 existed) — not a novel or
  unusually risky category of thing to build, but still a real one. The
  app ships this opt-in only (empty proxy list → sample data), with the
  risk stated in-app (Settings screen) as well as here, never as a silent
  default.
- **Novig's own official, credentialed API — if it exists and if Tj's
  developers@novig.com email gets a "yes" — would be the clean case**: built
  and marketed by Novig specifically for algo traders automating strategies
  against their exchange, the API's intended use, not a gray area. Novig is
  a CFTC-regulated Designated Contract Market operating in 47 states + DC
  ([CNBC](https://www.cnbc.com/2026/06/16/novig-wins-cftc-approval-as-competition-intensifies-in-sports-prediction-markets.html),
  [sportshandle.com](https://sportshandle.com/novig-launches-cftc-regulated-sports-prediction-market-in-47-states/)) —
  Nevada, Arizona, and Michigan are excluded; confirm the Moto G 2026's
  operating location isn't one of those three before assuming account
  access works. `NovigApiClient`/`NovigLiveFeed` stay dormant, unwired,
  ready for this path — but per §4.4 it's now unconfirmed whether this
  official path is even reachable for an individual, separate from whether
  its assumed field shapes are correct.
- **The reference-odds leg** (§4.3, Pinnacle/consensus via a paid API like
  The Odds API) is licensed data via a paying customer relationship — also
  clean.
- **If forced onto a scraping-based free fallback** (e.g. odds-api.io's
  "we aggregate publicly displayed odds... not affiliated" model) for the
  reference leg specifically — this app would only ever use that data as
  an internal comparison signal, never republish/redistribute it, which is
  the same posture the entire +EV tool industry operates under. Still
  worth being aware this leg (if it ever comes to this) sits on less solid
  footing than Novig's own official, sanctioned API — another reason to
  prioritize confirming §4.1 first.

## 10. Open items — what's still unverified, ranked by how much they gate
    the architecture

1. **Can Tj actually get a Novig API client ID/secret, and is it free?**
   (§4.1) — **superseded as the app's actual blocker, 2026-09-22 (§4.4):**
   a working, verified, $0 access method exists via `NovigGraphQlClient`
   (unauthenticated GraphQL, no account needed) — the app no longer waits
   on this to have a real Novig-leg data source. Still genuinely open,
   just lower-priority now: Tj's developers@novig.com email is still
   unanswered, and a real reply would resolve whether the *official,
   sanctioned* path (`NovigApiClient`/`NovigLiveFeed`, still dormant) is
   ever reachable — worth having regardless, since it wouldn't carry
   §4.4/§9's ToS-gray-area risk or ongoing proxy cost. Not blocking
   anything today.
2. ~~Does SharpAPI's free tier's raw-odds set include Pinnacle~~ —
   **resolved, 2026-09-20 (§4.2.2):** no. SharpAPI's own Novig product
   page states the free tier is scoped to **DraftKings and FanDuel
   only** — not the ~40-book catalog. That answers this for Pinnacle too
   (it's not DraftKings or FanDuel), so the reference leg's free-tier
   options are just The Odds API (§4.3, confirmed genuinely free,
   confirmed to include Pinnacle) — which is exactly what's already
   wired in as the reference-leg client, so no change needed there.
3. ~~Novig's parlay fee structure~~ — **resolved, 2026-09-22 (§3/§4.4):**
   `price × (1 - price) × 0.10` (same shape as the confirmed live-straight
   taker fee, 0.10 multiplier instead of 0.03), confirmed by the briefing
   Tj supplied. `Fees.kt`'s `PARLAY` case now returns a known fee instead
   of `Unknown`.
4. ~~Hands-on trial of Odds Assist Pro against Novig~~ — **done, §8.1**:
   confirmed working and free, but with an important caveat (undisclosed
   devig method, large edges on longshot lines are suspect). New follow-up
   from this: **run it for real over several days** (Tj's own state/account)
   to see what fraction of flagged edges hold up, rather than trusting a
   single snapshot — this is now the higher-value open item than the old
   "does it even work" question was.
5. Full field-level REST/WebSocket response schemas (exact JSON shape of
   order book / market-by-event payloads) — need to actually request a
   token and hit the API (or read the OpenAPI 3.1 spec docs.novig.com
   offers) once §1 is resolved, rather than inferring from doc summaries.
6. **(new, §4.4)** `NovigGraphQlClient`'s team-name extraction and
   market-type classification are best-effort, unconfirmed against a real
   live response — the reference package never needed to solve either
   problem (single-source liquidity filter, no cross-provider matching).
   Needs verification the first time Tj actually configures proxies and
   runs a real scan: does the moneyline-outcome-based team-name guess
   actually match the reference leg's `home_team`/`away_team` naming
   closely enough for `EventMatcher` to find real matches, and do the raw
   `market.type` values turn out to be human-readable or opaque codes.
7. **(new, §4.4)** What GraphQL `status` value Novig uses for live/in-play
   markets — both verified queries hardcode `"OPEN_PREGAME"`; live-market
   support would need this discovered/confirmed first, not guessed.

8. **(new, 2026-09-25)** Items 1, 5, 6 and 7 above are **superseded by
   [`NOVIG_API.md`](NOVIG_API.md)**. The official v3 API is documented,
   public routes are verified live, the full schemas are in the OpenAPI spec,
   and event statuses are `OPEN_PREGAME/OPEN_INGAME/...`. Still open, per
   NOVIG_API.md §12: the per-IP rate limit on public routes, and an
   end-to-end signed call (needs Tj's key).

None of the above blocks starting architecture/BRIEF.md decisions — they
block finishing them. Do not start writing app code from this file alone
per `TASKS.md`; architecture still needs Tj's sign-off once the open items
above are resolved enough to make real decisions.

## 11. Free and cheap odds sources, researched 2026-09-25 (Tj: "free or cheap by any means")

**Why it came up:** v0.5.0 on Tj's phone hit Novig 429s (auto-poll of ~44 public books every
15s). Tj asked for manual-only scans, provider-safe limits, and the best free/cheap way to get
fair odds from sharp books many times a day. Everything below was checked live from this
container or read from the provider's own docs the same day.

### 11.1 Measured provider limits (encoded in the app, see NOVIG_API.md §5.1)

| Provider | Limit (source) | What it means for a scan |
|---|---|---|
| Novig public routes | Per-IP CloudFront rule, undocumented. **Measured:** ~40–100 fast requests, then `429` + `Retry-After: 1`; at a steady 10/s about 6% get 429 | Pace books ≤4/s, burst ≤10, 2 at a time. A phone on a carrier IP shares that IP with other people (CGNAT), so the real budget can be lower |
| Novig signed routes (key) | Per key: `read` bucket 64 burst, 16/s refill (docs); edge per-IP rule also applies | With a key, books come from per-key buckets instead of the shared public rule |
| The Odds API | 500 credits/mo free. Cost = markets returned x regions (≤10 named books = 1 region). **Empty responses cost 0.** Burst 429s: "space requests over several seconds" (docs) | Re-use its odds for N minutes, ask only for the market families selected, one sport at a time |
| Polymarket Gamma API | `/markets` 300 req/10s, `/events` 500/10s; over-limit requests are queued, not rejected (docs) | Effectively unlimited for a manual app. No key |
| Kalshi public API | Basic tier 200 tokens/s, 10 per read = 20 reads/s; 429 with no cooldown (docs). Market data needs no key (verified) | 3 requests per league per scan. No key |
| pinnapi (Pinnacle feed) | Trial: **free, no card, no expiry**, 20/min, 100/hour, **100/day** per key (docs) | One request = a whole sport's prematch Pinnacle board, so ~2 requests per scan |

### 11.2 Sources compared

- **Polymarket** (free, no key): deep US-sports game markets. Verified live on 2026-09-25: NFL
  moneyline, many alternate spreads, and totals with 1¢ bid/ask spreads and $50k–$300k
  liquidity per line (e.g. Ravens/Cowboys ML 0.62/0.63, $297k). Sharp in practice for NFL.
  Leagues: NFL, NCAAF (cfb), NBA, MLB, NHL, WNBA, UFC, MLS, EPL. Query:
  `GET gamma-api.polymarket.com/markets?closed=false&tag_id=<league tag>&sports_market_types=moneyline&sports_market_types=spreads&sports_market_types=totals&end_date_min=..&end_date_max=..&limit=100&offset=..`
  (≈46KB gzipped per 100 markets). `bestBid`/`bestAsk` are for `outcomes[0]`; spreads carry
  `line` for `outcomes[0]`.
- **Kalshi** (free, no key): CFTC exchange. Series `KX{NFL,NCAAF,MLB}{GAME,SPREAD,TOTAL}`,
  `KXNBAGAME`, `KXNHLGAME`, `KXMLSGAME`, `KXUFCFIGHT` had open markets. MLB quotes are 1¢ wide;
  some NCAAF lines are very wide, so a spread/liquidity filter is required.
  `GET api.elections.kalshi.com/trade-api/v2/events?series_ticker=..&status=open&with_nested_markets=true`.
  Game = one market per team ("Carolina wins"); spread = "CAR wins by over 2.5" (Yes = CAR −2.5,
  No = CLE +2.5, `floor_strike`); total = "over 45.5" (`floor_strike`). Dates only in the ticker
  (`26SEP27` = ET date).
- **pinnapi** (Pinnacle, free trial key): Pinnacle closed its own public API on 2025-07-23
  (only bespoke commercial/academic access now). pinnapi is an independent feed (not
  affiliated with Pinnacle) with a free, non-expiring trial: 100 requests/day.
  `GET pinnapi.com/kit/v1/markets?sport_id=<5 football|6 baseball|3 basketball|4 hockey|8 MMA|1 soccer>&event_type=prematch`,
  header `x-portal-apikey`. `periods.num_0.money_line {home, away, draw?}`,
  `spreads{"<hdp>": {hdp(home), home, away}}`, `totals{"<pts>": {points, over, under}}`, decimal
  odds. Paid plans start at $99/mo (over budget), so free trial only.
- **The Odds API** (current): the free tier does include Pinnacle (Tj's own v0.5.0 screenshot shows
  Pinnacle among the books used) plus the US books; 500 credits/mo. $30/mo = 20K credits is the
  clean paid upgrade.
- **OddsPapi**: free 250 requests/month, but **per game** (`/fixtures/{id}/odds`), so ~8 games a
  day. Has Pinnacle/Circa/Singbet. Pro is $49/mo. Good only for spot-checking one game.
- **Rejected:** SharpAPI (free tier is DK/FD only, Novig paid), OpticOdds/Betstamp/MetaBet (sales
  gated), Pinnacle direct (closed), scraping sportsbooks' private web endpoints (ToS and
  bot-blocking risk; not needed now that the free exchange and Pinnacle routes above exist).

### 11.3 Multiple free Odds API keys on several emails

The Odds API terms don't explicitly forbid more than one account, but they reserve the right to
"terminate API access at any time without warning" if they "suspect abuse". Deliberately
multiplying free accounts to get around the 500-credit quota is exactly what that clause covers,
and all the keys come from one phone/IP, so they'd be easy to link and could be revoked
together. Vigilant already rotates keys automatically if Tj adds several. **Recommendation: not
needed.** Polymarket + Kalshi + pinnapi give sharp fair odds for free, and The Odds API becomes an
occasional extra that one key covers. If more Odds API refreshes are wanted, the $30/mo 20K plan
is the no-risk option.

### 11.4 Recommendation, implemented in v0.6.0

Fair odds from **Pinnacle (pinnapi free key) + Polymarket + Kalshi on every scan** (≈4–8
requests per scan in total, all well inside free limits), with The Odds API re-used for up to
N minutes to save credits. Novig books are paced to avoid its per-IP 429, and read through the
signed per-key routes once Tj connects a Novig key. Nothing fetches except on Tj's Scan button or
pull-to-refresh.

### 11.5 Live results, v0.6.0 code against the real APIs (2026-09-25, from this container)

`VIGILANT_LIVE=1 VIGILANT_LIVE_LEAGUES=NFL,MLB,NCAAF ./gradlew :data:test --tests '*LiveNovigSmokeTest*real scan*'`,
free keyless sources only (Polymarket + Kalshi), `linesPerGame` 2:

| League | Novig games | Matched to a fair line | Novig books read | 429s |
|---|---|---|---|---|
| NFL | 16 | 14 (Polymarket 14, Kalshi 14) | 70 | 0 |
| MLB | 17 | 16 (Polymarket 15, Kalshi 16) | 78 | 0 |
| NCAAF | 120 | 112 (Polymarket 19, Kalshi 112) | 396 | 0 |

- Scan time here was ~1 book/s because this container's proxy adds 0.15–0.5s per request and
  only 2 run at once. On a phone the 4/s pace is the limit: NFL ≈ 20s, a full Saturday of
  NCAAF ≈ 100s (≈ 50s with a Novig key). Fewer `linesPerGame` or days ahead shortens it.
- Typical NFL edges vs the two exchanges were 1–2%; college alternates showed 3–4% against
  Kalshi alone. A quarter of tight Kalshi college alt quotes had under 38 contracts at the top,
  so v0.6.0 requires ≥100 contracts on each side (`KalshiClient.MIN_TOP_SIZE`) and Polymarket
  requires ≥$1,000 liquidity. Edges priced from one exchange only are still the least
  trustworthy: prefer ones where Pinnacle or both exchanges agree.
- pinnapi could not be exercised live (no key). Its parser follows the published docs, and
  league names are matched loosely with a whole-sport fallback.

## 12. Provider quotas, resets and rules for key rotation and meters (researched 2026-09-25 ~14:05Z)

Read from each provider's own docs the same day. Encoded in `data/keys/Quota.kt`.

| Provider | Quota | Resets | What the API tells us | Refusals |
|---|---|---|---|---|
| The Odds API | Free: 500 credits/month per key. `GET /odds` costs **markets specified × regions** (10 named bookmakers = 1 region); **0 if no events come back**; `/sports` and `/events` are free | **1st of every month** (FAQ; time zone not stated, treated as UTC, and corrected from the headers) | Every response: `x-requests-remaining`, `x-requests-used`, `x-requests-last` (cost of that call) | `OUT_OF_USAGE_CREDITS` (quota used), `INVALID_KEY`, `DEACTIVATED_KEY`; `429 EXCEEDED_FREQ_LIMIT` above **30 calls/s** ("space out API calls over several seconds") |
| pinnapi (trial) | 100 requests/day, 100/hour, 20/minute per key | Day window at **UTC midnight** ("429 with window=day until UTC midnight rolls over") | Nothing on success (no usage headers, no usage endpoint) → count locally | `429 {"error":"rate_limited","window":"minute"\|"hour"\|"day","limit":N,"retry_after_ms":M}` + `Retry-After`; `401 missing_key/invalid_key` |
| Polymarket Gamma | 300 `/markets` requests per 10s; excess queued | Rolling | None | Throttled, not refused |
| Kalshi | Basic tier 20 reads/s (200 tokens/s, 10 per read) | Rolling | None | 429 |
| Novig public | Per-IP edge limit, undocumented (~40–100 fast requests, then 429 Retry-After 1) | Seconds | None | 429 / HTML 403 |
| Novig signed | Per key: read 64 burst, 16/s | Seconds | `GET /v3/limits` (not called: costs a request) | 429 + Retry-After |

**Rules that matter for multiple keys:**
- **pinnapi terms:** "Don't attempt to disrupt, overload, reverse-engineer, or **circumvent the Service or its
  rate limits**" and "We may suspend or terminate accounts that violate these terms." Rotating several
  trial keys to get past 100/day is plausibly "circumventing its rate limits". The app supports it because
  Tj asked, and warns in Settings; one key used within its limit carries no such risk.
- **The Odds API:** no rule on multiple accounts in the FAQ; the terms' "suspect abuse" clause (§11.3) still
  applies.
- Both: the app never knowingly sends a call a key can't afford (pre-emptive skip), so rotation happens
  before a refusal, not after one.

**How the app meters (smart, per key, persisted in `usage.json`):**
- The Odds API: trusts the headers after every call (used, remaining, limit = used + remaining). Before a
  call it skips any key whose remaining is below the call's cost (families × 1 region). Period = calendar
  month UTC; if the server's used count drops without a month change, the key's cycle is different
  (e.g. a paid plan's billing date) and the ledger follows the server. A key refused with
  OUT_OF_USAGE_CREDITS rests until the 1st; if it's still refused right after a reset, it's re-probed
  every 6h instead of waiting another month.
- pinnapi: counts requests per key per UTC day, and keeps call times for the 20/min and 100/hour windows;
  a 429 uses the server's own `window` and `retry_after_ms`.
- Rotation always starts from key 1: a key is used only when every key before it is spent or cooling
  down, so when a period resets, key 1 is back in front automatically.
- Keyless providers show requests today and any throttling.

## 13. Alternative markets (props, 1st half / first 5, team totals), researched 2026-09-25 ~15:30Z

**What Novig lists (live, pregame, next 4 days):**
- NFL: player props dominate (RECEIVING_YARDS 1664, RECEPTIONS 847, RUSHING_YARDS 602, TOUCHDOWNS 373,
  PASSING_YARDS 337, FIRST_TOUCHDOWN_SCORER 306, LONGEST_RECEPTION, RUSHING_AND_RECEIVING_YARDS,
  PASSING_TOUCHDOWNS, RUSHING_ATTEMPTS, PASSING_ATTEMPTS, INTERCEPTIONS_THROWN…), TEAM_TOTAL,
  SPREAD_1H, TOTAL_1H. NCAAF: SPREAD_1H, TOTAL_1H, MONEY_1H, TEAM_TOTAL (no player props).
  MLB: HITS, TOTAL_BASES, HOME_RUNS, RBIS, RUNS, HITS_RUNS_RBIS, STOLEN_BASES, BATTING_STRIKEOUTS,
  PITCHER_STRIKEOUTS, HITS_ALLOWED, EARNED_RUNS, PITCHER_OUTS, TEAM_TOTAL, TOTAL_1H/MONEY_1H/SPREAD_1H
  ("1H" = first 5 innings), FIRST_INNING_TOTAL. WNBA: POINTS, REBOUNDS, ASSISTS,
  THREE_POINTERS_MADE, POINTS_REBOUNDS_ASSISTS, DOUBLE_DOUBLE, MONEY_1H/SPREAD_1H/TOTAL_1H.
- Shapes: props `"Patrick Mahomes 233.5 PASSING_YARDS"`, outcomes `Over 233.5`/`Under 233.5`;
  team totals `"Los Angeles Rams 22.5 TEAM_TOTAL"` Over/Under; `SPREAD_1H` `"WSH +4.5 1H"` with
  outcomes `WSH +4.5`/`SEA -4.5`; `TOTAL_1H` `"LAR @ DEN t21.5 1H"` Over/Under. Every one is
  `voids: FMV` (a void settles at fair market value, not a refund).

**Fair-odds sources for them:**
- **Kalshi (free):** NFL `KXNFL1HSPREAD`, `KXNFL1HTOTAL`, `KXNFLTEAMTOTAL`, props `KXNFLPASSYDS`,
  `KXNFLRSHYDS`, `KXNFLREC` (receptions), `KXNFLRECYDS`, `KXNFLRRYDS`, `KXNFLTD`, `KXNFLPASSTDS`,
  `KXNFLPASSATT`, `KXNFLRSHATT`, `KXNFLPASSINT`, `KXNFLLONGREC`, `KXNFLLONGRSH`; NCAAF
  `KXNCAAF1HSPREAD`, `KXNCAAF1HTOTAL`, `KXNCAAFTEAMTOTAL`; MLB `KXMLBF5SPREAD`, `KXMLBF5TOTAL`,
  `KXMLBTEAMTOTAL`, `KXMLBKS`, `KXMLBTB`, `KXMLBHIT`, `KXMLBHR`, `KXMLBHRR`, `KXMLBRBI`, `KXMLBSB`,
  `KXMLBHA`; WNBA `KXWNBA1HSPREAD`, `KXWNBA1HTOTAL`, `KXWNBATEAMTOTAL`, `KXWNBAPTS`, `KXWNBAREB`,
  `KXWNBAAST`, `KXWNBA3PT` (none open late Sept). Props: market title `"Bryce Young: 150+ passing
  yards"`, `floor_strike` 149.5, Yes = over; ladders at whole-number thresholds, so only Novig lines
  that equal a Kalshi strike are priced (counting props match often; yardage less).
  Team totals: `"Boston over 2.5 runs scored"`, ticker suffix team code + digits, `floor_strike`.
  F5/1H spreads: `"Boston wins first 5 innings by over 2.5 runs?"`, suffix `BOS3`.
- **Pinnacle via pinnapi:** the same single request per sport already carries `periods.num_1`
  (1st half; 1st 5 innings in baseball) spreads/totals and `team_total` — no extra calls.
  Pinnacle props need `include_specials=1` (separate events, unverified shape): not used yet.
- **Polymarket:** no props or 1st-half markets for these leagues (only thin novelty markets).
- **The Odds API:** props and period markets only via `/events/{id}/odds`, charged per event
  (markets x regions each). Used for props since v0.9.0 under a strict per-scan budget: §14.

**Not priced, on purpose:** 1st-half/first-5 **moneylines**. Kalshi's are 3-way (tie is a
separate outcome, ~7% in NFL halves) while Novig's MONEY_1H is 2-way with `voids: FMV`, and how a
tied half settles isn't documented. Yes/No props with no two-sided source (first TD scorer,
double-double) are also skipped.

**Kalshi throttling, measured:** after ~130 unauthenticated requests in ~40s it answered
`429 {"error":{"code":"too_many_requests"}}` and kept refusing at 1 request/s for a while. The
documented 20 reads/s is not what an anonymous client gets in a burst. The app paces Kalshi at
2 requests/s (burst 4) and backs off on 429.

## 14. Sportsbook player props and cross-book matching, researched 2026-09-25 ~16:30Z

Tj (16:15Z): "Other major sports books offer props. See if you can make a market average then
devig for the props. Make sure the app matches odds between different sports books."

**Source: The Odds API per-game endpoint** (the only practical way to get DraftKings, FanDuel,
BetMGM, Caesars, ESPN BET, Fanatics, BetRivers props on a free key):
- `GET /v4/sports/{sport}/events?apiKey&dateFormat=iso&commenceTimeTo=…` — ids, teams, start
  times, no odds. **Free** (doesn't count against the quota).
- `GET /v4/sports/{sport}/events/{eventId}/odds?apiKey&bookmakers=…&markets=…&oddsFormat=decimal`
  — cost = **unique markets returned x regions**; up to 10 named `bookmakers` = 1 region; a market
  no book posts costs nothing. `x-requests-last` carries the real charge.
- Outcome shape: `{"name":"Over","description":"Josh Allen","price":1.87,"point":245.5}` — one
  flat list per market, every player in it, sometimes two numbers per player at one book.
- Market keys (their betting-markets page, re-checked 16:30Z), each mapped to Novig's stat:
  NFL `player_pass_yds, player_rush_yds, player_reception_yds, player_receptions` (the "Core 4"),
  then `player_pass_tds, player_tds, player_anytime_td, player_pass_attempts,
  player_pass_completions, player_rush_attempts, player_pass_interceptions,
  player_rush_reception_yds, player_pass_rush_yds, player_reception_longest, player_rush_longest,
  player_pass_longest_completion, player_kicking_points, player_field_goals`.
  MLB `batter_hits, batter_total_bases, pitcher_strikeouts, batter_hits_runs_rbis`, then
  `batter_home_runs, batter_rbis, batter_runs_scored, batter_stolen_bases, batter_strikeouts,
  batter_walks, pitcher_hits_allowed, pitcher_earned_runs, pitcher_outs, pitcher_walks`.
  WNBA `player_points, player_rebounds, player_assists, player_threes`, then
  `player_points_rebounds_assists, player_double_double`.
- Listing styles: most are Over/Under. `player_anytime_td` and `player_double_double` are
  **Yes/No** (Yes = Over 0.5; Novig lists them as "Over 0.5 TOUCHDOWNS"/"Over 0.5
  DOUBLE_DOUBLE", verified live). `*_alternate` ladders and `player_tds_over` are **Over only**
  and can't be devigged: never requested. A player priced on one side only is dropped.
- Novig's own names for all of these exist and were checked live (e.g. `WALKS` is pitcher walks,
  `BATTING_WALKS` batter walks; `RUNS` is runs scored). NCAAF has no player props on Novig.

**Budget (defaults):** Core 4 per game, 24 credits a scan (≈6 games), games starting within
24h, soonest first across every league, only stats Novig lists for that game, each game's props
re-used 60 min. 500 free credits ≈ 20 fresh prop scans a month per key on top of game lines;
more keys rotate in (§12).

**Fair price:** unchanged engine: each book devigged on its own, then averaged (Market average),
or blended with the sharp books (Kalshi's same-line prop) under Blend. Averaging raw odds first
and devigging once gives nearly the same number; per-book devig lets a stale or arbitrage-priced
book (hold < 0) be dropped instead of skewing the average. Only books on Novig's exact number
count (OddsJam's rule too); a book on 225.5 doesn't price Novig's 224.5.

**Matching across books** (`PlayerNames`, only ever within one matched game): case, accents,
punctuation, suffixes (Jr./III), joined initials (C.J./CJ), "Last, First", middle initials,
spacing (St. Brown/St.Brown), first-name short forms (Mike/Michael, Gabe/Gabriel, Zach/Zachary…)
and a few known nicknames (Hollywood/Marquise Brown, Kiké/Enrique Hernández, Jazz/Jasson
Chisholm). Never a surname alone or an initial alone. Games match on teams and start time exactly
as the main lines do (`Planner.matchEvents`); stats match through the per-source maps
(`PropStats`), so "Rush + Rec Yds", `player_rush_reception_yds` and Kalshi's `KXNFLRRYDS` all land
on Novig's `RUSHING_AND_RECEIVING_YARDS`.

**Not verified live:** no Odds API key exists in the dev container, so the props calls are
tested against recorded-shape fixtures only. First real scan with Tj's key is the live check.

## 15. Background scans, scan speed, and OddsJam-style market coverage, researched 2026-09-25 ~18:10Z

Tj (18:05Z): "Make sure it can run in the background without stalling… research safe ways to
speed up the scanning. Oddsjam refresh is very fast. This app is very slow. If it is not possible
to speed up, make the results show up in the app as they come in… oddsjam scans a wide range of
props and halftime / f5 markets… include markets most likely to have positive EV."

**Why a backgrounded scan stalled (v0.9.0).** The scan ran in the screen's `viewModelScope`. Once
Tj switched apps, Vigilant had no foreground component, so Android ranked it a cached process:
the cached-apps freezer (on by default since Android 14) suspends such a process within seconds,
and its network is cut. Backing out of the app also destroyed the ViewModel, which cancelled the
scan outright. **Fix (v0.10.0):** the scan runs in an app-lifetime `ScanRunner` (not tied to any
screen), and a `dataSync` foreground service (`ScanService`) holds the process for exactly the
length of one scan: a progress notification, a partial wake lock capped at 10 minutes (so a
screen-off phone keeps the CPU up), and `stopSelf()` the moment the scan ends. Nothing is
scheduled, polled or started at boot. Rules checked: an FGS must be started while the app is
visible (it is: the Scan tap), must call `startForeground` within seconds (done first thing), and
since Android 14 must declare its type (`dataSync` + `FOREGROUND_SERVICE_DATA_SYNC`); Android 15
caps `dataSync` at 6 hours a day (`onTimeout` stops it; a scan is ~1 minute). Processes running an
FGS keep network access under Doze and App Standby. Notifications need `POST_NOTIFICATIONS`
(asked once, on the first Scan tap); without it the service still runs and only the notification
is hidden.

**Where the time went (v0.9.0, one NFL scan):**
| Step | Calls | Pace | Time |
| ---- | ----- | ---- | ---- |
| Novig board (events + markets) | 2–4 pages | public gate | 1–3 s (re-used 3 min) |
| Kalshi (18 NFL series) | 18+ | 2/s (measured 429s at ~3/s anonymous) | ~9 s |
| Polymarket, The Odds API, pinnapi | 1–5 each | — | 1–3 s |
| **Wait for every source, then** Novig books | 200 | 4/s, 2 in flight | ~50 s |
Books waited for the slowest fair-odds source, and nothing was shown until the last book.

**Why OddsJam is faster.** OddsJam runs servers that hold streaming feeds from every book and
push already-priced results to the app; the phone does no fetching. Vigilant on a phone with no
Novig key must read each Novig market's book with one REST call through Novig's per-IP public
edge. There is **no bulk book route** in Novig's v3 API (re-checked: every catalog route in
`openapi-v3-target.json`; `Market` carries no price fields), so a scan's cost is one request per
market priced. The only OddsJam-class path is Novig's **websocket** (`GET /v3/ws`, `bbo` or `book`
on whole events: one socket, every book pushed), which needs a `trading::read` key (NOVIG_API.md
§6). The code for it exists (`NovigStream`, tested against a mock, never against the real API)
and is the next step once Tj creates a key.

**Safe speed-ups, implemented in v0.10.0:**
1. **Pipelining.** Novig books start as soon as the board is in, planned from the fair odds
   already known (the last scan's, until this scan's arrive) and re-planned whenever a provider
   answers. The ~10–20 s of fair-odds fetching now overlaps the book reads instead of preceding
   them. Each market is still read at most once per scan and never past the per-scan cap.
2. **Most-promising first.** Order: open bets → last scan's +EV lines (by EV) → near misses
   (≥ −2%) → lines never priced, props/period lines/team totals before main lines → lines that
   were well below zero → games no fair source covers. On a rescan, last scan's edges are
   re-checked within the first ~2 seconds.
3. **Streaming.** Every 8 books the scan re-prices and publishes a partial result: the feed fills
   in as prices land. Mid-scan, the feed offers only Novig prices read *this* scan (a stale price
   is never offered as a bet); the Games tab keeps last scan's prices until each is re-read.
4. **Pacing, still under every measured/documented limit.** Public book reads start at 4/s (as
   before) and rise 0.5/s after every 40 clean requests to at most 6/s (measured: ~5/s steady
   never drew a 429, 10/s did); any 429 halves the pace for a minute and restarts the ramp; 5 idle
   minutes restart it too. Three requests in flight instead of two (at a phone's 300–500 ms per
   request, two in flight couldn't reach 6/s). With a key, the signed book route runs at 14/s,
   burst 40, 6 in flight (documented `read` bucket: 64 burst, 16/s).
**Measured live (2026-09-25 ~18:45Z, this container's data-center IP):** the v0.10.0 public pacing
(4/s ramping to 6/s, burst 10, 3 in flight) read 240 real NFL/MLB books in 45.2 s (5.3/s average,
ramp reached 6/s) with **240 × 200, zero 429/403**; median latency 170 ms, p90 478 ms. The same
240 at v0.9.0's fixed 4/s take 60 s. A phone on a carrier IP may see more 429s (shared IP); the
ramp backs off on the first one.

Net effect for a 300-book scan on public routes: first results in ~2–5 s instead of after the
whole scan; the whole scan ~55–60 s for 50% more markets than v0.9.0's 200 (which took ~65 s
including the wait for the fair odds).

**Not done, and why:** faster public pacing (the edge limit is unpublished and per IP, and a
carrier IP may be shared: v0.5.0 got 429s); scraping Novig's web app (ToS, and NOVIG_API.md §9
retired that path); multiple IPs/proxies (signed routes refuse them with 451, and it's evasion).

**Markets most likely to be +EV, and what was added.** OddsJam's own guidance and the exchange
structure point the same way: main lines are the most efficient (every sharp book and bot prices
them), while player props, period lines (1st half, first 5 innings, 1st inning) and team totals
are thinner on Novig, move on news, and are where resting orders go stale. Novig lists (live,
18:30Z): MLB `FIRST_INNING_TOTAL` ("PIT @ DET FIRST_INNING_TOTAL", Over/Under 0.5: NRFI/YRFI),
`PITCHER_OUTS` 29, `EARNED_RUNS` 55, `WALKS` 64; NFL `PASSING_COMPLETIONS` 44, `KICKING_POINTS`
31, `FIELD_GOALS_MADE` 26; plus `MONEY_1H` in both. Kalshi (free) prices five of these, all open
and quoted live at 18:30Z:
- `KXMLBRFI` "1st inning: Over 0.5 runs" (Yes = over; its `floor_strike` reads 1, so the line is
  fixed at 0.5 in code) → `FIRST_INNING_TOTAL`, a new period (`PERIOD_FIRST_INNING`), in the
  "1st half / F5 / NRFI" family. 1¢-wide quotes seen live.
- `KXMLBOUTS` → `PITCHER_OUTS`, `KXMLBERA` → `EARNED_RUNS`, `KXMLBWA` ("walks allowed") →
  `WALKS` (Novig's pitcher walks), `KXNFLPASSCOMP` → `PASSING_COMPLETIONS`. Same "Name: N+"
  ladder shape as the other Kalshi props. These were sportsbook-only (paid credits) before; now
  they have a free source and are read from Novig on every scan.
Quote quality, measured live 18:50Z against the app's own filter (≤3¢ wide, ≥100 contracts each
side): `KXMLBRFI` 16 of 44 open markets usable, `KXNFLPASSCOMP` 12 of 84; the pitcher ladders
were mostly too thin that afternoon (`KXMLBOUTS` 0/24, `KXMLBERA` 1/108, `KXMLBWA` 0/70). Thin
quotes are dropped, never priced, so these cost one Kalshi request each and add lines only when
they tighten (typically nearer first pitch). NRFI and pass completions are the real additions today.
Defaults widened because streaming makes coverage cheap to wait for: props per game 4 → 8, Novig
reads per scan 200 → 300 (saved settings move only if still on the old defaults).

**Still not priced:** `MONEY_1H` (Kalshi's `KXMLBF5`/`KXNFL1H` are 3-way with a tie; how Novig
settles a tied half on its 2-way FMV market isn't documented), `KICKING_POINTS`/`FIELD_GOALS_MADE`
without an Odds API key (no free source), NHL props (Kalshi's `KXNHLPTS`/`KXNHLGOAL`/`KXNHLSAVES`
had no open events on 2026-09-25, preseason: shapes unverified), first-TD scorer (multi-way).

## 16. OddsAssist Pro and CrazyNinjaOdds, re-checked 2026-09-26 (~01:50–02:30Z)

Tj asked: do `pro.oddsassist.com/advantages/plus-ev` and
`crazyninjaodds.com/site/tools/positive-ev.aspx` truly offer +EV bets, for Novig, and can the app
use them? How this was checked: CrazyNinjaOdds (CNO) loaded once in the pre-installed Chromium
(its table renders server-side); OddsAssist's server-rendered HTML read with `curl` and parsed.
A second Chromium session (to filter each tool to Novig only) was blocked by the session's
permission classifier, so this pass did **not** re-run §8.1's hands-on Novig-only filter on
OddsAssist. Everything below is from the default all-books views unless it says otherwise.

### 16.1 CrazyNinjaOdds "Positive EV": yes, real, and it covers Novig

- **Free** (donation-supported; supporter tiers from $5/mo). "Last Updated: 56 seconds ago" at load.
- **Books:** 25+ including **Novig**, ProphetX, Kalshi, Pinnacle, Circa (NV), Bet365, DraftKings,
  FanDuel, BetMGM, Caesars, Fanatics, BetRivers, Hard Rock, Fliff, PrizePicks.
- **Liquidity is shown for exchanges**: a Novig row reads `+108 ($5)`, the dollars available at
  that price. Vigilant already shows the same thing ("$X fillable at +EV", from the full ladder).
- **Methodology is disclosed** (unlike OddsAssist): "Fair value is calculated from the worst-case
  between market average and median." Three weightings (Liquidity-Weighted: more weight to
  high-limit books; Unweighted Market Consensus; Conservative), each with worst-case /
  multiplicative / additive-Shin / power devig. Filters: min/max odds, min liquidity, min EV,
  min books (recommended 3), min market sides (recommended 2), mainlines only, start window.
- **Snapshot** (default filters, all books, top ~70 rows): EV 4.3%–15.8%, mostly player props at
  Bet365 / BetRivers / Bally, fair prices from 3–15 books. **Three Novig rows:**
  | Game | Bet | Novig (size) | Fair | Books | EV |
  |---|---|---|---|---|---|
  | SEA @ WAS | Jaxon Smith-Njigba Under 6.5 receptions | +108 ($5) | −104 | 15 | 6.06% |
  | PIT @ CLE (Thu) | Over 38.5 | +111 ($5) | +101 | 3 | 5.06% |
  | CAR @ CLE | Tetairoa McMillan Under 4.5 receptions | +115 ($15) | +105 | 13 | 4.76% |
- **Verdict:** the edges are real by the standard every +EV tool uses (Novig price beats a
  multi-book devigged consensus, conservatively computed). They are also **tiny in dollars**: $5–$15
  of liquidity at those prices is $0.25–$0.75 of expected profit per bet. That is the Novig +EV
  reality, not a CNO flaw: the lag is on thin props, and the size isn't there. The larger lever on
  an exchange is posting your own bid (§16.4).
- **Its devigger agrees with Vigilant's math.** CNO's devigger reads
  `sportsbook_devigger.aspx?autofill=1&LegOdds=<this side>/<other side>&FinalOdds=<price>` and
  computes on load (worst case by default; the `DevigMethod` parameter is ignored). Four lines
  checked against Vigilant's engine, all equal to CNO's printed tenth of a percent: +120/−140 at
  +130 → 43.4% fair, −0.1% EV; +330/−450 at +400 → 19.9%, −0.4%; −110/−110 at +105 → 50.0%,
  +2.5%; +250/−300 at +275 → 26.4%, −1.1% (`CrossCheckTest`).
- **API:** the "Devigger API" (api.crazyninjaodds.com) is marked work-in-progress and only devigs;
  "APIs for providing sportsbook odds" are "coming in the future". CNO's odds come from
  **OddsBlaze**, with Novig among its sources. *Corrected 2026-09-26 (§18.2): OddsBlaze's own
  pricing starts at **$299/mo**, not the $29/mo a competitor's page claimed, and CNO **does** have
  terms of service (a PDF linked from its footer) that forbid bots, scripts and scrapers.*
  robots.txt allows all with `Crawl-delay: 30`.

### 16.2 OddsAssist Pro "+EV Bets": some real edges, but the headline numbers are longshot noise

- Free; 3 rows readable without an account, the rest blurred behind a free sign-up.
- **Default (all books) top rows, none of them Novig:** Missouri State ML **+4900** at OG.com vs
  no-vig +2736 → "76.32%"; Illinois ML **+2400** at Kalshi vs +1683 → "40.24%"; Robert Morris ML
  **+3000** at ESPN Bet vs +2314 → "28.41%". Blurred rows: 14%–27% on +862 to +2736 lines. CNO
  had the same Illinois game at 5.3% on a *spread* (Bally Bet, 12 books): the big numbers are the
  favorite–longshot problem from §8.1 (thin longshot books, simple devig), not money on the table.
- **Its "Pinnacle +EV" page** (`/advantages/plus-ev-pinnacle`, Pinnacle alone as the truth) looks
  sane: Browns ML +133 at Kalshi and OG.com vs +122 → 4.9%; JMU/ODU Over 44.5 +104 vs −106 → 4.83%;
  others 2.7%–4.5%. Whether exchange fees are netted isn't stated; Kalshi's taker fee
  (0.07 × P × (1 − P) per contract, about 1.7¢ at +133) would eat most of that 4.9%.
- Devig method still undisclosed (§8.1). Novig is in its book list, and §8.1's 2026-09-20
  hands-on found Novig rows (e.g. Dolphins ML +809, "18.73%"): also longshots.
- **Terms of service:** "Use automated tools or bots without our permission" and "scrape, or
  reverse-engineer our systems" are prohibited; content is "for personal use only". So it can't be
  a data source for the app.

### 16.3 Can they be incorporated? What was taken instead (v0.11.0)

Neither can be a live feed inside Vigilant: OddsAssist forbids it, CNO has no odds API (its odds
are OddsBlaze's), and scraping either would be fragile and only as fresh as their servers. Vigilant
already reads Novig's own book directly, which is fresher than either site's copy of it. What
they do better was adopted:

1. **Outlier guard** (CNO's rule): with 3+ books behind a fair price, each side uses the lower of
   the books' mean and median. One stale book can pull a mean; it can't pull a median. On by
   default (`FairSettings.outlierGuard`, `FairValueTest`).
2. **Longest-odds cap** (both sites' biggest "edges" are longshots): the feed hides prices longer
   than +1000 by default; +300/+500/+2000/Any in Settings (`ScanSettings.maxOdds`).
3. **Recheck**: re-read only the feed's Novig books (≤40, about 7 s, no fair-odds calls) or one
   bet's, right before betting. Cards say "old price" past 10 minutes and the banner offers the
   recheck (`Scanner.recheck`, `ScannerTest`).
4. **Maker bid** (§16.4) in the bet sheet.
5. **"Double-check on CrazyNinjaOdds"** in the bet sheet: opens CNO's devigger prefilled with the
   reference line (Pinnacle when it's in the line) and Novig's price, for an independent second
   opinion in one tap (`CrossCheck`, `ResearchFeaturesTest`).

Not adopted: liquidity-weighted consensus (Vigilant's sharp/average blend already weights
Pinnacle and the exchanges; per-book limits aren't published), OddsBlaze (*$299/mo first-hand,
§18.2; the rest of this sentence assumed $29*: a 2-minute throttle at
$29 is slower than Vigilant's own reads; worth a look only as a cheaper prop-odds source than
The Odds API's credits, if its pricing checks out first-hand).

### 16.4 Why a maker bid matters on Novig

Both tools only ever say "take this price". On an exchange you can also post your own bid and wait,
and Novig's makers pay no fee (NOVIG_API.md §8). With the take side holding $5–$15 at +EV prices
(§16.1), the bet sheet now shows the highest price on Novig's grid that still clears the EV target
(at least 2%: a resting order tends to fill when the line moves against it) and the current best
bid on that side. It's shown only when that bid would be cheaper than taking now.

### 16.5 Also found in this pass (full test, 2026-09-26)

- **Bug, fixed:** a metered source (pinnapi's 100/day, The Odds API) that refused on the first
  league skipped the rest, leaving their old snapshots in place, and the scan's final result priced
  from them, however old (a two-hour-old Pinnacle line in the test). The final result and every
  re-price now use only fair odds young enough to bet on, like the mid-scan results already did.
- Tracker averages (EV, CLV, beat-the-close) no longer count voided bets.

## 17. Seeing scans while Novig is open: picture-in-picture vs. overlay vs. split screen (2026-09-26)

Tj asked for "a floating widget … or a picture in picture type view so I can see the scans while
I have novig open". Android offers four ways; what each costs:

| Option | Permission | Interactive? | Size | Notes |
|---|---|---|---|---|
| **Picture-in-picture (built, v0.12.0)** | None (on by default per app; user can switch it off in Special app access) | No touches inside; up to 3 buttons when tapped, tap-to-expand | Starts small (a 3:2 window is roughly 180×120dp on a phone); pinch or double-tap to enlarge | System keeps it above any app, handles drag/resize/close. Android 12+ "auto-enter" shrinks the app on the way out. No extra service or battery cost: the scan's own foreground service already runs. |
| Draggable overlay ("chat head") | "Display over other apps" (a trip to Settings); Android 14+ also wants a `specialUse` foreground service to keep it up | Yes: scroll, tap a bet | Anything we draw | Most flexible, most code. Apps that set `filterTouchesWhenObscured` ignore taps under an overlay; unknown whether Novig does. Worth building only if the PiP window proves too small. |
| Split screen | None, nothing to build | Both apps fully | Half the screen each | Works today: Recents → Vigilant's icon → Split screen. `resizeableActivity` is now explicit and config changes don't recreate the screen. |
| Notification / bubbles | Notifications | Pull-down only | Shade | Bubbles are for conversations; a richer "scan done" notification already exists. |

The mini window shows scan progress and the feed's top bets (EV, selection, market and game,
Novig price in American odds; an old price is in the warning color), as many as fit, with a
"1–3/9" page label. Its buttons: **Scan**, **Recheck** (the feed's Novig prices, seconds) and
**Next** (next page). It opens when Tj leaves Vigilant with a scan running or bets on the feed,
from the button next to Scan, and from a bet's "Open Novig" (which now opens Novig's app,
`us.novig.app`, when installed). Settings › Mini window turns the automatic part off. There is no
device or emulator in the dev container: the window's content is screenshot-tested at PiP sizes,
the Android side (auto-enter, buttons) is unit-tested for the parameters it hands Android, and the
real behavior needs Tj's phone to confirm.
