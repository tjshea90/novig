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
  a 50¢ price — about 1.5% of stake at even money ($0.0075 on a $0.50 stake;
  corrected 2026-09-27, the app's math always used cost = price + fee), less at extreme
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

## 18. CrazyNinjaOdds' scanned odds inside Vigilant and the mini window (2026-09-26 ~15:35–16:00Z)

Tj: "I like [crazyninjaodds.com/site/tools/positive-ev.aspx] for positive EV odds when I choose
novig and a couple filters. Consider all possible ways to make this site's scanned odds display in
this app, especially in the floating widget." Checked with curl and Python in the dev container
(the page twice and its table once, all before the terms were found; after that only CNO's
information pages: API, Discord, add-ons, OddsBlaze), plus CNO's terms PDF and OddsBlaze's docs
and JS bundle.

### 18.1 How the page works (verified)

- **Filters travel in the URL.** CNO's "Shared View → Copy Link" builds
  `positive-ev.aspx?site_id=17&ev_min=…&odds_min=…&odds_max=…&liq_min=…&books_min=…&sides_min=…&main=…&live=…&sport=…&league=…&starts_within_h=…&limit=…`
  (`site_id=17` is Novig; the devig method is *not* in the link, it stays the page default,
  Liquidity-Weighted worst case). A plain GET with `?site_id=17` comes back with Novig selected.
- **The table is not in that page.** It arrives by a second request: an ASP.NET AJAX timer
  (`TimerAjaxDelayedLoad`, 1 ms) posts the whole form back (`__VIEWSTATE`, `__EVENTVALIDATION`,
  `__ASYNCPOST=true`, header `X-MicrosoftAjax: Delta=true`) and gets a "delta" reply
  (`length|type|id|content|` records; lengths count UTF-16 units including `\r\n`). ~1 s and ~75 KB
  each way.
- **What a row holds** (`GridView1`, 100 rows at the default limit, all Novig with `site_id=17`):
  EV % (LW worst case), start time (UTC), sport, league, event (link to CNO's game page with
  CNO's game/market/side ids), market ("Player Receptions", "Moneyline 3-way", …), bet name
  ("Brock Bowers Under 4.5"), Novig odds **with dollars available** ("+100 ($109)"), book (link
  to CNO's deeplink page, which asks for a consent tick and then opens the book with the referrer
  hidden), fair odds, number of books, and `data-fairpercentage` (fair probability, 15 digits).
- **Snapshot, Novig only, `books_min=3&sides_min=2`, 15:40Z:** 100 rows from 11.6% down; the top
  ones: Miguel Rojas O0.5 RBI +335 ($4) vs fair +290, 11.59%; Pittsburgh −63.5 +344 ($8), 9.50%;
  Javonte Williams O19.5 rec yds +156 ($1), 7.45%; Baker Mayfield longest pass O35.5 +117 ($19),
  7.21%. Rows with real size exist too: Brock Bowers U4.5 rec +100 ($109) vs −108, 13 books, 4.05%;
  Geraldo Perdomo U0.5 hits +167 ($203), 11 books, 3.81%; Amon-Ra St. Brown U0.5 TD +106 ($226),
  3.31%. Mostly player props, which is where Vigilant's own coverage is thinnest (§14).
- "Last Updated: 41 seconds ago": CNO refreshes its odds about once a minute server-side. The page
  does not refresh itself; the user presses Refresh.

### 18.2 What CNO's terms and CNO itself say

- **Terms of service** (`/files/terms-of-service.pdf`, Becker Games LLC, updated 2022-09-01; §16.1
  wrongly said there were none): §2 grants "a limited license to access and use the Site and to
  download or print a copy of any portion of the Content … solely for your personal,
  non-commercial use". §3: you "will not access the Site through automated or non-human means,
  whether through a bot, script, or otherwise". §5 forbids "systematically retriev[ing] data … to
  create or compile … a collection", "unauthorized framing", "any automated use of the system,
  such as … data mining, robots, or similar data gathering and extraction tools", and "except as
  may be the result of standard search engine or Internet browser usage, use, launch, develop, or
  distribute any automated system, including … any spider, robot, cheat utility, scraper, or
  offline reader that accesses the Site". §12: they may block IP addresses without notice.
  Contact: mike@crazyninjaodds.com.
- **But CNO features browser add-ons that act on this exact page** (`/site/tools/community-addons.aspx`):
  "CNO Parlay Buddy" (Chrome) "adds a small window with the ability to select and devig multiple
  legs on the CNO +EV page"; "Injury Guard" fills the page's filters automatically. "If you've built
  something you'd like featured here, reach out to me via the CNO Discord." So the owner welcomes
  user-side tools on the page someone has open; the terms' wording still covers anything
  automated.
- **API page:** the Devigger API is work in progress; odds APIs are "coming in the future"; "CrazyNinjaOdds
  uses OddsBlaze API for much of its Odds. You can develop your own tools with OddsBlaze API!"
- **OddsBlaze, first-hand** (docs.oddsblaze.com and the pricing data in oddsblaze.com's JS bundle):
  Plan 1 **$299/mo** (300 requests/min), Plan 2 $999/mo; free trial. Novig is one of its 24
  books. Pull API is one request per book per league (`odds.oddsblaze.com/?key=…&sportsbook=novig&league=nfl`),
  plus a push feed. Ten times Tj's $30 budget, so rebuilding CNO's scan from CNO's own source is out.
- **Discord** (5,000+ members): "Get pinged when a verified user posts a +EV play … for a state or
  sportsbook". Those are people's posted plays, not the scanner table.

### 18.3 Every way to get CNO's rows onto the screen, and what each costs

| # | Way | What Tj sees | Terms | Work | Verdict |
|---|---|---|---|---|---|
| 1 | **Split screen**: Chrome on his Shared View + Novig | The real page, half screen | Clean (it's a browser) | None | Works today |
| 2 | Freeform/floating window of Chrome (Android developer option "Enable freeform windows"; some OEM skins) | The real page in a floating window | Clean | None | Unverified on the Moto G; worth one try on the phone |
| 3 | **In-app CNO tab (WebView) + mirror in the mini window**: Tj opens his Shared View inside Vigilant; every load/refresh is his tap (in the tab or the mini window's Refresh button); Vigilant reads the rows already on the page and shows them in the mini window and feed | CNO's rows in Vigilant's own layout, in the floating window, with EV, price, $ available, fair, books, start time, age | Grey: like CNO's featured Chrome add-ons, no background access, but the terms' words ("extraction tools", "framing") still cover it | Medium (WebView kept alive under the PiP window, a JS reader, tests of the reader in Chromium on the saved page) | **Best without permission**, if Tj accepts the grey |
| 4 | **Background reader** (Vigilant does the GET + postback itself every ≥60 s while the feed or mini window is on screen, from Tj's Shared View link; stops in the background) | Same as 3, refreshing by itself; can alert on new rows | **Breaks §3/§5** unless CNO says yes in writing; risk is an IP block, which would also cut Tj's own browser access | Medium; the protocol is fully worked out above | **Best with Mike's written OK** |
| 5 | Share/copy rows from Chrome into Vigilant (allowed by §2's personal-copy licence) | Pinned rows in the mini window; Vigilant rechecks each price on Novig itself | Clean | Medium; mobile Chrome's text from a collapsed table is unknown without a device | Clunky for prices that move each minute |
| 6 | OddsBlaze direct, CNO's method in Vigilant | Vigilant's own rows, 24 books | Clean | Large | $299/mo: out |
| 7 | Discord pings → Vigilant via a notification listener | Posted plays, not the scan | Clean for CNO | Medium | Not what Tj asked for |
| 8 | Show the web page itself in the picture-in-picture window | CNO's phone layout collapses every column but EV% | As 3 | Small | Unreadable at PiP size |
| 9 | CNO API | — | — | — | Doesn't exist for odds yet |

For the display side, the mini window from §17 is the floating widget in every option; the
"display over other apps" overlay (§17) stays the upgrade if the PiP window proves too small, and
works the same for CNO rows as for Vigilant's own.

### 18.4 Recommendation

1. **Ask Mike** (CNO Discord or mike@crazyninjaodds.com) for written OK to read one Shared View
   once a minute, only while the app is on screen, for one person's own use. The add-ons page
   and his stated aim ("fuel cheaper alternatives to the expensive +EV sites") make a yes
   plausible. With a yes, build 4: it is the only way the floating window keeps itself current.
2. Until then, the choice is Tj's: **3** (user-driven, grey) or **1/2** (clean, nothing to build).
3. Either way, Vigilant should keep its own scan: CNO's copy of Novig is up to a minute old and
   its sizes ($1–$226) are what was on Novig at CNO's last refresh. A CNO row shown in Vigilant
   should say how old it is, and Vigilant's own Recheck (Novig's live book) is the check before
   betting.

### 18.5 What was built (v0.13.0), after Tj's answer

Tj, asked which way: "Whatever the best way is, disregarding the terms of service. I am friends
with the owner." So way 4, the self-refreshing reader:

- **`data/cno/`**: `CnoView` (his Shared View link, cleaned; Novig added when no book is set;
  the filters read back in words), `CnoPage` (form fields as a browser posts them, the delta
  format, the table found by column names), `CnoClient` (page + the loader timer's postback on the
  first read; then the Refresh button's postback with the state the last reply handed back, so
  one request per refresh; fresh page load if that fails or the session is 15 min idle; 429/503
  and 403 become a pause), `CnoFeed` (no read within 30 s of the last, taps included; timer only
  while watched; 2-minute back-off after an error; last list cached in `cno.json`, excluded from
  backup).
- **Verified live** (2026-09-26 ~17:05Z, `LiveCnoSmokeTest`): 100 Novig rows on the first read
  (CNO's data 29 s old), and 100 again on the refresh 31 s later with 1 request (2 s old).
- **In the app**: a CNO tab (rows with EV, price, dollars available, fair odds, books, a ¼-Kelly
  stake from CNO's fair probability capped at the dollars available; a sheet with Open Novig and
  CNO's every-book page), and the mini window listing both Vigilant's and CNO's rows by EV (CNO's
  tagged), or either alone; with CNO alone its buttons are Refresh and Next. It reads only while
  Vigilant is started, which includes the mini window over Novig; closing both stops it.
- **Not done:** matching a CNO row to its Novig market to recheck the live price from Novig
  itself (CNO's price is up to ~1 minute + the refresh interval old). The row's age is shown and
  goes amber after 5 minutes; Novig's own screen is the check before betting.


## 19. Making the CNO scanner accurate, standalone and fast (2026-09-26 ~18:20–18:45Z)

Tj: make sure CNO's rows are really +EV (no 1–2-book markets), worst-case devig, an odds cap of
+150, a CNO-only mode with the rest of the app asleep, 5 s / 15 s / real-time refresh, and every
book's odds for a tapped bet, in the widget too. Checked live (about 20 requests):

- **CNO's EV is its fair odds against the Novig price.** Every row checks out: EV = fair
  probability × decimal price − 1 (Justin Jefferson U69.5 +120 vs fair +109 → 0.4785 × 2.20 − 1 =
  5.26%, CNO 5.25%). CNO's fair is devigged from the other books, so the question is only how
  trustworthy that consensus is.
- **Devig choices** (CNO FAQ): Liquidity-Weighted (weighted by liquidity and limits, as game time
  nears), Unweighted Market Consensus (plain average/median), and **Conservative = the worse of
  those two methods' worst cases** ("most cautious … can hide legitimately profitable edges").
  Worst-case = the longest fair value of multiplicative, additive/Shin and power. "Fair value is
  calculated from the worst-case between market average and median." CNO's default is LW-WC.
- **The form is the filter.** Posting the page's own form fields is honored: devig method 8
  turned the EV column into "C-WC EV%", `TextBoxMaximumOdds=+150` capped odds, minimum books 5,
  2 market sides, "Require a Complete Sportsbook", result count 200 → 139 rows, all Novig. So the
  app can set these itself, whatever the Shared View link says.
- **Thin markets are real.** The game page (`/site/browse/game.aspx?...&side_id=…`, loaded the same
  way: page + timer postback) has one row per side and a column per book (FD, DK, CZR, MGM, BR,
  BB, TSB, B365, FN, HR-*, FL, BV, BO, CS=Circa, PN=Pinnacle, PX=ProphetX, NV=Novig, KI=Kalshi,
  ST-*=Sporttrade, PP=PrizePicks). For Justin Jefferson U69.5 (CNO: 7 books) the Over was priced
  by 11 books (−120 to −135) but the **Under only by ProphetX −107, Kalshi −103 and Novig +120**:
  sportsbooks listed only the Over. Two-sided books are what make a devig honest, so the app counts
  them and redoes the worst-case devig itself on tap. The row whose `id` is the link's `side_id` is
  the bet; the other side sits next to it. "⚠️" on a fair price = devigged from a one-way line
  with an estimated juice (CNO's legend).
- **Update cadence.** "Last Updated" read every ~11 s for a minute: CNO's odds changed at :17,
  :50, :03, :29 (every 13–33 s, irregular). Reading faster than every few seconds gains nothing;
  "real time" can only mean "within a few seconds of CNO publishing". CNO's FAQ: a "Last Update"
  over 10 minutes means CNO has crashed. CNO's server also gives "1 minute and 6 seconds ago"
  (compound), which v0.13.0's parser missed.
- **Size.** No gzip (75 KB on the wire either way); a refresh reply is ~78 KB at 100 rows. At 5 s
  that is ~60 MB/hour, so the app asks CNO for fewer rows (the result count) to cut it.
- **Novig deeplink.** `deeplink.aspx?line_id=…` shows a consent page once; with the cookie it sets
  (`BetaDeepLinkIntro=Read=1`) it answers 103 bytes: `location.replace('novigapp://events/<Novig
  event id>/cno')`. So a tapped bet can open Novig's app on that game.

### 19.1 What was built (v0.14.0), and what the full test found

- **Scanner choice** (Both / Vigilant only / CNO only), **filters posted to CNO** (Conservative
  worst case, +150, 5+ books, 1%, 2 sides, complete book, 50 rows; the link's stricter values
  win), **the app's own checks** (`CnoChecks`), **every book per bet with Vigilant's verdict**
  (`CnoBooks`, CNO's game page; in the sheet and the widget's Books view), **refresh** real time /
  5 s / 15 s / 30 s / 1 min / taps, **Open in Novig** via the deeplink.
- **Live, after the build:** 48–50 Novig rows with the defaults, EV column "C-WC"; refresh = 1
  request; top bets' books: Tyquan Thornton U19.5 +108 → 5 two-sided books, Vigilant 3.6% vs
  CNO 3.5% (confirmed); Chuba Hubbard O14.5 +106 → 7 two-sided, 3.0% (confirmed). Refresh
  postbacks honor changed filters (devig, odds cap, rows switched on the same session).
- **Full-test fixes** (each with a test that fails on the old code): CNO labels Market consensus
  "UW-WC", not "UMC-WC" (a false "CNO used another devig" banner); the books check judged
  Novig's price even for another book's row; a stuck CNO was polled every 5 s on fixed
  intervals; the list was rewritten to disk on every read (now when it changes, or once a
  minute); the widget's Books view could stick on "Reading books…" after a refresh reordered the
  list (now pinned to its bet, and it asks for its own books); "1 minute and 6 seconds ago" read
  as unknown; the nav badge's clock recomposed every screen every 15 s; two Settings chip rows
  printed "$it" (caught by eye in the screenshots, now asserted).
- **Not done:** reading Novig's live book for a CNO bet (would need CNO's market matched to
  Novig's, and Tj asked for the rest of the app to stay asleep in CNO-only mode); tracking a CNO
  bet in the Tracker; a touchable overlay (the picture-in-picture window can't take taps on a
  row, so Books + Next stand in for tapping).

### 19.2 The mini window didn't show the pick (v0.14.1)

Tj's screenshot over his home screen: EV, market, game and price were there, but each pick's name
("Jahmyr Gibbs Over 4.5") was nearly black on the dark window. Picture-in-picture draws MiniFeed
straight into the theme with no Surface, so text without its own color fell back to black; the
screenshot tests drew it inside a Surface and never saw it. Fixed (MiniFeed has its own Surface,
the name is bold at full contrast) and pinned by a pixel-contrast test that renders the window
exactly as the app does. The name also used to be cut before its line ("Justin Jefferson Under …");
now the side and line always show: "J. Jefferson Under 69.5" when needed, and in the smallest window
the name on the first line with "Under 69.5" leading the second.


## 20. The CNO widget: scroll buttons, taps, placed bets, teams, agreement, and reads only while watched (2026-09-26 ~20:15–21:00Z)

Tj's six asks for the CNO widget: permanent up/down buttons instead of Next; nothing refreshing
once the scanner or the app is closed; the player's team next to the name ("d. Schultz (hou)");
a green check where several books agree on the fair value, if it doesn't slow scanning much; a
way to mark a bet placed so it never comes back after a refresh; and tapping a bet to land on
that exact bet in Novig. Checked first-hand from this container:

- **Picture-in-picture can't do four of them.** Android draws its own menu when a PiP window is
  tapped (at most ~3 `RemoteAction` buttons, shown only while tapped) and never passes touches
  to the app's content. So no permanent buttons, no tapping a row, no per-row "placed" button.
  §17 named the way out: an overlay window (`TYPE_APPLICATION_OVERLAY`, "Display over other
  apps"). With the permission granted, Android 10–15's background-activity-launch rules exempt
  the app while its overlay is visible, so the widget can open Novig or Vigilant, and the
  overlay keeps the process at perceptible priority without a foreground service. Built as
  `FloatingWidget` (a `ComposeView` in a `WindowManager` window with its own lifecycle owner;
  `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`, so Novig keeps the keyboard and the back button
  and gets every touch outside the widget). PiP stays the fallback.
- **Novig's app links** (NOVIG_API.md §9.1): app.novig.us is Novig's Expo app built for the web;
  its bundle holds the React Navigation linking config: `novigapp://events/:orderslip_outcomes?/
  :partner_id?/:amount?` opens Home with those outcomes in the bet slip. **CNO's Novig deeplink is
  exactly that, with an outcome id** (Dalton Schultz Over 5.5 receptions resolved to that outcome
  in Novig's catalog), so §19's "Open in Novig to the game" was really "to the bet". A desktop
  User-Agent gets `https://novig.com/events/<outcome>/cno` instead; the app rewrites that to
  `novigapp://` and pins the intent to `us.novig.app` when installed.
- **Player teams:** Novig's catalog has none (`"Jadarian Price 53.5 RUSHING_YARDS"`), nor has
  CNO's list or game page (its player dropdown is names only). ESPN's free site API does:
  `/apis/site/v2/sports/football/nfl/teams` (32 teams, `abbreviation`, `displayName`,
  `location`) and `/teams/{id}/roster` (~390 KB raw, **~30 KB gzipped**; football groups players
  by position under `items`, other sports list them flat). Two rosters per game, cached a day;
  names matched with `PlayerNames.same` (suffixes, short first names); no match, or a match on
  both teams, means no tag rather than a guess. College football's team list is 762 teams
  (~100 KB gzipped, a week's cache).
  **ESPN's CDN (Akamai) answers 403 "Access Denied" to any User-Agent naming Vigilant, and to a
  browser User-Agent sent by OkHttp; OkHttp's own (`okhttp/4.12.0`) goes through** (checked live,
  full test 2026-09-26), so the roster reads send no custom User-Agent. Live after the fix: 14
  games, 20 reads in the first pass, 39 of 40 player bets tagged (Dalton Schultz (HOU), Justin
  Jefferson (MIN), Gabby Williams (GS)); the 40th was past the first pass's 20-read cap, which
  now carries straight on instead of waiting 30 minutes.
- **Agreement:** CNO's list row has only its fair odds and a book count; each book's price is on
  the bet's game page (2 requests, ~65 KB). A lane reads the 12 best bets' pages one at a time,
  at least 2 s apart, never while a list read is running or CNO asked for a pause, each again
  after 5 minutes (2 after a failure): about 0.1 requests/s on top of the list, and the list
  itself is never delayed. ✓ = CONFIRMED: 3+ books price both sides, their worst-case consensus
  (lower of mean and median) makes Novig's price +EV, **and 3+ of them each do on their own**. A
  new SPLIT verdict covers "consensus +EV, only 1–2 books alone" (possible with 3–4 books). The
  check uses the list's Novig price when the list is newer than the game page.
- **When CNO is read** (`CnoWatch`): v0.13–0.14 read CNO whenever `MainActivity` was started, so
  the Tracker or Settings tab kept real-time reads going. Now only the CNO tab (started), the PiP
  window, or the floating widget (up, not a bubble, screen on and unlocked) count; the list, the
  green-check lane and the teams lane all stop together, in-flight requests cancelled. Closing
  Vigilant (back or swipe from Recents) destroys the activity, which takes the widget down.

**Not verified on a device** (there is none here): the overlay window itself, drag/resize, the
permission flow (Android may grey the switch out for a sideloaded app: App info › ⋮ › Allow
restricted settings), and where exactly Novig's app lands for `novigapp://events/<outcome>/cno`.

### 20.1 Resize, move, "CNO error" and the stuck refresh arrow (v0.15.1, Tj 2026-09-26 ~23:45Z)

- **The cut-off thing at the bottom right** was v0.15.0's resize grip, drawn inside the widget's
  12 dp rounded corner, which clipped it. The window is now the widget plus a 10 dp frame on
  every side, dark enough to see over any app, with a green handle drawn at each corner.
- **Resizing and moving** are handled by the window before the list sees a touch
  (`FloatingWidget.TouchFrame` → `WidgetGestures`): two fingers anywhere spread/pinch it (about
  its middle) and move it; one finger on a corner handle (a 44 dp square) resizes from that
  corner with the opposite one fixed; one finger on the frame or the (taller, 36 dp, with a grip)
  top bar drags it. A finger becomes a move only past the touch slop, so taps on top-bar buttons
  still tap. All in screen pixels (`MotionEvent.getRawX/Y`, API 29+): v0.15.0 dragged with the
  view's own coordinates, which shift as the window moves, so the widget lagged behind the finger.
- **"CNO error"**: a 3-minute soak of the app's own pattern against the real site (list in real
  time, books lane, teams lane: 106 requests) had no errors, and CNO serves HTTP/2. Two code
  faults fit what Tj saw: (1) a read cut short because nobody was looking any more (tab left,
  widget closed, phone locked) comes back from OkHttp as a network failure ("stream was reset:
  CANCEL"), which CnoFeed recorded as an error that stayed on screen until the next good read;
  now a cancelled read is a cancellation (`CnoWatchTest`, failed on the old code). (2) The list,
  books and roster bodies were read on the main thread (HTTP/2 made it block rather than throw);
  now `Call.awaitText()` reads them on OkHttp's thread. The widget's status also says what went
  wrong ("CNO offline, retrying", "CNO busy, waiting") instead of "CNO error".
- **The stuck arrow**: Material3 1.3.1's `PullToRefreshBox` parks its arrow at the threshold
  after a full pull and hides it only when `isRefreshing` goes true then false; Vigilant always
  passed false. `VigilantPullToRefresh` now holds it until the read (or scan start) it triggered
  has come and gone, or 1.2 s when CNO's pacing skipped the read. Same fix on the +EV and Games
  tabs. The CNO tab's status now says "Read 4s ago · odds 20s old · every 15 s" ("Reading now…"
  while it reads), so it's plain the list is being refreshed.

### 20.2 Reading CNO reliably: "unable to resolve", "timeout", taps that didn't open the bet slip (v0.15.2, Tj 2026-09-27 ~00:05–00:30Z)

Tj: "sometimes it says unable to resolve cno sometimes it says timeout" and "see if there is a way
to safely and repeatedly refresh cno odds without timeout or unable to resolve or any other
restrictions, whether that is using a specific dns server, or my nordvpn, or any cheap service".

**What CNO is.** One IIS server on Winhost shared hosting (162.250.75.106), HTTP/2, no CDN, DNS
at ns1–3.winhost.com with a one-hour TTL. robots.txt asks crawlers for 30 s between pages and
disallows the game pages. No rate-limit headers, no Cloudflare, no 429 seen: a 3-minute soak of
the app's own pattern from the container (106 requests) had zero errors. So CNO was not
throttling Tj; the two messages are the phone's network:

- **"Unable to resolve host"** is DNS failing on the phone before any request leaves it: a
  network switch (Wi-Fi ↔ mobile), the phone waking from sleep, or a VPN (his quick settings show
  one) reconnecting and swapping its DNS server. NordVPN's own DNS answers only while its tunnel
  is up.
- **"timeout"** is mostly a dead pooled connection: OkHttp keeps a connection open between reads;
  after the phone slept or changed networks (or the VPN reconnected) that socket is gone but not
  yet known to be, and the next read waits on it until the read timeout.
- **Taps that didn't open the bet slip**: the Novig link was asked of CNO on the tap, so a tap
  during either failure got no link and opened Novig's home.

**What the app now does (all on the phone, nothing to pay for or set up)** — `CnoNetwork`:
- DNS that doesn't give up (`RememberingDns`): the phone's DNS first; if it fails, DNS over HTTPS
  to Cloudflare (1.1.1.1) then Google (8.8.8.8), reached at their fixed addresses so they don't
  need DNS themselves (`DnsOverHttps`, only the name crazyninjaodds.com is sent); if that fails
  too, CNO's last good address (kept a day; it changes rarely).
- Connections idle 20 s are closed rather than reused, reads time out after 12 s, and a failed
  request is retried once at once on a fresh connection (the pool emptied first).
- One pace for every CNO request, whichever part of the app makes it (`CnoPace`, ≥1 s apart;
  the list itself stays ≥3 s apart), and the green-check and link lanes wait while the list is
  failing or CNO asked for a pause.
- Bet links read ahead of time for the listed bets (one small request every 3 s, kept on disk in
  `cno_links.json`: a line's link never changes), so a tap usually needs no network. A tap
  without one asks CNO for at most 5 s, then finds the bet in Novig's own public catalog
  (`NovigBetFinder`: the game by its teams and start, the outcome by player/team, line and side,
  exactly one match or nothing), else opens its game; it says so when it couldn't open the bet
  itself (`TapLink`, `TapLinkTest`).
- The status says which failure it is: "CNO lookup failed, retrying" (DNS), "CNO slow,
  retrying" (timeout), "CNO offline, retrying" (no connection).

**Recommendations for Tj (cost: none)**
1. **Private DNS** (Android Settings › Network & internet › Private DNS › Private DNS provider
   hostname: `one.one.one.one`, or `dns.google`). Makes the phone's own lookups steadier on any
   network. Free; the app no longer depends on it.
2. **NordVPN**: it doesn't help with CNO (CNO wasn't blocking anything), and a VPN reconnecting
   is itself a cause of both messages. Novig also checks location and refuses VPNs
   (NOVIG_API.md §4), so it should be off while betting anyway. If he wants it on for other
   apps: NordVPN › Settings › Split tunneling, and leave Vigilant and Novig out of the tunnel.
3. **Paid relays are not worth it now.** A Cloudflare Worker (free up to 100k requests a day) or a
   $4–6/month VPS could fetch CNO for the phone, but CNO would see the same load, it adds
   something that can break, and the in-app fixes above cover what went wrong. Revisit only if
   the status keeps showing "CNO slow" or "offline" on a good connection.
4. **Not done on purpose**: rotating proxies or IPs to read faster than CNO allows. CNO is a free
   site on one small shared server; the app already reads it more often than its robots.txt asks
   of crawlers, only while Tj is looking. If he wants more, the honest route is asking CNO's
   owner (donations page) for an allowed rate or a feed.

### 20.3 Bet slips without CNO, both scanners in the widget, CNO under rapid refreshing (v0.15.4, Tj 2026-09-27 ~01:18Z)

Tj: "make it so vigilant can open the bet in novig even if it can't reach cno servers"; "an
option to also use the regular scan in addition to cno and put all the results in the widget
together"; "consider ways to make cno respond even with high traffic and rapid refreshing. Is
there a workaround? Dns? Free or cheap service? Vpn? Use proxies to get around the limit, it is ok".

**Bet slips from Novig's own catalog (measured live).** `LiveNovigBetFinderTest` ran CNO's real
60-row list through `NovigBetFinder` (Novig's public catalog only, no CNO): 56 exact at first; the
4 misses were Novig naming CNO's "Passing Interceptions" `INTERCEPTIONS_THROWN` and soccer's 3-way
moneyline being a Yes/No market per team (`MONEYLINE_3_WAY_WIN`, and one `_DRAW`). Fixed: 60 of 60
exact, and each of the 60 was checked against CNO's own link: the same outcome every time, 0 wrong.
So a tap no longer needs CNO: links are looked up ahead of time from the catalog first (two public
Novig reads per game, kept 5 minutes, paced ≥350 ms, Novig's 429 Retry-After honored), CNO only for
a bet the catalog can't pin down; a tap without a known link asks CNO and the catalog at once and
takes the first exact answer (`TapLink`).

**CNO under rapid refreshing (measured live, `LiveCnoBurstTest`).** The list re-read once a second
for a minute (61 requests): 0 errors, 0 refusals (all HTTP 200), median 258 ms, p90 306 ms, 2.5 s
only for the first (session) read. CNO's replies are not compressed (~30 KB each; ~36 MB an hour
in real-time mode). **There is no rate limit to get around**, so proxies (or a VPN) would add a hop,
latency and a third party that sees the traffic, and fix nothing. What Tj saw ("unable to resolve",
"timeout") was the phone's network (§20.2), now absorbed by the backup DNS, fresh connections and
the retry. CNO's own data updates only every 13–33 s (longer when its updater lags): reading CNO
faster can't make its prices fresher.

**The workaround that does make prices fresher: Novig's own book.** `NovigLive` reads the order
books of the 10 best listed CNO bets on Novig every 15 s while the list is on screen (through the
scanner's paced public client; mostly "304 not modified"), and each CNO read is re-priced against
the books already read with no request. The price shown is Novig's now; the EV is CNO's fair
probability against it (Novig's fee on live games from each market's own fee object); "was +117"
flags a line that moved, an orange EV one now under the minimum. Measured live: 10 of 10 prices
identical to the Novig price CNO listed, dollars available matching within a few dollars (except
where the book changed since CNO's read). Vigilant's EV reads slightly lower than CNO's on the same
price (e.g. 3.52% vs 3.75%) because it uses Novig's exact price (0.475) where CNO's EV uses the
rounded American odds (+111): Vigilant's is the more exact figure. Switch: Settings › CNO › "Novig's
price now".

**Options weighed, costs (unchanged from §20.2 unless noted):** Private DNS (free, recommended);
NordVPN (no help; Novig refuses VPNs; split-tunnel Vigilant and Novig out if kept on); a relay
(Cloudflare Worker free tier / $4–6 VPS: no benefit without a limit to get around); proxies (no
limit to get around; free proxies are a security risk on a betting phone; residential ones cost
~$5–15/GB): not built.

## 21. Live (in-game) +EV on Novig: can it be found fast enough? (measured 2026-09-27 ~01:50–02:05Z)

Tj: "consider if it is possible and if there is an online feed fast enough to tell me positive EV
live bets on novig. I have to be able to bet these very fast because the odds change. Also, it has
to scan for live odds on games very fast to find positive EV live bets that are not based on stale
odds. If this can be done, make it. If not, tell me your findings."

**Measured from here, during Saturday night's games** (50 events live on Novig: MLB, NHL, MLS,
NCAAF; `research/live_leadlag.py`):
- **Novig's side is fast enough to read.** Live books over the public REST route take ~170 ms, are
  not cached at the edge ("Miss from cloudfront", no ETag), and two backends (separate `seq`
  ranges, never compare them) return the same book within ~1 s. Prices jump within a second of a
  play (HOU 0.675 → 0.61 in one second), and market makers pull their quotes around plays (the
  take price briefly goes wide, e.g. 0.795 or 0.895 against a 0.70 middle). With Tj's read key,
  `NovigStream` (the signed websocket, §6 of NOVIG_API.md) would push every change instead: the
  client is built and tested but not yet wired into the app (scans read books over REST, signed
  with the key when one is connected).
- **The free reference is the stale side.** Kalshi's live game markets (the only free live
  reference with liquidity; Polymarket had no live MLB game markets and its live NCAAF/NHL ones
  were settled or empty) were sampled against Novig once a second for 150 s on three live MLB
  games: Novig's price jumped 9, 7 and 2 times; Kalshi's 1, 2 and 0. Every Kalshi jump came after
  Novig had already moved. A scanner using Kalshi as "fair" would flag bets exactly when Kalshi is
  behind: stale-odds false positives, the thing Tj asked to avoid.
- **No edge after the live fee.** In 440 samples, Novig's take price never beat Kalshi's middle by
  1% after Novig's live taker fee (0.03 × P × (1 − P): ~1.5% of stake at even odds, ~1% at 70¢);
  the best was −0.2%.
- **CNO's live view** updates every 13–33 s (§18): far too slow for live prices.

**Paid feeds that could be fast enough (not bought):** Pinnacle is the sharpest live line and the
one Novig's makers most likely follow. Since Pinnacle closed its public API (July 2025), pinnapi
sells it: live odds and streaming only on paid plans ($99–$229/mo; Edge $149/mo), 15–40 ms quoted
from a Pinnacle change; the free tier (100 REST calls/day) has no live odds. Others: SportsGameOdds
websocket (~100 ms, from $49/mo), SharpAPI (SSE, ~89 ms p50), OpticOdds (enterprise, "high hundreds"
a month and up), Unabated ($3,000/mo for its consensus line). Even with Pinnacle live, the windows
where a Novig resting order is stale against Pinnacle last about the time Novig's makers take to
re-quote (their quotes are already pulled around plays): a second or two. A person seeing an alert,
opening Novig's bet slip and confirming takes several seconds, so most such bets would be gone or
re-priced; catching them reliably needs an automated taker (a trading key and a bot), which is a
different product with its own risks.

**Decision (reported to Tj, not built):** a live +EV scanner on free feeds would show almost no
bets, and the few it showed would come from the reference being behind Novig, i.e. stale odds. Not
built, so it can't mislead. What would change the answer: Tj buying Pinnacle live (pinnapi Edge,
$149/mo) for a measured trial: Vigilant would first log how often and how long Novig trails Pinnacle
by more than the fee, before any alerts are trusted. Pregame +EV (fee-free on Novig) remains where
the edge is, and v0.15.4's "Novig's price now" keeps those prices current.

## 22. Every free odds API / sportsbook feed, re-surveyed 2026-09-27 (~06:15–06:50Z, Tj: "better accuracy or faster scanning")

**Why:** Tj asked for every odds API and sportsbook with a free API, and whether any would make
Vigilant more accurate or faster. §11 covered the 2026-09-25 field (Polymarket, Kalshi, pinnapi,
The Odds API, OddsPapi, SharpAPI…). This pass looked for anything new since, measured from this
container where possible ("docs" = read from the provider's own docs the same day, not measured).

### 22.1 What changed the picture

**1. Pinnacle's player props, free: PinnWire** (pinnwire.com; an independent Pinnacle feed, same
`/kit/v1/markets` JSON as pinnapi, which the app already parses).
- Free key: 100 requests/day, 20/min, no card, no expiry (docs). Every plan, the free one included,
  has "props & specials" (docs); pinnapi's own trial does not (its docs: "no special-market access
  on trial"). A public `key=demo` exists (one shared bucket for everyone: 10/min, 50/day; docs), so
  it is fine for trying the feed, not for the app.
- **Measured** with `key=demo` (2026-09-27 06:18–06:40Z): NFL prematch board, 15 games, 63 KB, 0.9 s.
  With `include_specials=1`: 1,041 rows in ONE request (692 KB): **768 Pinnacle player props** + 240
  game props, as child rows (`parent_id` = the game) with `special` = "DJ Moore Total Receptions",
  `special_units` = "Receptions", `special_markets.num_0[0].prices` = Over/Under, `points`, decimal
  `price`, `max_risk` ($250–$500 on props). NFL units: Receptions 158, Receiving Yards 157,
  Touchdowns 148 (Over 0.5 = anytime TD), Rushing Yards 81, Rush Attempts 45, Passing Yards 30,
  Touchdown Passes 30, Pass Completions 30, Pass Attempts 30, Field Goals 30, Interceptions 29.
  MLB (11 games): Home Runs 58, Bases (= total bases) 20, Strikeouts (pitcher) 4. WNBA: Points,
  Rebounds, Assists, Threes Made. NFL + NCAA share sport 5, so both cost one request.
- Why it matters: Pinnacle is the sharpest book on player props, and until now Vigilant priced props
  only from Kalshi (NFL/MLB/WNBA ladders) and The Odds API's sportsbooks (1 credit per prop type per
  game out of 500 a month). Pinnacle's props need **one request per sport per scan**.
- Auth: header `x-api-key` (pinnapi uses `x-portal-apikey`) or `?key=`. 429 body is pinnapi's
  (`window`, `retry_after_ms`). `since=<last>` returns only events changed since the last call.

**2. Thirty books in one free call: PropLine** (prop-line.com).
- Free: 1,000 requests/day (UTC reset), burst 10, 5/s, 20 in flight; no card (docs). Quota headers
  `X-Daily-Limit/Used/Remaining/Reset` on every reply. Free = live odds + scores; +EV, history and
  prop grading are paid ($9/mo and up).
- Books (**measured** `/v1/freshness`, no key, 06:17Z): Pinnacle, DraftKings, FanDuel, BetMGM,
  Fanatics, BetRivers, Hard Rock, Bovada, BetOnline, LowVig, BetUS, Unibet, 1xBet, Marathon, Fliff,
  Betway, TAB, ReBet, Courtside, Kalshi, Polymarket (+US), ProphetX, Smarkets, Matchbook, **Novig**,
  and DFS books. Game lines were 0–42 s old across every book (Pinnacle 13 s, Novig 10 s).
- One call = a whole league's game lines for every book (`/v1/sports/{sport}/odds?markets=h2h,
  spreads,totals`); props are one call per game (`/events/{id}/odds?markets=…`). Response is the-odds-
  api's format (American prices; team totals ride `totals` with a `team` field; each outcome carries
  `last_seen_at` — older than its market's `last_update` means withdrawn; `suspended_at` on pulled
  markets). the-odds-api sport keys are accepted as aliases.
- The published demo key had already hit its 1,000/day cap (shared) at 06:17Z, so **no board was read
  live**; the parser follows the documented schema (openapi.json) and needs Tj's own free key.
- Why it matters: The Odds API's free 500 credits a month buy about 5 full refreshes a day of 3 markets
  in 3 leagues and almost no props. PropLine's free 1,000 a day covers ~40 scans a day with every book
  plus props for ~20 games a scan, so the market-average fair line (and CNO-style "books agree" checks)
  gets many more books for free.

### 22.2 Everything else looked at

| Source | Free? (measured / docs) | Books / data | Verdict |
|---|---|---|---|
| Pinnacle's own website API (`guest.api.arcadia.pinnacle.com`) | Answers with no signup (measured: NFL 1,040 matchups incl. 1,025 specials, 0.3 s) | Pinnacle itself | Not built: private website backend, not a published API (same rule as §11's "no sportsbook web endpoints"); PinnWire gives the same prices legitimately |
| Action Network (`api.actionnetwork.com/web/v2/scoreboard`) | Answers with no key (measured: NFL 16 games, 7 books, 576 KB, 1 s) | DraftKings, FanDuel, BetMGM, Caesars, BetRivers, consensus, open; no timestamps | Not built: unofficial, soft books only, no update times; PropLine covers the same books officially |
| ESPN scoreboard odds | Free, no key (measured) | DraftKings only | Not useful for fair odds |
| SX Bet (`api.sx.bet`) | Free, no key (measured: NFL markets listed) | Crypto exchange | Not built: order read failed (400), liquidity unproven |
| Odds-API.io | Free 100/hour, **new free keys paused**; 2 soft books; sharp books paid (docs) | — | No |
| SportsGameOdds | Free 2,500 objects/mo, **10-minute delay**, 9 books (docs) | — | No (delay) |
| The Rundown | Free 20k data points/day, **5-minute delay**, DK/FD/MGM only, no props (docs) | — | No (delay, soft books) |
| OddsPapi | Free 250 requests/**month** (docs, §11) | 350 books incl. Pinnacle | Spot checks only |
| Owls Insight | No free plan (3-day trial); $9.99 plan has no sharp books (docs) | — | No |
| BoltOdds | 7-day trial only; $99+/mo (docs) | — | No |
| Unabated, OpticOdds, OddsJam, OddsBlaze, LSports, Sportradar | Sales-gated or $99–$3,000+/mo | — | No |
| ProphetX API | Partners only, no self-serve key (docs) | — | No (PropLine carries its prices) |
| Betfair / Smarkets / Matchbook APIs | Need a funded account; Betfair not available in the US | Exchanges | No (PropLine carries Smarkets/Matchbook best back prices) |
| PinnOdds, pinnapi | Free trial 100/day, game lines (pinnapi: no props on trial) | Pinnacle | pinnapi kept as the fallback Pinnacle key |

### 22.3 Faster scanning

Novig's own book reads (4 a second on public routes, 16/s with a Novig key) are still the long pole
of a Vigilant scan, not the fair-odds calls: every source above answers a whole league in one call
(PinnWire, PropLine, Polymarket, Kalshi). No free feed is fast enough for live (in-game) betting
(§21 still holds: Novig's live book leads the free feeds). What speeds a scan up is spending Novig
reads only where an edge is plausible, which more (and sharper) fair lines do: lines no source quotes
are never read, and props get Pinnacle's line instead of waiting on scarce Odds API credits.

### 22.4 Built (v0.16.0) — see TASKS.md P3 for the tests
1. **Pinnacle through PinnWire** (keys in Settings → Fair-odds sources → Pinnacle): game lines as before
   plus Pinnacle player props, one request per sport; pinnapi keys stay as the fallback.
2. **PropLine** (its own switch and keys): every sportsbook's game lines per league and props per game,
   filtered to the reference books picked in Settings, exchanges and DFS books left out (the app
   reads Polymarket and Kalshi directly, and Novig is the thing being priced).

### 22.5 Also found in this full test (2026-09-27)
- **Settling bets:** Novig's public catalog drops a game and its markets a few hours after it ends
  (404 by id; not listed under any status), so v0.15.6's auto-settle could never settle anything.
  Free score feeds do it instead: ESPN's scoreboard (`site.api.espn.com/apis/site/v2/sports/{sport}/
  {league}/scoreboard?dates=YYYYMMDD`, college with `groups=80`/`50`) and box score (`/summary?event=`)
  for football, basketball and hockey; MLB's Stats API (`statsapi.mlb.com/api/v1/schedule?sportId=1&
  date=&hydrate=linescore`, `/game/{pk}/boxscore`) for baseball, because ESPN's MLB box score has no
  per-player doubles/triples (total bases) while MLB's has totalBases, stolenBases and pitcher outs.
  Live check: 8 real bets from 2026-09-24/26 games (moneyline, F5 total, run line, pitcher Ks, total
  bases, NFL spread, rushing yards, receptions) all settled correctly in 4 requests.
- **Scan speed on the phone:** matching every Novig game against every feed's games re-tokenized the
  team names on each comparison (Unicode normalize + regex). Measured on a desktop JVM: 165 ms per plan
  for 61 college games x 4 feeds, and a scan plans again each time a feed answers. Caching each name's
  tokens: 16 ms per plan (0.14 µs per comparison, was 3.3 µs). A Moto G is several times slower, so
  this was seconds of CPU per busy scan.
- **Not built, for Tj to decide:** PinnWire's `since=<last>` returns only changed games, which would cut
  the ~700 KB NFL props download per scan to a few KB (same request count); PropLine carries Novig's own
  game-line prices (10 s old), which could order Vigilant's Novig reads so likely edges are read first.

## 23. Which API first when two carry the same books (2026-09-27 ~16:00–16:40Z, Tj: "If apis overlap odds from the same sports books, use the best/fastest API first and the others as automatic fallbacks")

### 23.1 Every odds API a scan reads, re-checked against the providers' docs today

| Source | Books | Free allowance | Cost per scan | Freshness / speed | Notes |
|---|---|---|---|---|---|
| Novig API (NOVIG_API.md) | Novig (the book priced) | public 4 req/s; 16/s with a key | 1 per market read (≤300) | the book itself | the only source for Novig's depth; no change |
| PinnWire → pinnapi (one `PinnapiClient`, PinnWire first) | Pinnacle: game lines, every alt line, 1st half / F5, team totals, player props (PinnWire only) | 100/day, 20/min per key (llms-full.txt, 2026-09-23 rev.) | 1 per sport (NFL + NCAAF share) | real-time feed ("1–19 ms path latency", REST snapshot at request time) | best Pinnacle source: alt lines and props in one call |
| PropLine | 19 sportsbooks incl. Pinnacle, DK, FD, MGM, Fanatics, BetRivers, Hard Rock, Bovada, BetOnline, LowVig, BetUS, Unibet…; props per game | 1,000/day (UTC), burst 10, ≤20 in flight (llms.txt) | 1 per league + 1 per game for props (≤12 games) | measured `/v1/freshness` 16:20Z: DK/FD/Fanatics/Pinnacle/BetRivers 0–1 s, MGM/Hard Rock 12 s, BetOnline/LowVig 28 s; docs: DK/FD/Fanatics pushed in ~1 s, others polled 60–90 s pregame | carries Pinnacle props too (96,925 Pinnacle markets listed) |
| The Odds API | ≤10 picked books (Pinnacle, DK, FD, MGM, Caesars, ESPN BET, …) | 500 credits/month | 3 credits per league (h2h+spreads+totals); props 1 credit per prop type per game | docs don't publish update intervals | cost rules unchanged (guide v4, today): markets × regions, ≤10 books = 1 region, empty = free, `/events` free |
| Polymarket (Gamma) | Polymarket | free, 300 req/10 s, over-limit queued | 1–2 pages per league | measured 0.39 s for an NFL page (646 KB) | exchange: bid/ask, spread filter |
| Kalshi | Kalshi | free; anonymous reads throttle near 3/s (§11) | 1 per series (NFL: 19 series incl. props) at 2/s | measured 0.4–1.3 s per series | exchange; the only free prop ladders besides PinnWire |

### 23.2 Where two APIs return the same book, and what goes first

1. **Pinnacle** — PinnWire first (real-time, every alt line, halves, team totals and props in one request per
   sport), then pinnapi (same format, no props on trial), then PropLine's copy of Pinnacle (free inside the
   PropLine call already made), then The Odds API's copy. The fair-odds merge already prices the first
   source's copy of a book per line (`Scanner.SOURCE_ORDER`, `Pricing` keeps one quote per book), so this
   chain needs no extra requests: PropLine's and The Odds API's Pinnacle quotes only price a line PinnWire
   didn't give.
2. **Sportsbook game lines (DK, FD, MGM, BetOnline, …)** — PropLine first: one request per league from
   1,000 a day, fresh within seconds for the big books. The Odds API costs 3 of 500 monthly credits for the
   same books, so it becomes the automatic fallback, called for a league only when PropLine gave nothing for
   it (no key, daily limit, error, league missing), when Novig lists games PropLine's board doesn't have and
   The Odds API's free game list does, or when a book picked as sharp in Settings is one only The Odds API
   carries. What that gives up when PropLine answers: Caesars and ESPN BET (soft books PropLine doesn't
   carry) drop out of the market average.
3. **Sportsbook player props** — PropLine props first (1 request per game, up to 12 games a scan), then The
   Odds API's props (1 credit per prop type per game) only for the games and prop types PropLine didn't
   price this scan.
4. **Exchanges** — Polymarket and Kalshi straight from their own APIs (bid/ask with the spread filter).
   PropLine also relays both, but as one-sided prices without the spread check, so its copies stay unused
   (`PropLineClient.EXCLUDED`).
5. **Novig** — its own API only. PropLine relays Novig's game lines ~20 s old; that could order which Novig
   books a scan reads first, but it can't price anything. Left for Tj to decide.

### 23.3 Looked at and not changed
- **PinnWire `since=<last>`** (only games changed since the last call): same request count, far smaller
  download, but REST never says when a game or prop was removed (docs: "Only events changed after this
  timestamp"; deletions are signalled only on the paid WebSocket). A prop Pinnacle pulled would keep its old
  price in a merged board, and a stale Pinnacle prop against a moved Novig price is exactly a fake edge. Not
  built.
- **Compression**: pinnwire.com serves gzip, and OkHttp asks for it on every call already.
- **Kalshi pacing** (2 req/s): kept. It is ~10 s per NFL scan for 19 series, in parallel with everything
  else, and faster pacing drew 429s before (§11).
- **MLB scores fallback to ESPN**: ESPN's MLB box score lacks total bases (§22.5), and a failed MLB Stats API
  read already just waits for the next settle pass (every 3 h). Not built.

### 23.4 Built (v0.16.3)
- `ReferenceSource.fallbackFor` / `needed()`; `ScanContext.covered` (per Novig game: "MONEYLINE:0",
  "PROP:RECEPTIONS", …) and `firstAnswered`; `Scanner` starts first choices at once and runs a fallback
  after its first choice, with what that one gave (`Scanner.covering`). A fallback that stands by drops its
  own older answer, so its stale quotes never price beside the fresh ones.
- The Odds API game lines behind PropLine (`TheOddsApiClient.needed`: PropLine didn't answer the league;
  or a sharp-picked book only The Odds API carries; or Novig games PropLine lacks that The Odds API's free
  `/events` lists, re-used 5 min; an unreadable free list = stand by). The Odds API props behind PropLine
  props (`OddsApiPropsSource.allocate` skips game/stat pairs PropLine priced).
- Settings and the scan line say so ("Backup to PropLine", "The Odds API on standby").

### 23.5 Also found in this full test (2026-09-27 ~16:40Z)
- **Placed bets across a doubleheader or a series:** v0.16.2's "same bet" check allowed 12 h between
  start times for every sport, so a ✓ on a doubleheader's game 1 hid the same bet in game 2; and a mark
  with no start time matched the same pairing on any later day (a Mets–Nationals series). Now baseball
  allows 2 h (the feeds agree to the minute), other sports 12 h, and an unknown start only matches a mark
  from the last 24 h (`PlacedIndex`, league carried on marks and widget rows).
- **Rosters read for a CNO list nobody sees:** since the widget works in Vigilant-only mode, its "on
  screen" signal kept ESPN roster reads going for an old CNO list; now only with CNO's scanner on
  (`UiState.cnoTeamRows`).

### 23.6 PropLine's Novig prices order the Novig reads (v0.16.4, Tj: "use PropLine's Novig prices to order the reads, but if there is any failure or delay, make the app automatically fallback to the original novig read")
- `novig` rides in the same PropLine requests (league board and per-game props: no extra requests) and is
  parsed apart (`RefSnapshot.novig`), so it never prices a fair line and never counts as a fallback's
  coverage.
- The book pump builds a stand-in Novig book per planned line from those prices ("taking A at P" = a bid of
  1 − P on B), prices it with the normal `Pricing` against this scan's fair line, and reads the likeliest
  +EV lines first (`Scanner.preview`, `fetchOrder`). The feed is still priced only from Novig's own books.
- Fallback to the original order, automatically: no PropLine key or a failed call (no relay), an answer
  that isn't in yet (the pump never waits: the first reads go in the original order and the rest re-sort
  when it lands), a relay older than 3 minutes (`NOVIG_PREVIEW_MAX_AGE_MS`; props relays likewise), a line
  PropLine doesn't quote (last scan's EV, as before), or any error computing it.

## 24. No stale sportsbook odds in any comparison (2026-09-27 ~17:20Z, Tj: "The other sports books odds MUST be current or at most a few minutes old")

### 24.1 Audit (v0.16.3 code): every place another book's price can feed an EV, and how old it could be
1. **Re-use windows longer than a few minutes**: The Odds API game lines 15 min (Settings, up to 60), its props
   60 min (up to 4 h), PropLine props 10 min. Priced while `planFor(youngFairOnly)` allowed
   max(stale limit 30 min, re-use), so up to 30–60+ minutes.
2. **A failed call keeps the last snapshot** up to the 30-min stale limit, and it still priced.
3. **No per-quote age check at all.** A snapshot fetched now can carry a book's quote the feed itself says is
   old: The Odds API docs (guide v4, today): the market-level `last_update` "shows the last time our system saw
   odds for that market from the bookmaker … When a market is suspended or closed, its last_update stops
   advancing, and the market is removed from the API response after about 15 minutes" — so a pulled market
   priced for up to 15 minutes. PropLine sends each outcome's `last_seen_at` (and `last_change_at`); the app
   used the market's `last_update` and never checked either against the clock (PropLine's own "stale"
   threshold is 30 minutes, `/v1/freshness`).
4. **Recheck and re-pricing judged fair-odds age as of the last scan** (`fairAsOf = lastScanAtMs`): a Recheck
   45 minutes later priced Novig's fresh book against 45-minute-old fair lines.
5. **The feed, widget, mini window and Games tab keep showing a scan's EVs** with no fair-odds age limit (only
   Novig's own price is flagged old after 10 min).
6. **CNO**: its rows (CNO's EV from its books) stay listed until CNO's data is 10 min old ("stuck"), flagged
   "old" only after 5; the green "books agree" check used a CNO game page of any age (re-read every 10 min).
7. Not a bet decision, left as is: the Tracker's "now ±x% EV" (labelled with its age), CLV.

### 24.2 The rule (v0.16.4)
- One hard limit, `Freshness.MAX_QUOTE_AGE_MS` = **5 minutes**: a book's quote prices a fair line only if the
  feed saw it within the last 5 minutes (Pinnacle, Kalshi, Polymarket: when fetched; The Odds API: market
  `last_update`; PropLine: the outcomes' `last_seen_at`; never later than when Vigilant fetched it). No setting
  can raise it.
- Re-use windows are capped at 2 minutes (`Freshness.MAX_REUSE_MS`) so a re-used answer is still fresh
  through a scan.
- Every EV shown carries the time of its oldest quote; past 5 minutes it leaves the feed, widget, mini window
  and Games tab ("scan again"), and Recheck/re-pricing judge age as of now.
- CNO: rows hidden while CNO's odds are over 5 minutes old; the green check needs a book page under 5 minutes
  old (re-read every 4).

### 24.3 Built (v0.16.4)
- `data/scanner/Freshness` (5 min per quote, 2 min re-use). `Scanner` stamps each quote's "last seen" at
  fetch (the feed's own time, never later than the fetch; the fetch time when the feed doesn't say),
  prices only quotes seen within 5 minutes of the pricing moment (`planFor`), re-uses no answer past
  2 minutes, and judges Recheck / re-pricing as of now. PropLine quotes carry `last_seen_at` (older side).
- `Opportunity.fairAsOfMs` / `fairIsOld(now)`: `UiState.feedAt(now)` feeds the +EV tab, widget, mini window
  and tab badge; the Games tab shows "old" instead of a fair price/EV; an open bet sheet drops its EV with a
  warning; a card says "odds aging" past 3 minutes; Recheck within 30 s of the limit runs a scan instead.
- CNO: `UiState.cnoTooOld(now)` hides every CNO bet (tab, widget, mini window, lanes) while CNO's own odds
  are over 5 minutes old; `UiState.booksAt(row, now)` never shows or agrees with a game page over 5 minutes
  old; the green-check lane re-reads each page every 4 minutes (`CnoFeed.AGREE_TTL_MS`, was 10).
- Costs: The Odds API (now mostly PropLine's backup) and sportsbook props are asked again after 2 minutes
  instead of 15/60; when The Odds API is the only sportsbook source, frequent scanning spends more credits.

## 25. The same +EV scanner for BetMGM: Vigilant MGM (2026-09-27 ~19:00Z, Tj: "make it also do the same exact functions to find positive EV on betmgm … Do not scan for both at the same time unless this can be done without wasting too much api usage. If needed or if smart, make this a totally separate app")

### 25.1 Where BetMGM's prices can come from (checked today)
- **BetMGM has no public odds API.** Its site sits behind Cloudflare bot protection (every `*.betmgm.com` page
  answered 403 from this container, measured). Its own "Sports API" docs (sportsapi.<state>.betmgm.com/restapi)
  are for partners and describe only deep links.
- **PropLine already carries BetMGM** (measured `/v1/freshness` 18:59Z: 1,653 active game-line markets, 3,463 props,
  10 s behind BetMGM) in the same `/odds` and per-game calls Vigilant makes for the fair line. With
  `includeBookIds=true` each book block carries `book_event_id` and each outcome `book_outcome_id` (the book's own
  ids); `includeLinks=true` adds the book's event page (docs: "BetMGM" among the books with a verified link
  template). Neither costs a request or changes the reply's shape for other callers (both fields are null unless asked).
- **The Odds API** carries `betmgm` too (its fallback role is unchanged).
- **CrazyNinjaOdds** lists BetMGM as a book (`site_id=4`, column `MGM`), so its Positive EV page works for BetMGM.

### 25.2 Decision: a second app from the same code, one book per app
- **Vigilant MGM** (`com.tjshea.vigilant.betmgm`, module `mgm`) compiles `app`'s own sources and resources with
  `BuildConfig.BOOK = "betmgm"`; `app` sets `"novig"`. `AppBook` switches every book-specific part. A copied fork was
  rejected: it drifts (CLAUDE.md's "duplicated normalizer" lesson). A runtime switch inside Vigilant was rejected: it
  would mix placed bets, tracked bets, CNO caches and keys between books and put BetMGM code paths in the Novig app.
- **Nothing scans both at once**: each app scans its own book on its own tap. **No request is made for BetMGM**: it is
  asked for alongside the reference books in the same PropLine/The Odds API calls (always first in the book list, so
  The Odds API's 10-book region never drops it), split off as the board, and never counted in its own fair line.
- Scanning in both apps spends each app's own requests (both can use the same PropLine key: its meter reads PropLine's
  own daily headers, so both apps' meters stay right; PinnWire/pinnapi count locally, so two apps on one key each see
  only their own calls).

### 25.3 Built (v0.17.0)
- `data/book/`: `Sportsbook` (NOVIG, BETMGM), `BookBoard` (the book's quotes → a Novig-shaped board: one event per
  game, one market per line, exact posted prices via `NovigBook.posted`, no fee; later feeds' games matched and turned
  to the board's home/away), `SportsbookScanner` (same `OddsScanner` interface as `Scanner`: game lines in parallel,
  The Odds API after PropLine, props for the book's games once its board is in, same re-use/freshness rules; the
  book's own quote ages its EV like the others, `BookBoard.withBookAges`; recheck = one PropLine request per league),
  `BetMgmLinks` (bet slip `sports.<state>.betmgm.com/en/sports?options=<fixture>-<market>-<option>` from BetMGM's
  documented deep-link format when the ids spell it out; else the game page; else BetMGM's home).
- `PropLineClient(relayNovig, bookIds)`: Novig's relay off and book ids on for Vigilant MGM only; Vigilant's requests
  are byte-for-byte as before (`SportsbookScannerTest` "Novig's scan is untouched").
- App: order book, maker bid, depth, Novig key, Novig's live prices and catalog, Novig meter and per-line read limits
  are Vigilant-only; BetMGM state picker; CNO view defaults to `site_id=4`; CNO's live-fee adjustment only on Novig rows.
- **Not verified live:** PropLine's demo key was at its daily cap all session, so the exact shape of BetMGM's
  `book_outcome_id` (market-option pair vs. option alone) is unknown; the link builder accepts either and falls back to
  the game page. First real scan with Tj's key settles it.

## 26. Faster scans, background auto-scan and +EV alerts (2026-09-28 ~03:20–04:10Z, Tj: "make the scans better or faster or find more bets … auto scan either cno or both cno and vigilant every 5 10 20 30 or 40 minutes in the background … a push notification … open the exact bet in novig immediately")

### 26.1 Where scan time went, and what changed (v0.18.0)

- **Novig's public edge is the floor**: ~4–6 books/s (§5.1, `RateGate`), so 1,200 prices ≈ 4 min
  public, ≈ 1.5 min with a key. Raising the pace was not tried: the measured limit (10/s drew 429s)
  is per IP and a phone may share a carrier IP; a 429 costs more than it saves.
- **The scan's own CPU was the surprise.** Every partial result (each 8 books) re-priced the whole
  plan, re-devigging every line's books (power devig: bisection, ~200 `pow` per book). Measured on a
  desktop JVM, a 1,200-market plan with 25 books a line: ~160 ms per partial. The book pump waits on
  it, so on a phone (several times slower) a large scan spent about as long pricing as reading.
  Now: `FairMemo` keeps each plan's fair lines (a plan is rebuilt whenever its fair odds change, so
  nothing stale is re-used), `FairLine.booksUsed`/`usedUpdates` are computed once, and the power and
  Shin bisections stop at 1e-13 (~45 steps, not 100): **3.4 ms per partial**.
- **Long scans re-read their first edges.** With 1,200 prices the first +EV lines were read minutes
  before the end. At the end, the feed's edges read over 60 s earlier are read again (≤40 books,
  best EV first); an edge that vanished is gone from the final feed. `ScanReport.booksReread`.
- **More room to find bets**: up to 1,200 prices a scan (was 400), and 16 or 24 props per game
  (props are where exchange prices lag most, §15). Vigilant's odds cap is +120/+150/+200/+300 only
  (Tj), migrated once (schema 6).

### 26.2 Background auto-scan on Android 16 (Moto G)

- **Why a persistent foreground service**: WorkManager's minimum period is 15 min (Tj wants 5).
  Android freezes a backgrounded app's process within seconds, so only a foreground service keeps
  the process and its network. `dataSync` foreground services stop after 6 h a day on Android 15+
  (`onTimeout`), so `AutoScanService` is `specialUse` (a sideloaded app: no Play review applies),
  alive only while auto-scan is on, with one low-importance notification (Scan now, Stop).
- **Why exact alarms**: with the screen off the CPU suspends and coroutine `delay`s stop counting.
  `setExactAndAllowWhileIdle` fires on time in Doze; `USE_EXACT_ALARM` (API 33+, granted at install;
  `SCHEDULE_EXACT_ALARM` up to API 32) lets Vigilant set them, and an exact alarm also lets it start
  the foreground service from the background if Android had stopped it. The next alarm is armed when
  a cycle starts (its interval after the start), so a long or killed cycle can't break the schedule.
- **Battery**: no wake lock between scans; the alarm's receiver holds a 60 s bridge lock until the
  service holds the scan's own (≤20 min). A process with a foreground service keeps network in Doze.
  Settings offers Android's "Unrestricted" prompt (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) for OEM
  battery savers. Restart after reboot and after an update (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`;
  both may start a `specialUse` foreground service).

### 26.3 Which bets alert

- Only what the lists would show (placed/✕ bets, "Starts within", every filter), at or over the
  alert minimum (2/3/4%), **and several books agree**, in the green check's terms: 3+ books price
  both sides and 3+ of them alone (worst-case devig) make Novig's price +EV. CNO bets: CNO's game
  page (`CnoBooks.check`) at Novig's price now (`NovigLive.readNow`). Vigilant bets: the books behind
  the fair line (`Agreement`), at a Novig price read ≤3 min ago. A single sharp book never alerts.
- One alert per bet (`alerts.json`, keyed by Novig's outcome id when known, so the same bet from both
  scanners alerts once), ≤5 per cycle, taken down after 20 min (the price has likely moved). Tap =
  `novigapp://events/<outcome>` in Novig's app, as the widget's tap; Novig's site when the app isn't
  installed. Live check (2026-09-28 ~04:00Z): CNO's list → game page → 14 of 14 books agreed on
  Michigan State @ Wisconsin Over 44.5 at +108 (EV 3.2%), exact Novig link found.

## 27. Only 7 games, the Novig key used fully, more +EV found, and "just copy CNO?" (2026-09-28 ~06:15–08:30Z, Tj: "Why did it only scan 7 games? … Make sure the app is taking full advantage of the novig API key … finding as many positive EV bets on novig as possible … For the cno scanner, can't it just copy what is already on cno website")

### 27.1 Why "440 prices checked across 7 games" (v0.18.0 screenshot, Mon 2026-09-28 06:11Z)

Novig's live catalog, read the same hour (`/v3/public/catalog/events`, next 4 days, pregame):

| League | Games Novig listed | Inside "Days ahead" (3) | Notes |
|---|---|---|---|
| NFL | 2 | 1 (Eagles @ Bears, MNF) | Steelers @ Browns is Friday 00:15Z, past the window |
| MLB | 4 + series/futures | 4 | Regular season over; Wild Card starts Tuesday |
| WNBA | 4 + series | 4 | Playoffs |
| NCAAF | 2 | 0 | Next games Thu/Fri |
| NHL (not picked) | 7 | — | Preseason |
| **ATP + WTA (not in the app)** | **50 (763 markets)** | — | More than every US league together |
| MLS 2, NPB 3 | — | — | Removed from the app at Tj's request (2026-09-25) |

So 9 real games in the window; "Series Winner" and futures listings aren't games. 7 of the 9 matched a
fair-odds source (the other two most likely had no reference lines yet). ~~The slate was simply thin~~
**Wrong: see §27.5.** The window was the problem: 55 college and 15 NFL games sat 4–6 days out, past
"Days ahead: 3" (and past the 4-day bound this check itself used). Each game was also read shallowly:
2 lines per spread/total group and 8 props per game held the scan to ~220 markets (440 prices), whatever
the per-scan budget (300–1,200), while Novig lists 50+ game lines and hundreds of props per game.

### 27.5 Correction (2026-09-28 ~07:15Z, Tj: "Baseball is not over … college football has games. There are way more than 7 total games")

The live catalog read without a date bound lists **55 college games (Thu night–Sat) and 15 NFL Week 5 games
(Sun–Mon)**, 4–6 days out. MLB really was down to its 4 Wild Card openers (the regular season ended Sunday; those
games show FINAL). The cause was **"Days ahead: 3"** (the default): a Monday-morning scan ended Thursday morning.
v0.19.1 makes a week the default (a saved 3 moves to 7 once), reads every event (one small request) so the feed says
how many games start past the window ("55 more games on Novig start later than that"), and scans `DELAYED` games.
Fair-odds requests don't grow with the window (one per league or sport); only Novig reads do, inside the per-scan
budget, soonest first. With the key's live feed, a 1,200 budget costs about the same time as 300. The Novig docs
review that came with it: NOVIG_API.md §13.

### 27.2 What the Novig key can do, from Novig's docs (re-read 2026-09-28)

- No batch route for books: every REST book is one request. The key's REST route has its own `read`
  bucket (64 burst, 16/s; Vigilant uses 14/s, 6 at once), which is why Tj saw scans get much faster.
- **The websocket is the big one.** `GET /v3/ws` (trading or trading::read key: Vigilant's is
  trading::read). A subscribe is charged `weight × subjects` (`book` = 16 per market) **but never more
  than the 512-token `stream` bucket, and a request over that passes whenever the bucket is full**
  (docs: api/streaming/connection). A connection may watch **2,048 markets** (api/throttling). So one
  subscribe covers a whole 1,200-price scan: the upgrade spends 32 tokens, the bucket is full again
  ~8 s later (4/s), one request goes out, every snapshot arrives together, and from then on each change
  is pushed. REST at 14/s takes ~86 s for the same 1,200.
- Subscribing by **event** was rejected: an event counts as all its markets (500+ for an NFL game), so
  four games fill the 2,048 cap with lines no fair source quotes (and a subscribe past the cap
  subscribes nothing: `SUBSCRIPTION_LIMIT_EXCEEDED`).
- The `bbo` channel (8/market) is cheaper per subject but its message format isn't documented; `book`
  is, and since the charge is capped at the bucket either way, `book` costs the same for a big scan.
- Signed routes (REST or socket) refuse a VPN and need the Novig app opened every 3 days (451).

### 27.3 What was built (v0.19.0)

- **Websocket reads for keyed scans** (`data/novig/stream/NovigStream`, `PushedBooks`; `NovigPublicClient.stream`,
  `watch`/`pushed`; `Scanner.BookPump`): each pass hands the whole plan (likeliest first, up to the budget)
  to the socket; its first subscribe on a connection waits for a full bucket so the first few planned
  lines can't spend what the whole plan needs; later additions go when their tokens are back. REST keeps
  reading the likeliest lines meanwhile (separate throttle), and every book the socket holds is taken in
  one pass with no request. Dropped lines are unsubscribed; gaps re-snapshot; a throttle reply is retried
  after a refill, a limit reply asks for half. The socket closes 2 minutes after a scan last used it
  (rechecks right after a scan are instant), at once when a scan ends with Vigilant off screen (background
  auto-scan) or Vigilant leaves the screen with no scan running, and on any failure scans use the key's REST route for 5
  minutes and say why once. OkHttp pings every 20 s, so a dead socket fails rather than serving old books.
  **Not verified against the real API** (the key lives in the phone's hardware keystore; nothing here can
  sign as it): the mock socket speaks the documented protocol, and anything unexpected falls back to
  REST, which Tj's phone already uses. Settings › Novig API shows how the last scan's prices came in.
- **The budget is filled** (`ScanSettings.fillBudget`, on by default; `Planner.fill`): after the per-game
  picks, every other line a fair source quotes (alternate spreads/totals, more props) up to "Novig prices
  per scan", best-covered first, read after the picks.
- **Tennis** (ATP, WTA; turned on once by schema 7): Kalshi's free match markets
  (`KX{ATP,WTA}MATCH`, `…CHALLENGERMATCH`) price the winner; Pinnacle (PinnWire/pinnapi `sport_id` 2)
  the winner, games spread, total games, each player's games won and the 1st-set winner. Live check
  (`LiveTennisTest`, 07:40Z): 48 Novig matches, 375/375 outcomes resolved to a player, 37 of 46 paired
  with Kalshi and priced end to end. Not priced: set spread and total sets (no source quotes them in a
  shape checked here). The Tracker can't grade tennis from scores yet (ESPN's tennis feed isn't wired):
  Won/Lost by hand.

### 27.4 "Can't the CNO scanner just copy what's on CNO's website?"

It already does: since v0.13.0 the CNO scanner reads CNO's own +EV list (Tj's Shared View, his filters)
every 5–15 s while the CNO tab or a widget is on screen (§18.5), and adds what the website doesn't have:
Novig's price **now** for each bet (CNO's copy of Novig is up to a minute old), the books-agree check,
exact bet-slip links, placed-bet hiding, the start-time filter and background alerts. Copying more of it
wouldn't find more bets: CNO's list *is* its scan, it refreshes about once a minute server-side, and
reading it harder only invites CNO's pauses (§20.2). What CNO can't give is what Vigilant's own scan adds:
Novig's live books read directly (now pushed through the key), tennis and alternate lines CNO's filters
skip, and fair lines from Pinnacle, Kalshi and PropLine. Keeping both ("Both" mode) is the best of each.

## 28. Bets that showed mid-scan and then disappeared (2026-09-28 ~07:50–08:30Z, Tj: "The app found several positive EV bets while scanning but they quickly disappeared. Is this supposed to happen?")

Every way a bet could leave the feed, from the code (v0.19.1):
1. **Priced before the fair line was complete (the cause Tj saw; measured).** A scan reads Novig's books while the
   fair-odds sources are still answering and published each batch priced from whoever had answered. When the next source
   answered, the fair line moved. Live (`LiveFlickerTest`, Novig + Kalshi + Polymarket, 07:55Z): a Washington Mystics
   moneyline showed 19 s in at +1.13% from Polymarket alone and left when Kalshi's odds moved the fair price
   (0.4146 → 0.4068). With Pinnacle, PropLine and sportsbook props (per game, slowest) arriving at different times,
   more of it.
2. **The 5-minute rule (§24) counted from the feed's own "last seen".** A sportsbook price PropLine or The Odds API
   last saw 4½ minutes ago was used, and the bet hid itself 30 seconds later, silently.
3. **The end-of-scan re-read (§26)**: an early edge whose Novig price moved by the end is dropped. Intended.

v0.19.2: (1) a partial result holds back a league's bets until every fair source for it has answered (its props until
the props-only sources have: game lines don't wait for per-game props), `ScanResult.waitingFor`; (2) a scan prices only
with quotes at most 3 minutes old (`Freshness.MIN_SHOWN_MS`), so every bet it shows stays for at least 2 minutes
(Recheck and re-pricing keep the plain 5-minute rule for bets already shown); (3) bets hidden for old odds are counted on
the feed ("2 bets hidden: the other books' odds behind them are over 5 minutes old. Scan for current odds."). Live after
the fix (same test, 08:25Z): 10 shown mid-scan, 0 gone, 10 kept.

## 29. "Now it is reading the API very slow" (2026-09-28 ~14:45–16:00Z, Tj, on v0.19.2: "Did this latest version change anything with the novig API scan because now it is reading the API very slow")

**What v0.19.2 changed:** no Novig request code at all (`git diff v0.19.1 v0.19.2`). It held each league's bets
mid-scan until every fair-odds source had answered for it (§28), and Kalshi is the slowest by far: measured live
(`LiveSourceTimingTest`, 2026-09-28 ~14:50Z) 57 series at 2/s, one request each, leagues one after another: NFL 8.2 s
(19 series), NCAAF 3.5 (6), MLB 8.9 (18), WNBA 5.1 (10), ATP 0.9 (2), WTA 1.0 (2): 27.6 s; Polymarket 9.2 s. So a
league's first bets showed 8–28 s into a scan. **What v0.19.1 changed:** 7 days ahead (board 8,319 markets, 0.67 MB, 2
pages, vs 4,004 at 3 days; 0.9 s either way) and "use the whole budget", so every scan now reads the full budget
(e.g. 1,200 prices where v0.18.0 read 440); keyed reads paced from `GET /v3/limits`; the board read through the key.

**Real caps found while tracing the keyed read path (all older than v0.19.2, all fixed in v0.19.3):**

1. **OkHttp ran the key's reads 4 at a time.** OkHttp's default dispatcher allows 5 requests at once per host, and
   with OkHttp 4.12 an open websocket holds one of them for as long as it's open (probed: `runningCallsCount() == 1`
   with only a socket open). With the key's socket to `api.novig.com` up, the "6 in flight" keyed reads got 4 lanes,
   shared with anything else reading Novig (CNO's live prices, the board). At a phone's ~400–700 ms per signed request
   that is 6–10 prices a second, not 14. Now `vigilantHttpClient()` allows 16 per host (`HttpClientTest`, which fails
   on OkHttp's default), and a key keeps 10 in flight with 30-price batches between re-plans (was 8, leaving lanes idle
   at the end of every batch).
2. **One refusal could cut the pace to 1 a second for a minute, or stop the scan.** When Novig refuses a burst,
   every request in flight comes back 429 together, and each one halved the pace again (14.4 → 7.2 → 3.6 → 1.8 → 1/s,
   held a minute) and counted toward the 8 refusals that stop a scan's reads. Now refusals within a second of each other
   are one (`RateGate.SAME_BURST_MS`; `RateGateTest`, `NovigPublicClientTest` fail before).
3. **Kalshi's props held back game lines.** Kalshi now reads every league's game-line series first (29 of 57, ~15 s)
   and its props after, re-using what it read (nothing asked twice); a league's game-line bets show once its Kalshi
   lines are in (`ReferenceSource.linesFirst`, `SteadyFeedTest`). Measured live (`LiveSourceTimingTest`, ~15:25Z):
   game lines in at NFL 1.1 s, NCAAF 4.6, MLB 8.1, WNBA 11.1, ATP 12.1, WTA 13.1 (before: NFL 8, MLB 21, WTA 28);
   all of Kalshi at 27 s, as before.

**Checked and left:** `GET /v3/limits` matches Novig's OpenAPI spec exactly (`read {capacity, refillPerSec}`, 1 token
per book read, no batch book route: the websocket is the only bulk path); pacing by it stays. The board is not cached
on either route (CloudFront "Miss" on every public read, 0.3–1.6 s a 5,000-market page), so reading it through the key
costs nothing extra. The scan's own work between reads is small (`PumpCostProbe`: 0.6 s of CPU per 1,200-price scan
on the JVM here).

**Still unmeasured, now visible:** the phone's real signed-request latency and whether the websocket's snapshot
arrives. Settings › Novig API now shows where the last scan's time went (`ScanTiming`: board, fair odds, Novig
prices with their pace and how they came (live feed / key / public), first bet, Novig's refusals, the key's limit), so
the next "slow" report comes with the phone's own numbers.

## 30. "Test key says proxy or VPN, but I don't", the 5-minute rule, and CNO's fewest books (2026-09-28 ~15:25Z, Tj: "The app is telling me I have a proxy or vpn when I test the novig key, but I don't. Research online and reconsider the 5 minute stale odds cutoff … For the fewest books behind the fair price filter, add options for 1 and 2 books. Remove any option over 4 books")

### 30.1 The VPN/proxy message
- **What Novig said:** Test key signs `POST /v3/echo`; the only VPN/proxy wording it could show came from a 451
  `ANONYMIZED_NETWORK`. Novig's errors page: 451 codes come from its location screen; `ANONYMIZED_NETWORK` = "the
  request came over a VPN, a proxy, or a Tor exit" and, with `RESTRICTED_NETWORK_REGION`, "judge the request's
  network"; the rest judge the key holder's device ("the key holder must open the app"). A data-center address is
  fine; a read admits a stale device check.
- **So it's a verdict on an internet address, not the phone.** IP-reputation screens (GeoComply's GeoGuard and the
  like) list shared addresses now and then: carrier-grade NAT pools (T-Mobile's mobile and home internet share few
  public IPv4 addresses among many customers) are the known false-positive family; vendors themselves advise against
  hard-blocking carrier IPs and take false-positive reports per address/CIDR. `api.novig.com` is IPv4-only, so it
  isn't an IPv6 quirk. A real VPN on the phone shows as `TRANSPORT_VPN` (ad blockers and security apps run one
  without it being obvious).
- **Vigilant got it wrong twice:** it said "Turn the VPN off" to someone with none, and read
  `RESTRICTED_NETWORK_REGION` as "open the Novig app". **And it cost speed:** a refused key sends every scan to the
  public routes (4–6 prices a second, 3 at a time, vs the key's 14) for 10 minutes, then tries again: the likeliest
  real cause of §29's "reading the API very slow".
- **v0.19.3:** advice per documented code, with the code; Test key names the connection it used and whether a VPN is
  really up, and on a network refusal tries the other connection (mobile data ↔ Wi-Fi, held only for the test) and
  says which one Novig accepts. Scan and live-feed banners say the same in one line.

### 30.2 The 5-minute rule, reconsidered with live data
- **What the rule measures:** not "the line hasn't changed in 5 minutes" but "the feed hasn't *seen* this price in 5
  minutes" (§24: Pinnacle/Kalshi/Polymarket = when fetched, The Odds API = market `last_update`, "the last time our
  system saw odds for that market", PropLine = `last_seen_at`). A line that sits still is re-seen on every read and
  never ages out while the scan is current. Past 5 minutes means the feed stopped seeing it (a suspended or pulled
  market: The Odds API freezes `last_update` for ~15 minutes after a market goes) or the scan itself is minutes old:
  in practice the rule removes a found bet 3-5 minutes after the scan that found it.
- **Measured (2026-09-28 15:29-16:00Z, a quiet Monday midday; most games hours to days off):** every 60 s for 31
  minutes, every open Kalshi game-line market (NFL, NCAAF, MLB, WNBA, ATP, WTA: 2,471 markets, 62k before/after
  pairs; mid of a <=4c spread) and the 86 soonest Novig NFL/NCAAF/MLB/WNBA lines (taker price = 1 - opposing best bid):

  | Minutes later | Kalshi moved >=0.5 pt | >=1 pt | >=2 pt | Novig moved >=0.5 pt | >=1 pt | >=2 pt |
  | --- | --- | --- | --- | --- | --- | --- |
  | 5 | 5.8% | 1.6% | 0.1% | 10.6% | 1.8% | 0.2% |
  | 10 | 8.9% | 2.7% | 0.3% | 17.9% | 3.8% | 0.3% |
  | 15 | 11.3% | 3.7% | 0.4% | 23.6% | 6.8% | 0.5% |
  | 30 | 17.2% | 6.9% | 1.4% | 35.9% | 11.5% | 2.6% |

  By market (1 pt+ within 15 / 25 min): NFL moneylines 12% / 19%, MLB totals 15% / 21%, MLB spreads 8% / 9%, NFL
  spreads and totals 2-3% / 4-5%, WNBA sides 0-1%. A 1-point move in the fair line moves EV about 2 points at even
  odds, so against 2-4% edges it matters.
- **Elsewhere:** NFL spread numbers change ~0.2 times a game from release to kickoff (12 books, 2018,
  sportsbettingdime); moves bunch up on news, and MLB moves most between lineups (3-4 h before first pitch) and the
  start. Around late injury news slow books keep stale numbers 5-15 minutes: the window +EV tools live on, and
  exactly when an old *fair* line shows +EV that isn't there. The bets a scan shows lean toward lines in motion,
  so their risk is higher than the averages above.
- **Decision (v0.19.3):** Tj's instinct holds away from game time: most lines don't move within 10 minutes. So
  other books' quotes may be **10 minutes old on a game more than 3 hours from its start, 5 minutes within 3 hours
  or live** (`Freshness.maxAgeMs`), one rule for scan pricing (still with 2 minutes' headroom), re-pricing, Recheck
  and how long a found bet stays listed. A game whose every quote aged out drops from the fair side (as if the feed
  hadn't answered), so Novig's board still lists it. Cards say "odds N min old" past 3 minutes. Not loosened further:
  past 10 minutes 3-7% of fair lines are off by a point, concentrated in the markets that move, and a scan (or the
  background auto-scan) refreshes them for free. CNO's own 5-minute checks are about CNO's updater stalling and
  stay as they were.

### 30.3 CNO's fewest books
- Choices 1–4 (were 3, 4, 5, 6, 8, 10), default 4 (was 5), a saved 5+ becomes 4 once (schema 9). The app's pick is
  now always posted to CNO's form: before, a Shared View link's bigger count won, so 1 or 2 would have done nothing.
  The Settings hint still says why thin markets are risky; Vigilant's own books check on each bet is unchanged.

## 31. Bigger scans, per-game caps, props credits, a stake in the bet slip, PinnWire → pinnapi, one-tap Open (2026-09-28 ~18:40Z, Tj: "consider if the 1200 Max prices per novig scan is enough … If I can have no limit on the prices safely, then make that option … raise the max alternate lines and player props per game … raise the most credits per scan on props … automatically enter 1 dollar on every betslip … When pinnwire api usage runs out, automatically switch to pinnapi … one press buttons next to each bet to Open the bet in novig")

### 31.1 Is 1,200 enough? (measured, `LiveBudgetTest`, 2026-09-28 ~18:50Z)
- Novig's board, 7 days, NFL/NCAAF/MLB/WNBA/ATP/WTA: **8,620 markets** on 205 events: 2,577 spreads + 2,242 totals
  (mostly alternates, ~15 per game), 286 team totals, 169 moneylines, halves/sets, and ~2,700 player props.
- Priced by the free sources alone (Kalshi + Polymarket): **1,168 lines** on 150 matched games; per-game caps 2/8 pick
  409 of them and "use the whole budget" fills the rest. So 1,200 already covers everything the free sources price.
  Pinnacle (PinnWire: every alt line + props) and PropLine (30 books) price many more of the 8,620: not measurable here
  without Tj's keys, but likely well past 1,200.
- **What more reads cost:** a keyed REST read is one token of the key's `read` bucket (16/s, shared with everything else
  Vigilant reads on Novig): ~14 prices a second. One websocket connection watches at most 2,048 markets
  (`maxWatchedMarkets`), and a watched market costs no request after the ~8 s snapshot: so up to ~2,000 a keyed scan is
  cheap, past it every price is a request. Other books' odds are read once as a scan starts: a scan that runs past
  ~3 minutes shows near-game bets already expiring (5-minute rule), past ~8 minutes far-off ones too. And data/battery:
  each price is ~0.5-2 KB, repeated by background auto-scans every 5-40 minutes.
- **Decision (v0.19.4):** budget choices up to **2,000** (one live-feed connection's worth); **no "No limit"** (past
  2,000 a scan can run many minutes and its early reads age out, and background scans would repeat it). And a guard for
  any long scan: a line whose other books' odds (read as the scan began) couldn't stay listed 2 minutes once priced is
  left for the next scan (`BookPump.canStillShow`, `ScanReport.booksTooLate`, shown in Settings › Novig API's timing line).

### 31.2 Lines and props per game, props credits
- With "use the whole budget" on (the default since v0.19.1) the per-game caps only decide which lines are read
  *first*; the rest of the budget still goes to every other priced line. Measured: caps 5/24 pick 710 of the 1,168,
  caps 50/500 pick all of them. Raising them costs nothing extra (the budget bounds the reads): choices now 1-10 lines,
  0-48 props.
- The Odds API props are a backup: only games and prop types PropLine didn't price, never more than the cap a scan.
  Safe as far as the plan's credits go: 500 a month free (at 96 a scan, as few as 5 scans), 20,000 on the smallest paid
  plan. Choices now up to 192, with that worst case spelled out; when credits run out, props still come from
  PropLine, Pinnacle and Kalshi.

### 31.3 A stake in Novig's bet slip
- Novig's deeplinking docs (docs.novig.com/affiliates/deeplinking): web `novig.com/events/<outcome_ids>/<partner_id>/
  <wager_amount>`, "Optional pre-filled wager amount (requires partner_id)", in dollars ("10 Novig Cash"); native
  `novigapp://events/<outcome_ids>/<partner_id>`. The app's own linking config has `:amount?` after the partner
  (NOVIG_API.md §9.1), so the native link should take it too: **not checked on a device**.
- Built: Settings › Bankroll & Kelly › "Amount in Novig's bet slip": Off (default, links unchanged) / $1 / Kelly (the
  bet's Kelly stake, never under $1) / My amount. Every bet opened carries it: the +EV cards' new button, the bet
  sheet, the widget and mini window, the CNO tab, alerts. Partner tag: CNO's `cno` stays on CNO's links; Vigilant's
  use `novig` (the docs' own example). Novig still asks to confirm.

### 31.4 PinnWire → pinnapi
- Already built (v0.16.0): PinnWire first; its daily limit (429 `window: day`, or the app's own count of 100/day) rests
  its key until the reset, pinnapi answers meanwhile (no props on pinnapi's trial), then PinnWire goes first again. A
  gap fixed: any other PinnWire failure (a 5xx, an odd status, a dropped connection) failed Pinnacle for the scan
  instead of trying pinnapi.
