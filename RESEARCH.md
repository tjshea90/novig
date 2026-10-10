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

## 32. No limit on every scan cap, bounded by the time window (2026-09-28 ~20:2xZ, Tj: "Add unlimited options in the vigilant app for all types of scans that can benefit from unlimited. For example, unlimited credits per scan, unlimited novig prices per scan, etc. but make sure the app doesn't just scan continuously, it should stop the scan when all the markets are finished scanning for the selected time period")

### 32.1 Which caps, and what "No limit" means for each (v0.19.6)
- **Novig prices per scan** (`maxBooksPerScan`): No limit = every line a fair source prices, for the games in the scan's
  time window, each read once. §31.1's reasons for stopping at 2,000 still hold as costs (past the live feed's 2,000, each
  price is a request at ~14/s keyed, ~300/min public), but the scan can't run away: other books' odds are read as the scan
  starts and last 5 minutes (10 for games over 3 h off), and a line that couldn't stay listed 2 minutes once read is left
  (`canStillShow`, §31.1). So Novig reading stops by ~8 minutes whatever the limit. New: lines a scan left too late are read
  first (right after likely/near +EV) by the next scan (`Scanner.leftLastScan`), so back-to-back scans cover the window.
- **The Odds API props credits per scan** (`bookPropCreditsPerScan`): No limit = every game in the props window with props
  Novig lists (and PropLine didn't price), one credit per prop type, each game once a scan. ~4 credits a game (Core 4), up
  to 18 in football (All): the free 500 a month can go in one or two scans; the key pool stops when credits run out and
  props come from PropLine, Pinnacle and Kalshi.
- **PropLine props games per scan** (`propLineGamesPerScan`): was a hidden fixed 12; now 12/24/48/No limit (one request a
  game against the free 1,000 a day; a full slate is 50+).
- **Lines / props per game** ("All"): with "fill the scan" on they only order reads; off, All reads every quoted line.
- **Sportsbook props hours** ("All"): every game the scan reads (`bookPropWindowHours` = min(hours, scan window)).
- Not given one: Days ahead (it *is* the time period), the 40-price recheck and end-of-scan re-read (internal, the feed is
  rarely larger), Novig-only games on the Games tab (20; they can't be +EV), CNO's rows per read (25/50/100 are CNO's own
  choices; another value isn't known to work).

### 32.2 "Stop … for the selected time period"
- The scan's window is now `scanWindowHours`: Days ahead, or "Starts within" when shorter (open task S1, done here). Before,
  Starts within only hid bets while the scan read the whole Days-ahead window; with No limit that meant minutes of reads
  on games Tj had filtered out. Props follow the same window. Widening Starts within after a scan now needs a new scan: the
  feed says so ("The last scan read games starting in the next 12 hours. Scan to add …", `ScanStatus.scannedWindowHours`).
- A scan is one pass: every line in the window at most once, then it ends (BiggerScansTest `with no limit a scan reads every
  priced line in the time window once, then stops`). Nothing repeats except background auto-scan and the widget's rescan,
  each on its own timer (unchanged), and Pause stops all of it (v0.19.5).

## 33. Claude tooling for building Vigilant: the claude-api skill, hillclimb, plugins and skills (2026-09-28 ~22:16Z, Tj: "Research the new claude-api skill and hillclimb and figure out if it can improve this app or development. Then research other skills or plugins including from third parties that can improve the app or Claude ability to make the app better. Tell me anything I need to do")

### 33.1 The claude-api skill and hillclimb
- **What it is.** A skill bundled with Claude Code (2.1.284 here): a reference for writing code that *calls* the Claude API
  (Anthropic SDKs, model ids, caching, tool use, Managed Agents), plus subcommands `migrate`, `prompt-audit`, `upgrade`,
  `cost-optimize`, `build-eval`, `hillclimb`, `preserved-thinking-migration`, `managed-agents-onboard`. Anthropic's post of
  2026-09-08 ("Reducing cost and improving performance with Claude Platform") introduced `prompt-audit` and `hillclimb`.
- **`/claude-api hillclimb`** (skill file `shared/evals/eval-hillclimb.md`) tunes an app that calls Claude against an
  existing eval: baseline, then one change per round (prompt, tool text, model, effort), re-run, keep or revert, with a
  train/test split so the headline number comes from cases the analyzer never read. Step 0: no runnable eval, stop (or
  `build-eval` first). Every round's eval run is billed API usage.
- **Verdict for the app: no.** Vigilant makes no LLM calls (Kotlin math + HTTP), so there is nothing to climb. Adding Claude
  to the app was considered and rejected: every call costs money (Tj: nothing that costs money), adds seconds to scans
  that race odds going stale (§24, §30.2), needs an API key on the phone (public repo), and turns deterministic, tested
  matching/pricing into something that can't be pinned by a test. The idea behind hillclimb (measure, change one thing,
  re-measure on held-out data) is already how scan speed (`ScanTiming`) and matching (`LiveNovigSmokeTest`) are worked on;
  a CLV-based tune of the devig/fair-price choices would need far more settled bets than the $1 test bets give.
- **Verdict for development: `/claude-api prompt-audit` is the useful part.** It audits instruction files (CLAUDE.md,
  skills, commands, subagents) for dated patterns, stale facts and contradictions, writes a report plus a proposed diff,
  and applies nothing without consent; its rules say fact/contradiction fixes are proposed, never auto-applied. It runs
  inside Claude Code (plan usage, not API money). This repo's instruction surface is large (CLAUDE.md 36 KB loaded every
  session, BRIEF.md 44 KB) and has stale bits found during this research: bootstrap.sh's rules block still says "NO
  signing keystore exists yet" and "no architecture decision has been locked in", both false since v0.1.0. Offered to Tj
  as an opt-in run (§33.5).

### 33.2 Fixed now: the session-start briefing never reached Claude (ckpt 605)
Claude Code caps a hook's `additionalContext` at 10,000 characters; over that it saves the text to a file, shows a
2,000-character preview, and does not tell Claude to read the file (code.claude.com/docs/en/hooks, no setting raises it).
`tools/resume.sh` printed all of TASKS.md (210 KB) through `bootstrap.sh`, so the briefing was 215,548 characters and
sessions saw only the first lines of the INBOX tail: no CHECKPOINT "Do this next", no task list. Now `bootstrap.sh`
prints TASKS.md's open boxes only (one line each, with line numbers; current job first, the 12 most recent older ones
after) and `resume.sh` trims anything over 9,500 characters with a pointer to `bash tools/resume.sh --text`. Briefing:
7,186 characters. Tests: `tools/test_resume.sh` "the briefing fits Claude Code's 10,000-character hook cap…" and "an
oversized briefing is trimmed…" (both failed before the fix).

### 33.3 What a cloud session can load (code.claude.com/docs/en/cloud-environments, "What carries over")
- Loaded: the repo's CLAUDE.md, `.claude/skills/`, `.claude/agents/`, `.claude/commands/`, `.claude/rules/`, and skills
  enabled on the claude.ai account. A skill in `novig/.claude/skills/` loads once Claude works on files in `novig/`
  (sessions here start in `/home/user`, above the clone).
- Not loaded: plugins turned on in the repo's `.claude/settings.json` or in user settings. Only an organization's
  server-managed settings (Team/Enterprise owner) push plugins into cloud sessions. So marketplace plugins (LSP servers,
  context7, superpowers, …) are not a repo-side switch for this project.
- The environment's setup script runs once, then a filesystem snapshot is reused for about 7 days if the script finishes
  within about 5 minutes. This environment already has one (ran 19:12Z today; SDK + Gradle mirror: TASKS S3 ticked).

### 33.4 Candidates checked
| Candidate | Source | Verdict |
| :- | :- | :- |
| `kotlin-lsp` (official plugin, JetBrains Kotlin language server) | claude-plugins-official | Not now: see 33.4.1 |
| Chris Banes' skills (`compose-performance`, `compose-state-and-effects`, `kotlin-concurrency-and-flow`, `compose-ui-testing-patterns`) | github.com/chrisbanes/skills, Apache-2.0 | ADDED 2026-09-29 (TASKS X2, `.claude/skills/THIRD_PARTY_NOTICES.md`). Plain SKILL.md folders work in cloud sessions when committed to `.claude/skills/`; they target exactly the Compose recomposition, battery and coroutine-cancellation code this app is full of. Read in full before committing (third-party instructions) |
| `/code-review`, `/security-review`, `/simplify` | bundled with Claude Code | Already available, no install. `/code-review` on the diff since the last release fits the full-test protocol |
| `/claude-api prompt-audit` | bundled | Yes, opt-in run (33.1) |
| context7 (library docs MCP) | claude-plugins-official | No: a plugin (not loaded in cloud sessions), and WebFetch/WebSearch already reach the docs |
| superpowers, mattpocock-skills, feature-dev, beads/ZSL "superpowers" | marketplaces | No: process frameworks with their own SessionStart hooks and workflows that would compete with CLAUDE.md's checkpoint/TASKS/test process and add context every session |
| pr-review-toolkit, code-review plugin, commit-commands | claude-plugins-official | No: PR-centric; this project pushes to main without PRs, and the bundled `/code-review` covers review |
| security-guidance | claude-plugins-official | No: runs an LLM diff review on every Stop (usage), and plugins don't load here anyway |
| KotlinSense, community `kotlin-lsp` (fwcd kotlin-language-server) | Anthropic Directory (claude.ai catalog) | No: community wrappers; fwcd's server is deprecated, same binary-install problem |
| Kobiton, Ansight, Bugsee, Dynatrace | Anthropic Directory | No: need real devices/emulators or paid services; this container has no emulator |
| Devil's Advocate, Graph of Thought (already connected MCP servers) | Tj's claude.ai connectors | Harmless (their tools load only on demand), little value for this repo; keep or disconnect as Tj likes |

#### 33.4.1 The Kotlin language server, tested in this container (2026-09-28 ~22:20-22:50Z)
- The official plugin (`kotlin-lsp@claude-plugins-official`) only names the command `kotlin-lsp --stdio`; the server is
  JetBrains' Kotlin LSP (github.com/Kotlin/kotlin-lsp, "Alpha", partly closed-source). Build 263.4702.0 ("2026.3 EAP",
  2026-09-08): 368 MB tarball from download.jetbrains.com (5 s here), 1.2 GB unpacked, bundles Java 25. `license status`
  says "eap (Valid) … valid through 2026-10-08" and "This build does not require a license", but the server also ships
  `license login/activate/trial`, so later builds may need a JetBrains license.
- Real repo: the Gradle import fails on the Android module ("Querying the mapped value of property(…) before task
  ':app:generateDebugBuildConfig' has completed"; its Android Gradle Plugin support is experimental). With the import
  failed it reports nothing, not even a type error injected into `engine` (0 diagnostics). Cold import with an empty Gradle
  cache ~2 min (~930 MB of dependencies); the server uses ~1.7 GB RAM plus a ~1.5 GB Gradle daemon.
- Scratch copy with `:app` left out of `settings.gradle.kts`: import ~46 s, usable ~5 min after start (indexing on 4
  cores). Then it works: an injected `val x: Int = "…"` came back as "Initializer type mismatch: expected 'Int', actual
  'String'" on the right line in both `engine` and `data`, clean files had no errors, and go-to-definition went from
  `data` to `engine/Devig.kt`.
- Not now, because a cloud session can't switch the plugin on from the repo (33.3), the Android module (the whole UI)
  stays out, and the free EAP build expires monthly. Untested route if it's ever worth it: the environment variable
  `CLAUDE_CODE_PLUGIN_DIRS` pointing at a plugin folder in the repo, the setup script installing the server, and a
  settings.gradle.kts switch that leaves `:app` out only for the language server. Revisit when its Android import stops
  being experimental. Probe script: a ~150-line stdio JSON-RPC client (initialize, wait for import, didOpen/didChange,
  `textDocument/diagnostic`, `textDocument/definition`), not kept in the repo.

### 33.5 What Tj needs to do
- Nothing is required. The briefing fix is pushed (ckpt 605), and the environment's setup script already exists (S3).
- Both opt-ins taken 2026-09-29 (Tj: "run the prompt audit and add the Compose skills"): the audit is in PROMPT_AUDIT.md, applied 2026-09-29 (TASKS Y1-Y2), the skills are in `.claude/skills/` and CLAUDE.md's "Skills for this app" says when each one loads (TASKS Y4). The original offer: (1) "run the prompt audit": `/claude-api prompt-audit` over CLAUDE.md, BRIEF.md
  and the briefing scripts, report plus proposed diff, nothing applied until Tj says which hunks; (2) "add the Compose
  skills": read Chris Banes' four skills in full, commit them to `.claude/skills/` with the Apache-2.0 license.
- No plugin installs, connectors or paid services are recommended.
- Update 2026-09-29: one thing per account after all, its cloud environment: the one-line setup script and
  `dl.google.com` allowed (33.6, BRIEF.md build trap 6).

### 33.6 Skills across accounts, the setup script, and test speed (2026-09-29 ~00:41Z, Tj: "Are the compose skills installed in the repo to use between different Claude accounts? I have three Claude accounts. Do I have to do anything to the other accounts before working on this repo again? … Think of and implement any other clean up or optimization for this repo so that future work is efficient and Claude can use skills for the best coding. The setup script for each cloud session should load a maven central script, does this work well?")
- **What travels with the repo (nothing to do per account):** `.claude/skills/` (Chris Banes' four + `test-protocols`),
  CLAUDE.md, BRIEF.md and the briefing/checkpoint scripts. Any account's session on this repo gets them (33.3; all five
  skills were listed in this session). The checkpoint hooks install themselves (CLAUDE.md "FIRST ACTION").
- **What belongs to each account:** its cloud environments (Pro/Max environments aren't shared) and its GitHub
  connection. Each environment needs the one-line setup script and `dl.google.com` allowed (BRIEF.md build trap 6).
  Without them nothing breaks: `bootstrap.sh` warns, Claude runs `bash tools/setup-android.sh` in the session (the SDK
  still needs `dl.google.com`; without it the app module is tested only on CI).
- **Does the mirror script work well? Yes.** Cold, with an empty Gradle home and no `~/.m2`: the full floor (761 tests)
  in 210 s, 1.3 GB of Gradle dependencies and 191 MB of Robolectric jars through Google's mirror, zero 429s. Warm: 110 s.
  Weak spots found and fixed: (1) `set -e` made any failed step (e.g. `dl.google.com` blocked on a "Trusted" network)
  exit non-zero, which stops a cloud session from starting: now WARN and exit 0; (2) it installed build-tools 36.0.0
  but AGP 8.13 builds with 35.0.0, which AGP then fetched itself mid-build (22:26Z yesterday): both installed now;
  (3) every new session re-downloaded 1.5 GB: `--prewarm` builds a throwaway clone and runs one Robolectric test inside
  the setup script, so the environment's snapshot carries the caches. Cold with `--prewarm`: 210-235 s total (SDK 13-65 s,
  pre-download 170 s with the Kotlin daemon; in-process compilation took 197 s). Then the first floor: 106 s, 2 MB
  downloaded. The pre-download gets what's left of a 250 s budget (a cut test: WARN after 42 s, exit 0, no process left).
- **Test speed:** `data:test` took 71 s in one JVM, mostly tests waiting out paced/retried requests (ExchangeClientsTest's
  Kalshi test 13 s at Kalshi's 2 requests/s, CnoClientTest 18 s, PlayerTeamsTest 15 s). `maxParallelForks` = half the
  cores: 38 s. `tools/test.sh` prints a per-module line and only failing tests' messages (`tools/gradle_summary.py`,
  checked by `tools/test_gradle_summary.sh`), and `ship.sh` uses it.
- **Fixed on the way:** the live checks' `VIGILANT_*` switches are now inputs of `data:test`. Before, turning one on
  after a run with the same filter left the task "up to date": the live check silently didn't run (shown both ways).
- **Not adopted:** Gradle's build cache and configuration cache (containers are thrown away, so a local cache rarely
  hits; the screenshot PNGs and the live checks are side effects Gradle doesn't track, so a cache hit would skip them
  silently; a configuration-cache problem fails the build, CI and release included). Chris Banes' `gradle-run` skill (a
  workflow ledger plus a mandatory diagnostic subagent for every Gradle workflow: heavier than this repo's flow, and
  `tools/test.sh` gives its main benefit, short output). CI changes (3 min 45 s per run, Gradle and Robolectric caches
  already on). Nothing on futures (Tj: leave them out, BRIEF.md).

## 34. Grading bets: what leaves them open, and what PropLine's "grades every prop" is (2026-09-29, Tj)

Tj: "Some bets are still pending in the open bets tab that are final … one of [the APIs] claims that the API can grade all props
markets. Research this and see if vigilant can use this."

### 34.1 PropLine (prop-line.com; openapi.json + llms-full.txt read 2026-09-29)
| Endpoint | Free key? | What it gives |
|---|---|---|
| `GET /v1/sports/{sport}/events/{id}/results` | **No** (redacted: `resolution` and `actual_value` are null, `redacted: true`; Hobby / Pro / Streaming / Enterprise only, Hobby is $9/mo) | every prop of every book resolved won/lost/push/void with the actual stat |
| `GET /v1/exports/resolved-props` | No (Pro+, 90 days) | the same as CSV |
| `GET /v1/sports/{sport}/players/{name}/history` and `/trends` | Structure only | per-player resolved history |
| `POST /v1/clv/grade` | No (Hobby+) | closing-line value of placed bets |
| `GET /v1/sports/{sport}/scores?days_from=N` | **Yes** | id, teams, status (`final`, `postponed`, `cancelled`, …), scores, last N days |
| `GET /v1/sports/{sport}/events/{id}/stats` | **Yes** | box score rows `player_name / stat_type / stat_value` (MLB hits, HR, RBI, runs, K, walks, SB; NBA points, rebounds, assists, threes, steals, blocks, turnovers; NFL passing/rushing/receiving yards, TDs, anytime TD; NHL goals, points, SOG, blocked shots, saves; soccer goals, assists, shots, cards; tennis sets won, aces, total games) |

Every call is 1 of the key's 1,000 requests a day (the same quota scans spend); event ids match the odds calls'.

**Verdict.** The claim is true but paid: PropLine grades every prop for every book **on a paid plan**. On the free key it publishes
the box score and the final scores, which is what Vigilant already reads for nothing from ESPN and MLB's Stats API (and reading
the box score is what makes a grade auditable: the Tracker now shows "Boldy: 3 Shots On Goal" next to "Over 2.5"). Wiring PropLine's
free `/scores` + `/stats` in as a second source would add redundancy only, spend the daily quota scans depend on, and could not be
checked live (the demo key answers 401 on `/scores`). Not built. If ESPN's feed ever breaks, or Tj buys a paid PropLine plan, the
smallest change is a `PropLineScores : ScoreSource` behind `FreeScores` (games by `days_from`, players from `/stats`, stat names
mapped to Novig's), used only for bets the free feeds couldn't grade.

### 34.2 What was actually leaving bets open (fixed in v0.20.0)
No score-feed parser for: **hockey** (ESPN's NHL box score was never read, so every NHL prop stayed open), basketball steals /
blocks / turnovers and the P+R / P+A / R+A / S+B sums, double and triple doubles, football tackles, **tennis** (ESPN's scoreboard has
each set's games; retired and walkover matches are "called": books' rules vary, so Tj taps those); market wordings `BetGrader` didn't
read ("Alternate Total", "Game Total", "Total Games", "Games Won", "1st Set Winner", "Set Spread", "Total Sets"); and an Undo that
switched auto-grading off for good. Novig's own market list (`GET /v3/public/types/markets`, 181 types) was read in full: every
prop a box score can settle is covered; first-scorer markets (`FIRST_TOUCHDOWN_SCORER`, `FIRST_BASKET`, `FIRST_GOAL_SCORER`), 3-way and
quarter/period markets need play-by-play the feeds don't give and stay a tap, each saying so. Because Tj's bets live on his phone,
the app now says why on every open bet instead of guessing.


## 35. Slow "Check odds now", bets that "can't be graded", and 61 of ~100 (2026-09-29, Tj, after v0.20.0)

Tj: "it scanned very slow. Slower than before. And a lot of bets can't be tracked … If they can't be tracked, how did the app know it
was positive EV to begin with? And it said it only updated 61 bets, but I have 100 or so open."

### 35.1 Why it was slow (measured live against CNO, 2026-09-29)
A CNO game page costs **two requests** (GET the page, then the timer postback that fills the books' grid), about **2.4 s** and ~139K
characters. About one open bet per game, so no page is shared, and CNO's session can't be reused for another game's page (a postback
with another game's URL answers "game page changed"). v0.20.0 read them one at a time through `CnoPace` (global, 1 s minimum gap per
request) with a 2 s wait on top, behind the same lock a Novig recheck uses. Result: ~2.1 s per bet live, so 100 bets took ~3.5 minutes.
**Fix (v0.20.1):** bulk reads use their own 500 ms pace (`BULK_GAP_MS`), three worker coroutines read at once (`BetRecheck.concurrency`;
`CnoFeed.readBooks`, lock-free), and the extra gap is gone. Measured live: **1041 ms a bet three at a time vs 2120 ms one at a time**
(`LiveCnoBooksSpeedTest`), so ~100 bets in under two minutes. CNO's "retry after" pause still stops the whole pass (`CnoFeed.pausedUntilMs`),
and five failures in a row stop it too.

### 35.2 Why those three bets couldn't be graded (real ESPN and MLB data)
| Bet | What the feed has | Was | Now |
|---|---|---|---|
| Kade Anderson Over 1.5, "Player Earned Runs Allowed" | MLB box: `EARNED_RUNS` 0 (Mariners 9/26) | CNO's label has "Allowed"; Novig's type is `EARNED_RUNS`, so no type fit and the market was unreadable | `marketWords` aliases: earned runs allowed, walks allowed, outs recorded (`PITCHER_OUTS`), runs batted in, batter strikeouts/walks, reception yards, 3-pointers made; `statOf` retries without a filler word ("Points Scored", "Total Rebounds") only when nothing fits |
| KC Concepcion Over 5.5, "Player Rushing Yards" | Browns box: receiving 2 for 9 and punt returns; **no rushing group** | "box score has no Rushing Yards" | **Football box scores list a player only under the groups where he recorded something**, so a missing stat is 0 (rushing yards, receptions, TDs, tackles, kicking …). Longest rush/reception excluded (no play is no market). |
| Erick All Jr. Under 0.5, "Player Receptions" | Steelers/Bengals box: not listed at all; ESPN's roster endpoint says `didNotPlay: false` (he played, no stats) | "isn't in the box score … mark it yourself" | Absent from a football box = played with no stat = 0 → **WON**; the card says "no Receptions recorded (no line in the box score, counted as 0)" |
Other rules added: a player on the game's **injury report as Out / IR / Suspension** with no line is `inactive` and the bet is **VOID**
(Novig refunds a player who sat out); in every other sport (basketball, hockey, baseball) the box lists everyone who played, so a
player who isn't in it didn't play → **VOID**; a name one letter off a listed player (same first initial, surname ±1 letter) is a
spelling, not a scratch → left to a tap; a box with fewer than 8 players is "not fully posted yet" (waits, never judged).
Live check: `LiveUngradedBetsTest` grades the three from the real feeds (Concepcion LOST, All WON, Anderson LOST), and
`RealBoxGradingTest` does the same offline from saved copies of the two ESPN box scores.
**Not built (idea):** ESPN's core roster (`sports.core.api.espn.com/v2/sports/football/leagues/nfl/events/{id}/competitions/{id}/competitors/{teamId}/roster`)
has `didNotPlay` per player (7 a side, the game-day inactives) for NFL: it would tell a healthy scratch (void) from a player who
played without a stat (zero) for certain. It costs two more requests a game and its names are short ("All Jr."), so it's only worth
adding if a healthy scratch not on the injury report ever grades wrong.

### 35.3 "How did it know it was +EV if it can't grade it?"
Two unrelated data sources. **+EV is computed before the game** from odds: CNO's list gives the fair price from the books, and Novig's
price is compared with it. **Grading happens after the game** from the RESULT (a score or box score), read from ESPN and MLB's Stats
API. A bet can be priced perfectly and still fail to grade because a market name or a box-score row didn't match, which is what these
three were. Nothing about a grading failure means the EV was wrong.

### 35.4 "Updated 61 but I have ~100 open"
The odds check only reads bets **with a CNO page whose game hasn't been on for four hours**. The rest were: games already over (the
very bets that couldn't be graded, so they stayed "open"), and Vigilant-only bets (no CNO page; a Vigilant scan prices those). v0.20.1
grades the finished games **in the same tap** (`checkOdds` runs `BetSettler.run(force = true)` beside the odds read; the toast says
"1 game already over: graded 31 from final scores, 3 need a tap (each says why)"), the Open list says "odds read on N of M upcoming",
and every upcoming bet without odds says why (a Vigilant bet, or "Odds not read yet: tap Check odds now").

## 36. Novig's whole API, why a scan is slow, and seven third-party odds APIs (2026-09-29, Tj, after v0.20.1)

Tj: "review in depth the entire novig API docs. I read somewhere that it can show the bets I actually placed and grade them and I can place bets through the
API … optimize the vigilant app … Right now the novig scan is slow, even though I tested my key and it says it works." and "research these API: MoneyLineApp,
OpticOdds, PredictionData, LiveFeedAPI, SharpAPI, Betstamp, odds-api.io … free or very cheap … help grade bets".

### 36.1 Novig's API: the answers (details in NOVIG_API.md §14)
| Question | Answer |
|---|---|
| Can it show the bets I placed? | **Only bets placed through an API subaccount.** Orders, fills, positions and the ledger (`GET /v3/orders`, `/v3/portfolio/fills`, `/v3/portfolio/positions`, `/v3/account/subaccounts/{keyId}/transactions`) all belong to one subaccount's wallet, and a trading key reaches only its own subaccount. Bets placed in the Novig app come from Tj's cash wallet, which **no route reads**. The `trading::read` key Vigilant already holds *can* read those routes, but its subaccount ("Vigilant", never funded) has no orders. |
| Can it grade them? | For a subaccount's bets, yes and exactly: the ledger's `SETTLEMENT` rows, and an outcome's `status` (`WIN` / `LOSS` / `PUSH` / a decimal price for a fair-market-value void) are Novig's own grade, fair-value voids included. For app-placed bets, no: a settled market leaves the catalog (re-verified 2026-09-29: three finalized markets from `data.novig.com` answer 404), and the public CSVs say `finalized` but not who won. ESPN and MLB box scores stay Vigilant's grader. |
| Can I place bets through it? | Yes: `POST /v3/orders` with a `trading` key (`IOC`/`FOK` is a taker bet at `1 − best opposing bid`; pregame it is fee-free), after the management key funds a subaccount from the cash wallet. Not built: it moves real money (§36.9). |
| Does Vigilant use every feature it can? | Everything for reading: signed catalog, `GET /v3/limits` pacing, ETag/304 books, the websocket `book` channel with the one big subscribe, `events` left unsubscribed for the 2,048 cap. Unused: `trades` (last price, volume), the websocket's `private` and `lifecycle` channels, `bbo` (undocumented shape), `data.novig.com` CSVs, and the whole account/execution half. |

### 36.2 Why a scan feels slow (measured live from this container, 2026-09-29)
- A league's first bets wait for **every** fair-odds source of that league (v0.19.2). The free ones: **Kalshi took 27 s** for its 57 series at the 2 a second Vigilant paces it to
  (game lines for a league arrive first: NFL at 1.2 s, MLB at 8 s, the last league's at 13 s; props after that, done at 27 s), **Polymarket 13 s** (one league after another,
  pages one after another). Novig itself is not the wall: the key's websocket needs ~8 s before its first bulk subscribe (the `stream` bucket, 512 at 4 a second, after the 32-token
  upgrade), which hides inside those seconds; REST alone would be 16 books a second, 75 s for 1,200 prices.
- **Kalshi's real limit.** A light `GET /markets?limit=1` took 300 requests at a sustained 20 a second and 200 at ~35 a second with **no** 429 (Kalshi documents 20 reads a second for a basic
  account: `docs.kalshi.com/getting_started/rate_limits`, "Basic 200 tokens/s, 10 a read"). Vigilant's real read is the nested-markets route
  (`/events?series_ticker=…&status=open&with_nested_markets=true&limit=200`), **0.5–0.8 MB a reply uncompressed**: at 2 a second all 12 requests passed, at 4 a second 2 of 24 got
  429 (no `Retry-After`), at 6 a second 3 of 36. A first attempt at 6 → 14 a second and four at a time made a scan **slower** (56 s: the 429s cost waits and retries): reverted to the
  measured-safe 2 a second (`KalshiClient`: two series in flight to hide a reply's own delay, three tries a page, the pace unchanged). Only an authenticated Kalshi key might lift it
  (unverifiable without one: see §36.10).
- **Polymarket 13 s → 5.6 s** (fixed in v0.20.2): the first page alone, and when it is full the next pages three at a time instead of one after another (`PolymarketClient.WAVE`).
- Settings › Novig API's "Last scan took …" line now names the slowest fair-odds sources ("fair odds 27 s (Kalshi 27 s, Polymarket 6 s, Pinnacle 2.1 s)") so the next "slow" comes with
  the culprit. **Test key** now also measures, on the phone, what the key gets (`NovigLiveCheck`): its own limits from `GET /v3/limits`, a signed-catalog read, five book reads through the key
  vs five public, and the websocket (connect, subscribe to 10 markets, seconds to the first books and how many arrived). That is the first time the live feed is checked against the real API.

### 36.3 The seven third-party APIs (pages read 2026-09-29; prices as published; earlier notes in §4 for SharpAPI, odds-api.io, OpticOdds, Betstamp)
| API | Novig? | Free / cheapest paid | Speed | Player props | Grades bets? | Verdict |
|---|---|---|---|---|---|---|
| **MoneyLine** (moneylineapp.com, `mlapi.bet/v1`, REST) | No (DK, FD, BetMGM, Caesars, ESPN BET, Fanatics, Hard Rock, BetRivers, **Pinnacle**, bet365, Bovada, BetOnline) | **Free $0: 1,000 requests a month, 10 a minute, every endpoint, commercial use allowed.** Starter $29: 150,000 a month, 60 a minute. Pro $149: 1.5M, 200 a minute. Business $299: 5M, 1,000 a minute. **1 credit = 1 request** for every standard endpoint (its docs) | "updates continuously", no figure | Yes, with L5/L10/L25/season hit rates, precomputed no-vig fair odds, +EV and arbitrage endpoints; NFL, NBA, MLB, NHL, NCAAF, NCAAB | Scores, statuses and **box scores and player stats by event/date** (NFL, NBA, MLB, NHL); no bet grader | **The only one worth a test.** Free tier can't feed a scan (1,000 requests a month = 33 a day). If its player-props call returns whole slates with Pinnacle's lines, Starter at $29 could be one fast fair-odds source next to Kalshi/Polymarket. Unknown: props payload, which stats, latency. Needs Tj's free key to measure (§36.10) |
| **OpticOdds** | Yes ("trial basis", sales-gated) | No public price: "Book a Demo" | "1 million odds per second", push stream | Yes, plus futures and alternates | **Yes: `GET /api/v3/grader/odds`** (`fixture_id` + `market` + `name` → Won / Lost / Refunded / Pending / Half Won / Half Lost, with scores; errors like "Unsupported player over/under") and game/player results | The only real bet-grader API of the seven, and enterprise-priced. Not needed: Vigilant's ESPN/MLB grading now settles the props CNO lists (v0.20.1) |
| **PredictionData** (predictiondata.io) | Yes (a page for it; also Pinnacle, Kalshi, Polymarket, DraftKings, 200+ books) | Prices are in a page the fetch couldn't render (`/pricing`); docs: `X-API-KEY`, 25 requests a second default, monthly quota by plan, REST pull and an SSE "Markets Stream" | Real time (SSE) | Yes (`/markets`: moneylines, spreads, totals, props, futures) | Not documented | Unknown price; Vigilant already reads Novig directly and free. Only worth a look if Tj wants one paid multi-book feed |
| **LiveFeedAPI** | Not mentioned | €79 a month (no odds), €199 live score, €399–599 Pro, bookmaker packages €1,290–6,500; 14-day trial, 30 a minute | "<100 ms" | Bet-builder rules, soccer/cricket/MMA/horse racing focus | Verified settlement data for football (soccer) and cricket | B2B for operators. No. |
| **SharpAPI** | Yes, **Hobby ($79) and up only** | Free: 12 a minute, 2 books (DK, FD), 60 s delayed. Hobby $79 (120 a minute, 5 books). Pro $229 (300 a minute, 15 books, +EV). Sharp $399. Streaming +$99, live state +$79 | SSE only as a paid add-on | Yes | No | No: Novig is free direct, and the free tier has neither Novig nor Pinnacle |
| **Betstamp** | Yes | Trial keys "for evaluation", no public price | "sub-second", REST `/api/markets` and SSE `/v1/markets`, 200+ books | Yes | Not on the API (a separate consumer Bet Tracking product) | Sales-gated. No |
| **odds-api.io** | Yes (pre-match and live main markets, **no player props**) | Solo $65 (2 books, 5,000 requests an hour), Starter $129 (5), Growth $239 (10), Pro $299 (15); websocket doubles the price; **free keys paused indefinitely** (2 recreational books, 100 an hour) | <150 ms on the websocket | No | No | No |
Since 2026-09-25 Novig's own API is free with a key, so a reseller's Novig feed has **no advantage** here (no signature, no location check, no rate limit of its own): none of the seven improves the Novig side.
None gives a free way to grade bets that ESPN + MLB's Stats API don't already give. **Nothing here is free *and* useful today; the cheapest thing worth testing is MoneyLine's free key.**

### 36.4 Placing bets through the API: the decision Tj has to make (not built)
What it would do: a "Bet" button on a +EV card places a `FOK`/`IOC` taker order at the exact taker price (`1 − best opposing bid`) for the preset stake from a funded "Vigilant" subaccount,
so the fill is instant, the real fill price and fee land in the Tracker with no ✓ tap, and every bet is graded by Novig's own `SETTLEMENT` ledger row (fair-value voids included).
What it needs: (1) a `trading` key: the phone's Keystore already holds one for the Vigilant subaccount (`vigilant_novig_trading_<stamp>`, not yet used), or a new one; (2) money in the subaccount
(only the management key can move it: `POST …/transfer` `fund`, so the app would ask for the management key each time, or Tj funds it himself in a script); (3) the Novig app opened within
3 days (the placement geolocation), no VPN; (4) KYC passed. What could go wrong: an automated order is real money (a bug, a wrong side or a stale price loses it; mitigations: a hard per-bet
and per-day cap, a confirm tap, re-read the book right before sending, FOK so nothing rests); the subaccount is a separate wallet from the cash wallet Tj's app bets use; whether the Novig app
also shows the subaccount's trades is not documented; GOLIVE voids resting orders (not relevant to IOC/FOK). QA (`api.qa.novig.com`) with test money exists to build and try it risk-free first.
**Offered to Tj; needs his yes** (BRIEF.md: no major change without approval, and this one moves money).

### 36.5 What Tj can do now
1. **Send me the line under Settings › Novig API after a scan ("Last scan took …") and, after updating, the result text of Test key** (it prints limits, book speeds and the live feed's timing). That says
   whether the key, the live feed or a fair-odds source is the slow part, on the phone itself.
2. Optional, free: sign up at moneylineapp.com for the free key (1,000 requests) and give me the key through Settings (never commit it), to measure the props call.
3. Optional: a free Kalshi account's API key might lift its 2 a second pace (docs promise 20 reads a second): unverifiable until one exists; Kalshi signs requests with RSA-PSS.
4. Say whether to build API betting (§36.4), and if yes whether the cash wallet's money should be moved by the app (management key typed each time) or by him.

### 36.6 MoneyLine's free key, tested (2026-09-29 ~15:20Z, Tj gave the key; 3 of its 1,000 requests used; the key is in INBOX.md only)
`GET https://mlapi.bet/v1/…` with `x-api-key`; every reply carries `x-ratelimit-limit: 10` (a minute), `-remaining`, `-reset`. **Not a usable fair-odds source:**
- **Stale:** every NFL event's `fetchedAt` was **13:09Z when it was 15:18Z**: over two hours old (Vigilant refuses anything over 5 or 10 minutes, `Freshness`).
- **No Pinnacle in what came back.** `/v1/odds?league=nfl` listed 29 books: DraftKings, FanDuel, BetMGM, Caesars-family, BetOnline, Bovada, PrizePicks, Underdog, and the exchanges **Novig, Kalshi, Polymarket, ProphetX**;
  `/v1/player-props` (3 events) had DraftKings, FanDuel, BetOnline, Courtside, PrizePicks, Underdog and others, **no Pinnacle**, no exchange.
- **Heavy:** `/v1/player-props?limit=3` was **3.1 MB** (2.1 s); `limit` is at most 50 events a page: one league's props could be 50 MB. Game lines are light (78 KB for 3 events, 1.4 s).
- 1 credit per request, 10 a minute, 1,000 a month on the free key. Verdict: **no**; nothing to build. Not stored anywhere in the app.

## 37. Betting through Novig's API and grading from Novig's books (v0.21.0, 2026-09-29; Tj: "Build the betting through the API function, and include the API grading bets feature for the tracker system")

### 37.1 What was built
| Piece | Where | What it does |
|---|---|---|
| `NovigTradingClient` | `data/novig/trading/` | place order (`IOC`), get order, orders by status, fills (paged), positions, ledger rows, balance; Novig's refusals in words (`NovigApiException.advice`: 403 KYC, 422 wallet/position cap, 423, 451 network or location, 429) |
| `ApiBetPlanner` (pure) | same | the checks and the arithmetic: pregame only, market open, fee known, **fair odds' age known and inside `Freshness`**, a book read ≤ 15 s ago, per-bet and per-day caps, the edge still ≥ the minimum at the best price, then the ladder walked cheapest first: contracts, limit price (the deepest level reached, on Novig's grid), average price, cost, payout, EV |
| `ApiBetPlacer` | same | one bet at a time; re-plans on a fresh book at the confirm and **refuses if the price moved above the confirmed ceiling**; `IOC` at that ceiling (buys what's there at that price or better, never rests); waits for the order to end, reads its **fills**, logs a `TrackedBet` from them (stake = dollars paid + fee, price = the average paid, EV from the fair the bet rested on, `orderId`, `contracts`, `paid`, `fee`, `fillIds`); a lost answer is looked up by its `clientId` (twice, in every status) before it's called failed, and is never re-sent; after the order is in, recording runs even if the screen is gone |
| `NovigBettingSetup` | `data/novig/signing/` | "Enable betting": uses a trading key already in the phone's Keystore that Novig accepts, else **revokes the subaccount's live trading key and mints a replacement** from a new phone keypair (retrying while Novig finishes revoking, ≤ 90 s); fund / withdraw with the management key (`fund`/`defund`, polled until Applied); the management key is in memory for the call only |
| `ApiSettler` | `data/tracker/` | grades API bets from **Novig's ledger**: `SETTLEMENT` rows naming the bet's market, fill or order: payout = a full win → won; = the cost → push; else settled at a fair value (exact profit); nothing paid and the position still held → waiting (flagged after a day); nothing paid and the position gone → a loss, **cross-checked against the score feeds** (they say won/pushed → left to a tap with "check Novig"; unreadable → the loss is taken 6 h after the start); a tapped result is never overwritten; Novig's payout beats a feed's opinion |
| `ApiBetSync` | same | adds fills Novig has that the Tracker doesn't (a lost answer, an order placed as the app closed), without EV; runs before every grading pass, so nothing waits for a tap; "Sync with Novig's fills" adds all of them |
| UI | Settings › Novig API key › Betting through the API; the Bet button on +EV and CNO cards; the Bet sheet; Tracker | enable, wallet balance, add/take back money, limits (start amount, most per bet, most per day, smallest edge), sync, turn off; the sheet shows price, cost, payout, edge and wallet, and the button is the confirm; a bet placed through the API says so, its stake is exact (no stake/price edit) and it shows "graded by Novig's own books" |

### 37.2 Safety rules (all tested)
Nothing is sent before the confirm tap; a game that has started is refused (CNO bets use the earlier of CNO's and Novig's start); the edge must still be positive (default ≥ +1%) at the price now; the fair odds must be fresh and their age known; a stake over the per-bet limit, or a day's total over the daily limit (the device's midnight), is refused; the same outcome isn't bet twice unless asked; scanning paused blocks it; one order at a time; a moved price is refused, never chased; an order that filled nothing places no bet and moves no money; Undo of a ✓ never removes an API bet.

### 37.3 Not verified against the real API (no key here): what to watch on the phone
The order/fill/ledger shapes are the documented ones and mock-tested. **Unseen live:** (1) what a `SETTLEMENT` row's `ref` really names (market, fill or order: all three are matched) and that a **loss leaves no row**; (2) that a settled position leaves `GET /v3/portfolio/positions`; (3) that `startsAfter`/`startsBefore` on the ledger are milliseconds (the catalog's are); (4) how long Novig takes to apply a revoke before a replacement trading key can be minted (retried for 90 s); (5) whether the Novig app also shows the subaccount's trades. Every ledger grade is noted on the bet ("Novig paid $4.00 on 400 contracts: a win") so a wrong reading is visible, and a loss is only taken when the score feeds agree (or 6 h have passed with none).

### 37.3a First real orders (2026-09-29 ~17:50Z, v0.21.1): refused, nothing placed, one fixed field
Tj's first two real orders (Taylor Trammell Under 0.5, Michael King Over 4.5) both came back "Novig said no: clientId: UUID parsing failed … found `v`". The placer sent `clientId = "vigilant-" + UUID`; Novig parses it as a UUID (the spec says `format: uuid`, which the design missed: NOVIG_API.md had it as a free string). Nothing was placed (the request failed while being parsed) and the sheet said so plainly, so the refusal path worked as designed. v0.21.2: a plain UUID for the order and the transfer, `placeOrder` refuses anything else before sending, the mock Novig in `ApiBettingTest` now refuses a non-UUID the way the real one does, and the spec was re-read for every other field the placing, grading, funding and key-mint paths send (only `clientId` was wrong). Also from that read: a lost answer's order is looked for in `PENDING` too (a queued order), on this outcome only and across pages; an `OPEN` order with nothing left resting counts as finished.

### 37.4 First run (what Tj does)
1. Novig app open (location check), no VPN. 2. Settings › Novig API key › Betting through the API › management key ID + .pem › **Enable betting** (the old trading key is replaced if the phone doesn't hold it). 3. **Add $5** to the wallet (management key again). 4. Set the amount to $1 and the per-bet limit to $5 for the first bets. 5. Tap **Bet** on a +EV card, read the sheet, **Place bet**. 6. After the game, the Tracker grades it from Novig (Tracker › Grade now). Report anything odd, especially a bet that stays open a day after its game.

## 38. The Tracker's current EV for every open bet, and sorting the list (2026-09-29 ~16:46Z, Tj, after v0.21.0: "right now it only checks cno scanned EV. Make it update the EV for every single open bet, including bets added from vigilant scanner … add filter options on the top … date placed, current EV, amount of bet, scanner used … make sure the tracker is telling me the current, up to date EV, which is devigged and compared to the actual odds that I placed the bet at")

### 38.1 What "current EV" is today (read from the code, X1)
`TrackedBet.nowEv = nowFair / cost - 1`: the **fair probability now (devigged)** against the **cost of the price actually paid** (`cost` = price + any fee; for an API bet the real fills' average and fee). That is exactly Tj's definition, so the arithmetic stays; what was wrong is *coverage* and *honesty about age*. Two writers set it:
- **`BetRecheck` ("Check odds now")** reads the bet's **CrazyNinjaOdds game page** (`gameUrl`). Only bets found through CNO have a `gameUrl`; **every bet logged from Vigilant's own cards (`BetTracker.track`, `logApi`) has none, so Check odds now never read them** (the report counted them as `vigilantOnly`: "update with each Vigilant scan").
- **`BetTracker.observe`** (after every Vigilant scan, incl. background auto-scan) sets `nowFair/nowEv` for open bets the scan happened to price. It only prices what the scan *plans*: leagues switched on in Settings, games inside "Days ahead", lines a fair source quotes, props inside the props window and the per-scan credit/game caps. A bet outside any of those is **never** priced, and `observe` returns early when the fair line is unchanged, so `nowAtMs` (the "3 min ago" on the card) didn't advance either. `checkOdds()` started a normal feed scan for these ("startScanForOpenBets") and hoped.
- The card said "now +3.2% EV" whatever its age (a number from yesterday read "now"), and gave no reason when a bet had no number except for the Vigilant-bet sentence.

### 38.2 Design (approved by the request)
1. **A dedicated bets-only pricing pass for Vigilant's own bets** (`OpenBetPricer`): a second `Scanner` instance (`betsOnly`) so it never touches the feed's catalog, books or props snapshots; same fair sources (shared clients, so their rate gates hold), same freshness rules (`Freshness`: only quotes ≤ 5 min old, 10 min for games > 3 h away, ever price), same devig/blend as the feed (Settings). Its settings are widened for the bets, not Tj's feed filters: the leagues of the bets, a window reaching the last bet's start, every market family, props windows/caps no smaller than the bets need. Its catalog is cut to the bets' markets and games, so props sources (`needsCatalog`) fetch only those games; only the bets' Novig books are read (never `watch()`, which would replace the feed's live-feed subscription; no end-of-scan re-read).
2. **Every open bet ends with a current EV or a stated reason.** CNO bets keep the CNO page read (CNO's own devig, shown as CNO's); a CNO bet whose page can't be read, and every Vigilant bet, goes to the pricing pass. A bet still without a fair price gets `nowNote` ("no fair-odds source lists this game", "no book quotes exactly this line", "fair odds older than the limit", "Novig no longer lists this market", "league not supported", "Vigilant scanner is off"), shown on the card in place of a stale number.
3. **Honest age.** The card says "now" only for a read inside the last `FRESH_LABEL_MS`; older reads say "as of 2h ago" in a muted colour, and a started game says "last read before the start". `nowVia` says whose fair it is (CNO's books / Vigilant's blend); `nowBooks` how many books.
4. **Sort and filter the bets** (Tracker › Bets): sort by Date placed, Current EV (best first: fair now vs the price bet at), Amount (stake), Game start; tap the chosen one again to flip; a Scanner filter (All / Vigilant / CNO). Bets with no current EV sort last.

### 38.3 Built (v0.21.1) and what was measured
- `Scanner(betsOnly = true)` (own instance: catalog cut to the bets' markets/games and re-made every pass, fair-odds snapshots cleared each pass, no `watch()`, no end-of-scan re-read), `BetsScope.settingsFor`, `BetPricingReasons.explain`, `OpenBetPricer`, `BetTracker.applyPricing`/`applyFair` (`observe` now also advances a read's age once a minute old), `TrackedBet.nowVia/nowNote/nowNoteAtMs`. `BetRecheck.Plan/Report` count the pass; `MainViewModel.checkOdds` runs the CNO read and the pass together and hands the bets CNO didn't read to the pass; the bet sheet's "Price now" prices one bet.
- **Live (real Novig board, free Polymarket + Kalshi, Tj's feed narrowed to NFL / 1 day so it would have hidden most of them):** 10 open moneyline bets in 5 leagues (WNBA, MLB, NFL, NHL, NCAAF), one game per league, **all 10 priced in 26.7 s** (Kalshi is the wall again: RESEARCH.md §36.2), each side of a game complementary (0.626 / 0.374): `LiveOpenBetPricerTest` (`VIGILANT_LIVE=1`).
- **Shared sources:** the pass and the feed scan use the same client objects, so Kalshi's 2-a-second gate and the props sources' locks hold across both; PropLine/Odds API props re-use a game's props for ≤ 2 min across the two, so a scan and a Check odds now don't buy the same game twice.
- **Cost:** a pass asks each metered source once per league it needs (pinnapi's 100 a day counts it like a scan), so a Check odds now with Vigilant bets in the list is about one scan of the leagues those bets are in. Bets read within the last minute are left alone.
- **Not built (offers):** keeping the sort/scanner choice across app restarts (it survives rotation and the app being recreated); sharing one fair-odds fetch between a feed scan and a pass that start together.

## 39. CNO only means Vigilant is asleep in the background too; what Tj can show to verify grading and tune the app (v0.21.3, 2026-09-29; Tj: "if I have cno only turned on in the settings that it doesn't scan vigilant in the background and waste api usage")

### 39.1 The audit (every path that can spend a Vigilant API's allowance)
| Path | Before | Now |
| :- | :- | :- |
| Background auto-scan cycle (`AutoScanner.cycle`, run by `AutoScanService` from an alarm or boot) | **Auto-scan "Both" ran Vigilant's whole scan even with the scanner on CNO only** (deliberate in v0.18.0, and Settings said so); "CNO" ran CNO even on Vigilant only | each part is gated on the scanner choice: `ScanSettings.autoScansCno` / `autoScansVigilant` (`activeAutoScan` is OFF when neither is left, so the service stops); the notification and the Settings hint say what really runs |
| `ScanRunner.start` (the one door every feed scan goes through: Scan button, widget, background cycle) | no check | refuses when `!vigilantOn`, whoever asks |
| Tracker "Check odds now" / "Price now" (`OpenBetPricer`) | guarded in the view model only | also refuses inside `OpenBetPricer.run` |
| Widget "Vigilant's scan again" timer, Scan/Recheck buttons, mini-window buttons | guarded (`vigilantOn`) | unchanged; switching to CNO only now also stops a scan already running |
| `SettleWorker` / grading | free score feeds (ESPN, MLB) and, with betting on, Novig's own ledger for your bets | not fair-odds APIs; unchanged (your bets are graded whatever the scanner) |
| CNO's own reads, Novig's price now for CNO's listed bets | CNO feature | unchanged |
Tests: `CnoOnlyAsleepTest` (the predicate for every scanner x auto-scan x paused, the runner never scans on CNO only, Both / Vigilant only still do), `OpenBetPricerTest` (asks Vigilant's APIs for nothing on CNO only), `AutoScanTest` (the cycle is gated; the notification and hint texts). Vigilant only now leaves CNO asleep in the background the same way (a "CNO"-only auto-scan choice then has nothing to do and stops).

### 39.2 Grading check (Settings › Diagnostics, needs betting set up)
`ApiGradingCheck` (data): the wallet, the positions, the ledger of **every kind** for the games' window (an ask without `kind`, so a loss's absence of a SETTLEMENT row can be seen against its FILL/FEE rows), each API bet with its order, fills, contracts, paid, the Tracker's grade and note, the ledger rows that name its market / order / fill (and which one the `ref` was), and whether Novig still holds the position. It reads only your own trading data. It answers the four unverified things (NOVIG_API.md §15): what a SETTLEMENT `ref` names, whether a loss leaves no row, whether a settled position leaves the list, and whether the time window brings the rows back.

### 39.3 Diagnostics (Settings › Diagnostics › Show report → Copy)
`Diagnostics.report` (app, pure): version and phone; every setting that changes speed or API use; what the background scan really runs; the last scan's timing line, errors and per-source results; each API's calls, refusals and per-key allowance (last four characters only); CNO's last read and errors; the background scan's last cycle; the Tracker (open, current EV coverage, why bets aren't priced, bets waiting since their game and why, results). **Never a key.**

### 39.4 What Tj shows, and when
1. **Grading:** after a game you bet through the API ends (baseball ~3-4 h), open the Tracker, tap Grade now, then Settings › Diagnostics › **Grading check › Copy** and paste it. Best with one win and one loss (a push or void if one happens). Add a screenshot of that bet's card. A bet still open a day after its game: the same.
2. **Tuning:** Settings › Diagnostics › **Show report › Copy**, right after a normal Scan, and again right after a Tracker Check odds now; plus a sentence on what felt slow or wrong and when.
3. **CNO only:** set the scanner to CNO only with auto-scan on Both, leave it an hour, then Show report: the API usage counters for Kalshi, Polymarket, Novig fair odds, The Odds API, PropLine and Pinnacle must not have moved.


## 40. Diagnostics read, which APIs run short, the widget, Settings tabs, pinned tabs and filters (v0.22.0, 2026-09-29 ~19:05Z; Tj, after v0.21.3, with two Diagnostics reports: "See what you can optimize from the diagnostics … 1) often when I switch from vigilant to another app, the widget opens automatically. Only open the widget if I press the icon … 2) tell me which apis deplete too quickly for daily use so I can add more keys 3) the settings section is getting very long … Maybe tabs on the top. 4) … keep the top navigation tabs sticky to the top")

### 40.1 What the two reports say (Sep 29, ~18:54Z and ~18:57Z, 79% of the UTC day gone; one Check odds now between them)
| API | Before Check odds now | After | One Check odds now cost | Read |
| :- | :- | :- | :- | :- |
| Kalshi | 624 calls | 727 | **+103** (about 50 s at the measured-safe 2 a second) | unmetered; the time is the cost. 57 series for Tj's 6 leagues; only 4 were empty when probed (2026-09-29), so skipping empty series would save ~7%: not built |
| Polymarket | 84 | 128 | +44 | unmetered |
| PropLine | 141 calls, 132 used | 155, 156 | +14 calls = **+24** of its day (props cost more than one) | 1,000 a day: on pace for ~200 |
| PinnWire | 44 | 48 | +4 | 100 a day: 48 used at 79% of the day, on pace for ~61 |
| pinnapi | 1 call, 2 used | same | 0 | the backup; untouched |
| The Odds API | 120 calls | 121 | +1 (no credits) | 500 credits a month per key: key …16a7 spent (500, until Oct 1 00:00 UTC = Sep 30 8 PM Eastern), …71c4 162 used, 338 left: 662 of 1,000 with a day to go |
| Novig | 4,319 | 4,386 | +67 | 8 throttled, 12 hours ago; the key's limit is 16 a second |
The last regular scan: 30 s (board 1.1 s, fair odds 27 s, all 965 Novig prices in 29 s), first bet at 20 s, 41 of 88 games matched, 74 +EV, feed 18 bets, no errors. The Tracker after a scan had current EV on **21** of 109 upcoming bets (the scan only covers the feed's); after Check odds now **106** (3 old, 0 none). Check odds now works; what it costs is one whole fair-odds round, because the pass asked every family in every league.

### 40.2 What was optimized
* **The pass asks only for the market families the open bets are on** (`BetsScope.familiesFor`, from `BetGrader.pickOf`: moneyline, spread, total, team total, player prop, or a period market counted as its full-game kind too). Every source already honours `settings.families` (Kalshi picks series by family: 28 of 57 are player props; Polymarket, PropLine's markets and its per-game props, The Odds API's markets and props, Pinnacle's props), so a Check odds now on moneylines and totals no longer reads any props, and one on props alone reads no game lines. **A bet whose wording can't be read for certain asks for every family**, so nothing is left unpriced to save a request. The wording reader is the grader's: it read 98% of Tj's settled bets (136 of 138 were graded from score feeds, 2 by hand). The rescue pass (CNO couldn't read a bet) is now cheap for the same reason: its bets' leagues and families only.
* **Not built, and why:** empty-series memory for Kalshi (4 of 57 empty); re-using the first pass's snapshots in the rescue pass (a per-game source's snapshot depends on which games were asked for, so a re-used one could miss a game: the bets-only scanner clears them on purpose); a per-league family map (`ScanSettings.families` is global).
* **Diagnostics now shows the cost of the last scan and the last Check odds now per API** (`UsageDelta` between two looks at the ledger, `RoundCost`, kept in `AppContainer.lastScanCost/lastCheckCost`), how long each took, and how the Check odds now covered the bets.

### 40.3 Which APIs deplete too quickly for daily use (the answer, and the Diagnostics "Runway" block that keeps giving it)
`Runway.lines` (data, pure) projects each keyed API's allowance to its reset at the pace used so far this period (needs 3 h of a day or 2 days of a month), and says OK / WATCH (projected over 80%) / SHORT (the last of it goes before the reset, with when), or SPENT until when. `Runway.roundsNote` turns a round's cost into "a scan costs 6 → 16 a day per key". At Tj's pace today all four are OK. Where they run short with more use:
1. **PinnWire (Pinnacle), 100 requests a day per key**: about 1 request per league per pass (6 leagues = 6 a scan; a Check odds now 4). ~16 scans a day per key, and background auto-scan every 10 min would spend it in under 3 hours. pinnapi (another 100 a day, game lines only) takes over after it. **The one to add keys to first**: Pinnacle is the sharpest book, and without it fair odds fall back to Kalshi, Polymarket and PropLine's books.
2. **The Odds API, 500 credits a month per key**: 1 credit per market per league (6 leagues × 3 markets = 18 a scan) and 1 per prop type per game for props, with "credits/scan: no limit" set. With PropLine first it is only asked for what PropLine couldn't give (0 fetched in the last scan), which is why 662 of 1,000 lasted a month; it is the one that empties in a couple of scans if PropLine is off or down. Set "Most credits per scan on props" to about 24 if it should never surprise (recommended, not forced).
3. **PropLine, 1,000 a day per key**: ~11-25 a scan (lines per league + props per game), a Check odds now 14 requests. ~40+ scans a day: fine.
4. **Kalshi, Polymarket, Novig**: unmetered; Kalshi's cost is time (27 s a scan floor, ~50 s a Check odds now), Novig's is its 16 a second key limit.
Keys of one feed: the terms of PinnWire and pinnapi forbid getting round a rate limit with extra keys of the same feed (Settings says so); a different feed's key (pinnapi behind PinnWire, PropLine, The Odds API) is the honest way to add runway.

### 40.4 The widget opens only from its button
`ScanSettings.miniWindow` ("Float over Novig", on by default since v0.12) made leaving Vigilant with bets on the feed or a scan running bring up the widget or picture-in-picture by itself (`MainActivity.onUserLeaveHint`, PiP auto-enter, and `floatOverNovig` when a bet is opened in Novig). Now off by default, moved to off once in every saved file (schema 10; a saved "on" can't be told from the old default), and the Settings switch is an opt-in ("Also open it when I leave Vigilant"). The button on the +EV and CNO tabs (`showWidget`) is unchanged. Tests: `MiniWindowTest`, `AltMarketsTest` (schema), `ScreenshotTest.settingsOfferTheMiniWindowSwitch`.

### 40.5 Settings tabs and pinned tabs and filters
* Settings is seven tabs in a scrolling tab row outside the scrolling page (`SettingsTab`): Scan (pause, scanner, start window, background auto-scan and alerts), CNO & widget (CNO filters and refresh, widget; "Widget" when CNO is off), Fair odds (method, devig, sources with their keys, sportsbooks), +EV feed, Betting (bankroll, Kelly, bet-slip amount, Novig key and betting through the API), Usage & keys (each API's meter, keys backup), Tools (Diagnostics, Grading check, About). CNO only hides Fair odds, +EV feed and Usage & keys with Vigilant's scanner. The choice survives rotation (`rememberSaveable`), each tab opens at its top. `SettingsTabsTest` checks every old section is on exactly one tab.
* Pinned (`stickyHeader` + `StickyBar`): the **Tracker**'s Stats | Bets, its period chips or Open / Settled / All, and Sort and Scanner; the **+EV** tab's league chips and start window; **Games**' league chips; **CNO**'s scanner chip, filters and start window. Sort and Scanner became two **menu chips** ("Sort: Current EV · best", "Scanner: CNO"; tap a sort again in the menu to turn it round, as before): as wrapped chips the pinned bar was 311 dp of a 420 dp screen in the test, as menus 161 dp (`StickyHeadersTest` caps it at 175). A different tab, filter, sort or scanner starts the list at its top. Tests: `StickyHeadersTest`, `TrackerUiTest`, `TrackerSortTest`; screenshots 4h, 5_settings_tab_*.

## 41. True closing line value (v0.25.0, 2026-09-30; Tj: "Do any of these stats check true closing line value based on the closing line for each bet? If not, make a system that finds the true closing odds for each of my bets … the percentage of my bets that beat closing line value … the average percentage that my bets beat the closing line … Keep this stat line running forever … filter … (all time, today, yesterday, last 3 days, last week), and an option to remove outliers (bets over 5% different than closing line value)")

**Answer to the question (audit, before v0.25.0): no.** `TrackedBet.closingFair` was the last pregame fair line any read happened to write
(a scan's `observe`, Check odds now, the pricing pass), however long before the start; the Stats' "Avg CLV" and "Beat the close" used it for
every bet, open ones included. Only the background auto-scan (off by default, CNO bets only, within the hour) ever read a line near the start.

**What v0.25.0 does (`ClosingLine`, `ClosingCapture`):**
- A **true close** is a pregame read made in the last **15 minutes** before the start (`TRUE_CLOSE_MS`), and it's final once the game has
  started. A bet placed inside those 15 minutes with nothing read after it closes at the line it was bet at. A bet placed after the start has no
  close. Everything else has no CLV (it's "started with no close read", counted, never guessed).
- **CLV** = closing fair probability / cost − 1: how much better the bet's odds were than the closing (devigged, fair) odds. Positive = beat
  the close. Same fair lines and devig as the bet's EV (CNO's books for CNO bets, Vigilant's own fair odds for the rest).
- **The capture:** an exact alarm (`USE_EXACT_ALARM`, allowed in Doze) goes off **6 minutes** before the next open bet's start
  (`ClosingLine.nextAt`) → `ClosingReceiver` → expedited `ClosingWorker` (needs a network) → `ClosingCapture.run`: every open bet starting
  within **12 minutes** is read (CNO's game page for CNO bets; one bets-only Vigilant pricing pass for the rest, only while Vigilant's
  scanner is on, so CNO only spends no API credits). A failed read retries 2 minutes later, never in the last minute (`closeTriedAtMs`).
  Nothing is read while scanning is paused. `AppContainer` re-arms the alarm whenever the bets change; the boot/update receiver re-arms it
  after a reboot. The auto-scan's older within-the-hour capture still runs too.
- **Stats:** the Stats tab's "Closing line value" card (`ClvStats`) runs over every bet ever (never reset), with its own periods by when each
  bet was placed, in local calendar days (All time, Today, Yesterday, Last 3 days = today and the two before, Last week = today and the six
  before) and "Hide outliers" (|CLV| > 5%). It shows the share that beat the close, the average CLV and the average EV at bet of the same
  bets, and counts how many are waiting for their close and how many started without one. The old CLV row and the bet card's CLV column now
  use true closes only; the bet sheet says "CLV so far" before the close. Diagnostics prints the all-time line and the next capture time.
- **Limits:** bets tracked before v0.25.0 mostly have no true close (their last read wasn't near the start). A phone that's off or offline at
  the capture misses that close. Inexact alarms (if Android ever withdraws exact-alarm permission) can fire late enough to miss the window.
  Historical closing prices (to back-fill old bets) would need a source that keeps price history (Kalshi candlesticks, Polymarket's
  prices-history); not built.

## 42. Closing lines after the start, when the phone was off (v0.26.0, 2026-09-30; Tj: "My phone will not always be on. The app has to be able to find clv from closing lines after the games started or even days later. Espn may have the closing lines information. Check for sources that the app can use for this and implement it")

**Sources checked live from the dev container, 2026-09-30:**

| Source | What it keeps after the start | Markets | Cost | Verdict |
| :- | :- | :- | :- | :- |
| **ESPN core odds** `sports.core.api.espn.com/v2/sports/{sport}/leagues/{league}/events/{id}/competitions/{id}/odds` | The provider's (DraftKings in 2026, ESPN BET in 2025) **open / close / current** moneyline, spread (line + price) and total (line + over/under prices), for finished games and past seasons (checked: NFL 2026-09-27, 2026-09-28 MNF, 2025-10-12; NCAAF, MLB, NBA Jan 2026, MLS with a 3-way draw price). A second "Live Odds" provider has no close. | Full-game ML / spread / total | 1 scoreboard + 1 odds request per game, free, no key | **Used** (`EspnCloses`): devigged across the two closing sides; a spread/total only when it closed at the bet's own line. |
| ESPN site scoreboard / summary | `odds` is null for final games; summary `pickcenter` keeps only the current (= close) line without open/close | — | — | Not needed |
| ESPN core `…/odds/{provider}/propBets` | 600+ player props per NFL game, with **lines only** (`open.target`, `current.target`), no prices | Props (lines) | — | Not usable for CLV (no odds) |
| **Novig trade history** `data.novig.com/reporting/trade-data/<ET date>/trades.csv` (+ `index.json`, `markets.csv`) | Every trade on Novig, from 2026-08-03, one file per Eastern day published ~09:00Z the next day; ~240k rows / 36 MB a day, **sorted by time**, byte ranges served (HTTP 206). Columns `timestamp, outcomeId, marketId, contractSeries, league, marketType, tradeType, legs, cost, qty, side`: a STRAIGHT trade is a TAKER row on one outcome at `cost/qty` and MAKER rows on the other at `1 − that` | Every market, props and alternate lines included, keyed by the Tracker's own outcome ids | 0.5–4 MB per kickoff's half hour (MNF 2026-09-28: 3.8 MB, 2.6 s) | **Used** (`NovigTradeCloses`): volume-weighted price of the bet's outcome in the 30 minutes before the start (pregame trades carry no fee and both sides add to 1, so it's fair already); the window is found by halving byte ranges; read on any network (Tj: unlimited data, accuracy first). `markets.csv`'s daily close is end-of-day (in-play/settled), not the pregame close. |
| Novig public `catalog/markets/{id}/trades` | Newest-first trades, but a finished game's markets 404 a few hours after it ends (NOVIG_API.md) | Every market | — | Not reliable days later |
| Kalshi `series/{s}/markets/{ticker}/candlesticks` | Hourly/minute bid/ask/price candles for settled game-winner markets (checked KXNFLGAME-26SEP28PHICHI-PHI) | Game winners mainly | free | Possible extra source; not built (ESPN + Novig cover it) |
| Polymarket `clob …/prices-history` | Needs the token id; a probe returned an empty history | Game winners | free | Not built |
| The Odds API historical | Paid plans only (10 credits a call) | Everything | paid | No |

**Cross-check (Eagles @ Bears, 2026-09-29 00:15Z, moneyline):** ESPN DraftKings close −185 / +154 devigged = PHI 0.6225; Novig's trades
in the last 30 minutes = PHI 0.6337 (624 trades); Kalshi's hour before ~0.65. Within a point or so of each other.

**What v0.26.0 does (`CloseBackfill`, run with the grading on app open / Grade now / Check odds now and in the 3-hourly background worker):**
every started bet without a close read before the start ([§41]) is looked for 10 minutes after its start: ESPN first (game lines, right away),
then Novig's trades (every market, from the next morning, any network); "not yet" retries every 3 hours; a bet no source will ever have is
marked and left; 60 days at most. A close read before the start still wins; the back-filled one fills the gaps. The CLV card counts closes
by source; the bet sheet shows each started bet's close, where it came from and its CLV, or why there's none yet; Diagnostics says what the
last look found and how many KB of Novig data it cost. Checked end to end against the real ESPN and data.novig.com
(`LiveClosesTest`, VIGILANT_LIVE=1).

## 43. Five sources checked, ParlayAPI adopted, and what's worth buying (v0.27.0, 2026-09-30; Tj: "Research these … sources and see if they can help improve anything in the app, whether it is speed or accuracy or grading or finding historical closing lines to calculate clv … implement … anything … Then research online if there is anything I can buy … My budget is around $40 per month"; then "I will buy the parlay-api $5 per month starter plan … take full advantage of the paid API … prioritize its use if it can do anything better … fall back if I don't have the paid parlay-api anymore, and consider if the free API is still worth using")

**The five sources (checked live from the dev container, 2026-09-30):**

| Source | What it is | Verdict for Vigilant |
| :- | :- | :- |
| The Odds API `apps-script/ClosingLinesAnyMarket.gs` | A Google Sheets script: finds each game's *final* start time from `/v4/historical/sports/{s}/events` (games get delayed), then reads `/v4/historical/.../events/{id}/odds` at that time. Historical endpoints are **paid plans only**, 10 credits per market per region per snapshot. | Tj's Odds API keys are free: unusable. Its one lesson (read the close at the game's real start, not the scheduled one) is already how ESPN's close and ParlayAPI's closing lines work: ParlayAPI enforces "priced before the listed start" itself. |
| TheRundown (therundown.io/api) | Odds + scores + play-by-play. Free: 3 books (BetMGM, DK, FD), no props, no history, **5-minute delay**, 200k data points. Starter **$49**: 60-second delay, 7-day history, no closing lines. Opening and closing lines only on Pro **$149** (30-s delay); real time from Ultra $399. | Over budget and slower than what Vigilant has. No. |
| r/ParlayAPI "The complete sports betting data stack for 2026" | Reddit blocks reads from this container (curl and fetch both get the bot wall); no copy found by search. It's ParlayAPI's own subreddit, so it was judged by testing ParlayAPI itself (below). | See ParlayAPI. |
| DeliciousPipe1326/edge-scanner | A Python/Flask scanner over The Odds API: multiplicative devig of one sharp book (Pinnacle, then Betfair/Matchbook with commission), exact-point matching, quarter Kelly, arbitrage, middles with NFL key numbers. | Vigilant already does all of the +EV parts, with more (several devig methods, consensus of books, Kalshi/Polymarket, freshness gates). Arbitrage and middles need two books; Tj bets only Novig. Nothing to port. |
| skills.rest `odds-api-historical` (brandonalfred/fortuna-app) | A Claude skill wrapping The Odds API's historical endpoint: snapshot at 10 am ET for a day's games, closing odds 30-60 minutes before each game's `commence_time`, 10 credits per market per region, paid plans only. | Same wall (paid Odds API). Its "the historical endpoint only returns games that haven't started at the snapshot time" is why a close must be read *before* the start: what Vigilant's capture and ParlayAPI's closing lines do. |

**ParlayAPI (parlay-api.com), tested keylessly (`/v1/try`, `/v1/sandbox`, `/v1/meta/*`, `/v1/pinnacle-coverage`, its OpenAPI):** The Odds API's
format at `/v1` (same params, `x-requests-*` headers per its docs), 15+ books including **Pinnacle** (live on NFL, NCAAF, NBA, NHL, MLB, WNBA,
MLS: `/v1/pinnacle-coverage`), ProphetX, BetOnline, bet365, Bovada, the US books and Novig itself. Plans (`/pricing`, 2026-09-30): free
1,000 credits a month, 48 h of history; **Starter $5: 20,000 credits, 7 days**; Pro $20: 100,000, 30 days; Business $40: 1,000,000, 90 days,
WebSocket/SSE. What each call costs (`/v1/meta/credit-costs`): `/odds` markets × ⌈books/10⌉, and it serves `alternate_spreads` and
`alternate_totals` for a whole league (The Odds API sells those per game); `/props` **3 credits for a whole league's player props, every book**
(The Odds API format: 1 per prop type per game, ~60 for an NFL Sunday); `/sports/{s}/closing-lines` 5 (last pre-start price per book, h2h,
spreads, totals, `daysFrom` ≤ 30); `/historical/closing-lines.json` 1 per 1,000 rows (player-prop closes, one UTC day a call, cached 6 h
server-side); `/scores` 1-2; `/exchange/{s}/markets` 3 (Novig/ProphetX books); `/clv` max(5, 2 × bets). Streaming is Business-and-up only.
Its pre-game period markets (1st half, F5) are live-only, and team totals come through `/props`, so those stay with the existing feeds.

**What v0.27.0 does with it:**
- **Closing lines (the best use, any plan):** `ParlayCloses`, asked first by `CloseBackfill`: Pinnacle's prop close from the daily file (the
  bet's UTC date and US date), Pinnacle's game-line close from `closing-lines` (exact line only, devigged). A key past its plan's window gets
  `403 HISTORICAL_LIMIT`, read as "nothing to find"; out of credits is "look later". Starter's 7 days cover a phone that was off for days.
- **Scans (paid plans):** `TheOddsApiClient(feed = PARLAY)` for Pinnacle + 9 books with alternate spreads and totals (5 credits a league), and
  `ParlayPropsSource` for every book's player props in one `/props` call per league (3 credits, 10,000 rows a page, pages joined). ParlayAPI's
  book keys `caesars`/`betonline`/`hardrock` are read as The Odds API's `williamhill_us`/`betonlineag`/`hardrockbet`, so a book two feeds carry
  counts once. Merge order puts ParlayAPI ahead of PropLine (its quotes carry their measured age; PropLine runs ~20 s behind).
- **Pacing (`CreditPace`):** a scan may spend today's share of the month plus whatever earlier days left unspent, never tomorrow's; the last 300
  credits are kept for the closes. Past today's share ParlayAPI stands by quietly (`CreditsHeldBackException`: no error banner; Diagnostics
  says so) and the other feeds price, as they do between its refreshes anyway (quotes older than a couple of minutes never price, §24). Stateless:
  read from the meter, so it survives restarts and follows the server's own figures. On 20,000 credits: ~650 a day ≈ 80 league refreshes.
  Background auto-scan cycles leave half of each day's share for the scans Tj starts himself (found in the v0.27.0 full test: 15-minute
  cycles for four leagues would otherwise spend the day's share by mid-morning, leaving the evening, when he bets, with none).
- **Free plan (1,000 credits):** worth keeping for the closes alone (Pinnacle's close for CLV, a few credits a league-day); not for scans (it
  would be spent in a day). A key the server says has 1,000 or fewer is used for closes only; a key not yet heard from gets one scan call,
  whose headers say its plan.
- **Fallback:** no key, the switch off, a spent or refused key: ParlayAPI isn't asked (or its calls fail as any metered feed's do) and
  PinnWire/pinnapi (Pinnacle), PropLine, The Odds API, Kalshi, Polymarket, ESPN and Novig's trades carry on unchanged.

**What's worth buying (≈$40/month budget, "only if this money can be put to great use"):**
- **ParlayAPI Starter, $5/month:** yes. Pinnacle's closes for CLV back 7 days, a league's props in one call, alternate lines, ~650 credits a day
  for scans. Tj is buying it (2026-09-30).
- Pro $20 (100,000, 30 days of closes): only if the meter shows the day's share running out on busy days.
- Business $40: its extra is streaming; Vigilant's scans are on demand, so no.
- Not worth it: TheRundown ($49 Starter: 60 s delay, no closes; $149 for closes), The Odds API paid ($30 for 20,000: 6× ParlayAPI's price),
  SportsGameOdds ($99+), theoddsapi.com Business ($99), a Pinnacle-only feed ($299).

## 44. Tj's first diagnostics with ParlayAPI, its best practices, and the key's own credit count (v0.28.0, 2026-09-30; Tj: "Review this diagnostic report", "Let me know exactly what you need to make sure I'm using parlayapi to its fullest extent but also efficiently and not wasteful", "Look at parlayapi docs and use whatever they have in my starter api that can help the vigilant app", "Also study this: https://parlay-api.com/docs/best-practices … It allows the API key to tell the app how many credits I have left. Add this to the app so the meter is accurate")

**What the report showed (v0.27.0, 2026-09-29 11:06 PM ET, Starter key 26 of 20,000 used):**
- **"ParlayAPI props: … has spent today's share"** with 19,974 left: a real bug. Game lines and props run at once on one key; a big props reply
  charged at used = 8 landed after lines replies that said 18, and the meter read "the count went down" as a new billing cycle (periodStart =
  now), so the pace thought a month had begun at 11 pm and allowed an hour's share. Fixed: a lower count within 2 minutes of the last answer and
  within 100 credits is a late reply, kept out (`UsageMeter.STALE_WINDOW_MS/STALE_SLACK`); a real reset (a bigger drop, or after a gap) is
  still followed.
- **The opposite risk, found beside it:** a plan bought on the 29th was paced as if it had existed since the 1st (29 days "unspent": ~19,000
  credits allowed on its first day). Now paced from the key's first answer (`KeyUsage.firstSeenMs`), and the first day always gets a whole
  day's share.
- **Check odds now: "CNO read 1 … 81 CNO couldn't read"**, no failures listed: the pass stopped because CNO asked for a pause, and the rescue
  pass (Vigilant pricing the rest) zeroed the counts that said so. CNO's game pages read fine from the dev container (18 bets 3 at a time,
  ~1.1 s a bet, `LiveCnoBooksSpeedTest`), so the pause was CNO's answer to the phone at that moment. Now kept: `CnoState.lastPause` (which
  read, CNO's words, how long) and the round's note keeps CNO's own counts ("CNO read 1 of 82 (81 not tried: stopped early, …)").
- **"100 started with no close found yet"**, 69 of them marked final by ESPN + Novig before ParlayAPI existed: a new close source now reopens
  them once (`TrackedBet.closeAskedOf`, `CloseBackfill.reopened`); Starter's 7 days reach the recent ones.
- **PinnWire "SHORT"** at 22 of 100 three hours into the UTC day: pinnapi's 100 take over when PinnWire's run out; the runway now judges the two
  together (WATCH at that pace: ~170 of 200).
- Not changed: Kalshi's 29 s (paced at the measured-safe 2 a second, §36.2; it runs beside Novig's 59 s of reads, which the key's 16/s caps);
  The Odds API standing by (PropLine covered every league). The record: 160 settled, +8.2% ROI, beat the close 58%, average CLV +0.5% against
  +2.4% EV at bet.

**ParlayAPI's best practices (parlay-api.com/docs/best-practices, read 2026-09-30) and what Vigilant does:**
| Practice | Vigilant |
| :- | :- |
| Key in the `X-API-Key` header, not the query string (query strings leak into logs) | ParlayAPI calls send the header; The Odds API keeps `apiKey=` (its only way) |
| `X-RateLimit-Limit/Remaining/Reset` on every reply (paid: limit "unlimited", remaining = credits in the period, reset = epoch seconds) | `CreditHeaders` reads them (only as the month's figures when the limit is "unlimited" or monthly-sized: the free tier's are per second) with `x-requests-*`/`x-credits-*`; the reset time becomes the key's cycle (`KeyUsage.resetAtMs`) for the meter, the pace and the runway |
| Retry 502 once after 1 s, honor 503/429 `Retry-After`, never retry 400/401/403/404 | One retry for 502-504 and a dropped connection; 429 through the key pool's wait; 4xx never retried |
| Log `X-Request-ID` on every non-2xx | In the error text (Diagnostics shows errors) |
| Degraded mode: `/v1/meta/source-quality`; `breach`/`stale`/`missing` unsafe for live execution | `ParlaySourceQuality` (free, every 5 min): `stale`, `missing`, or `breach` past the book's own stale threshold leave that book out of ParlayAPI's quotes; a book a minute behind stays (Vigilant's own age rule is 5-10 min) |
| Cost: `/v1/usage`, quotes, burst alerts (50/75/90% webhooks in the dashboard) | The pace, the meter line; the dashboard alerts are Tj's to switch on |
| WebSocket/SSE, idempotency keys, ETags | Business-tier streaming; no POSTs; meta endpoints aren't polled |

**The key telling the app its credits:** `ParlayAccount` reads each key's free `/v1/meta/api-key-check` (tier, credits left, reset; "no
credit cost" per its docs) when a scan starts (every 5 minutes at most), when Settings › API usage or Diagnostics opens, and when a key is
added; `UsageMeter.recordBalance` puts the key's own figures in the meter. Its field names aren't documented, so they're read loosely
(`credits_remaining`/`monthly_credits`/`reset_at` and kin, nested or not) and the reply's headers win where they carry the same thing.
**Unverified until a real key answers:** that check's field names, `/props` row fields (`event_id`, `home_team`, `away_team`,
`commence_time`, `player`, `market_key`, `line`, `over_price`, `under_price`, `age_seconds`, `period`), and the closing-lines file's rows.

**Also from the docs, used:** `/props?maxAgeSec=600` (rows older than a quote may be to price stay on the server) and `/odds?commenceTimeTo=`
(the scan's window plus a day): smaller, faster replies for the same credits. **Looked at, not used:** `/v1/prediction-markets/{sport}` (1
credit; Kalshi and Polymarket prices relayed with a 90 s cache, where Vigilant reads both directly, fresher, for free), `/scores` (ESPN and
MLB grade for free), `/consensus`/`/ev` (Vigilant prices its own), `/v1/clv` (the Tracker does it), streaming (Business tier).

## 45. ParlayAPI checked with Tj's own key, and every call matched to its docs (v0.29.0, 2026-09-30; Tj: "Here is the parlayapi key for you to use … Make sure the app is making full use of parlayapi's features and speeds", "Make sure the app can read my parlayAPI usage credits remaining", "Thoroughly research parlayapi docs to get endpoints and everything matched and all commands and usage correct", "if the API offers any other features I may want in the app let me know")

The key lived only in the session's scratchpad (never in this public repo, a fixture, a checkpoint or a commit); every fixture below is
ParlayAPI's public odds data with the key, key fingerprint and account email removed. Tested 2026-09-30 ~04:05–04:50Z; ~130 credits spent.

**Account (free).** `/v1/usage` (alias `/v1/account`, "usage and remaining credits"): `credits_used`, `credits_remaining`,
`credits_total` (plan + `credits_granted`), `period_start`/`period_end` = **the calendar month, UTC** (a plan bought 2026-09-30 02:51Z had
the whole 20,000 for Sep 1 → Oct 1). `/v1/meta/api-key-check`: the same counts, `valid`, `reason` (`credit_exhausted`, `key_inactive`),
and `subscription.period_end_iso`, which is **Stripe's billing date, not the credits reset**. `/v1/meta/usage` reads `credits_remaining: 0`
(broken) but has a 7-day daily breakdown. Both answered live (no cache: `cf-cache-status: DYNAMIC`). → `ParlayAccount` reads `/v1/usage`
first, the key check when that fails, at most once a minute; the pace spreads a new plan's credits over the days left in its month.

**Headers.** Paid replies carry `x-requests-remaining/used/last` (exact) and `x-ratelimit-limit/remaining: unlimited` (no per-second cap,
no reset header). `/v1/meta/limits` lists `X-Rate-Limit-*` too: both spellings are read.

**Costs measured** (x-requests-last): `/odds` = markets × ⌈books/10⌉ (h2h+spreads+totals = 3; +alternate_spreads/totals = 5);
`/props` = 3 a league (one call held every two-sided line; `x-result-truncated: true` names a book writing faster than one read, and
narrowing `markets=` is their fix, which the app already does); `/sports/{s}/closing-lines` = 5; the closes file = 1 per 1,000 rows.

**Shapes that differed from the docs or from what the app parsed:**
- `/props` rows name each book's own market (`player_passing_attempts`, `batter_total_bases`, "Total Bases"): `ParlayMarkets` normalizes
  key + label to Novig's stat. NHL props (goals, points, assists, shots on goal, saves, power-play points) are ParlayAPI-only in Vigilant.
- `/sports/{s}/closing-lines` answers **flat rows** (`home_odds`, `away_odds`, `draw_odds`, `last_update` = the start), and **only h2h**
  (15 of 15 NFL rows, 4 of 4 MLB), though its docs promise spreads and totals. → moneylines there; spreads and totals from the closes file.
- The closes file (`/historical/closing-lines.json?source=pinnacle`) has Pinnacle's game lines too, one team a row (`player_name` = the
  team, `over_price` = its price; totals as `player_name: "Total"` with both sides), and **many snapshots are hours old** (MLB props 12–15 h
  before first pitch, game lines ~75 min): a price taken more than 2 h before the start isn't used as a close.
- `/odds` alternates are **Pinnacle's alone**, already sent by PinnWire/pinnapi: bought only when neither is on (2 credits a league saved).
- `/odds` leaves games under way out unless `include_live=true` (no extra cost): sent when Settings' live games are on.
- **History by plan** (`/v1/meta/limits`, public): free 48 h, Starter 7 days, Pro 30, Business 90. `closing-lines` past it is 403
  `HISTORICAL_LIMIT`: nothing older than the key's plan is asked (`ParlayAccount.historyDays`).

**New use: Check odds now's backup for CNO** (`ParlayBooks`). Tj's "crazyninjaodds didn't answer" was mostly CNO answering *without the
bet* (its game page lists current lines only, and the soonest games' lines had moved): five such pages in a row stopped the pass and the
toast blamed CNO. Now a page without the bet is no reason to stop, a real no-answer is told apart, and either way the bet is re-priced from
ParlayAPI's books (one `/odds` + one `/props` call per league, kept 2 minutes) shaped like CNO's page, so `CnoBooks.check` judges them
with CNO's exact math (each two-sided book devigged worst case, the lower of mean and median).

**Offered to Tj, not built (his call):** `/sports/{s}/best-bets?books=novig` (10 credits a league: ParlayAPI's own ranked +EV list at
Novig, a third list beside CNO's and Vigilant's); `/v1/verdict` (5 credits a bet: BET/LEAN/FAIR/PASS for one bet); `/line-movement`
(2 credits: a bet's price history, steam); injury status (already on every `/props` row, free: an OUT/Questionable tag on prop cards);
`/v1/meta/movers` (free: biggest moneyline moves); `/live/period_markets` (2 credits: 1st-half/quarter/F5 lines from more books);
`/v1/meta/usage`'s daily breakdown (free: a per-day chart in API usage). Streams (SSE/websocket) need the Business tier.

Tj then asked (2026-09-30 ~05:10Z) for all of these to be built in a new session: the build guide, with real answers probed for each
(best-bets, verdict, movers, injuries, period markets, usage breakdown; line-movement only ever answered busy or empty), is PARLAY_API.md §6,
the tasks TASKS.md §M.

## 46. More sportsbooks for each scanner, and whether they'd make fair odds better (v0.35.0, 2026-09-30; Tj: "can I add more sports books to scan on vigilant either for cno scanner or vigilant scanner? Can parlayapi do it? Would it make the app more accurate? If so, add sports books to each scanner")

Checked live 2026-09-30 ~20:00Z (CNO's page once, ParlayAPI ~10 credits of Tj's key).

- **CNO scanner: nothing to add from the app.** CNO's +EV form has no book choice for the fair line: its fields are devig method, odds
  range, minimum EV, fewest books, sides, result count, sport/league, live/main, and one dropdown, `DropDownListSportsbookSite_All`, which
  is the book you bet AT (27 options: 17 = Novig). The fair line is CNO's own, from the ~20-25 books on its game pages (FD, DK, CZR, MGM, BR,
  BB, TSB, B365, FN, HR, FL, BV, BO, **CS = Circa, PN = Pinnacle, PX = ProphetX, KI = Kalshi**, ST, PP), sharp ones included. Adding
  ParlayAPI's books to Vigilant's own worst-case check of CNO bets would cost credits on every widget/Tracker read for books CNO already has.
- **ParlayAPI can't add a sharp book today.** Its `/v1/bookmakers` lists `bookmaker_eu` (BookMaker, sharp for US sports) and `superbet`,
  but a 20-book `/odds?markets=h2h` read returned **no BookMaker, Superbet, Betr or Polymarket line** for any MLB (3) or NFL (32) game.
  What it did return beyond Vigilant's 10 game-line books: BetRivers, Hard Rock, Fliff, betPARX (soft) and Kalshi (Vigilant reads Kalshi
  itself). `/odds` costs markets × ⌈books/10⌉: 11+ books doubles game lines from 3 to 6 credits a league, for soft books that barely move
  a fair line Pinnacle (and BetOnline, ProphetX) already anchor.
- **Props: already every book ParlayAPI has.** An unfiltered `/props` read (MLB hits+Ks: 1,356 rows; NFL pass+rec yards: 5,925) had only
  books already in `ParlayProps.BOOKS`, plus pick'em apps (PrizePicks, Underdog, Sleeper, Pick6: flat payouts, rightly left out). One
  page is 3 credits whatever the books.
- **Some books are one line under two names.** BetRivers and betPARX (both Kambi) posted **94% identical NFL moneylines** (15 of 16);
  bet365 and Hard Rock 75% identical on 28 NFL props (small sample: watch it). LowVig is BetOnline's reduced-juice site: the same line,
  devigged. Counting both halves of such a pair gives one line two votes in the consensus: less accurate, not more.
  **Since v0.44.2 (Tj, 2026-10-02: "it is counting identical odds from sister sports books ... multiple hard rock sports books just in
  different states"):** CNO's own book check follows the same rule: its state columns (HR-IN/FL/IL/OH, ST-NJ/CO/IA/AZ/VA, MGM-ON, FDYW)
  are one company each (`CnoBooks.company`), their devigged fairs averaged into one vote; counts and "agreeing" are by company.
- **The Odds API free keys don't get Caesars or Fanatics** (its docs: "paid subscriptions only"); `espnbet` is theScore Bet now.
- **What was changed (free, no extra credits):** the reference books (PropLine reads every one at no extra cost; The Odds API, its backup,
  asks the first 10) are now Pinnacle, BetOnline, DraftKings, FanDuel, BetMGM, BetRivers, **Hard Rock, Bovada, Fliff**, Caesars, Fanatics,
  theScore Bet; **LowVig out** (a saved list loses it only beside BetOnline: `ScanSettings.migrate` schema 11); the Settings picker has no
  10-book cap any more (The Odds API still takes 10). PropLine goes from 8 books to 10 independent ones: more props priced by 2+ books,
  and a wider consensus where Pinnacle has no line. `MoreBooksTest`.
- **What would help most next:** a second sharp book for game lines. Circa is on CNO's pages but no API Vigilant has carries it;
  BookMaker via ParlayAPI once it has lines (re-check `/odds?bookmakers=bookmaker_eu` now and then, 1 credit).

## 47. Tj's first v0.35.0 Diagnostics, read end to end (v0.36.0, 2026-09-30 ~21:34Z; Tj pasted the report with no words beside it: V3's purpose, "Claude can run deep analysis on the app and know what is working or broken and how to improve")

**Working:** scans (105 s, 1,474 Novig prices through the key at 14/s, none refused, 93% judged against a fair line), every fair-odds source,
CNO reads, background auto-scan (CNO every 5 min, service up), grading (nothing waiting over 6 h), phone permissions (all allowed), the
closing capture alarm, credits (ParlayAPI 19,400 of 20,000 left; The Odds API's first key spent to the reset with the second carrying on).

**What the numbers say about accuracy (the headline):**
- CNO's bets are real edges: 264 bets, CLV +1.3%, beat the close 68%, ROI +9.4%, results +9.90 vs +1.95 expected.
- **Vigilant's own bets are not, so far:** 68 bets, CLV −1.2%, beat the close 37%, ROI −7.8%. Its open bets agree: EV when bet +4.8% →
  now +1.3%, the fair line moved away on 11 of 12 that moved. Worst markets overall: totals (CLV −2.6%, beat 22%), 1st-half/inning
  totals (−5.9%, 0%), team totals (−1.4%); player props (mostly CNO's) +1.5% / 72%.
- Bigger edges are more real: 4%+ EV beat the close 91% (CLV +2.0%); 1-2% EV only 58% (CLV +0.1%, ROI −5%).
- Overall CLV +0.6% with 60% beating the close; EV when bet averages +2.5%, so the EV shown runs ~1.9 points above what the close says.

**Why Vigilant's edges might be overstated (not provable from this report: bets didn't record which books made their fair):** BLEND takes a
lone sharp book as the whole fair line when fewer than "at least 2 books" price it (`FairValue.compute`: `sharp.isNotEmpty() -> avg(sharp)`),
and Kalshi and Polymarket count as sharp when tight; a thin exchange's total that lags Pinnacle makes a "fair" line the market then moves
away from. Also possible: soft books lagging inside the 30% average. v0.36.0 keeps each bet's `FairBasis` (method, sharp books, book count)
and Diagnostics splits CLV by it ("Bets by what made their fair odds") and lists Vigilant's bets against the close one by one, so the
next reports can say which. Until then the recommendation to Tj: lean on CNO's list; treat Vigilant's totals and 1st-half totals as
unproven; prefer Vigilant edges of 4%+.

**Diagnostics' own mistakes (fixed in v0.36.0):** a busy ParlayAPI props board (503 `props_temporarily_busy`, retried once already) was a
FAIL; The Odds API's props backup matching nothing (it's only asked for what PropLine missed) was a WARN; "spent until 2h ago from now"
(a future time worded as the past) and a WARN though the second key carried on; 9 Novig throttles in 8,255 calls (7 h ago) was a WARN;
**"a scan costs 600 ParlayAPI credits → 33 a month"** was the key's month-to-date count landing in one round (a key new to the ledger, or
the count re-synced to ParlayAPI's own figure): rounds now count what the app's own calls were charged (`KeyUsage.charged`); a scan is
~40 credits. Closing-line coverage (58%) counted 53 ✓ marks imported before the Tracker kept bets (no league, no Novig ids: no source can
ever close them) and their note blamed a missing ParlayAPI key; both fixed. CLV averages now say how many closes they rest on.

**Game matching 36% (30 of 83):** 63 of Novig's games in the window were tennis (WTA 40, ATP 23) and 2 NFL futures; ParlayAPI and PropLine
matched all 19 other games. Tennis is priced only by Kalshi and Pinnacle today. ParlayAPI has tour-wide keys (`tennis_atp`: 102 events,
Pinnacle on 75, FanDuel 36, ProphetX 21; `tennis_wta`: 60; 3 credits a tour), **but its Pinnacle rows put SET lines (±1.5 sets, 2.5
sets) in the match event and game lines in a separate "Name (Games)" event, while bet365/Caesars put game lines in the match event**:
merged naively a sets spread −1.5 would price Novig's games spread −1.5, a fake edge. Worth building only with that split handled per book.

## 48. Lag, then a crash, when switching tabs during a Vigilant scan (v0.36.1, 2026-09-30; Tj: "The app just crashed a couple times. Both times it was scanning vigilant and I tried to switch tabs, which got very laggy then crashed")

No stack came with it (the app kept none), so the cause was found in the code and measured where it could be. A scan's progress is
emitted after every Novig price read (`Scanner.Progress.emit` per book: ~14 a second through the key; Tj's no-limit scans read 1,474 in
105 s), and so is the API usage count (`UsageMeter.recordCall` per request). `MainViewModel.follow()` and the usage mirror copied each one
into `UiState` on the main thread, `follow()` rebuilding the +EV feed (`feedOf`) every time even when only the progress moved. The whole
`UiState` is collected at the root (`MainActivity`), so every tick recomposed the app, and the tab badges recompute the +EV feed and
CNO's list (with its books-agree check) on each recomposition. `feedOf` alone is small (0.46 ms over 5,000 priced sides on a desktop JVM,
maybe 5-10 ms on the phone); the full recomposition 20-30 times a second is what kept the main thread busy, so a tab switch (a new screen
to build) couldn't get through and Android ended the app. The notifications already limited themselves (ScanService 2 a second, auto-scan
1 a second); the screen didn't.

Fixed: the screen takes a running scan's newest state at most every 350 ms (`followThrottled`: a StateFlow keeps only its latest value
while the collector waits, so the scan's end is never missed), the feed is rebuilt only when the result itself changed and off the main
thread, and the usage meters take the newest count at most once a second. `ScanMirrorTest`: 1,500 ticks at 14 a second arrive as ~300
states instead of 1,500, the last one always.

So the next one says why by itself: `AppExits` keeps a crash's stack as the process goes (files/last_crash.txt → Recent problems as "App
crash"), and Diagnostics reads Android's own record of each exit (`ApplicationExitInfo`: crash, "not responding" with the main thread's
stack from its dump, low memory…) into "How the app last ended", with an "App stability" health check.

## 49. Tennis through ParlayAPI (v0.37.0, 2026-10-01; Tj: "Build tennis through parlayapi")

The offer from §47: 63 of 83 Novig games in a scan window were tennis, priced only by Kalshi (winners) and Pinnacle direct. ParlayAPI's tour
keys (`tennis_atp`, `tennis_wta`; Challengers included) add bet365, Caesars, DraftKings, BetMGM, ProphetX and FanDuel, plus Pinnacle's set
lines, for 3 credits a tour. The answer's traps and how each is handled are in PARLAY_API.md §6.11: Pinnacle's match event holds SET lines and
a "(Games)" twin holds its games lines, every other book's match event holds games lines (BetMGM and DraftKings at ±1.5 games, so size can't
tell), one match is often several events, doubles ride along. Set lines get a period of their own (`RefBookMarket.PERIOD_SETS`) and price
only Novig's `SET_SPREAD` and `TOTAL_SETS`, which nothing priced before (71 and 63 markets that day). The same split was found in the closes
file, where it had already been feeding wrong CLV to tennis spread bets (a games −1.5 took the sets −1.5's close: 38% instead of 52%); fixed.

Live check (`LiveParlayTennisTest`, 6 credits): Novig 64 matches; ParlayAPI 51 ATP and 42 WTA matches after merging (from 93 and 64
events), 112 set lines; 63 of 64 Novig matches paired (the 64th, a WTA quarterfinal, wasn't in ParlayAPI's answer); lines with a fair
price: MONEY 63, SPREAD 50, TOTAL 60, SET_SPREAD 41, TOTAL_SETS 36. Novig's price against the fair line, median |EV| per kind: MONEY 2.0%,
SPREAD 1.7%, TOTAL 1.9%, SET_SPREAD 3.4%, TOTAL_SETS 4.0% (a sets line pricing games would sit 20+ points off). Not done: each player's
games won (ParlayAPI has no tennis team totals), 1st-set winners (not in /odds), ParlayAPI's movers board for tennis.


## 50. Faster background scans, and CLV from real closing lines (v0.38.0, 2026-10-01; Tj: "for the cno scanner background auto-scan feature, add to the settings options for it to scan every 3 minutes, 1 minute, 30 seconds, and 15 seconds. Make sure the app is properly tracking clv based on real closing lines and the actual odds I placed the bet at")

**The interval.** `ScanSettings.autoScanSeconds` (was `autoScanMinutes`; schema 12 moves a saved file's minutes over once, written back when the app opens)
offers 15 s, 30 s, 1, 3, 5, 10, 20, 30 and 40 minutes (`AUTO_SCAN_SECONDS_CHOICES`, labels `intervalLabel`). Three things the old design would have
got wrong at 15 seconds, all fixed and pinned in `AutoScanTest`:
- *A dropped alarm ended the schedule.* The next alarm was armed when a cycle started; one that went off while the cycle was still running was
  dropped (`runCycle`'s "already running" guard) and nothing re-armed it. At 15 s that is certain (a slow CNO page is enough); at 5-40 minutes with
  CNO + Vigilant it needed a scan over the interval. The cycle's end now arms the next alarm from the live interval (`AutoScanClock.nextAtMs`), unless
  the service is stopping. The start-of-cycle alarm stays, so a cycle that is killed can't end the schedule either.
- *The 30 s minimum gap* (`MIN_GAP_MS`) is 5 s now.
- *Vigilant's own scan* (API credits, ~100 s) ran inside every cycle. In CNO + Vigilant it now starts at most every 4 minutes
  (`AutoScanClock.vigilantDue`; every cycle at 5 minutes and slower, as before; Scan now in the notification always runs it): 15, 30 and 60 s cycles
  run it every 4 minutes, 3 minute cycles every 6. The Settings hint says so and counts the credits from that (360 scans a day at 15 s, not 5,760).
  While it runs the cycle waits for it, so CNO isn't read for that ~100 s; CNO only never waits.

What a CNO cycle costs: one CNO list read, Novig's price for the top 8 candidates and their game pages (re-used 4 minutes), and the open bets starting
within the hour. At 15 s that is ~5,760 list reads a day to crazyninjaodds.com (`CnoFeed`'s 3 s gap and pause-on-429 still apply). Android may space
exact alarms out while the phone is idle (Doze's allow-while-idle quota); "Unrestricted" battery use is the Settings hint's existing advice. Unverified
on a phone: how tightly a 15 s schedule holds with the screen off for hours.

**CLV audit** (`ClosingLine`, `ClvStats`, `ClvPlacedPriceTest`). CLV = closing fair probability / cost - 1, the same as the EV formula at the close.
- *The price used is the price paid.* `cost` = price + taker fee (none pregame). A ✓ logs Novig's live price shown at the tap (`livePick`), a bet placed
  through Novig's API logs the average of its fills plus their fee (`logApi`), and a price Tj corrects in the Tracker (`setPrice`) replaces the first.
  Worked: +141 → cost 0.41494, close 0.45 → +8.45%; corrected to +150 → cost 0.40 → +12.5%; fills 100 @ 45¢ + 300 @ 47¢ → 46.5¢, close 0.50 → +7.5%.
  What can't be exact: a bet placed by hand in the Novig app. Novig's API sees only the Vigilant subaccount's own wallet (NOVIG_API.md §14.2), so that
  bet's price is the one shown at the ✓ unless Tj corrects it.
- *A bet is never "closed" at its own price.* Before, a bet placed in the last 15 minutes with no read after it closed at the line it was bet at, so its
  CLV equalled its own EV at bet and every +EV bet "beat the close". Now it has no close until a real one is found (the back-fill, which asks for these
  bets: ParlayAPI's Pinnacle closes, ESPN, Novig's trades), and counts under "started with no close found yet".
- *The close is the read nearest the start.* One read ~6 minutes out was the close. Now a bet read more than 3 minutes before its start is read once more
  ~110 s before (`needsFinalRead`, `FINAL_LEAD_MS`; the alarm, retries and last-minute cut-off are the capture's own), and the later read wins. A read of
  older prices never replaces a fresher close (`ClosingLine.supersedes`; a cached page arriving after a fresh read used to). With the auto-scan on at
  under 5 minutes, bets in their last 15 minutes are re-read at the cycle's pace, once a minute each at most (`AutoScanClock.closingFreshMs`).
  Cost: a second bets-only Vigilant pass at the final read for bets with no CNO page (credits like the first), a CNO page per CNO bet.
- Not changed: the close is the devigged fair line from the same sources as the bet's EV (CNO's books, Vigilant's fair odds, both averaged), not Novig's
  own closing price; history closes (ParlayAPI Pinnacle, ESPN, Novig's trades) still only fill bets with no read before the start.

## 51. Auto-bet: placing CrazyNinjaOdds' bets with nobody confirming (v0.39.0, 2026-10-01; Tj: "Build the auto get feature, which would automatically bet each bet without me doing anything at all, including automatic bets in the background as the cno scanner is on in the background")

**What it is.** Settings › Betting › Auto-bet (off by default; turning it on asks once, plainly). Inside each background CNO auto-scan cycle (`AutoScanner.cycle`),
after the list, Novig's price now and the best bets' books are read, `AutoBettor.run` places every CNO bet that passes Tj's criteria through Novig's API from the Vigilant
wallet: best edge first, one order at a time, at most 5 a cycle. Alerts are computed after it, so a placed bet doesn't also alert. The check interval IS the background CNO
scan's (15 sec … 40 min): Tj's "use the same options for cno scanner refresh time intervals", read as one setting shown in both places, not a second timer.

**Tj's seven choices → code.** (1) books agreeing 2/3/4/5+ = `CnoBooks.Check.agreeing` (two-sided books whose own worst-case fair alone makes the price +EV);
(2) smallest edge +2/2.5/3/3.25/3.5/3.75/4% or typed (floor 0.5%) = the EV the CNO card shows at Novig's live price, re-checked by the planner at the real book price;
(3) CNO only = `autoBetsNow` needs the CNO scanner running in the background; Vigilant's own bets and ParlayAPI's picks never reach it; (4) stake ⅛/¼/½ Kelly, $1 or typed =
`AutoBet.stake` (Kelly = bankroll × fraction × (fair − price)/(1 − price), worked: +100 at fair 0.515 on $1,000 → $3.75 / $7.50 / $15.00; +150 at 0.42 → ¼ = $8.33; −300 at 0.77 → ⅛ = $10.00,
so it changes with each bet's odds), held to the per-bet maximum, to Novig's available dollars and to the wallet, floored to the cent, under $1 skipped (never rounded up);
(5) books pricing both sides 1/2/3 = `Check.twoSided`; (6) most per bet, typed; (7) above. Pregame only, the wallet read before every pass, tracked "just as if I were to manually bet it":
`BetTracker.logApi` from the fills (stake = dollars + fee, real price, `orderId`), hidden from the lists like a ✓ (`markPlaced`, shared with the Bet sheet), flagged `TrackedBet.auto`.

**Safety, beyond every `ApiBetPlanner` check** (pregame, market open, fair odds fresh with a known age, a book read ≤ 15 s, per-bet and per-day limits, edge still ≥ minimum level by level).
Each was mutation-checked (removed → a test fails): Novig's price for the bet read in the last minute (no live price, no bet); the order book's best price must be within 3 points of the price
it was judged at (`placeAuto`; guards a wrong-outcome match: the finder matches CNO's text to Novig's outcome and nothing shows Tj the match), and the outcome Novig's price came from
(`LivePrice.outcomeId`) must equal the outcome the catalog found; one bet per Novig market while one is open (the other side of a line is never bet); a game starting within a minute is skipped;
an edge over 15% is never bet unattended (a stale line or a mismatch, not an edge: Tj can place it by hand); one `orderLock` for the Bet sheet's placer and the auto-bet's, so the same bet
can't be placed by both at once; a refused bet waits 2 min; Novig refusing an order (location, KYC, funds) or the day's limit backs it off 5 min; an order whose answer is lost HALTS auto-bet
(`autoBetHalted`, saved; the order is never re-sent; Tj checks Novig and taps Resume); an order once sent is followed to the end and recorded under `NonCancellable`.
The daily limit is the existing "Most in a day" API setting ($50 by default), shared with Bet-sheet bets.

**What is and isn't verified.** Tj's v0.38.0 Diagnostics (2026-10-01) shows 123 API bets in the Tracker, 21 graded by Novig's ledger, and a funded wallet ($14.24): order placing,
fills and ledger grading HAVE worked live (an earlier version of this section, and an answer to Tj, said no live fill was on record: wrong; the Tracker's own numbers were the record to read).
Still unverified for auto-bet itself: placing from the background cycle with the screen off, Novig's 3-day location check keeping it alive (it stops with a refusal and a note when the
check expires: open the Novig app), and Doze limiting a 15 s alarm schedule. Try it with a few dollars in the wallet, $1 a bet, and watch the first bets in the Tracker.
**A crash mid-order (found re-reading it against Tj's out-of-memory crash, §52):** the process can die after Novig takes an order and before the Tracker has it; the next cycle would find the same bet
with nothing on record and place it again. So an order is marked in flight (`autoBetHalted`, saved) BEFORE it can be sent and cleared only on a definitive answer; a restart finds auto-bet
stopped with the bet named, Tj checks Novig and the Tracker's Sync with Novig's fills, then taps Resume. What it does NOT do: size by game or correlation (several bets on one game can all pass), stop on a
losing streak, or bet anything but pregame CNO bets at Novig. Possible next steps: a per-game cap, a daily-loss stop.

## 52. The out-of-memory crash in Tj's v0.38.0 Diagnostics, and what the report says (v0.39.0, 2026-10-01; Tj pasted it with no words)

**The crash.** `OutOfMemoryError: Failed to allocate a 32 byte allocation … target footprint 268435456` on the main thread, 1:40:41 AM, on screen, from the scan service's progress path. The
stack is only the allocation that failed: the heap (256 MB, the default) was full. Settings at the time: no limit on Novig prices per scan, lines or props per game, "fill the budget", seven
leagues (ATP, WTA, MLB, NCAAF, NFL, NHL, WNBA), ten fair-odds sources, 375 tracked bets. No heap dump exists, so this is reasoning from the code, not a measurement:
- Every partial result (`BookPump.publish`) prices the WHOLE plan again and builds a complete new `ScanResult`, plus a copy of the books map, after every batch of ~30 Novig books: with no limits,
  thousands of markets, ~100 times a scan, the previous partial still alive beside it (and the UI's mirror of it).
- `ScanRunner` kept the last finished result (strongly) for the whole next scan as a fallback if the scan failed: a second full scan's priced lines, and through each `Opportunity.refEvent`
  the previous scan's reference boards.
- The scanner keeps every source's last parsed board per league between scans (`references`; a no-limit props board is tens of thousands of `RefBookMarket`s per sport per source), and
  Novig's last 3,000 books (`bookCache`) for "not modified" answers.
Changed: `android:largeHeap` (a bigger ceiling); `MemoryGuard` (trims the books cache at 75% of the heap; after a forced collection, at 90% a scan ENDS with what it has read and says why in its
errors, instead of crashing the app); a big plan (400+ markets) publishes a partial at most every 2 s (the final result is always priced in full); the fallback result is a `SoftReference`
(under pressure it goes, and a failed scan then shows no old result); Diagnostics gets a Memory block (heap, the scan result's size, the boards and books kept between scans, CNO pages, the Tracker)
with a WARN over 75%, and a crash record now carries the heap (`heap=…/…MB`). Not changed: the parsed boards still stay between scans (a 2-minute re-use saves credits); if the next report shows
them dominating, interning the repeated strings in `RefBookMarket` and dropping boards older than the freshness limit are the next steps.

**The second layer (v0.39.1, 2026-10-01; Tj: "After finishing the release, Investigate and fix the app crashes in the diagnostic"):** v0.39.0's guard ends a scan before the heap is gone, but a
scan that can't finish its pricing inside 256 MB is still a scan cut short, so the allocation itself went. (1) `Pricing.price` rebuilt an `Opportunity` (and its order-book ladder, quote and depth) for
every outcome of the plan on every partial, though a batch of ~30 books changes ~30 markets. `FairMemo.pricedFor` now keeps the LAST plan's priced markets by their place in the plan and re-uses a
market's outcomes while its Novig book is the same object (a re-read makes a new one), the bankroll and Kelly multiplier are the same (a stake is worked from both) and the plan and fair method are
the same; the first partial prices everything, every later one the new books only. Measured on a synthetic 1,200-market scan with 40 partials (a test prints it): pricing allocates 212 MB without
re-use, 23 MB with it (9x); a real no-limit scan has more markets and ~100 partials, so more. Only one plan's outcomes are held (a second held beside it would be the memory this saves). It also
means the old and new partial share every unchanged outcome instead of each holding its own copy. A new plan (every minute, `planFor` keys on the minute, or whenever a fair source answers) prices in
full once, as before. (2) A scan starts by dropping the parsed boards of leagues and sources no longer picked (`Scanner.dropUnusedReferences`): they were kept for the life of the process.
Still reasoned, not measured: no heap dump exists, so the next Diagnostics' Memory block (heap, result size, boards and books held) is what says whether this was the cause. If it shows the
boards dominating, interning the repeated strings in `RefBookMarket` is next.

**What else the report says.** Vigilant's own bets still lose to the close (CLV −1.1% on 27 bets, 37% beat; totals −2.8% on 10 at 20% beat, team totals and 1st-half totals negative, props +0.3%)
while CNO's beat it (+1.5% on 87, 66%); at these counts only the totals gap is more than noise, and it is the same on both scanners (CNO totals −1.7% on 6). 71% of the last week's started bets have a
true close (79 of the 114 from Novig's trades, 21 read before the start: the phone's own capture is the minority); 105 started bets are still looking, most waiting for Novig's file (published the
next morning). Wallet $14.24 with $1 bets, bankroll $185, ¼ Kelly, most per day $500.


## 53. Longest odds for the auto-bet, and what Kelly does with longshots (v0.39.2, 2026-10-01; Tj: "add an option for longest odds of any auto bet … I don't want it to bet anything that is more of a longshot than +130 odds, unless ¼ Kelly betting automatically puts a much lower stake on longshots. Does Kelly do this?")

**Does Kelly stake less on longshots? Yes, in proportion, but it doesn't cap them.** For a contract at price c (what it costs; pays $1) with fair chance p, full Kelly is `(p − c) / (1 − c)`
(`EvQuote.kellyFraction`). Write the edge as EV% `e = p/c − 1` and that is `e × c / (1 − c)` = `e ÷ b`, where b is the profit on a $1 bet (+100: b = 1, +130: 1.3, +300: 3). So at the SAME
edge the stake falls as the odds lengthen. On a $185 bankroll at ¼ Kelly with a +4% edge (tests: `AutoBetTest`): +100 stakes $1.85, +130 $1.42, +200 92¢, +300 62¢, and −200 $3.70. Two caveats:
(1) *(Superseded in v0.39.3, §55: the $1 minimum this paragraph assumed is gone, so those longshot stakes are now bet at their Kelly size, a few cents to 92¢, and the longest-odds limit is what keeps longshots out.)* Under Vigilant's then $1 minimum the stake was skipped, not rounded up, so on a small bankroll ¼ Kelly already skipped most bets past about +150 on its own, but a bigger bankroll or ½ Kelly would not;
and the $1 and typed amounts ignore the odds entirely. (2) Kelly only sizes a bet whose probability is right; the fair price of a longshot is the least reliable one (devigging is worst there, and
the favorite-longshot bias: §8.1, §16), so a big edge on a longshot is more often a bad price than a real edge, and Kelly stakes it anyway. A cap on the odds is a different control from the stake size.
It is also true that the CNO page's own Max odds filter (Settings › CNO, default +150) already limits which bets auto-bet sees; the new limit is Tj's own, in the auto-bet card.

**What was built.** `ScanSettings.autoBetMaxOdds` (American; 0 = no limit, the default, so what ran before doesn't change; chips +100 … +300 and No limit, or a typed amount of +100 or more) held by
every stake mode. Checked twice: `AutoBet.judge` on the price the bet was judged at (the reason has no price in it, so the report counts bets by reason), and the planner on the order book read
just before the order (`BetLimits.maxOdds`, refusal "Novig's best price is now +141, longer than your +130 limit"): a bet judged at +125 whose market then drifts out to +141 is within the 3-point
price match but is not bet. Favorites (negative odds) always pass; a Bet-sheet bet is not held to it. Seven mutants killed (never too long, boundary, judge ignoring it, the +100 floor on a typed
value, planner ignoring it, the bettor not passing it on, the bettor judging the wrong odds).


## 54. Running the auto-bet with the phone off (2026-10-01; Tj: "Investigate whether it is possible for this auto bet feature to work even with my phone turned off … a simple and free way to run it on the cloud? Can Claude run it?")

**Verdict: possible, but not as the app stands, and not fully phone-free.** Findings (from the code, NOVIG_API.md and today's web facts):
- **Novig's location rule keeps a phone in the loop.** Signed routes refuse a VPN/proxy address, but "a data-center address is fine" (NOVIG_API.md §4, docs.novig.com errors). A PLACEMENT also needs the key holder's device to have geolocated from a permitted state in the last 3 days, by opening the Novig app (`GEOLOCATION_EXPIRED` otherwise; a read admits a stale one). So a phone off for hours is fine, off for 3+ days is not. Unverified: whether the Novig app on any logged-in device counts, or only the phone the key was made on.
- **The trading key can't leave the phone.** Its private half is a non-exportable Android Keystore key (§14.2). A second machine needs its OWN trading key: a new subaccount (up to 5 per trader; separate wallet, funded separately) or revoking the phone's trading key and minting one for the server (then the phone only reads that subaccount; its Bet sheet can't place API orders). The management key (1 per trader, a PEM Tj downloaded) is what mints and revokes them. A leaked trading key can lose its wallet's balance through trades but can't withdraw.
- **Most of the code is already plain JVM.** `engine` and `data` (21k lines) have no Android imports and hold the CNO reader (`CnoClient`, `CnoFeed`, `CnoBooks`, `CnoChecks`), the Novig clients, `ApiBetPlacer`/`ApiBetPlanner`/`AutoBet` and every order safeguard. What is Android-bound is the cycle and its glue: `AutoScanner`, `AutoBettor`, `AlertPicks` and `UiState` (~750 lines in `app`, tied to `UiState`, the settings store and notifications). A headless version means moving that glue onto plain inputs, a small `main` (settings from a file, the PEM and CNO link from environment variables, a loop), a push channel for stops and halts, and a dedupe against Novig's open orders (the phone's local Tracker isn't there). My estimate, not measured: one to two sessions plus a dry-run week.
- **Hosts (checked 2026-10-01):** GitHub Actions: 5-minute minimum schedule, runs delayed by tens of minutes under load, and its terms bar use unrelated to producing, testing, deploying or publishing the repo's software and "as part of a serverless application" (docs.github.com additional-products terms): not a fit, and this repo is public so logs would be too. Claude Code routines: 1-hour minimum interval and a small daily run quota per plan, each run an LLM session: Claude can BUILD this, not be its runtime. Google Cloud always-free e2-micro (US regions, billing account needed); Oracle Always Free (card needed, Ampere allowance cut to 2 OCPU/12 GB in June 2026, idle instances may be reclaimed); a home PC/Raspberry Pi/old Android phone (free if owned, residential IP). Cloudflare Workers would need a JavaScript rewrite.
- **Risks to name:** CNO's terms forbid automated access and allow IP blocks (§18.2; the phone app is already in that grey area, a data-center address is more likely to be blocked, and CNO answered curl from a cloud container once, 2026-09-26); two runners on one wallet would bet the same bet twice, so one auto-bets at a time; nobody is watching a server, so halts need a push message.


## 55. No $1 minimum for the auto-bet: stakes down to one cent (v0.39.3, 2026-10-01; Tj: "I don't want a $1 minimum bet for the auto bet feature. It can bet as low as 1 cent, whatever the number is that I have in options. Usually it will be a Kelly number and often under $1")

**What Novig allows (docs.novig.com, read 2026-10-01):** `PlaceOrder.qty` is an integer with minimum 1, in contracts, and "a winning contract pays full value, 1¢", so a contract costs its price in cents (46¢ → 0.46¢) and a one-cent stake buys at least one at any price (two at 46¢). Money is stored to $0.00001 and "fees use the same precision, with no one-cent minimum". Prices are on a grid (0.001 steps at the ends, 0.005 between 0.055 and 0.945). **Not published:** the errors page lists `ORDER_TOO_SMALL` and `ORDER_TOO_LARGE` (HTTP 400) with no threshold anywhere in the spec or the guides, so whether Novig refuses some small notional is unknown until a real small order is sent (nobody has sent one: every API bet so far was $1).

**What changed.** `AutoBet.MIN_STAKE` is $0.01 (it was $1.00): a Kelly stake, a typed amount, a per-bet maximum and the wallet's remainder are all floored to the cent and bet if they are a cent or more; under a cent is skipped ("its ¼ Kelly stake is under a cent"), the wallet-empty stop is under a cent, and the fixed $1 choice is still $1. The Bet sheet's own rules are untouched. Because Novig might still refuse a size: an `ORDER_TOO_SMALL` 400 is now `PlaceResult.Refused(tooSmall = true)` (before, any 400 was a `Failed`, which makes auto-bet stop and back off for five minutes: one tiny bet would have stalled the rest), and `AutoBettor` remembers the largest stake Novig refused that way during the run and skips a stake no bigger without asking again, while carrying on with the other bets; a bigger stake still goes. A new run asks again. The Diagnostics skipped-by-reason list says it when it happens.

**What to expect.** At ¼ Kelly on a $185 bankroll most bets stake $0.20 to $2, and the bets that a $1 floor used to skip (longer odds, thinner edges) are now placed, so there are more, smaller bets and more Tracker rows for the same dollars; the five-bets-a-cycle cap and the daily dollar limit still apply, and so does the longest-odds limit (§53). The tracked stake is the real cost of the contracts bought (a stake is rounded down to whole contracts), so a 37¢ stake at 46¢ is 80 contracts, $0.368.


## 56. Add money in every Bet sheet, the one-cent wallet, a 5-second check and a pop-up for every auto-bet (v0.40.0, 2026-10-01; Tj's four-part request)

**Add money from the Bet sheet.** Before, the sheet offered "Add money" only when the planned bet cost more than the wallet held (or Novig refused for the balance), and only as a way to Settings. At one cent in the wallet a bet of one cent is covered, so there was no button at all. Now every Bet sheet has "Add money to the wallet · $x in it", whatever the wallet holds (not on a placed bet), opening $1, $2, $5, $10, $15, $20 chips and a typed amount (cents allowed, at most $10,000: `WalletAmount`); it is open already when the bet is short, with the shortfall rounded up to whole dollars. A chip only chooses the amount; money moves on the "Add $X to the wallet" button. With a management key saved on the phone (`ManagementKeyStore`, saved once Novig accepted it) the transfer is sent right there (`ApiBettingController.fundFromSheet` → the same `transferNow` Settings uses: cash wallet → Vigilant subaccount, signed by the management key) and the sheet then reads the wallet again, so its balance, the bet's amount (back to its own once the wallet covers it) and its plan follow; the sheet says what Novig answered. Without a saved key the button opens Settings' wallet with the amount typed in, as before (the key is typed there once). There is one Bet sheet in the app (the feed, CNO, ParlayAPI picks and the widget all open `ApiBetSheet`), so one change is "all the bet slips"; **Novig's own bet slip** (what "Open in Novig" opens) belongs to Novig's app and can't carry a button of ours. Not touched: Settings' own wallet chips (5, 10, 20, 50, 100).

**The stake sentences, as built (v0.39.3's rules, now pinned end to end in `AutoBettorTest`):** the least a stake is one cent; a wallet of one cent is bet and may be emptied (the contracts bought are whole, so the cost is at most the cent, and the next check finds the wallet under a cent and stops); a Kelly stake over the wallet bets the remainder of the wallet; a Kelly stake over the maximum in the options bets the maximum; the smaller of the two wins. Nothing needed changing.

**5-second check.** `AUTO_SCAN_SECONDS_CHOICES` starts at 5 (the one list the Settings › Scan background scan and the Auto-bet card both use). `AutoScanClock.nextAtMs` waited at least 5 s after a cycle ENDED, so "5 sec" would really have been a cycle plus 5 s; at the 5 s interval that wait is now 1 s (`minGapMs`), so the cadence is 5 s start to start unless a cycle runs longer. CNO's own floor between two reads (3 s, `CnoFeed.MIN_GAP_MS`) still holds, a 403 still backs the scan off 10 minutes, Vigilant's own scan still starts at most every 4 minutes, and the closing reads stay at one per bet per minute. At 5 s CrazyNinjaOdds is read about 12 times a minute; its terms (§18.2) let it block addresses; CNO itself refreshes about once a minute (§18.1), so 5 s mostly shortens how long a new row waits for the next read. Exact alarms in Doze can still be delayed by Android (unverified live: no background run of this app has been timed under Doze).

**A pop-up for every auto-bet.** One notification per bet placed already existed (v0.39.0) on the `auto_bet` channel at normal importance. Android won't raise an existing channel's importance from the app, so each bet now posts on a new HIGH-importance channel, `auto_bet_placed` ("Auto-bet bets placed"), as its own notification (tag = the bet's id, so two bets in one cycle never replace each other), titled with the stake and the EV ("Auto-bet $0.37 · +5.8% EV · Over 5.5") and with the odds, books agreeing, market, game and the wallet left under it. The Auto-bet card says it, has "Send a test notification" (a made-up bet on the real channel, to see it pop up and fix the channel's settings if not), and warns while auto-bet is on if Android has the notifications, the app or the channel switched off; Diagnostics' health check adds a WARN for notifications switched off. Not known: why v0.39.0/0.39.x notifications may not have shown on Tj's phone (permission, the channel's setting or the importance); the test button and the warning are how the next report tells.

## 57. Check odds now gets every closing line and holds the focus (v0.40.1, 2026-10-01; Tj: "make sure it gets all available closing line data, and make it pause other parts of the app such as the cno scanner so that it focuses on refreshing the current odds and EV and stats")

**Closing lines.** Where closes come from: the last read of a bet before its start (the CNO page or Vigilant's pricing, recorded as `closingFair` while the game hasn't started: a Check odds now already does that for bets about to start), and for bets that started with nothing read, `CloseBackfill`'s sources in order: ParlayAPI's Pinnacle closes (5 credits a sport call, 1 per 1,000 rows of the closes file), ESPN's closing odds (full-game moneylines, spreads, totals), Novig's trade history (the next morning, every market). The ordinary look runs with the grading (app open, Grade now, the 3-hourly worker) and was limited to bets not looked at for 3 hours (`RETRY_MS`) and the newest 120 (`MAX_PER_RUN`): a bet looked at an hour ago when ESPN had no answer yet, or the 121st, waited. A Check odds now now runs `CloseBackfill.run(force = true)`: every started bet still without a close and not "never" from every source (up to 1,000), except one looked at in the last 10 minutes (`FORCE_GAP_MS`: the feeds don't move that fast, and a second tap would spend ParlayAPI's credits again). It reports what it found by source and, for what it didn't, the reason the sources gave ("Novig publishes this day's trades the next morning" and the like), in the same toast as the odds ("Closing lines: found 14 of 20 (ESPN 3, Novig's last trades 5, Pinnacle 6) · 6 started bets still have none: …"), and Diagnostics' closes line says it was the forced look and what is still missing. What can't be had by tapping: today's Novig trades (published the next morning), ESPN's lines for props and for a spread or total at another number, and Pinnacle's closes past the plan's 7 days of history.

**The focus.** `FocusGate` (in memory only, 15 minutes at most) is held from the moment a check starts to when it ends, fails or is cancelled, so nothing is ever left paused. While it is held: the background auto-scan cycle does nothing and says so (`AutoScanner.cycle` returns before any CNO read, auto-bet or Vigilant scan; the schedule goes on, and the check's end starts a cycle at once), **which means auto-bet places nothing while a check runs**; CNO's own list refresh, its books and teams lanes and Novig's live prices wait (`cnoReadsHeld`); Scan, Recheck and Refresh say a check is running; a scan under way stops, as the Pause switch stops it; the widget's rescans, ParlayAPI's movers and injury look-ups wait. Not held: the pre-start closing capture (the last read before a start is the close and that moment doesn't come back; it shares CNO's pace with the check), the Bet sheet (Tj's own tap), grading. The Pause switch itself is untouched. The Tracker shows a banner while it runs and the auto-scan notification says "Paused while Check odds now runs (auto-bet too)".

Open question for Tj: a check of ~375 open bets reads CNO's pages at 2 a second plus Vigilant's own pricing and the closes, a few minutes in all; auto-bet is off the whole time. If that costs bets, the focus can leave auto-bet's cycle running (CNO's pace is shared either way).

## 58. "Every book scanned must agree" for the auto-bet (v0.40.2, 2026-10-01; Tj: "require that every sports book scanned agrees the bet is positive EV (for example, 5 of 5 books agree positive EV)")

**What "agree" and "scanned" count** (`CnoBooks.check`): the books on a bet's CNO game page, except the book the bet is priced at (Novig) and the pick'em apps, that price BOTH sides of the bet are `twoSided` (the "y" in "x of y books agree"); each one's two prices are devigged (worst case) into its own fair probability, and the books whose own fair line alone makes Novig's price +EV are `agreeing` (the "x"). A book that lists only one side (sportsbooks list only the Over of a prop) can't be devigged honestly and is in neither number (`oneSided`). The auto-bet already required at least N agreeing (2, 3, 4, 5+), so 3 of 5 passed a "3".

**The setting.** `ScanSettings.autoBetAllAgree` (off by default: nothing that ran changes): with it on, `AutoBet.judge` also skips a bet unless `agreeing == twoSided` ("only 4 of 5 books say +EV on their own (you need every one)"), on top of the minimum, the books-pricing-both-sides minimum, the edge, the odds limit and the rest, never instead of them (a 2 of 2 under a minimum of 5 is still skipped). To ask for "5 of 5" and nothing less, pick 5+ and switch it on; 2 and the switch is "all of however many, at least 2". The test on the sample bet: Jefferson Under 69.5 has 3 of 3 (KI, PN, PX all +EV at +117); with KI priced at +105/−135 (its fair 45.5% is under Novig's 46.1%) it is 2 of 3 with the consensus still +EV: placed with the switch off at a minimum of 2, skipped with it on. Effect to expect: the more books a bet's page has, the harder the test, so fewer bets pass (a prop with 11 two-sided books needs all 11); books that disagree by a hair count the same as ones that disagree by a lot. The card says it in the switch's note, the confirm and Diagnostics' auto-bet line say it, and the notification already shows "x of y books agree".



## 59. Does auto-scan / auto-bet survive the screen off, locked and idle? Keep awake (v0.41.0, 2026-10-02; Tj: "Research if this app stays awake and auto bets if the option is turned on even through screen lock and an idle android 16 moto g 2026. If not, research if there are ways to keep it alive robustly … Maybe wake lock or a don't sleep or keep screen awake function (but I still want the screen turned off if possible)")

**Short answer.** Through v0.40.2: **no, not reliably.** The screen locking is harmless (an app's foreground service keeps running), but a phone that is unplugged, still and dark goes into Doze, and v0.40.2 kept time with alarms alone, holding the CPU only for the length of a cycle. Fast schedules (5 s to 5 min) could not be kept that way. v0.41.0 adds **Keep awake** (on by default): the service holds a partial wake lock (CPU on, screen off) and runs the cycles from its own loop; the alarm is demoted to a safety net.

**What was read, and what each says** (docs: developer.android.com/training/monitoring-device-state/doze-standby, /develop/background-work/services/fgs/timeout, /develop/background-work/services/alarms; source: AOSP `services/core/java/com/android/server/power/PowerManagerService.java`, `.../net/NetworkPolicyManagerService.java`, `core/java/android/net/NetworkPolicyManager.java`, `.../alarm/AlarmManagerService.java`, `.../DeviceIdleController.java`):

- *Doze* starts after the screen is off, the phone unplugged and still for a while; it suspends network access for apps, ignores wake locks and defers jobs, syncs and standard alarms, with periodic maintenance windows. App Standby buckets and Battery Saver restrict background work further. The documented exemption is the user's battery-optimization allow-list ("Unrestricted").
- *Partial wake locks* are disabled while the device idles unless the app is allow-listed or its process state is at or above a foreground service's (`PowerManagerService.setWakeLockDisabledStateLocked`: disabled when idle, the uid not allow-listed and `procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE`). So the lock Vigilant held for the length of a cycle (v0.40.2) works only while a foreground service runs, and v0.41.0 relies on that.
- *Network in Doze*: `NetworkPolicyManager.isProcStateAllowedWhileIdleOrPowerSaveMode` allows a uid whose state is at or above `FOREGROUND_THRESHOLD_STATE` (= bound foreground service) and a foreground service has the capability that keeps its network. A foreground service is the supported way to keep working in Doze (it is what music and navigation apps do).
- *Foreground-service limits*: timeouts apply only to `dataSync` and `mediaProcessing` (6 hours a day, Android 15+) and `shortService`. Vigilant's service is `specialUse`, with no timeout. Android 16 (API 36) changed JobScheduler quotas, not this.
- *Alarms in Doze*: `setExactAndAllowWhileIdle` fires, but at a limited rate. **Two numbers disagree and I could not settle which one Tj's phone applies.** Google's documentation says about one per app every 9 minutes; the AOSP source I read (`AlarmManagerService`, `ALLOW_WHILE_IDLE_QUOTA = 72` per hour for an app that holds the exact-alarm permission, as Vigilant does with `USE_EXACT_ALARM`; `ALLOW_WHILE_IDLE_COMPAT_QUOTA = 7` per hour for inexact and pre-Android 12 apps; both overridable by the device's config) allows far more. Either way 5 to 30 second schedules cannot be kept by alarms (72 an hour is one per 50 s once the burst is used), which is why Keep awake is on below 9 minutes, the safe line. At 10 minutes and slower an alarm is on time by either number, and the CPU is left to sleep between scans.
- *Motorola*: the phone's own battery manager ("App battery usage" in Settings › Apps › Vigilant: Optimized / Unrestricted, Adaptive Battery, Battery Care) is a separate layer that can stop a background app regardless of the above. Setting Vigilant to **Unrestricted** is the one lever that is documented to matter (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is in the manifest; Settings › Background auto-scan has the prompt, a button to Vigilant's own app page, and the line that says whether the exemption is on). What this particular Moto G (2026) does beyond that was **not** verified: nothing here ran on the phone.

**What v0.41.0 does** (`KeepAwake`, `AutoScanService`, `CycleLog`):

- `ScanSettings.autoScanKeepAwake` (on by default; Settings › Background auto-scan › Keep awake). Active while auto-scan runs something and cycles are under 9 minutes apart (`KeepAwake.active`); at 10 minutes and slower it isn't needed and isn't used.
- Active: the service holds `Vigilant:keepawake` (PARTIAL_WAKE_LOCK: CPU, never the screen), with a 15-minute timeout renewed every cycle and at least every 30 s, released on switch-off, Stop and service end. A loop in the service starts each cycle at its time (a cycle someone else started is waited for; a failure is a Recent problem and a retry, not a crash; a cycle that didn't start waits a moment so Check odds now can't spin it). The alarm becomes a **safety net**: armed three intervals away (never under 3 minutes), moved on by the loop (not every 5 s: only after a third of it has gone), so it goes off only if the loop stalls. A stray one only moves itself on.
- Restarts: START_STICKY as before; `onTaskRemoved` (Vigilant swiped out of the recent apps) arms an alarm 3 s ahead in case the phone ends the process; the boot and update receivers are unchanged. A **force-stop** from Android's app info clears every alarm and the service; nothing can survive that.
- Cost: the CPU runs all the time while it's on (a 5 s schedule would keep it busy most of the time anyway). Best plugged in overnight. The notification says "stays awake".
- **The meter** (`CycleLog`, files/cycles.json): each cycle records when it started against when the schedule said (late = more than 30 s or one interval after), and whether the screen was off and Doze on when it started. Diagnostics shows "Cycle record since …: N cycles, M started with the screen off (K of those in Doze) · none late" or the late ones with their size and state, a health check warns on late cycles in the last day (and says OK with the counts when the screen was off and none were late), warns when Keep awake is off on a fast schedule, when the service isn't holding the lock, when Battery Saver is on without the exemption and when Android has put the app in the rare or restricted standby bucket. A process that was killed in the night shows as a late cycle at its next start; Check odds now's wait and a deliberate Stop don't count.

**What to look at after a night idle:** Diagnostics › Background auto-scan: the Keep awake line ("holding the CPU awake: yes"), the Cycle record (cycles started with the screen off, how many in Doze, how many late), and the Phone block (battery unrestricted, standby bucket). "N started with the screen off, M in Doze, none late" is the answer to Tj's question; any late cycle with Doze on points at Android holding it back despite the lock (then the battery setting or a Motorola layer), and "How the app last ended" says if the process was killed.

**Not verified:** behaviour on the Moto G 2026 itself over hours; whether Motorola's layer honours the lock with the exemption on; which alarm quota the phone applies. The tests run the real service on a simulated phone (screen off, Doze on) and pin the rules; none can show what Android does to a process after hours of Doze.


## 60. Sharp-book confirmation for CNO's alerts and the auto-bet, and nothing running by itself after a reopen (v0.42.0, 2026-10-02; Tj: "anytime I close the app and reopen it, auto bet and background scan is turned off by default … Research if there is a way to have a setting for the cno scanner and auto bet feature to require bets to be proven positive EV by a current, devigged sharp book such as Pinnacle … Does cno already have this information in its feed? If not, can pinnapi or any other Pinnacle api be used in addition to cno to compare the odds?")

### 60.1 Auto-bet and background auto-scan start off every time Vigilant is opened again (`LaunchReset`)

`MainActivity.onCreate` with no saved state (`savedInstanceState == null`: the app opened from the launcher, a notification or Recents after it was closed) saves `autoBet = false` and `autoScan = OFF` before anything reads the settings (a blocking read and write of the one small settings file), and says so on screen ("Auto-bet and background auto-scan are off again after reopening Vigilant…") when either was on. The service stops on the saved setting. Everything else (criteria, limits, the interval, Keep awake, the halt) is kept for when he switches them on again. Not a reset: a rotation, a return from Home or another app (the activity is still there), Android bringing the app back after ending its process (saved state comes with it). **Two edges Tj should know:** (1) a tap on an auto-bet or alert notification while the app is closed is a reopen, so it turns both off; (2) a reboot with the app unopened still restarts what was on (the boot receiver), until the app is opened. Both are one-line changes if he wants them the other way.

### 60.2 Does CNO already have a sharp book's price? What it has, and what it lacks

- **CNO's +EV list row** has no per-book data: the bet, Novig's price, CNO's fair odds and fair probability (its devig of the other books, worst case), and a count of books (§18.1). So the list alone can't say "Pinnacle says +EV".
- **CNO's game page** (the page Vigilant already reads for each candidate bet to count books that agree: `CnoBooks`) has a column per book, **PN = Pinnacle** and CS = Circa among them, with both sides of the exact bet (the row for the bet's `side_id` and the other side next to it), so the same line and side. Pinnacle's two-sided price from it can be devigged and compared with Novig's price exactly as the books check does. **What it lacks:** a time for each book's quote. The page has one "Last Updated" for the whole page (CNO re-publishes every 13–33 s, §19; its own words are that it uses OddsBlaze for much of its odds), so a Pinnacle column there can be only as old as that, but nothing says when Pinnacle's own price in it last moved, and a book's column is blank when it doesn't list the bet (Pinnacle lists props for fewer players and stats than the sportsbooks do, so for a prop "two-sided at Pinnacle" is less likely; how often was not measured). The app now keeps that page-level time (`CnoBooksView.cnoAgeSeconds`, from the game page's own server-info panel, null when the page doesn't carry one: **not verified live**, since CNO's terms forbid automated reads and this session read none).
- **A feed with the quote's own time** is the proof Tj asked for. What is available (all kept in the app already; `data/reference`):

| Source | What it gives | Time of the quote | Cost / limit | Verified |
|---|---|---|---|---|
| **PinnWire** (pinnwire.com, free key) / **pinnapi** (same format) | Pinnacle's whole prematch board for a sport in ONE request: moneyline, spreads, totals, team totals, halves, alternates; PinnWire also Pinnacle's player props (`include_specials`); decimal prices | the board's `generated_at` = when it was made | 100 requests a day and 20 a minute per free key | 2026-10-02: one request with the shared demo key, board `generated_at` equal to the request's own time (`cache-control: no-store`, `cf-cache-status: DYNAMIC`): built per request, 80 NFL/NCAA events; **no per-market time**; "real time" is the provider's claim, not measured against pinnacle.com |
| **PropLine** (free key) | every book in one call per league, Pinnacle among them | `last_update` per market, `last_seen_at` per outcome | 1,000 requests a day | §22: game lines 0–42 s old across books (Pinnacle 13 s); not re-measured |
| **ParlayAPI** (Tj's Starter plan) | `/odds` Pinnacle's game lines (3 credits a league, 5 with alternates), `/props` per page (3 credits) | `last_update` per book (game lines) / `age_seconds` per row (props: `now − age`) | 20,000 credits a month, paced by day (`CreditPace`; the background auto-scan keeps half a day's share) | PARLAY_API.md §3, §5; a line unchanged for hours can read old (§5) |
| The Odds API | Pinnacle among its books | market `last_update` | 500 credits a month | last of the order; seldom on |

Not used: Pinnacle's own website API (a private backend, §22.2), OddsPapi (250 a month), the Odds API's exchanges, Polymarket and Kalshi (not sportsbooks: left out of `SharpBooks`).

### 60.3 What was built

- **The rule** (`SharpConfirm`, pure): for the bet's exact game, market, line and side, a sharp book's own two-sided price, devigged **worst case** (`CnoBooks.fairFor`: the lowest fair chance of multiplicative, additive, power and Shin: never rosier than the book's vig allows), makes Novig's price now +EV by at least the chosen edge (any +EV, +1, +2 or +3%), from a quote **no older than the limit** (1, 2, 3 or 5 minutes; never over the app's 5-minute rule, §24.2; a quote with no time proves nothing; up to a minute ahead of the phone's clock is "now"), and **no other fresh sharp quote says it isn't** (Pinnacle's no is not outvoted by Circa's yes). Sharp books: Pinnacle (default) or Pinnacle or Circa. Settings: Betting › Sharp-book confirmation: one switch for the auto-bet, one for CNO's push alerts (both off by default), the three choices, and "Also take Pinnacle's price from CNO's page" (off). Vigilant's own scan is not asked: its fair odds already come from the sharp books.
- **Where the quote comes from** (`SharpBooks`): the feeds already switched on with keys, asked cheapest and freshest first (PinnWire/pinnapi, PropLine, ParlayAPI, The Odds API), the first that has the bet's two sides at a sharp book answers and the rest cost nothing. The bet is matched like every other comparison in the app (`ParlayBooks.viewOf`: both teams and the start, the line's exact number and side, a prop by the player and the stat). A league's board is kept a minute per feed (a failure too), so a second bet in the same league costs nothing and a refusing feed isn't hammered. The scan's family choices don't limit it. Props go to the feeds with props (PinnWire's board, the props sources), game lines to the game-line feeds.
- **Efficiency** (`SharpGate`): (1) the check is asked **last**, only for a bet that passed every other criterion (books, edge, odds, two-sided, wallet…) and is about to be placed; for alerts, only for an alert about to be sent (one already sent isn't asked again); a cycle that finds nothing to bet asks nothing. (2) **CNO's page first, free:** a fresh Pinnacle column there that says the bet is NOT +EV vetoes it without calling a feed (a no from CNO's own copy of Pinnacle costs nothing). (3) With "also take Pinnacle's price from CNO's page" on, a fresh CNO Pinnacle column that shows +EV confirms it with no feed call (weaker proof: the page's time, not the book's). (4) Feeds are asked once per league per minute, in cost order, and each spends its own allowance as a scan does (the paced background variants of ParlayAPI's sources).
- **What a no does:** the bet is skipped with one general sentence (no numbers, so the report counts them): "Pinnacle's own devigged price doesn't show it +EV at Novig's price", "…shows less than your X% minimum edge", "the sharp books disagree about it", "Pinnacle's price for it is older than 3 min (or has no time)", "no Pinnacle price for both sides of this exact bet", or "couldn't get a fresh Pinnacle price (why)". A failed or throwing check is a skip, never a bet. A confirmed bet's pop-up ends "· sharp: Pinnacle +3.3% (devigged, 40 sec old, via PinnWire)". Diagnostics: a Settings line, a health check (WARN when it is on and no feed is on with a key: the auto-bet then skips every bet; WARN when most feed calls fail) and the feed counters.

### 60.4 What to expect, and what is not verified

- **Many bets will be vetoed.** CNO's EV is against the consensus of all its books; a single sharp book's devig, worst case, is a harder test, and Pinnacle's margin on props is wide. Expect far fewer bets, especially props; that is the point of the switch, and the report's skipped list says how many and why.
- **Cost:** with PinnWire's free key (100 requests a day) a quiet day is a few requests; each league with a bet about to be placed costs one request a minute at most; ParlayAPI is last in the order and paced. Not measured on Tj's keys: this session had none of the paid keys' answers for these calls beyond the fixtures.
- **Not verified:** a live run against PinnWire, PropLine or ParlayAPI with Tj's keys (the tests use feeds' shapes from earlier measurements and fakes); that PinnWire's board is as fresh as pinnacle.com; the semantics of ParlayAPI's `last_update` for a line unchanged for hours (it may read old and be rejected: a bet skipped, never a bad bet placed); whether CNO's game page carries its own "Last Updated" (the page time is then the read's time).

## 61. Diagnostics as a file Claude can read and act on (v0.43.0, 2026-10-02; Tj: "make it output a file that I can send directly to Claude which Claude can understand and easily diagnose and improve the app … log all types of events, code, failures, connection speed and issues, API usage and issues … opens an android 'share with' prompt … signal to Claude what to optimize, what bugs or failures there are to fix, how to make features smarter or faster or better coded")

### 61.1 What the app already recorded, and the gaps

Before: `Diagnostics.report` (the pasted report of v0.38.0: settings, scans, API usage, CNO, background, tracker, accuracy, memory, phone, how the last run ended), `ProblemLog` (last problems, in memory and file), `AppExits` (crash and exit reasons), `CycleLog`. **Gaps:** nothing recorded how long a call took or how fast its body came, which calls failed and how (timeout, DNS, TLS, 429, 5xx), what the app decided and why over time (it kept only the last scan), which step of a cycle was slow, or what changed since the last report; and the report was text to copy and paste, with nothing to say what to fix first.

### 61.2 The flight recorder (always on, no switch; persisted so a crash or restart keeps it)

- **`EventLog`** (`data/diag`): a ring of the last 1,000 notable events (category, level INFO/WARN/ERROR, message ≤220 chars with credentials masked, optional ms, and for errors the app's own stack frames `com.tjshea.vigilant.app.AutoScanner.cycle(AutoScan.kt:231)`, `where`), identical events within 60 s merge into one with a count, plus **counters** kept across restarts over a 14-day window (cycle runs/errors, auto-bet looked/passed/placed and every skip reason, sharp-check verdicts, alerts sent, CNO pauses, settings changes). Flushed to `event_log.json` at most every 5 s (30 s loop in `VigilantApp`, forced on share).
- **`NetStats` + `NetInterceptor`** (`data/diag`): one OkHttp interceptor on the app's one shared client records, per host: calls, errors by kind, HTTP statuses, time to first byte (p50/p95/max over the last 100), body speed (bytes/s over the last 50 bodies of ≥8 KB), bytes, the network type (Wi-Fi/mobile/other), rate-limit (429) hits, the 14 busiest request path shapes (ids, UUIDs, long tokens and numbers of 5+ digits become `{id}`; **never the query string**, where keys live) and 48 hourly buckets. Events for failures, 4xx/5xx and calls ≥8 s. Timing is taken at the end of the body, not the headers (a websocket's 101 is recorded at once).
- **`PerfStats`**: `cycle.ms`, `cycle.step.<cno|autobet|alerts|closing|vigilant>`, `scan.ms` (last 200 each, this run), cold start (process start to first window focus).
- **`LogcatTail`**: the app's own warnings and errors from Android's log (`logcat --pid`, masked), newest last; empty where the phone doesn't allow it (the file says so).
- **`DiagHistory`**: after each shared report a compact snapshot (finding keys and key numbers); the next file's "SINCE THE PREVIOUS REPORT" says resolved / new / worse / still there / numbers that moved, and whether the app was updated in between. Last 12 kept.

### 61.3 The findings engine (`Advisor`) and the file (`DiagnosticsFile`)

`Advisor` turns all of the above into a ranked list, each with a stable key (so reports can be compared), what it saw, which file owns it and what to try. Kinds, worst first: **BUG** (crashes, with the repo file and line parsed from the stack; caught errors with `where`), **FAILURE** (failing checks, a provider failing ≥10% of ≥15 calls, 429s, a feed that couldn't answer), **OPTIMIZE** (call p95 ≥3 s, body speed <60 KB/s, a cycle longer than max(1.5× its interval, 20 s) with the slowest step named, a scan ≥180 s, cold start >2.5 s, heap ≥70%, files ≥20 MB, logcat jank), **IMPROVE** (the auto-bet's top skip reason when ≥50% of ≥20 skips, sharp checks that are mostly UNAVAILABLE/STALE/NO_QUOTE, leagues the matcher misses), **WATCH**. It never advises saving data or storage. File name `vigilant-diagnostics-v<version>-yyyy-MM-dd-HHmm.txt`; sections in order: READ ME FIRST (what the app is, how to work from the file, constraints, the rules Claude must keep), WHAT TO DO, SINCE THE PREVIOUS REPORT, the existing report, CONNECTIONS, API ISSUES, PERFORMANCE, COUNTERS, EVENT TIMELINE, APP LOG, STORAGE, CODE MAP (module/file for each area), and a `<<<JSON … >>>` block. About 25-40 KB. The newest 3 files are kept in the app's cache.

### 61.4 The share

`DiagnosticsShare` writes the file to `cache/diagnostics/` and opens `Intent.createChooser(ACTION_SEND, text/plain)` with the file as `EXTRA_STREAM` through a `FileProvider` (`<package>.files`, `cache-path diagnostics/`, read permission granted to the chosen app; `EXTRA_TEXT` holds a one-paragraph prompt telling Claude what to do, so even an app that takes only text is useful). Buttons: Settings › Tools › "Share diagnostics with Claude", and the Diagnostics dialog's "Share with Claude". The ViewModel hands the intent to the activity through a buffered one-consumer channel (survives a rotation gap).

### 61.5 What is verified and what is not

- **Verified by tests** (each mutation-checked): the recorder's merging, masking, eviction and persistence; the interceptor's recorded kinds, limits and timing on a MockWebServer (including a body read to the end, a cancelled call, a 429 and a 500); the advisor's thresholds and ranking; the file's section order, masking of key-shaped text, JSON; the share intent's action, type, stream URI, grant flag and the FileProvider serving the file's bytes; the buttons.
- **Not verified:** that the Claude app on Tj's phone appears in the share sheet and accepts a `text/plain` stream (Android shows only apps that declare `ACTION_SEND` for text; if Claude isn't listed there, the fallback is the Diagnostics dialog's Copy button, as before); that `logcat --pid` returns anything on the Moto G under Android 16's restrictions (the file says when it is empty); real-world sizes and speeds. **First real upload:** read the file's CONNECTIONS and PERFORMANCE against what the phone did; the thresholds above are first guesses and are meant to be tuned from real files.

## 62. "Large liquidity on Novig = syndicates or sharps": tested on Novig's own trades, not built (2026-10-02; Tj: "I read somewhere that sharp bets can be found on novig by analyzing liquidity on certain bets, and if large liquidity is offered on certain bets that it is probably betting syndicates or sharps. Investigate whether this is true or not. If it is true and a good betting strategy, implement in vigilant a way to scan for this liquidity and follow the sharp bets … Only make this if you discover that it has merit and is a good strategy")

**Verdict: no. Nothing was built.** Novig publishes every trade it has matched (NOVIG_API.md §10), so the claim was measured
directly rather than argued: 59 days (2026-08-03 to 09-30), 9.5M straight-trade rows, 10,225 two-outcome markets with a
pregame close. Re-run it any time with `python3 tools/research/novig_size_study.py` (about 80 s once the files are cached;
`pip install pandas numpy`). The big resting liquidity loses to the close, and copying it loses about 1% per bet. The biggest
*takers* beat the close by about half a cent. That edge is not significant, is zero on the NFL, comes about 9 times a day, and
what it catches is the gap between Novig and Pinnacle, which Vigilant already prices directly.

### 62.1 Where the claim comes from

- **SmartStake's "Smart Money"** (smartstake.app/learn/how-to-use-smartstake-smart-money, read 2026-10-02) reads the resting
  orders on Novig, ProphetX and Kalshi and takes the maker to want the opposite side. Its worked example is a $11,995 offer on
  Spurs −329, read as "a maker holding Kings +329". It then lists **sportsbook** prices (DraftKings, FanDuel…) that match or
  beat that maker. The bet is placed at a sportsbook, never on the exchange. It does not tell market makers apart from sharps.
- **+EV Bettors on X** says "Novig is a VERY sharp book, especially when liquidity is high" (from a search snippet; x.com
  answered 402 to a direct read). That claim is about the price
  (deep markets are efficient), not about the size marking one side as sharp.
- **Betfair traders' "weight of money"** (the same idea, decades older): "somewhat discredited with the prevalence of bots
  and spoofing"; "most profitable traders now lean more on traded volume, market flow and how quickly bets are taken rather
  than how much money is sitting there" (caanberry.com/understanding-weight-of-money-on-betfair; forum.betangel.com).
- **Mechanics** (NOVIG_API.md §7): every order is a buy, so a resting bid on A at P is liquidity for B at 1 − P. Taking a big
  resting order means betting **against** whoever posted it. To bet with them you must buy A at 1 − (best bid on B), which
  costs at least one tick more than the maker paid. So "follow the liquidity" on Novig always pays more than the liquidity did.

### 62.2 Who posts the big resting orders (live books, 2026-10-02 ~03:10Z, 57 pregame moneylines, public route)

The size is symmetric and identical across games, which is a quoting algorithm, not a side being backed:
- **NFL:** the biggest order on *both* sides of nearly every moneyline is the same $10,500 (some $11–14k), 0–3.5¢ back from
  the best bid. Depth is $25–100k a side.
- **MLB:** about $4.8k on both sides of each game.
- **FCS college football:** one order a side, sized for equal payout on each side (ILST $1,503 at 0.455 / $1,521 at 0.505),
  quoted 4–12¢ wide.

Novig recruits professional liquidity providers. Its "Maker Credit Program" page (support.novig.com) excludes members with a
Market Maker Agreement, and press on its $75M Series B (hellorookie.com, nextpredict.io) says the money goes to onboarding
institutional liquidity providers. Those LPs quote off the sharp
books. Their size says how sure they are of the price. It says nothing about which side wins.

### 62.3 What the trades say (`novig_size_study.py`, 95% intervals bootstrapped over markets)

CLV = Novig's later price for the outcome bought minus the price paid, in cents of probability (+ = beat it). Test A takes
the price 1–2 h before the start (the trades carry no start time, so it is placed safely before the latest possible start).
Test B takes the true close, 10 min before kickoff (NFL kickoff slots).

| Stake of the trade | Taker (hit the book), A: all leagues | Maker (the resting liquidity that was hit), A | Taker, B: NFL true close | Maker, B |
|---|---|---|---|---|
| < $100 | −0.33¢ [−0.36, −0.29] n=979k | +0.32¢ [+0.30, +0.35] | −0.30¢ | +0.30¢ |
| $100–1,000 | −0.26¢ | +0.19¢ | −0.29¢ | +0.24¢ |
| $1,000–5,000 | −0.12¢ [−0.19, −0.03] | +0.01¢ [−0.15, +0.11] | −0.27¢ | +0.16¢ |
| $5,000+ | +0.16¢ [−0.04, +0.42] n=2,631 | **−0.78¢** [−2.31, +0.03] n=1,319 | −0.13¢ [−0.28, +0.03] | +0.12¢ |
| $10,000+ | +0.61¢ [−0.02, +1.56] n=605 | **−1.81¢** [−4.89, −0.02] n=245 | **−0.24¢** [−0.45, −0.06] n=375 | +0.01¢ |

**Following** (C: buy the same outcome at the next taker price, which on average came 37–84 s later; EV is measured at the
1–2 h close):
- **The big liquidity** (makers filled for $2k+): the follower paid +0.46¢ more than the maker did, and ends at **−0.57¢**
  [−0.84, −0.37], **−1.05% EV**, n=5,922 in 1,623 markets. That is Tj's strategy as stated. It loses.
- **Big takers, $2k+:** −0.04¢ [−0.18, +0.15], +0.01% EV, n=9,139: nothing.
- **Big takers, $10k+:** +0.55¢ [−0.11, +1.61], +1.31% EV, n=562 in 316 markets over 59 days (about 9 a day, all sports).
  By league: NFL −0.09¢ [−0.29, +0.16] (−0.24¢ at the true close); MLB +0.75¢ [+0.06, +1.94]; NCAAF +2.8¢ [−0.2, +8.6]
  (58 markets). By month: Aug +1.84¢ (n=76), Sep +0.43¢ [−0.11, +1.39]. The positive part sits in a few college football
  games and MLB. It only appeared after slicing by league, it shrank in the later month, and in the smaller sample with
  results the same trades' return at settlement was −7% flat (SE ±3.9%). It is not a strategy.

What the table does show: on Novig, small and mid-size **takers lose about 0.3¢ to the close and makers earn about 0.3¢**,
which is the spread. The largest resting orders are the ones that get picked off. Big money that *takes* is, at best, slightly
informed, and in the NFL it is not.

### 62.4 Why nothing was built, and what Vigilant already does instead

- Tj's bar was "only make this if … it has merit and is a good strategy". Following resting liquidity fails it (negative EV
  with a tight interval). Following whale takers is an interval that includes zero, about +1% at best, with no NFL edge. A
  scanner for either would add alerts with no edge, and the auto-bet would lose money on them.
- **Sharp action is already followed, at its source.** Pinnacle's and Circa's lines are where sharp money moves a price
  (they take big limits and move on it). Vigilant devigs them and bets Novig's taker price only when it is below that fair
  price. Since v0.42.0 it can also require a fresh (≤ 3 min) devigged Pinnacle price to confirm each alert or auto-bet
  (§60, `SharpConfirm`). A big Novig taker who knew something was, by these numbers, mostly picking off a Novig price that
  lagged the sharp books, and Vigilant's scan catches that lag itself, before the whale and without him.
- **Big depth at a price Vigilant already found +EV is good news**, because Tj is the taker against it. The bet sheet already
  shows "$X fillable at +EV" from the whole ladder (§16.1, `Opportunity` depth).
- **SmartStake's use** (Novig's resting orders as a fair price, bet at DraftKings or FanDuel when they beat it) is an
  exchange-as-reference strategy for sportsbook bettors. Vigilant bets on Novig only (BRIEF.md), so it doesn't apply.
- **Makers earn the spread.** That supports the maker-bid line Vigilant already shows (§16.4), not a liquidity scanner.

### 62.5 What is not verified

- **Unfilled resting orders** are not in the published data (no book history). §62.2 is one live snapshot. The filled-order
  numbers are the evidence about big resting orders, and an order that never fills is, by selection, one no informed trader
  wanted to take.
- **Accounts are anonymized:** no way to follow one bettor's record. A "sharp account" tracker is not possible from this data.
- **Season mix:** August to September 2026 is mostly MLB, NFL (preseason, then weeks 1–4), NCAAF and WNBA. NBA and NHL
  are not in it. Re-run the script once a basketball or hockey month is published if the question comes back. The script
  reads every published day by default.
- Test A's close is 1–2 h before the start, so late steam is not in A. Test B (the true NFL close) gives the same answer
  for big money.


## 63. Full tests, the laggy list, "odds 9 min old", the APIs at full use, and Tj's v0.43.0 Diagnostics file (v0.44.0, 2026-10-02; Tj: "Run full tests on this app, look for ways to improve the app and scanners … 1) when I start the vigilant scanner the list of bets gets laggy … 2) most odds say 9 minutes old. Is there a way to get fresh odds during a scan? Is 9 minute old odds still good data? 3) consider ways to use free apis such as ESPN apis and also my paid parlayapi to their full extent … I'm about to send a diagnostics file from the app, review that too")

### 63.1 "odds 9 min old" was mostly a measuring error, and it was throwing away most of ParlayAPI
- A card's "odds N min old" is the age of the OLDEST quote behind its fair price (`FairLine.usedUpdates`), as each feed dates it. The cards in
  Tj's screenshot (NCAAF, books "Polymarket, Kalshi, BetOnline.ag +7", "Pinnacle, BetMGM, Bovada +4", "Kalshi, FanDuel, Fliff") all used ParlayAPI's books.
- ParlayAPI's `/odds` carries two stamps. Its docs (`/v1/sports/{s}/odds`, read 2026-10-02): "**every bookmaker's `last_update` is the freshest of
  (price-change, no-change verification heartbeat)**. On hot-cycle sources (Pinnacle, FanDuel) that means a maximum age around the 2-second poll
  interval, even if the price hasn't moved"; `?include=verification` adds `verified_at`/`line_changed_at`/`is_current` **per bookmaker**. Each
  MARKET's own `last_update` is when its price last moved. Vigilant read the market's stamp first (`last_update ?: book.last_update`, right for
  The Odds API, whose market stamp is "the last time our system saw odds for that market").
- Measured on ParlayAPI's own answers (`parlay-tennis-atp.json`/`-wta.json`, 77 quotes): market stamps sat a median 7–37 minutes behind their
  book's, books seen 2–50 s before the answer. **Under the old stamp 10% of the quotes passed a 5-minute check (23% at 10 minutes); under the
  book's, 99%.** Scans price only quotes inside the limit minus 2 minutes (`planFor(youngFairOnly, headroomMs)`), so most of Tj's paid ParlayAPI game
  lines never priced, and the few that did were the ones that had just moved: they showed "N min old" and left the feed a minute or two later.
  The same parser feeds Check odds now's ParlayAPI backup (`ParlayBooks`) and the sharp-book confirmation's ParlayAPI Pinnacle feed (§60), both
  of which demand a fresh quote.
- **Fixed:** ParlayAPI quotes are dated by their book's stamp (`last_update_ms`, else `last_update`), never older than the market's own
  (`TheOddsApiClient.parseEvents(seenByBook)`; `ParlayFreshnessTest`, 5 tests, 4 mutants killed). `/props` rows carry `age_seconds` ("the real age
  of that write") and, for some books, `last_observed` = the same instant in the sample: left as they are.
- **Not verified:** whether ParlayAPI keeps serving a market its book has PULLED while the book itself is still verified (no stamp can tell that
  apart from a line that sits still; The Odds API drops a pulled market after ~15 minutes). Every scan prices from several books and the sharp
  confirmation re-reads Novig's own price, so one stale book moves a fair line less than one book's share.
- **Is 9-minute-old data good?** It depends on what the age means. A quote ParlayAPI *confirmed* seconds ago whose price last moved 9 minutes ago is
  current: that was most of the screenshot. A quote nobody has *seen* for 9 minutes is not safe near the start: §30.2's measurement (Kalshi, 2,471
  markets) had 2.7% of fair lines move a point or more in 10 minutes (more near kickoff, in NFL moneylines and MLB totals), and a point is ~2 EV
  points at even odds. The rule stays as §30.2 set it: 10 minutes for games 3+ hours off, 5 inside 3 hours, now measured from the right stamp.
- **Fresh odds during a scan:** with the stamp fixed and the live feed used (§63.3), a 4,588-price scan should take ~2.5–3 minutes instead of 5½,
  inside the 3-minute window a near game's odds may be read in and far inside the 8-minute one for later games, so a mid-scan re-read of every fair
  source (doubling ParlayAPI's 39 credits a scan and PinnWire's 5 of 100 a day) isn't worth it yet. If the next file still shows "left for the next
  scan" lines, that is the case for it.

### 63.2 The laggy list: bad code, found and fixed
- `VigilantRoot` provided `LocalApiBet` (a **static** composition local) with `ApiBetActions(...)` built inline: a new object on every state the app
  got. A new value for a static local recomposes everything under it with skipping off. A running scan hands the screen a new state every 350 ms
  (§48's throttle), plus the meters each second and every CNO read: so every card on screen, the bars, the chips and the badges were rebuilt 3+ times a
  second while Tj scrolled. `ScanLagTest` reproduces it (a card with unchanged inputs drew 6 times in 5 ticks; with the fix, once).
- Fixed: `ProvideApiBet` (remembered on `enabled` and the controller); `LocalOpenNovig`'s lambda remembered; the floating widget's `FloatingActions`
  remembered per window (every widget row redrew each tick); `ParlayPickActions` remembered at the root and in `FeedScreen`; `KeyActions` made a data
  class like `BetActions`/`BettingActions`/`ReportActions`. Checked and fine: the cards' own lambdas are memoized (bytecode), `OpportunityCard` is
  skippable with stable inputs (`-PcomposeReports`), unchanged markets keep the same `Opportunity` objects between partials (`FairMemo.pricedFor`),
  the badges and CNO screening are remembered (`RecompositionCostTest`). Not changed: cards slide (`animateItem`) when a partial result re-sorts the
  feed every 2 s; that's intended.

### 63.3 The scan's speed: the live feed was barely used
- Tj's file: "4,588 Novig prices in 318 s (14.4 a second: 113 by live feed, 4475 through the key) · the key's limit is 16 a second". REST is capped
  at 16 a second; the websocket can load up to 2,000 books in ONE subscribe, but the next takes ~2 minutes (512-token bucket at 4/s, §6). The scan
  handed the feed its whole plan at the first plan; the feed's first subscribe went at ~8 s (its bucket after the upgrade), when the plan held only
  the first source's few hundred lines (a plan has only lines a fair source quotes; fair odds took 29 s). Every later subscribe needed a full bucket and
  favoured lines the requests had already read. No error: the feed worked, it just carried 113 books.
- Fixed (`Scanner.BookPump.feedStream`): the feed is OPENED at the first plan (`NovigSource.openFeed`, `NovigStream.open`: connects without changing
  what it watches, so its bucket refills meanwhile) and HANDED its markets once per scan, when the plan has filled in (every source answered for every
  league, or more unread lines than it holds, or `STREAM_HOLD_MS` = 30 s): what it already holds for this plan first (dropping it costs tokens and a
  current book), then the unread lines in reading order. Also: an unsubscribe is charged at most the bucket (it was 1 a subject uncapped: a big drop
  waited minutes and left the bucket in debt), and a fresh connection forgets the last scan's list (after an idle close it would have spent the one
  bulk subscribe on lines that scan had read). `ScanTiming` says "live feed asked for N at X s". Tests: `LiveFeedPlanTest` (5, mutation-checked),
  `NovigStreamTest` (+2). **Not verified on the real feed** (the key stays on the phone): the next file's timing line is the check.
- ParlayAPI is the slowest host (NFL `/props`: 13–17 s to the first byte for 3.5 MB). That is its server building the board; the call already runs in
  parallel with the others. `grouped=true` would send fewer bytes but changes the row shape (no sample yet): not done.

### 63.4 What the v0.43.0 Diagnostics file said, and what changed
- **"BUG: The app crashed 1 time … OutOfMemoryError" (22 h old):** 05:40 UTC Oct 1, on v0.38.0, before v0.39.0/0.39.1's fixes (§52). The Advisor now
  splits crashes and exits by the install time of the running version (`PackageInfo.lastUpdateTime` → `Extras.installedAtMs`): older ones are a WATCH,
  "before this version was installed". A saved crash now records its version (`crashText(version)`). Its stack read `at n5.l.E0(…0c73:6)`: R8
  renamed everything and the masker shortened R8's source-file map id. Release builds now keep Vigilant's own class and method names with file and
  line (`-keepnames class com.tjshea.vigilant.**`, `-keepattributes SourceFile,LineNumberTable`; the APK goes from 6.9 to 7.9 MB, and phone storage
  isn't a constraint), and each Release carries the build's `mapping.txt.gz` for exact lines (R8 inlines).
- **"BUG/FAILURE: Android ended the app 3 times: low memory, in the background":** `ApplicationExitInfo.importance` now says where it was. A kill
  while CACHED (nothing on screen, no widget, no service) is Android freeing memory for the apps in use, as for any background app: reported, not a
  failure (`Exit.reclaimed`). A kill while the widget, mini window or a scan's service ran is still a BUG, with what it used. And Vigilant now gets
  smaller when it leaves the screen (`VigilantApp.onTrimMemory` ≥ UI_HIDDEN, not while scanning): fair-odds boards past every freshness limit (they
  can never price again), the priced-lines memo, the plans and Novig's 3,000-book cache go (`Scanner.trimForBackground`; `BackgroundTrimTest`).
  Android ends the biggest cached apps first.
- **"FAILURE: Vigilant's edges (CLV) −0.3% on 36 bets":** 36 bets; its spread of outcomes includes zero. §63.1 is the likely cause of part of it: the
  fair prices behind Vigilant's bets were built mostly without ParlayAPI's books (only the ones that had just moved got in). Next file: compare.
- **"OPTIMIZE: scan 322 s"** → §63.3. **"parlay-api.com slow"** → §63.3 (server-side).
- **"WATCH: 59 of 155 open bets have a current EV":** the Check odds now just before had priced 97; the rest were older. Unchanged.

### 63.5 The APIs at their full use, and free ones (checked 2026-10-02)
- **ParlayAPI** (every one of its 194 GET paths listed from `openapi.json`, costs from `/v1/meta/credit-costs`; PARLAY_API.md §6.9 covers the rest): the
  biggest gain was §63.1, its quotes now count. Unused and why: `/ev`, `/consensus`, `/compare`, `/arbitrage`, `/middles` (Vigilant prices its own
  lines against Novig's taker price), `/exchange/{s}/markets` (Novig is read free directly), SSE (Business plan), `probable-pitchers`/`news`
  (1 credit, information Vigilant can't price), `include=verification` (the bookmaker stamp already is the verified time).
- **ESPN (free, probed live):** core API odds are **DraftKings only** (`providers/100`: open and current spread/total/moneyline), already in
  ParlayAPI and PropLine; `…/odds/100/propBets` lists 1,232 DraftKings prop LINES for an NFL game with **no prices** (can't be devigged); the
  league injury report (`site.api.espn.com/…/injuries`, 8.7 MB for the NFL) could replace ParlayAPI's 1-credit injury calls (3 credits since the app
  opened: not worth the code); `predictor` is ESPN's FPI model, not a market. Vigilant already uses ESPN for scores, box scores, closing lines and rosters.
- **Other free odds APIs:** SportsGameOdds' free tier updates every **10 minutes** with 9 US books and no Pinnacle (fails the freshness rule);
  MoneyLine free = 1,000 requests a month (~33 a day); OddsPapi free = 250 a month, per game. None beats Pinnacle (PinnWire/pinnapi), Kalshi,
  Polymarket, PropLine and ParlayAPI, all already in the scan.

## 64. Auto-bet off only after a phone restart, BetMGM (ON) on CNO's sheet, and the sharp check's volume (v0.44.3, 2026-10-02; Tj: "I had auto bet running in the notifications in the background and when I opened vigilant it again turned off auto bet. I want the app never to turn off auto bet unless I turn it off. The default is auto bet off but only when opening the app after a restart or after I already turned off auto bet manually. / Review the screenshot, notice betmgm and betmgm (on). Is the app still double counting these? / And I'm getting no volume so far on auto bet with the option for each bet to be verified positive EV by a sharp book. Is this working correctly? Is it getting sharp book pricing?")

### 64.1 Why auto-bet went off, and the new rule

v0.44.2's `LaunchGate` called a screen "fresh" (and switched auto-bet and background auto-scan off) after a swipe out of Recents seen by a service's
`onTaskRemoved`, and on a new process whose last exit Android recorded as Tj's (force stop, swipe), an update, a crash or a reboot. Auto-bet running in the
notification with Vigilant swiped away is exactly the first case; installing a new version over a running auto-bet is the second (an update). No
Diagnostics file came with the report, so which one fired isn't known; the event log says it from v0.44.3 on ("screen opened (first since the phone
restarted)" vs "(auto-bet and auto-scan kept as they were)").

New rule (`LaunchGate`, `LaunchReset`): the only automatic off is the first look after the phone restarted. The gate saves Android's
`Settings.Global.BOOT_COUNT` (and the boot's wall-clock start, used only where a phone gives no count, with a 10-minute slack for clock changes) in
`SharedPreferences("launch")`. The boot receiver (`AutoScanReceiver.afterBootOrUpdate`) handles a new boot before anything can bet: it saves the reset, keeps
the note for the next screen, then marks the boot handled (a process that dies in between does it again); the first screen checks too, for a boot the
receiver never heard (Android delivers nothing to a force-stopped app). The very first look (a new install, or the update to v0.44.3) is not a restart, so
installing this version keeps what was on. Background auto-scan follows auto-bet: auto-bet only bets from its CNO cycles (`ScanSettings.autoBetsNow`).
What still stops auto-bet without switching it off: the in-flight halt (an order whose answer was lost; Resume in Settings), Tj's daily limit, the empty
wallet, Novig refusing orders (the 423 lock, retried), and Pause. Tests: `LaunchGateTest` (3), `LaunchResetTest` (7), 4 mutants killed.

### 64.2 BetMGM and BetMGM (ON): counted once since v0.44.2

The numbers in Tj's screenshot prove it: two-sided books ProphetX 48.1%, Kalshi 48.4%, BetMGM 50.0%, BetMGM (ON) 50.0%, BetRivers 49.4%. One vote a
company: four books, mean 48.98%, median 48.9%, lower 48.9% = +104, "4 of 4 books agree" (the sheet's). Counted twice it would be five books, mean
49.18%, median 49.4%, fair +103 and "5 of 5". The table still listed both rows with their own 50.0%, which read like two votes: now the first site of a
company shows the company's fair (its sites' average, the number the check uses) and the others "same co.", and the footnote names them ("One company's
sites count once, at their average: BetMGM and BetMGM (ON)."). `BookTableText`, `SisterRowsTest` (the screenshot's sheet), 2 mutants killed.

### 64.3 The sharp check: working, and why it places so little

Checked end to end with a real PinnWire answer (`pinnwire-football.json`, NFL with `include_specials`) through `PinnapiClient` → `SharpBooks.quotes` →
`SharpConfirm.judge`, for the bets as CNO names them: Chase Brown Under 21.5 receiving yards (PN −112/−112, confirmed at +110), Chase Brown Over 3.5
receptions (PN +106/−134: −4.4% at +110, not confirmed), Aaron Rodgers Over 220.5 passing yards (confirmed), the moneyline and a total: each found
Pinnacle's two sides at the exact line, dated by the board's read (0 s old), devigged worst case. CNO's market names map to the same stats as
Pinnacle's props for football (passing/rushing/receiving yards, receptions, passing TDs, anytime TD), baseball (strikeouts, total bases, home runs) and
basketball (points, rebounds, assists, threes). ParlayAPI's `/props` `age_seconds` is the age of the book's latest observation (its docs), so a quiet
Pinnacle line isn't wrongly "too old". So it gets Pinnacle's prices when a feed has the bet (`SharpRealBoardTest` keeps this checked on that real answer).

Why the volume is near zero: (1) Pinnacle prices far fewer props than CNO's ~20 books, only main lines, and the check needs the exact line: Tj's own
screenshot (Juwan Johnson Under 39.5) has no Pinnacle column on CNO's page at all; Pinnacle's hockey props aren't read from PinnWire (no stat map: ParlayAPI
and PropLine can still answer). (2) Where Pinnacle has it, its own devig (worst case) is a harder test than CNO's consensus, and it vetoes (§60.4 said:
"Expect far fewer bets, especially props"). (3) Novig's 423 lock on his key (§63/NOVIG_API.md) stops every order whatever the check says, if still on.
Which of these it is on his phone is now on screen: Settings › Betting, under the auto-bet status, "Sharp check since Vigilant started: N bets asked
about, X confirmed, Y with no Pinnacle price for that exact line, Z that Pinnacle's own price doesn't show +EV (enough), …" (each bet's latest verdict;
`AutoBettor.sharpLine`, `SharpConfirmAppTest`, 2 mutants killed). The Diagnostics file has the same as counters (`sharp.autobet.*`) and the feed calls.
Ways to more volume, Tj's call (not built): "Pinnacle or Circa" (already a choice), "Also take Pinnacle's price from CNO's page" (already a switch), a lower
minimum edge for the check, or not requiring the check for game lines vs props.

## 65. Auto-bet settings for volume with the best chance of beating the close (2026-10-02 ~16:45Z; Tj: "Do research and tell me the best settings to get volume but also a good chance at beating clv. For example, if 7 of 9 books agree that it is positive EV, is this good enough or is it a red flag because 2 books say no? Is it good enough to find positive EV through multiple non sharp books or should I require a sharp book? What is the lowest percent positive EV I should look for per bet to safely beat clv? What other settings or changes should I have to get some volume but also the best chance at beating clv")

**Tj's own record** (his Diagnostics pasted 2026-10-01 06:31Z, INBOX.md; outliers aside; CLV on the bets with a true close, mostly Novig's own trades):
| Group | Bets with a close | CLV | Beat the close |
| :- | -: | -: | -: |
| CNO scanner, all | 87 | +1.5% | 66% |
| CNO player props | 70 | +2.0% | 71% |
| Game totals (both scanners) | 10 | −2.8% | 20% |
| Team totals / 1st-half totals | 3 / 3 | −2.0% / −5.9% | 33% / 0% |
| EV when bet 1–2% | 45 | +0.5% | 58% |
| 2–3% | 33 | +1.3% | 58% |
| 3–4% | 19 | +1.6% | 63% |
| 4% and up | 12 | +1.4% | 83% |
Average EV when bet +2.5% vs CLV +0.9%: the shown EV runs ~1.5–2 points above what the close says (§47 said 1.9). Small samples: one band's CLV
has a standard error near 0.5–1 point, so 2–3% vs 3–4% is not a real difference; 1–2% is not distinguishable from zero. Results (ROI) are −0.4
standard deviations from expected: far too few bets to judge by wins. The bets don't record their book agreement at the time of the bet
(`TrackedBet.books` is refreshed by every re-check), so his record can't yet say whether "7 of 9" does better or worse than "9 of 9".

**Outside evidence:**
- A consensus of many ordinary books is a good fair price: Kaunitz, Zhong & Kreiner (arXiv 1710.02824) took the average of 32 bookmakers'
  odds as the truth, bet whichever book was far above it (5 points of implied probability, their best α), and the accuracy matched the consensus'
  prediction (45.9% expected, 44.4% actual); +3.5% over 10 years of closing odds, +9.9% on minute-by-minute odds, +6.2% over 672 real bets, then the
  books limited them. Buchdahl's "wisdom of the crowd" (football-data.co.uk) is the same idea.
- Pinnacle alone is the best single fair price for main markets (Data Golf: betting other books against Pinnacle's no-vig odds gives about a
  1-to-1 expected-to-actual ROI; Buchdahl, football-data.co.uk "wisdom of crowd … closing odds": 26,960 European football 1X2 bets with a 2%
  minimum EV against Pinnacle's odds at the time, expected 4.13% (2.88% against the closing odds), actual 4.90%). *(Corrected 2026-10-02 17:10Z:
  the first version said "31,000 bets, 3.8% expected, 3.6% actual", from a search summary, not his page.)*
- **But not for props:** a 600-million-line-move study of MLB props (SmartStake, a vendor: treat as one source) ranks Kalshi and ProphetX the
  sharpest, Novig next, DraftKings/FanDuel middle, and **Pinnacle and Bookmaker the softest** (tiny prop limits, little modeling). Pinnacle also
  lists few props (§64.3). Requiring Pinnacle on props both cuts volume and anchors on a soft prop price.
- Estimated edges shrink when realized: Data Golf's model realized about half its estimated EV (0% threshold −0.9% ROI, 5% → +1.5%, 8% → +4.4%);
  Tj's record shows the same shrink (~1.5–2 points).
- Commonly recommended minimums: main markets 0.5% when the fair is Pinnacle-anchored, props 3–5%, less liquid sports 2–5%, odds +150 to −200
  (betsharpmoney.com filter guide, read 2026-10-02); Wizard of Odds: props "+3% or better" (its context: a bettor's own model). *(Corrected:
  the first version attributed a 5% prop floor to wizardofodds.com; it says 3%.)* Unabated: prop closing lines are less efficient (low limits keep sharps out),
  so CLV on props is a weaker yardstick than on NFL sides: a reason to also watch results over hundreds of bets.

**Answers:**
1. *7 of 9:* not a red flag by itself. The consensus EV already includes the two that say no (the app's own check takes the lower of the
   mean and median of every two-sided company, worst-case devig). What matters is **who** dissents and **by how much**: the sharpest book for that
   market saying clearly "not +EV" (Kalshi/ProphetX on props; Pinnacle/Circa on sides and totals) is a red flag; two soft books a hair under is
   noise. "Every book must agree" removes exactly the bets with the most books behind them (more books, more chance one is off) and cuts volume
   hard with no evidence it raises CLV: keep it off. Many US books share one feed or copy each other, so 7 books can be 3–4 opinions (sister
   sites are already one vote, §AS4).
2. *Soft-book consensus vs a sharp book:* consensus is good enough, and Tj's CNO props (+2.0% CLV, 71% beat) prove it on his own bets. Don't
   require a sharp book to confirm: on props it's the wrong book (Pinnacle is soft and absent) and kills volume (§64.3). Use sharp books as a
   veto when they are on the page instead (not a setting yet: proposed below).
3. *Lowest EV:* his record supports **2%** as the floor (2–3% bets: +1.3% CLV) and nothing below (1–2%: +0.5%, not distinguishable from zero).
   **2.5%** is the recommended balance (a buffer for the ~1.5-point shrink, roughly 1.5× the volume of 3%); 3% for extra safety. Props sit on the
   higher side of the outside guidance, but his CNO props already clear it.
4. *Other settings:* books pricing both sides ≥ 3 (the most the app offers), agreeing ≥ 3, every-book-must-agree off, longest odds +130 to +150,
   ¼ Kelly (½ is too swingy for edge estimates this noisy), CNO devig Conservative (default), background auto-scan every 30–60 s (edges that last
   a minute are caught before they close), sharp-book confirmation off. And stop auto-betting game totals, team totals and 1st-half totals until
   they show a positive CLV (−2.8%, −2.0%, −5.9%; small samples, but the same sign on both scanners): no setting does this yet.

**Proposed changes (not built; Tj's call):** (a) a "sharp veto" mode in place of "sharp must confirm": skip only when the market's sharpest
book on CNO's page (Kalshi/ProphetX for props, Pinnacle/Circa for game lines) says not +EV; (b) an auto-bet market filter (props only, or skip
totals/team totals/1st-half totals); (c) record each bet's agreement at the time of the bet (x of y, which books dissented, a sharp book present
or not) and split CLV by it in Diagnostics, so "7 of 9" is answered from his own bets in a few weeks.

## 66. Re-checked research, deeper CLV research, the sharpest book per bet type, and the presets (v0.45.0, 2026-10-02 ~17:01Z; Tj: "Use the research you just found, double check and make sure it is accurate. Do more deep research on clv and best settings for finding clv … make a preset section … make it so I can make my own settings presets. The auto bet feature must abide the preset rules … 1) sharp veto instead of requirement … separate types of bets by which books are sharpest for those bet types. 2) record all types of information on the bet as placed … include this information for all bets in the diagnosis feature … saved to my android downloads folder")

### 66.1 §65 re-checked, source by source (2026-10-02 ~17:05–17:15Z)
- **Kaunitz, Zhong & Kreiner** (arXiv 1710.02824, full PDF read): consensus = the mean of **32** bookmakers' odds; bet when the best odds beat
  `1/(p_cons − 0.05)` (α = 0.05 chosen from 0.01–0.1; 0.06 was as profitable); 10 years of closing odds: 44.4% accuracy (45.9% expected), +3.5%;
  minute-by-minute odds: 6,994 bets, +9.9%; real money and paper trading: 672 bets, +6.2% (paper 407 bets +5.5%, real 265 bets +8.5%), 30% of the
  prices they saw had already moved at the book. Correct in §65 (the "~30" is now "32").
- **Buchdahl** (football-data.co.uk, his own page): 26,960 bets at ≥ 2% EV against Pinnacle's odds at the time: 4.13% expected, 4.90% actual
  (2.88% expected against the closes). §65's "31,000 / 3.8% / 3.6%" was a search summary's: corrected.
- **Data Golf** ("How sharp are bookmakers?", read): Pinnacle's no-vig odds give ~1:1 expected vs actual ROI against other books; its own golf model
  at 0% / 5% / 8% EV thresholds: −0.92% / +1.45% / +4.43% realized: estimated edges realize at roughly half. Correct.
- **SmartStake MLB props study** (read): >600 million MLB prop line moves; crossed-market test; sharpest Kalshi and ProphetX, then Novig, DraftKings
  and FanDuel middle, Pinnacle and Bookmaker softest; FanDuel weaker on MLB than its NBA/NFL reputation; closing-line (Brier) accuracy couldn't
  separate books (all converge by the close). Vendor study, methodology summarized, not published in full: one source.
- **betsharpmoney.com filter guide** (read): main markets 0.5%, props 3–5%, less liquid sports 2–5%, odds +150 to −200; Circa often sharper than
  Pinnacle in college; Pinnacle alone for soccer and tennis; Circa and Bookmaker beside Pinnacle for NBA/NFL. §65's "props 3–5%" is right; its
  wizardofodds attribution of 5% was wrong (Wizard of Odds says "+3% or better"): corrected.
- **Unabated** ("Getting precise about CLV", read): measure CLV against a no-vig close; props and other low-limit markets have weak closes ("CLV
  doesn't mean anything in props" is its strongest wording): judge props by results too. Correct.
- **Tj's record** (INBOX.md 2026-10-01T06:31Z, re-read line by line): every number in §65's table matches the file.

### 66.2 Deeper: what predicts CLV and profit
- **Which book is sharpest depends on the bet type.** Sides and totals: Pinnacle first (Data Golf, Buchdahl, betsharpmoney), Circa beside it and
  ahead of it in college (betsharpmoney). Player props: the sides-and-totals sharps post few props late at low limits (establishtherun.com "current
  ecosystem of NFL player props": Pinnacle and Circa "don't prioritize props"; among US books Caesars, which takes $500 even on openers, and FanDuel,
  which originates its own numbers, are the efficient ones); on MLB props the exchanges Kalshi and ProphetX lead and Pinnacle trails (SmartStake).
- **Bigger estimated edges are more real, with shrinkage.** Tj's 4%+ bets beat the close 83%; his 1–2% only 58%; estimated edges realize at
  roughly half to two-thirds (Data Golf; his shown EV runs ~1.6 points above the close).
- **Odds range.** Long shots carry the favorite–longshot bias and thin books (§8.1, §16.2); +150 to −200 is the common range (betsharpmoney).
- **Sample size.** CLV needs ~200–500 bets per group before a split means anything (pikkit.com, bet-analytix.com guides); his groups are 10–90.
  So the presets lean on outside evidence where his own groups are small, and the new per-bet record (66.4) makes his own data decide later.
- **Timing.** The close is the sharpest price; an edge found earlier has more time to be proven right or wrong. No source measured CLV by time to
  start for props; his record can't split it yet. Recorded from now on (66.4) rather than guessed into a rule.

### 66.3 What was built
- **Sharp veto** (`SharpVeto`, data): skips a bet only when the **sharpest book for that kind of bet** that prices both sides on the bet's book page
  says it isn't +EV at Novig's price (its own two prices, devigged worst case, like the book check). The ranking by kind (`SharpVeto.ranking`):
  player props: Kalshi, ProphetX, FanDuel, Caesars (MLB props: Kalshi, ProphetX, DraftKings, FanDuel); sides, totals, team totals and period lines:
  Pinnacle, Circa (college football and basketball: Circa, Pinnacle); soccer and tennis: Pinnacle. When none of them prices both sides, there is no
  veto (the bet goes on its other criteria). The "require a sharp book to confirm" mode stays as an option (`SharpMode.CONFIRM`), off.
- **Presets** (`Presets`): a built-in "Volume + safe CLV" (recommended) and "Strict CLV" set every rule the auto-bet and the scanners use at once;
  Tj saves his own (Settings › Presets: save current, apply, delete). The auto-bet reads exactly those settings, and the new rules (bet types,
  shortest odds) are enforced in `AutoBet.judge`. Values in 66.5.
- **The bet as placed** (`AtBet`, kept on the bet, never overwritten by re-checks) and Diagnostics' per-bet record and splits (66.4), and the
  Diagnostics file saved to Downloads/Vigilant as well as shared.

### 66.4 What each bet records as placed
See `AtBet` (data/tracker): when, app version, scanner, by hand or auto, preset and rules in force, league, kind of bet, minutes to the start,
American odds and price, the other side's price, dollars available at that price, Novig's quote age, CNO's EV, fair, book count and one-way flag,
the book check (fair, EV, books pricing both sides, one-sided, agreeing, verdict, page age), every book on the page (both prices, its own fair, its
EV at Novig's price, agrees or not), the sharpest book and what it said, the stake rule, Kelly fraction, full-Kelly share, stake, bankroll and
wallet. Diagnostics lists every bet with it (JSON lines) and splits CLV by agreement, dissent, the sharpest book's verdict, minutes to the start,
the check's EV band, kind and preset.

### 66.5 The presets' values, and what Apply changes (written 2026-10-02 ~17:45Z; the values are `Presets.kt`'s)
| Rule | App default | **Volume + safe CLV** (recommended) | Strict CLV | Why (sections above) |
| :- | :- | :- | :- | :- |
| Smallest edge (auto-bet) | 3% | **2.5%** | 4% | Tj's 1–2% bets weren't distinguishable from zero CLV, 2–3% beat the close by +1.3%, 4%+ beat it 83% of the time; shown edges shrink ~1.5 points (65, 66.2) |
| Books pricing both sides | 2 | **3** | 3 | more independent opinions; 3 is the most the app offers (65) |
| Books agreeing on their own | 3 | **3** | 4 | |
| Every book must agree | off | **off** | off | a dissent matters only when it is the sharpest book for that kind of bet: the veto (65, 66.2) |
| Odds range | any | **−200 to +150** | −200 to +130 | favorite–longshot bias, thin books on long shots (8.1, 16.2); betsharpmoney's range (66.1) |
| Kinds of bet | every kind | **props, moneylines, spreads** | same | his game totals (−2.8% CLV), team totals and 1st-half totals lost to the close (65) |
| Stake rule | $1 | **¼ Kelly** (still capped by his own most per bet and per day) | same | edge estimates this noisy: a quarter of Kelly (65) |
| Sharp books, auto-bet and alerts | veto | **veto** | veto | (66.3) |
| Alerts from | 3% | **2.5%** | 4% | same floor as the auto-bet |
| CNO filters | conservative, 4+ books, 50 rows | **conservative, 4+ books, 1% min, 100 rows, complete book, both sides** | same | more rows = more candidates for the same read |
| Background scan | 10 min | **30 s** | 30 s | CNO publishes every 13–33 s; Vigilant's own (credit-spending) scan keeps its own cap (`vigilantEverySeconds`) |

Not set by a preset: bankroll, wallet, most per bet, most per day, keys, and whether auto-bet / auto-scan are on.

**Kind of bet, fixed in the post-build sweep (AW7):** `BetKind.of` first read kinds through the grader, which gives up on what it can't grade from a
score: Player Interceptions, Sacks, Field Goals, Singles, Pitcher Outs, Blocked Shots and Shots on Target came out "Other", so the Volume preset never
auto-bet them and the veto judged them by Pinnacle/Circa instead of the prop books. It now falls back to the market's words (player/batter/pitcher/
anytime → prop; quarter/period/half/inning → period line), and a tennis set spread or total sets counts as the whole match. `SharpVetoTest`.

## 67. Locking in a profit on your own Novig bets by taking the other side later (2026-10-02 ~19:00Z; Tj: "Research and see if it is possible to arbitrage bet my own bets in novig based on timing … It must guarantee profit because I will put real money on it … include an option to auto bet these bets")

### 67.1 Verdict: plausible, and on Novig it can be made exact
On an exchange this is "greening up" / hedging: after the price moves your way, buy the other side of the **same market** so whichever side wins
pays the same ([Wikipedia: Betting exchange](https://en.wikipedia.org/wiki/Betting_exchange); [sharpbetting.co.uk green-up calculator](https://sharpbetting.co.uk/calculator/green-up-trading-calculator)).
Novig's own rules make it exact (NOVIG_API.md §7, §8, §14.3, §15):
- Every order is a buy, and one contract pays $0.01 whichever outcome it's on. Holding N contracts of A and N of B pays exactly N × $0.01 at
  settlement **whichever side wins**. Bought A at p and B at q (each per contract): profit = N × (1 − p − q) / 100 − fees.
- A **fair-market-value void** pays each outcome its FMV price, and those prices sum to 1.000 (§14.2): N of each still pays N × $0.01, the same.
  A **push** gives every outcome's collateral back: the bets are refunded; only a fee already paid is lost (pregame fills pay none).
- **`FOK` (fill or kill)** orders exist: the whole quantity fills at the limit price or better, or nothing fills and no money moves. So the hedge can
  never half-fill into a position where one outcome loses, and never fills at a worse price than the one the profit was worked out at.
- Pregame taker fills in game markets are fee-free (`WHEN_LIVE`); in-game, the fee is `coefficient × p × (1 − p)` per $1 of payout, known before the
  order, so it goes into the worst case.
- `GET /v3/portfolio/positions` returns what the subaccount actually holds per outcome (contracts, cost): the app checks it before any lock.

So a lock is a guaranteed profit when: (a) it's the other outcome of the **same Novig market** (a different line is a middle, not a lock; a 3-way soccer
"No" is that market's own other outcome, which is exact); (b) the quantity makes both outcomes pay more than everything spent (both bets, all fees),
worked out at the **limit price** (the worst a FOK can fill at); (c) the order is FOK; (d) Novig's positions agree with the Tracker's fills. A push
returns the money (break-even, or minus an in-game fee): so in-game locks skip markets that can push (spreads and totals on a whole number).

### 67.2 What it costs and when it pays
- A lock doesn't create profit: it **cashes in the line move you already got** (your CLV). If Novig's middle price now is the true chance, holding
  the bet is worth N × (mid − p) and locking pays N × (mid − p − half the spread − fee): the price of certainty is half Novig's spread (often half a
  cent) plus any in-game fee. Sportsbook hedges cost ~2–3% of EV because of the vig ([oddsshopper](https://www.oddsshopper.com/articles/betting-101/how-to-hedge-a-bet);
  [therundown hedge calculator](https://therundown.io/betting-calculators/hedge-bet-calculator)); on Novig's tight books it's far less.
- So: locking turns a +EV bet whose price has moved your way into a smaller **certain** profit with no variance. Letting it ride keeps slightly more
  expected profit with the risk. Both are shown on each lock so Tj chooses (the auto-lock's minimum profit sets the rule).
- How often: a lock exists once the other side's price falls below 1 − (what you paid): the line must move your way by more than the spread. Tj's
  bets beat the close on average (+2% CLV on CNO props, §65), so many will offer a small lock by game time; in-game swings offer large ones.
- Only **bets placed through Vigilant's API** (the Vigilant subaccount) can be locked with a guarantee: their real fills are known and Novig's
  positions confirm them. Bets placed in the Novig app (the cash wallet) are invisible to the API (§14.2), so their contracts can't be confirmed.

### 67.3 How Vigilant scans for them (cheap)
Only Tj's open API bets, grouped by market; one Novig order-book read per market (the websocket's when it's on, else the public/keyed REST read with
its ETag cache: free, no other book's API), on every background cycle while auto-lock is on, when the Tracker opens, and on Check odds now. For each,
the math below on the other outcome's ladder; positions are read (one signed read per market) only when a lock is about to be placed.

### 67.4 The math (LockIn.kt)
Market position: A held `qA` contracts having spent `SA` dollars (fills + fees), B held `qB`, `SB`; S = SA + SB. Buy `x` of B at the limit `q`
(Novig grid), worst-case fee F = x/100 × c × q × (1 − q) (c = the market's coefficient when charged, else 0):
- A wins: qA/100 − S − x·q/100 − F; B wins: (qB + x)/100 − S − x·q/100 − F; FMV: between those two. A lock needs both ≥ the minimum profit.
- Equal profit: x = qA − qB. The limit q is the highest grid price keeping both ≥ the minimum; the ladder must hold x contracts at ≤ q.
- The guaranteed profit shown is the worst case at the limit; a FOK filling at better prices only adds to it.

## 68. API-Sports (api-sports.io) and TheRundown, against what Vigilant uses (2026-10-02 ~22:25Z; Tj: "Research whether https://api-sports.io/ or therundown apis are better than the apis I currently use or if they would add value to the app in any way")

**What Vigilant needs from a feed, and what it has now.** Fair odds: two-sided prices from many books, sharp ones above all, no older than 5 minutes
(§24, `Freshness`), for game lines AND player props (most of Tj's bets are props). Closing lines: the price just before the start, for CLV (§41-§42).
Grading: final scores and box scores. Injuries: who's out (voids). Today: Novig's own API (books, catalog, trades), CrazyNinjaOdds (every book's
page, free), ParlayAPI Starter $5 (Pinnacle, ProphetX, BetOnline + US books, a league's props in one call, Pinnacle's closes 7 days back, injuries),
PinnWire/pinnapi (Pinnacle with player props), PropLine (30 books' props), The Odds API (free), Kalshi and Polymarket (exchanges), ESPN and MLB
(scores, box scores, closing odds), Novig's daily trade files (closes). Budget ~$40/month (§43).

**TheRundown (therundown.io/api, /pricing/api, read 2026-10-02).** Books on paid plans: Pinnacle, Circa, BetCRIS, BookMaker, Heritage, Bet105, LowVig,
BetOnline, Matchbook, the US books; prediction markets (Novig, Kalshi, Polymarket, ProphetX) are listed but not on Free-Ultra.

| Plan | $/month | Books | Delay | Props | Closing lines | History |
| :- | :- | :- | :- | :- | :- | :- |
| Free | 0 | BetMGM, DraftKings, FanDuel | 5 min | no | no | none |
| Starter | 49 | all | 60 s | no | no | 7 days |
| Pro | 149 | all | 30 s | yes | yes | 30 days |
| Ultra | 399 | all + websocket | real time | yes | yes | 90 days |

- Free: three soft books five minutes late. Vigilant never prices from a quote over 5 minutes old, so everything it served would already be too old. No use.
- Starter $49 is the only plan near the budget, and it's over it. Its one real addition is sharp game-line books Vigilant doesn't read itself
  (Circa, BetCRIS, BookMaker; CNO's pages already show Circa, which the sharp veto uses). No props, no closes, a minute late: it can't touch the
  props that are most of Tj's bets, and it can't replace ParlayAPI's closes. Not worth $49 on top of what's here.
- Props and closes cost $149 (Pro): three to four times the budget for what ParlayAPI ($5) + PinnWire + PropLine + CNO already give.
- Novig's own odds through it: only on top tiers; Vigilant reads Novig directly, free and real time.
Verdict: no (same as §36 and §43; its book list grew, its prices didn't).

**API-Sports (api-sports.io; one account, a separate API and plan per sport: API-Football (soccer), API-American-Football (NFL, NCAA), API-NBA,
API-Basketball, API-Baseball, API-Hockey, MMA, F1, …).** Its site and docs refuse automated reads (HTTP 403 from here; the API answers
"Missing application key" without one), so this is from its published plan figures and API-Football's documented odds rules:
- Plans, per sport API: free 100 requests a day; Pro $19 (7,500 a day), Ultra $29 (75,000), Mega $39 (150,000). Each sport is its own subscription.
- Odds: bookmaker pre-match odds that are **refreshed every 3 hours** (API-Football's /odds: "updated every 3 hours", 1-14 days before the game,
  7 days of history); in-play odds for soccer only. The books are mostly European (Bet365, 1xBet, Pinnacle, bwin, Unibet, Marathonbet …); no
  Novig, no exchanges, and no US player props found in any of its sports.
- Data: fixtures, live scores (about every 15 s), standings, players, injuries (soccer and American football), team and player game statistics.
- Fair odds: a price up to 3 hours old can never price a bet here (5-minute rule), and there are no props. No use.
- Closing lines: 3-hourly snapshots aren't a close. No use.
- Grading: ESPN's free box scores and MLB's feed already grade every sport Vigilant bets; API-Sports adds no sport and no stat Vigilant lacks.
- Injuries: ParlayAPI (§6.1) and ESPN rosters already cover the US leagues Tj bets.
Verdict: no. It's a stats-and-scores API with slow bookmaker odds, built for soccer sites, not for +EV betting on Novig.

**What would actually add value, if anything is ever bought:** ParlayAPI Pro ($20: 100,000 credits, 30 days of closes) only when its meter runs
short on busy days (§43). Nothing else in this price range beats what Vigilant reads now.

## 69. How professional bettors actually profit, Vigilant against them, and what Novig's history says (2026-10-03; Tj: "Now do deep research on proven successful betting strategies. Not speculative things but research how professional bettors were able to profit. What did they look for? Am I on the right track with vigilant? What settings most closely matches professionals? Novig has betting history with liquidity and there are probably other sources with betting information and history; can these be used to find successful strategies for betting? The goal is to use novig to make as much money as possible.")

Builds on §62 (big Novig money: following it loses), §65-§66 (Tj's own CLV, the presets) and §67 (locks). Only documented records and
measured data here; every number has its source.

### 69.1 What the documented winners did
- **Bill Benter** (Hong Kong racing, the best-documented systematic bettor; his 1994 paper "Computer Based Horse Race Handicapping and Wagering
  Systems: A Report", read in full): a model alone was biased ("always in the direction of being closer to the public's estimate"), so he
  **combined it with the market's own odds** (a logit of both) before judging any edge; bet every positive-expectation bet available; staked a
  **conservative fractional Kelly (1/2 to 1/3)** because "if one overestimates the advantage by more than a factor of two, Kelly betting will cause
  a negative rate of capital growth … Overestimating the advantage by a factor of two is easily done in practice"; "full Kelly … downswings
  during which more than 50% of total wealth is lost are a common occurrence". It took ~10 person-years and needed "a large number of high
  advantage betting opportunities".
- **Billy Walters** (Gambler, 2023; summarized by squarebettor.com and shortform.com): everything starts with a price he believes is wrong (his
  own numbers: injuries, weather, travel, home field ~2.5 pts not 3); accounts everywhere and watching the market-making books (Circa, Pinnacle,
  MGM, Caesars) for the moves; half-points around key numbers; **1-3% of bankroll a bet**; no parlays; scale is the point ("a 5% edge on $1M is
  $50k").
- **Rufus Peabody** (RotoWire Q&A, The Ringer, Establish the Run): props "much more profitable than sides and totals" in the NFL; attack them
  early in the week; a model doesn't have to be better than the market, only to know something it doesn't price; now bets more on prediction
  markets/exchanges.
- **The modern +EV method** (Kaunitz et al. 2017, Buchdahl's Wisdom of the Crowd, Data Golf, Unabated; §65-§66): take the sharp books' or the
  market consensus' devigged price as the truth and bet only where a book is clearly better. Buchdahl: ~20,000 bets, 3.4% actual vs 4.0%
  expected; later ~18,000 at 3.7% actual vs 4.0% expected.
- **The scoreboard is the closing line.** Buchdahl (Pinnacle articles, pinnacleoddsdropper interview): CLV proves skill far faster than profit
  ("beating the close by 5% can show significance in as few as 50 bets"; results need thousands). Pikkit's benchmark: beating the close on 60%+
  of 200+ bets says you are beating the market; 65-70%+ is strong.
- **What winning looks like in money.** Sustainable ROI is 1-3% on turnover for most winners, 3-6% strong, rarely more (DRatings, Pikkit). On a
  public exchange the record is visible: Polymarket's all-time sports leaderboard (data-api.polymarket.com, read 2026-10-03): #1 +$18.4M on
  $1.85B traded (**1.0%**), #2 +$11.9M on $1.22B (0.98%); the 19 top-50 accounts with over $100M traded make a median **1.8%**; the 50
  biggest by volume: 38 profitable at a median **0.08%** (market makers). The 30-60% ROIs on the list are $10-20M accounts: a profit ranking
  surfaces lucky runs, not a repeatable edge.
- **Long shots lose.** "The Favorite-Longshot Bias in Prediction Markets: Evidence from Polymarket" (arXiv 2609.12878): buys under 10¢ lose
  19.3¢ a dollar, buys at 90¢+ earn 0.83¢; the same bias on Betfair tennis (28,595 matches) and in fixed-odds college markets.
- **The limit problem, and Novig's answer.** Sportsbooks limit winners; that is why pros run many accounts and "beards". Novig is an exchange:
  it doesn't limit or close winners (predictionscout.com, darkhorseodds, Novig's own blog). The ceiling on Novig is liquidity at a +EV price,
  not the book.

### 69.2 What Novig's trades say (`tools/research/novig_strategy_study.py`, all 60 published days 2026-08-03 to 10-01)
9.7M straight-trade rows, 140,429 two-outcome markets; 1.22M taker orders and 1.52M maker fills before each market's close (test A's close,
§62: the median price ~1-2 h before the start). CLV in cents of probability; EV@close = close/price − 1; ROI = flat-stake return at
settlement where the last traded price shows the winner (46% of takers: mostly game lines). 95% intervals bootstrapped over markets.

| Kind (all sizes) | Taker CLV | Taker EV@close | Taker ROI | Maker CLV | Maker EV@close | Maker ROI |
| :- | -: | -: | -: | -: | -: | -: |
| Game lines (5,132 markets) | −0.28¢ | −0.54% | **−2.9%** [−5.2, −0.4] | +0.26¢ | +1.02% | **+3.5%** [+0.6, +7.2] |
| Player props (4,487) | −0.41¢ | −0.80% | −16% (few results) | +0.41¢ | +1.26% | −0.7% (few results) |
| Period lines (716) | −0.38¢ | −0.78% | – | +0.42¢ | +0.86% | – |
| Team totals (71) | −0.80¢ | −1.49% | – | +0.81¢ | +1.91% | – |

- **The resting order wins and the taker loses, in every kind of market, at every size under $100, in every big league** (MLB, NFL, NCAAF, WNBA,
  NHL, EPL); the one exception is a 46-market Champions League sample. Every trade is one taker against one maker (pregame: no fee), so this is
  the spread changing hands: Novig's average taker pays about 0.3-0.4¢ (0.5-0.8%) to the close.
- **Earlier is wider.** Takers lose more the earlier they trade: game lines −0.22¢ in the last hour → −0.54¢ at 72 h+; props −0.29¢ → −1.16¢.
  Makers earn the mirror image. Early Novig markets are thin and wide.
- **By price:** takers lose least on slight underdogs (0.35-0.50: −0.43% EV) and most on favorites (0.80+: −0.87%); long shots under 0.20 are
  thin and noisy.
- **CLV matches results at the extremes:** taker orders that beat the close by 2¢+ returned +6.5% [−6.7, +17.8] at settlement; those that lost
  2¢+ returned −18.4% [−28.3, −8.0]. The middle bands are all about −2% (the taker average): CLV needs large samples to show in results.
- **Big resting orders are the exception** (§62): makers filled for $5k+ lose to the close (picked off). The small maker fills are the winners.
- Not in the data: unfilled orders (the queue, fill rates), who the makers are (Novig's paid liquidity providers are among them, §62.2), and
  most prop results. So "makers win on average" is a fact about fills that happened, at the place in the queue those makers had.

### 69.3 Other sources of betting history
- **Novig trades** (used here and in §62): every trade, both sides, size, time; anonymized, so no account can be followed.
- **Polymarket**: per-account history is public (`/v1/leaderboard?category=SPORTS`, `/trades?user=`). The steady winners there are ~1% market
  makers; copying a ranked "top trader" mostly copies luck (69.1). Following big takers on Novig lost (§62.3); nothing suggests Polymarket's
  would be different, and its markets would have to be matched to Novig's. Not a strategy without a persistence test first.
- **Kalshi**: public trade history with the taker side (`/trade-api/v2/markets/trades`), no accounts. The same maker/taker study could be run on
  it; it is already one of Vigilant's sharpest prop references (§66).
- **Closing lines**: ParlayAPI's Pinnacle closes, ESPN's, Novig's trades (already the Tracker's CLV sources, §41-§42).
- **Bettor-tracking apps** (Pikkit, Juice Reel): aggregate results are published only as marketing snapshots; no raw data.

### 69.4 Vigilant against the professionals
| What pros do | Vigilant | Verdict |
| :- | :- | :- |
| Price every bet against a sharp/consensus fair, devigged (Benter's market blend, Kaunitz, Buchdahl, Unabated) | CNO's consensus + Vigilant's own blend, worst-case devig, lower of mean and median; sharp veto by bet type (§66) | **Matches** |
| Judge yourself by CLV, not wins | True closes captured or back-filled, CLV card, per-bet record and splits (§41-§42, §66.4) | **Matches**; Tj's CNO props: +2.0% CLV, 71% beat (70 bets): "strong" by Pikkit's bar, sample still small |
| Shrink estimated edges; fractional Kelly (Benter ½-⅓; Data Golf realizes ~½) | ¼ Kelly in the presets; shown EV runs ~1.5-2 pts above the close on his bets | **Matches** (¼ is right for an edge that shrinks this much) |
| 1-3% of bankroll a bet (Walters) | "Most per bet" is Tj's own cap | Set it near 2-3% of the wallet |
| Avoid long shots and parlays | −200 to +150 in the presets; no parlays | **Matches** |
| Attack soft markets: props (Peabody) | CNO props are his best group | **Matches**; keep props first |
| Volume at small edges (exchange winners: 1-2% on huge turnover) | Auto-bet, background scan down to 5 s, every league | **Matches**; the limit is bankroll and liquidity |
| No limits for winners | Novig doesn't limit | Scale stakes as the wallet grows (¼ Kelly does it automatically) |
| **Provide liquidity (make), don't only take** (exchange pros, Novig's LPs; 69.2) | Takes only; shows a "maker bid" line on the bet sheet (§16.4) but never posts one | **The gap** |

### 69.5 The settings that match professional practice (the presets, re-checked)
The "Volume + safe CLV" preset (§66.5) is the closest: 2.5% minimum edge (a buffer over the ~1.5-2 point shrink; pros go to 0.5-1% only with
a Pinnacle-anchored fair on main markets, and Tj's own 1-2% band shows no edge), 3 books pricing both sides, 3 agreeing, every-book-must-agree
off, −200 to +150, props + moneylines + spreads (his totals lost to the close), ¼ Kelly, sharp veto, background scan 30 s. Two additions from
this research: (1) **most per bet ≈ 2-3% of the wallet** (Walters; the Kelly cap already enforces it on big edges, this stops a wrong edge
estimate from doing damage); (2) **don't take Novig markets days ahead without a bigger edge**: Novig's early books are its widest (69.2), and
Vigilant's EV already uses the real taker price, so the minimum edge covers it; no new setting needed.

### 69.6 How to make the most money on Novig (in order)
1. **Keep taking only real edges, at volume.** Auto-bet on the Volume preset with background scan on; props first. Profit = edge × turnover:
   at a realistic 1.5-3% ROI, $10,000 of monthly turnover is $150-300, $100,000 is $1,500-3,000. Novig won't cap a winner; the wallet and the
   +EV depth will.
2. **Let the bankroll compound with ¼ Kelly** (stakes grow with the wallet) and keep "most per bet" near 2-3% of it.
3. **Judge every segment by CLV and cut what loses to the close** (his game totals did, §65), at 200+ bets a segment before trusting a split.
4. **Add the maker side** (69.2: the one structural edge every Novig market shows). Vigilant already has the fair price that liquidity providers
   quote from. A careful version, not built: post a post-only bid at the maker price (fair minus a margin, §16.4) on markets with a confident fair,
   cancel it the moment the fair moves or the game nears its start (Novig already voids resting orders at go-live), and record each fill's CLV
   like any bet. Risks: being picked off on news (the big-maker losses in §62.3), the queue (Novig's own LPs are ahead at the best price), and
   fills that are slow and uncertain. Start with a by-hand "Post a bid" on the bet sheet and measure fills and CLV before any automation.
5. **Don't chase** whale-following, liquidity-following or leaderboard-copying: measured on Novig (§62) and Polymarket (69.1), they don't hold.

## 70. Make orders on Novig: where to post, how far under the fair, how long, and how to keep the CLV (2026-10-03 ~02:00–03:00Z; Tj: "Now do deep research on how to do make orders on novig (post orders). The goal of the make orders is to get positive ev orders filled. 1) figure out how to do make orders through the novig API 2) figure out the optimal way to get the most positive EV out of my make orders but also a good chance that the orders get filled 3) figure out how long the make orders should be placed before they expire, and how to set this option in the novig API 4) figure out the best timing and types of bets to make for profit and positive EV 5) figure out how to get the most clv out of make bets 6) … build the system in the app")

Builds on §69.2 (makers beat the close on Novig, takers lose to it). The API is NOVIG_API.md §17. Two new scripts, both re-runnable:
`tools/research/novig_maker_study.py` (a posted bid simulated against every fill in Novig's 60 published days, 2026-08-03..10-01: 10,670
two-outcome markets with a close, 1.6M simulated bids) and `tools/research/novig_book_snapshot.py` (701 live books read 2026-10-03 ~02:05Z).

### 70.1 How the simulation works, and its limits
- A bid on outcome X at price b **filled** if a later maker fill on X traded **below** b before the bid expired: Novig matches best price first, so
  every bid at b was used up first (a lower bound: a fill AT b only reached a bid there if the queue ahead was used up; that upper bound is shown once).
- A bid at or above X's last traded offer would have taken, not rested (post-only refuses it): 2% of the simulated bids, left out.
- **The fair price at posting time** isn't in Novig's files (Vigilant's comes from other books). Three stand-ins: **w=0** Novig's own price then
  (the median of its last 7 trades: a fair with no outside information), **w=0.25** and **w=0.5** a fair that already knows a quarter or half of the
  move to Novig's close (a sharp book leading Novig). Tj's own taker bets realized about 40% of their shown EV at the close (§65: +2.5% shown, +0.9% CLV;
  Data Golf and Buchdahl about half), so **Vigilant's fair is about w=0.25-0.5 on props, nearer w=0 on game lines** (Novig's game lines are quoted
  by liquidity providers off the same sharp books, §62.2).
- The bid is posted at **fair / (1 + margin)**, snapped down to Novig's grid: "4%" = 4% EV at the fair, about 2¢ under it at even money.
- CLV/EV are measured at Novig's close (test A, §62). ROI at settlement is shown where the last trade shows a winner (mostly game lines; prop results
  are few, so their ROI intervals are wide and nothing below leans on them).
- Not measurable: unfilled orders, the real queue at the time, and whether Tj's bid would change what takers do (small bids: assumed not).

### 70.2 Q2: how far under the fair (posted 3 h before the close, left until the close)
| Margin | Fill (all kinds) | EV@close per fill, w=0 | w=0.25 | w=0.5 | Per posted bid, w=0 | w=0.25 | w=0.5 |
| -: | -: | -: | -: | -: | -: | -: | -: |
| 1% | 48% | −0.24% | +0.35% | +0.79% | −0.12% | +0.17% | +0.39% |
| 2% | 35% | −0.01% | +0.95% | +1.68% | 0.00% | +0.32% | +0.55% |
| 3% | 26% | +0.32% | +1.51% | +2.64% | +0.08% | +0.37% | +0.60% |
| **4%** | **20%** | **+0.73%** | **+2.09%** | **+3.55%** | +0.15% | **+0.38%** | +0.58% |
| 6% | 13% | +1.54% | +3.33% | +5.25% | +0.19% | +0.35% | +0.48% |
| 8% | 8% | +2.61% | +4.63% | +7.08% | +0.22% | +0.31% | +0.39% |

- **Adverse selection is the whole story.** A bid close to the fair fills often, and the fills that happen are the ones where the price moved through
  it: at 1-2% with no outside information the fills LOSE to the close. Every point of margin buys back some of it.
- **The per-bid optimum is 3-4% (w=0.25-0.5), the per-fill value keeps rising with the margin.** 4% is the balance point: about the best expected
  profit per bid posted, +2 to +3.5% EV at the close on each fill, and a fill on one bid in five (more for props, below).
- By kind of market (4%, 3 h before, until the close):

| Kind | Fill | EV@close per fill w=0 / 0.25 / 0.5 | Per posted bid w=0 / 0.25 / 0.5 |
| :- | -: | -: | -: |
| Player props | 30% | **+1.27% / +2.51% / +3.78%** | +0.38% / +0.73% / +1.05% |
| Period lines (1st half, F5 …) | 15% | +1.61% / +2.59% / +3.78% | +0.25% / +0.36% / +0.44% |
| Team totals (74 markets) | 30% | +1.49% / +4.00% / +4.31% | +0.44% / +1.08% / +1.28% |
| Game lines | 13% | **−0.30%** / +1.10% / +2.94% | −0.04% / +0.12% / +0.25% |

**Props make money even with no outside information; game lines only with a fair that leads Novig.** On props the spread is wide and the takers are
recreational; on game lines the takers who reach a resting bid are disproportionately the ones who know the line just moved.
- **By the price of the side bid on (4%):** under 0.20: fill 47%, +2.3/+3.0/+4.2% per fill, the best per posted bid (+1.1 to +1.9%); 0.20-0.35:
  33%, +1.4/+2.9/+3.6%; 0.35-0.65: 15-20%, +0.2-0.6% (w=0) to +3.3-3.7% (w=0.5); 0.65 and up: fills 1-4% (almost never). **Bid on the underdog side.**
  (A taker who buys the favorite fills a maker on the underdog: the favorite-longshot bias, §69.1, from the other side of the trade.)
- **By league (props, 4%):** MLB +1.2/+2.5/+3.7%, NFL +1.9/+2.8/+4.1%, WNBA +0.9/+2.3/+3.8%: all positive. Game lines: MLB −0.6% and MLS −2.1% at w=0,
  NFL/NCAAF/WNBA/EPL about 0 at w=0, positive from w=0.25.
- **Both sides at once:** both bids filled in 2-4% of markets at 4% (17-22% at 1%): a double fill is a lock of the two margins (≈ 2 × 4% of the stake).

### 70.3 Q3: how long a bid should rest, and how to set it
Static bids (posted 6 h before the close at 4%, then left alone), player props:

| Expires after | Fill | EV@close per fill w=0 / 0.25 / 0.5 | Per posted bid w=0.25 |
| :- | -: | -: | -: |
| 15 min | 4% | +1.9% / +5.2% / +6.0% | +0.20% |
| 1 h | 11% | +2.1% / +4.5% / +5.4% | +0.50% |
| 3 h | 25% | +1.6% / +3.3% / +4.4% | +0.80% |
| 6 h | 37% | +1.3% / +2.7% / +3.7% | +1.00% |
| until the close | 40% | +1.3% / +2.6% / +3.6% | +1.04% |

Game lines show the same shape, steeper (w=0.25: +5.9% per fill at 15 min down to +1.1% until the close; at w=0, −0.6% to −0.7% once it rests 3 h+).
**A bid loses value as it ages:** the fair it was priced from goes stale and the later fills are the ones the market moved through. But a longer life
fills more, so the total keeps rising. The way to get both is not a long expiry but **re-quoting**: keep a bid up all the time at the CURRENT fair.

**Re-quoting every 10 min at the fair then, from 24 h before the close (first fill per side):**

| Margin | Fill | EV@close per fill w=0 / 0.25 / 0.5 | Per side w=0 / 0.25 / 0.5 | Props per side w=0 / 0.25 / 0.5 |
| -: | -: | -: | -: | -: |
| 2% | 53-58% | −0.05% / +1.45% / +2.35% | −0.03% / +0.81% / +1.24% | +0.02% / +0.74% / +1.20% |
| 3% | 40-45% | +0.37% / +2.28% / +3.50% | +0.17% / +0.97% / +1.42% | +0.33% / +1.09% / +1.58% |
| **4%** | 31-35% | **+0.87% / +3.07% / +4.65%** | +0.31% / +0.99% / +1.43% | **+0.57% / +1.32% / +1.78%** |
| 6% | 19-22% | +2.03% / +4.65% / +6.81% | +0.45% / +0.89% / +1.27% | +0.84% / +1.45% / +1.88% |
| 8% | 12-14% | +3.34% / +6.2% / +8.94% | +0.48% / – / +1.06% | +0.92% / – / +1.77% |

Props fill on 41-46% of sides at 4% (24-33% at 6%). Re-quoting beats a static bid at the same margin on both counts: 4% props re-quoted earn +1.32% per
side (w=0.25) against +0.73% for a single 3-h bid, and more of it per fill.

**How to set it (NOVIG_API.md §17):** `tif: "PO"` (post only) with `ttl` in milliseconds. Vigilant's choice: a `ttl` of **30 minutes** as a safety net
(if the phone or the app stops checking, nothing rests longer than that on a stale fair), re-posted at the current fair while the bet is still good,
and cancelled at once when the fair moves against it. Never `GTC` (it would rest until the game starts, unwatched).

**When to stop:** fills in the last half hour before the close are still positive on props (+1.5% w=0, +3.4% w=0.25 per fill at 4%) but the lowest
of the day; game lines at w=0 lose in the last hour (−1.25% at 0.5-1 h). Novig voids every resting bid at the start anyway (`GOLIVE`). Vigilant stops
posting **15 minutes before the start** (configurable).

### 70.4 Q4: when and what to post
- **What:** player props first (MLB, NFL, WNBA all positive), period lines and team totals next; **game lines only with a sharp fair** (off by default).
  The underdog side of a market (bid under 0.35) is the best per bid; favorites over 0.65 almost never fill.
- **When:** for props, as early as Vigilant has a fair for them: posted 12-48 h before the close a 4% prop bid fills ~50% of the time at the same EV
  per fill as one posted 1 h before (which fills 16-20%); w=0.25: +2.6% per fill at every posting time, +1.3% per bid at 12-48 h vs +0.45% at 1 h.
  Game lines posted early lose at w=0 (−0.4 to −0.6%).
- **What's there now (live books, 2026-10-03 ~02:05Z):** props within 24 h of the start: **69% of sides have no bid at all** and only 8-28% are
  two-sided: a Vigilant bid would usually be the whole book on its side, with nobody ahead in the queue. Game lines: two-sided, 4-10 grid steps
  (2-5¢) wide, 1-3 orders at the best price, about $25-140 there. Period lines and team totals: 6-14 steps wide.

### 70.5 Q5: getting the most CLV out of make bets
1. **A margin of 4% or more under the fair** (6% for more per fill, fewer fills). Below 3% the adverse selection eats the edge unless the fair leads
   Novig by a lot.
2. **Re-quote at the current fair**, cancelling the moment the fair moves against the bid (the stale bid is the one that gets picked off: §70.3).
   A short `ttl` makes the phone going quiet safe.
3. **Props, period lines, team totals; underdog sides.** Game lines only with a fair that leads Novig (a fresh sharp-book price).
4. **Post early** (as soon as there's a fair) and keep re-quoting until ~15 min before the start.
5. **Size like a bet** (¼ Kelly on the EV at the fair, capped by the per-bet limit): a filled bid is a bet. Spread the wallet over many small bids:
   Novig holds the cost of a resting bid (assumed), so the number of bids at once is the wallet ÷ the stake.
6. **Judge it by CLV per fill**, like the taker bets, over 200+ fills, and by kind (props vs game lines): the w the simulation can't know is what
   Tj's own fills will measure.

**What it could make:** at 4% on props re-quoted, each side quoted earns +0.6% to +1.8% of its stake per game (w=0 to w=0.5), about +1.3% at Tj's
realized w. 20 props a day, both sides, $10 a bid: ~40 bids → ~17 fills → ~$170 a day matched at +3% EV at the close ≈ $5 a day, before scaling
the stake with the wallet. It adds to the taker bets (it uses lines the taker side skips: their offer isn't +EV, but a bid under it is).

### 70.6 What was built (v0.51.0, the Bids tab)
The rules above as defaults (BRIEF.md "Make orders"): off until Tj switches them on; 4% under the fair, $5 a bid (never over the per-bet limit), at most 20 bids
and $100 up at once, props + 1st-half/inning lines + team totals (game lines off), bid prices 0.10-0.65, both sides, at least 2 books behind the fair, each bid
post-only with a 30-minute `ttl`, none within 15 minutes of the start. A pass runs after every Vigilant scan (new fairs) and every background cycle (fills, expiries,
the start coming up, fairs going old): a bid moves down at once when the fair falls (the stale bid is the one that gets picked off, §70.3), up only after 2 grid steps
(moving loses its queue place), and is re-posted before it expires. Fills are Tracker bets (`TrackedBet.maker`, "your bid, filled") with the fair and EV when posted,
so their CLV is measured like every bet's; Diagnostics has the settings, the bids' outcomes and the fills' CLV. The tab lists the bids up (Cancel), the bids the
latest scan would post (Post, by hand, works with the switch off), why the other lines get none, and the fills with their CLV. Judge it by the fills' CLV over
200+ fills (§70.5): that measures the w the simulation could only bracket.

### 70.7 v0.52.0: only +EV bids, never older than their fair, sized and judged like the pros (2026-10-03; Tj: "Make sure the math is sound and that it only will make bets which are positive EV, aiming for as much profit as possible … It should not keep make orders long enough that they lose their positive EV … Make sure to implement the strategies of proven professional bettors")
- **The EV invariant.** A bid is posted at `floor(fair / (1 + margin))` on the grid, so its EV at the fair is at least the margin; at every pass a
  resting bid is kept only while that's still true at the new fair (the bid wanted is the same or higher), else it comes down at once. Checked over
  4,000 random fairs, margins and moves (MakerTest). The margin is the floor, not the target: 4% by default (§70.2's per-bid optimum), never break-even.
- **Never older than its fair (§70.3: "a bid loses value as it ages").** Expiry = min(ttl, start − stop window, oldest book price behind the fair +
  its freshness limit: 5 min, 10 min for games over 3 h off). With background Vigilant scans every 4 min the bids are re-priced before their fair goes
  old; without them they lapse within minutes instead of resting on a stale price.
- **Confidence before money (Benter: combine with the market; Buchdahl/Unabated: the books must agree).** At least 2 books each put the bid at +EV on
  their own worst-case devig; a sharp book saying no vetoes it; game lines (efficient on Novig, §70.2) need a sharp book in the fair.
- **Fractional Kelly (Benter ½-⅓, Walters 1-3% of the bankroll a bet).** ¼ Kelly on the edge at the fair by default, capped per bid (`makerMaxStake`,
  never over the per-bet limit), the day's API limit and the wallet. A filled bid is a bet and its CLV is measured like every bet (judge it over 200+ fills).
- **Underdog sides and props first** (the plan posts cheapest first; props, 1st-half lines and team totals by default): §70.2's best per-bid groups.

### 70.8 v0.53.0: why Tj's first bids never filled, and what changed (2026-10-03; Tj: "I posted plenty of bids and not one of them was taken. Maybe the criteria is too restrictive. Investigate, but it should never be too loose where it is no longer positive ev")
**Evidence (Tj's v0.52.0 Diagnostics, 00:45 EDT):** 83 bids on record over ~1.5 h (v0.51.0 went out 23:05 EDT), late at night: 46 expired, 19
cancelled by the Pause button, 18 cancelled "the fair price goes old within a minute"; 0 fills. The Vigilant scan took **484 s** (the key refused
mid-scan with `451 ANONYMIZED_NETWORK`, so most reads went the public routes at ≤6/s, which answered 429 about once a minute), and **every automatic
pass waited for a finished scan**. By the end the fair prices read first were 7-8 min old (5-min freshness under 3 h from the start, 10 min past it),
so the after-scan pass found most lines too old and auto-make posted nothing; Tj's Post-now taps judged the running scan and worked. Those bids then
lived **minutes** (expiry bounded by the fair's freshness, §70.7) with gaps of several minutes between scans.

**What that means for fills:** §70.3's numbers assume a bid up all the time from hours out: a static bid left 15 minutes fills about 4% of the time
(props, 4%), re-quoted for 24 h about 40-46%. Bids up a few minutes each, for about an hour and a half after midnight, should expect a fill or two
from 83, not dozens: zero is unlucky, not proof the margin is wrong. **The criteria are not too tight; the bids weren't up long enough.** The margin
stays 4% under the fair (the per-bid optimum, §70.2; 3% is the lowest the research supports; nothing under it ships), and every +EV check stays.

**v0.53.0 keeps bids up:** passes run on a scan still in progress (every 20 s; a bid on a line not judged yet stays up, its `ttl` bounds it), the
lines use the last scan's Novig book (≤20 min: the price comes from the fair, `PO` refuses a cross), so each league's bids are re-quoted within
seconds of its fair odds arriving; the key comes back 2 minutes after an address refusal instead of 10 (faster scans, fresher fairs); the public
pacer stops re-trying the pace Novig just refused. Bidding turns on what it needs (Vigilant's scanner, the background scan with Vigilant at least
once a minute). **Two limits Tj controls:** the Vigilant wallet ($18.51 then: resting bids are treated as held, so at ¼ Kelly of $250 only ~6-9
bids fit at once; the tab now says when bids wait on the wallet), and the hours (prop takers trade in the day and evening, not after midnight).
Diagnostics now records per bid who posted it, how long it rested, and whether it led its side of Novig's book when posted (`MakerStats`), so the
next file says whether bids rest long enough and sit at the top of the book.

### 70.9 v0.54.0: why none of Tj's automatic bids filled either (2026-10-03; Tj: "None of my auto bids were accepted", with the v0.53.0 file)
**Evidence (v0.53.0 Diagnostics, 02:37 EDT; v0.53.0 installed 01:56):** 289 bids on record (206 by auto-make in ~40 min, 83 by hand earlier), rested
**1 min at the median** (6 at the 90th), 0 filled. Ended: **163 "About to expire: re-posted"**, 46 expired, 43 paused by Tj, 35 "the fair price goes
old within a minute", 2 sharp veto. Led their side 41%; 4.0¢ under Novig's price to take at the median; Novig's book 2 min old at posting. Up to
1,913 wanted bids waiting on the $8.32 wallet or the 20-bid cap. Wallet "$8.32 → $8.32 with $12.54 resting". `/v3/portfolio/fills` answered 429.

**Why (four causes, none of them the margin):**
1. **A re-post loop.** A bid lives only as long as its fair price stays fresh (§70.7: 5 min under 3 h from the start, 10 min past; Vigilant's fairs
   are already 1-3 min old when a scan prices them). A bid within 2 min of its expiry was re-posted every pass, from the same fair: the new bid
   ended at the same moment, joined the back of the queue (no amend on Novig, §17), and was re-posted again 20 s later. A "bid" was a string of
   one-minute bids, each starting last in line. Two passes ran at once (the scan's own every 20 s and the background cycle's), a pass every ~10 s.
2. **Bids behind other bids.** Novig fills the best bid first; a bid behind another fills only once that one is used up (on Novig's thin prop books,
   practically never in minutes). 59% of the bids were counted behind one, many of them behind **our own** previous bid at the same price (the book
   read 2 min earlier still held it), the rest behind someone else's. With the wallet and the cap binding, the order bids went up in (cheapest
   first) didn't care.
3. **The hour and the length.** §70.3: a prop bid at 4% left 15 minutes fills ~4% of the time. ~206 bids × ~1.5 min ≈ 5 bid-hours ≈ 20 such
   15-minute bids → **under one fill expected**, after 2 AM when prop takers are scarce. Zero was the likely outcome even without causes 1-2.
4. **Novig holds nothing for a resting bid** (verified, NOVIG_API.md §17): the app thought the balance already reflected the bids up and posted
   $12.54 against $8.32. Not why nothing filled, but a limit broken.

**What changed (v0.54.0), no +EV rule loosened (4% margin, books agree, sharp veto, freshness, stop window all unchanged):** an expiring bid is
re-posted only when a fresher fair lets the new one rest a minute longer (else it keeps its place to the end of its life); one pass at a time (the
background cycle skips its pass when one ran in the last 15 s); a line's best bid leaves out Vigilant's own bids that were in the book read;
**bids that would lead their side go up first** (then the underdog side, then the most EV); every bid not yet ended counts against the wallet; one
fills read a pass for all bids (`startsAfter`), cancels confirmed by one re-read of the open orders, no per-order record reads once an order showed
open. **What decides fills from here:** how long bids can rest (each one's fair: the fair-odds sources' credit budgets set how often fairs are
re-read) and the hours they're up (afternoon and evening, when the takers are there). Judge it again over a full day of bids, by fills per bid-hour
and, past ~200 fills, by their CLV (§70.5).

## 71. Trap bets: "gifts" a sharp on Novig priced before the books caught up, and the trap guard (v0.55.0, 2026-10-03; Tj, with the v0.54.0 Diagnostics file: "Research if there is a way to indicate sharp bettors offering odds based on knowledge that the other books haven't caught up to, because I noticed that some of my "gift" positive EV bets moved against me dramatically, and I think they were made by sharp bettors with information not yet reflected by other sports books. See if there is a way to find these trap bets and avoid them.")

**Verdict: yes, two signals carry it, and both are now the trap guard (`data/scanner/TrapGuard.kt`, on by default).** (1) **When** the bet is
placed: Tj's own bets placed 6 h or more before the start lost to the close; bets inside 6 h beat it. (2) On **game lines**, Novig's own trades:
a price that just fell 2¢+ under where it traded this hour, while $100+ was bought on the other side, is a trap on Novig's whole history. The
size of the resting order that gives the gift, its being quote-led or flow-led on props, and the size of the gift itself are **not** signals.
Re-run both: `python3 tools/research/tj_bets_by_lead.py <diagnostics file>` and `python3 tools/research/novig_trap_study.py --cache DIR`
(about 5 min for all 61 published days once cached; `pip install pandas numpy`).

### 71.1 How a gift is made on Novig, and who makes it
Every price a taker gets is someone's resting bid on the other side (NOVIG_API.md §7): the Under at +117 exists because someone bids the Over at
0.54. A "gift" against the books means that bidder values the Over above the books' consensus. Either the bidder is stale or careless (a real
gift) or knows something (an injury, a role, a lineup, a better model) and the books are the stale side (a trap). Tj's worst closes in the file
look like the second: Veronica Burton Under 11.5 +117 (fair +113) closed +147, DK Metcalf Under 40.5 +115 → +144, Jesus Luzardo Under 2.5 K
+125 → +156, Jamie Drysdale Over 1.5 SOG → −24.8% CLV. All were placed 10 to 100 hours before the start, on props with a handful of Novig trades.

### 71.2 What Tj's own bets say (`tj_bets_by_lead.py`, v0.54.0 file: 176 bets with a true close and an EV, outliers and locks aside)
| Placed before the start | Bets | CLV (95%) | Beat the close | EV when bet |
| :- | -: | -: | -: | -: |
| 0-1 h | 23 | +3.08% ±1.38 | 83% | +2.70% |
| 1-3 h | 19 | +1.47% ±1.56 | 74% | +3.46% |
| 3-6 h | 13 | +1.60% ±1.16 | 77% | +3.08% |
| 6-12 h | 34 | −0.25% ±1.88 | 41% | +2.95% |
| 12-24 h | 47 | −0.67% ±2.21 | 47% | +2.51% |
| 24-48 h | 26 | −1.06% ±2.68 | 50% | +2.81% |
| 48 h+ | 14 | −0.57% ±4.05 | 50% | +2.25% |
| **under 6 h** | **55** | **+2.17% ±0.85** | **78%** | +3.05% |
| **6 h or more** | **121** | **−0.62% ±1.24** | **46%** | +2.67% |

The gap (2.8 points) is about 3.6 standard errors; it holds for CNO's bets (+2.35% vs −0.23%), Vigilant's (+1.29% vs −1.52%) and props (+2.53%
vs −0.46%). Settled results agree: every settled bet with an EV returned **+8.3%** under 6 h (66 bets) and **−9.8%** at 6 h or more (141).
On the same shown EV, the close realized ~70% of it inside 6 h and none of it earlier.

**Why:** Vigilant and CNO have no model of their own; their fair is the books' consensus, devigged. The consensus is weakest far from the start
(openers, prop lines set by formula, before the news and the limits that move them), and a Novig maker who disagrees with it then is often the
better-informed side (the pros who bet openers bet with models: §69.1). Near the start the books have taken their sharp action and the
consensus is the sharpest price there is, so a Novig price under it is Novig lagging: the real gift. A consensus-follower should bet late.

Matched by time and price to Tj's own taker trades in Novig's file (398 bets, all exact to the cent within a second; the file carries no names):
almost none of Tj's bets was a gift against Novig's own trading level (1 of 109 with a level), so his traps were standing prices that disagreed
with the books, not sudden Novig moves. Thin Novig markets (fewer than 3 trades in the hour before) did worse (−1.0% CLV, n=48) than ones with
a trading level (+0.6%, n=108), not significant alone.

### 71.3 What Novig's trades say (`novig_trap_study.py`, 61 days 2026-08-03..10-02, 1.24M pregame taker orders in 10,556 two-outcome markets)
A gift here is a taker buying a side **2¢+ under that side's own median price over the hour before** (3+ trades). CLV is against test A's close
(§62), in cents; "kept" = CLV ÷ the gift (100% = the whole discount was real).
- **Gifts against Novig's own level keep nothing on average:** 2-3¢ gifts −0.37¢ [−0.60, −0.16], 3-5¢ −0.15¢, 5-8¢ −1.38¢ (an ordinary taker:
  −0.22¢). The new price is the informed one: the market moved and stayed.
- **Game lines:** a gift with **$100+ bought on the other side in the 15 min before** lost **−2.26¢ [−4.19, −0.53]** (n=2,551, 469 markets;
  $1,000+: −2.71¢); quote-led or under $100: about 0. By time to the close it holds at 0-3 h (−3.0¢), 3-12 h (−1.3¢) and 12 h+ (−1.8¢). It fires
  on ~0.3% of game-line taker orders. **This is the trap guard's second rule.**
- **Player props:** gifts are neutral to slightly real (+0.23¢ with $100+ flow, +0.43¢ with $1,000+; NFL props +0.82¢); not a trap signal.
- **Period lines (1st half, F5…):** gifts are real (+1.4¢ quote-led, +1.6¢ flow-led, 38-75% kept): the guard leaves them alone.
- **The size of the resting order a gift came from** (the maker fill size, $0-25 to $2,000+): −0.4¢ to −1.1¢ at every size. Size doesn't mark
  the sharp one (as §62 found for following liquidity).
- **Where the takers are:** 71% of prop taker dollars and 57% of game-line dollars trade in the last 6 h before the close (88% / 75% in 12 h).

### 71.4 What was built (v0.55.0)
- **`ScanSettings.trapEarlyHours`** (Off / 3 / 6 / 12 / 24 h, **6 h by default**): the auto-bet, the +EV alerts (CNO's and Vigilant's) and the
  bids leave alone any game starting more than that far off. The lists still show every bet. A game too far off isn't a candidate, so the
  cycle doesn't spend CNO page reads on it; the auto-bet's report counts how many it left alone (`AlertPicks.tooEarly`). For bids it also puts the
  wallet (which binds: 1,814 bids waited on $3.03 in the v0.54.0 file) where 71% of the takers' dollars trade.
- **`ScanSettings.trapNovigMove`** (on): before the auto-bet places a full-game moneyline, spread or total, one public request reads the market's
  newest trades (`NovigPublicClient.trades`, NOVIG_API.md §5); `TrapGuard.move` takes the hour's level for our side and the other side's buying
  in 15 min; 2¢+ under with $100+ is skipped (2-min cooldown). A failed read stops nothing. What it read is on the bet's record
  (`AtBet.novigMove`: CLEAR / NO LEVEL / UNREAD) and split in Diagnostics ("Novig's own trades just before").
- **Diagnostics:** "Time to the start" now splits **every** bet (from `startsTs − createdAtMs`; it was only the 56 with a record as placed), so the
  next file shows this effect directly; the Settings block states the guard; each bet's JSON line carries Novig's public `marketId`/`outcomeId`
  so research can find it in Novig's trade files by id.
- UI: the Auto-bet tab's "Trap guard" section (both rules), Settings › Alerts (the early rule), the Bids tab's rules (the early rule); Settings
  search finds all three.

### 71.5 What is not verified, and what to watch
- Tj's split is 176 bets over ~8 days, mostly NFL/WNBA/MLB props; the 6 h line is where his data breaks, not a tuned optimum. Re-run
  `tj_bets_by_lead.py` on each new file: if bets 6-12 h out turn positive with more data, 12 h is the setting.
- The move rule's CLV is Novig's own close (test A); the books' fair at the time isn't in Novig's files, so the study can't separate "Novig moved
  and the books followed" from "Novig moved and the books had already moved" (Vigilant wouldn't bet the second: no edge). The game-line result is
  the mix, the safe direction for a veto.
- Unfilled resting orders (the book's history) aren't published, so the quote-led case is seen only through the trades that hit it.

## 72. Beating the close, spotting sharp money, and trap bets: the sources, three new studies, and what changed (v0.56.0, 2026-10-03; Tj: "do deep research on beating clv and finding true positive EV bets while avoiding "trap" bets ("gift" bets with positive EV on paper but are actually offered by sharp bettors with information). find historical betting information from different sources, especially sharp data, which shows how sharp money can be spotted and avoid the other side of those bets ... the timing of positive EV bets, types of bets, and best methods ... both taking and making bets and bids ... whether it would be practical or plausible to "follow" verified sharp bets ... implement all of the findings")

**Verdict.** (1) **The scoreboard holds**: beating the close is the expected return (Buchdahl: open/close ratio ≈ actual return on 132,645
matches; Moskowitz: the close is near-efficient, nothing beats it after the vig). (2) **A "gift" is a trap when the price comes from the better-
informed venue**: on 48,394 soccer matches, when Pinnacle's own early price beat the soft books' consensus, betting it LOST −2.3% to the close and
−21% in results; the close moved ~70% of the way to Pinnacle. When a soft book was the one out of line, the gift was real. On Novig the
informed venue is Novig itself far from the start (§71: Tj's bets 6 h+ out lost to the close) and on game lines it has just moved (§71 rule 2).
(3) **What a +EV bet keeps by the close is about the sharpest book's own edge, not the consensus's**: bets the consensus called +2.5%+ kept +0.8%
(no better than zero) when the sharp book gave them 0-1%, +1.6% at 1-2%, +2.8% at 2-4%, +5.7% at 4%+. Built: **the sharp veto's bar**, 1% by
default (was "any +EV"), for the auto-bet, the alerts and the bids. (4) **Bids**: one-sided buying does NOT predict bad prop or 1st-half fills;
on game lines a side whose Novig price just fell 2¢+ in the hour kept far less per fill. Built: the trap guard's move rule now also stands over
game-line bids. (5) **Following sharps**: impossible on Novig (no accounts); following RLM/"sharp splits" after the move buys the moved price;
on Polymarket the best quarter of sports accounts by past CLV kept beating the close, and a 1-minute copy kept about two thirds of it (+1.0¢,
+1.3¢ on moneylines, before the spread). Vigilant already prices off Polymarket as a sharp book; a wallet-watch feature is a proposal for Tj, not
built. Re-run everything: `sharp_anchor_study.py --dir DIR` (football-data, ~12 s once downloaded), `novig_toxic_flow_study.py --cache DIR`
(Novig's 61 published days, ~15 min), `polymarket_follow_study.py --cache DIR` (public APIs, ~20 min first run), `tj_bets_by_lead.py <file>`
(now also splits Tj's bets by the sharp book's edge when bet).

### 72.1 Sources read (primary first)
- Kaunitz, Zhong & Kreiner 2017 (arXiv 1710.02824, read): consensus-follower: bet when a book's price > 1/(consensus prob − 0.05); 10-year
  closing-odds simulation +3.5%; minute-by-minute simulation betting **1 to 5 h before kick-off** +9.9% on 6,994 bets; real money then limited.
- Moskowitz 2021, J. Finance "Asset Pricing and Sports Betting" (read): >100k contracts, 4 US leagues, 30 years; open-to-close moves chase team
  momentum and about half is reversed by the result, but every strategy loses after the vig ("−32.12% returns per year" for the best). The
  close is near-efficient: fine as the scoreboard.
- Buchdahl, Pinnacle "What can closing odds tell us about profit expectation?" (read): 132,645 matches, opening/closing ratio ≈ actual
  return (1.05 → ~105%); 162,672 matches: opening→closing odds ratio mean 1.003, sd 0.12; spread grows with the odds.
- Buchdahl, football-data.co.uk "Market efficiency of opening odds at Pinnacle vs bet365" (read): 28,748 bet365 openers ≥2% over Pinnacle's
  devigged opener: expected 106.3%, actual 107.4%; 115% expected → 117.7%.
- Data Golf "How sharp are bookmakers?" (read): Pinnacle's fair → ~1:1 expected vs realized ROI; when Pinnacle and Betcris open 5% apart,
  **Pinnacle moves 54.7% of the way to Betcris** (70.2% at 15%), Betcris 15.6%; DraftKings/bet365 barely move; Pinnacle's OPENERS against
  other books' openers at a 5% bar: 9.35% expected, **1.29% realized**.
- Unabated "Who sets the line? The market makers" (read): a few originators (Pinnacle, BetCRIS, Circa in US markets); most books "move their
  lines only when the market leader moves"; early lines at low limits, limits grow toward the start.
- Pinnacle "Market movement in betting" (read): Pinnacle opens at reduced limits and lets sharp money move it ("limits gradually increasing"),
  "check Pinnacle's odds 1 hour before the start" for the fair. Pinnacle "Should you use public betting percentages?" (read): splits come
  from recreational books, aren't tied to the number bet, and waiting for them costs CLV; RLM's "sharp side" after the move is a coin flip at
  −110. Pinnacle "Historical NFL line movements" (read): average NFL move 1.1 points; 23% of lines close where they opened.
- Bürgi, Deng & Whelan 2026 "Makers and Takers: The Economics of the Kalshi Prediction Market" (read): 313,972 contracts; makers −9.64% vs
  takers −31.46% after fees; both lose on cheap contracts (≤10¢ significantly, makers included); makers buying 50¢+ earn +2.6%.
- Microstructure: "The Market Maker's Dilemma" (arXiv 2502.18625) and "Market informedness and market-maker profitability" (arXiv 2606.05882):
  fill probability and post-fill return trade off; quotes should widen or withdraw when flow is informed.
- Copy-trading on Polymarket (dev.to analysis of 200+ whale wallets; marketing-grade, used only as a pointer): most copiers trail the wallet
  they copy because the whale's own order moves the price before the copy lands.
- Injury and lineup news windows (league rules, read 2026-10-03): NBA teams report by 5 p.m. local the day before and again 11 a.m.-1 p.m. on
  game day (official.nba.com); NFL inactives 90 minutes before kickoff, Friday game statuses; MLB lineups usually 1-4 h before first pitch,
  scratches in the last 1-2 h. These are when an informed price appears before the books move, and most of them fall outside the trap guard's
  6 h window or inside its last hours.
- Sportsbook "sharp action" signals (reverse line movement, bet-vs-money splits): the evidence offered is vendor records (Sports Insights) and
  small samples (92 NFL games 2022-23); Pinnacle's own article: splits come from recreational books, aren't tied to the number bet, and the
  "sharp side" after the move is "a coin flip at −110". No independent test shows RLM beats the close after the line has moved.

### 72.2 Study A: who is right when a price disagrees (`sharp_anchor_study.py`, football-data.co.uk, 48,394 matches, 22 leagues, 2019/20-2025/26)
Each match has Pinnacle's early price (Friday/Tuesday afternoon, 1-3 days out) and close, the same for bet365, Bet&Win, William Hill, BetVictor
and Interwetten, and the best (Max) and average (Avg) price across ~40 books, with results; 145,182 1X2, 96,586 over/under 2.5 and 27,764 Asian
handicap outcomes. Fair = power devig; CLV = price × Pinnacle's closing fair − 1; 95% intervals bootstrapped over matches.

**A. A soft price over the SHARP fair** keeps most of it: the best price early vs Pinnacle's early fair kept 82% / 78% / 73% / 58% / 52% / 59% of
the shown edge at 1-2 / 2-3 / 3-5 / 5-8 / 8-12 / 12%+ (n 14,652 … 287), ROI +0.8 to +8.8% (wide); at the close, by construction, all of it, with
ROI +2.2% [+0.7, +3.9] at 1-2% up to +16.2% at 12%+. Single books differ (BetVictor's small early gifts kept nothing; Bet&Win's kept 54-75%).

**B. The best price over the CONSENSUS fair** (Vigilant's method when no sharp book prices a line): early it kept 75% / 87% / 79% / 87% / 59% /
72% by edge band; at the close 74% / 85% / 95% / 101% / 105% / 104%, with significant ROI at 3-5% (+5.5% [+2.7, +8.4]), 5-8% (+5.5% [+0.2, +10.4]) and 12%+ (+18.6%). **Late is
better, and big early edges are the least reliable.** Totals (over/under 2.5) kept more than 100% early and late (+29.6% ROI on 5%+ early, n=102);
Asian handicap's big early edges kept 37% (n=61). A lone book far above the rest (best ≥10% over the average price) kept 55% at a 2-5% edge, vs
~100% when the best is 3-10% over.

**The trap, measured: Pinnacle early vs the consensus early.** When Pinnacle's OWN price was the "gift" against the soft consensus, it lost:
1-2% shown → **−2.34% CLV [−3.11, −1.61], ROI −21% [−32, −10]** (n=694); 3-5% → −2.16%. bet365 as the gift-giver: kept 90% at 1-2% but 23-41%
at 3-8% and 14% at 12%+ (ROI −49% and −57% at 8%+, n=38 each): big gifts from a well-run book are suspect too.

**C. Who was informed.** Share of the early gap (venue vs consensus) the close moved toward the venue: Pinnacle off by 1-2 points → Pinnacle's
close sat **68%** of the way to its own early price and the consensus's close moved **39%** toward it (2-4 points: 51% / 22%); bet365, Bet&Win,
William Hill, BetVictor off the consensus → the close moved −12% to +14% toward them (no information). **Benter's blend on the results**
(logistic regression of the result on both early fairs): Pinnacle 89% / consensus 11%; Bet&Win, William Hill and BetVictor each got a negative weight (−12% to −38%; bet365's fit didn't converge).

**D. Favorite-longshot bias at Pinnacle's close**: wins minus fair −0.81 points [−1.56, −0.02] under 0.10 (n=4,031; about −13% relative), within
±0.3 elsewhere. Bids under 0.10 stay off (Vigilant's bids start at 0.10).

**E. The sharp veto's bar.** Bets the consensus called +2.5%+ (shown ~3.5%), by what Pinnacle's own early price gave the same price:

| Sharp book's edge | n | CLV at the close (95%) | ROI |
| :- | -: | -: | -: |
| under −2% | 389 | +0.42 [−1.45, +2.20] | −6.4% |
| −2 to 0% | 174 | −0.15 [−2.19, +2.14] | −0.1% |
| **0 to 1%** | 155 | **+0.81 [−1.61, +2.79]** | +16.2% (wide) |
| 1 to 2% | 301 | +1.55 [+0.31, +2.81] | −13.2% (wide) |
| 2 to 4% | 1,199 | +2.75 [+2.10, +3.33] | +3.5% |
| 4%+ | 2,252 | +5.69 [+5.17, +6.19] | +5.8% |

Regression on every bet the consensus called +EV: CLV ≈ 0.69 × sharp edge + 0.48 × consensus edge − 0.5% (best price, n=40,741); with one book's
price (bet365, n=3,040) CLV ≈ 0.46 × sharp edge − 0.08 × consensus edge: once the sharp book's edge is known the consensus adds nothing.
**Accuracy**: Pinnacle's close and the average's close are nearly tied (Brier 0.21872 vs 0.21876); both early prices are worse (0.21958, 0.21964).

### 72.3 Study B: do Novig bids get picked off after one-sided buying? (`novig_toxic_flow_study.py`, 61 days, 10,130 markets, 3.39M quotes)
A bid at 4% under the stand-in fair, re-quoted every 10 min from 24 h before the close (as Vigilant's maker does), on both sides; each 10-minute
interval split by what the app can see at posting. w=0: fair = Novig's own price; w=0.25: a fair that knows a quarter of the move to the close
(§70.1). EV per fill at Novig's close.

| Slice (w=0 / w=0.25) | Game lines | Player props | Period lines |
| :- | -: | -: | -: |
| all | +0.08 / +5.36% (fill 0.5%) | +1.67 / +4.36% (2.2%) | +1.75 / +3.28% (1.6%) |
| other side bought $1,000+ in 15 min | −0.58 / +3.89% | +1.76 / +5.09% (fill 8.6%) | (24 fills) |
| our side fell 0.5-2¢ over the hour | −0.94 / +6.22% | +1.75 / +4.89% | +3.48 / +4.48% |
| **our side fell 2¢+ over the hour** | **−2.59 / +1.21%** | +2.35 / +6.01% | (5 fills) |
| our side rose 0.5-2¢ | +1.22 / +6.62% | +2.50 / +4.91% | +1.96 / +3.24% |
| §71's rule (other ≥ $100 and fell ≥ 1¢) | −2.13 / +1.71% | +0.63 / +4.01% | (10 fills) |

- **Props and 1st-half lines: no adverse selection from flow.** One-sided buying raises the fill rate (8.6% vs 1.9%) at the same EV per fill, so
  props stay unguarded (as §71 found for prop takers).
- **Game lines: momentum.** EV per fill falls monotonically as our side's price falls over the hour, and a 2¢+ fall (or §71's rule) leaves
  little; a side that's been rising earns the most. This is the classic maker adverse selection, and why game-line bids need a fair that leads Novig.
- Price bands (props, w=0): bids 0.65+ lose (−1.49%, fill 0.2%); under 0.20 earn the most vs the close (+3.8 to +5.5%), but prop results can't
  confirm it at settlement (too few, biased toward markets traded in-game), and Kalshi's makers lose on ≤10¢ contracts at settlement (§72.1): the
  0.10-0.65 window stays. By time (props, per quote): 3-6 h +0.053%, 0-1 h +0.048%, 1-3 h +0.041%, 6-12 h +0.040%, 12-25 h +0.014% (fills 1.0%).

### 72.4 Study C: can you follow verified sharps? (`polymarket_follow_study.py`, Polymarket's public ledger, read 2026-10-03)
Cohort: Polymarket's sports profit leaderboard (all-time and this month, top 100 each: 186 accounts, 520,560 buys); their newest pregame buys on
resolved markets (15,379 with a minute price history, 157 accounts, 1,571 markets); CLV = price at the game's start − price paid, in cents.

- **The leaderboard as a whole**: CLV +0.25¢ [−0.03, +0.55] (ROI +23%, but the ranking is by profit on these same results: in-sample luck). A copy
  1, 5 or 30 min later paid +0.25¢ more and kept **0.00¢**: following "top traders" picked by profit gets nothing.
- **Persistence**: an account's CLV in its older half vs its newer half correlates **+0.65** (137 accounts with 30+ buys); 63% stay positive.
- **Following the best quarter, picked on the older half, copying the newer half**: theirs +1.51¢ [+1.11, +1.97]; copy 1 min later **+0.99¢
  [+0.55, +1.48]**, 5 min +0.86¢, 30 min +0.61¢ (paying +0.52 to +0.90¢ more than they did). Moneylines +1.75¢ theirs, **+1.32¢** copy@1min [+0.79,
  +2.00]; spreads +0.81¢ (borderline); totals ~0. Taking the OTHER side of their buy a minute later: −0.99¢. ROI at settlement: not significant.
- **What that means for Vigilant**: (a) on Novig nobody can be followed (anonymous trades), and following Novig's big money lost (§62); (b) a copy
  pays the spread (~1¢ on liquid games), so the edge left is about +0.5-1¢ on moneylines the best accounts happen to trade; (c) Polymarket is
  already one of Vigilant's sharp books (`FairSettings.DEFAULT_SHARP_BOOKS`, 70% sharp weight in the blend), so when these accounts move
  Polymarket's price and Novig lags, Vigilant's scan already sees it; the wallet signal adds speed and the part their trades haven't moved the price
  for yet. A "sharp wallet" watch (a list of accounts refreshed by this script; skip a Novig game line whose other side a listed account just bought,
  note it when one bought ours) is plausible and cheap per bet (one public read of the market's trades), but it is a new subsystem: proposed to
  Tj, not built.

### 72.5 Timing of +EV bets
- **Takers: late beats early for a consensus fair.** Tj's bets: under 6 h +2.2% CLV, 6 h+ −0.6% (§71). Soccer: the consensus method kept more of
  its edge at the close than early, and its big early edges least. Kaunitz bet 1-5 h before kick-off; Pinnacle says to read its price an hour out
  for the fair; Data Golf (golf matchups): Pinnacle's own openers realized 1.29% of a 9.35% expected edge. Early is where originators with models win (Pinnacle
  moves 54.7% toward Betcris's opener) and where a consensus-follower is the one picked off. **The trap guard's 6 h stays**; inside it, the closer
  the better (Tj: 0-1 h +3.1%, 1-6 h ~+1.5%).
- **News windows**: the informed price appears around injury reports and lineups (NBA 5 p.m. the day before and late morning on game day; MLB
  lineups 1-4 h out; NFL inactives 90 min out). Novig makers who know first are the trap; books update minutes later.
- **Makers: bids within ~12 h earn about the same per quote; further out fills dry up** (§72.3), and the consensus fair is the weak part early:
  bids keep the trap guard's 6 h.

### 72.6 Types of bets
- Props: the softest books' market and Tj's best group (§65-§66, §69); the sharpest prop books are weak references (Kalshi, ProphetX, FanDuel,
  Caesars), so most props have no sharp veto and keep ~70% of their shown edge late. Bids: props make money even with no outside information.
- Game lines: the most efficient; Novig's are quoted off the sharp books, so the sharp veto (Pinnacle, Circa) decides; bids only with a sharp
  book in the fair and now with the move rule.
- Totals: soccer's over/under kept more than 100% of consensus edges; Tj's own game totals lost to the close (§65): judged by his CLV, as now.
- Period lines / team totals: bids earn steadily (+1.6 to +3.3% per fill, §72.3), no trap pattern.

### 72.7 Spotting sharp money and the other side of it
What marks a trap (a sharp on the other side, the rest of the market lagging), with what it was measured on:
1. **The price comes from the informed venue**: Pinnacle/Betcris/Circa on game lines, an exchange's resting order far from the start, Novig early
   (Tj, §71). Measured: Pinnacle's gifts −2.3% CLV (§72.2), Tj's 6 h+ bets −0.6% (§71). → trap guard early rule; the sharp veto's bar.
2. **The venue just moved with money behind it** (Novig game lines: 2¢ under the hour's level with $100+ bought on the other side): −2.3¢ for
   takers (§71), +1.2% vs +6.5% per bid fill (§72.3). → the move rule, now for bids too.
3. **The sharpest book doesn't give the edge itself**: CLV tracks the sharp book's own edge (§72.2 E). → the bar (1%).
4. Not signals: the size of the resting order (§62, §71), the gift's size on Novig (§71), one-sided flow on props (§71, §72.3), leaderboard or
   splits "sharp money" after the move (§72.1, §72.4).

### 72.8 The method, taking and making (what Vigilant does from v0.56.0)
- **Take**: a consensus edge of 2.5-3%+ (presets) at Novig's live price, inside 6 h of the start, books agreeing, the sharpest book for the kind
  giving at least 1% itself (2% on Strict), game lines not just moved by Novig; when money or the per-cycle cap is short, the bets with the biggest
  **credible edge** go first (the sharp book's own edge where it priced the bet, else 70% of the shown edge, `AutoBet.credibleEv`); ¼ Kelly on the
  shown edge (on an edge that keeps ~70% that is about ⅓ Kelly of the true edge: inside Benter's ½-⅓), **never on a fair above the sharpest book's
  own** (a 4% shown edge the sharp book gives 1.2% would otherwise be staked at ~0.8× full Kelly on the edge that holds: Benter's overbet).
- **Make**: 4% under the fair, props/period/team totals (game lines only with a sharp book and now the move rule), 0.10-0.65, re-quoted, inside
  6 h, every sharp book in the fair giving the bid at least the bar.
- **Judge**: CLV per segment over 200+ bets; `tj_bets_by_lead.py` now splits by the sharp book's edge when bet, so Tj's own data tests the 1% bar.

### 72.9 What was built (v0.56.0)
- `ScanSettings.sharpVetoMinEv` (0 / 0.5 / 1 / 1.5 / 2%, **1% default**): `SharpVeto.judge(…, minEv)` (auto-bet, CNO alerts, the bet's record),
  `MakerRules.sharpMinEv` (bids at their own price), presets (`PresetRules.sharpVetoMinEv`: Volume + safe CLV 1%, Strict CLV 2%; presets saved
  earlier read as 1%), the Auto-bet tab's and Alerts' veto sections (chips + note), the Bids tab's veto row, Settings search, Diagnostics.
- `AutoBet.credibleEv` / `NO_SHARP_KEEPS` (0.7): the auto-bet's order when not every bet can be placed. `AutoBet.stake(…, sharpFair)`: a Kelly stake's
  fair is the lower of CNO's and the sharpest book's own (only ever smaller stakes).
- Game-line bids: `MakerRules.novigMove` (= `trapNovigMove`), `MakerLines.moveWanted` / `withMoves`, `MakerRunner` reads at most 6 markets a pass
  (kept 2 min; none by default, game lines being off for bids); a resting game-line bid on a just-moved line comes down.
- Tests: SharpVetoTest (bar, inclusive edge), PresetsTest, MakerTest (bar at the bid, move rule), AutoBetTest (credible edge), AutoBettorTest (bar
  on the real auto-bet path, order pin), MakerAppTest (reads only for game lines), SharpConfirmUiTest (chips, one setting), SharpDiagnosticsTest;
  mutants 7/7 killed.

### 72.10 Not verified, and what to watch
- The bar's evidence is soccer main markets with Pinnacle as the sharp book; US props' "sharpest" books are weaker, so the right bar there may be
  lower (or the veto less useful). Tj's next file answers it: `tj_bets_by_lead.py` splits his bets by the sharp book's edge when bet.
- The Polymarket follow result is against Polymarket's own close, on 35 accounts' newer trades; whether Novig's close follows is untested.
- The bid study's fair is a stand-in (w); game-line bids are off by default, so the move rule for bids is protection for when Tj turns them on.


## 73. Another AI's CLV/EV report, checked claim by claim (2026-10-03 ~18:00Z; Tj: "Attached is a report from another AI. If the information is accurate, research and see if any of the information can improve the logic, accuracy, or profitability of vigilant"; the report: `research/external_report_2026-10-03_clv_ev.md`)

**Verdict.** The report's general principles are sound and Vigilant already does them (de-vigged fair from sharp books, CLV against the de-vigged
close, 2.5-3% edges, fractional Kelly, no tailing, no parlays). Its two concrete, testable timing claims are **false on Novig** (no late drift toward
favorites; "under a tick in the last 2 h" is about one tick), its Novig market-making advice is **wrong for pregame Novig** (no maker credit pregame,
game-line quotes at Novig's own price earn ~0 after adverse selection), and its escrow claim **contradicts Tj's own wallet**. Its "edges over 5% are your
error" rule is **contradicted by the 48,394-match soccer study** at the close and only half true early, so it isn't adopted as a hard rule; Tj's own bets
will test it (`tj_bets_by_lead.py` now splits by shown edge and by Novig's price over the best book). **No app logic changed**: nothing new in the
report survived testing that Vigilant doesn't already do. Two proposals for Tj (not built): a per-game exposure cap, and (from §72.4) a Polymarket
sharp-wallet watch.

| # | Report's claim | Verdict | Evidence | For Vigilant |
| :- | :- | :- | :- | :- |
| 1 | Beating the no-vig close predicts profit; ~50 bets to show it | Accurate (50 bets only at a ~5% CLV) | Buchdahl 132,645 matches (§72.1); §69.1 | Already the scoreboard (§41-§42) |
| 2 | De-vig the close before CLV | Accurate | — | Already: CLV = de-vigged close fair ÷ cost − 1 (`ClosingLine.clv`) |
| 3 | Pinnacle's no-vig close is the reference | Accurate for main markets | §72.2: Pinnacle close ≈ average close (Brier 0.21872 vs 0.21876) | Pinnacle closes from ParlayAPI are one close source |
| 4 | Pass below ~2%; pros 3-5% | Consistent | §65-§66: Tj's 1-2% bets showed no edge | Presets 2.5% / 4% |
| 5 | Edges >5% are a red flag ("your error") | **Half true** | §72.2 B: vs the consensus at the close 5-8% kept 101%, 12%+ 104% (ROI +18.6%); early 8-12% kept 59%. bet365's own big gifts were traps | Not a hard rule. The trap guard already keeps bets late, where big edges held; `AutoBet.MAX_SANE_EV` (15%) catches data errors; Tj's own split added |
| 6 | Steam/RLM following loses | Accurate | Pinnacle article; §72.4 | Not built (§72) |
| 7 | The trap is adverse selection on resting quotes | Accurate | §71, §72.2-§72.3 | Trap guard, sharp veto bar, game-line bid move rule |
| 8 | Closers beat openers (NFL 65.9% vs 63.5%) | Consistent | §71.2 Tj's bets, §72.2 B | 6 h trap guard |
| 9 | Novig: CFTC market since 2026-08-04; pregame no fee; live taker 0.03·P(1−P); maker credit half the fee; "bot orders don't qualify" | Fees accurate; the credit is **in-play only** (re-checked 2026-10-03 ~18:15Z: Program terms §2, the fees page, the live catalog's `WHEN_LIVE` on every game market, and `GOLIVE` voiding resting orders); "bot orders" exclusion **not in the terms** (only Exchange affiliates and Market Maker Agreement members are excluded) | NOVIG_API.md §8, §17; `Fees.kt` | Already modeled: a pregame bid earns its price edge only, no credit |
| 10 | Tailing sharps has no support | Mostly accurate | §72.4: leaderboard copy 0.00¢; but the best quarter by past CLV copied 1 min later kept +1.0¢ on Polymarket | Wallet watch proposed (§72.4) |
| 11 | "Bet favorites early, underdogs late" (MLB lines drift −3.4¢ to favorites in the last 2 h; +180 dogs move toward the favorite 54%) | **False on Novig** | `novig_drift_study.py` (9,127 markets): game-line favorites drift **−0.08¢** [−0.13, −0.03] in the last 2 h (MLB −0.03¢ [−0.10, +0.03]); +180 dogs moved toward the favorite **33%**, away 49%; Pinnacle soccer early→close: favorites +0.03 to +0.23 points, dogs 50/50 | No timing rule by favorite/underdog |
| 12 | "Final two hours move less than one tick" on prediction markets | **Partly** | Novig game lines: median move from 2 h to the close is one step (0.5¢); only 32% move less than a step (props 28%) | Late bets still see moves; nothing to change |
| 13 | Resting Make orders sit in escrow; editable | **Wrong for the API** | Tj's v0.53.0 file: $12.54 resting on an $8.32 wallet, balance unchanged; NOVIG_API.md §17: no amend (cancel + re-post) | NOVIG_API.md §17 now says so plainly |
| 14 | Market-make both sides near fair in liquid NFL sides/totals and collect the credit | **Wrong for pregame Novig** | No maker credit pregame (§17); game-line bids at Novig's own price earn ~0 per fill (§70.2, §72.3: +0.08% w=0) | Bids stay props/period/team totals first; game lines only with a sharp book + move rule |
| 15 | Toxicity guard: pull quotes when fills skew one-sided | **Only on game lines** | §72.3: props/period bids unhurt by flow; game lines hurt | Built for game lines (v0.56.0) |
| 16 | Never risk >1.5-2% of bankroll on one event | Practitioner advice (untested here) | Walters 1-3% (§69.1) | Per-bet: ¼ Kelly + Tj's per-bet cap. **Per game: no cap today** (several props in one game add up) → proposed |
| 17 | News windows (no bets 30-60 min after unresolved news) | Untested | Kickoff times aren't in Novig's files, so the windows can't be resolved | The 6 h guard and freshness (5-10 min) stand; nothing built |
| 18 | CLV unreliable in props (no market-making books) | Plausible, partly | Tj's props: under 6 h +2.5% CLV and settled bets under 6 h +8.3% ROI (§71.2): CLV and results agreed | Keep judging props by CLV, with more bets |
| 19 | De-vig with Shin/odds-ratio/log, not equal margin | Accurate | — | Already: power (≈ log) for Vigilant's fair, worst case of four for CNO's checks |
| 20 | Quarter Kelly ≈ 44% of full growth, half ≈ 75% | Accurate (f(2−f)) | — | ¼ Kelly default, capped at the sharp fair (v0.56.0) |

Re-run: `python3 tools/research/novig_drift_study.py --cache DIR` (~3 min once cached), `sharp_anchor_study.py --dir DIR` (section F),
`tj_bets_by_lead.py <diagnostics file>` (new splits: shown edge, Novig over the best book).

**§73 addendum: does Novig pay a maker credit pregame? (2026-10-03 ~18:15Z; Tj: "Reconsider whether novig pays maker credit pregame")** No, for every market
Vigilant trades. Four independent checks: (1) the Maker Credit Program terms §2: "Trading fees assessed at any other time — including before the event
begins — are not Live Trading fees and do not generate Maker Credits"; a qualifying trade is one "matched (filled) while the underlying event is in
progress (in-game)"; (2) the Trading Fees page: a straight-contract fill "matched at any other time is not charged, on either side, and generates no
Maker Credit"; (3) Novig's live catalog, read now: every NFL, NCAAF and MLB game market (moneylines, spreads, totals, player props, 1st halves, team
totals) carries `fee {coefficient 0.03, makerCredit 0.5, charged WHEN_LIVE}`: the credit is half of a taker fee that is zero pregame; (4) `GOLIVE`
voids every resting order (event-lifecycle and fees pages), so a bid posted pregame can never be filled in-game. The only markets with a credit
outside live play are futures: NFL and NCAAF futures by the Program's Notice (taker 0.06·P(1−P) on every fill, maker credit 70% of it = 0.042·P(1−P)
a contract, ~2.1% of the cost at even money, paid within 7 days), and MLB awards futures carry `{0.06, 0.7, ALWAYS}` in the catalog though the Notice
names only NFL and NCAAF. Vigilant trades no futures (BRIEF.md: left out of the app). So Vigilant's maker math is right as built: a pregame bid earns its
price edge under the fair and nothing else (`MakerQuote.stake`, EV = fair ÷ price − 1, no fee, no credit). Two places a credit could be earned, neither
built and neither recommended without research: in-game bids (0.015·P(1−P) a contract, ~0.75% of the cost at even money, against in-game adverse
selection from faster feeds) and NFL/NCAAF futures bids (needs a futures fair Vigilant doesn't read; book futures carry 15-30% hold; money tied up
for a season).


## 74. Why the app locked up with auto-bid on and Pause took long, and why no bid has filled (v0.56.2, 2026-10-03; Tj, with the v0.56.1 Diagnostics: "The app was running on auto bid and it got so laggy I almost couldn't use it and I pressed pause and even that took a while to register. The bids are still not getting filled, how long do they usually take to get filled?")

### 74.1 What the file says
- **Android ended the app**: 15:52:05, "not responding": input dispatch timed out after 10 s, the **main thread** in `BetGrader.totalWording` ← `pickOf` ←
  `PlacedIndex.pickKey` ← `PlacedIndex.has` (a java.util.regex matcher, `Runnable`, not blocked on a lock: pure CPU on the UI thread).
- **The timeline before it**: 15:42:41 Tj switched scanning on (background auto-scan BOTH, paused → running). The 342-s Vigilant scan ended 15:48:45. At
  **15:51:09-15:51:52 the scanner was switched CNO↔BOTH eight times**, auto-make switched off 15:51:38, **Pause 15:51:50**, ANR 15:52:05. The phone was
  on Battery Saver (slower CPU).
- **Bids**: wallet $12.60 (≈ $1.24 a bid → 9-13 resting at once); **151-451 wanted bids waiting** on the wallet every pass; 0 filled of 320 on record.
  Bids rest 1 min at the median, 6 at the 90th over the 14 days (the file's numbers mix every version since v0.51.0).

### 74.2 The causes (main-thread work, and what made Pause wait)
1. **The feed was built on the main thread, inside `_state.update { ... }`**, at every scanner switch, Pause, scan end, recheck, ✓ mark (`applySettings`,
   `applyReport`, `repriceNow`, `recheck`, the placed-marks collector). Building it = filter + sort of every priced side, then `PlacedIndex.has` per row, which
   read each row's matchup (`parseMatchup`: a regex) and wording (`BetGrader.pickOf`: a dozen regexes + a stat lookup) from scratch every time, for any game Tj
   has a bet on. ~0.1 ms a row on a desktop JVM, several times that on the phone on Battery Saver, × a feed of thousands of rows × eight switches queued ahead
   of the Pause, and `StateFlow.update` re-runs its lambda whenever another thread wins the swap. That is the ANR, and "pause took a while": Pause was ninth in
   line.
2. **`applySettings` waited for a re-pricing, and a re-pricing waits for a running scan**: `Scanner.reprice` takes the scanner's mutex, which `scan()` holds
   for all of its minutes. Every settings change (Pause's toast and `resumeThen`'s next step included) was held until the scan unwound.
3. **A pass kept posting after Pause**: `MakerDesk.cycle` sent every bid it had planned, one request after another, holding the lock that the Pause's
   cancel-all needs; the cancel-all then took each of those down again.
4. **The Bids tab re-worked its lists on every state** (filter + sort + group of the pass's thousands of decisions, several times per recomposition, three
   recompositions a second in a scan), on the main thread.

Not causes: the store writes (a whole-list JSON write + fsync per update, but on `Dispatchers.IO`: ~20 a pass, tens of ms each, no main-thread work),
the maker pass's own maths (`MakerLines.from` prechecks before it devigs, §70.7), the request pacing (the 429s are the public routes' 1-s Retry-After).

### 74.3 What changed (v0.56.2)
- `BetGrader.pickOf` keeps each wording's answer (a bounded map, `unreadable` kept too), and `PlacedIndex` keeps each matchup's and wording's key: a row is
  now two hash lookups. Every caller of the wording readers gets it (sharp veto, injury tags, maker lines, parlay compare, the CNO list's `hasCno`).
- The feed is built **off the main thread** (`FeedBuild.kt`: `publishResult`, `refeed`, `reindex`): built on `Dispatchers.Default` from a snapshot, swapped
  in only if the result, settings and marks it was built from are still current (else built again). No screen update builds a feed itself (a source pin).
- `applySettings` swaps the new settings in first (Pause shows at once), stops the scan, shows the feed under the new settings, and starts the re-pricing as
  its own latest-wins job (eight quick switches = one re-pricing, after the scan) that nothing waits for.
- A pass asks before each new bid whether scanning is still running and auto-make still on (`MakerDesk.cycle(keepPosting)`), so Pause stops posting at the
  next bid and the cancel-all gets the lock.
- The Bids tab works its lists out once per change of bids/decisions (`MakerLists`, found by list identity).
- Diagnostics: **last 24 h of bids in bid-hours** against the fills §70.3 expects from lives like those (`MakerStats.recent`), so the next file says
  whether zero fills is bad luck or a real shortfall.

### 74.4 How long does a bid take to fill? (the answer for Tj)
From §70.3 (60 days of Novig's trades, prop bids 4% under the fair): a bid left up **15 min fills 4% of the time, 1 h 11%, 3 h 25%, 6 h 37%**, and a side
re-quoted from 24 h out fills **40-46%** of the time. So a bid is not a minutes-long wait: **when a bid fills it is usually hours in**, and more than half
never fill. What decides it is bid-hours up at a competitive price, in the hours takers trade (afternoon and evening), not how many bids were posted.

This file's window: auto-bid actually ran for **~9 minutes** (15:42:41-15:51:38) with 9-13 bids up on a $12.60 wallet ≈ 1.5-2 bid-hours, i.e. **≈ 0.1-0.2
fills expected**. Zero is the expected result, not a defect. (The 320 bids on record are mostly the v0.51-v0.53 one-minute bids, §70.8-§70.9.) What
limits fills in the app, none a bug, all Tj's call: **(1)** a bid never outlives its fair's freshness (5 min under 3 h from the start, 10 min past it,
§70.7), by design ("it should not keep make orders long enough that they lose their positive EV"), and a re-post goes to the back of the queue; **(2)** the
wallet: ~$12 holds ~10 bids while 150-450 wanted bids wait; **(3)** the scan runs only while Tj has it on (the fairs behind bids must be fresh).

## 75. The scan study: every listed bet, graded, for Claude to find what beats the close (v0.57.0, 2026-10-03; Tj: "on every cno scan, the vigilant app saves logs on all kinds of information … The new feature will be a button in the settings in the diagnosis section that can output a file to Claude … contains comprehensive info about all scanned bets, and is constantly updated. When those bets are final, it logs whether they won or lost or pushed and their closing line odds. The file will prompt Claude to do deep analysis on all of the bets and find profitable patterns … utilizes already available features in the app, such as the function in the app that already grades results and closing odds … efficient and doesn't interrupt or break any other part of the app")

### 75.1 Why a log of everything listed, not only the bets Tj placed
Every study so far (§65-§73) rests on Tj's own bets (a few hundred, chosen by whatever he or the auto-bet took) or on Novig's trades (no view of what the scanners listed). A bet the scanners
listed and nobody took has no close and no result in the Tracker, so "which listed bets beat the close?" was unanswerable, and any rule found on placed bets carries the selection of who placed
them. The scan study logs the whole population: ~50-100 bets per CNO read, thousands a day, each with its closing line and result once its game is over.

### 75.2 What is logged (and where it comes from, nothing recomputed)
- **At the first look** (`AtBets.cno` / `AtBets.opportunity`, the same record a placed bet gets, Tj's 2026-10-02 request): league, sport, kind, minutes to the start, Novig's price (CNO's, or Novig's own
  live price when read in the last minute), EV, fair, CNO's book count and one-way flag, list age, dollars available; when the green check had read its page: companies pricing both sides, how many
  agree, the check's EV, every book's odds/fair/EV, the dissenting books, the sharp veto's verdict (`checkAtMs` says when: a page is read for the top ~10, so many bets have no check).
- **Why the app's own screen would have hidden it** (`CnoChecks.rejection`): NOT_A_GAME, MISMATCH, ONE_WAY, BOOKS, ODDS, TOO_GOOD, EV: tells whether the app's checks help or hurt.
- **Looks** (`Sight`): price, EV, fair, books, dollars on each change (≥ 0.25 pt EV or any price/books move, at most 1 a minute), every 5 minutes while listed, each page read's agreement counts, and when
  a scan drops the bet ("gone": the edge fell under the filters, or CNO's row limit pushed it out). A bet both scanners list is one record with looks from both and each scanner's own record.
- **After the game**: result (`BetSettler`: ESPN / MLB scores and box scores), the close (`CloseBackfill`: Pinnacle via ParlayAPI, ESPN's DraftKings line, Novig's last trades), and CLV at the first-listed
  price, at the best price it was ever listed at, and at the last. A bet Tj also placed takes its result, its pre-start close and Novig's close from his Tracker bet.

### 75.3 Why it cannot slow or break the app
- **No request of its own while scanning**: it reads the CNO list, the pages the green check read, Novig's live prices and Vigilant's result, all already in memory (source-pinned in `ScanStudyTest`).
- **Append-only journals**, one per game day: a scan adds a few lines in one write every 10 s; nothing is rewritten (the Tracker's file is one document rewritten whole on every save: right for hundreds of
  bets, wrong for tens of thousands). Measured: 240 scans of 100 rows with every price flickering = 0.56 s of CPU and < 6 MB (`a two-hour evening …`).
- **Background priority** (`scanScope`, the scan's own threads) and a catch around every step (`studyStep`): a failure is a line in Recent problems. A kill switch in Settings.
- **Grading reuses the Tracker's code** on a scratch Tracker file (deleted after), beside the 3-hourly `SettleWorker`; ParlayAPI's closes (credits) only while ≥ 40% of the month's credits are left.
- **The file streams**: one day folded at a time, ≤ 24 MB of journal per file (older days stay on the phone and say so).

### 75.4 What the first files will and won't say
Selection: only what CNO's filters and row limit listed (Tj's: conservative devig, 4+ books, ≤ +150, ≥ 1%, 100 rows) and what Vigilant's feed listed. ROI is at the first-listed price with a unit: Novig had only
`available` dollars there, so size matters. Closes differ by source (compare CLV by `closeVia` before pooling). Same-game bets are correlated: count games. A group needs ~200+ bets with a close before its CLV
says much (§65-§66). The READ ME in the file tells Claude all of this and the task: the checks, the splits, timing, traps, candidate rules measured out of sample by date, and the deliverables.


## 76. The wide read: every row CNO finds, including the ones Tj's filters hide (v0.58.0, 2026-10-03; Tj: "make it include cno scanned bets that are filtered out of showing up in the vigilant list. In other words, log all cno finds on every scan with all the information for each bet cno shows even if these bets don't meet my criteria for showing up in the list in the app. They should still be hidden in the app but logged into the scan study file. The more information the better")

### 76.1 What the study saw before, and what it lost
The app reads CNO with Tj's filters posted into CNO's own form (§19: the form is the filter): devig method, minimum EV, longest odds, fewest books, "Require a Complete Sportsbook", minimum market sides,
result count, and any stricter value the Shared View link set. CNO drops what those exclude **before** the app sees a row, so the v0.57.0 study could only log what CNO sent (and flag what the app's own
screen, `CnoChecks`, then hid: NOT_A_GAME, MISMATCH, ONE_WAY, BOOKS, ODDS, TOO_GOOD, EV). Never seen: rows under the EV floor, under the book count, over the odds cap, one-sided or not on a complete
sportsbook, and everything past the row limit.

### 76.2 The design: a second session with the filters opened, kept apart from the list
- **`CnoClient.fetchWide`** loads the same view in a **session of its own** (own cookies, own form fields, own view state) and posts: Tj's devig method (EV means the same thing), `TextBoxMaximumOdds` and
  `TextBoxMinimumOdds` blank, `TextBoxMinimumOddsProviderCount` 1, `TextBoxMinimumEVPercentage` 0%, `TextBoxMinimumSubMarketSideCount` 1, `TextBoxMaximumResultCount` 1000, the complete-sportsbook box
  unticked. What the Shared View link scopes (book, sport, league, main lines, live, and any filter the app has no name for) is **kept**. Because the session is separate, nothing the list posts
  can change (`CnoWideTest`: a wide read between two list reads leaves the list's second read one postback with its own cookie, view state and filter values).
- **`CnoPage.table(keepColumns = true)`** keeps every column CNO printed for the row by its header (and every `data-*` attribute of the row) as `CnoRow.cols`: a column CNO adds is logged without the app
  knowing it. The list's own reads keep none (rows compare equal; the disk copy isn't rewritten every read).
- **`CnoFeed.readWide`** (own mutex, own `wide` state): never faster than every 30 s (CNO's robots.txt asks 30 s between pages), only after a list read and only when CNO's odds have moved since the last wide
  read, never while the list is failing or CNO asked for a pause (and a pause CNO asks for during it pauses every lane); a failure waits 30 s × 2^n up to 10 minutes; a refusal (an error, or CNO's red
  message and no table) asks for fewer rows next time (1000 → 500 → 200). The list's `state`, its disk copy, alerts, auto-bet, widget and mini window never read it (source-pinned in `ScanStudyAppTest`).
  Cost: one ~0.8 MB reply every 30 s while the CNO list is being read, one paced request among the shared pace's one-a-second.
- **The study** (`ScanStudy.observeCnoWide`): each wide row is a `Sight.WIDE` ("w") look (and "xw" when a read with room no longer has it; a read with as many rows as it asked for calls nothing gone). A bet
  the app's list carries too is one bet with looks of both kinds (`src` "c+w"); one only the wide read finds is "w" and has no app-list life (`listedMin`, `gone` are the app's lists' only). The bet's
  **`screen`** at its first look: the app's own reason (`CnoChecks.rejection` under Tj's filters), or **NOT_LISTED** (the screen passes it, the list's CNO read under his filters didn't carry it: the
  complete-book or sides rule, the row limit, a link filter), null = shown. NOT_LISTED is only judged when the app's newest list read is the same view and filters and within a minute. `cols` goes on its own
  line once per bet. A futures bet is logged but never graded (no feed has a result). Grading takes at most 1,500 bets of a day per pass, the app's list's bets first.
- **Cost on the phone**: `hydrate` (after a restart) streams the journal a line at a time instead of folding days; grading and the counts fold without the looks; the journal's `rules` string isn't copied onto
  every bet. Measured (`a two-hour evening of 800-row wide reads …`): 240 reads of 800 rows with every price flickering and 14 columns each = 1.8 s of CPU, 12 MB of journal (worst case; most rows
  don't move every read). The file: bets' lines stop at 24 MB (the app-hidden ones take at most 65% of it) and every bet is still in the summary, which now sums the shown and the hidden apart.

### 76.3 Not verified live (this container never reads CNO: its terms bar automated reads, Tj's own phone does)
- Whether CNO accepts a blank odds field, a minimum of 1 side, and a result count of 1000 (200 was accepted in §19). If it answers with its red message the wide read steps the row count down and says so in
  Diagnostics ("Scan study's wide CNO read: … last problem: …"); a refusal of the blank/1 values would show the same way. The file's READ ME prints the form as posted (`CnoSnapshot.asked`), every filter
  field the page has by name, so the first file tells which link filters (a liquidity floor, hours to the start) still apply and can be opened next.
- How many rows a read holds, how often "AS MANY AS ASKED FOR" appears, and the journal's real growth per day: Diagnostics' line (rows read, how many were also in the app's list, reads since launch).

### 76.4 The props split (v0.58.1, 2026-10-03; Tj, after §65/§66.2/§72.6 were put to him again: "consider if it is needed or smart to require that prop bets have at least one sharp prop book that agrees … Do option 1 and add the props split to the study")
Nothing about the app's rules changed (option 1: leave them, let the data decide). What is true today: a prop's EV and fair on the CNO list are CNO's consensus of 4+ books, mostly soft; the sharp veto
(`SharpVeto`: Kalshi, ProphetX, then FanDuel and Caesars, DraftKings and FanDuel on MLB) touches only auto-bet, alerts and bids, only after the bet's page was read, and only when the first ranked book that prices both
sides says no; with none of them on the page (`NO_SHARP`) the prop goes through. In Tj's 43 recorded prop bets (v0.56.1 file) 24 had Kalshi/ProphetX pricing both sides, 5 one side, 14 neither; several passed on
Caesars or FanDuel alone; 4 of the 57 recorded bets had a close, so his own data can't say whether a sharp-confirmed prop beats the close more.
The study file now answers it (`StudyExport`): three PROPS splits (what the sharp-ranked book said: an exchange agrees / says no, an originating book agrees / says no, nobody ranked prices both sides, no page read;
the sharp book's own edge band; whether Kalshi/ProphetX are on the bet's page at all) and a WHAT IF section: for each of three rules (today's veto; a sharp-ranked book must agree; an exchange must agree) the props it would
keep, drop and not be able to judge, each with W-L-P, ROI and CLV; the READ ME asks Claude the question (item 7) and the caveats say the sample is the props whose page was read.
**A bug the test caught before it shipped:** a study bet logged with no page is judged against no books, and `SharpVeto` says `NO_SHARP` for that: counted as "nobody ranked prices both sides" it would have made every
unchecked prop look kept by the veto and dropped by the requirement. A prop only has a verdict when its page was read (`checkAtMs`, `twoSided` or `books` on its record); the rest are "not judged".
Limits: a verdict exists only for bets whose game page the green check read (the top ~10 of the list, again every 4 minutes) and the first read is the one used, so the judged props are the best-EV rows; wide-read-only
props have none. If the first files show the judged sample is too thin, the next step is reading more pages for props (CNO requests: Tj's call).

## 77. Is the study logged in the background auto-scan? (v0.58.2, 2026-10-03; Tj: "Confirm that all the betting data is being logged even when the app is backgrounded but in auto scan background mode.")

### 77.1 What was true, traced through a background cycle
- **The watchers live in `AppContainer`** (built when a process starts for the service or an alarm, with no Activity): CNO list, wide read, wide rows, book pages and Vigilant scan each log from what a scan already produced, on `scanScope`. They run in the background.
- **Keep awake on (cycles under 9 minutes apart, the default)**: the service holds a partial wake lock the whole time, so the watchers run as results land and the 30 s flush loop ticks. Everything was logged.
- **Alarm only (keep awake off, or cycles 9+ minutes apart)**: the wake lock is held only while `AutoScanner.cycle` runs and let go the moment it returns (`releaseAfterScan`). The study's work was **not part of the cycle**: the wide read (a network
  call a watcher starts after the list read), the checks of the last book pages (the books watcher waits 5 s between looks), the wide rows' log and the write to disk came after, with the CPU free to sleep. Android could freeze the process
  there until the next alarm (9+ minutes on). By then the list that cycle read was older than `MAX_SCAN_AGE_MS` (90 s, which keeps the list saved on disk at launch from being logged as a scan), so it was **dropped as "saved"**, and
  the page checks (older than `VIEW_FRESH_MS`) with it; lines still in memory when Android ended the process were lost. A Vigilant scan that outlived the cycle had the same race at its end.
- **Also true in every mode**: `cycle` skips entirely while Check odds now holds the focus (no CNO read, so nothing to log: the study adds no read of its own to make up for it); grading and closes run in `SettleWorker` every 3 h and
  at launch, background-safe (WorkManager, network required).

### 77.2 What was built
`StudySync` (app/): the watchers' steps (list, wide read, wide rows, books, Vigilant) in one place, and `catchUp(cycleStartMs)`: the list read at or after the cycle's start counts as that cycle's scan whatever its age by now, the wide read after it,
the wide rows, the book pages, a finished Vigilant scan, then `ScanStudy.flush()`. `AutoScanner.cycle` calls it in its `NonCancellable` finish (bounded, `STUDY_SYNC_MS` 25 s) while the cycle still holds the mutex and the service its wake lock; the
service's `scanHold` calls it when a Vigilant scan the cycle started ends, before the wake lock goes. Every step is idempotent (`ScanStudy` keeps what it last saw of each scan), so the watchers and the catch-up never double-log; a read
dropped as old is not remembered as seen, so a late watcher can't stop the catch-up logging it. A list saved from before the cycle (the scanner is Vigilant's, or the read failed) is still not a scan, and makes no wide read.
Cost: up to one wide read (≤ every 30 s, only when CNO's odds moved) at the end of a cycle, and one write.

### 77.3 Not verified on the phone
That Android really sleeps the CPU between alarm-only cycles with the process alive is Android's documented behavior, not measured here. Diagnostics' "Scan study" line (bets logged since the app opened, last logged, last problem) and the cycle log
(screen off / dozing per cycle) show on the phone whether a background run logged: a 10-minute auto-scan with the screen off should add bets every cycle.

## 78. The wallet kept ahead of the bids, and why Novig scans run fast or slow (v0.58.3, 2026-10-04; Tj, with a screenshot "Vigilant wallet $8.98 · 7 bids up ($16.14)": "My wallet has less than open bids money. I think this is because I was betting manually and auto betting and the app doesn't constantly monitor how much money is in the wallet to make sure the open bids aren't more than available money"; then, with the v0.58.2 file: "A lot of times the vigilant scanner slows down significantly when it is scanning novig prices, maybe down to 2 per second. Other times it is very fast. Can this be diagnosed?")

### 78.1 Why bids exceeded the wallet (Tj's guess was right, and there was a second way in)
- **Bids were checked against the wallet once: when posted** (`MakerDesk.cycle`: budget = min(wallet, day's limit left) − every bid not yet ended). Novig holds nothing for a resting bid (§70.9, NOVIG_API.md §17), so any
  money that left afterwards (a bet from the Bet sheet, an auto-bet, a bid that filled, a transfer out) left more bids up than the wallet covers, and nothing took them down. The v0.58.2 file shows it live: "wallet $11.55 → $11.55
  with $18.71 resting" (21:41, one minute after auto-bet placed five bets at 21:40:29), "$7.42 with $16.14 resting" (21:45), the same $16.14 as the screenshot.
- **Approving a bid by hand (the Bids tab's Post, the notification's Approve) was held only to the whole wallet**, not the wallet beside the bids already up: two $5.00 bids went up on $9.00. It didn't look at the day's limit either.
- What Novig does when a bid fills over the wallet isn't known (not seen live). The fix's point is never to find out.

### 78.2 What was built
- **`MakerPlan.plan` trims.** The budget may be negative now (it was clamped at 0). The resting bids may add up to budget + their own dollars; over that, the ones that don't fit come down, ranked by worth: Tj's hand-approved bids
  last, then the ones that lead their side, the cheaper, the more EV (a bid that outranks another but doesn't fit comes down, and a lower one that does fit stays). A trimmed side isn't posted again that pass (not even a smaller bid).
  Bids already coming down (CANCELING) still count as up until Novig confirms them gone, as in every budget. The day's limit trims the same way ("resting bids may not push it over", the rule's own words). The reason on the bid,
  shown in the Bids tab and in "last 24 h ended": "The wallet (or today's limit for API bets) no longer covers it beside the other bids up: taken down". Works with auto-make off and on a running scan.
- **`MakerDesk.fit`**: settle (fills first) then the same trim with no lines to judge. Used by the no-scan pass (a restarted process or a background cycle before any scan: it used to read fills and expiries only) and by the watch.
  The balance is read before the settle, so a fill that lands between them can only make the count lower than the wallet (caught next look), never higher (no bid taken down for a fill counted twice).
- **The watch** (`AppContainer` init): `combine(wallet.flow, makerStore.flow)` → `MakerRunner.overWallet` (bids up > the reading by more than half a cent, with a resting bid to take down; no request) → `MakerRunner.fitToWallet`,
  at most one run per 5 s. Every balance reading anywhere feeds the flow (Bet sheet after a bet, auto-bet before each pass, a transfer's answer, the strip's 30 s read while the app is open, each background cycle), so a manual bet
  or an auto-bet is followed by the trim within seconds with the app open, and by the next cycle in the background.
- **Approve** (`MakerDesk.post(wallet, maxPerDay)`): the wallet and the day's limit beside the bids already up.
- **The strip** says "· over the wallet" in red while the resting bids are worth more than the wallet (the screenshot's state, until the trim lands).
- Proofs: MakerTest (+10: plan trim, order, partial/auto-make-off, desk cycle, replacement on a short wallet, day's limit, fit incl. settle-first and an unapplied cancel, Approve budget, hand-approved last), MakerAppTest (+5: the
  watch end to end, no trim when covered, the no-scan pass, Approve, wiring pins), WalletStripTest (+2 incl. the screenshot with Tj's numbers); 11 mutants of the trim/fit/post all killed (two needed stronger tests: the
  leader-over-cheaper-follower rank, and "not re-posted" with a smaller replacement that would fit).
- Not changed: `makerMaxDollars` / `makerMaxBids` lowered in Settings don't take bids down (they only gate new ones; a bid lives 30 minutes at most); a reading taken at the moment a fill lands can still trim one bid too many
  for a moment (the next pass posts it again if the line is still wanted).

### 78.3 "The scanner slows to 2 a second": what the v0.58.2 file shows
- **The 299 s scan is REST-bound, not broken:** 4,274 Novig prices in 295 s = 14.5 a second against the key's documented 16 a second; "Novig refused none". The live feed was asked for 2,000 at 4.1 s and held 315 by the end,
  so 3,959 prices came one request at a time. A scan with ~4,300 books to read can't beat ~270 s by REST. The median scan is 31 s (8 scans in the file): few books to read.
- **The 201 s scan** (21:41-21:44) read 4,359 prices, only 2,501 through the key: ~1,860 came from the feed or the public route, and the timeline has public 429s inside it (×21 at 21:42:48; ×140 at 21:31 in the scan before).
- **"2 a second" is the public route halved:** the public route starts at 4 a second (up to 6 on a clean run); a 429 halves it for a minute (4 → 2), and afterwards the pace climbs back only to a step under the refused pace for
  ten minutes (`RateGate`); repeated refusals go to 1 a second. The scan is on the public route when the key route stands down: Novig's network screen judges the phone's carrier address (`451 ANONYMIZED_NETWORK`:
  Recent problems 12:37 AM "This scan read Novig's public prices instead", 95 such 451s in the file, 7:57 PM and 8:44 PM today), and the key is tried again after 2 minutes. The carrier's address is shared, so the public edge's
  per-IP limit is spent by other people too: 1,952 public 429s in all (24% of the public route's 8,164 calls), only 100 failures of 58,376 keyed calls.
- **So the diagnosis is: fast = key route + live feed pushes; slow = either thousands of books by REST at ≤16 a second, or a stretch on the public route after a 451 (2-4 a second, less after 429s).** The file couldn't say
  which for a given scan (only the last scan's line, with no pace of the public route and no count of how much of the feed arrived).
- **Recorded now (no pace changed):** every scan's timeline line says its pace and ways (`· 14.5 a second (315 live feed, 270 public)`), the public route's pace when it carried reads (start, lowest after a refusal, end), the
  key route's pace and 429s, and how many of the markets the live feed was asked for it held at the end (`ScanTiming.pace`, `liveFeedHeld`, `ReadPace`, `RateGate.takeLowRate`). The next file will say, per scan, which of the two it was.
- **Open, not changed (Tj's call, a scan-plan change):** 4,274 Novig books were read to price 141 lines (214 sides with a fair price): props markets dominate the plan. And the feed holding 315 of 2,000 asked in 299 s
  is unexplained (no error was reported); the new "held H of N" says whether it's a pattern.

## 79. The first scan-study export, read (v0.58.3 file, 2026-10-03; Tj: "Here are early vigilant results to consider. Make any fixes if needed", with another Claude's analysis, `research/scan_study_analysis_2026-10-03_v0.58.3.md`)

### 79.1 What the analysis found, and what was checked against the file and the code
- **The data is one evening** (622 bets, 63 games, first looks 17:56-22:47 EDT Oct 3; 47 graded; 43 closes), so it can't carry day/hour/out-of-sample findings; **403 of the 622 are Sunday's NFL games, ungraded**. Its headline: results on expectation
  (25 W vs 24.2 expected), clean CLV **+2.2%** (41 closes, 23 games), no group separable from "everything", the props/sharp-book question **not answerable** (33 of 365 props had a book page read). It changes no
  rule (keep presets, the veto, the 1% EV floor, the +150 cap, 4+ books). CLAUDE.md: a rule the study suggests waits for Tj; none was suggested.
- **Verified in the file: its "2 bad Pinnacle closes".** Washington State ML (Fresno State @ Washington State; -117 at first look, -115 at the last, 110 min before the start, CNO fair 54%) "closed" at +272 (26.9%, CLV -50%): wrong, no
  game moves 27 points in two hours. **Arkansas State +186 → +216 (CLV -9.5%) is NOT shown wrong**: 3.3 points from CNO's last fair (35.0% vs Pinnacle 31.7%), inside the offset between a sharp book and CNO's conservative devig that
  every close shows (median gap 0.8 points, 90th percentile 2.2, the 41 other closes all within 2.8). The analysis called both "wrong side/game": one is.
- **Root cause of the Washington State close (reproduced failing-first: the old code gave 0.258, the file's 0.269):** `ParlayCloses` matched a closing-lines row to the bet by `TeamMatcher.similarity >= 0.5` for each team, and two names sharing only a
  school word ("State" is the token `st`) score exactly 0.5. Any other "X State @ Y State" game starting within 3 hours passed, and the code took the LATEST row among the passing games, not the best fit: another State game's home
  moneyline was returned as Washington State's close. Same family, found while fixing it: (a) in the event shape the bet's team was "the first outcome that shares a word" (a wrong-SIDE close for two State teams); (b) in the closes file a game of
  two State teams never got a close (both rows counted as the bet's team, the other side found none); (c) `ParlayBooks.viewOf` (an open bet's books from ParlayAPI) and `OtherBooks` used the same 0.5 bar. Not affected: the scan's planner (it
  demands 1.5 across the two teams), the grader (`BetGrader.gameOf`, 0.8 a team) and the Novig bet finder (0.8).

### 79.2 What was built (v0.58.4)
- `TeamMatcher.gameScore` (both teams at least 0.5 AND together at least 1.5: one team really matches, the planner's own bar) and `TeamMatcher.whichOf` (a team is the side of the game it fits BEST, none when two fit alike); `ParlayCloses` takes rows of ONE game
  (the best fit; the nearest start among equal ones, a doubleheader's own game; two different pairings that fit equally are not guessed between) and assigns sides by `whichOf`; `ParlayBooks` and `OtherBooks` use `gameScore`.
- **`ClosePlausibility`, a backstop whichever source is wrong** (ESPN, Novig's trades, ParlayAPI; `CloseBackfill` asks it of every found close): a close further from the bet's own fair price when bet than 10 points (within an hour of the start), 12 (6 h),
  20 (a day), 25 (beyond) is "never" from that source, with the numbers on the bet's close note, and the next source is asked. The bars are 3-4 times the largest real move in Tj's 230 closes by gap (2.6 points within an hour, 6.8 within 6 h,
  14.7 within a day: a player ruled out hours ahead, 10.4 beyond), so a real move, news included, is kept (dropping the largest real moves would flatter CLV).
- **The study export applies the same check to what the journal already holds** (it is append-only, so the Washington State line stays on disk): such a close is left out of every figure with its reason in `closeNote`.
- Log-only: the rules line now says what CNO's list is read with (edge ≥ 1%, odds up to +150, 4+ books, rows), where it said only devig, books and rows, and the file says which part decides which list (the analysis had to work out that the shown list's
  filters were 1% / +150 / 4+, not the line's "edge ≥ 3%, odds -200 to +120", which are the auto-bet's); a close copied from Tj's Tracker names whose fair line it is ("(CNO's books)", "(Vigilant's fair line)", "(CNO and Vigilant, averaged)").
- Proofs: ParlayClosesTest (+4: the file's wrong game reproduced and fixed, a doubleheader, two different pairings refused, State-vs-State rows, the event shape's side), TeamMatcherTest (+2), ParlayBooksTest (+1), HistoricalClosesTest (+3 and
  three fixtures given realistic fair prices), ScanStudyTest (+1, one extended); mutants in §79.4.

### 79.3 Not built (and why), for Tj
- **The level offset** (closes price these bets ~1.4 points above CNO's conservative-devig fair whatever the listed EV): the close under two or three devig methods needs each source's two raw sides carried through `CloseLookup` into the Tracker and the journal (a
  schema change). The file already has CNO's fair at every look (`s`), so a close can be compared with CNO's own last fair like for like; the gap in this file is median 0.8 points. Offered, not done.
- **An independent close for every listed bet** (the analysis' fix 3): the Tracker's closes are only bets Tj placed, and Novig's own last trades would cover the rest, but they publish the next morning (the file was made at 22:48 that evening) and 325 of the 622 bets (every wide-only
  row and most CNO-only ones) have no Novig market or outcome id, which the trade lookup needs. Finding the ids means Novig catalog requests before each game starts (public edge: the carrier address already draws 429s). **Tj's call**; the Sunday export will show how many
  closes Novig's trades give for the bets that do carry ids.
- **A book page for every prop at 1.5% EV or more** (fix 4): more CNO requests; it is what the sharp-book question needs (12% of props judged). **Tj's call.**
- Per-look listing reason and fill price: the look's kind (c/w) already says whether the app's list carried it, `placedAmerican` is the price Tj got, and a hidden look's reason follows from its EV, odds and books against the filters now printed on the rules line.
- The 4 `MISMATCH` rows: CNO's listed EV is a few hundredths of a point positive while its own fair odds give a slightly negative one (rounding of the odds CNO prints); flagged by design, none over 1% EV.


## 80. One game is one risk: the per-game exposure limit (v0.59.0, 2026-10-04; Tj: "auto bet placed bets on a team at +5, then the same team at +6, then the same team at +10 … Should there be some type of safeguard in the app that limits exposure to each game because if that one team loses badly, I lose many bets due to one event … figure out how to make it without incorrectly blocking bets on different games")

### 80.1 What was wrong (read from the code, BW1)
- Every limit was per bet (`BetLimits.maxStake`), per day (`spentToday`), per Novig MARKET (`AutoBettor`'s `openMarkets`, the placer's same-outcome check) or per bid side. A game's alternate spreads and totals and its props are separate Novig markets of ONE event, so +5, +6 and +10 on the same
  team each passed every check, and so would a moneyline, a spread and a team total. Those bets are one event: they win and lose together, so their dollars are one exposure, however many lines it was cut into. This is the standard bankroll rule for correlated bets
  (size the event, not each line), and the reason Kelly sizing assumes independent bets: three bets that are one bet at three thresholds are sized three times too large.
- No placed bet or resting bid carried the game's identity: `TrackedBet` kept `marketId`, `outcomeId`, `eventName`, `startsTs` but not Novig's `eventId`.

### 80.2 What was built
- **The unit is the Novig event** (`GameExposure.sameGame`, `TrackedBet.eventId`, set when the order is logged, for maker fills and for Vigilant's own bets). Two different ids are two games whatever their names and times say (a doubleheader's two games, the same teams a day later, two games starting
  at the same time). A bet saved before this version (no id) matches by its matchup and start the way `PlacedIndex` hides placed bets (12 h; baseball 2 h), and a matchup that can't be read matches nothing: the guard never blocks on a guess.
- **Exposure** = dollars of open bets plus the unfilled dollars of resting bids (a filled part is already a bet). Per Novig market only the LARGER side counts (the other side can't also lose, which is why a two-sided bid or a hedge isn't double-counted); a market held on both sides
  equally (locked in) counts nothing; a lock bet is never exposure of its own. The game's exposure is the markets added up.
- **The limit** `apiMaxPerGame` (Settings › Betting & Novig account › Most at risk on one game; chips $5 / $10 / $25 / $50 / $100 / No limit, or any amount typed: dollars and cents up to $10,000, saved as it is typed, the last good limit standing while the text isn't one; v0.59.1, Tj: "add $5 and a manual entry"), **default $25 = 2.5x the default per-bet maximum** (2.5% of the default $1,000 bankroll): two or three full bets fit on a game, the third line of the same team does not.
  **The default is a judgment, not fitted to Tj's data** (his diagnostics file with the EVERY BET lines was not in the session that built this: BW2 is open until he sends it again); the design doesn't depend on it, only the default does.
- **Who obeys it:** the auto-bet (a pre-filter in `AutoBettor` that reads no book, and the placer's own check on its fresh read of the Tracker, which also sees a bet placed meanwhile; both count under the one skip reason `AutoBet.GAME_LIMIT_SKIP`, the game and the dollars in the log) and auto-make
  (`MakerPlan.plan`: open bets and the bids kept from the last pass count; waiting reason `GAME_REACHED`). Recommendations stop suggesting a bid past it (the side isn't marked, so it's suggested later when the game has room). **A bet by hand is only warned** (a line on the Bet sheet's plan:
  Tj, 2026-10-02: he decides what he bets by hand), a bid he approves is his call, and a **lock is never blocked**: neither is any bet that adds no risk to a game (the other side of a market already held), even when the game is over the limit.

### 80.3 Proofs
GameExposureTest (16: +5/+6/+10 stack and the third goes over, a smaller third fits, another event never counted, legacy bets by matchup and start, a doubleheader, two games starting together, a lock that cost more than its pick, settled bets, the larger side, blank markets, zero = no limit,
exactly the cap, the words), ApiBettingTest (+6: planner refuses auto / warns by hand, placer with open bets, with resting bids, another event with the same names, a hand bet placed with the warning, an old bet by matchup), AutoBettorTest (+6: skipped under one reason with no book read, a hold the placer makes on its own read, room, other
games, bids, 0), MakerTest (+6), MakerAppTest (+1), ApiBettingUiTest (+1). 32 mutants, all killed (one needed a stronger test: a lock costing more than its pick hid behind the larger-side rule).

### 80.4 Not done, for Tj
- **Tune the default on his bets**: send the diagnostics file again (the EVERY BET lines) and the clusters in it (same side of one game at different lines, and whether they won or lost together) say whether $25 is right.
- **Correlation inside a game is not modelled**: Over 47 and Under 52 (a middle) or a team's moneyline and the other team's spread are partly hedged, and are counted as added exposure (conservative). A net-worst-case across markets would need each market's payoff, which is not on a bet today.
- **Bets placed in the Novig app itself** are known only if marked ✓ in Vigilant; Novig's positions carry no event id.

## 81. Why the Novig scan slows to 2 a second, the v0.59.1 files read, the bids, and the per-game button (v0.60.0, 2026-10-04; Tj, with the v0.59.1 diagnostics file and scan study: "novig scanning is going extremely slow. Maybe 1 per 2 seconds … Review the diagnosis and scan study for app improvements and EV scanning and logic improvements, but keep in mind the sample size is still relatively low. Also ensure that the study and diagnostic makes sense and it isn't feeding you illogical data … consider lowering the EV to 3.5 or 3.25% … more popular than obscure players props … Maybe a sharp book should be required … make a quick button next to each bet in the scanners … '$21 bet in this event, press for details'")

### 81.1 The slow scan: ONE 404 took the whole key route down for ten minutes (found in the code, matched to the file's timestamps)
- **The mechanism** (`NovigPublicClient.books`, before v0.60.0): a book read through the key that came back with any status other than 429 was thrown as a `NovigApiException`, and the handler read every such exception as Novig's verdict on the KEY: `keyedDownUntil = now + keyRetryAfter(e)`, which is
  ten minutes for anything that isn't a 451 network refusal (2 min) or no connection (30 s). A **404** is not a verdict on the key: it is one market that no longer exists (its game kicked off, or the market closed while the scan was still reading the plan). So one closed market sent the rest of that scan, and every
  Novig read for the next TEN minutes, to the public routes: 4 a second to start, halved to 2 by the 429s of a carrier address other people share (the note in §78.3), and a 429 pause of a second on top. That is "1 per 2 seconds", and it is why "other times it is very fast".
- **The file's evidence**: the key route's per-route failures are `/v3/catalog/markets/{id}/…` 135 of 87,062 (451×52, **404×13**, dns×7, cancelled×2): thirteen 404s over three days, each starting up to a ten-minute stretch on the public route. The timeline has two of them inside the stretch Tj looked at: **13:12:11** two keyed 404s (`/v3/catalog/markets/{id}/…`),
  then public 404s and 429s ×20, ×38 until 13:17; **16:01:07-08** two keyed 404s, then public 404s ×3 and 429s, the board itself read through `/v3/public/catalog/markets` and `/events` with 429s at 16:01:26-42 (the key's catalog was also off), 645 `api.novig.com` calls in the six minutes up to the file (1.8 a second against the key's 16) and none of the key's own 429s.
  Both came at kickoffs (the 4:05 and 4:25 games: 16:01 is four minutes before the first of them); 1:00 pm games the same at 13:12. A Sunday is a day of kickoffs: the scan is slowest exactly when games are starting and Tj is watching.
- **What else the file shows about those minutes**: the file has **no scan record** for them ("No scan since the app opened": Tj paused scanning at 16:05:44 while the scan was reading, and a scan that doesn't finish writes none: the next file needs one, see 81.2); a background cycle took 118 s and 71 s (`cycle.step.closing` p95 112 s: the Tracker's closing capture runs BEFORE the cycle starts the Vigilant scan, so in "CNO + Vigilant" mode the scan began up to two minutes late); ParlayAPI's NFL props answered in 8-18 s, timed out at 20 s ×3 and 503'd ×2 inside the same stretch (the props lines enter the plan as that answer arrives); the phone was on **Battery Saver**. Those are real but second-order: with the key route standing down there was nothing else needed to explain 2 a second.
- **The fix** (`NovigPublicClient`): a 404 on a book (key or public route) is `BookFetch.Gone`: not a failure, no banner, not a verdict on the key, **and its cached book is dropped** (a closed market's last price is not a price; before, a failed read served the cached copy as current). One server error (5xx) fails only that book; six in a row stand the key down for 30 s, not 10 minutes
  (`GEOLOCATION_SCREENING_UNAVAILABLE` and every other code still stand it down as before: they are about the key or the account). 200 404s in a row with no read between are taken for a dead route and stand it down like any refusal (a game's every market closing together is dozens, not hundreds). And the scan no longer reads a game that kicked off since the plan was made
  (`Scanner.BookPump.run`: a started game has no pregame book). Every stand-down of the key route is now kept (`NovigPublicClient.keyStanddowns`: when, for how long, why) and written to Diagnostics and the event log.
- **Not changed (and why)**: the key's 16 a second (the one bulk path is the websocket, §63.3, still holding few of the markets it is asked for); the Planner's budget (Tj's "no limit" reads every line a book quotes: the old §78.3 note stands); the 5-10 minute fair-odds age limit; the closing step's place in the cycle (a minute's change of start in "both" mode; the scan was started by the 404 in any case).
- **Proofs**: `NovigPublicClientTest` +6 against a mock Novig (a 404 on one market leaves the key route up and nothing goes public; a closed market is not served from the cache; the public 404 the same; a single 5xx vs a run; a dead route; every stand-down kept), 9 mutants.

### 81.2 The two files: what makes sense and what does not (checked before trusting any split)
- **Pooled CLV hides the close source.** Of the 383 closes in Tj's bets, 182 are "read before the start" (the Tracker's own last read, a median of **50 seconds** before the kickoff: a real close), 176 "Novig's last trades", 14 ESPN, 11 Pinnacle through ParlayAPI. CNO's bets by source: read-before-start **+1.12%** (175, 69% beat), Novig's trades **−0.35%** (120, 51%), ESPN +3.4% (12), Pinnacle +3.0% (9);
  Vigilant's: Novig's trades **−1.6%** (49, 39%), the other sources 2 each. So "Vigilant's edges lose to the close" (the FAIL) is mostly Vigilant's NCAAF game lines against Novig's own last trades, and "CNO's edges beat it" is mostly props against the Tracker's last read: two different yardsticks. A pooled CLV is not a number.
- **The study's closes are not a sample of the bets it lists.** 156 of 1,189 bets have one; **88 of the 156 are bets Tj placed** (their Tracker read), 44 ESPN's game lines; props are 802 of 1,189 bets and have a handful (Pinnacle's prop closes are in ParlayAPI's file for few of them; "ESPN keeps full-game lines only" ×235). The graded 376 are one Saturday's NCAAF/NHL: one day, correlated games. Nothing in "ALL BETS" is a rate until
  the NFL games of Oct 4-5 are graded (~400 bets). Every ROI in the splits is on a unit stake at the first-listed price, not at what Tj could take.
- **Novig-trade closes of 1-2 trades are noise** (§10's file: a trade is one TAKER row and a MAKER row on the other side; the app averages every row for the outcome): CLV by the number of trades behind the close, Tj's bets: 1-2 trades **−2.3%** (36, 39% beat); 3-5 +0.9% (37); 6-10 0.0% (32); 11-30 −1.1% (42); 31+ −1.5% (23). The study's summary printed "Novig's last trades (1) 6, … (3) 2, … (62) 1" as sixteen sources, so nobody could pool them. **Fixed in the log**: the study names one source ("Novig's last trades") and has a split by the trades behind it.
- **Numbers that agree with each other**: EV at bet +3.0% against CLV +0.3% (the shown edge is about 10% real on the pooled yardstick; +2.2 to 2.6 points overstated); results −6.75 against +12.25 expected over 339 graded bets with an EV (−0.8 σ: nothing to conclude); "profit +6.06" and "−6.75" are different sets (the first includes the 66 bets with no EV on record: imported ✓ marks), consistent.
- **The unit of the sample is the game.** Tj's 612 bets are in 153 games; over the 39 games with 3+ graded bets, the standardized game results have a variance of **1.51** (1.0 = independent bets): bets in one game do win and lose together (§80's premise, now measured). 59 clusters of one player or team and direction at two or more lines hold 130 of the bets (21%): the +5/+6/+10 ladder.
- **Real exposure (BW2)**: 20 of 153 games have over $10 on them, 3 over $25: Steelers @ Browns **$35.20 in 30 bets** (13-17, −$5.56), Eagles @ Bears $23.00 in 23, Jets @ Bears $19.60, the Sunday NFL games now $15-26 each in 12-20 open bets. All of the over-$25 are bets by hand (the guard only warns by hand): the median bet is $1.00, the games are big because there are 15-30 of them.

### 81.3 What the two files say about the rules (all of it thin: say "the data suggests")
- **Time to the start is the best-supported finding** (Tj's own bets with a close, CLV and share beating it): 24 h+ −0.7% (176, 55%); 6-24 h 0.0% (109, 51%); 2-6 h +2.4% (38, 74%); 30 min-2 h +2.5% (22, 77%); under 30 min +3.9% (30, 90%). The same shape as §71 (the trap guard's 6 h) and monotone; a bet placed a day out is a bet on a stale fair. Nothing to change (auto-bet, alerts and bids already stop at 6 h); the lists still show the early ones with a note.
- **The rules added since v0.45.0 look better than the bets before them**: bets with a record as placed (v0.45.0+) +2 to +3% CLV (43 auto: +2.1%, 77%; 34 by hand: +3.3%, 85%), the 458 before them −0.2% (55%). Sharp veto PASSED +3.5% (62 closes, 85% beat), NO_SHARP −1.9% (12, 50%), VETOED +3.6% (2: nothing gets through to measure). The "NO_SHARP is worse" gap is 5.4 points on 12 closes (about 1.5 standard errors), and the study's WHAT IF
  on props (which props a required sharp book would drop) points the other way on 6 closes (dropped +4.58% vs kept +2.38%): **not a basis for a rule**; see 81.4.
- **Large liquidity (Tj's §62 question again)**: the study's props and lines at $500+ at the price: CLV −0.8% (26 closes, 58%) against +1.6 to +2.1% for the smaller books (37-53 closes each); Tj's own bets say the opposite (+3.7% on 7 at $500+). Two files, two signs, a handful of closes: nothing.
- **Props vs game lines**: Tj's own bets: props +1.2% (234 closes, 68% beat), spreads −1.5% (60, 50%), totals −1.3% (48, 42%); the study: props +2.0% (68), totals +1.0% (33), spreads +1.3% (37), all against different yardsticks (81.2). The game lines' closes are mostly Novig's own trades and ESPN's DraftKings: they disagree with each other and with props. No rule.
- **What would settle anything**: the study's 812 open bets are the NFL games of Oct 4-5 and the following week; after Sunday night's games ~400 bets have a result and, for the props that ParlayAPI's file carries, a Pinnacle close. Ask Tj to send both files again then; and until then read ROI as nothing and CLV only inside one close source.
- **Changed because of this**: nothing in what the app bets. Fixed in the log: the close-source split and the one-name summary (81.2); the key-route stand-downs (81.1).

### 81.4 The bids (Make orders): lower the margin? popular markets? a sharp book required? (BZ4)
- **What the file says**: 597 bids posted in 14 days (505 by auto-make), **1 filled**. The last 24 h: 277 bids posted, **13.2 bid-hours** up (longest 8 min), 1 filled where §70.3's own table expects ≈2.1 for lives like these (the file's own words: "too few bid-hours to judge: this many is the likely outcome"). Median rest 2 min, 90th 5 min. Why they come down: ×242 "About to expire: re-posted" (the re-quote of §70.3, working as designed),
  ×164 "the fair price goes old within a minute: re-priced at the next scan", ×67 "Scanning is paused", ×54 expired, ×22 cancelled by Tj. 58% led their side.
- **A bid's life is the fair's life**, not the 30-minute expiry: `MakerQuote.precheck` caps it at the fair's own freshness (`Freshness.maxAgeMs`: 5 minutes inside 3 hours of the start, 10 beyond), so a bid is up from one scan's fair-odds read until that read goes old, and again after the next scan. Fills come from bid-hours (§70.3: 4% on a prop fills 11% an hour, 25% in three), so **more bid-hours, not a smaller margin, is what is missing**: the wallet ($26) holds 5-13 bids at once,
  scanning was paused for stretches of the day, and a scan that takes 5 minutes leaves the bids down for part of every one. The 404 fix (81.1) shortens scans.
- **3.5% or 3.25% under the fair: not made the default, offered as chips.** §70.2/§70.3 (1.6M simulated bids on Novig's real trades): per posted bid, 3% and 4% earn the same (re-quoted, w=0.25: +0.97% vs +0.99% a side); 3% fills about 30% more often and earns about 0.8 points less per fill, so 3.5% is +15% fills for ~0.4 points less per fill, 3.25% +22% for ~0.6. At w=0 (a fair that knows nothing Novig doesn't, which is
  what Vigilant's own bets measured against Novig's trades: CLV −1.6% on EV +3.1%, 49 closes) the per-fill edge at 3% is +0.37% against +0.87% at 4%: half of it gone for 30% more fills. Tj's CNO props ran about a quarter of their shown EV (CLV +0.7% on +2.9%), i.e. w≈0.25, where lowering the margin is a wash. **And Novig's grid is half a cent**: at a fair of 0.52, 4%, 3.5% and 3.25% all post at 0.500; at 0.45, 4% and 3.5% both at
  0.430 (+4.65% at the fair) and only 3.25% moves to 0.435 (+3.45%). So the choice is mostly illusory in 0.25-point steps. The default stays 4% (`makerMargin`); `MAKER_MARGIN_CHOICES` is 3 / 3.25 / 3.5 / 4 / 6 / 8% (the label shows two decimals, 3.25%, not 3.3%).
- **Popular props first (built, on by default), measured on Novig's own volume**: a bid fills only when a taker crosses it, so what matters is how much takers trade each KIND of market. Novig publishes every listed market with its `dailyVolume` (`markets.csv`, zero-volume ones included, NOVIG_API.md §10); `tools/research/novig_popularity_study.py` (7 days, 2026-09-27..10-03: an NFL Sunday, Monday and Thursday night, the Saturday NCAAF slate)
  gives dollars traded per LISTED market a day, and it differs by 100x within one sport: **NFL** anytime touchdown **$5,900** (74% of listed markets traded at all), first touchdown scorer $3,900, interceptions thrown $2,900, rushing attempts $2,000, passing completions $1,460, passing attempts $1,210, receptions $980, passing TDs $680, rushing yards $660, tackles+assists $555, **receiving yards $490**, passing yards $490,
  rushing+receiving yards $140, kicking points $86, longest rush $57, **longest reception $51**, passing+rushing yards **$3**; **MLB** pitcher strikeouts $7,500, pitcher outs $7,400, home runs $3,900, **hits $190, hits+runs+RBIs $180, total bases $155**, RBIs $50, batting strikeouts $31, runs $21; **NHL** shots on goal $780, saves $700, player goals $345, **points $175, assists $75**, first goal scorer $20, power-play points $9;
  **WNBA** points $700, rebounds $550, assists $415, first basket $405, PRA $390, **threes $225**, double-double $230. Tj's own NFL bets sit mostly in the middle ($250-1,000: 178 of his 294 NFL bets, 54 hot, 1 obscure). **`MarketPopularity`** holds the table (league|type -> dollars; hot from $1,000, popular from $250, obscure under); `MakerPlan.priority` orders the bids that go up when the wallet, the most bids or the most dollars
  can't take them all: **those that lead their side, then hot, popular, obscure, then the cheapest and the most EV** as before; a kind the study never saw is popular when 6+ books price the line (`MAKER_POPULAR_BOOKS`). It changes which bids go up first, never which qualify, and it is not a measured edge (fills by kind weren't simulated: the volume says where takers are, not what their fills are worth): a bid on a hot market meets more takers, and §70.2 found props earn per fill at w=0.
- **A sharp book required to agree (built, OFF)**: `makerRequireSharp`: no bid unless a sharp book (Pinnacle, Circa, Kalshi, ProphetX …) prices the line both ways and agrees; the veto (any sharp book saying no) is unchanged and game lines already required one. The evidence is a coin: Tj's bets with no sharp book on the page −1.9% CLV (12 closes) vs +3.5% (62) with one; the study's props the other way on 6 closes. A resting bid is exposed to informed takers longer than a bet is, so the argument for it is
  strongest here; turn it on in Bids › Rules to see how many bids it costs, and look again when the Sunday games have closes.
- **Not changed, Tj's call**: the fair-age limit that ends a bid (`Freshness.maxAgeMs`) and `stop 15 min`, and the 6 h trap guard on bids (§70.4 found early posting fills more: 12-48 h out ~50% against 16-20% an hour out; §71's guard was about bets, and bids inherit it). Longer-lived bids on a fair a sharp book hasn't moved would be the next lever; it loosens a freshness guard, so it waits for his word.

### 81.5 The button next to every bet (BZ5)
- `GameBets` (data/tracker) indexes Tj's open bets (locks included) and the bids still resting by game, built when bets or bids change (`MainViewModel.gameBets`, off the main thread, equal while nothing changed). **Which game**: Novig's event id when the listed bet has one (Vigilant's), else the matchup and start `GameExposure.sameGame`/`PlacedIndex.gameKey` already use for the guard (CNO's rows, bets saved
  before v0.59.0; 12 h, baseball 2 h), so the opponent's side is the same game and next week's rematch or a doubleheader's other game is not. A game he has nothing on has no button.
- **The button** is "$21.30 in game ▾" (the money in open bets on it; "$4.50 in bids" with only bids up), a 24 dp chip under the game's name, amber when the game is at the per-game limit. A tap opens a sheet: each bet (newest first) with its pick, market, price, auto-bet/lock, and its dollars; resting bids apart ("not placed yet"); the total ("Total bet $21.30 across 7 bets"); what the limit counts
  when a lock or both sides of a market make it less ("$19.10 at risk"); and the room under the limit. On the +EV cards, CNO's cards and bet sheets, ParlayAPI's picks, the Games list, and an open bet in the Tracker (a graded bet shows none). Not on the picture-in-picture window (it takes no taps).
- **Proofs**: GameBetsTest 11 (six props and a total are one list; a bare matchup finds the game; another event, next week, a doubleheader's other game, never; the id finds it whatever the name says; only open bets; bids apart and ended ones not counted; a lock never adds risk; equality), GameBetsUiTest 10 (the chip on the +EV card, CNO's list, the Games tab, the Tracker; none for another game; the sheet's rows and total; the words), 17 mutants killed (3 needed stronger tests).

### 81.6 The three started NCAAF bets that waited for a tap (found reading the Grading WATCH)
`Miami (FL) @ Clemson` (two bets) and `Arkansas State @ Louisiana-Lafayette`: ESPN's own scoreboard (read 2026-10-04) has them as "Miami Hurricanes at Clemson Tigers" and "Arkansas State Red Wolves at Louisiana Ragin' Cajuns" on 2026-10-03. `BetGrader.gameOf` asked each team to score 0.8 ([TeamMatcher.similarity]); "Miami (FL)" and "Louisiana-Lafayette" share one word with their ESPN names (0.5), so the game was
"not found" and the bet waited for a tap. It now accepts what the scans' matcher accepts: both clear 0.8, **or** each clears 0.5 and together 1.5 (one exact, one sharing its name); two games that share only a school word (Washington State @ Fresno State against Oregon State @ Idaho State: 0.5 + 0.5) are still two. `BetGraderTest` +1 with ESPN's real names, 3 mutants.

## 82. Am I beating the close, and why am I losing? The v0.60.0 files read (v0.61.0, 2026-10-05; Tj, with the v0.60.0 diagnostics file and scan study: "Analyze my clv and EV bets. Am I beating the clv? Why am I losing money? Can I and should I tweak anything")

Method: the diagnostics file's EVERY BET lines (Tj's own 638 placed bets; 554 graded non-lock bets with a price, $664 staked, 418 with a true close) parsed with code; the study's 1,459 logged bets (1,054 graded, two days: Saturday NCAAF and Sunday NFL) parsed the same way. Intervals count GAMES, not bets (a game-clustered bootstrap), because bets of one game win and lose together (§81.2).

### 82.1 Am I beating the close? Yes, by a little, and only in one place
- **All 418 closes**: CLV **+0.28%** (95% interval −0.57 to +1.06), median **+1.52%**, 61% beat the close, stake-weighted +0.53%. The listed EV on the same bets is **+3.0%**: about a tenth of the edge shown survives to the close, pooled. Mean far under the median = a fat left tail (below).
- **By close source** (never pool; §81.2): the Tracker's own read just before the start +0.73% (223 closes, 69% beat; median +2.8%), Novig's last trades with 3+ trades −0.35% (134, 49%), with 1-2 trades −2.33% (36: noise), ESPN's DraftKings line or Pinnacle +3.47% (25, 88%: small, mostly game lines and a few props). Three yardsticks, three different answers; the only independent sharp one has 25 bets.
- **By how long before the start the bet was placed** is the one thing in the data that moves it (every split below counts closes, game-clustered intervals):
  | placed | bets (games) | closes | CLV (95%) | beat | listed EV | profit on stake |
  | :- | :- | :- | :- | :- | :- | :- |
  | within 6 h | 110 (61) | 96 | **+3.00%** (+2.2 to +3.8) | 81% | +3.3% | **+$1.44 on $140** |
  | 6-24 h | 198 (71) | 117 | +0.15% (−1.3 to +1.3) | 54% | +3.0% | +$3.61 on $236 |
  | 24 h or more | 246 (76) | 205 | −0.91% (−2.4 to +0.5) | 56% | +2.9% | **−$31.07 on $289** |
  Including the 19 outlier bets (over ±6% EV) the picture is the same: inside 6 h +$3.95 (CLV +2.85%, 99 closes), beyond −$24.21 (CLV −0.45%, 328). **All of the net loss (−$20 to −$26) came from bets placed 6 h or more before the start.**
- **It holds on both halves of the period** (split by date, 2026-09-30 16:29Z): inside 6 h +1.64% (43) then +4.11% (53); beyond −0.86% (166) then −0.17% (156). **It holds inside each close source where there are enough**: Tracker read +4.78% inside (47) vs −0.35% beyond (176); Novig's trades (3+) +0.81% (35) vs −0.76% (99). **It does not show** in the 25 bets with a DraftKings/Pinnacle close (+3.4% inside on 7, +3.5% beyond on 18): too few, and mostly game lines.
- **A regression of CLV on EV, "within 6 h", "over 24 h", the Tracker-close flag and "prop"** (413 closes): within 6 h **+2.9 points (t = 2.6)**, over 24 h −1.2 (t = −1.3), listed EV **+0.22 points per point of EV (± 0.36)**: once the lead time is in, the size of the listed edge says almost nothing about the real one. Inside 6 h every EV band beats the close (under 2% +1.2% on 11, 2-3% +2.1% on 27, 3-4% +4.1% on 31, 4-6% +4.4% on 23); beyond 6 h none does (−0.4, −1.0, −0.3, 0.0).
- **The left tail is all early bets**: 37 of the 418 closed more than 10% worse than their price (a player ruled out, a line that moved on news); **every one was placed 6 h or more before the start, 26 of them over 24 h out**. Inside 6 h the worst close is −5.4% and the 5th percentile −2.3%. Without the worst 5% of bets the mean CLV is +1.45%.
- **Honest limits**: (a) for bets placed in the last 15 minutes the Tracker's last read can be the very read the bet was placed on, so CLV ≈ EV there (29 bets, CLV minus EV +0.6 to +1.0); from 30 min to 6 h the reads are separate and CLV is +2.7 to +3.0% on a listed +3.3 to +3.5% (58 bets: it keeps about 85% of its edge). (b) The Tracker read is CNO's own consensus at the close (the same books that made the EV), a weaker yardstick than Pinnacle's; against Novig's trades the within-6 h figure is +0.8 to +1.7%. So the real edge inside 6 h is somewhere between +1% and +3%, not the +3.3% listed. (c) 96 closes (the 110 bets are in 61 games) is a start, not a proof: ±0.8 points either side.

### 82.2 Why am I losing money?
- **Mostly variance, on an edge that is small.** −$26.02 on $664 staked (ROI −3.9%, interval −13% to +5%). At the EV the lists showed (+$19.06 expected, one standard deviation $30.4) that is z = −1.4; at no edge at all (z = −1.06, p = 0.14); at the edge the closes actually say (+$2.84 expected, $27.6) it is **z = −0.34**: an ordinary stretch for an edge of +0.3%. Win rate 45.4% against a listed fair 49.1% and a price-implied 47.6% (489 bets, standard error 2.3 points): 1 standard error under break-even.
- **What makes the edge small**: (1) 444 of the 554 bets (80%, $524 of $664) were placed 6+ h before the start, where the closes say the shown edge is gone (−0.5%); they lost $27.46. (2) The shown EV runs about 10x the real one pooled (82.1), and ¼ Kelly sized on the shown EV bets more than the real edge supports. (3) Stakes of $1.27 on average ($250 bankroll) make an honest +2-3% worth $2-4 on the 110 bets inside 6 h; no stake size makes a bet that loses to the close profitable.
- **Props lost most (−$43 on $478, 381 bets, ROI −9.0%, z = −2.1 against their listed EV) while beating the close** (CLV +0.85%; inside 6 h +3.57%, 88% beat, on 76 closes): that is the shape of bad luck, not of a bad bet; overs −16.3% (z −2.4) and unders −1.4% have the same CLV (+1.0% and +0.7%). The study's 736 graded props agree: overs won 48.0% against a fair 48.7%, unders 61.6% against 57.4% (calibrated within noise). **No rule on overs or props is supported.** The 3-4% EV band (−21.1% ROI, z −2.5, but CLV +0.9%) is the same: ROI and CLV disagree and CLV is the one with the evidence.
- **The study cannot settle any of this**: 255 of 1,459 bets have a close, 163 of them Tj's own bets' Tracker reads, 68 ESPN, 4 Pinnacle; the 1,054 graded are two days and 57 games (ROI +3.6%, interval −1 to +9; shown −0.7% [−10, +10] vs hidden +7.0% [−1, +13]: overlapping, nothing to read). Its use is the calibration: the fair at the first look vs how often it won (53.2% against a fair of 52.1% and a price of 51.5%) is consistent with a real edge of 1-2 points, no more.
- **Data checks** (BZ2 again): nothing contradicts between the files; Tj's 638 bets are in the study as the 151 `placedByTj` rows (ROI −4.3%); the diagnostics' "Edge accuracy +0.3% on 456" and "all-time +0.3% on 467" are the same bets with different outlier handling; the FAIL at the top of WHAT TO DO ("Vigilant's edges lose to the close") is 58 closes of a scanner that has been asleep since the CNO-only switch, mostly game lines against Novig's own trades: history, not a failure (82.4).

### 82.3 Can I, should I tweak anything?
- **Do (Tj's one tap): set the lists' "Starts within" to 6h.** It had no 6 h choice (Any / 12 / 24 / 48): the trap guard (auto-bet, alerts, bids) already stops at 6 h, but the lists a bet is placed from by hand did not, and 135 of Tj's 202 bets since Oct 2 (64% of the dollars, $209 of $329) were placed 6+ h out. Some of those are auto-bets: 17 on Oct 4 between 01:15 and 03:09 under v0.59.0 were placed 7.5 to 19 h before their games, which the guard cannot do at 6 h (the code applies it before any bet: `AlertPicks.cnoCandidates`), so it was set wider then: the auto-bet's skip counts list both "starts in more than 6 h" (170) and "more than 12 h" (151), so it ran at 12 h for a stretch (the file keeps no settings history; it reads 6 h now). **Built (v0.61.0)**: Starts within offers **3h and 6h** (`ScanSettings.STARTS_WITHIN_CHOICES` = 0/3/6/12/24/48; the widget's switch cycles them); his setting is his to change.
- **Keep**: the 6 h trap guard on auto-bet, alerts and bids (do not widen it to 12/24 h); the sharp veto; the CNO floors; the Bids margin. **Not supported by this data**: a higher EV floor (every EV band inside 6 h beats the close, the 2-3% one by +2.1%), a lower one for volume (11 bets under 2%: +1.2%, too few), requiring a sharp book for props (the props a required sharp book would drop closed +3.10% on 11 against +1.63% for the kept 64; §81.4 again), any rule on overs, props or leagues.
- **For Tj to decide (not changed; safety limits)**: the edge is real but thin (+1 to +3% inside 6 h) and the stakes are small ($1.27 average, $3.50 cap, $250 bankroll). The profit lever is more bets inside 6 h, not bigger ones outside it; a larger stake inside 6 h would be sized by the real edge (about a quarter to a half of the shown EV), not the shown one. `Most a bet`, `Most a day` and `Kelly` are his.
- **Built into the logs so the next file says it by itself**: Tracker › Stats › Where it's working has a **Time to start** chip (profit, ROI and CLV per band, nearest to the start first); the health checks judge **edge accuracy on the bets placed inside the guard's window** and add an **Early bets (CLV)** check (WARN while bets placed earlier lose to the close and some were placed in the last 3 days, naming the Starts within fix); a switched-off scanner's old bets are a WARN, not a FAIL; the study's summary lines carry a **± that counts games** ("ROI +3.60% … ±5.0 points over 57 games").
- **What would settle it**: about 300 closes inside 6 h (96 now) with the interval under ±1 point, and a sharp (Pinnacle) close for the props among them: ParlayAPI's file has Pinnacle's prop close for few (×114 "not in the file" in the study). Ask Tj for the next files after a full NFL week at 6 h.

### 82.4 The rest of the diagnostics file, read
- **Auto-bet "on but can't run"**: Tj turned the background scan off himself at 21:06 (`SETTINGS background auto-scan: BOTH → OFF`); not a bug. The Auto-bet tab's one-tap fix turns it on.
- **451 ANONYMIZED_NETWORK on Novig's key routes** (274 in 3 days: 115 of 2,608 `/v3/orders`, 83 of the market reads): Novig's verdict on the carrier address (all 141,170 calls were on mobile data, none on Wi-Fi); the app stands the key route down for 2 minutes and reads the public routes (§30.1). A refused order places nothing. Nothing in the app to fix; Wi-Fi or a different carrier route would remove it.
- **WTA/ATP "0 of 12/8 games matched"** are qualifying rounds, which no fair-odds source prices in the feed: the finding repeats while those games exist. Not touched (the Vigilant scanner is asleep; CNO prices them itself).
- **The Odds API runway** (891 of 1,000 credits, 2 keys) and **ParlayAPI "spent today's share"** concern only Vigilant's own scan and the closing-line lookups; with the scanner on CNO only neither costs a bet.


## 83. Live (in-game) arbitrage on Novig: is a system that buys one side and later the other, for a guaranteed profit, plausible? (2026-10-05; Tj: "consider whether it would be plausible to make a live betting arbitrage system for the app … track rapidly moving live odds … place bets on one side of a live event and then … the other side … guaranteed profit. It can utilize both make and take bets … just investigate and research if it is plausible. The feature would have to auto bet using the novig API")

**Verdict, in one paragraph.** What Tj described (buy one side of a live market, later buy the other, profit guaranteed) is **not an arbitrage on Novig and loses money**: measured on 664 decided games over 29 days (87,000 entries) and on tonight's live games, every rule that doesn't know the future loses 1 to 3 cents per $1 of payout, takers and makers alike, with intervals that exclude zero (83.4). What **does** exist is a different, true arbitrage that needs no price movement: **inconsistencies between the lines of one game's ladder** (the moneyline against the spread ±1.5, one total against the next), which open for **a second or less** after a score and are **worth cents**: 9 bursts, about **$6.56** of net profit in 2.4 hours of one NFL game if every one had been caught (83.5). It is technically reachable with Tj's key (a websocket book feed, a batch order endpoint, 120 ms order calls) and nowhere near what the work, the risk and a phone on a carrier network justify, unless it is first **measured passively** for a few weekends (83.7). Not built; nothing in the app changed.

### 83.1 Why "buy A, then buy B" cannot be a guaranteed profit on one exchange
- The two ladders are one book (NOVIG_API.md §7): the price to take A is 1 − the best bid on B. So ask_A + ask_B = 2 − (bid_A + bid_B) ≥ 1 + the spread whenever the book isn't crossed. **At any one instant buying both sides costs more than $1.** Measured live: 73 two-sided game-line books (155 live markets, NFL/MLB/NHL/WTA, 2026-10-05 02:38Z): **none crossed**; the spread (1 − bid_A − bid_B) median **4.0¢** (25th 2.5¢, 75th 11.5¢; thin alternate lines pull it up), narrowest **0.5¢** (an MLB moneyline, $3.3k to $9.1k a side).
- So a profit needs the price to **move between the legs** (buy A at a1, later buy B at b2, profit 1 − a1 − b2 − fees). Until the second leg fills that is a bet on direction; and a price that is a fair game gives a rule that picks the moment (a take-profit, a stop) the same expectation as buying and holding: **minus the spread and the fees**. In play the taker pays 0.03·P(1−P) a leg (0.75¢ per $1 of payout at 50¢; fees in play only, NOVIG_API.md §8): two taker legs cost the spread plus 1.5¢, so the move has to beat about 3¢ before a cent is made.
- **The trap in looking at the books afterwards**: in a 5-minute sample of six liquid live lines (2-second reads) a net-positive taker-taker lock existed in 0 to 66 of 144 starts per line "within 2 minutes" (up to 74 within 5), **but only if you knew in hindsight which side to buy first**. That is look-ahead, not a strategy.

### 83.2 What the API can and can't do in play (docs.novig.com read 2026-10-05; NOVIG_API.md §18)
- **Can**: an event in `OPEN_INGAME` is tradable (event-lifecycle: Tradable ✓, Fees: taker); `DELAYED` is tradable too and clears the book when it reopens; orders are `GTC`/`GTT`/`IOC`/`FOK`/`PO` (§14.3); **`POST /v3/orders/batch`: up to 256 orders in one signed request, one `place` token each, "all or nothing" for accepting the batch, but "the exchange judges each order on its own"**: both legs go in one round trip, **but their FILLS are not atomic** (one `FOK` can fill and the other not): the leg risk stays.
- **Speed**: the `book` channel pushes every order-book change (16 tokens a market) and `trades` every execution; a connection may watch 2,048 markets and a subscribe over the 512-token `stream` bucket passes when it is full (about every 128 s); `place` is 256 burst, 8 a second; `cancel` 16 a second. Live books are not cached (REST read 158 ms median from here, "Miss from cloudfront"); Tj's phone (his v0.60.0 file): `api.novig.com` first byte **p50 76 ms, p95 89 ms, max 147 ms** over mobile, **`/v3/orders` 121 ms on average**. So a push-to-order loop of about 200-300 ms is plausible. The app's auto-bet is a **15-second poll** inside `AutoScanner.cycle`: a live arbitrage would be a different process (a foreground service holding the socket, event-driven, `NovigStream` exists).
- **Can't / unknown**: **`NOT_LIVE_TRADABLE`** is a documented refusal (400) that no page explains: some markets can't be traded in play through the API, and which ones is not published (the first real in-play order is the test; QA, `api.qa.novig.com`, can try it first). **Nothing has ever been placed in play by Vigilant** (`AutoBettor`: "not pregame (live betting isn't available)", `AutoBet` pregame only by the safety rules). RFQ/parlays aren't in the API.
- **Limits that bite on a phone**: a signed route is refused `451 ANONYMIZED_NETWORK` on a carrier address that others share: **115 of 2,608 `/v3/orders` calls (4.4%)** in the v0.60.0 file were refused so, all 141,170 calls were on mobile data; the 3-day companion-window rule (open the Novig app); `423` locks; one live `trading` key per subaccount. A missed leg is a naked bet.
- **Rules**: `GOLIVE` **voids every resting order** (a pregame make order can never fill in play; a live maker must post after the event goes live and again after every `GOLIVE`/`DELAYED`); **a self-match is a wash** ("your order traded against another of yours": you get a `fill`, the position doesn't change; a bid on each side at prices summing to 1 or more would trade with itself); the Maker Credit terms forbid "wash trades, prearranged trades, self-matching orders, or any other trade or course of conduct designed to generate Maker Credits **without bona fide market risk**" (a lock whose point is the credit is what that sentence is about); the Ludlow Rulebook (abusive trading) governs the account and **was not readable here: unverified**; LPs (market makers) onboard with a $30,000 minimum and the standard fees.

### 83.3 Static arbitrage inside one book or one ladder: none at rest
- **Within a market**: none (83.1). **Across the lines of one game** (a ladder): the margin or the total is one number, so "margin > t" is a contract at every threshold t and must be worth no more at a higher t. Buying "margin > t₁" and "margin < t₂" for t₁ < t₂ pays at least $1 whatever happens (both win when the margin falls between), so it is a **guaranteed profit when ask(margin > t₁) + ask(margin < t₂) < $1**: a **crossed book across lines** (the lower line's ask under the higher line's bid). Moneyline = threshold 0, a spread of ±k = threshold ∓k, a total = a ladder of its own.
- **Measured** (three sweeps of every live moneyline, spread and total book at about 2.5 reads a second: 648 reads, 34 ladder checks across the live NFL, MLB, NHL and WTA games): **zero crossed ladders**; the nearest miss was **1.0¢** gross (an NFL moneyline against the +1.5 spread, reads 2.4 s apart) and 1.5¢ on a total, before about 1.5¢ of fees. So a mispricing that sits there for seconds is not there to find.

### 83.4 Dynamic locks, on Novig's own trades (`tools/research/novig_live_lock_study.py`, 29 days, 664 decided games, 87,000 entries)
Novig's trade files have no live flag (a taker's cost carries no fee: checked on 63,953 trades), so the in-play window is inferred (the last 1.5-2.25 hours before a decided last trade, game lines only, entries priced 10-90¢, prices from the last print, never older than 60 s); each number is cents per $1 of payout, a mean over entries with a 95% interval over games. A taker row is an executable ask for the outcome bought and a bid for the other.
| rule (enter every 30 s, on each side) | horizon 60 s, lock target 1¢ | horizon 300 s, lock target 1¢ |
| :- | :- | :- |
| **T** taker-taker, unlocked position marked at the mid (no exit cost: the kindest mark) | **−1.51¢** [−1.60, −1.42], locked 8% | **−1.80¢** [−1.90, −1.68], locked 33% |
| **F** taker-taker, the other side bought at the horizon anyway (a forced lock: what the guarantee costs) | **−3.01¢** [−3.19, −2.85] | **−2.98¢** [−3.15, −2.81] |
| **M** maker-maker (post-only bid at the best bid, then a lock bid at 1 − b − 1¢), through fills (a lower bound), unlocked at the mid | −1.47¢ [−1.61, −1.33], filled 37%, locked 29% of the filled | −2.68¢ [−2.90, −2.44], filled 69%, locked 55% |
| **MF** the same, forced lock | −2.42¢ [−2.56, −2.25] | −3.18¢ [−3.41, −2.94] |
| **M** touch fills (an upper bound; the queue is not in the file), unlocked at the mid | −0.36¢ [−0.46, −0.25] | −1.39¢ [−1.58, −1.23] |
| **MF** touch fills, forced lock | −1.06¢ [−1.18, −0.95] | −1.77¢ [−1.99, −1.59] |
- By league (T / F, 60 s): MLB −1.2 / −2.4, NHL −1.2 / −2.4, WNBA −1.4 / −2.7, NFL −1.6 / −3.1, NCAAF −2.0 / −3.9. A lock target of 3¢ changes nothing (the table is the same to 0.2¢).
- **On tonight's live games, exact windows** (the public trades route, 1,503 markets, 46,907 prints; 47 markets with enough prints): T −1.6¢, F −3.2¢, M through −0.75¢ (forced −1.7¢), M touch **+0.21¢** [+0.05, +0.47] at 60 s unlocked (the single best cell of the whole study, and its forced version is −0.5¢); the spread seen from prints 1.2¢. Props worse (T −2.5¢, 11 markets).
- **Reading it**: a lock does get made (8-33% of entries for a taker, 29-55% of fills for a maker), and the rest are held or forced at a loss that outweighs it, exactly as an unpredictable price implies. The maker rows sit closest to zero because a maker earns the spread and half the taker's fee (a credit worth about 0.4¢ a fill at 50¢) while a taker pays both; they still lose because a bid fills when the price is moving **through** it (adverse selection). "Quick and rapid" doesn't change it: in a liquid live market the price moves a lot (the NFL moneyline traded about once a second; 45% of 10-second windows moved 1¢ and 16% moved 3¢, 17 liquid game lines tonight), and every one of those moves is as likely to go against the first leg.
- **Not tested**: a rule with a **signal** (a faster outside feed such as Pinnacle live, §21; a leader line leading the others). That is not arbitrage and has no guarantee; §21 found Novig leads every free feed.

### 83.5 What does exist: cross-line bursts after a score (from tonight's live trades)
- Method: tonight's trades for 13 ladders (1,503 markets, 13,545 prints on the lines of 4 live games); pairs of EXECUTED trades within 1 s on two lines of one ladder (a taker bought "margin > t₁" and a taker bought "margin < t₂" with t₁ < t₂), prices as executed (the public route's prices are the resting order's, so the taker paid 1 − price).
- Result: 1,058 such pairs, **101 cost under $1, 76 are still profitable after both live fees** (overlapping combinations: one trade pairs with several partners; **matched once each they are 17 pairs over 84,965 contracts**, see §84.2), in **9 bursts** (7 on the NFL game's moneyline against the spread, 2 on a total), each **1 to 26 pairs over 1 to 2 seconds**, net **0 to 2¢ per $1 of payout**, legs of **$0.60 to $145** of payout. If every one had been caught at its printed price (each print once): **about $6.56 in 2.4 hours of one NFL game**.
- Read it with care: these are trades that **happened** (somebody took them; four pairs have equal-sized legs 0.25 s apart, hardly proof of a bot); a window nobody took isn't in a trade file; only the 13 ladders of the games live at the time. They agree with the book sweeps: the windows exist for a second or two around plays, while makers re-quote line by line (§21: "market makers pull their quotes around plays").
- Order of magnitude if it could all be caught: a 13-game NFL Sunday plus the night games is perhaps tens of dollars to about a hundred gross a week (this game's $6.56 times a slate), before every leg that fills alone (a naked bet losing about the spread and a fee, 1.5-3¢, against 1-2¢ made when both fill: it takes both legs filling more than half the time, against sub-second competition) and before Tj's own limits ($3.50 a bet, most on one game). The depth is $40-$100 a burst, which a $99 wallet could fill; the money is small because the windows are.

### 83.6 What a system would have to be (not built), and what each piece costs
1. **A recorder**, not a trader: a foreground service holding one websocket (`NovigStream`, `book` on every live game line, ~16 tokens a market) that writes, per game, every moment a ladder's asks cross, with the books at both ends, how long it lasted and what it would have paid after fees. No orders, no money, no risk; Tj's key and phone as now. This is the only way to learn the true count, length and size of the windows (the trade file shows only the taken ones), and it costs a weekend of battery.
2. **A paper trader** on those logs with a latency model (push 50-100 ms, order 120 ms on this phone, 4.4% refusals) saying what share of windows a slower-than-the-rival bot would have caught.
3. **Execution** (only then, at $1-2 a leg): one `POST /v3/orders/batch` of two `IOC` orders at the printed prices (the batch is one round trip; the fills are independent: `FOK` per leg means a leg fills whole or not at all), then on a one-sided fill an immediate hedge (a lock, `LockIn`, §67) at the worst case worked out beforehand; `cancel`s and a self-match check (never two orders that can trade with each other); positions read to confirm.
4. **Risks that stay**: a leg that doesn't fill (leg risk, no guarantee until both do), a refusal (451, 423, `NOT_LIVE_TRADABLE`, 429), a `GOLIVE`/`DELAYED` mid-sequence, a tick of latency on a mobile network, a rival faster than a phone, and rules this repo cannot read (the Rulebook). Locked arbitrage on a **fee** schedule that can change (§8, "The coefficient can change").

### 83.7 Recommendation
- **Do not build "buy one side, then the other for a guaranteed profit" (83.4): it is a measured loss, taker or maker, with a number on it.**
- **If Tj wants to pursue the cross-line idea, build only the passive recorder first** (83.6.1), run it for two or three weekends of NFL/MLB/NHL/college, and decide from the counts: what the windows pay a week in total, how long they last (a window under about 300 ms is not catchable from a phone), and how many are on the depth Tj can bet. Written into TASKS.md as a question for Tj, not started.
- Not changed: nothing in the app; `tools/research/novig_live_lock_study.py` is new (re-runnable, ~5 min once cached); NOVIG_API.md §18 records the new API facts.


## 84. How the 9 bursts happened, and how Vigilant could replicate them (2026-10-05; Tj: "Short bursts after scores … 9 bursts, each 1 to 2 seconds long … Do research on how this happened and how vigilant can replicate it")

**In short.** The bursts are real, mechanical and small. **How**: a play moves one team's win probability by 5-10 points; the lines of the game are separate order books re-quoted one after another, and for **0.3 to 2 seconds** one of two *neighbouring* lines (the moneyline against the ±1.5 spread, a total against the next one) still shows the old price. Buying the stale line and the fresh one together costs **under $1** for a payout of **at least $1** (and $2 if the final margin lands between them). **Replicate**: it needs no outside feed, only the signed websocket's `book` channel on every line of a live NFL-type game, a ladder check on each book change, and a two-order batch of `IOC` orders, all inside a few hundred milliseconds. **What it would pay**: the replay of the one game we have says a 300 ms reaction keeps 47% of the guaranteed floor ($3.08 of $6.56), 500 ms 23%, 1.5 s none; adding the middle lottery (about 3% of the contracts win twice) the whole game's 17 trades were worth about **$32 expected, $6.56 locked**. At Tj's wallet (~$99, most $50 on a game) that is **a few dollars to perhaps $20 a game, on one measured game, against rivals that are demonstrably faster than 100 ms**. It is plausible to build, not obviously worth it, and **the next step is measurement, not code** (84.7).

### 84.1 What set each burst off (the tape, ESPN's play-by-play, the moneyline's own jumps)
- **Eight of the nine bursts follow a play that moved the win probability 3.5-13%**, 5 to 13 seconds after ESPN's wall-clock stamp for it (the stamp is to the second; the market reprices when the play's result is known). ESPN's swing for CAR (`summary?event=401872978`, `winprobability`) and the play: 00:30:07 −4.4% a 54-yd field goal; 01:14:13 +6.9% a 26-yd completion; 01:17:05 +9.2% a 10-yd completion; 01:25:43 −5.5% a 17-yd completion; 01:27:35 −3.5% an 8-yd completion; 01:48:05 −8.7% a 49-yd completion; 02:14:43 +10.8% a 10-yd touchdown pass; 02:21:42 −13.2% a 57-yd completion. **The ninth (02:44:16) followed an incomplete deep pass at 15:00 of the fourth quarter, +1.4%: the start of a period, not a swing** (the quotes are re-made at a period break; one burst, so not a pattern). Most are not scores. In the other direction a swing does not guarantee a burst: **45 plays moved the win probability 3%+, 8 made a burst (18%); 17 moved it 5%+, 6 did (35%); 6 moved it 8%+, 4 did (67%)**; the +12.9% 32-yd touchdown pass made none (the quotes were pulled on the score).
- **From the moneyline's own trades** (19 moneyline jumps of 3¢+ in 2.4 hours of live play, 11 of them 5¢+): jumps of 3-4¢ (8): **0** made a burst; 5-7¢ (7): 2; 7-12¢ (3): **3 of 3**; the 16¢ touchdown (1): none. (`novig_ladder_tape.py analyze` counts jumps of 4¢+ and so shows 11.) So the market alone says it: a burst needs a jump big enough to carry a stale line past the band below.
- **The band**: two nested lines can only cost under $1 when the stale one is wrong by more than the chance the margin falls between them plus the spreads plus the two fees. Measured as the cost of YES at the lower line + NOT at the higher one for executed trades within 2 s of each other: moneyline vs +1.5 **median 1.030 outside the bursts (never under $1: 0 of 50)** and **0.985 inside them (96% under $1, 41% under 0.985, which is 1.5¢ of fees below a loss)**; the band widens with distance (moneyline vs +2.5 1.055, +3.5 1.14, +4.5 1.17, +5.5 1.20). **Only the nearest neighbours can cross**, and only after a 5¢+ move.
- **Only football showed it**: the NFL game (27 margin lines + 29 total lines with trades) made 9 bursts; MLB (7 lines, 2,759 prints), 3 NHL games and tennis made none. In baseball and hockey the chance of a one-run or one-goal margin is 20-25% against 3% in football, so that band is ten times wider and an ordinary stale line can't cross it. The prediction (not measured): the sports that can show it are the high-scoring ones: NFL, NCAAF, NBA, NCAAB, WNBA.

### 84.2 What the bursts looked like (17 distinct trade pairs, 84,965 contracts = $850 of payout)
| burst (UTC) | window | ladder | stale-then-hedge pair | pairs / contracts | floor |
| :- | :- | :- | :- | :- | :- |
| 00:30:07 | 0.4 s | margin | +2.5 YES vs +1.5 NOT | 1 / 1,000 | $0.06 |
| 01:14:13 | 2.0 s | margin | +1.5 YES @0.48 vs ML NOT @0.504 | 3 / 30,000 | $1.50 |
| 01:17:05 | 1.2 s | margin | +1.5 / +2.5 YES @0.505 vs ML NOT @0.465 | 4 / 14,703 | $2.35 |
| 01:25:43 | 0.3 s | margin | ML YES @0.539 vs −1.5 NOT @0.445 | 1 / 4,301 | $0.22 |
| 01:27:35 | 0.0 s | margin | ML YES vs −1.5 NOT, one millisecond | 2 / 18,291 | $1.11 |
| 01:48:05 | 0.9 s | margin | ML YES @0.44 vs −1.5 NOT @0.53 | 1 / 5,000 | $0.76 |
| 02:14:43 | 0.0 s | total | 51.5 YES vs 52.5 NOT | 1 / 1,000 | $0.07 |
| 02:21:42 | 0.7 s | total | 51.5 YES @0.855 vs 52.5 NOT @0.133 | 3 / 7,364 | $0.26 |
| 02:44:16 | 1.0 s | margin | ML YES @0.685 vs −1.5 NOT @0.295 | 1 / 3,306 | $0.24 |
(the "76 pairs" of §83.5 were overlapping combinations of these.) Net per contract 0.1¢ to 1.5¢ after both fees: **the arbitrage is the thin end of a much bigger mispricing**: the pair is 4.5¢ cheaper than in calm (1.030 → 0.985), and the 1.5¢ of fees plus the 2-3% overlap take it to a floor of 0-1.5¢ and an expected 2.5¢.
- **Which line is stale changes**: after a play that favours Carolina the spread +1.5 lags and the moneyline is the hedge (01:14, 01:17); after one that favours Detroit the moneyline lags and the −1.5 spread is the hedge (01:25, 01:27, 01:48, 02:44). **There is no fixed leader**: whichever line the makers re-quote second is stale. In 01:17:05 the +2.5 spread printed 0.510 at 05.085 and 0.555 at 06.079: **about a second** to re-quote; the +1.5 spread's stale slab (20,000 contracts at 0.505) was gone **0.28 s** after its first trade.
- **Who took them**: sizes and rhythm look like programs, not people: 22 prints of exactly 10,000 contracts at one price in **2.65 s, 8.3 a second, the default `place` refill (8 a second)**; a single 113,000-contract print (a $1,130 taker order); two takers of different size in one millisecond across two markets (01:27:35.569, the only same-millisecond pair among the 43 same-millisecond multi-market moments of the game: not proof of a batch). In the 01:17:05 burst the stale spread was bought 56 ms **before** the repriced moneyline traded: someone acts on the book, or ahead of it, in tens of milliseconds. **Identity is not in the file** (accounts are anonymised); a rival faster than a phone is the working assumption.
- **The middle is the part the floor ignores**: covers pay $2 when the margin lands between the lines (moneyline vs +1.5: Detroit wins by exactly 1; +1.5 vs +2.5: by 2). At 2%, 3%, 4% (football's one-and-two-point margins; assumption, the file has no results) the 17 trades (85k contracts) were worth **$23.6, $32.1, $40.6 expected** against **$6.56 locked**.

### 84.3 How it happened: the mechanism (an inference from the tape, flagged as one)
1. A game's lines are separate books, each re-quoted by whoever makes the market in it (NFL: 27 margin lines and 29 totals with trades; ~500 markets with props). After a play every one of those quotes has to be cancelled and re-posted. A key may place **8 orders and cancel 16 a second** (`/v3/limits`, 256-burst): a maker with dozens of lines re-quotes them in a sequence over about a second. That is the measured window (0.3-2 s) and the reason there is no fixed leader.
2. The lines near the money are the ones people trade, and they are the ones whose neighbours are only a point apart, so the cover between them has the thinnest band (2-3%): they are the ones that cross.
3. Quotes are pulled entirely on the biggest plays (the touchdown made no burst), and on small plays the move is inside the band: the bursts live between 5 and 10 points of win-probability swing.
4. A taker who sees one line's book change (the signed websocket pushes it) can buy the lagging neighbour before it is re-quoted and cover it with the fresh line: no information about the game is needed, only the books.
Not shown by the tape (it holds only what traded): when the quotes went stale, which maker was slow, and windows nobody took.

### 84.4 What replicating it takes (nothing here is built)
**Detector** (pure, on the signed websocket; `NovigStream` and `StreamBooks` already apply `book` deltas):
- *Universe*: events in `OPEN_INGAME` (`catalog/events?status=OPEN_INGAME&league=…`, then `GOLIVE`/`UNLIVE`/`DELAYED` on the `lifecycle` channel), their `MONEY`/`SPREAD`/`TOTAL` markets (NFL: 56 with trades; the 2,048-market cap and the 512-token `stream` bucket at 16 tokens a `book` market: one bulk subscribe once the bucket is full, as `NovigStream` does today).
- *Ladders*: one number per game (the margin of the first team; the total): a market is a threshold t with a YES outcome = "margin > t" (moneyline t = 0, a spread "X −k" t = k, "X +k" t = −k; a total's "Over s" t = s) (the parse is in `novig_ladder_tape.py`). Keep best bid and best ask for YES per threshold.
- *Check on every delta*: for the changed market and every other threshold, the cover cost `ask(YES at t1) + ask(NOT at t2) = (1 − bid(NOT t1)) + (1 − bid(YES t2))`, t1 < t2; net = 1 − cost − fee(p1) − fee(p2) with fee(p) = 0.03·p·(1−p) (the market's own `fee` object); a hit when net ≥ the safety margin (say 0.3¢). All pairs, not just adjacent (a wide middle line can hide a cross): n ≈ 30 lines, microseconds.
**Executor** (new; `NovigTradingClient` has `placeOrder` only):
- `POST /v3/orders/batch`: two `IOC` orders (`price` = the observed ask, never worse, `qty` = the smaller depth capped by Tj's limits), one signed request, one round trip; **the fills are independent** (§83.2), so read them back at once (private `orders`/`fills` channel, or `GET /v3/portfolio/fills`).
- **Leg risk is smaller than it sounds**: the stale leg alone is a bet bought below its fair price (worth about the 4.5¢ mispricing less one fee); the fresh leg alone is a fairly priced bet (it costs the 0.75¢ fee). So if only one leg fills, the loss is a fee and the stale-leg bet is itself +EV; a top-up `IOC` on the short leg at the break-even price closes the cover when the book still allows it. (An unhedged "stale-leg" bet is a second, riskier strategy, about +3.7¢ expected a contract before variance: unmeasured, no tape of the books to check it.)
- Safety as the auto-bet has it: its own switch, off, pregame rules untouched; per-game and per-day limits (`BetLimits`, `GameBets`); never two orders that can trade with each other (a wash); the key's `GET /v3/limits` read first; a halt on a lost answer; the `451` stand-down. A cover holds capital until the game settles (two different markets don't net).
**Latency** (the replay, per burst, reaction measured from the first trade of the burst: the quote went stale at or before it): L = 0.1 s keeps **64%** of the locked $6.56, 0.2 s 49%, **0.3 s 47%**, 0.5 s 23%, 1.0 s 23%, 1.5 s 0%. Phone: websocket push over mobile (unmeasured; the signed `book` frames' arrival against the engine's trade `ts` is the thing to measure) + `/v3/orders` **121 ms on average** (Tj's v0.60.0 file; first byte p50 76 ms) + the 4.4% of order calls refused 451 on that carrier: realistically 250-400 ms, so **the first 300 ms of every window, the biggest slabs, belong to something faster**.
**Money** (Tj's scale: wallet ~$99, most $50 on one game, $3.50 a bet today): a $50 cover earns about $0.75 locked and about $2.50 expected at a 5% EV; the game's 9 bursts if all were caught: $7 locked, $22 expected; at a 300 ms reaction and the same competition roughly half. **A few dollars a game, one game measured.**

### 84.5 What we don't know (and one correction)
- **One game.** The other four live games had none (their ladders are thin or their bands wide); the burst rate per game, per sport and per week is unknown; so is the effect of a different set of competitors on another night.
- **Staleness is read from trades.** A window nobody took isn't in the file; a window that lasted longer than the tape shows can't be seen; the first trade is not the first stale moment. The book channel is the instrument.
- **The push channel's own delay** against the engine clock is unmeasured; so are `NOT_LIVE_TRADABLE` (which markets refuse an in-play order), the Rulebook (abusive-trading rules, "bona fide market risk"), and whether a fee schedule change (the coefficient "can change") moves the band.
- **Correction to 83.5**: "76 profitable pairs" counted overlapping combinations; matched once each there are **17**, and the dollars ($6.56, 84,965 contracts) were already per trade.

### 84.6 Re-run, and the tool (`tools/research/novig_ladder_tape.py`)
`record --league NFL --minutes 200 --wait --out tape.ndjson` polls the PUBLIC routes (no key, 3.7 requests a second at most, 429 backs off) for every live event of the league: each near-the-money moneyline, spread and total market's trades (ms engine timestamps, de-duplicated; 300 a poll) and, every fourth pass, its top of book; `analyze tape.ndjson [--overlap 0.03]` prints the bursts, the floor, the expected value with the middle, the reaction-time table, the moneyline jumps against the bursts and the calm cover cost. It reproduces this section's numbers from tonight's capture (converted to its format: 9 bursts, $6.56, 100/64/49/47/23/23/0%) and ran on a live NHL game.

### 84.7 Recommendation
1. **Do not build the trader.** One game, a measured reaction share under half at the best case, rivals under 100 ms, and a few dollars a game at Tj's size.
2. **Measure first, for free**: run the recorder on the next high-scoring games (Monday night NFL Falcons @ Saints 8:15 pm ET = 00:15Z on Oct 6, the Thursday game, the college Saturday, NBA and WNBA when they start) and send the tapes: ~3 more NFL games say whether 9 bursts a game, 1-2 s each and ~$30 expected is the rule or one night. It runs from any machine with the repo (this container included) and needs the session alive at kickoff.
3. **If the tapes agree, the next step is a no-orders recorder inside the app** (a foreground service on `NovigStream`, `book` on every line of live games, timestamping each change on arrival, logging every moment a cover crosses and how long it stayed): it measures the push delay, the true window length and how many a phone could have caught. Only after that, a paper trader with a latency model, and then one tiny live cover.
4. **Hold the unhedged "stale-leg" variant** (84.4) for that recorder: it is the same data, it pays more per trade, and its edge (the 4.5¢) is the thing to confirm.


## 85. Does the CNO scanner get tennis? No: CrazyNinjaOdds doesn't carry it (2026-10-05; Tj: "I don't think the cno scanner is getting any tennis. Can it?")

- **CNO has no tennis, anywhere.** Its Positive EV form's Sport list is All / Baseball / Basketball / Football / Hockey / Soccer, and its League list has no ATP, WTA or any tennis tour (read live 2026-10-05: 6 sports, 18 leagues: MLB, NFL, NCAAF, NHL, NBA, NCAAB, NCAAW, WNBA, MLS, Premier League, LaLiga, Bundesliga, Ligue 1, Serie A (Italy, Brazil), Liga MX, FIFA World Cup); the Browse (games, markets), Arbitrage and Low Hold pages have the same lists; the words tennis, ATP and WTA appear on no tool page, only in the FAQ's sportsbook house rules (void and retirement rules). The app's own `CnoView` knows five sports for the same reason. A posted ASP.NET form can't carry a value outside its own dropdown, so there is nothing to ask for.
- **The files agree**: the scan study's CNO columns (1,260 rows over 10 days, the app's list and the wide read with every numeric filter opened) have Sport = Football 1,096, Hockey 85, Baseball 36, Basketball 35, Soccer 8; **no Tennis row**. All 16 tennis bets in the study (11 ATP, 5 WTA) came from Vigilant's own scan (`src: v`); none from `c` or `w`.
- **Where tennis comes from**: only Vigilant's own scanner (`League` ATP and WTA since v0.19.0: Pinnacle with set lines, Kalshi `KXATPMATCH`/`KXWTAMATCH` and the challenger series, PropLine, market averages; Novig's round suffixes handled in `NovigText`). It is **asleep in Tj's settings** (Scanner: CNO only): so the app has had no tennis at all since the switch. Novig lists plenty: 74 tennis events open now (ATP 30, WTA 44, 63 starting within 24 h; 31 are qualifying rounds, which Vigilant's sources don't price: "WTA 0 of 12 matched" in the Diagnostics), each main-draw match with a moneyline, game spreads, game totals and a first-set market.
- **What the tennis there looks like**: 16 bets in the study at EV +1.0% to +8.7%, fair from "market average, worst case" on all but two (not a sharp book), 6 graded (3-3 at unit stake); Tj placed 2 (Lois Boisson moneyline won, Vendula Valdmannova lost, CLV −1.1% on 1 close). **There is no CLV evidence for tennis yet**, and Vigilant's own bets as a whole closed −1.5% against Novig's own trades (58 closes; §81.2, §82.2); CNO's +0.6%.
- **How to get it today**: it takes Vigilant's scan, not CNO's. The league list (ATP, WTA are already on) only matters to Vigilant's scan: CNO's rows are filtered by Tj's link and the start window, never by the league chips (`UiState.cnoCandidates`). So **Scanner: Both with the league chips set to ATP and WTA only** gives CNO for everything it carries plus a tennis-only Vigilant scan (small: the last all-league scan priced 20 lines of 54 games; tennis uses Pinnacle's one tour-wide request and Kalshi, not The Odds API). The chips live on the +EV and Games tabs, which "CNO only" hides, so the league list can't be changed in that mode. Limits: the **auto-bet only reads CNO's rows** (`AlertPicks.cnoChecked`), so tennis would be hand bets and Vigilant's alerts, not auto-bets.
- **Not changed**: nothing in the app. Options for Tj (TASKS.md CD2): (a) a "CNO + tennis" scanner mode (Vigilant asleep except ATP and WTA, one tap, no league chips to find); (b) a line on the CNO tab and in Settings saying CNO has no tennis; (c) letting the auto-bet bet Vigilant's tennis picks, which needs a sharp fair for tennis and some CLV evidence first (turn it on for a week and send the files).

- **Update 2026-10-07 (DI5, §110): the leagues CNO does carry are now Tj's to pick.** Read off the live page's two dropdowns (kept as `data/src/test/resources/cno-page-selects.html`; `CnoPageTest` checks the app's table against it id for id): Sport = All 0, Baseball 1, Basketball 4, Football 2, Hockey 3, Soccer 5; League = All 0 plus **17**: MLB 1, NFL 2, NCAAF 3, NHL 4, NBA 5, NCAAB 6, WNBA 7, NCAAW 8, FIFA World Cup 9, MLS (USA) 10, Serie A (Brazil) 11, Liga MX (Mexico) 12, LaLiga (Spain) 13, Bundesliga (Germany) 14, Ligue 1 (France) 15, Premier League (England) 16, Serie A (Italy) 17 (the "18 leagues" above counted All). Both are single-choice. Still no tennis, UFC or boxing.


## 86. "Cheaper than Pinnacle on the same prop side": how often is it +EV, and how often does it beat the close? (2026-10-05; Tj: "If I'm only comparing prop bets to the same side on pinnacle, and I can get it at better odds than pinnacle, what are the chances it's a positive EV bet that beats clv")

Method: the v0.60.0 diagnostics' bets (Tj's placed ones) and the scan study's bets, kept when the book page carried Pinnacle's price for both sides (82 and 126 bets; **73 and 98 props**). "Better than Pinnacle" = Novig's taker cost below Pinnacle's implied probability for the same side. "+EV against Pinnacle" = cost below Pinnacle's no-vig fair (multiplicative; the power method gives the same counts within one bet). Scripts were scratch (`/tmp/claude-0/pin/`), figures below are the final run.

### 86.1 What Pinnacle's own vig does to "better odds than Pinnacle"
- Pinnacle's two-way prop overround is **5.6% (Tj's bets) / 4.8% (study)** at the median. A side near 50% therefore has a **dead zone of 2.5–2.7 probability points** between Pinnacle's quoted price and its no-vig fair (median gap 2.73 / 2.46 points). A Novig price inside it is better than Pinnacle's odds and still a **negative-EV** bet against Pinnacle's own fair, about 5% of EV at the edge of the zone.
- So "better odds than Pinnacle" is a necessary test, not a sufficient one. The bar is Pinnacle's devigged fair, and a real margin above it.

### 86.2 The data (props that were better than Pinnacle's raw price: 73 of 73, and 96 of 98 — CNO's list already requires a plus)
- **+EV against Pinnacle's fair: 71 of 73 (97%, Wilson 91–99%); 83 of 96 (86%, Wilson 78–92%).** Mean EV against Pinnacle's fair +4.4% (median +4.2%) and +2.9% (median +2.8%). The 13 study bets that fell in the dead zone were all listed at CNO EV +0.2% to +2.3%: small listed EVs are where "better than Pinnacle" fails.
- **By how much cheaper than Pinnacle's price** (probability points; share +EV against its fair; beat the close; mean CLV):

  | edge vs Pinnacle's raw price | Tj's bets | study |
  | :- | :- | :- |
  | under 2 pts | n=1, 0% +EV | n=9, **0% +EV** (mean EV −1.8%) |
  | 2–3 pts | n=1, 0% | n=14, 79% +EV (mean EV +0.4%) |
  | 3–4 pts | 15, 100%; 14 closes, 79% beat, CLV −1.18% | 28, 96%; 10 closes, 90% beat, +1.49% |
  | 4–5 pts | 30, 100%; 25 closes, 84% beat, +2.83% | 30, 100%; 14 closes, 79% beat, +2.96% |
  | 5+ pts | 26, 100%; 20 closes, 75% beat, +0.47% | 15, 100%; 5 closes, 100% beat, +2.39% |

  Rule of thumb that falls out: under about 2.5 pts cheaper than Pinnacle's price is not +EV against it; 3 pts is marginal; 4+ pts is +EV in all 71 cases.
- **Beat the close: 49 of 61 (80%, Wilson 69–88%) and 28 of 34 (82%, Wilson 66–92%).** Mean CLV +1.1% (median +3.2%) and +2.1% (median +2.1%). Among the +EV ones: 47 of 59 and 27 of 32 (80%, 84%).
- **Time to start matters, but less than the first draft said** (Tj's, bet-placed to start): under 6 h 17 closes, 88% beat, CLV +3.4%; 6–24 h 23 closes, 96% beat, +2.7%; **24 h or more 21 closes, 57% beat, mean CLV −2.6%**. The −2.6% is two news moves (§86.5: Michael Mayer Under 21.5, −36%, and Tory Horton Over 11.5, −42%, both 38–40 h out); without them the 24 h+ bets are 12 of 19 (63%) at +1.2% (median +2.2% either way), still the weakest group, but it is 19 bets. The study has none above 24 h with a close (its lists were already inside Tj's window): under 6 h 13 closes 85% beat, +2.1%; 6–24 h 21 closes 81% beat, +2.1%. The cheap price is the same at any lead time; whether it survives to the close is not.
- **How much of the edge shows up as CLV: little of it.** The EV against Pinnacle's fair vs realised CLV: correlation 0.08 (n=61) and 0.22 (n=34), slope 0.28 / 0.24; neither is distinguishable from zero. EV bins 2–4%, 4–6%, 6%+ on Tj's bets give mean CLV +1.4%, +0.4%, +3.1% — not monotone. Realised CLV is roughly a quarter of the EV against Pinnacle's fair, consistent with the shrinkage in §82 (listed EV is ~0.1× pooled, ~0.85× inside 6 h).
- **Profit says nothing yet**: Tj's 59 graded props lost $12.52 on $99.14 staked (−12.6%); the study's graded props are 40 won / 41 lost. At 4–5% EV on prop prices near 50%, one standard deviation of ROI over 59 bets is about 13 points; the sign of the ROI is not informative.

### 86.3 What this cannot settle
1. **The yardstick is not Pinnacle.** 60 of the 61 and 34 of the 35 closes are Tracker reads of CNO's consensus (no Pinnacle close in either subset; ParlayAPI's Pinnacle closes cover 11 bets in the diagnostics, none of these props). "Beat the close" here means the edge held against the same consensus that listed it, a lower bar than beating Pinnacle's own closing no-vig price. The direct test (Pinnacle prop closes from ParlayAPI) is not in these files.
2. **Selection.** Every bet here was already on CNO's +EV list. This is the chance given that CNO flagged it, not the chance for any prop that happens to be cheaper than Pinnacle. The base rate over unfiltered Novig props is not in the data.
3. **Size and overlap.** 61 and 34 closes, with Tj's and the study's bets overlapping and several bets per game; the Wilson intervals above are for independent bets, so they are tighter than the truth.
4. **Pinnacle's props are not its sharpest product.** Props carry low limits, are often posted early and stale, and are the least efficient market Pinnacle makes; "fair" there is itself uncertain by a point or two.

### 86.4 What to set (nothing built; Tj asks first)
- If the question is "what does cheaper than Pinnacle have to be": compare to Pinnacle's **devigged fair, not its quoted price**, and require **at least 3 points of probability** (≈ 3–4% EV against its fair) for the bet to be +EV with margin; under 2.5 points it is the dead zone.
- Inside **24 h** (already inside 6 h for the auto-bet/alerts/bids via the trap guard); the 24 h+ props at the same price beat the close only 57% of the time with −2.6% CLV.
- Treat the result as unproven until Pinnacle's own prop closes confirm it: the next diagnostics + study after a full NFL week at "Starts within 6h" should carry Pinnacle closes for these props (BX5).

### 86.5 What the bets were, and what replicates them (Tj, 2026-10-05: "how I can replicate these bets. What kind of bets were they, how long before each game")
List of every bet (one row each): the scratchpad file `pinnacle-prop-bets.md` of the 2026-10-05 session (not in the repo).

**One slate, not a season.** Pinnacle's price for both sides is on a bet's book page only in the newest records: the 61 (Tj) and 35 (study) closes are **21 and 16 games, almost all of one day** (Tj's 61: 23 bets in the Sunday Oct 4 1:00 pm ET NFL games, 18 in the 4:05/4:25 pm games, 13 in the Sunday-night game (11 of them Lions @ Panthers), 7 NHL/MLB/WNBA on Friday and Saturday). 41 of the same props sit in both lists.

**What they are.**
- **Sport and market**: NFL player props 51 of 61 (Tj) and 30 of 35 (study): receiving yards 16, receptions 12, passing yards/attempts/completions/TDs/interceptions 15, rushing yards/attempts 7, anytime/player TD 1; NHL **shots on goal** 8 and 5; one MLB hits allowed, one WNBA points. Both sides: Over 29 / Under 32 (Tj), 18 / 17 (study); Over beat the close 86% and 89%, Under 75% and 71%, not a gap this sample can call.
- **Price**: coin-flip props. Taker cost 0.415–0.605, median **0.475 (about +110)**, 38 of 61 at 0.45–0.50, American +100 to +141 on almost all (three at 0.55–0.61, −102 to −153, that did as well). Pinnacle's price for the same side was about **4.8 points** higher (median; 3.9 in the study), i.e. **+2% to +9% EV against Pinnacle's fair, median +4%**, mean +4.4% (Tj) and +2.9% (study).
- **Time to start**: median 12–15 h before the start for Tj's; the early NFL games (Sun 1:00 pm ET) were bet at a median 14 h (the night before), the late games (4:05/4:25 pm) at 29–33 h, the night game at 3 h, NHL shots at 0.2–18 h (median 2.4 h). By lead: under 30 min 5 bets, 30 min–2 h 4, 2–6 h 8, 6–12 h 8, 12–24 h 15 (**15 of 15 beat, +3.6%**), 24 h+ 21.
- **Who placed them**: Tj's 61 = 38 auto-bets (74% beat, +0.9%, median lead 15 h, up to 49 h: they predate the 6 h trap guard) and 23 by hand (91% beat, +1.3%, median 12 h). All from CNO.
- **Games**: 16 of the 21 games had a positive mean CLV; the worst were LAC@SEA (4 bets, −7.7%) and KC@LV (6 bets, −4.4%), each carried by one news move (Horton, Mayer).

**Replicating rule that falls out** (checked on both lists):

| rule on the props with a close | Tj's (n; beat; mean / median CLV) | study |
| :- | :- | :- |
| Pinnacle same-side price 3+ points above Novig's cost, bet **under 24 h** before the start | 38; 92%; +3.1% / +3.8% | 29; 86%; +2.4% / +2.2% |
| + under 12 h | 24; 88%; +2.8% / +3.6% | 16; 81%; +2.0% / +2.4% |
| + under 6 h | 17; 88%; +3.4% / +3.8% | 13; 85%; +2.1% / +2.0% |
| 4+ points cheaper, under 24 h | 29; 93%; +3.6% / +3.8% | 19; 84%; +2.8% / +3.3% |
| 3+ points cheaper, **24 h or more** | 21; 57%; −2.6% / +2.2% | none |

With the two news moves left out (|CLV| above 6%, Tj's own outlier rule) the 38 become 33: 94%, +3.1%. Of those 38, 35 are graded: Tj staked $59.27 on them and made **−$0.02**: the CLV has not turned into money yet, and one standard deviation of ROI over 35 bets is about 17 points.

**What in today's app already selects these**: CNO only; "only bets the books agree on"; Starts within 6 h (the 3 h / 6 h chips since v0.61.0); the trap guard (auto-bet, alerts and bids only inside 6 h); the auto-bet's "≥ 3 books agreeing it's +EV", "2 pricing both sides", edge ≥ 2.5% at Novig's price now, odds +130 to −200 (the +131 to +141 bets were by hand), props allowed; **the sharp-book veto at 1.0%** (auto-bet drops a bet whose sharpest book, Pinnacle where it prices it, gives it under +1%). What is NOT there: a bar against **Pinnacle's own price on the same side** (the veto is on EV against its fair, 1%, not a 3-point price edge), and nothing in the CNO list shows "points cheaper than Pinnacle's price" as a column.

**What it can't say**: one slate (a Sunday NFL card plus hockey); the close is CNO's consensus, not Pinnacle's; the 3-point threshold is a first-principles dead-zone figure (§86.1) the data is consistent with but cannot pin (the study's 2–3 point bin is n=14, 79% +EV); 3 of the 38 under-24 h bets did not beat the close; no 24 h+ study bets to test the lead cut, and the 24 h+ weakness is two bets plus noise. A week of NFL at "Starts within 6h" with Pinnacle's prop closes (BX5) is what would test it.


## 87. "Locked in" showed −$2.29: a wrong grade, not a lock at a loss (2026-10-05; Tj: "Notice the app locked in negative profit. Either this is an error in stats or the app allowed lock in at negative return. Immediate")

**It was a wrong grade.** The Locked-in card on Tj's phone read 14 locked bets in 13 markets, **−$2.29 on $35.11 (−6.52%)**, **−$2.49 graded (12 markets)**. The Oct 4 21:50 diagnostics had 12 markets, +$0.63 on $32.44, +$0.51 graded (11 markets): so one market, still pending then, went from its locked profit to **−$3.00**. That market is **Ollie Gordon II 29.5 rushing yards (Dolphins @ Vikings, started 20:05Z)**:
- the pick, Under 29.5, +108, $1.4976 (312 contracts), graded lost by the score feeds at 23:13Z;
- the lock, **Over 29.5**, +108, $1.4976 (312 contracts), placed 2026-10-02 19:43:14Z by a path that left no `lockFor` (it was imported from Novig's fills as a plain bet: no fair odds, the market's own wording "Ollie Gordon II 29.5 RUSHING_YARDS" / "Over 29.5", which is why the card counted 13 and 14 "bets" for 12 and 13 markets); still PENDING at 21:50 local, 5 h 45 min after the start, 15 minutes before the 6-hour mark.
- **ESPN's box score: Gordon ran 9 times for 100 yards.** Under lost, **Over won** (profit +$1.62), and the market made 312 × $0.01 − $2.9952 = **+$0.12** exactly as locked. Two losing legs in one two-outcome market with a half-point line cannot happen: the Over leg's grade was wrong. The arithmetic closes to the cent: +$0.507 (11 markets) − $2.9952 = −$2.488 = "−$2.49 graded (12 markets)"; the rest of −$2.29 is a 13th market not yet graded, +$0.20.

**Why** (`ApiSettler`): "no ledger payout and no position left = a loss" (NOVIG_API.md §14.3: a loss leaves no row). The score feed can't read the imported lock's wording ("Over 29.5"; no player in the selection), so with no feed a loss is taken 6 hours after the start (`INFER_LOSS_AFTER_MS`). Novig's ledger showed no payout for the winning leg at that point (why is unknown from the files: a payout row not yet posted, or not found by the market ref; the other 11 locks' payouts were found), and silence became "lost". Both legs of a locked market were lost.

**No lock was placed at a negative return.** `LockIn.plan` refuses a plan whose worst case pays under the minimum (≥ $0.01, and ≥ 2% of the stake for auto-lock) and checks both outcomes ("Belt and braces", LockIn.kt); the 11 locks of Oct 2 each locked +$0.02 to +$0.10 (+$0.507 total, graded correctly), the Gordon pair +$0.12.

**Fixed in v0.62.0** (`ApiSettler`; tests in `ApiSettlerTest`):
1. **Silence alone is never a loss for a market held on both sides** ([LockedBets.markets]: two outcomes, equal contracts): after the 6 hours it is flagged "check it in the Novig app" instead of graded lost, and a payout that appears later in the ledger grades it won as before. A single bet is unchanged.
2. **A second "lost" in a locked market is refused** (even from a feed): if the other side already lost, this one can't have.
3. **Repair**: a locked market whose every leg is graded lost has its silent-loss leg (note exactly "Novig paid nothing for it and no longer holds the position: a loss", i.e. no feed evidence; a result Tj tapped is never touched) taken back to open with a manual note; the card then reads the lock's worth (+$0.12 for Gordon) until the payout or a tap settles it. Logged as `SETTLE` and the `settle.reopened` counter.
Mutation checks (outside the repo): removing 1, the repair, 2 or the evidence condition each fails a test; the Tj-tap condition is covered by the note condition (redundant).

**Not changed, and what is unknown**: why Novig's ledger had no row for the Over leg (the diagnostics from Oct 5 on will show the settle notes); an imported lock fill isn't linked to its pick (`lockFor` null), so it counts as a pick in "14 of 634" (cosmetic); the feed grader can't read Novig's own market wording for imported bets ("Over 29.5" with "Player 29.5 RUSHING_YARDS").

### 87.1 Investigation (Tj: "Investigate"): the same wrong grade happened twice, and v0.62.0's guard was too narrow
Question: why did Novig's ledger have no payout for the Over leg, and is it a one-off? What the files can and cannot say:
- **Not a one-off.** All 190 LOST API bets in the Oct 4 diagnostics were checked by *when* they were graded (a bet graded 6 h or more after the start can only have come from the silence rule, `INFER_LOSS_AFTER_MS`): eight. Seven NCAAF bets (Mercyhurst/LIU Under 46.5 on 51 points, Alabama State/Bethune-Cookman Over 54.5 on 26, Tennessee Tech −14.5 lost 20-44, Gardner-Webb/Charleston Southern Overs 46.5 and 47.5 on 22, Southern Utah −13.5 won by 13, Eastern Washington/UC Davis Under 60.5 on 87) are **correct losses** (ESPN finals). The eighth is **wrong**: `b88120d1`, the 1-contract Over 53.5 on **Bhayshul Tuten's rushing yards** (JAX @ CIN, a $0.00465 probe order of Sep 29, imported from Novig's fills with no fair odds), graded lost at 6.22 h while Tuten ran **17 for 73 yards**: Over won (+$0.0054). Its market also holds Tj's 232-contract Under 53.5 (graded lost correctly at 3.76 h). Both legs lost, again.
- **Same signature as Gordon**: an imported bet (no `lockFor`, no EV, Novig's own wording "Over 53.5" / "Bhayshul Tuten 53.5 RUSHING_YARDS", so the score feeds can't read it) on the other side of a pick the feeds graded lost at ~3.1-3.8 h; no ledger payout found; position gone; silence taken as a loss 6 h after the start. 2 of the 9 silence-rule grades were wrong, both imported legs with a counter-leg.
- **The ledger's timing is part of it.** In the four 1 pm lock markets the pick was graded lost at 3.0-3.8 h and the lock (the winner) was graded won **at 5.95 h** (one pass, 22:56Z): a winning leg's payout comes later than the losing leg's position disappearing, by up to ~2.2 h here (the passes are ~3 h apart, so the true lag is smaller and unknown). The 6-hour rule is therefore a race against that lag, and a payout that was merely late is indistinguishable from none.
- **Why the two imported legs' payouts were not found is still not known** from these files: the four auto-lock legs with the same game window were found, the two imported legs were not. What differs is only that they were imported (`ApiBetSync`) rather than placed through `placeLock`/`placeBet` (both carry `orderId`, `contracts`, `fillIds`). It needs one ledger read: **Settings › Diagnostics › Grading check › Copy** (what Novig's ledger, positions and fills hold for each API bet, every kind of row) pasted back, or the next diagnostics file, which from v0.63.0 carries `gradeNote` on a bet graded from silence or waiting for a tap.
- **v0.62.0's guard covered only equal holdings** (`LockedBets.markets`). Tuten's market is 232 vs 1 contracts, so it slipped through. **v0.63.0**: the guard and the repair apply to every market where exactly two outcomes are held, in any amounts (`ApiSettler.bothSidesHeld`); a market with a "Draw"/"Tie" side is three-way and keeps the old rule; a void is left out. Tests: the Tuten case (guarded, and the wrong grade taken back), the three-way case; mutation-checked (5 of 5 killed).
- **Money**: Gordon's Over, +$1.62 on a $1.4976 stake, and Tuten's Over, +$0.0054: the Tracker's profit was short by that much until the grade is corrected; the wallet is Novig's own and unaffected if the payouts were credited (check the Vigilant wallet against the Novig app).


## 88. Tracker profit that disagreed with itself, the kill switch, are the bids +EV, unlimited and "quick & likely" bids, and MatchWire (v0.64.0, 2026-10-05; Tj: the Pinnacle-only request below, "when I click novig only … green … red … Shouldn't my profit be the same?", "make sure the math for the auto bid feature is sound", "unlimited bids … only the bets with the maximum chance of being filled quickly and also a decent chance for me to win", "a stop button kill switch … visible everywhere", and "See if this can help: https://matchwire.win/docs/")

### 88.1 Why Profit was green with "Novig only" on and red with it off (CJ1)
Nothing was wrong with the money. The two views counted **different bets**. The "Novig only" lens ([NovigNow.view]) sets every bet's "EV when bet" to the price paid against itself (about 0%, Novig's own odds are its fair), and the Tracker's outlier rule (Tj, 2026-09-27: bets over ±6% EV when bet are "ignored completely") is judged on that field. On: nothing is an outlier, so Profit, Staked and the running-profit line held every bet = the real bankroll. Off: the bets listed at more than ±6% EV (**23** in Tj's screenshot) dropped out of Profit, Staked, ROI and the line (−$3.36 on $713.63), while the small caption "With the outliers counted too, your bankroll's result is …" under it said otherwise. Fix: Profit, Profit % and Staked are every settled bet (`TrackerStats.profitAll/stakedAll/roiAll`), the line is `BetTracker.profitLine` (it ends at Profit), and `TrackedBet.outlierListed` carries the outlier verdict from the EV the bet was LISTED at through the lens, so both views leave the same bets out of the record, EV, CLV and "Are the edges real?" numbers (which is what Tj's 2026-09-27 rule was about: "I don't want the average skewed"). **A change of Tj's standing rule, flagged to him:** outliers are no longer left out of Profit. Tests: NovigNowTest, BetTrackerTest.

### 88.2 The kill switch (CL1)
`ScanSettings.killed` (+`killedAtMs`) is saved with the settings **and** in a one-key `SharedPreferences` copy written with `commit()` before anything else (`KillMarker`), which is the settings file's default and is put back by `KillSwitch.reconcile` when a damaged, reset or restored settings file lost it. `ScanSettings.paused` is now derived (`pausedByHand || killed`; the saved key is still `paused`), so every part that already waits for a pause waits for the kill: Vigilant's scans, CrazyNinjaOdds' reads, Check odds now, the widget's rescans, the background cycle and its service and alarm, auto-bet, auto-lock, and auto-make (which takes every resting bid off Novig: `maker.cancelAll`). `KillSwitch.engage` saves it, stops the scan, the services and the Novig feed, then cancels every bid with one `DELETE /v3/orders` and confirms; the auto-bet also re-reads the saved settings before every order, so a pass already under way places nothing after the press. Only the red bar's RESUME (`resumeAfterKill`) lifts it; ▶ Pause, a pull to refresh, Scan and Check odds now answer "Everything is stopped by the STOP button" (they used to auto-resume a pause). Where: the `KillBar` above the tab bar on every tab, the floating widget's red stop / red play, the auto-scan notification's STOP ALL, Settings › Scanning, Diagnostics and the health checks. Not stopped, on purpose: bets already placed and their grading, a bet placed by hand from the Bet sheet, the wallet read. Tests: KillSwitchAppTest (4), KillBarUiTest (3), PauseScanningTest (+2), ScreenshotTest (+2).

### 88.3 Are the bids +EV? An audit of the math, and what was changed (CK1, CK2)
**What is sound (checked in code and by test):** a bid is `floor(fair / (1 + margin))` on Novig's grid (a floor, never a round-up), so its EV at that fair is at least the margin (random-fair test, 4,000 draws); makers pay no fee pregame (NOVIG_API.md §8, §17) and a fill is at the bid's own price; a post-only bid never takes; a bid never outlives its fair's freshness (5 min within 3 h of the start, 10 beyond), the stop window or the expiry; the wallet, the day's limit and the per-game limit hold every bid; both sides of a market are separate bids; the same side is never bought twice.
**What was weak, and is changed:**
1. *The margin was under the BLEND, not under the book that moves first.* Vigilant's fair is 70% sharp + 30% market average; the soft books in it follow the sharp ones, and a bid is more likely to fill exactly when a sharp book disagrees with the blend. The old veto only asked the sharp book for +1% (worst-case devig) at the bid's price, so the claimed 4% could be 1% against Pinnacle. Now (`makerAnchorSharp`, on) the bid is the margin under the LOWER of the blend and the sharpest book's own fair (each sharp book devigged the worst way): the 4% is a real edge against the sharp book, the veto can't fire on a lower sharp fair (the bid goes down instead), and with no sharp book in the fair nothing changes (most props).
2. *Kelly was sized on the blend* (the auto-bet caps its Kelly fair at the sharp book's; bids didn't). Now sized on the anchor.
3. *A fill's EV was never checked after the fact.* The Tracker's "EV when bet" for a filled bid is the EV at the fair when the bid was POSTED, minutes to hours earlier, so it can never show a bid being picked off. Now each fill is judged against the fair on the first scan after it that has a price seen after the fill (`MakerBid.fairAtFill`, `sharpFairAtFill`, `fillEv()`); a negative one is **picked off** (the market had already moved under the price).
4. *The picked-off guard* (`makerGuard`, on): of the newest 8 judged fills, when half or more were picked off and their average EV at the fill is under +1% (they claimed 4%+), the bids stop themselves (`makerHalted`), the bids the app posted come down, a notification says why, and the Bids tab shows a red banner with Resume bids (fills before the resume aren't looked at again). It needs 6 judged fills, so a few coin flips never stop it.
**What it can and can't say about "taken fast":** a bid 4% under the fair that fills within 30 minutes is normal for longshot props (13-15% of those bids, §88.4) and rare elsewhere (2-3% for 0.30-0.60 bids; 1-2% for game lines); so a pile of fills inside two minutes, on game lines or on 0.40-0.60 sides, is more likely stale bids than good luck. Whether Tj's fills were picked off is in his next Diagnostics (the fills table and the "how fast they were taken" split) and scan study (the BIDS section) — I did not have his post-Oct-4 files.
**What is tracked now (CK2):** `BidReport` (one `Row` per bid: price, the fair and the blend and the sharp fair at posting, the EV claimed, the book it was posted against and its age, whether it led, the expiry; how long it rested and why it ended; the fill's delay, the fair and EV on the next scan, picked off or not; and the Tracker bet's close, CLV, result and profit). Diagnostics prints the summary, the guard's verdict and the newest 40 fills; the scan study has a BIDS section with the same summary, every filled bid and the newest 300 unfilled ones as JSON lines, and its READ ME says how to judge them; a filled bid's Tracker bet now carries an `AtBet` (how = "bid"). Tests: MakerTest (+7), MakerGuardTest (3), BidReportTest (4), ScanStudyTest (+1), MakerAppTest (+1), MakerUiTest (+3).

### 88.4 Unlimited bids, and "quick & likely to win" (CK3)
**Unlimited:** `makerMaxBids` accepts `NO_LIMIT` (and the dollars `MAKER_NO_DOLLAR_LIMIT`); the wallet, the dollars, the per-game and the day's limit still hold every bid. A pass sends at most 60 new bids (`MakerRules.postsPerPass`; Novig's `place` bucket is 8 orders a second, 256 burst), the rest go up on the next pass, so a busy slate can't hold the lock the Pause, the kill switch and the fills' checks wait for.
**The research** (`tools/research/novig_bid_focus_study.py`, output kept in `research/bid_focus_study_2026-10-05_output.txt`): §70's simulation (a bid at fair/(1+4%) on Novig's grid, filled when a taker trades through it) over **29 days, 5,782 markets, 155,000 simulated bids** (2026-09-06..10-04), sliced by the bid's price (about the chance its side wins), how fast it fills, the kind of market, and the fair stand-in w (0 = Novig's own price, 0.25 / 0.5 = a fair that knows that share of the move to the close; Vigilant's fair is w≈0.25-0.5 on props, §70.1).
- **Price is the trade-off.** Fills within 30 min (posted 3 h before the close): bids under 0.20 fill 12-15% (they win 17-23%); 0.20-0.30 fill 5-9%; **0.30-0.60 fill 2-3% (all kinds)**; 0.60+ almost never (0-2%). Takers buy the favorite, so the underdog's bid fills. A bid that is both fast and likely to win doesn't exist for game lines; it exists for props.
- **Kind decides speed:** within an hour (posted 3 h before), player-prop bids fill **22% (under 0.20), 16-19% (0.20-0.30), 10-11% (0.30-0.50), 8-9% (0.50-0.60), 4-5% (0.60-0.70), ~0 (0.70+)**; game-line bids fill 1-2% at 0.40-0.60 and at a fair that knows nothing Novig doesn't they LOSE to the close (−1.5% EV@close); period lines ~5% an hour.
- **Value per fill at 0.30-0.60 (props):** EV@close **+0.75-1.2% (w=0), +2.5-3.4% (w=0.25), +4.3-5.8% (w=0.5)**, CLV +0.3 to +3.1¢; the band's win rate at fill is 33-40% (0.30-0.50) and 52-67% (0.50-0.60). The best candidate rule in the study, "props + team totals, bid 0.30-0.65", fills **9-10% within an hour** against 4-5% for all bids, with EV@close +0.85% / +3.4% / +5.1% (w = 0 / 0.25 / 0.5) and an interval above zero at every w.
**What was built (`QuickLikely`, `makerFocus` = "Quick & likely to win", off by default):** only player props and team totals; only bids priced **0.30-0.60** (never widening what Tj set); a sharp book must price the line both ways (his bets with one kept +3.5% at the close, those without −1.9%, §81.3) and the price is taken under its fair (§88.3); bids that lead their side go up first, then the kinds takers trade most, then the likeliest fill, then the most edge. Honest limits: fills are simulated against Novig's published trades (no queue, no real latency); the fair stand-in is bracketed, not known; props with a sharp book are a small share of props (the Bids tab's "why other lines get none" says how many it drops); the team-total sample is thin (31 bids per 29 days in the study).

### 88.5 Pinnacle only: Novig against Pinnacle's devigged price and nothing else (CI1-CI3, v0.65.0)
**Tj's ask (2026-10-05):** "an option … to auto bet and also a scan filter for only comparing current novig odds on any market and any sport to the current pinnacle devigged odds for the same bet … only scans novig and pinnacle when this option is on so as not to waste usage of other apis … make sure the Pinnacle odds are as current as possible … the diagnostics scan logging keep track of all betting information used with this Pinnacle only setting on so I can track how well bets do clv and EV and profit when only compared to Pinnacle".

**CI1, what the app did before.** It did NOT cover this. The nearest settings were the sharp-book "Require a confirmation" (SharpConfirm asks one Pinnacle feed for the exact bet and devigs it worst-case) with Pinnacle chosen, but the candidates and every other criterion still came from CrazyNinjaOdds (CNO's EV over several books, 2+ books agreeing), the scan beside it fair-priced against all books and exchanges, and nothing stopped Kalshi, Polymarket, The Odds API and ParlayAPI from being read.

**What was built.**
- `ScanSettings.pinnacleOnly` (+ `pinnacleMaxAgeSeconds`, default 90 s, choices 30 s to 3 min). `ScanSettings.effective()` is the settings as the scan reads them: scanner = Vigilant, fair source SHARP, devig WORST_CASE (the lowest of the four devigs: the most cautious for a buyer), `sharpBooks = referenceBooks = {pinnacle}`, no fall-back to an average, one book enough, Kalshi / Polymarket / The Odds API off. The scanner applies it at its own door (`Scanner.scan / recheck / reprice / refreshFair`), so the Tracker's Check odds now, the closing-line capture and the scan study price against Pinnacle alone too. `scannerNow`, `cnoOn`, `vigilantOn`, `autoScansVigilant`, `autoBetsNow` are derived from it (CNO asleep; the background scan runs Vigilant's scan; auto-bet runs from it).
- **Only Novig and Pinnacle are read.** `AppContainer.referenceSources` in Pinnacle only returns Pinnacle's own feeds (PinnWire, then pinnapi) plus ONE backup wrapped in `PinnacleBackup`: PropLine (a free daily allowance of requests, asked for Pinnacle's book alone) if it has a key and is switched on, else ParlayAPI (credits, asked for Pinnacle alone). A backup is asked only for a league the Pinnacle feeds did not answer (game lines) or where they priced no prop at all (props; the per-game requests are the expensive kind). The scanner also drops any offered source the effective settings do not switch on (`Scanner.readable`).
- **Pinnacle as current as possible.** PinnWire's/pinnapi's board is shared for 20 s (not 60 s) in this mode. After a scan ends, the auto-bet pass (`AutoBettor.runPinnacle`) looks at the best edges (`PinnacleBet.TOP` = 12); any whose Pinnacle quote is older than a third of Settings' age limit has its league's Pinnacle board READ AGAIN first (`Scanner.refreshFair`: drops the scanner's windows and the feed's own share via `ReferenceSource.forget`, re-fetches the first choices then the fallbacks as a scan does, re-prices with the books already read). A bet then passes only if Pinnacle's quote is within the limit AT THE ORDER (`PinnacleBet.judge`), plus: Pinnacle's price is the whole fair, pregame, kind / odds limits, an edge in [minimum, 15%], the trap guard's early rule. The order itself goes through the same path as every auto-bet (`sendOrder`, extracted from `run`: in-flight marker, the placer's own check against Novig's book right now, a lost answer halts).
- **Tracked.** Every bet carries `AtBet.pinnacleOnly`, `pinnacleAgeSec` and Pinnacle's own two-sided price (`books`). Diagnostics has a "Pinnacle only" section (state, last pass, re-read counters, the bets' record, profit counting every settled bet, EV / CLV / beat-close, split by the age of Pinnacle's price, market, time to start, and where the closes came from), the scan study has two splits and a READ ME paragraph, the Tracker's Stats scanner breakdown and its Bets scanner filter have a "Pinnacle only" row, and the health checks name a scan that priced nothing from Pinnacle, another source being read, failing re-reads and old-price refusals.
- A bug found on the way: `FairBasis.group` looked for the lower-case key "pinnacle" in the fair line's book TITLES ("Pinnacle"), so a Pinnacle-anchored bet was grouped as "exchange sharp only (Pinnacle)" in Diagnostics' basis lines. Now case-insensitive.

**What to know (and tell Tj).**
1. The free PinnWire/pinnapi key allows 100 requests a day (20 a minute) and a whole sport's board is one request, so continuous Pinnacle-only betting spends a key in hours; the key pool rests it and the next feed takes over (pinnapi, then the PropLine backup). The binding limit is requests, not bytes. PropLine's free 1,000 a day (Pinnacle updates in real time there) is what carries the day; see §90 for whether PropLine's paid plan or more keys are worth it.
2. The devig is the lowest of the four (multiplicative, additive, power, Shin). That is conservative: it understates a favorite's fair and overstates a long shot's less than the others. A plain multiplicative choice would show more edge and be less careful; it is a one-line change (`effective()`) if Tj wants it.
3. Pinnacle-only bets are judged against Pinnacle's price. Their CLV is measured against the close the app finds (its own last read before the start, Pinnacle via ParlayAPI after, ESPN or Novig's trades as a last resort); the Diagnostics section says which, so the yardsticks are never pooled.
4. Bids (make orders) price from the same scan, so with Pinnacle only on a bid's fair is Pinnacle's alone as well.

### 88.6 MatchWire (matchwire.win), asked 2026-10-05 mid-session (CN1)
Read in full (docs, pricing page, `llms.txt`). **It is a hosted mapping layer for prediction markets, not an odds feed**: one row per game matched across Kalshi, Polymarket US, Polymarket International and Predict.fun, each with the venue's own market ID and a ready-made request URL; "Mapping only: no prices or order books" (its own words, three times). Key header `x-api-key`, 60 requests a minute, `GET /api/v1/rows?since=SEQ` for changes and `/api/v1/push` (SSE); personal plans Starter/Growth/Pro (prices not on the page; a commercial licence for anyone serving others). **It carries no sportsbook: no Pinnacle, no Novig, no ProphetX, no Circa, and no closing lines or history.** What Vigilant needs it for is the opposite: a fresh sharp PRICE for the same bet as a Novig market. Vigilant already reads Kalshi and Polymarket's prices directly and for free (`KalshiClient`, `PolymarketClient`) and matches games and lines itself (`TeamMatcher`, `ParlayBooks`); the exchanges are weaker references than Pinnacle for the bids (a Kalshi line is a thin book), and the mapping problem it solves is not one of Vigilant's. **Verdict: no use for the Pinnacle-only mode, the bids' fair, or CLV; not worth a paid plan.** One narrow case it could help: if a Kalshi/Polymarket prop were found to be a good sharp reference, its line-aware matching could replace Vigilant's own; nothing in the data says that, so nothing was built.

## 90. The APIs, read against their own docs: used right? what is unused? what is wasted? is PropLine worth buying? who is best at what? (2026-10-05, after v0.65.0; Tj: "Review the full docs on all the apis used in the app …", and "Also consider if matchwire can help match props …")

Method: every provider's current docs page / llms.txt / OpenAPI was fetched on 2026-10-05 ~17:35-18:30Z (PropLine llms.txt + pricing + docs, PinnWire llms.txt + llms-full.txt, ParlayAPI openapi.json + /v1/meta/limits + /v1/meta/source-quality + best-practices + llms.txt, The Odds API v4 guide, Kalshi's market-data and rate-limit pages, Polymarket's overview, Novig's llms.txt + the batch, private-stream and throttle pages, MatchWire's llms.txt + docs) and the public keyless endpoints were called live from this container (PropLine `/v1/freshness`, ParlayAPI `/v1/meta/source-quality`, `/v1/meta/limits`, Kalshi `/markets` and `/events`). Pages are summarized by a fetch model, so each number below that matters for a purchase or a limit was re-checked against a second page or the live endpoint.

### 90.1 What the app calls (CO1)
| API | Calls | Auth / pace the docs set | Where |
|---|---|---|---|
| Novig public | `GET /v3/public/catalog/events|markets`, `/markets/{id}/book` (ETag), `data.novig.com/reporting/trade-data` | none; edge throttle per IP (4-6 a second, halved by a 429) | `NovigPublicClient`, `RateGate` |
| Novig signed | `/v3/catalog/*`, `/v3/ws` (book channel), `/v3/limits`, `/v3/orders` (POST, GET, DELETE one / all), `/v3/portfolio/fills|positions`, `/v3/account/subaccounts/*` | NOVIG-V3 signature; buckets place 256 (8/s), cancel 256 (16/s), read 64 (16/s), account 64 (8/s), stream 512 (4/s), history 512 (4/s), read once from `/v3/limits` | `NovigPublicClient`, `NovigStream`, `NovigTradingClient` |
| PinnWire, then pinnapi | `GET /kit/v1/markets?sport_id&event_type=prematch&include_specials=1` | `x-api-key` / `x-portal-apikey`; free key 100 a day, 20 a minute; a whole sport per request; 429 body `window`, `retry_after_ms` | `PinnapiClient` |
| PropLine | `GET /v1/sports/{sport}/odds`, `/events`, `/events/{id}/odds` | `X-API-Key`; free 1,000 a day, 5/s sustained, burst 10, 20 in flight; 429 `burst_limit_exceeded` + `Retry-After`; 503 on the 21st in flight; `X-Daily-*` on every reply | `PropLineClient`, `PropLinePropsSource` |
| The Odds API v4 | `/sports/{sport}/odds?bookmakers=`, `/events` (free), `/events/{id}/odds` | `apiKey`; free 500 credits a month; cost = markets x regions, up to 10 books = 1 region; `x-requests-*` headers | `TheOddsApiClient`, `OddsApiPropsSource` |
| ParlayAPI | `/v1/sports/{s}/odds`, `/props`, `/closing-lines`, `/historical/closing-lines.json`, `/injuries`, `/usage`, `/meta/source-quality`, `/best-bets`, `/verdict`, `/line-movement` (PARLAY_API.md §3) | `X-API-Key`; Starter $5: 20,000 credits a month, 7 days of history, no per-second cap | `TheOddsApiClient(feed = PARLAY)`, `Parlay*` |
| Kalshi | `GET /trade-api/v2/events?series_ticker&status=open&with_nested_markets` per series | none; paced to 2 a second (429s seen at 4) | `KalshiClient` |
| Polymarket Gamma | `GET /markets?closed=false&tag_id=..` | none; 300 per 10 s | `PolymarketClient` |
| CrazyNinjaOdds | its positive-EV list and devigger HTML pages | no API; a page every ~3 s | `CnoFeed` |
| ESPN, MLB | scoreboards and box scores for grading | public, unofficial | `FreeScores` |

### 90.2 Is each used as the docs say? (CO1)
Yes, with three notes. **PropLine**: header, `bookmakers`, `markets`, the burst / daily / in-flight answers and the `X-Daily-*` headers all match the docs read today; the client's 4 a second is under the 5 a second sustained limit. **PinnWire**: the free key is 100 a day and 20 a minute as the client assumes; every reply carries `generated_at` (the client stamps its own fetch time instead, which is the conservative reading of "how old is this price"). **ParlayAPI**: the plan table and the 429 / 403 bodies match PARLAY_API.md; the advertised `endpoint_costs` (1 / 2 / 5) is a generic table, the measured costs (3 for a three-market odds call, 3 for props, 5 for closing lines) stand. **Kalshi**: its docs now name `https://external-api.kalshi.com/trade-api/v2` as the production URL; the app's `api.elections.kalshi.com` still answers 200 (four reads today), so nothing is broken. **Built (v0.66.0)**: the client keeps `api.elections.kalshi.com` and, if it answers 404 / 410 or its name stops resolving, switches to `external-api.kalshi.com` for the life of the app (a timeout or dropped connection does not switch: it says nothing about the host); test `ExchangeClientsTest › kalshi moves to the alternate host …`. **Novig**: re-read with the changelog and the batch / private-stream pages; nothing the app does is out of line (NOVIG_API.md §13 did the full pass on 2026-09-28).

### 90.3 Who is best at what: the ranking (CO5)
Evidence (live, Sunday 2:13 pm ET): PropLine's own `/v1/freshness` lists Pinnacle at **3,735 active game-line markets, last update 2 s ago, and 1,746 active props, 3 s**; DraftKings 8-9 s, FanDuel 5 s, BetMGM 2 s, Fanatics 2 s, Hard Rock 5 s, Bovada 2-3 s, ProphetX 11-20 s, Novig 6 s; no Caesars and no bet365. ParlayAPI's `/v1/meta/source-quality` shows bet365, BetMGM, BetRivers, Bovada, Caesars, DraftKings, Fanatics `ok` at 0.1 s pulse age, Hard Rock `ok`, BetOnline and Novig `degraded` (33 s, 23 s). PinnWire's docs claim a real-time snapshot per request.
| Job | First | Then | Why |
|---|---|---|---|
| Pinnacle game lines, any alternate line | PinnWire (one request a sport, every alt line) | pinnapi, PropLine's copy of Pinnacle, ParlayAPI | The only feed with alt lines in one request; PropLine's copy is as fresh (2 s) with ten times the daily requests, so it is the right first choice where a main line is enough and PinnWire is out of requests. |
| Pinnacle props | PinnWire (`include_specials=1`, one request a sport) | PropLine (one request a game), ParlayAPI `/props` (one call a league, 3 credits) | PinnWire is one request for every prop of a sport; the others are per game or paid. |
| Soft books' game lines (DK, FD, MGM, Fanatics, Hard Rock, Bovada …) | PropLine (2-9 s, no credits) | ParlayAPI (credits), The Odds API (500 a month) | Free and as fresh as any. |
| Caesars, bet365 | ParlayAPI | The Odds API | PropLine does not carry them. |
| ProphetX, Kalshi (the props' sharp reference) | ParlayAPI (ProphetX), Kalshi direct (free) | none | PropLine lists ProphetX but the app does not use its copy (one-sided prices). |
| Closing lines for CLV | the app's own last read before the start | ParlayAPI Pinnacle closes (5 credits a league), ESPN, Novig's trades | Unchanged. |
| Grades | Novig's own ledger (it is what pays) | ESPN / MLB box scores | PropLine's `/results` (paid) would be a third. |
The app's order already follows this: first choices start together, a fallback runs only for a league its first choice did not answer, a key or a provider that is out of requests rests until its reset and the next takes the league (PinnWire to pinnapi to PropLine's copy; PropLine to The Odds API; ParlayAPI's `CreditPace` holds a day's share). What the ranking adds is an honest budget table (90.6): the free allowances, not the code, decide how often every source can be asked.

### 90.4 Should Tj buy PropLine? (CO4)
PropLine plans (pricing page and llms.txt, read twice): Free $0, 1,000 requests a day, 5/s; **Hobby $9: 5,000 a day, 10/s, burst 20, plus prop resolution (`/events/{id}/results`: actual stat values), `/ev` (Pinnacle-anchored no-vig fair lines, cached 45 s), `/best-line`, `/odds/history`, `/odds/closing` (opening and closing lines), player trends, `POST /v1/clv/grade`, SGP pricing**; Pro $19: 25,000 a day, 20/s, 90-day CSV export of every resolved prop; Streaming Lite $39: 250,000 a day plus a websocket and 5 webhooks (line moves, resolutions, steam); Streaming $79; a one-time $99 history backfill. Data freshness is the same on every plan.
**Recommendation: not for grading; yes for the requests, when the meter says so.**
- *Grading:* Novig settles Tj's bets, and Vigilant grades API bets from Novig's own ledger. The grading errors found (2 of 190 losses, RESEARCH.md §87.1) were a ledger that paid late and an imported leg with no fair odds, not a prop that no stat feed could grade. `/results` would have caught the Tuten case (73 rushing yards in the feed), so it is a good cross-check, not a missing capability. $9 for that alone is not worth it.
- *Requests:* a Vigilant scan costs PropLine one request a league plus up to 12 props games (a minute apart at most); at the background scan's fastest, every 4 minutes all day, 4 leagues is 16 x 360 = **5,760 requests**, so the free 1,000 covers about 62 scans (4 hours of 4-minute scans) and Hobby's 5,000 covers about 312. Pinnacle only adds one re-read a sport per pass. If **Settings › API usage** shows PropLine at its cap before the evening on a normal day, Hobby is the cheapest fix (five times the requests, 10/s); if it ends the day under about 70% of 1,000, it is not needed.
- *CLV:* `/odds/closing` (Hobby) would give Pinnacle's close for every event for one request, in place of ParlayAPI's 5 credits a league; only worth it if ParlayAPI's credits are the thing running short.
- Not worth it for Vigilant: the websocket tier ($39), `/ev` (the app computes the same Pinnacle-anchored fair itself), SGP, trends.

### 90.5 Features the providers have that the app does not use (CO2): what, the gain, the cost, the decision
| Feature | Gain | Cost | Decision |
|---|---|---|---|
| Novig `POST /v3/orders/batch` (256 orders, all or nothing) and `DELETE /v3/orders/batch` (256 ids, partial, idempotent) | a pass that posts or takes down many bids does it in one request, not one each (each ~0.15-0.3 s with the order lock held, so 60 bids is 9-18 s in which fair prices move and Pause, STOP and bets wait) | none in tokens (1 per order either way) | **built** (v0.66.0, `NovigTradingClient.placeOrders / cancelOrdersBatch`, used by the bid desk when it has 2+; a refused batch falls back to single orders) |
| Novig private stream (`orders`: open / fill / cancel / reject, 1 token) | a fill, a reject (which no HTTP status reports) and a cancel the moment they happen, instead of a poll each pass | a second websocket to keep alive and reconcile (`seq`, snapshot on gap) | not built; the picked-off guard and the fill delay would be exact, but a poll every pass is already inside a minute. Worth it only if the Diagnostics show fills noticed late |
| PropLine `/sports/{sport}/ids` (free ESPN / MLB / book id crosswalk) | exact game identity for grading and matching | a new join | not built: team-name matching misses are not what the Diagnostics show; ask for the next file's "unmatched" lists first |
| PropLine `/odds/closing`, `/results`, `/ev` (Hobby) | see 90.4 | $9 a month | decision is Tj's, 90.4 |
| PinnWire `since=` (changes only), `/kit/v1/prematch/lines` | fewer bytes | none, but the limit is requests (100 a day), not bytes | not built (data is not a constraint) |
| PinnWire `generated_at` | the server's own time of the snapshot | the app's fetch time is already the safe reading | not built |
| ParlayAPI ETag on `/v1/meta/*`, `maxAgeSec` on props, `/ev`, `/best-bets`, `/scores`, `/events/canonical` | bytes; server-side age filter (it filters on time since the price CHANGED, which drops lines that are merely unchanged: PARLAY_API.md §5) | | not built |
| Kalshi `external-api` host, flat `/markets?series_ticker` (a prop series is 25 KB compressed, 0.18 s), `tickers=` bulk | a lighter read per series; a bulk read of exactly the markets Novig lists | needs a mapping from Novig games to Kalshi tickers (MatchWire sells exactly that; see 90.7) | not built; measured today: both routes answered 10 of 10 reads at 3-5 a second, but the nested route was refused 3 requests in (RESEARCH.md §36.2 saw the same), so a longer measurement comes first |
| Streams: ParlayAPI websocket / SSE (Business, $40), PropLine websocket ($39) | pushed price changes | plan | not worth it for a scan that prices on request |

### 90.6 How many requests Vigilant spends (CO3)
A Vigilant scan, per league: PinnWire / pinnapi 1 request a sport (NFL and NCAAF share), PropLine 1 board request a league plus up to 12 props games (each re-used 2 minutes), ParlayAPI 3 credits a league for odds (+2 for alternates when no Pinnacle feed answers) and 3 for props, The Odds API only for what PropLine missed, Kalshi 19-57 series a league at 2 a second (~15-27 s, free), Polymarket 1-2 pages (free). The background Vigilant scan starts at most every 4 minutes (360 a day if it ran all day). With four leagues, all day:
| Source | Spent at 360 scans a day | Allowance | Share it can serve |
|---|---|---|---|
| PinnWire / pinnapi | ~1,080 requests (3 sports) | 100 a key a day | ~9% a key |
| PropLine | ~5,760 requests (16 a scan) | 1,000 a day (Hobby 5,000) | ~17% (87%) |
| ParlayAPI | ~8,600 credits a day | 20,000 a month, ~645 a day | ~7% (`CreditPace` rations it) |
Reading the code for waste found none in how a single request is made: a board is shared between NFL and NCAAF, props are re-used for the freshness limit and no more, a standing-by fallback spends nothing, a rested key is not asked, ParlayAPI's account read is free and at most once a minute, closing-line capture reads only bets about to start, and grading reads scores only for finished games. The spend is cadence x leagues x sources, so the levers are Tj's: fewer leagues in the off-season (a league with no Novig games still costs a board request on every source), a slower background interval for sources with small allowances, and extra free PinnWire / pinnapi keys (each key is its own 100 a day). The Diagnostics' network block has the real per-host counts; the next file settles which allowance runs out first.

### 90.7 Can MatchWire help match props, or save other APIs' usage? (CO7)
Re-read in full today (its `llms.txt` and docs, not a summary): **it does match props**, line by line ("winners, spreads and handicaps, totals and props are matched too, and the line is part of the match"; each market carries `family, period, stat, line, subject`), but only **across Kalshi, Polymarket US, Polymarket International and Predict.fun**. It has no sportsbook and no Novig, so it cannot map a Novig player prop to a Pinnacle, PropLine, ParlayAPI or Odds API prop, and those are where matching is hard (player-name spellings, stat names, the line). It sells mapping only; every price is still read from the venue, so it cannot reduce any API's usage: Kalshi and Polymarket are free and unmetered, and the metered ones (PinnWire, PropLine, ParlayAPI, The Odds API) are not touched by it. Its one real use here would be to name, for each Novig game, the exact Kalshi tickers, so a scan could ask Kalshi for those markets and not sweep every series (the 15-27 s Kalshi step) — a speed gain on a free source, for a plan whose price is not published. **Verdict (unchanged from §88.6, now with props checked): no; not for matching, not for usage.** The way to find the props Vigilant misses is its own report: the next Diagnostics file lists unmatched games by league, and the scan study's "no fair" rows list the props; send them and the matcher (`TeamMatcher`, `PlayerNames`, `PropStats`) is taught from real misses.

### 90.8 What was built from this audit (v0.66.0) and what was left, with the reason
Built (each with its test): (1) **Novig batch place / cancel** in the bid desk (`NovigTradingClient.placeOrders` / `cancelOrdersBatch`, `MakerDesk.placeBatch`): a pass that posts or takes down 2+ bids does it in one request each way, so the order lock is held ~0.3 s instead of ~0.2 s a bid, which keeps Pause, STOP and the fair-price moves from waiting behind a long pass. Safe by construction: a refused batch (`400`, nothing placed) removes the bids' records and falls back to single orders; a batch whose answer is lost is looked up by client id before anything is re-sent (the batch is not idempotent); an answer the client cannot read turns batches off for the run and uses singles (tests `MakerTest` x6, `MakerOrdersClientTest` x3). (2) **Kalshi alternate host** (above). (3) **PropLine board re-use 30 s in Pinnacle only** (was 2 min): PropLine's Pinnacle is 2-3 s stale, and in Pinnacle only it is the backup for Pinnacle's own price, so a 2-minute-old board was the oldest price in the mode; 30 s costs at most 4 more requests an hour a league against the free 1,000 a day (`PropLineClientTest`).
Left, with the reason: the Novig private `orders` stream (a second socket to keep alive; a poll each pass is already inside a minute; build it only if the Diagnostics show fills noticed late), PropLine's `/ids` crosswalk (matching misses are not what the Diagnostics show), PinnWire `since=` and ParlayAPI ETag / `maxAgeSec` (the limit is requests and credits, not bytes; `maxAgeSec` filters on time since a price CHANGED and would drop unchanged lines), Kalshi `tickers=` bulk (needs a game-to-ticker mapping; MatchWire sells that but it is a speed gain on a free source), buying PropLine Hobby (not for grading: Novig settles; only if the API-usage meter shows PropLine hitting 1,000 a day before evening).


## 91. Is it wise to bid the Over and the Under of one prop? And the longest odds a bid may be posted at (v0.67.0, 2026-10-05; Tj, with a Bids-tab screenshot: "Is it wise to bid the under and the over for the same prop? If not, set a guard for it. Also make a settings options for the auto bid feature for me to select the longest odds for bids (for example, do not post bids longer than +140 odds)")

**What the screenshot showed** (21 bids, $54.55 up of a $55.19 wallet): Ottawa Over 2.5 team total at −127 (fair −141); **Tampa Bay Rays Over 3.5 at +170 (fair +157) and Rays Under 2.5 at +141 (fair +131)**, the same team total on two lines; Dallas Stars Under 3.5 at +113 (fair +103). Two different things can be meant by "the under and the over of the same prop", and they are different bets:

### 91.1 Same line, both sides (Over 3.5 and Under 3.5 of one market): wise, kept
- Each side is its own bid, a margin under its own fair, so each is +EV by itself and **EV adds** (E[A+B] = E[A] + E[B]; nothing about one bid changes the other's edge).
- **They can never both lose**: exactly one side of a market wins (a push on a whole-number line refunds both). If both fill, it is a lock: the two bids cost under $1 together and a contract pays $1 whichever side wins (the two margins, about 8% of the stake at the 4% default; RESEARCH.md §70.2 measured both filling in 2-4% of markets at a 4% margin, 17-22% at 1%). If only one fills it is a plain bet that the app's fair says is +EV.
- **The per-game limit counts it once**: `GameExposure` counts only the larger side of a market (§80.2), because the other side cannot also lose. Nothing is double-counted.
- **The data does not say the second side is worse than the first**: the price bands where a favorite side sits (bids 0.50-0.60) fill 10-16% and earn +0.0% / +1.9% / +4.1% at the close (fair stand-in w = 0 / 0.25 / 0.5, posted 3 h out); only bids at 0.60+ (fill 3-8%, −1.3% / −0.2% at w = 0 / 0.25) and game lines (§70.2, −0.3% at w = 0) are not worth the wallet, and the price window (0.10-0.65), the cheapest-first order and the "popular first" order already put those last when money runs out (`research/bid_focus_study_2026-10-05_output.txt`).
- What it does cost: **adverse selection**. When the fair moves on news, the side that moved against you is the stale one a taker hits, and the other side (now better than its price) rarely fills. Every bid carries that; two sides carry it twice as often (once per direction). It is inside the measured per-fill EVs above (they bid both sides) and it is what the picked-off guard (§88.3), the re-pricing every pass (a fair that fell moves the bid down at once) and the 10-minute freshness limit are for.
- "Wash trading" does not apply: a pregame bid earns no Maker Credit (NOVIG_API.md §17), either side may fill alone (bona fide market risk), and the two bids' prices add up to under $1 (below), so they can't trade with each other. The Ludlow Rulebook's abusive-trading text was not readable (still unverified, NOVIG_API.md).

### 91.2 Two lines of one prop (Rays Over 3.5 + Rays Under 2.5): fine, and it is one bet in disguise
- Over 3.5 wins on 4+ runs, Under 2.5 wins on 0-2, **both lose on exactly 3**, and they can never both win. By the app's own fairs: Over 3.5 fair 38.9% (+157), Under 2.5 fair 43.3% (+131), so P(exactly 3) = 100 − 38.9 − 43.3 = **17.8%**.
- Bought together at the bids' prices (+170 = 37.0¢, +141 = 41.5¢) the pair costs 78.5¢ and **pays $1 unless the Rays score exactly 3**: one binary bet on "not 3 runs", fair 82.2¢. EV +3.7¢ a pair (4.7% of cost) = the sum of the two bids' EVs, as it must be. The "hole" at 3 runs is the pair's one losing outcome, already inside its EV; holding Over 3.5 alone loses at 3 runs too.
- **Variance goes down, not up**: per contract each, the pair's variance is 0.146 against 0.238 for the Over alone and 0.246 for the Under alone (the two can't win together, so one hedges the other: the win indicators have covariance −pq). Kelly-wise, bets that can't win together deserve at least the stake they'd get alone. The real concentration risk is the opposite pairing, two bets in the SAME direction on one game (Over 2.5 + Over 3.5 + Over 4.5: one bet at three thresholds, §80.1), which the per-game limit already holds.
- So a rule that bars the pair would remove a +EV bid that also lowers the risk. **No guard was built for it.** Over 2.5 + Under 3.5 (both win on exactly 3) is the mirror case, a middle, also fine.
- **Honest limit**: both bids rest for the same few minutes against the same underlying (the Rays' runs), so one piece of news (a lineup, a scratched starter) makes one of them stale; that is §91.1's adverse selection, not a new one. If Tj still wants one bid per prop across all lines, it needs a prop key (game + player or team + stat, any line) that the planner doesn't have yet; the existing "Both sides of a market" switch does it per line.

### 91.3 The one real hole found: nothing stopped two of our own bids from trading with each other (built)
- Novig is one book per market: a bid on A at P is the same as an offer of B at 1 − P (NOVIG_API.md §7). **Two bids of ours on the two sides of one market that add up to $1 or more meet each other**: Novig sends a `fill`, the position doesn't change, nothing is earned (a wash; the Maker Credit terms also name self-matching orders). Today it can't happen while the two fairs add up to 1 (each bid is under its own fair/1.04, so the pair is under 1/1.04 = 96¢), and a post-only bid that would take is refused whole (whether Novig counts a cross with our own resting bid as "taking", or washes it, is unverified). But nothing in `MakerPlan` enforced it, and a fair that moves a long way between two passes (a stale 60¢ bid still up when the other side's new bid is 45¢) could meet it.
- **Built (`MakerPlan.wouldTrade`, `MakerPlan.WASH`)**: a new bid is held back when it and any bid of ours on the other side of its market add up to $1 or more. It counts every bid that may be on the book: the ones up, **the ones this very pass cancels** (a cancel can lag or fail; the stale bid is gone by the next pass), the ones whose cancel is already in flight (`onTheWay`, from `CANCELING` bids), and each bid the pass has already placed. It goes up on the next pass once the other is gone. The reason shows on the Bids tab's red "ready bids wait" line and in Diagnostics' bid line ("held back: 2 because it would trade with your own bid …"). Different markets (Over 3.5 and Under 2.5, the screenshot's pair) and the same side's own re-post never hold a bid back. Tests: `MakerTest` +3 (the boundary at exactly $1.00, both new in one pass, the cancelled and the coming-down bids, the screenshot's pair), 3 mutants killed (boundary, the in-flight list, the stale list).
- One existing test (`both sides of one market count as the larger side`) priced both sides at 50¢ (exactly $1: a wash), now 50¢ and 46¢ as a two-sided bid really is.

### 91.4 The longest odds a bid may be posted at (built)
- **Setting `makerMaxOdds`** (Settings › Bids › rules › "Longest odds a bid may be posted at"; chips +100 / +110 / +120 / +130 / +140 / +150 / +175 / +200 / +250 / +300 / No limit, or any number from +100 typed; **default No limit, so nothing changes until picked**; the auto-bet's own longest-odds setting, §66, is separate and untouched). +140 means no bid priced under 41.7¢ (100 / 240): the grid's 0.415 is +141 (skipped), 0.420 is +138 (posted). Favorites always pass. The number is the bid's own price, the odds the Bids tab shows first ("+170 / fair +157"), which is a margin under the fair, so a fair of +157 with a 4% margin posts near +170.
- **Every path obeys it**, because it sits in `MakerQuote.outsideWindow`, which both the early check (`precheck`, run on every line) and the final decision (`decide`, after the sharp-anchored price is worked out) call: automatic and recommended bids, re-posts, quick & likely (it narrows the window but keeps the limit), and the resting bids: a bid already up at longer odds is cancelled at the next pass with the reason "A bid at that price would be at longer odds than your +140 limit for bids" (the reason carries no price, so Diagnostics and the Bids tab count it as one reason). It skips; it does not raise the bid to the limit's price (that would take the margin away, a bid at 41.7¢ against a 38.9% fair is −EV).
- **Cost to know**: long odds are where the margin pays most per fill (bids under 0.20 fill 47-56% and earn +2.3% to +4.1% at the close, above) and where the app's fair is least reliable (the Auto-bet tab's own words for its longest-odds limit: "long shots are where fake edges hide"). A +140 limit gives up the underdog bids the research likes best; that is Tj's call, which is why the default is No limit.
- Shown in the Bids tab's summary line ("· no bid longer than +140"), in its rules note, in the settings search (`SettingsIndex`), and in Diagnostics' "Make orders / Bids" line. Saved with the settings (a file saved before it reads as no limit). Tests: `MakerTest` +6 (the boundary, favorites, the sharp-anchored price, the settings, the saved file, the bid already up coming down), `MakerUiTest` +1 (chips, typed amount, under +100 refused, No limit), `MakerAppTest` +1 (Diagnostics), 5 mutants killed in all with the wash tests.


## 92. Low-API-usage prop bids: sharp prop books only, the next 6 hours, a slow pace (v0.68.0, 2026-10-05; Tj: "make an option for a low API usage auto bid feature. this will only scan for current odds on all prop bets available in the games for the next six hours from 2 to 3 sharp books for props only … devig these odds to find fair odds and place bids at least 2.5% below (positive EV) the fair odds … should not waste api usage on scanning too frequently or scanning books other than the sharp prop books … the longest odds it should place bids at is +130 (no long shots), and make it place the types of bets most likely to be matched and filled … at least two sharp books … current and not stale … the sharp books must prove both sides of the prop bet")

### 92.1 What the app already had (read before building; nothing here is re-researched)
- Bids are priced from Vigilant's own scan (`MakerLines.from` → `MakerQuote.decide`): fair = the scan's fair line for the side, margin under it, anchored under the lowest sharp book's own fair (`makerAnchorSharp`), none within 15 min of the start, none on a fair past `Freshness.maxAgeMs` (5 min, 10 min for a game over 3 h away), each bid ends when its fair goes old. "Which bids go up" already has *All bids* and *Quick & likely to win* (`QuickLikely`: props and team totals, bids priced 0.30-0.60, the likeliest fills first).
- The scan already has a window (`startsWithinHours` → `scanWindowHours`, 6 h is a choice) and families (`PLAYER_PROPS` alone is possible), and Pinnacle only (§88.5) is the pattern for a narrowed scan: `ScanSettings.effective()` rewrites the settings at the Scanner's door, the app offers fewer sources (`referenceSources`), `Scanner.readable` drops any other.
- **Which books are sharp for PROPS, from this repo's own research (§65, §66.2, §76.4)**: NOT Pinnacle first. The 600-million-line-move MLB study (SmartStake, a vendor: one source) ranks Kalshi and ProphetX sharpest, Novig next, DraftKings / FanDuel middle, Pinnacle and Bookmaker softest (low prop limits); the NFL prop ecosystem (establishtherun) names FanDuel and Caesars as the efficient US books. `SharpVeto.ranking` (props) = Kalshi, ProphetX, FanDuel, Caesars (MLB: Kalshi, ProphetX, DraftKings, FanDuel). Pinnacle stays a choice (it is the free, one-request-a-sport feed) but is not the default.

### 92.2 The design (decided; the code follows it)
1. **A third choice under "Which bids go up": *Low API usage*** (`BidFocus.LOW_USAGE`), live whenever bids are Recommend or Automatic. Picked books: `lowUsageBooks`, 2 or 3 of Kalshi / ProphetX / FanDuel / Caesars / DraftKings / Pinnacle (default **Kalshi, ProphetX, FanDuel**); pace `lowUsageMinutes` (default **10**); margin `lowUsageMargin` (default **2.5%**, never under it).
2. **What it reads (the usage).** `ScanSettings.effective()` while the mode is on: families = player props only; window = the next 6 h (`startsWithinHours`, `bookPropHours`); fair = SHARP, devig = lowest of the four, `sharpBooks = referenceBooks = the picked books`, no average to fall back on, **at least 2 sharp books** (`FairSettings.minSharp`), quotes older than the freshness limit **dropped before the devig** (`dropStaleQuotes`: a stale third book no longer makes the whole line old); Polymarket / The Odds API / the soft books off. The feeds asked are the fewest that carry the picked books (`LowUsage.feeds`): Kalshi (free, direct), PinnWire / pinnapi for Pinnacle (one request a sport), PropLine props for FanDuel / DraftKings (free daily requests, one per game), ParlayAPI `/props` for ProphetX / Caesars (**3 credits a league, one call returns every picked book**, `bookmakers` = the picked ones). A metered feed is used for every picked book it carries once it is needed for one (ProphetX needs ParlayAPI, so FanDuel comes from the same call, not a second feed). **A league with no pregame game starting inside the window is not asked at all** (`LowUsageSource`: it was 1 request per source per league per scan even with nothing to bid on, §90.6).
3. **How often.** Vigilant's own background scan runs at most every `lowUsageMinutes` (the usual 4-minute gap otherwise, `AutoScanClock.vigilantDue`); the cycle itself (fills, expiries, the bids' housekeeping: Novig calls, no credits) is unchanged. A manual Scan / pull-to-refresh still scans on the mode's narrowed settings; bets-only passes (Check odds now) do not (they price Tj's open bets of every kind).
4. **Freshness vs pace (the honest trade-off).** A bid never outlives the fair behind it (`MakerQuote.precheck`'s `until`): 5 min inside 3 h of the start, 10 min before that. At a 10-min pace a game inside 3 h has its bids up about half the time; a game 3-6 h out is covered all the time; at 5 min everything is covered and the calls double. Nothing relaxes the limit to fit a pace.
5. **The bid.** `LowUsage.narrow` over `QuickLikely.narrow`: kinds = PROP only; price at or over 0.435 (**longest odds +130** = 100/230, never loosened, a tighter `makerMaxOdds` still wins) and at most 0.60 (QuickLikely's band: over 0.60 a bid almost never fills); margin `lowUsageMargin` (≥ 2.5%) under the fair, anchored under the lowest picked book's own fair (so every picked book gives the bid at least the margin); a sharp book required, ≥ 2 books agreeing, sharp veto on; games within 6 h only; kinds of prop Novig's takers measurably rarely trade (< $250 a listed market a day, `MarketPopularity`) are skipped — they are the least likely to be filled — and the rest go up by *leads their side → hottest market → likeliest fill → most edge*.
6. **Both sides proven.** `Pricing.bookPrices` already drops a book that lacks either side of the exact line; Kalshi's yes/no comes as bid/ask (over and under by construction); ParlayAPI `/props` rows without both `over_price` and `under_price` are not parsed. The mode adds the count: fewer than 2 fresh two-sided picked books = no fair = no bid.

### 92.3 Cost at the default pace (per day, a league with games in the window all evening, 10-min pace, ~6 active hours)
Kalshi 36 reads (free, ~20 s each); ParlayAPI 36 × 3 = **108 credits** per league (a 20,000-credit month is ~645 a day; background scans keep half, ~322: three leagues fit, four are held back by `CreditPace`); PinnWire (if Pinnacle is picked) 36 requests a sport against a free key's 100 a day; PropLine props one request a game per scan inside its 1,000 a day. At 5 min those double.

### 92.4 What was built (CQ2-CQ4, v0.68.0) and what to know
- **Where to find it:** Bids tab › Rules › "Which bids go up" › **Low API usage** (beside All bids and Quick & likely to win). It is live whenever bids are Recommend or Automatic; with bids Off the scan is the usual one. Controls: *Sharp prop books (pick 2 or 3)*, *Vigilant's scan runs at most every* (5, 8, **10**, 15, 20, 30 min), *Under the fair* (**2.5%**, 3%, 3.5%, 4%). The usual margin, kinds, sharp switches and popular-first controls are hidden while it is chosen (it sets them); *Longest odds* stays (a tighter limit than +130 is kept, the note says the mode's cap).
- **Code:** `data/scanner/LowUsageBids.kt` (books, feed plan, the scan profile), `ScanSettings` (`lowUsageBooks`, `lowUsageMinutes`, `lowUsageMargin`, `lowUsageNow`, `vigilantGapSeconds`, `effective(forBets)`, the transient `lowUsageScan` / `minSharpBooks`), `engine/FairValue` (`FairSettings.minSharp`), `Pricing` (stale quotes dropped before the devig), `Scanner` (bets-only never narrowed; catalog read for the window, not a week; sources limited to the mode's), `reference/LowUsageSource` (a league with nothing to bid on is not asked), `ParlayProps` (the picked books only), `maker/LowUsage` (`narrow`: the bid rules), `MakerRules.lowUsageBooks / skipObscure / focus`, `MakerLine.fairBooks`, `MarketPopularity.measuredObscure`, `VigilantApp.lowUsageSources / lowUsagePlan`, `AutoScan` (the gap), Diagnostics (`lowUsageLines`), `HealthChecks.lowUsage`, `BidReport` (splits), `MakerBid` (`focus`, `fairBooks`, `fairAgeSec`, `fairNewestAgeSec`).
- **Rules, as Tj wrote them → where enforced:** "next six hours" → `startsWithinHours` 6 + catalog window + `LowUsageSource` + trap guard 6 h; "props only" → `families = {PLAYER_PROPS}`, `kinds = {PROP}`; "2 to 3 sharp books" → `LowUsageBids.books` (2-3, ranked); "at least two sharp books" → `FairSettings.minSharp = 2` and `MakerRules.lowUsageBooks` (≥ 2 picked, nothing else, in the fair); "current and not stale" → `Pricing` drops a quote past `Freshness.maxAgeMs` (a quote with no time counts as stale) and each bid ends with its fair; "both sides" → `bookPrices` drops a book missing either side, ParlayAPI rows need both prices; "devig" → lowest of four, averaged; "at least 2.5% below" → margin ≥ `MIN_MARGIN` under the lowest picked book's fair; "+130" → `LowUsageBids.MAX_ODDS` through `outsideWindow`; "most likely to be filled" → `QuickLikely` (0.30-0.60, leads first, hottest market, likeliest fill) + `skipObscure`; "not scanning too often" → the pace gap and no request for a league with no game in the window.
- **Tests (54 new):** `FairValueTest` +4, `LowUsageBidsTest` 19, `LowUsageBidTest` 15, `LowUsageScanTest` 9, `MakerTest` +1, `BidReportTest` +2, `DiagnosticsTest` +1, `AutoBetDiagnosticsTest` +1, `AutoScanTest` +1, `MakerUiTest` +1. **14 mutants killed** (minSharp → any sharp; stale-drop off; a quote with no stamp counted fresh; +130 → +140; the 2.5% floor removed; the picked-books gate removed; the obscure skip removed; the window not applied; the pace gap ignoring the mode; a bets-only pass narrowed; the 3-book cap removed; the catalog horizon back to days; `readable` ignoring the mode; a looser limit of Tj's kept over +130).
- **Honest limits (tell Tj):** (1) *The pace vs the freshness rule*: a bid ends when its books' prices are 5 min old (10 for a game over 3 h out), so at the default 10 min a game inside 3 h has bids up about half the time; at 5 min always, with double the calls. (2) *ProphetX and Caesars exist only on ParlayAPI* (3 credits a league per read); without a key the mode says so (Diagnostics, health check) and prices from the other picked books, which needs two of them on the line. (3) *Kalshi is a thin exchange book*: its quotes are dropped when the spread is over 3¢ (`exchangeMaxSpread`), so it is often missing from a prop. (4) *The research ranking is one vendor study plus two articles* (§66.2): Pinnacle is offered but not default because its prop limits are low; switching the picks is one tap. (5) A manual Scan while the mode is on is the mode's narrowed scan (props only, 6 h, the picked books); turn the choice back to All bids for a full scan. (6) The first narrowed scan comes when the pace gap allows (tap Scan to start at once).


## 93. Why Low API usage bids all ended together and left a gap, and what keeps them up (v0.68.1, 2026-10-06; Tj: "the bids only stay up a couple minutes then they are cancelled and no new bids go up", then "It will put up many bids, then leave them a couple minutes, then cancel all of them at the same time. Is there a fresher source for prop odds from sharp books?")

**In short.** It was by design, and the design was wrong for what Tj wants. A bid lives until **5 minutes after the OLDEST quote behind its fair was read (10 minutes for a game over 3 hours away)**: `MakerQuote.precheck` → `until`. All the bids of one scan were priced from the same read, so they all ended at about the same moment, and the next scan (10 minutes later by default; Tj had set 5) only put bids up again after its own Novig reads reached each market. v0.68.1 fixes it without loosening a single one of Tj's rules (two fresh two-sided sharp books, 2.5%+ under the fair, +130 at most, props only, next 6 hours): the scan now starts again **just before the bids end** (Auto pace), and the markets that already carry one of our bids are read **first**, so the bid is rolled forward before it ends.

### 93.1 The mechanism, from the code and Tj's v0.68.0 file
- Kalshi and Pinnacle quotes carry no time of their own: `Scanner.seenBy` stamps them with the read time. ParlayAPI's `/props` rows carry `age_seconds`, read as `now - age`. A fair line's age is its OLDEST used quote (`Pricing`: `fairAsOfMs = usedUpdates.second`); the bid ends at that plus `Freshness.maxAgeMs` (300 s, 600 s beyond 3 h), never past 15 min before the start (`stopMs`) or 30 min (`makerTtlMinutes`).
- The desk re-posts a bid from a fresher fair only **within 2 minutes of its end** and only if the new one rests at least a minute longer (`MakerPlan`, "About to expire: re-posted", `MakerRules.REFRESH_BEFORE_MS`), and only when the scan's Novig read reaches its market.
- That read order is the second cause. `Scanner.fetchOrder` ranks a line by its EV at Novig's TAKE price (open bets first, then +EV, near misses, never priced, "well below zero" last). A market with a bid of ours resting on it is by construction one where taking is not +EV (our bid is under the fair, Novig's offer above it), so it sat in the last group and was read at the END of the scan: `d`, the delay from the scan's start to the bid being re-posted, was most of a scan.
- Tj's file (v0.68.0, 24 h): **2,085 bids posted, 112.3 bid-hours up, longest 9 min, "rested 3 min at the median, 6 at the 90th", median bid priced with a book 2 min old**; 847 ended "About to expire: re-posted" (the roll working) and 846 "The fair price goes old within a minute: re-priced at the next scan" (the gap). Fills 17 (11 prop), CLV +2.6% on 16 with a close: the bids are good when they are up.

### 93.2 The fix, and what it costs
- **Auto pace** (`LowUsageBids.AUTO`, the default; setting `lowUsagePace`, which replaces `lowUsageMinutes`: a saved 10 is NOT carried over, it was the default that caused this): Vigilant's own scan starts again `limit - 2 min` after the last: **180 s while a game a bid could still go on is inside 3 h of its start (the 5-minute limit), 480 s while every game is further (the 10-minute limit)**; 2 min is the desk's re-post window, so the next scan's bids land inside it. A game 3 h 8 min out already counts as near (it gets the short limit before the next far scan). Fixed paces stay as choices (5, 8, 10, 15, 20, 30 min) with an honest note. Code: `LowUsageBids.autoGapSeconds / gapSeconds`, `AutoScanner.vigilantGap` (the games come from the last scan's result), `MakerRules.REFRESH_BEFORE_MS` shared with the desk.
- **Resting-bid markets first**: `LowUsage.restingMarkets` are added to the scan's pinned set when the mode is on (`VigilantApp.startVigilantScan`), group 0 of the read order.
- **Tests**: `LowUsagePaceTest` replays one game's bids second by second through the real `MakerQuote.precheck` and the desk's re-post rule: the old 10-minute pace is down more than up with a stretch of at least 4 minutes of nothing; Auto's 180 s (near) and 480 s (far) have **no second without a bid**; a gap one minute longer, or quotes already 90 s old with a 90 s delay, leaves one; the bid still ends 5 (10) minutes after the oldest quote (no rule loosened). Mutants killed: gap +60 s, always-far, stop window ignored.
- **Cost** (3 credits per league per ParlayAPI call, which is how ProphetX is read): **60 credits per league-hour in the near stretch, 22 in the far one**. Starter's 20,000 a month is ~645 a day and the background scan gets about half (`CreditPace`): about **5 league-hours a day at the near pace**. Tj's runway check already says ParlayAPI is short at the old pace (3,303 used by Oct 5), so Auto will meet `CreditPace`'s hold-back on a busy evening and the bids then lapse until the next day's share: that is the real price of bids that never end, and the screen cannot hide it. **Levers, Tj's**: fewer leagues in the list (a league with no game in the window is already free), the fixed 8-minute pace for far games, or a plan: ParlayAPI Pro $20 a month = 100,000 credits (5x: ~27 league-hours a day), checked on its pricing page 2026-10-06.
- **A limit that stays**: no gap-free pace can be promised when a feed's quote is already old when read: a bid's life is `limit - quote age - delay`. Auto holds with up to 2 minutes of age plus delay (the test above); Tj's file shows book ages of 20 s to 3 min at posting, so a bid priced from a 3-minute-old ParlayAPI row will still end early and be rolled by the next scan. The Diagnostics' "bids posted / bid-hours up / longest" line is the check.

### 93.3 Is there a fresher source of sharp prop odds? (checked against each provider's own page, 2026-10-06)
| source | what it gives | cost | verdict |
| :- | :- | :- | :- |
| ParlayAPI `/props` (Tj's Starter) | every row has `last_update` and `age_seconds`; the docs call it "when ParlayAPI last wrote or observed that price" (the real age of that book's latest observation), `maxAgeSec` "bounds freshness ... observation age, not price-change age" | 3 credits a league | already used; the age is what the bids' life is measured from. (RESEARCH.md §90.5 noted `maxAgeSec` may filter on price-change age: the docs now say observation age; unverified on a live answer, so the app keeps its own age rule and does not use `maxAgeSec`.) |
| ParlayAPI WebSocket `/v1/ws/odds/{sport}` (`kinds=prop`, `bookmakers=`, `markets=`) | pushes changed rows; frames carry `last_update`, `price_age_s` (since the price MOVED), `line_changed_at_ms`; min push interval Business 1.0 s | **Business tier $40 (1M credits), "no per-frame charge"**; not on Starter ($5), Pro ($20) | the one real stream of ProphetX / Kalshi / FanDuel props among Tj's providers, behind a $40 plan |
| PropLine (Tj's 3 free keys) | the books' own times; its `/v1/freshness` showed Pinnacle 2-3 s old; "sub-second median push lag" for DraftKings, FanDuel, Fanatics, Kalshi, Polymarket inside PropLine; REST on every tier | free 1,000 requests a day a key; Hobby $9 (5,000), Pro $19 (25,000); **WebSocket and webhooks only from Streaming Lite $39** | REST polling is the only option under $39, and props cost one request a game |
| Kalshi websocket | public channels (`ticker`, `trade`) still need a signed session (an API key); `orderbook_delta` is private | free with an account key | the app reads Kalshi's public REST (no key) at 2 a second; a socket would need Tj's Kalshi key, and Kalshi is already the bids' first book (free, any pace) |
| ProphetX | no public developer API (partners and aggregators only) | n/a | only through ParlayAPI / aggregators |
| The sharp books themselves | Pinnacle closed its public API in July 2025; Circa, Bookmaker, FanDuel, Caesars have none | n/a | the resellers: PinnWire / pinnapi ($89-$229 + $89-$99 for a raw WebSocket), Tj has their free keys (100 requests a day each) |
**Verdict**: nothing cheaper than ParlayAPI Business ($40) streams the picked prop books; under that, the fresh source is the poll, and the cost of keeping a poll fresh is the credits above. A fresher quote would lengthen a bid's life by the quote's age (a minute or two of the five) but the roll-forward already removes the gap, so a stream is a convenience here, not the fix.


## 94. Tj's v0.68.0 file, Oct 5 8:25-8:44 PM: "it said it scanned but ... only took 1 second", "scanned for a while then abruptly stopped", "made no auto bids" (v0.68.1, 2026-10-06; Tj with the diagnostics file `vigilant-diagnostics-v0.68.0-2026-10-05-2044.txt` and a +EV tab screenshot)

**In short.** Nothing was broken in what the scans did; two things were broken in what the screens SAID, and a third, outside the app, did real damage for about two minutes. (1) The one-second scans are Low API usage reading nothing because no game with a prop market on Novig started in the next 6 hours: Monday Night Football and the 8:00 PM Yankees-Rays game were live (live games are never bid on) and tomorrow's games were over 6 hours out. Nothing was asked of any feed and no credit was spent, which is the mode doing its job, but the +EV tab said "Scanned just now · No +EV right now" as if the board had been priced. (2) The 32-second scan at 8:32:35 PM read 508 Novig prices with 0 errors: it finished. (3) At 8:21:34 PM Novig answered HTTP 451 ANONYMIZED_NETWORK (the carrier's shared address is on Novig's VPN/proxy list) and, from about 8:23, the phone had no DNS at all. (4) No bids since 8:15 PM because nothing qualified.

### 94.1 The evidence in the file
- **Last scan (8:41:42 PM): "took 0.5 s: board 0.4 s · fair odds 0.4 s (Kalshi 0.4 s, ParlayAPI props 0.4 s) · Novig prices 0 read · games 23 on Novig, 0 matched · 0 lines priced · Kalshi: 8 fetched, 0 games matched; ParlayAPI props: 4 fetched, 0 matched · errors: none"**. A real Kalshi read of 8 leagues is 15-27 s a league (19-57 series at 2 a second, §90.6); 0.4 s for all of them is no request: `LowUsageSource.hasPropsToBidOn` answered "nothing to bid on" for every league and returned an empty snapshot, which the scan counts as answered ("fetched") and which matches no game. The health check "Low API usage bids: the last scan priced no prop from the picked books" is the same fact.
- **The timeline**: 8:23:42, 8:24:46, 8:26:40, 8:31:04 PM "Vigilant scan finished in 0 s: 0 Novig prices"; **8:32:35 PM "finished in 32 s: 508 Novig prices (485 through the key), 0 errors, 15.9 a second, live feed held 53 of 53"**; 8:36:41 and 8:41:42 PM "0 prices". The plan holds markets only for games a fair feed matched, so a scan that is empty for the feeds reads no Novig prices. The 8:32 scan most likely priced a game that had just come inside the window and was about to start (props posted, 15-minute stop window for bids, or fewer than two fresh picked books), then began and left the window: **an inference** (the file has one line per scan, not the plan).
- **The pace was 5 minutes** (Tj had picked it: "scan at most every 5 min"): scans at 8:26:40, 8:31:04, 8:36:41, 8:41:42 PM, as set.
- **8:21:34 PM**: `/v3/limits`, `/v3/catalog/events`, `/v3/ws` and the book routes refused with HTTP 451 ANONYMIZED_NETWORK; the key route stood down 30 s then 120 s (reads went to the public routes, 429 Retry-After 1 ×25 at 8:31:55 as a result); **`/v3/orders` refused 451 ×5 (8:21:43-8:22:55 PM): during that time a bid could be neither posted nor cancelled.** STOP ALL at 8:21:49 PM and Resume at 8:22:19 PM (Tj), STOP at 8:32:46 and Resume at 8:32:48. The app's message says it: "it judges the address, not the phone: ... it's the Wi-Fi's or the carrier's shared address that's listed. Try the other connection". Also 7:33-7:34 PM: "Unable to resolve host api.novig.com" and the CNO pages unreachable.
- **8:23-8:24 PM**: `cloudflare-dns.com` and `dns.google` 100% failed (105 of 105 each): the app's own DNS-over-HTTPS fallback, which only runs when the system's DNS has failed; both failing means the phone had no route at all (a 5G dead spot or handoff), not that the fallback is broken. `parlay-api.com` "dns after 1 ms", `api.novig.com` "dns", CNO "Host unreachable": the same minute.
- **Bids**: 3,012 posted, 17 filled, **0 resting now**; the last fills 7:09-7:25 PM were on the MNF game (49-65 minutes before its 8:15 start). "Auto-make switched off ×97 in 24 h" are the bids taken down by the 8:21:49 STOP.

### 94.2 What was changed (v0.68.1)
- **+EV tab**: with Low API usage on and nothing in the scan's window, it says "Low API usage: nothing to read right now: no game with a player-prop market on Novig starts in the next 6 hours (games already under way aren't bid on), so this scan asked no feed and spent nothing: that is why it took a second. It reads again every few minutes and starts as soon as a game comes inside the window." When it did read, the "No +EV right now" text adds that this tab lists bets to TAKE at Novig's price and that the bids (priced under the fair) are on the Bids tab (`LowUsageText.nothingToRead`, `TAB_NOTE`).
- **Scan timeline line** (`AppRecorder.scanLine`): a scan with no market in its window now ends "· Low API usage: no game with a prop market on Novig in the next 6 h, so no feed was asked and nothing was spent" (or "· no market in the scan's window to price" outside the mode), so the next file tells an empty scan from a broken one.
- **Not changed, by design**: the 451 and no-DNS stand-downs (the app already stands down, says why and retries; it cannot change the carrier's address). Worth knowing: bids cannot be cancelled while `/v3/orders` is refused, so a resting bid keeps standing for its (at most 5-10 minute) life; Wi-Fi, or the other connection, is the fix Novig's own message gives.
- **Why no bids**: after 8:15 PM every game on the board was either live or over 6 hours out. This is expected; the first games to come inside the window bring bids again.


## 95. Live betting on Novig: are Tj's feeds fast enough, is there a cheap one, and does the score burst replicate? (2026-10-06; v0.69.0 recorder, v0.70.0 trader; Tj: "investigate if I have any apis or if there are any free sources that are fast enough that I can profit from live betting on moving [odds] ... a cheap API around 20 dollars or less ... in your earlier research you found a way to profit on novig live betting directly after a score ... See if this is plausible to replicate", then "Build a no orders recorder of the score burst idea ... It must prove to be able to profit on my current system and app. Also, does it work in sports other than NFL?", then "make it good enough so that if it is proven I can just turn it on for actual money betting")

**In short.** (1) **Nothing Tj has, free or at $20, is faster than Novig itself on a live game, so there is no "see the play first" edge to buy.** Measured on Monday Night Football (Oct 5, ATL @ NO, 132 plays): ESPN's free feed published each play a median **40.7 s** after it happened; Novig's moneyline had already moved a median **16.1 s** after the play on the 23 plays that moved it 3¢ or more (ESPN was first on 9 of 23); Kalshi was level with Novig (Novig first by a median 1.8 s; Kalshi first on 7 of 18, by 2 s or more on 3). The paid options at or under $20 are slower or no faster than that (95.2). (2) **The score burst is real and replicated**: 8 bursts in that one game on the public tape (95.3), 0.10-11.8 s long (median about 1.5 s), on the moneyline-against-spread pairs and the totals. But they are **worth cents per $1 of payout (1.1% of payout, about $15.64 on the 143,179 contracts that traded if every one had been caught at its first print)**, and the tape only sees bursts somebody TRADED: **someone is already taking them**. (3) **Whether Tj's phone and key can catch them is not knowable from here**, so the app now has a no-orders recorder that measures it on his own setup (v0.69.0) and, behind it, a real-money trader that stays locked until the recorder says "worth a $1 test" (v0.70.0): both legs as one batch of two immediate-or-cancel orders. **Honest limit: the recorder proves nothing about profit by itself** (it is paper; it cannot see a rival's speed or Novig's reaction to a late order); the first real order is the real test.

### 95.1 What Tj already has, and how fast each is on a LIVE price (measured or from the provider's own page, 2026-10-06)
- **Novig's own signed websocket (his key)**: `book` pushes the changes as they happen: the fastest source there is for Novig's price, and the only one that sees the stale quote itself (not just what traded). Used by the recorder (95.4). 120 ms signed round trip measured on his phone (the v0.60.0 file: `/v3/orders` averaged 121 ms).
- **Kalshi** (free, public): level with Novig on MNF (above), so it confirms a move, it does not lead one. **Polymarket**: not measured in this test. **ESPN** (free scores): 40.7 s late (p10 32.4, p90 50.0): useless for betting ahead of the market, fine for settling. **CNO** 13-33 s (§83). **PinnWire / pinnapi** (free, 100 requests a day): a day's worth is under two minutes of polling a game. **PropLine / The Odds API free tiers / ParlayAPI Starter**: credit-limited polling (PARLAY_API.md and §43 have each one's numbers), not built for sub-minute in-play.
- **Result**: nothing he has can tell him about a play before Novig's makers price it. The only edge that needs no outside information is the one inside Novig's own ladder (95.3), where the "feed" is Novig's own books.

### 95.2 Cheap APIs (<= $20/month): none leads the market
The argument does not need a price list: Novig's makers already move the moneyline a median 16 s after a play, and a free read of ESPN is 41 s behind the play. For a paid feed to give an edge it would have to publish a play's effect within a few seconds of the play, which is what real sportsbook data contracts (the Sportradar / Genius / OpticOdds class) sell and a $20 plan does not (general market knowledge, not re-verified on the providers' pages tonight); what sits at $20 is polled odds every 5-60 s under a request or credit cap, which is no earlier than the 16 s Novig already shows. **Recommendation: do not buy one for this.** (§93.3's table of sharp prop-feed freshness stands for pregame props.)

### 95.3 The burst on Monday Night Football (public trades tape, `tools/research/novig_ladder_tape.py`, 2 ladders, 9,653 prints, 150 minutes)
- **8 bursts** (pairs of executed trades on two lines of one ladder within 1 s that cost under $1 after the in-play fee): 2.11, 0.99, 0.12, 4.67, 2.02, 0.10, 11.79 and 0.15 s long; six on the margin ladder (the moneyline against the ±1.5 or -2.5 spread, once the -1.5 against the -2.5) and two on the totals (46.5/47.5, and 48.5/49.5/50.5/57.5 together in one 2 s burst). All three moneyline jumps of 4¢ or more had a burst within -2..+4 s of them.
- **Guaranteed floor if every burst were caught at its first print: $15.64** on 143,179 traded contracts ($1,431 of payout: **1.1% of payout**), adding the chance the margin lands between the two lines (3%, paying $2) +$42.95 expected (the paper trade ignores that lottery on purpose). A reaction time of 0.1-0.3 s keeps 75% of the floor ($11.69), 0.5 s 74%, **1.0 s 57% ($8.91), 1.5 s 50%** (a burst's half-life is about 1.5 s; the shortest are a single print).
- **What it means at Tj's size**: 1.1% of payout at $10 a leg (about $20 of payout at a 50¢ price) is about **$0.22 a cover, at most about $1.80 a game if all 8 were caught**, about $1 at a 1 s reaction. At the $1 test it is about $0.02 a cover. The depth is there (up to 30,000 contracts in one burst); the money is only worth it at stakes ten times Tj's limits, and a rival is already taking some of them.
- **Outside the bursts**: the nearest cover costs a median **1.150** (5th percentile 1.000, 0.9% under $1): the ladder is consistent almost always, the windows are rare.
- **Replication**: §83.5 found 9 bursts and $6.56 in 2.4 hours; tonight 8 bursts and $15.64: the same order of magnitude on a different game. It is not a one-off.

### 95.4 What was built to find out (CU2-CU5, v0.69.0): the no-orders recorder
`data/novig/burst/`. A **second websocket on his READ key** (it cannot place orders: its source files are grepped by a test for any order word; nothing it is given can trade) subscribes `book` on every moneyline, spread and total of the live games of the leagues he picks (82 markets a game, 16 tokens each against the 512-token bucket). On every book change it looks at the pairs of lines of one ladder: YES at the lower line plus NOT at the higher costs `(1 - best bid on the other side)` each, net of 0.03·P·(1-P) a leg; a **window** opens when a pair pays at least 0.3¢ per $1 on 100 contracts or more, and ends after a 150 ms grace. Each window is logged and **paper-traded at his own delays**: his signed round trip (`POST /v3/echo`, free) / 2 + 15 ms signing + the push delay (a fill's removal from the book against the same trade's engine time on the public trades route), at three profiles: none, typical (median) and slow (95th percentile). A leg is filled only at the seen price or better, to what is on offer and his per-bet limit; both legs = the cover's profit; one leg = a loss (its fee and 2¢); none = 0. The **verdict** (Settings › Diagnostics & about › Live burst recorder, and the share file): NEEDS DATA until 3 games and 10 windows; NOT CATCHABLE if under 25% of windows still have both legs at the slow delay or the paper P&L is not positive; TOO SMALL under $0.50 a game or positive in under 70% of games; **WORTH A TEST** otherwise.
- **What it can prove and what it cannot** (the file says so every time): it proves what a window looks like on this phone, key and network and how long it lasts against HIS delays. It cannot prove a profit: no order is sent, so it cannot know a rival's speed (a faster taker may already own the cover), what Novig does with an order that arrives late (the in-play delay, price band, `NOT_LIVE_TRADABLE`) or that both legs fill together.
- **Container limit**: no Novig key here, so the websocket on the read key, the echo and the trader have only been run against fakes; whether Novig allows a second concurrent connection on one key is undocumented (the status line says why if it refuses).

### 95.5 The real-money trader (CV1, v0.70.0): off, locked, and tiny until it has earned more
`data/novig/trading/burst/`. **OFF by default and not switchable until the recorder's proof**: delays measured (20 round trips and 20 push delays, not assumed), and **a league that has proved itself on its own windows** (that league's row, over only the windows the trader would act on, those that paid 1¢ or more when first seen, reads WORTH A TEST: 3 games and 10 windows in that league; football's windows prove nothing about hockey's, and two thin leagues do not add up to one), the trader trading only the leagues that did; then it is Tj's switch behind a confirmation in his own numbers; and the proof is re-read (30 s cache) before every window, so a lapsed proof stops it. Per window: re-read the live books (a window older than 250 ms is dropped), skip if it no longer pays 1¢+, if his own resting bid could trade against it (a wash that pays two fees), or if a cap would be passed; size = the least of **his stake a leg ($1 default; chips $1/$2/$5/$10; never over $10 whatever is saved)**, what is on offer on both legs and what his game ($5) and day ($10) caps leave (fees in); at least 20 contracts; send **ONE batch of two `IOC` orders** at the seen asks (all or nothing, one round trip; the two fills are independent); wait for both to end (2.5 s), read the fills; **a leg that filled alone is bought out once at the break-even price**, else it is a held bet, counted, and **two covers in a row ending that way, or his loss limit ($3 a day), halt it until he taps Resume**. A lost answer or an incomplete one also halts ("nothing is assumed"). 451 or 423 stands it down 10 minutes; a market Novig refuses is left alone 10 minutes; STOP ALL, a pause, no betting key or a wallet under 2.2 stakes stop it; it shares the app's one-order-at-a-time lock with the auto-bet and the bid desk (an attempt that cannot take it at once is skipped). It never rests or cancels an order (a test greps the source). Its legs are NOT Tracker bets (one leg of a cover always loses; the Tracker would read that as a loss): `files/burst-trades/` and the share file's REAL-MONEY TRADES are its record, and Diagnostics shows its totals and what it held back, by reason.
- **Tested** (24 mutants killed, 1 survivor killed by a new test): every skip reason, one batch of two IOC at the seen asks sized to stake / depth / caps, the hedge, the halts, the stand-downs, the lock, the gate, the proof, the UI lock and confirmation, the per-league proof (v0.70.1). **Not testable here**: the first real order (the in-play delay, the price band, IOC in play on every game line, 451s on a carrier address). Hence $1, a halt on the first surprise, and no automatic turn-on.
- **Limit that cannot be engineered away**: at $1-$10 a leg the expected profit is cents a game (95.3). If the recorder proves the idea, the next decision is Tj's: raise the stake (the depth allows it) or leave it as a measured curiosity.

## 96. Does the cross-line burst work outside the NFL? (2026-10-06; CU1; Tj: "does it work in sports other than NFL?")

**In short.** **Unproven, and the structure says it is an NFL / basketball idea, not a baseball or hockey one.** What was measured tonight is thin (no basketball burst, baseball and hockey ladders were consistent); the answer that will be real is the recorder's own per-league totals on Tj's phone (it watches NFL, NCAAF, NBA, WNBA, NHL and MLB by default and reports each league on its own line). Do not read "0 bursts tonight" as "none exist": tonight had two NBA preseason games, one MLB game and one NHL game, with 1,722 prints in all.

### 96.1 Measured (the same tape tool, public routes, Oct 5-6)
- **MNF (NFL)**: 8 bursts, 9,653 prints (95.3); the nearest cover outside the bursts cost a median 1.150 (5th percentile 1.000).
- **NBA preseason (SAC @ LAL, MIN @ MIL), NHL (SJ @ DAL), MLB (NYY @ TB)**: 6 ladders, 1,722 prints, **0 bursts**, no moneyline jump of 4¢ or more, and the nearest cover outside bursts cost a median **1.33** (5th percentile 1.05, 0.0% under $1, n=53); an earlier look at the MLB ladder alone found its nearest covers at a median 1.24. Too little play and too few prints to say anything about basketball in season.
- **What could NOT be measured**: the 29 days of Novig trade files cannot answer it: they carry the league, market and trade but no game (event) and no line (strike), so a ladder cannot be rebuilt from them. Only a book or trade tape that knows each market's game and line can (the recorder's catalog does).

### 96.2 Why the structure matters (a cover pays when a stale quote is wrong by more than the chance the margin lands BETWEEN the two lines, plus fees)
A cover of two neighbouring lines is fair at `1 + P(margin lands between them)`, and the two taker fees add about 1.5-2¢ more. So a window needs a stale quote that is wrong by at least that much, and the room differs by sport (typical base rates, approximate, NOT measured here):
- **NFL, NCAAF**: the moneyline against the -1.5 spread is `P(margin = 1)`, about 2-3%; adjacent totals half a point apart about 3-4% (more on a key number such as 3 or 7). Narrow band: a 4-5¢ error after a score opens a window (as seen).
- **NBA, WNBA, NCAAB**: spreads and totals a point apart are about 2-3% of the margin each: also a narrow band, and far more plays (every possession) so many more chances: but each play moves the lines by only a fraction of a point, so the errors are small. The most promising sport after football to MEASURE.
- **MLB**: `P(one-run margin)` about 25-30%: the moneyline against -1.5 costs about 1.25-1.30 at fair, in line with the 1.24 the MLB ladder showed at the median. A window needs a 25¢ mistake: a grand slam or an ejection, rare and priced fast.
- **NHL**: one-goal margins about 35-40%, adjacent totals about 15-20%: even wider; no window short of a huge instant jump.
- **Tennis, MMA, soccer 3-way moneylines**: no margin ladder of this kind, so no cover of this kind.

### 96.3 What to do about it
- The recorder is on all six leagues by default; leave them all on for a few weeks: **each league's own line in the share file (games, windows, how long they last, paper result at his delays, VERDICT)** is the answer to this question on his setup. Take a league off in Settings only to save battery.
- **The real-money trader is judged league by league (v0.70.1)**: it trades a league only when that league's OWN windows made the verdict WORTH A TEST (3 games, 10 windows, positive at the slow delay, over the windows of 1¢ or more), so a football proof never unlocks hockey and a league with thin data stays locked. The Settings line names the leagues that have proved themselves; the confirmation dialog says the others are left alone.

## 97. The v0.70.1 diagnostics file and scan study: what they show (2026-10-07; CX1, CX2, DA1; Tj sent both files from his phone with no words)

Full report: `research/scan_study_analysis_2026-10-06_v0.70.1.md`; numbers behind it: `research/v0701_partial/*.json` (one file per agent; the runbook that produced them: `research/RESUME_v0701_ANALYSIS.md`). **Scope:** 9 study + 5 diagnostics analysts and 3 strategy builders ran; of the 10 best of 24 candidate rules, rules 1-8 were tested by three lenses each (reproduce, luck, feasibility); rule 10 and the 14 weaker candidates were not; no synthesis or critic pass was run (Tj's cost call, 2026-10-07: the remaining verifiers were the two weakest rules).

1. **No proven edge yet.** All-bets CLV +0.19% [-0.37, +0.81] (683 closes, 69 games) and ROI +1.58% [-2.0, +6.3] (1,705 settled, 81 games) are both zero within error. The study is 2.3 days, 73-79% NFL, 78% of closes from one 29-game day, and only 33% of bets have a close (a selected set: correcting for that gives -0.07..+0.17%).
2. **Time to the start is the one robust signal**: first listed <= 6 h: +1.41% [+1.01, +2.00] (256 closes); 6-24 h: about -0.4%; > 24 h: -5.07% (15 closes). It holds within a game, on both date halves, and on closes of bets Tj never placed. The code's trap guard default is 6 h; the phone ran 24 h.
3. **Listed EV is real signal** (slope +0.66 CLV points per EV point; break-even listed EV about 1.3%), but only inside 6 h: EV >= 2.5% and <= 6 h: about +3.0%; the same EV listed earlier is zero.
4. **Every add-on gate is unproven or refuted**: their CLV leans on Tracker closes (a re-read of CNO's own consensus, existing only for Tj's own bets), so CLV there equals the listed EV; on independent closes (ESPN, Pinnacle, Novig last trades of 3+) the 6 h / EV >= 2.5% family is about +0.6..+0.8%. Late sniper, stack, wait-and-confirm, falling edge, plus-money-only, EV-gated entry: all refuted as rules; the 6 h guard is the one credible, existing setting (it cuts about half the volume; its clearest argument is tail risk, 1 of 149 vs 13 of 145 placed bets closing 10+ points worse, p 0.0007).
5. **Hidden bets, sharp books, bids**: hidden bets are not where the profit is (and 31% are listed later at a better price); a "require a sharp prop book" rule has no CLV benefit (PASSED - VETOED +0.88 [-1.05, +2.8], sign flips by half); 17 bid fills beat the close by +2.6% each but in only 6 games.
6. **Diagnostics file**: stable and honest about the network (the DoH failures are the phone's no-route minutes); real faults: Novig's batch-place reply is unreadable on the first batch of every run (DA7: log its shape), the study export's close denominator and 90-character reason cut, a 120-line timeline cap, several misleading health lines, a 5-minute websocket retry after a Novig 451.
7. **Nothing in the app's betting rules was changed**; eleven proposals wait for Tj's yes (report section 4), each with the number of games that would settle it.

## 98. Can Apify (apify.com) supply real-time live scores for Vigilant? (2026-10-06; CZ1; Tj: "research apify.com and if it can be used for real time live scores")

(§97 is the v0.70.1 scan-study analysis.) Every figure below was read on Apify's own pages on 2026-10-06 unless it says otherwise; nothing was run on Apify (this container has no Apify token and no live game).

**In short.** Apify is a hosted scraping platform, not a data feed: it runs other developers' scrapers ("Actors") on demand and hands back what they read. It *can* return live scores, but it cannot make them fresher than the site the Actor reads, it adds a run start-up and a polling interval of its own, and it charges per row. **Not usable for the one thing that would make live data worth money here (seeing a play before Novig's makers price it): the bar is 16.1 s (§95.1) and nothing on Apify's pages claims any latency number at all.** For grading and "is this game live", ESPN read directly is already free and does it. **Recommendation: do not use it for live betting; if Tj wants the open question answered, a cents-sized test exists (98.7) and needs his own Apify token.**

### 98.1 What it is and what it costs (apify.com/pricing)
Actors run in containers billed in compute units (CU). Free: $0, $5 of monthly prepaid credit, no card. Starter: $19 a month with $19 of credit. Both $0.20 per CU, residential proxy $8 per GB. Scale $199 ($0.16/CU, $7.5/GB), Business $999 ($0.13/CU, $7/GB). Store Actors bill either "pay per event" (the author's fixed price per result) or "pay per usage" (CU, data transfer, storage); both draw on the prepaid credit. (Apify's pricing page says the Free plan allows 5 concurrent runs; its limits page says 25. Unresolved, and irrelevant here.)

### 98.2 The live-score Actors, read from their own pages
| Actor | What it reads, and how | Price | Users | Freshness claim |
| :- | :- | :- | :- | :- |
| `statanow/flashscore-scraper-live` | Flashscore's "All Games" view by **browser automation**; batch runs or Standby; score, status, minute, kick-off, odds, a history of goals / cards / substitutions | "From $0.002 / result" | 579 total, 22 monthly | none: "does not guarantee real-time updates or specify refresh intervals" |
| `bovi/sofascore-live-events` | Sofascore's web JSON endpoints **through Apify residential proxy, falling back to a browser**; `live` mode returns the in-play events (the whole sport list, then today's schedule when nothing is live); 18+ sports incl. American football, basketball, hockey, baseball; score, period, `live_minute`, status | "$1.84 / 1,000 events" | 121 total, 14 monthly | the page says live scores "reflect real-time page data, making it suitable for in-play betting models"; **no number** |
| `rl1987/espn-api-scraper` | **ESPN's own** unofficial, public, unauthenticated mobile-app data API; `gameSummary` mode gives one `scoringPlay` row per score, not every play | "from $1.00 / 1,000 output rows" plus usage; "typically a few seconds and well under 0.01 CU per run" | 0 monthly | none; batch only |
Several more ESPN wrappers (`bright_oven/espn-scoreboards`, `rowfeed/espn-sports-data-scraper`, `mrbridge/...`, `ninhothedev/...`, `aurenic/espn-scraper`) turned up in the Store search at about $0.95-$2.00 per 1,000 rows; I did not open them (search-listing figures only). The Flashscore and SofaScore Actors are community-built and small.

### 98.3 How fresh can it be?
- **The source's lag is untouched.** The ESPN Actors read the same ESPN API Vigilant already reads free, which published each play a median **40.7 s** after it happened (§95.1). A wrapper cannot be fresher than what it wraps. Flashscore's and Sofascore's lag behind a play is **not measured** (no token, no live game here).
- **Batch run**: a container start per run; Apify's pages give no start-up figure (the ESPN Actor says "a few seconds" per run). `run-sync` waits up to 300 s and answers 408 after that.
- **Schedules**: "The minimum interval between runs is 10 seconds; if your next run is scheduled sooner ... the next run will be skipped"; runs fire "within one second of their scheduled time" in most cases. So a polling floor of 10 s before the run even starts.
- **Standby** (the Actor stays warm as an HTTP server): the docs give a "5 minutes" overall timeout with up to "2 minutes" possibly spent selecting or starting a run before a request is handled; idle runs bill like normal runs until the idle timeout; per-account rate limits answer 429. **No warm-latency number** in the docs or in Apify's launch post.
- **API**: 60 requests a second per resource, 250,000 a minute global.
- Chain for one score: the site's own lag + proxy + run or warm HTTP hop (seconds, unspecified) + the poll interval (>= 10 s on a schedule). Nothing here can beat Novig's 16 s median unless the source is far faster than ESPN *and* the hop is a few seconds; neither is shown.

### 98.4 What it would cost to poll like Vigilant would need to (arithmetic on the listed prices, not billed)
10 live games polled every 10 s is 3,600 rows an hour: Flashscore Actor **$7.20/h**, Sofascore Actor **$6.62/h**, an ESPN wrapper **$3.60/h**, each plus platform usage. A full NFL Sunday (15 games, 13 h, an upper bound since not every game is live all day) is 70,200 rows: about **$140 / $129 / $70**. A Starter plan's $19 would last about 2.6 h at the Flashscore rate. The free ESPN read the app already makes costs nothing. (The `live` mode of the Sofascore Actor returns every in-play event of every sport it is asked for, so real rows could be higher.)

### 98.5 Terms (what I could and could not verify)
- **Flashscore**: the published terms of Livesport Media Ltd (read on the `.ae` site's page): "Visitors are not authorised to copy, modify, tamper with, distribute, transmit, display, reproduce, transfer, upload, download or otherwise use or alter any of the content" without written authorisation. That page does not name scrapers or offer any API. (Only the `.ae` terms were read.)
- **Sofascore**: its terms page answered **403** to my fetcher: **not verified.** A search summary said Sofascore bars scraping and does not license an API, but the FAQ page it cited does not say that; I treat it as unknown.
- **ESPN**: the Actor's own page calls the API "unofficial, public, unauthenticated"; Vigilant already uses it the same way (§90).
- Apify itself does not clear a source's terms for the user; that is on whoever runs the Actor (not re-fetched here).
- An Apify token on the phone would be a secret in a public repo's app: it would have to be entered by Tj in Settings like his other keys, never committed.

### 98.6 Where Apify could still help
Not live betting. Possibly **grading coverage**: the data-quality analyst of the scan study found 62 started bets still PENDING that nothing could grade; if those are leagues ESPN's scoreboard does not carry, a Sofascore-style Actor could fill them. That is a lead only: it waits on the synthesis (CX1/CX2), and a cheaper fix may be a second free grader.

### 98.7 What would settle the one open question (cents, needs Tj, nothing built)
A free Apify account ($5 credit, no card) and an API token Tj creates himself. During one live game, call the Sofascore and Flashscore Actors (Standby, polling every 10 s) while the existing burst recorder's tape logs when Novig's moneyline moves; compare each source's lag per play with Novig's 16.1 s median, hop included. At a few cents a run it costs a few dollars for a game. If a source's median lag, hop included, is well under 16 s, revisit; if not, stop. **Say "build the feed-lag test" and it becomes a normal task; until then nothing is built, no key is asked for and nothing is spent.**

Sources (fetched 2026-10-06): https://apify.com/pricing , https://docs.apify.com/platform/limits , https://docs.apify.com/api/v2 (rate limiting), https://docs.apify.com/platform/actors/running/standby , https://blog.apify.com/actor-standby-mode/ , https://docs.apify.com/platform/schedules , https://apify.com/statanow/flashscore-scraper-live , https://apify.com/bovi/sofascore-live-events , https://apify.com/rl1987/espn-api-scraper , https://livesport.eu/terms/flashscore_ae .

## 99. Can Vigilant have its own rapid live score or odds feed, fast enough to catch Novig's stale quotes right after a score? (2026-10-06; DA2; Tj: "research if there is any way to build my own rapid live odds or scores feed for this app to use for live betting opportunities, especially what you found before about the market inefficiencies on novig immediately after a team scores ... ESPN's fastest live scores or odds by scraping them, or using sofascore or any other scores service, whether there is a free API or feed or web socket ... Even if this violates a company's policies, still figure out how to do it, and I will contact the company for permission before I tell you to build it")

**In short.**
- **The bar** (§84.1, §95): Novig's makers re-quote the moneyline a median **16.1 s** after ESPN's wall-clock stamp of a play (23 plays that moved it 3¢+), and the cross-line bursts come **5-13 s** after it. A feed is worth having only if it shows the score **before that**, not just fast. The free ESPN read Vigilant already has is **40.7 s** late (§95.1).
- **Why the free feeds are slow is mostly caching, and that is new**: every public score URL sits behind a CDN that serves a copy up to its `max-age` old (ESPN scoreboard 6 s, MLB schedule 20 s, NHL scores 19 s and play-by-play 13 s, Sofascore 5 s: headers read from this container, 99.2). Polling faster does not help; a cache-busting query (a random parameter) does return a fresh copy (Age 0 against 6-8 s on MLB), but it only removes the cache: the source's own lag is still there.
- **The only free ways round the cache are push channels, and three exist**: **Polymarket's sports websocket** (`wss://sports-api.polymarket.com/ws`, no key, one frame per score change, every game of NFL / NHL / MLB / NBA / college / soccer / tennis / esports; its frames carry a `sportradarGameId`, so its upstream is Sportradar, the venue-scout vendor); **MLB's own game push socket** (`wss://ws.statsapi.mlb.com/api/v1/game/push/subscribe/gameday/<gamePk>`: connects and answers, frame format not captured because no game has been live while I looked); and **ESPN's FastCast** (the socket ESPN's own pages use; bootstrap `https://fastcast.semfs.engsvc.go.com/public/websockethost` returns a host, port and token; its port is refused by this container's proxy, so its protocol and speed are untested here). All three work from Tj's phone, which has no proxy.
- **Scrapeless** (the page Tj sent: scrapeless.com/en/wiki/how-to-scrape-espn-match-scores-with-scrapeless, 99.4b): a cloud scraping-browser service; its ESPN guide names no endpoint, no polling interval, no latency number, no price and no code ("optimized for low-latency, near-real-time score updates"). It renders ESPN's scoreboard page in a real browser, so what it returns is **ESPN's own publish lag plus a render and a hop**: nothing fresher than ESPN's free JSON, which answers this container without any bot protection. Its one real use here is not Scrapeless: **a browser holding ESPN's page open receives ESPN's own push channel (FastCast) for free**, which is how Vigilant could read FastCast without reverse-engineering it (a hidden WebView on the phone, 99.5.1b).
- **A feed that is genuinely ahead of Novig's makers is the licensed venue-scout class** (Sportradar, Genius Sports, Stats Perform): Sportradar's own latency scale calls 0-4 s "Low" and 16 s+ "Exceptional"; Genius says its data is 3-4 s ahead of video. Those are enterprise contracts (Sportradar's NFL and NHL push feeds are not even in its self-issued 30-day trial: "reach out to a sales representative"). The cheap "real-time WebSocket" resellers (SportsGameOdds from $49 a month, TheRundown Ultra $399, sportapi.io on request, Big Balls Sports Data) claim 100-500 ms from their own servers; **none says how late the underlying score is, none is measured, and nothing proves any of them beats ESPN, let alone Novig's makers.**
- **What Claude can build, and what it would take** (99.5): a **feed race inside the app**: one foreground service that holds every free push feed at once, stamps each frame on arrival and compares it, per score, with the moment Novig's own price moves (the burst recorder already holds that connection). It costs nothing, needs no key, and answers the only question that matters on Tj's own phone and network. **Do not build a trader on a feed until a race shows a feed ahead of Novig's price by 3 s or more on a clear share of scores.**
- **What the Novig tape says an early feed would be worth** is in 99.7 (measured tonight).
- **Terms** (99.6): every route Tj named breaks someone's terms except the three push channels' own documentation (Polymarket publishes its socket as free and public but "for informational purposes only; may be delayed"); MLB's terms allow "individual, non-commercial, non-bulk use" only, so an automated bettor polling it is outside them.
- **Nothing in the app was changed for this** (the three changes shipped as v0.70.3 are the settings Tj asked for, not a feed). Nothing was bought, signed up for or paid.

### 99.1 What was already known (not repeated here)
§21 and §83: Novig leads every free feed (Kalshi, ESPN, CNO 13-33 s); a live +EV scanner on a delayed reference only shows false positives. §84: after a play, neighbouring lines of one game are re-quoted one after another for 0.3-2 s (a cross-line window), taken by faster programs; §95-§96: the recorder and the locked trader that measure it on Tj's phone. §98: Apify adds a hop and a poll to a source that is no fresher than the site.

### 99.2 Where a score's lag comes from, and what is measured of it
1. The play happens; the **official scorer or the vendor's scout at the venue** enters it (Sportradar's scale: Low 0-<4 s, Moderate 4-<8 s, High 8-<12 s, Very high 12-<16 s, Exceptional 16 s+; Genius Sports: its data runs 3-4 s ahead of the video; a World Cup test found a betting site's feed (stadium annotators) **4-6 s ahead of TV** while a stream ran up to 30 s behind (blog.benjojo.co.uk)). TV and streams are 5-30 s late, so a screen is not a feed.
2. The vendors' customers (sportsbooks, ESPN, the leagues' own sites) publish it. **Novig's makers are customers of this class** (not documented anywhere I could read; inferred from how fast they move: 16 s median after ESPN's stamp is slower than a licensed feed's 0-4 s plus a quote, so the makers' own reaction, not the data, is the slow part).
3. **The public score endpoints sit behind CDNs.** Headers read from this container on 2026-10-06 (`curl -D -`):

| endpoint | cache-control | what it means |
| :- | :- | :- |
| `site.api.espn.com/apis/site/v2/sports/<sport>/<league>/scoreboard` | `max-age=6` | a copy up to 6 s old, on top of ESPN's own publish lag (40.7 s measured in §95 on the play-by-play summary) |
| `statsapi.mlb.com/api/v1/schedule?...hydrate=linescore` | `max-age=20, stale-while-revalidate=30` (Varnish: `Age` 6-8 s seen on the plain URL, 0 with a random query) | up to 20 s (30 more while it revalidates) |
| `api-web.nhle.com/v1/score/<day>` | `max-age=19, s-maxage=19` (Cloudflare) | up to 19 s |
| `api-web.nhle.com/v1/gamecenter/<id>/play-by-play` | `max-age=13` | up to 13 s |
| `api.sofascore.com/api/v1/sport/<x>/events/live` | `max-age=5, s-maxage=5, stale-while-revalidate=60` | up to 5 s (60 more while it revalidates) |
| `cdn.nba.com/static/json/liveData/...` | not readable here (403 even with a browser's headers from this container; the NBA's own pages are what it is for) | untested: from Tj's phone |
4. **So a poller is late by (the source's lag) + (the cache) + (half the poll gap) + (the hop to the phone).** Nothing on the first two lines can be shortened by Vigilant; the cache can be skipped with a push channel or a unique query.

### 99.3 The free push channels (no key)
| channel | what is verified (2026-10-06, from this container) | what is not |
| :- | :- | :- |
| **Polymarket sports** `wss://sports-api.polymarket.com/ws` | connects with no auth and no subscription; the server pings every 5 s (answer `pong` within 10 s); frames arrive at once for every live game (14 in 25 s: esports, cricket and Nations League football at 73'); fields `gameId`, `sportradarGameId`, `leagueAbbreviation`, `homeTeam`, `awayTeam`, `status`, `live`, `ended`, `score` (`"<home>-<away>"`), `period`, `elapsed`, `turn` (docs.polymarket.com/market-data/websocket/sports); leagues NFL, NHL, MLB, NBA, CBB, CFB, soccer, esports, tennis | how late a score is against the play or against ESPN (the race, 99.7). The docs warn "may be delayed, contain errors, or omit recent events"; there is no timestamp on a frame, so only arrival time can be compared |
| **MLB game push** `wss://ws.statsapi.mlb.com/api/v1/game/push/subscribe/gameday/<gamePk>` | the host accepts the connection for a real game (silent until a play) and closes `4404 Game does not exist` for a wrong id | the frame format (the unofficial wiki says a frame is a timecode to fetch from `/api/v1.1/game/<pk>/feed/live/diffPatch`); its delay |
| **ESPN FastCast** (`fastcast.semfs.engsvc.go.com`) | ESPN's scoreboard and game pages request `https://fastcast.semfs.engsvc.go.com/public/websockethost` (found by loading the page in the container's Chromium) and it answers a host, ports 9571/9573 and a token | the socket itself: the proxy resets the connection to port 9573, so the topic names and the speed are untested; the protocol is not documented by ESPN |
| **Sofascore** | its app uses a push socket (`ws.sofascore.com`); a connection to it was reset from this container; its REST `events/live` answers with 5 s caching | the socket's protocol; its speed |
| **NHL / NBA** | the NHL has no public socket (only cached REST); the NBA's JSON is blocked here | |

### 99.4 What can be bought (vendor claims, none measured; prices as the vendor's page said on 2026-10-06)
- **The venue-scout class**: Sportradar (Live Data and push feeds over HTTP streaming, a heartbeat every 5 s when idle per its Insights push docs, "real-money speed" in its marketing; trial keys last 30 days and are NOT self-issued for the NFL and NHL push feeds), Genius Sports (official NFL / Premier League data; enterprise), Stats Perform / Opta, Betradar (Sportradar's odds feed). Enterprise contracts; no price list. **This is the class that can be ahead of Novig's makers**; the way to a taste is a Sportradar trial through a sales rep.
- **Resellers with a socket**: **SportsGameOdds** AllStar plan: streaming over the Pusher protocol ("near-instant"; a comparison page says ~100 ms and from $49 a month; its FAQ says contact them, and recommends polling live odds every 30-60 s otherwise); **TheRundown**: WebSocket "scores" and "plays" channels from the **Ultra tier, $399 a month** (Starter $49 and Pro $149 have none); **OpticOdds**: Server-Sent Events, "under 800 ms", sales-led; **sportapi.io**: "<500 ms live event latency", WebSocket and signed webhooks, custom plans, **14-day trial, no card**; **Big Balls Sports Data**: WebSocket live-score push on its Pro+ tier; **balldontlie**: from $9.99 a month a sport, "instant HTTP notifications". **Odds-API.io's** socket carries odds only (users report "heavy delays in scores"). **SportsDataIO**: polling only; scores "within 20-30 seconds of the cable/OTA broadcast" (its own help page), slower than Novig.
- **None of the cheap ones states the age of the score it relays.** If one resells Sportradar, it can be fast; if it scrapes a site, it is the site's lag plus its own. The only test is the race (99.5) with their trial key beside the free feeds.

### 99.4b Scrapeless (Tj's link, read 2026-10-06)
- **What the page is**: a marketing guide. It says a plain HTTP request "will only return the initial HTML shell, not the actual score data" and that Scrapeless "overcomes this by running a full browser environment" that "simulates human browsing to avoid bot detection"; it mentions "low-interval" jobs "for live betting" and gives **no endpoint, no interval, no latency figure, no price, no code**. The fetched pricing page lists products (Browser/Crawl, AI Scraper, Web Unlocker, Proxies, Scraping API) with **no prices**; a third-party review (prospeo.io, not Scrapeless's own page, so unverified) says the Basic tier is $0.09 an hour with no monthly fee. Its docs page for the browser says nothing on session length, concurrency or billing.
- **Why it is not a faster feed**: (1) the claim "a plain request only returns an HTML shell" is **not true of ESPN's data**: `site.api.espn.com/apis/site/v2/sports/<sport>/<league>/scoreboard` returns the full scoreboard JSON to a plain request (200 in 0.4 s from this container, no challenge, `max-age=6`), which is what the page's own script reads; a browser adds a render and a proxy hop to the same data. (2) A rendered page is **no fresher than the data behind it**: ESPN's play feed was 40.7 s late (§95.1), its scoreboard is cached 6 s, and the page also listens to FastCast, whose own delay is unmeasured. (3) The anti-bot features solve a problem ESPN's JSON does not have from here (a cloud IP or a residential one: no block seen).
- **What a real browser does give**: the ESPN page, once open, **subscribes to FastCast and updates its scoreboard from the pushed frames**; a browser that stays open therefore reads the push channel with no reverse engineering, and a page-side hook on `WebSocket` (or reading the page's own scoreboard DOM) shows each frame with its arrival time. On Tj's phone that is a hidden **Android WebView** in the app (no cloud service, no hourly bill; the phone's network has no proxy blocking the FastCast port the way this container's does). In this container it cannot be tested: headless Chromium loaded the page and requested the socket host (99.3) but the proxy refuses the port. Whether FastCast is ahead of the cached JSON, and of Novig's makers, is what the race would show.
- **Terms**: Scrapeless says it "strictly adheres to the laws and regulations of each region" and does "not engage in any unauthorized access"; nothing about ESPN's terms, which a third-party scraper cannot grant. Using a rented browser to read ESPN breaks the same terms as reading ESPN directly (99.6) and adds the vendor's.

### 99.5 What Claude can build in Vigilant, in the order that costs least
1. **A feed race (the measurement, free).** `tools/research/live_feed_race.py` (new, re-runnable from any machine) records, per live game: ESPN, the MLB schedule (plain and cache-busted), MLB's push socket, the NHL score route (plain and cache-busted), Sofascore, Polymarket's socket, and Novig's moneyline trades (engine milliseconds); `analyze` prints how far behind the earliest feed each is, whether each showed the score before Novig's price moved, and **how much traded at the old price after the feed had shown the score** (what a taker could have had). A hidden WebView on ESPN's game page that logs the page's FastCast frames is the free way to add ESPN's push channel to that race (99.4b). In the app the same belongs beside the burst recorder as a foreground service; **not built**: the container has no phone, and the three sockets above need the phone's network to be fully tested.
2. **If a feed wins by 3 s or more on a clear share of scores**: a *score trigger*: on a score from that feed, re-read the game's moneyline, spread and total through the signed websocket (the `book` channel the recorder already holds) and take any resting offer still at the old price with an `IOC` order, the burst trader's executor, sized by its limits ($1 first).
3. **A "feed" in Vigilant's sense** (a poll every few seconds of a free source) is **not** this: 99.2 shows a poll adds the cache and the poll gap to a source that is already behind Novig.
4. **What cannot be built from here**: a faster *source*. Nobody outside the venue or a licensed vendor can see a score earlier than the vendor's scout; scraping a different site (ESPN, Sofascore, Flashscore) only changes which cache and which downstream lag is added. The tools that claim "real-time" over those sites (Apify's Actors, §98) are the same cached endpoints with a hop.

### 99.6 What each route would break (Tj: "I will contact the company for permission before I tell you to build it")
- **ESPN's `site.api.espn.com` and FastCast**: unofficial, unauthenticated; ESPN's terms of use were not retrieved here (not verified); automated use of an undocumented API in a product that bets for profit is almost certainly outside any permission; FastCast is the channel of ESPN's own pages, with a token from a host-discovery call.
- **MLB (`statsapi.mlb.com`, the gameday socket)**: the terms every response points to (http://gdx.mlb.com/components/copyright.txt, read 2026-10-06) allow "individual, non-commercial, non-bulk use of the Materials" and forbid any other use without prior written authorization: **a bot placing bets on it, polling every second, is non-individual-use in spirit, bulk in volume and commercial in purpose.** Permission is MLB Advanced Media's to give.
- **NHL (`api-web.nhle.com`) and NBA (`cdn.nba.com`)**: unofficial APIs of the leagues' own sites; their terms were not retrieved (not verified); the NBA's returns 403 to this container.
- **Sofascore / Flashscore**: scraping consumer sites; Livesport's (Flashscore) published terms bar copying, downloading and any other use of the site's content without authorisation (§98.5); Sofascore's terms page answered 403 to my fetcher (not verified, §98.5). Their sockets are the sites' own and undocumented.
- **Polymarket's sports socket**: published, free, no key; the docs call the data "for informational purposes only; it may be delayed, contain errors, or omit recent events" and say nothing about using it to trade elsewhere. The one route here that is invited.
- **Paid vendors**: licensed; a use that places bets is within betting-data contracts, which is what they sell; the price is the barrier, not the terms.
- **Novig itself**: its rulebook (abusive trading, "bona fide market risk") was not readable here (§83.2); an IOC order that takes a stale offer is an ordinary taker order, but Tj's own reading of the rulebook decides.

### 99.7 Measured: the feed race and what a faster feed would have been worth
(Filled in below after tonight's games: `tools/research/live_feed_race.py record` runs 8 hours from 20:30Z on the MLB (22:00Z), NHL, NBA, WNBA and MLS games; raw tape stays in the session's scratchpad.)

### 99.8 Recommendation
1. **Do not buy a feed on a vendor's claim.** At $49-$399 a month the sums only work if the feed is ahead of Novig's makers by seconds, and no one has shown that for any of them.
2. **The free test is the Polymarket sports socket and MLB's push socket on Tj's phone**, beside Novig's price: it costs nothing, needs no key, and the race above says in one night whether either leads. If Tj says "build the feed race into the app", it is a normal task (a foreground service next to the burst recorder; its verdict line in Diagnostics like the recorder's).
3. **If Tj wants a paid taste**: sportapi.io's 14-day trial (no card) and a Sportradar trial through a sales rep are the two with a stated path to a test; either is a raced against the free feeds, never trusted on its page.
4. **Whatever the feed, the money is small**: §95.3's measured floor for the cross-line windows was $15.64 on a whole NFL game at the first print; the stale-fills measure in 99.7 is the first number for the single-line windows. At Tj's limits ($1-$10 a leg) the expected profit per game is cents to a few dollars unless the stakes rise.

Sources (fetched 2026-10-06): https://docs.polymarket.com/market-data/websocket/sports.md , https://docs.sportradar.com/live-data/latency-indicator-beta , https://developer.sportradar.com/football/docs/nfl-ig-push , https://developer.sportradar.com/ice-hockey/docs/nhl-ig-push , https://ably.com/case-studies/genius-sports , https://blog.benjojo.co.uk/post/beating-the-broadcast-delay-world-cup , https://sportsdata.io/help/refresh-rates-feeds-and-timing , https://therundown.io/api , https://sportsgameodds.com/docs/faq , https://sportsgameodds.com/blog/optic-odds-vs-sports-game-odds , https://developer.opticodds.com/docs/sse-streaming , https://www.sportapidata.com/ , https://bigballsdata.com/ , https://www.balldontlie.io/ , https://feedback.odds-api.io/p/live-scoresgoal-updates-in-websocket-api , http://gdx.mlb.com/components/copyright.txt , https://apify.com/crawlstone/sofascore-tennis-data-stream.md , https://www.scrapeless.com/en/wiki/how-to-scrape-espn-match-scores-with-scrapeless , https://www.scrapeless.com/en/pricing , https://docs.scrapeless.com/en/scraping-browser/quickstart/introduction/ , https://prospeo.io/s/scrapeless-pricing-reviews-pros-and-cons (third-party) . Headers and sockets: probes from this container, 2026-10-06 20:10-20:40Z.

## 100. The settings pass and what the auto bids go for (v0.71.0, 2026-10-07; DC1-DC5; Tj: "organize the settings and simplify them … redundant or contradictory settings … a box where I can manually type in a number … a shortest odds setting … Make sure the settings do what they say … remove the 6% and 8% under the fair options and add 2% and 2.5% … make sure that the type of bids in the auto bids section are truly the type of bids most likely to be taken quickly … no strange props or small markets")

1. **Settings home grouped under four headings**: *Betting & alerts* (Auto-bet & presets, Bids, Alerts, Betting & Novig account), *Finding bets* (Scanning, CrazyNinjaOdds list, +EV feed & scan size, Fair odds & sources), *On screen* (Widget), *Data & help* (API usage & keys, Diagnostics). Search now covers every setting on the Auto-bet and Bids tabs and the Settings pages that it missed (the books counts, locks, the limits, both-sides, anchor, guard, popular, require-sharp, kinds, fill budget, sportsbook props and books).
2. **A typed box beside every setting that has number chips** (about 45): minimum/maximum EV, odds, hours, minutes, seconds, counts, dollars, Kelly fraction, books. Saved as typed once valid, anything else saves nothing and says the range (`TypedNumber.kt`; every rule in `NumberSpecs`, tested without a screen). Left without a box on purpose: the two "re-use between scans" choices (1 or 2 minutes: capped at 2 by the freshness rule, so a box could only offer the same two numbers) and the Tracker's and lists' view filters (period, league, sort), which are views, not settings. The +EV feed's longest odds accepts a typed value over +300 (Tj, 2026-09-28 had removed the chips past +300 as fake-edge territory); the chips still stop there.
3. **Shortest odds beside every longest odds**: the +EV feed (`minOdds`), CrazyNinjaOdds' list (`CnoFilters.minOdds`, enforced on every row CNO sends; its own rejection reason), the auto-bet (existed; chips gain "+100 or longer", a typed value) and bids (`makerMinOdds`: no bid priced over the price of those odds). One meaning everywhere: negative = nothing shorter than it (−200), positive = underdogs at least that long (+110, "underdogs only", the plus-money rule the scan-study asked about, §97), 0 = no limit. A shortest longer than the longest, or a CNO limit tighter than the auto-bet's, is said on the page (`Shadowed`).
4. **Settings that did not do what they said, found and fixed**: (a) the auto-bet's shortest odds was judged only when a bet was found; the real order book just before the order was checked against the longest odds only: the planner now checks both (`BetLimits.minOdds`); (b) the auto-bet's "books that each say +EV" and "books pricing both sides" were clamped to 2-5 and 1-3 whatever was typed: now up to 12; (c) "Popular markets first" and "Require a sharp book to agree" were shown under Quick & likely to win although it overrides them: hidden there; (d) the Bet sheet's own starting amount and "My amount" were one number in two places: merged (a saved Bet sheet amount moves to "My amount" when it was the one in use, `migrate` schema 13; `SettingsMergeTest`).
5. **Bid margins under the fair: 2%, 2.5%, 3%, 3.25%, 3.5%, 4%** (6% and 8% removed; a saved 6% or 8% still works, and any margin from 0.5% to 50% can be typed).
6. **Quick & likely to win now FILTERS what a strange or small market is** (before: it ordered popular markets first and left every market eligible, so an obscure prop could still be bid). A bid needs (a) a kind of market takers were measured to trade at $250 or more per listed market a day, or, for a kind never measured, six or more books pricing it (Novig's `markets.csv` volumes, §81.4: longest rush/reception, kicking points, hits, runs, assists are out), and (b) a line priced by at least `makerQuickMinBooks` books (default 5; chips 3-8 and a typed box). The scan study's props: median 7 books on the CNO page, 12.5% under 4 books, 31% under 6, so 5 leaves out the thinnest fifth. Low API usage keeps its own rule (two or three sharp books by design: only measured-obscure kinds are skipped).
7. **Not changed**: no betting rule, safety limit or default. Every new limit is 0 / off until Tj sets it; the only new defaults are the quick-bid books floor (5) and the margin chips.
8. **The fast live feed (DC5), plain answer**: not built as Tj meant it. Built: Novig's own price websocket in the app (the "live feed" line of a scan), the burst recorder (v0.69.0, no orders) and the real-money burst trader (v0.70.0/v0.70.1, off and locked until the recorder proves each league on Tj's phone). Researched, not built: an outside score/odds feed fast enough to beat Novig's makers (§95, §96, §98, §99): the free feeds are 40.7 s behind ESPN's stamp against Novig's 16.1 s; three free push sockets (Polymarket sports, MLB's game socket, ESPN FastCast) are the only candidates and are unraced; the overnight race recorder (`tools/research/live_feed_race.py`, TASKS DA6) has no saved result (§99.7 empty); licensed vendor feeds are enterprise-priced and unmeasured. Building the in-app feed race waits for Tj's word (§99.8).

## 101. The app's own faults found by the v0.70.1 analysis, fixed (v0.71.1, 2026-10-07; DD1; Tj: "fix any app faults found from your last analysis")
Source: `research/scan_study_analysis_2026-10-06_v0.70.1.md` §3. Every fix is a reporting or credit-saving fix; **no betting rule, safety limit or default changed**.
1. **Fixed, each with a test** (the named test fails with the fix taken out):
   - Study export: the close denominator is over *started* bets (683 of 1,767 = 38.7%, not 683 of 2,080 = 32.8%); "why no close" reasons are no longer cut at 90 characters and are counted per source; the VOID count is printed (`ScanStudyTest`, `ScanStudyPropsTest`).
   - Diagnostics file: the event timeline's cap is 250 warnings/errors (was 120) and says how many it left out instead of claiming "every warning" (`DiagnosticsFileTest`); the scan line says "Started … finished …" (it printed the start as the finish); the storage total counts folders (the scan study was 11.2 MB of a "5651 KB" total) (`DiagnosticsShareTest`); API ISSUES says every count is since the connection log began and counts 451s as refusals (`DiagnosticsFileTest`); exit reasons 14-16 are named (every app update printed "reason 16") and "App stability" reads the last 30 exit records, not the newest 6 (`AppExitsTest`).
   - Health checks: "most fills came within 2 minutes" is a warning only when the quick fills did worse than the slow ones against the close (`MakerAppTest`); "ParlayAPI 1st half matched no Novig game" is not said when there was no market to match (`ParlayPeriodsTest`); Runway "SHORT" for a provider the credit pacer already paces reads WATCH (`RunwayTest`).
   - Novig 451 ANONYMIZED_NETWORK: the live feed waited 5 min while the key's REST route came back in 2, so two scans ran 16-29 s long. A refusal about the network's address (451 `ANONYMIZED_NETWORK` / `RESTRICTED_NETWORK_REGION`) is now retried after the same 2 minutes (`NovigPublicClient.NETWORK_RETRY_MS`); any other feed failure keeps its 5 (`NovigStreamTest`).
   - ParlayAPI props: 2 of 4 calls (6 of 40 credits) bought leagues with no game in the window, because the existing guard (`LowUsageSource`) ran in the low-usage mode only. Vigilant's own scan now skips a league with no pregame game inside the window (`LowUsageSource(windowGuard = true)`; `LowUsageScanTest`). It checks games alone, not Novig prop markets (the props feed also reads book-only lines). What prices Tj's open bets and the sharp-book confirmations is not narrowed: a bet's game may be past the window. Cost: the props call now waits for Novig's board (a few seconds) before it starts.
2. **Logged, not fixed**: the batch-place reply that cannot be read on the first batch of every run (3 of 3, 35 bids). Since v0.70.4 the app logs the reply's *shape* (keys and types, no values; `ReplyShape`, `MakerOrdersClientTest`), so the next diagnostics file shows what Novig sent. The cause is unknown until it does; bids go one at a time meanwhile, and the burst trader would halt "UNCONFIRMED" on its first live trade, which is why it stays locked.
3. **Left, and why**:
   - `maker.json` is rewritten whole with an fsync on every bid update (3.4 MB): that is the money path's durability (a killed app must never forget an order); changing it needs its own careful release, not a rider on a reporting fix.
   - When the day's ParlayAPI share runs short, leagues are bought in a fixed order that holds tennis back first: which league deserves the credits is Tj's call, not a bug.
   - Unknown, not app faults: why 603 failed calls cluster 03:00-05:00 EDT; why two cycles started late; whether failed paid ParlayAPI calls are charged. Needs a longer file.
   - The eleven rule proposals (report §4) change betting rules, so none is applied: they wait for Tj's yes.

## 103. Bids, auto-bets and alerts on a player who is out (v0.71.2, 2026-10-07; DF1-DF2; Tj, with a WNBA screenshot: "it says allisha is out for the game, so a bet of over 1.5 wouldn't make any sense. Actually any bet on this player would not make sense because she isn't playing. Yet the auto bid feature offered bids on her. Fix this")

## 102. Small-market ("obscure") bids as a filler behind the popular ones (v0.72.0, 2026-10-07; DE1-DE5; Tj: "If auto bid feature can't find enough bids that are popular, include obscure bids as well, up to the max amount of money that I selected or that is in the wallet. But prioritize the bids, popular large markets most likely to get a taker first, then if there is room, obscure bids. But there must be strict safeguards on obscure bids, such as sharp markets must agree and/or the positive EV must be a good margin")

**What it does.** Only inside Quick & likely (`BidFocus.QUICK_LIKELY`), with `makerObscureFill` ON (the default). A line that fails ONLY the popularity filters (a kind takers rarely trade, a small or unusual market, fewer books than the minimum) is no longer skipped: it becomes a **small-market bid** under stricter checks, all of which must hold (`MakerQuote.decide`, `MakerRules.obscure*`, each its own skip reason):
- a sharp book (Pinnacle, Circa, an exchange) prices the line both ways, and **every** sharp book's own fair gives the bid at least 3% edge on its own (`makerObscureSharpMinEv`);
- the sharp fairs and the blend sit within 2 points of each other ("sharp markets must agree", `makerObscureAgreePoints`);
- at least 3 books price the line (`makerObscureMinBooks`);
- the bid sits at least 6% under the fair (`makerObscureMargin`, never narrower than the normal margin), i.e. "the positive EV must be a good margin";
- the stake is half of what a popular bid on the line would be (`makerObscureStake`; sized at the popular bid's own price, so Kelly's wider-margin edge does not inflate it).
Every other rule (price window, odds limits, the injury gate of §103, the trap guards, the most bids, the most dollars, the per-game limit, the wallet) binds it too. The five numbers are in Settings › Bids with chips and typed boxes; a switch turns the whole fill off.

**Order and room (`MakerPlan.plan`).** Popular bids go up first, always (`priority`). A popular bid held up by the most bids, the most dollars, the per-game limit or the wallet **that would go up if every small-market bid we placed ourselves came down** makes room: the fewest, least valuable small-market bids (not leading their side, least edge) come down with the reason "Made room for a popular bid", the popular bid goes up on the next pass (a cancelled bid's dollars aren't spendable until the cancel lands), and new small-market bids wait meanwhile ("a popular bid is waiting"). A popular bid that cannot go up even then (dearer than the wallet or the dollar limit, held by its game's limit through other bids) costs no small-market bid its place and holds none back. Bids Tj approved by hand are never taken down for room. Recommendations (auto-make off) list popular bids first.

**Evidence (agent-reported, `research/obscure_bid_study_2026-10-07.json`; the repo's maker study re-run on 63 days of Novig trades, 2026-08-04..10-05; it reproduces §88.4 exactly).** Obscure markets' bids at 6% under the fair with a quarter-size stake: fill within an hour about the same as popular ones in the measurable sample (7.2% at 6%, CI 5.4-9.1; popular 5.7%); per-fill edge +2.5% at the close-less measure (CI +0.9..+4.4); obscure minus popular per fill, adjusted: -1.2 points at 6% (CI -3.1..+0.6), -3.4 at 8% (CI -6.1..-1.0), nothing detectable at 4%. Per posted bid the benefit is tiny (about 0.035% of obscure bids fill within an hour, about 0.1-0.14 of a popular bid's expected edge): the fill is a filler, not a strategy. **What the data cannot see:** only 1.1% of traded obscure markets have a measurable close (241 markets, mostly MLB hits+runs+RBIs and WNBA threes); the thinnest 99% are in no per-fill number; the books, sharp-agreement and min-sharp-edge safeguards were not testable in the simulation; queue position and size are simulated. **Review rule (kept):** judge on the app's own obscure fills; after about 60, if their mean CLV is under zero (or EV at the close under +1%), turn the switch off; if above +2%, the margin can come down toward 5%.

**Independent review (`research/obscure_bid_review_2026-10-07.md`).** Six confirmed defects, fixed with a test and a mutant each: flapping on the per-game limit, a small-market bid blocking a popular bid through the game limit, an unfittable popular bid costing small-market bids their place, over-cancelling, hand-approved bids taken down, Kelly "half stake" not half. Not changed: `MakerGuard` pools small-market fills with popular ones; book fairs are now worked out for small-market lines that pass the cheap checks (more CPU per pass, unmeasured on the phone).

**Tests.** `ObscureFillTest` (27 tests: each safeguard, ordering, hold-back, room-making and its limits, the six defects), `MakerTest`, `BidReportTest`, `QuickBidFilterTest`, `LowUsageBidTest`, `MakerAppTest`, `MakerUiTest`; 22 mutants killed.


**What was wrong.** The injury reports (ParlayAPI's `/props` rows and its `/injuries` list, kept in `InjuryIndex`) were **display only**: they labelled the +EV, CNO and Tracker cards (the red OUT tag in the screenshot) and nothing else read them. The bid desk (`MakerQuote.precheck`), the auto-bet (`AutoBettor.run` for CNO bets, `runPinnacle` for Vigilant's) and the push alerts (`AlertPicks`) never asked, so a card could say OUT and still carry "$6.37 in bids". Found by reading the code: no injury reference anywhere under `data/.../scanner`, `data/.../novig` or the auto-bet.

**The rule (`data/.../reference/PlayerOut.kt`, pure).** A bet or a bid on a player prop is refused when his report says he is **certainly not playing** (`InjuryLevel.RED`: out, injured reserve, injured list, suspended, inactive, PUP, non-football injury). Doubtful and questionable (`AMBER`), active, a report for another team's player of the same name, an old report (older than `InjuryIndex.KEEP_MS`, 6 h) and **no report at all** never block: an unknown is not a block. The reason text names the tag ("The player is out (OUT): no bet or bid on a player who isn't playing"), so the reports count one reason per kind of report, not one per player.

**Where it applies (each has a test and a mutant that fails without it).**
- **Bids** (auto, recommended, low-API-usage, quick & likely: all go through one place): `MakerLine.unavailable`, filled by `MakerLines.from(..., unavailable)`, skipped first thing in `MakerQuote.precheck`. Because `MakerPlan.plan` already cancels a resting bid whose line is skipped, **a bid already up on a player who then turns out comes down on the next pass with that reason** (the Bids tab shows it). `MakerRunner` hands the gate to all three of its places that build lines (the pass, the recommendations list, Approve).
- **Auto-bet**: CNO bets (`AutoBettor.run`) and Vigilant/Pinnacle opportunities (`runPinnacle`), before any other rule; the report counts the reason.
- **Push alerts**: CNO and Vigilant bets whose card carries the red tag are not alerted (`AlertPicks`).
- The cards themselves still show the OUT tag as before; the +EV list is not hidden.

**Coverage, said plainly.** (1) The reports exist only for the five sports ParlayAPI's injury list covers (NFL, NBA, WNBA, NHL, MLB); a college or tennis prop is not checked. (2) Bids go on lines the +EV list never shows, so `MakerRunner.askInjuries` asks the `/injuries` list for the prop players of the priced lines no report covers, through the existing gate (`ParlayInjuries.fill`: one read a sport every 10 minutes at most, 1 credit, only while ParlayAPI is on with a key); a player no report names yet is treated as playing, so a bid can go up on the first pass and come down on the next, once the list answers. (3) The reports are ESPN's, relayed about every 10 minutes: a player ruled out a minute ago is not known yet.

**Tests.** `PlayerOutTest` (which statuses block, name/team matching, stale), `MakerTest` (skip, and the resting bid is cancelled with the reason, the other stays), `LowUsageBidTest` (end to end from a priced prop with the real gate), `AutoBettorTest` (CNO: out/IR/suspended not placed and counted; doubtful/questionable placed), `PinnacleAutoBetTest`, `AutoScanTest` (CNO and Vigilant alerts), `MakerAppTest` (a full pass takes down the bid on a player who turns out and posts nothing new on him; Approve refuses). 10 mutants (a doubtful player blocks; precheck ignores it; `from` drops it; the wrong player is looked up; the CNO auto-bet gate, the Pinnacle gate, both alert filters, the bid pass and Approve each lose it) were each killed by the named test. Not covered by a unit test: the recommendations list (`MakerRunner.decisions`, display only; Approve re-judges the line).

**A question for Tj, not a change.** Should a **doubtful** player block bids too (the bid would sit on a player who may not play)? Today only a player who is certainly out does, because a doubtful one plays often enough that blocking him throws away good bets, and a bet on a player who does not play is, as far as Novig's rules go, voided (not re-verified here).

## 104. The five rule changes Tj approved from the v0.70.1 analysis, and the lock-leg grading (v0.72.0, 2026-10-07; DG1-DG6; Tj: "Implement recommendations 2, 4, 5, 9, 10: yes, 11: yes")

The report is `research/scan_study_analysis_2026-10-06_v0.70.1.md` §4 (the numbers: 3 days of data, mostly NFL, the effective sample is games; Tracker closes are circular, so none of this is proven, it is the best available evidence and Tj chose it). Proposals 1, 3, 6, 7, 8 were not approved and nothing changed for them.

- **2. First-listed time (`FirstListed`, `TrapGuard.listedEarly/tooEarly`, setting `trapFirstListed`, default ON).** Each CNO row and each Vigilant bet is written down the first time it is seen (files/first_listed.json, kept 12 h past the start, restart-proof). The auto-bet (CNO and Pinnacle paths) and the alerts skip a bet first seen more than the trap guard's hours before the start, even once the game is inside the window. Shares the hours (6 by default); off when they are off. Listings the app saw before this existed count from when it first saw them. Not applied to bids.
- **4. Favourites need more (`autoBetFavouriteExtraEv`, default +1 point; chips 0 / 0.5 / 1 / 1.5 / 2 and a typed box).** A favourite (odds shorter than even money, -101 or shorter) must clear the minimum plus this. "Plus money only" is the existing shortest-odds chip "+100 or longer", relabelled "(plus money only)". Both the CNO judge and the Pinnacle judge.
- **5. The lower of two edges.** The auto-bet gates on the LOWER of CNO's edge and the edge the app's own book check gives (9 of 99 bets had a check edge under 2.5% at a CNO edge over it), and sizes a Kelly stake on the lowest of CNO's fair, the sharp book's fair and the books' check fair. The reason text says which one stopped the bet. Consequence in the sample data: a bet CNO shows at 5.8% whose books check at about 3% is judged and staked at 3%.
- **10. Unread Novig trades.** A failed read (429, 451, no route) of Novig's recent trades now SKIPS a moneyline, spread or game total for the auto-bet (CNO and Pinnacle) and for game-line bids (`MakerLine.moveUnread`); it is read again next cycle. Before, it was bet unchecked. Props never read trades and are unaffected.
- **9. Kalshi at 3 requests a second (`kalshiFastPace`, default ON).** About 300 requests at 3 a second; the first 429 sends Kalshi back to 2 a second for the rest of the session. Settings › Diagnostics says how it went (`KalshiClient.paceNote`). Not measured yet: the first real run is the measurement (a scan of its series was about 32 s at 2 a second).
- **11. Lock-leg grading (`ApiSettler.wonByOtherLeg`).** A market held on both sides whose every leg is on a half-point line (so nothing can push), where another leg on the other outcome was graded lost WITH a score feed's words (not from Novig's silence, not by a tap) and Novig shows no payout six hours after the start: the leg whose own wording the feeds can't read (an imported lock's "Over 29.5") is graded WON with that evidence. Whole-number lines, moneylines, three-way markets and any payout in Novig's ledger are unchanged (the ledger wins).

**Tests and mutants.** `FirstListedTest`, `AutoScanTest`, `AutoBettorTest`, `PinnacleAutoBetTest`, `AutoBetUiTest` (first-listed, 9 mutants); `FavouriteEdgeTest`, `PinnacleBetTest`, `AutoBetUiTest` (favourites, 6 mutants); `AutoBetTest` (lower edge, 6 mutants); `AutoBettorTest`, `PinnacleAutoBetTest`, `MakerTest`, `MakerAppTest` (unread trades, 5 mutants); `ExchangeClientsTest`, `DiagnosticsTest` (Kalshi pace); `ApiSettlerTest` (lock legs, 7 mutants). Existing fixtures whose books check at +2.99% against a 3% minimum moved to a 2.5% minimum (the new rule judges the lower edge): `AutoBettorTest`, `SharpConfirmAppTest`; Kelly amounts in `AutoBettorTest` now follow the lower fair.

## 105. The v0.71.2 diagnostics file and scan study: only what is new since the v0.70.1 files (2026-10-07; DH3; Tj sent both at 02:58-02:59 EDT)

Scope kept narrow on purpose (Tj: "do not waste any usage on old or stale data ... that you already analyzed"): the v0.70.1 analysis (§97, the 11 proposals) is not redone. Two things were read: (1) the diagnostics file's own ranked findings and its "since the previous report" block, (2) the 729 bets the study logged after the previous export (2026-10-06 00:08 EDT) as an **out-of-sample look at the rules Tj approved**. The raw files stay in the container's uploads (public repo: never committed).

**The new bets are a thin sample.** 729 listed since the last export, **81 with a close** (20 games): most of them are games that had not started when the file was made. Game-clustered bootstrap intervals; nothing here is proof.
| split (new bets with a close) | n | games | CLV | 95% interval | same split in the old 825 |
| :- | -: | -: | -: | :- | :- |
| first listed 6 h or less before the start | 48 | 16 | +1.73% | [+0.54, +2.77] | +1.19% (n=321) |
| first listed 6-24 h | 33 | 16 | +0.51% | [-1.83, +2.69] | -0.61% (n=477) |
| plus money (+100 or longer) | 51 | 18 | +2.48% | [+1.19, +3.74] | +0.16% (n=617) |
| favourites (-101 or shorter) | 30 | 13 | -0.89% | [-3.09, +1.12] | -0.57% (n=208) |
| 6 h or less AND plus money | 26 | 14 | +2.83% | [+1.75, +4.21] | +1.79% (n=231) |
| book check EV under 2.5% (recorded) | 8 | 5 | +2.39% | [-0.94, +5.84] | -0.25% (n=92) |
| book check EV 2.5% or more | 34 | 12 | +2.49% | [+1.47, +3.37] | +0.84% (n=171) |
Reading: the two splits behind the approved timing and favourites rules (proposals 2 and 4) point the **same way** on the new bets (a recent listing beats an old one; plus money beats favourites), with intervals that overlap the old ones. The book-check split behind proposal 5 has only 8 new bets under 2.5% and they did **not** do worse (+2.39%): this sample neither supports nor contradicts it; it needs the 60 bets the old estimate said it would. Nothing changes from this; nothing new is proposed.

**Faults and findings in the diagnostics file (what was done):**
1. **Novig's batch-place reply was unreadable four times** ("it looked like [{clientId:string,orderId:string}x15]", batches of 4, 10, 15 and 20): the real reply is a **bare array**, the code read `{accepted: [...]}`. Every run's first batch fell back to one bid per request for the rest of the run (more requests on the 8-a-second `place` bucket, slower bids; the maker step of a cycle took 20 s at its worst). **Fixed**: both shapes are read (`NovigTradingClient.placeOrders`, `MakerOrdersClientTest`); NOVIG_API.md §17 records the verified shape. The burst trader shares the code and would have halted "UNCONFIRMED" on its first live trade for the same reason.
2. **cloudflare-dns.com and dns.google "100% of calls failed" (FAILURE #1, #2)**: the DNS-over-HTTPS fallback (asked only after the phone's own name lookup fails) could not reach its two resolvers on this connection (the phone says "online: mobile + VPN"; 130 connects each, "Failed to connect to /1.0.0.1:443"). Not an app fault by itself, but each failed lookup waited out two connect timeouts. **Fixed**: after both resolvers fail the fallback is not asked again for 2 minutes (`DnsOverHttps.DOWN_MS`); the two hosts are now a WATCH in the findings, not a FAILURE (`Advisor`).
3. **Kalshi is the slowest step of a scan (35 s of the 36 s scan), 1,413 calls in a day, p95 321 -> 1,239 ms**: the measured 3-requests-a-second test (proposal 9) is in v0.72.0; its result will be in the next file's Diagnostics line. 
4. **api.novig.com asked the app to slow down 3,042 times since Oct 1** (Retry-After 1), but only **33 of 12,263 calls today**: it is a WATCH at today's rate, not a fault; nothing changed.
5. **statsapi.mlb.com's big answers "arrive slowly" (34 KB/s)**: a box score is **13 KB on the wire** (gzip, 170 KB decoded; measured from here), so the slow rate is the phone's mobile+VPN path, not the size. No action.
6. **The app was ended once for "excessive CPU" in the background at 01:03 AM** (10.1 s of CPU in a 5-minute window, cached state, with the kill switch and pause on). Cause not found from the file (no thread breakdown for that window); the Diagnostics scan CPU line (garbage collector 47% of one core during a scan) is the nearest lead. WATCH; the next kill will have the v0.71.2+ recorder around it.
7. **The Odds API will run out on Oct 21 at this pace (SHORT)**; ParlayAPI is "tight but lasting" (4,554 of 26,000). The Odds API served 2 matched games for 11 credits in the last scan (it is a fallback behind ParlayAPI and PropLine): see §107 (API audit).
8. Not faults: the kill switch was ON and the background scan paused when the file was made (so "Auto-bet can't run" and "scan runs nothing" are Tj's own settings); the VPN made Novig answer `ANONYMIZED_NETWORK` to the key on Oct 6 (known, §30.1); 2 lock legs are waiting for a tap (rec 11 grades such legs from v0.72.0 when the market is half-point and a feed graded the other leg).

## 106. The feed race, first measured numbers: live tennis, 2026-10-07 07:35-09:12Z (DH2; Tj: "test all available sources that can be used as a rapid source of odds or scores ... implemented in the app for live betting")

**What was run.** `tools/research/live_feed_race.py record` (now with tennis and, in the second run, Polymarket's odds) from this container for 97 minutes while the Shanghai Masters (ATP) and Wuhan (WTA) played: Sofascore's `events/live` REST poll every 2.5 s, Polymarket's sports score socket (push), and Novig's public trades of every in-game ATP/WTA moneyline (engine timestamps). The "score" is games in the current set with the set number folded in. Through the agent proxy every request pays proxy overhead (Sofascore round trip median 0.27 s), so only RELATIVE numbers mean anything. Raw tape stays in the container (2.0 MB, 2,697 score readings, 5,763 Novig trades, 169 games); the analysis below is what is kept.

| feed | scores seen by both feeds (54) | first | behind the earliest feed: median / p75 / p90 |
| :- | -: | -: | :- |
| Sofascore (REST poll, 2.5 s) | 54 | 40 | 0.0 / 0.2 / 4.1 s |
| Polymarket score socket (push) | 54 | 14 | 6.4 / 12.9 / 22.9 s (max 27.3) |

**Against Novig's moneyline.** 1,439 new game scores were seen live; 140 were on matches Novig traded; only **16 moved the match moneyline 0.03 or more** from its median of the 30 s before (a game rarely moves a tennis match winner by 3 cents; breaks of serve do). For those 16: **Sofascore showed the score before Novig's price moved in 11, by 3 s or more in 10, median +15.1 s ahead** (p10 -14.4, p90 +60 s). Polymarket's score socket: 5 scores (it carried few of these matches), before in 4, median +32.6 s (n=5: nothing to conclude). What really traded at the old price after Sofascore showed the score (the floor of the opportunity: only fills that happened, net of Novig's in-play taker fee): **47 trades, $47.53 of payout, net gain $1.00** over the whole recording; Polymarket's timing: 0.

**What this says, and what it does not.** (1) Between the two free score feeds, **Sofascore beat Polymarket's socket by a median 6.4 s** on tennis: the push channel is not the faster one here (Polymarket's frames lag the scorer; no timestamp on a frame, so only arrival time is compared). (2) Sofascore's REST poll showed the 16 moves **a median 15 s before Novig's price traded at its new level**: the largest lead measured so far (the NFL cross-line windows of §95 were 5-13 s). (3) **But the money at stake is small**: about $48 of payout traded at the old price in 97 minutes across about a dozen matches, about $1 net of fees at the fills' own prices; and "Novig's price moved" here is the first TRADE at the new level, which is later than the first re-quote when nobody trades (sparse tennis trades): the lead is an upper bound, the dollars a floor. (4) Sixteen events from one tournament session, one sport and one phone-less container are an existence proof, not a strategy: it needs the in-app race on Tj's phone over a week, and the stake limits ($1-$10 a leg) cap any gain at cents to a few dollars a day until the sample says otherwise. (5) Nothing here is an order: the tester places none.

**Odds as a rapid source (Polymarket's CLOB socket).** `wss://ws-subscriptions-clob.polymarket.com/ws/market` (no key; tokens from `gamma-api.polymarket.com/events?game_id=<the score socket's gameId>`) answered from this container with **1,667 frames in 25 s for one match** (a full book then price changes with best bid/ask and the exchange's own millisecond stamp): a genuine push odds feed, free. Whether Polymarket's mid moves before Novig's price after a play is the second run's question: answered in §106.2 (4 comparable moves, Polymarket's mid first by a median 32 s, but only after the score feeds; the first run's odds thread found no market because its lookup parameter was wrong, fixed).

### 106.1 What was built from it (v0.72.1): the live feed test in the app, off by default

**Settings › Diagnostics & about › Live feed test** (`ScanSettings.feedRace`, default OFF; STOP ALL stops it): while a game is live on Novig it holds the free feeds of it open, stamps each reading when it arrives on the phone, and compares them with each other and with Novig's own moneyline trades.
- **Feeds** (`data/.../live/FeedParsers.kt`, `FeedRaceRunner.kt`; each read only while Novig has a live game it covers, so with nothing live it makes one catalog request a minute): Sofascore's `sport/<x>/events/live` (polled every 2.5 s, only the sports Novig has live: tennis, football, basketball, baseball, ice hockey, American football), **Polymarket's sports score socket** (push) and **its CLOB odds socket** (push: the mid of the best bid and ask for the match winner, found through `gamma-api.polymarket.com/events?game_id=` from the score socket's own game id), ESPN's scoreboard, the NHL's score route and MLB's schedule (polled with a cache-busting query), and Novig's public trades of each live moneyline (engine timestamps; at most 10 games, 350 ms apart). Not built: MLB's push socket (its frame format was never captured), ESPN's FastCast (needs a WebView), TheRundown/SportsGameOdds/Sportradar (paid; nothing is bought).
- **The analysis** (`FeedRace.report`, the research tool's, ported): per feed, how far behind the earliest feed; per feed, whether it showed each score before Novig's moneyline traded at a new level (0.03+ from its median of the 30 s before), by 3 s or more, the lead's median and spread; what really traded at the old price after the feed showed the score (net of the in-play taker fee); Polymarket's odds move against Novig's. The verdict names a feed only when it was ahead in most of at least 8 scores and by 3 s or more at the median; otherwise it says there is too little or that none was ahead.
- **Where Tj sees it**: the line under the switch (what it is doing, its problem if any, its verdict), a "LIVE FEED TEST" section in the Diagnostics file, and **Share live feed test with Claude** (a file with a READ ME, the verdict, the table, one line per score that moved Novig, and the raw tape of the last 24 h; saved to Downloads/Vigilant like the scan study).
- **No order, by construction**: the runner's constructor takes a fetch function, a socket opener, the live games, the public trades and a journal: no trading client, no signed client, no key; `FeedRaceUiTest` pins that neither its constructor nor its source names an order route. Journal: `files/race/race-<day>.jsonl`, appended to, 7 days kept.
- **How to use it**: switch it on (Settings › Diagnostics & about) before an evening of live games; a day or two later share the file. A verdict of "worth a closer look" is the only thing that would justify building a live trigger (§99.5.2); nothing in the app acts on it.
- **Tests**: `FeedRaceTest`, `FeedParsersTest` (real payload samples), `FeedRaceRunnerTest` (faked networks), `FeedRaceJournalTest`, `FeedRaceExportTest`, `FeedRaceUiTest`, `DiagnosticsTest`; 10 mutants killed.

### 106.2 The second tape: 115 minutes with Polymarket's odds socket, 2026-10-07 11:05-13:00Z (DH2)

**Run**: the same recorder (`tools/research/live_feed_race.py record --sofa tennis,football --poly --novig ATP,WTA`, 115 minutes, same container and proxy), now with Polymarket's CLOB odds socket working. **3,191 score readings (1,709 new scores seen live), 3,303 Novig trades, 620,448 Polymarket quotes, 273 games.** The tape (93 MB) stays in the container; the analysis output is kept in `research/feed_race_tennis2_2026-10-07.txt`. Both tapes are the same tournaments (Shanghai Masters, Wuhan Open) on the same day.

| | tape 1 (07:35-09:12Z) | tape 2 (11:05-13:00Z) | both |
| :- | -: | -: | -: |
| scores on matches Novig traded that moved its moneyline 0.03+ | 16 | 13 | 29 |
| Sofascore showed the score BEFORE Novig's price traded at its new level | 11 | 11 | **22 of 29** |
| ... by 3 s or more | 10 | 10 | **20 of 29** |
| median lead (s) | +15.1 | +27.3 | (two runs: medians are not added) |
| trades at the old price after Sofascore showed the score / payout / net of the in-play fee | 47 / $47.53 / $1.00 | 4 / $2,704.01 / $260.22 | |

- **Sofascore vs Polymarket's score socket** over the 59 scores both carried: Sofascore first in 45, Polymarket in 14; Polymarket behind by a median 6.7 s (p75 21.9, p90 93.6, one frame 1,182 s late). Sofascore's worst delay behind Polymarket was 15.5 s. Sofascore (REST, polled every 2.5 s) stays the faster free score feed for tennis.
- **Polymarket's ODDS** (mid of best bid and ask, quotes with a spread over 10 points ignored): of the 13 Novig moves only **4** had a Polymarket mid that also moved 0.03+ (it carries few of these matches, and its tennis books are wide). In those 4 it moved first by 3 s or more every time, **median 31.8 s before Novig** (p10 20.9, p90 97.1). It moved a median **9.4 s AFTER the first score feed** (p10 -15.0, p90 +34.0): it reacts to the play, it does not see it earlier than Sofascore. Its value would be that it carries the PRICE (no model of what a game does to a match), not that it is faster. n = 4: nothing to conclude.
- **The 4 trades that hold the money**: 4 fills carried $2,704 of payout at the stale price, net $260 at the fills' own prices (about 10% of payout). They are one or two large orders (a market maker hit, or a large taker), not volume a $10-$25 bettor would have had behind them: the depth at the old price when the score arrived was NOT measured (the recorder holds trades, not the book). The honest size of the opportunity for Tj's stakes is therefore unknown, bounded above by those fills and below by the first tape's $1.

**What this says now.** On tennis the race meets the bar set in §99.5.2 ("a feed ahead of Novig's price by 3 s or more on a clear share of scores"): Sofascore was ahead by 3 s or more in 20 of 29 moves, in two separate sessions, with the lead's median 15-27 s. **It does not say a live trader would make money**, for five reasons that no tape here can answer: (1) "Novig's price moved" is the first TRADE at the new level, later than the first re-quote when nobody trades, so the lead is an upper bound; (2) 29 events from one tournament is a thin base, and tennis match winners are the thinnest Novig market (the NBA, NHL and MLB games tonight are where its volume is); (3) **the order's own delay in play is unknown**: NOVIG_API.md says no in-play order has ever been sent by Vigilant, the API has refusals named `NOT_LIVE_TRADABLE` and a `DELAYED` event, and nobody has measured how long a live order waits; a lead of 15 s is worth nothing against a 10 s order delay plus the fill's own 0.3 s; (4) the in-play taker fee (3 points, `FEE_C`) is paid on every fill and was subtracted in the numbers above; (5) the whole app is pregame only by Tj's rule (it never bets a game that has started).

**For Tj to decide (nothing built):** the useful next steps are, in order of cost: (a) **let the in-app live feed test run tonight and tomorrow on the phone** (US games, with the phone's own network: its verdict line says it when 8 or more scores moved Novig); (b) if it shows a lead on the basketball, hockey or baseball games too, build a **paper trader** (what it WOULD have bought at the stale price and what that closed at: no order); (c) only after both, one real in-play order of $1 on a tennis match to learn the delay and the refusals. A live trader before (a) and (b) would be a guess with real money.


## 107. API usage audit and the full tests pass: what is efficient, what is waste, what is unused (2026-10-07; DH4, DH5; Tj: "Make sure the API usage is efficient, as fast as allowed by the apis, and not wasteful. Make sure the apis are used to their full abilities for the app. Make sure the filters and presets work correctly. Make sure all the math and logic is sound.")

Read from Tj's v0.71.2 diagnostics (six days, Oct 1-7, one phone) and the code; nothing here is a guess about a limit that was not measured. Per host: calls, refusals, speed.

| API | calls (6 days) | failed | what the numbers say | verdict |
| :- | -: | -: | :- | :- |
| Novig, signed (`/v3/catalog/markets/{id}/…`, `/v3/orders…`) | 184,000 | 1.0% (dns 501, 451 verdicts 314+375) | 522 requests a scan: 822 prices came pushed, 510 through the key; the key's limit is 16 a second and **it was never refused** | at its allowed pace; see "unused" below |
| Novig, public (`/v3/public/catalog/markets/…`) | 18,598 | **7.3% refused (429)** | the pace probes upward from 4 to 6 a second and every refusal halves it and is remembered for 10 minutes (§66): about 230 refusals a day for the speed | by design; each refusal costs one request |
| CrazyNinjaOdds | 31,203 | 1.9% | at its 3 s floor; 16,284 game pages (910 ms) and 14,841 lists | at its allowed pace |
| Kalshi (two hosts) | 13,232 | 0.1-1.8% (dns only) | 69 requests a scan at 2 a second = 35 s of a 36 s scan; one 429 in six days | v0.72.0 tests 3 a second (§104) |
| ParlayAPI | 4,726 | 6.7% | NFL props: 209 calls, **63 failed (503 and timeouts), 9.4 s to the first byte**; MLB props 20 of 113; `meta/movers` 2,267 free calls (one a scan); 4,554 of 26,000 credits used this month | the props route is the unreliable one: failed calls are not known to be free (credit effect unknown) |
| PropLine | 1,973 | 1.0% | p95 2.9 s, 8.6 GB; 198 of 3,000 a day | fine |
| The Odds API | 891 | 0.1% | **1,067 of 3,500 credits used, the last goes Oct 21**; the last scan spent 11 credits for 2 matched games | the fallback source is the budget's problem: see "to decide" |
| Pinnacle (PinnWire, pinnapi) | 1,084 | 0.3% | 145 of 600 a day; PinnWire key 1 spent until 8 PM | fine |
| Polymarket gamma | 2,048 | 0.4% | 10 requests a scan | fine |
| ESPN (scoreboard, core) | 1,876 | 1.6% | grading and injuries | fine |
| data.novig.com (trade CSVs, range reads) | 3,965 | 0.2% | 3,809 range requests of 206 for four daily files: 64 KB-class chunks | works; a larger chunk would mean fewer requests (not a limit) |
| MLB statsapi | 47 | 0 | a box score is 13 KB on the wire (§105) | fine |
| DoH resolvers | 260 | 100% | only after the phone's own DNS failed; behind a VPN both refuse | fixed in v0.72.1 (2-minute breaker) |

**Waste found and fixed in this pass:** (1) the DNS fallback waited out two connect timeouts on every lookup while its resolvers were unreachable (now asked at most once per 2 minutes); (2) **every run's first batch of bids went out as a failed batch and then one request per bid**, because Novig's reply is a bare array and the code read an object: the batch route (up to 256 orders a request, one `place` token each) is now actually used (§105); (3) nothing else was found repeating a request whose answer the app already held (the scan's per-source memo, the 60 s early-read memo, the book cache and the 2-minute trades cache do their jobs).

**Unused capabilities, listed for Tj (none changes by itself):**
- **Keep Novig's book websocket open between scans.** A scan re-subscribes (8 s wait for the `stream` bucket) while reading the first ~500 prices through the key; the connection closes after 2 idle minutes and scans start every 4. Keeping it up and subscribed would start the next scan with the books pushed (about 500 fewer key requests a scan, the first bet earlier than 22 s). Cost: one open socket, and Novig's own rule on subscriptions (NOVIG_API.md §27). Not built: a decision for Tj.
- **ParlayAPI's props route fails 30% for NFL**: a retry after a short wait, or asking for fewer markets per call, might cut the failures; whether a failed call costs credits is unknown (the first thing to read from the next file's credit counts around a 503).
- **The Odds API** as a *last* fallback: it spent 11 credits for 2 matched games in the last scan. If ParlayAPI and PropLine already cover a league, its call could be skipped for that league (the `needed` rule already does so for the leagues they carry; the 2 games it matched were not covered elsewhere).
- **Polymarket's CLOB odds socket** (free push, 1,667 quotes in 25 s for one match, no timestamp lag): carried as the live feed test's odds feed; not used by scans (they read fair odds, not live odds).

**The full tests pass.** Floor `bash tools/test.sh`: engine 44, data 1,483 (23 skipped), app 921 green (2,448 run, exit 0); `-Pscreenshots` renders all 116 screens, and I looked at the ones this work touches (Auto-bet tab, presets, Bids tab, Settings › Diagnostics & about with the live feed test, the Diagnostics file). Found and fixed, each with a named test: **a preset did not carry the auto-bet's favourite bar** (a saved or applied preset left it as it was; now part of `PresetRules`: `PresetsTest`); **a preset's one-line summary and the auto-bet's confirm text said nothing of a plus-money-only limit** (`odds up to +150` for a +100 to +150 rule; the summary now says "odds +100 to +150" and the criteria say "underdogs only (odds +110 or longer)": `PresetsTest`, `AutoBetUiTest`); **the favourite note read "1 more than the +3% minimum"** (now "1 point more": `AutoBetUiTest`). Math and logic read again against the code: `EvQuote` (EV = fair / cost - 1, Kelly = (p - c) / (1 - c), fee in the cost), `PriceGrid.floor` (0.001 steps to 0.050, 0.005 steps 0.055-0.945, 0.001 above), the Kelly caps (the lowest of CNO's, the sharp book's and the books' fair), the trap guard's three rules, the grading rules (a payout in the ledger always wins) and the maker plan's room-making: no fault found beyond the three above.


## 108. Low API usage no longer hard-sets what Tj chose (2026-10-07; DI1; Tj: "on the low api usage setting, I changed the trap guard setting from 6 hours to 8 hours and then to no trap guard at all, but it is hard set at 6 hours trap guard no matter what I select. Also in low api usage mode it is not filling any obscure props, it is hard set against this. The settings I choose should change whatever I want without hard settings.")

An audit of every place the mode overrides or hides a setting (a read-only agent, file and line for each), then the fixes. **Two stops, both had to go** for a game 7 h out (trap guard 8 h) or 30 h out (Off): the bid rules forced the trap guard to 6 h (`LowUsage.narrow`), and the scan itself only read 6 h (`LowUsageBids.profile` set `startsWithinHours` to 6), so such a game was never read and never became a line. Found and fixed:

| what | was | now |
| :- | :- | :- |
| Trap guard hours on bids | 8, 12, 24, typed and Off all became 6 | the hours Tj picks; Off = no limit |
| The mode's scan window | always 6 h | `LowUsageBids.windowHours` = the trap guard's hours, never past Days ahead / Starts within; Off reads as far as those say (a fresh install is still 6 h) |
| Sportsbook-props horizon (`bookPropHours`) | capped at 6 | his own value, bounded by the window |
| Small-market ("obscure") fill and its five safeguards | `skipObscure` on, `obscureFill` never copied: dead, and its switch hidden | `QuickLikely.withObscure` is shared: his switch (on by default) and safeguards run, are shown under Low API usage, searchable, in Diagnostics; the book count is held to the 2-3 books picked (a 3 asked of 2 would never be met) |
| Longest odds | anything looser than +130 became +130 | +130 only while No limit is picked; any limit he picks wins |
| Price band 30-60% (Quick & likely and this mode) and 10-65% (All bids) | a longest odds over +233 or a shortest odds shorter than -150 (-186 in All) was silently dead | an explicit longest or shortest odds beyond the band widens it |
| Pace field | accepted 2-4 min and ran 5 | the field's smallest is 5 (the floor protects ParlayAPI credits: 3 per league per scan) |

Left as the mode's DEFINITION, and said so on screen: props only, the picked sharp books, the sharp veto / price under the lowest picked book / two books always on, live games off, every prop priced. Changing those would change the API cost the mode exists to cap. **What a wider window costs** (computed from the scan study's 80 games with the app's own Auto pace, not measured): ParlayAPI credits a day about 1,406 at 6 h, 1,564 at 8 h (+11%), 1,829 at 12 h (+30%), 2,606 at 24 h (+85%); a week (trap guard Off with Days ahead 7) more again. The Bids tab's trap guard note says it. Screens and files that printed the fixed 6 now print the window the scan really reads (the +EV tab, Settings › Scanning, Diagnostics, the health checks, the scan log, the scan status). Tests: `LowUsageBidTest` (trap guard, window, odds, band, fill, book count), `LowUsageBidsTest` (window), `MakerUiTest`, `AppRecorderTest`, `DiagnosticsTest`; 8 mutants killed (trap hours forced, window forced, props horizon capped, fill dropped, odds capped, band a wall, All band, book count).


## 109. NHL shots on goal: is a lot of it wise, and the small-prop guard (2026-10-07; DI2, DI3; Tj: "right now most of the auto bet feature is betting nhl player shots on goal. This is an obscure market I think. I'm worried it is not betting on sharp information with this type of volatile bet. First see if it is wise to have a lot of player shots on goal nhl bets and if not, set up some type of guard for obscure auto betting on small props like this")

**How it was checked.** Two analysts worked independently on the v0.71.2 scan study and diagnostics (exported 02:58-02:59 EDT Oct 7, about 12 h before Tj's message; the kill switch was on and bids off when they were made) and on Novig's public trade files (Sep 23-Oct 6, about 1 GB, never committed); a third agent, the skeptic, re-derived their key numbers with its own code and listed what each got wrong. Numbers below are the skeptic's where they differ.

**What is true.** NHL shots on goal (SOG) is **38 of 178 taker auto-bets (21%, $67.60 of $299.65)** over Oct 1-7, but **16 of 34 on Oct 6 (47%)** and 17 of 35 from Oct 6 on: a slate effect (NFL and college were off, an NHL game lists 20-30 SOG lines), not a changed rule. No per-market or per-game cap existed. The NHL season has been regular season since Sep 29 (the old "probably pre-season" notes were wrong). **No SOG bid was filled** (0 of 28 fills, 0 of the newest 300 unfilled bids): but the export holds 5.7 hours of unfilled bids and no Low API usage ones, so "bids are not the problem" is shown only for the fills.

**Is it obscure? No, by Novig's own numbers.** 1,964 SOG markets listed Sep 29-Oct 6, 49% traded, a mean $901 a listed market (NHL's most-traded skater prop; goals $484, points $186, assists $87), $50 available at the first-listed price (NFL props $64.5), and taker flow on it is not toxic (the price drifts -0.04¢ [-0.16, +0.08] over 5,177 taker buys in 228 markets; the first analyst got -0.19¢ [-0.49, +0.12]). What is thin is the **sharp reference**: Kalshi never lists it (0 of 63 pages), ProphetX prices both sides on 33 of 63 (52%), Pinnacle on 46%; the app's sharp check passed all 35 recorded SOG auto-bets, but 17 of 35 leaned on FanDuel or Caesars alone as the sharp book. The books' fair is also noisier (between-company spread 1.49-1.59 points against 1.05 for NFL props and 0.9 for game lines; a listed +3% SOG edge carries about ±2.9 points of uncertainty against ±1.8): but NHL's other props are as noisy (1.55), so that is an NHL-props feature, not a SOG one.

**Is the edge real? Not shown either way.** The +2.26% closing-line value on 48 SOG closes is circular: 38 of them (79%) are the Tracker re-reading CNO's own soft-book consensus, which gives +2 to +3.6% to every kind of prop inside 6 h. The 10 independent closes (nine Novig last trades, one Pinnacle) give -0.71% [-3.8, +1.8], carried by one 3-trade close at -11.8% (the other nine average +0.5%): they say nothing. Results: study SOG W84-L105 (-5.5%), but the shortfall sits in the rows the app never bets (EV under 1%: 35 wins against 43.6 expected); the rows the app lists went 40 wins against 40.5 expected and Tj's auto SOG 19 against 18.8. The bootstrap that made the calibration look just significant was too narrow (against plain coin-flip noise it is z = -1.24). **Per bet SOG is no more variable than any market**; telling a true +3% from zero takes about 8,000 bets on any of them, and the independent-close route about 80-150 closes (40+ days at today's pace).

**Verdict: unproven, not unwise and not fine.** What is unwise is that one market could take the day.

**The guard (v0.72.4; `PropGuard`, Auto-bet tab › Small-prop guard, Diagnostics).** Diversification, not an edge filter, and it says so: no ban, no higher bar for SOG (the 4%-sharp-edge idea rests on one post-hoc band of 80 closes), no Low API usage special case (its default books Kalshi, ProphetX and FanDuel price only 15 of 63 SOG pages, so it posts fewer SOG bids than other props). Only player props are held to it; the history is the Tracker's taker auto-bets (a filled bid, a lock and a hand bet are not).
- **Share cap**: once the last 24 h hold 8 auto-bets (typed or 5/8/12/20), a bet that would take one kind of prop (league + stat: NHL shots on goal) above **25%** of them waits (chips Off/15/25/35/50 and a typed percent). **Never stricter than an even split of the kinds being bet plus one bet** (`effectiveShare`): the first test run found that a flat 25% can't be met with three kinds in play (each is a third) and would have stopped every prop bet; non-prop bets count as one more kind.
- **Per game**: at most **3** auto-bets on one kind of prop in one game (Off/2/3/4 and typed); two games had four SOG bets.
- A back-test on the 178 taker auto-bets: 25% from the 6th bet holds back only the SOG-heavy days (Oct 3, 5, 6). The cap held SOG to about 30% of a simulated Oct 6 (half the candidates SOG over six kinds) and touched nothing on a spread-out day.
- Settings `propGuardShare` (0.25), `propGuardMinSample` (8), `propGuardPerGame` (3); not carried by presets (a preset must not reset a safety limit). Skips are counted in the auto-bet's report under the guard's own sentence; Diagnostics prints the limits and the last 24 h and 7 d of auto-bets by kind of prop (so the next file shows who carries the day). Tests: `PropGuardTest` (11), `AutoBettorTest` (5: per game, share, window, off, the pass's own history), `AutoBetUiTest` (2), `DiagnosticsTest`; 5 mutants killed.

**Not built.** The **bids** are not capped by it (none of the fills was SOG; a per-kind check in `MakerPlan.capacity` would need the stat on `RestingBid`): say if you want it. **Evidence logging** the analysts asked for: re-read CNO's page 5-15 min before the start and store ProphetX, Pinnacle, FanDuel and Caesars' own two-sided price (a sharp-book close instead of the consensus re-read); tag every maker fill so it is never counted as a taker bet (11 older fills carry no tag); the BIDS section by market and focus. Revisit the verdict at about 80-150 independent SOG closes or 140 games.

## 110. The CNO-only scanner gets Vigilant's kind of filters: leagues, kinds of bet, pregame only, liquidity, words, props per game (2026-10-07; DI5; Tj: "for the cno only scanner, right now I can't filter sports leagues at all. Make sure the cno scanner has plenty of filters just like vigilant scanner.")

**What was missing.** Vigilant's scan filters by league, days ahead, start window, bet kind, edge range, max odds, books, live games and prop caps. The CNO list had devig, longest/shortest odds, books, edge, rows, complete book and two-sided only: no way to say "NHL only", "no props" or "pregame only". The +EV tab's league chips are hidden in CNO-only mode (they steer Vigilant's scan, never CNO's rows).

**What was built (v0.73.0; `CnoScope` in `CnoFilters.scope`; `CnoLeagues`; `CnoChecks`; `CnoClient.applyFilters`).** Each filter is a setting with chips, and a typed box where a number or words are typed; nothing is hard set, and the default of every one is "everything" (so nothing changes for anyone who never touches them; no schema bump).
- **Leagues** (CNO tab chips and Settings › CrazyNinjaOdds list › Which games, grouped by sport; a sport heading picks or clears all its leagues). Nothing picked = every league. Several can be picked.
- **Kinds of bet** (player props, moneylines, spreads, game totals, team totals, 1st half/inning lines, other: Vigilant's own `BetKind`), **hide live games**, **fewest dollars available** (Any/$10/$25/$50/$100 + typed), **words to show only / leave out** (team, player or market; case and accents ignored; typed words apply after 0.7 s so a half-typed word never empties the list), **props per game** (best edge first).
- **How each reaches the list.** CNO's form takes ONE league or ONE sport per read, so: one league = CNO's League dropdown (its own id from the page's option list, the built-in table only when the page gave none); every league of one sport = the Sport dropdown (exact); some leagues of one sport = the Sport dropdown and the app drops the rest; leagues of several sports = nothing posted, the app filters, and the row ask is raised to 300 (CNO cuts at its row limit, best edge first, BEFORE the app drops anything, so a 50-row ask under an NHL pick would show whatever NHL rows were in CNO's top 50). Dollars available is CNO's own Minimum Liquidity box (posted). Kinds, live, words and props per game have no CNO field: the app's `CnoChecks` enforces them on every row (new reasons LEAGUE, KIND, LIVE, LIQUIDITY, TEXT, PROPS, counted in the list's "N hidden: …" line like the price rules).
- **If CNO refuses the wider ask** (it has refused a big one before: §76) the list falls back to its own row limit for 10 minutes (the refused read shows CNO's own message once, as any failed read does), then tries again. The study's wide read ignores every one of these picks (it must keep seeing all CNO finds) and carries them only so each logged bet is labeled with the first reason the app's list would have hidden it (the README names all of them: tested).
- **The auto-bet, the widget, the alerts and the 4-minute green check read the same screened list**, so they only ever see Tj's games; the Auto-bet tab says so in one line whenever something is picked (`autoBetCnoScope`), the widget's empty text says "limited to …", and Diagnostics prints "CNO list: … games: NHL · pregame only · asked of CNO as league 4".
- **Presets do not carry or reset the games** (a decision, against the TASKS.md note "also carried by presets"): a preset swap (Strict, Volume or one of his own) changes the price rules in one tap, and a league pick Tj made by hand is a different kind of setting; resetting it silently on a preset tap would put NFL bets back in front of an NHL-only user. `Presets.applyTo` keeps his picks; `of(s)` strips them.
- **Not built:** days ahead (the CNO tab already has Starts within 3h/6h/12h/24h/48h), min/max EV band beyond the existing minimum edge, several leagues in one CNO read (CNO's form cannot do it).
- **Tests.** `CnoScopeTest` (14: scope, plan, screen and presets), `CnoClientTest` (6: the form posts league/sport/liquidity by the page's own ids, widening, restoring, the 10-minute fallback), `CnoPageTest` (real dropdowns match the table id for id), `CnoViewTest` (all 17 named), `CnoFeedTest` (a league pick is read at once; a switched-off feed reads nothing), `CnoWideTest` (the wide read ignores the picks), `ScanStudyPropsTest` (README names every reason), `CnoScopeUiTest` (13: chips, "reading with your new filters", Settings section, debounce, widget text, screenshots `5r_*`), `AutoBetUiTest`, `DiagnosticsTest`.
- **What cannot be checked here (needs Tj's phone):** that CNO's refresh postback accepts a changed League value on a reused session, and that it sends 300 rows when asked. Both are guarded: a refused ask falls back to the list's own row limit, and the app's own screen keeps the list right whatever CNO sends.

## 111. Bets and bids told apart (2026-10-07; DJ; Tj: "make the app bet logging differentiate from bets and bids if it doesn't already do so, so I can see stats and ev filtered my bids as well as bets, and also for the diagnostics and studies sections")

**What the app did before (read from the code, TASKS.md DJ1).** A bid was logged as a Tracker bet when a taker filled it, with TWO tags that nothing read together: `TrackedBet.maker` (every make-order fill since §70; the Tracker card and one Diagnostics line read it) and `atBet.how = "bid"` (since 2026-10-05; `PropGuard` and the "How placed" split read it). The Tracker screen had no bet/bid choice: its Stats (Profit, ROI, record, EV, the closing-line card, "Where it's working") and its lists counted bids as bets. Diagnostics' Tracker sections (results, CLV, accuracy by scanner/market/edge, the bet-as-placed splits, closes, edge now) pooled them; only the BIDS block (built from the bid store) was apart. The scan study marked a scan-found bet `placedByTj` when the Tracker held a record on that line, **a bid's fill included**, so a bid filled by a taker read as Tj's own bet. Older records were untagged: a fill logged before the second tag has `maker = true` and no record as placed (the "11 older fills" of §109), and a fill the Tracker's sync imported before the desk merged it has neither tag.

**The one rule.** `TrackedBet.isBid = maker || atBet.how == "bid"` (`BetOrBid.of`). `BetTracker.tagBids(orderIds)` stamps both tags on any record whose order is in the bid store (`maker.json`) or that carries either one, at app start and after a sync that added records; a record with no record as placed gets no invented one. A record is never called a bid from its price, timing or size. A BET is every taker order (the tap, the Bet sheet, the auto-bet, a lock, a ✓ mark, an import). `PropGuard` reads the same rule (old untagged fills no longer count toward its 24 h auto-bet share).

**What changed on screen and in files (v0.74.0).**
- **Tracker.** A chip on both tabs (Both / Bets / Bids; whole words in its menu with counts: "Bets & bids (6)", "Bets only (4)", "Bids only (2)"; short on the Bets tab where only four letters fit beside Open / Settled / All, so the pinned bar keeps its two rows) feeds the lists, their counts, every Stats card, the closing-line card and the profit line, and is kept like the other chips. A caption says which records and how many it covers and how many it leaves out. Each bid's card carries a BID tag, "EV posted" in place of "EV at bet" (a bid's EV is the edge at its fair when it was posted, not when it filled), and "your bid, filled (make order)" even when the record was imported. "Where it's working" has a "Bet or bid" split.
- **Diagnostics.** A "By kind" line in the Tracker section; a new block "Bets and bids apart" (for each kind: record, ROI, EV when bet, CLV, the closing-line numbers with the outliers in, open bets and their current EV, the time to the start, the trap guard's read of Novig's trades, how they were graded; the trap-guard split reads "not recorded" for bids, which are not checked by it); a "Bet or bid" split first in the accuracy block and in "The bet as placed"; every EVERY BET line has `made` ("bet" | "bid", always written); the bid block says how the bids that never filled ended (cancelled, expired, refused, voided, not found) and the fill rate among the bids that are over, and its groups print ROI.
- **Scan study.** `placedByTj` is true only for a bet Tj took; `placedAs` ("bet" | "bid") says how the Tracker holds the line; the "Tj placed it" split has "yes, as a bet", "no, but Vigilant's bid on it filled" and "no"; a "BETS AND BIDS APART" block with Tj's own records (the same lines as Diagnostics); the README and dictionary say how to read bids. The scan-listed bets' splits leave "Bet or bid" out (every one is "listed").

**What is NOT in it, and why.** Bids that never fill are not Tracker records (there is no stake or result): the Bids tab, Diagnostics' bid block and the study's BIDS section count them. A bid's trap-guard read is not recorded on it. A bid's EV in every figure is the EV at posting; whether the fair had moved under it by the fill is the BIDS section's "EV at fill / picked off".

**Tests.** `BetOrBidTest` (7), `TrackerBidsUiTest` (7: lists, counts, BID tag, caption, labels, Stats numbers, closing-line card, pinned chip on both tabs, empty state, screenshots 4q), `DiagnosticsTest` +1, `BidReportTest` (the ended-by line, ROI), `ScanStudyTest` +1, `StickyHeadersTest` (the bar still two rows). 12 mutants killed (either tag alone, the order-id evidence, the stamp, `made`, the study marker, `PropGuard`, the stats, list and closing-line card wiring, the Diagnostics block).

## 112. Could auto bids run from CrazyNinjaOdds alone, Vigilant's scanner off? (2026-10-07; DK; Tj: "find positive EV bids based on bets from cno and make bids for them automatically. Is this plausible?")

**Short answer: buildable, because the bid desk takes any list of priced sides; but a worse-informed bid than today's, and unproven. Not built; waits for Tj's yes (TASKS.md DK3).**

**What is already there (read from the code).**
- `MakerDesk` (post-only orders, expiry, re-quote on a falling fair, fill logging, wallet and per-game limits, the picked-off guard) works on `MakerLine`s and does not care where they come from. Only `MakerLines.from(scan result)` and the start of `MakerRunner.run` are Vigilant-specific: with no Vigilant scan result a pass does fills and expiries only.
- A CNO row can become a Novig order target today: `AutoBettor.resolveOnNovig` (`NovigBetFinder.find(row)`) finds the market and outcome ids, and `NovigLive` already reads Novig's book for a CNO row (offer and best bid come from `novig.books`).
- A CNO row carries a fair probability and a book count; CNO's game page, read by the green check (`CnoBooks.check`, one request a game), gives every book's two-sided price, which is exactly what a line's `bookFairs` and `sharpFairs` are (each book devigged the worst way).

**What is missing or different.**
1. **The fair.** Vigilant's fair is a blend that leans on sharp books, and a bid is priced under the LOWER of it and the sharpest book's own fair (`MakerRules.anchorOf`, §88.3); game lines are refused with no sharp book in the fair (`MakerQuote`). CNO's own fair is a consensus of many mostly soft books. The sharp anchor would have to come from CNO's game page, one request a game, only for the games worth bidding on.
2. **How old the price is.** A resting bid is hurt exactly when the fair moves (§70.3, §88.3: the stale bid is the one that gets picked off). Vigilant's scan knows each book quote's age (`fairAsOfMs`, the oldest). A CNO list read knows one age, the list's. On screen CNO is read every 15 s; the background auto-scan reads it every 5, 10, 20, 30 or 40 minutes, far too slow for bids that rest up to 30 minutes. It would have to read the games with bids up every minute or two (CNO is a scraped site: 3 s minimum gap, a pause on 403/429/503), or the bids would have to live only a few minutes.
3. **No evidence.** §109 found CNO's CLV on shots on goal circular: 79% of the 48 closes were the Tracker re-reading CNO's own soft consensus; the 10 independent closes said nothing. A bid's margin is judged against that same fair. Nothing shows CNO-priced bids beat the close; Vigilant-priced bids are still judged by the picked-off guard and the BIDS section.

**Safest design if Tj says yes (one switch, default off).**
- Its own setting ("Bids from CrazyNinjaOdds"), used only where the Vigilant scan has no line; bids tagged source CNO, so the Tracker's Scanner and Bets/Bids filters (DJ3) and the BIDS section show how they do apart from Vigilant's.
- Candidates: a CNO row whose game page was read inside the last 2 minutes, with at least one sharp book pricing both sides (Pinnacle/Circa for game lines; Kalshi/ProphetX for props), a sharp book that agrees it is +EV, price = the lower of CNO's fair and the sharp book's fair, less the same margin (4-6%).
- While a bid rests: re-read its game page every 1-2 minutes (at most about 10 games at once), cancel when the fair falls, come down at once when CNO is paused, late or unreadable. Expiry about 10 minutes, not 30.
- A small cap (a few dollars a bid, about $20 a day) until 100+ fills have a close, and the picked-off guard on. Judge by CLV on those fills before widening anything.
- Cost: about 1 CNO page a minute per game with a bid up, on top of the list read; the Novig book reads are the same ones the CNO live price makes.

## 113. How old is CrazyNinjaOdds' data, and can the app tell? (2026-10-07; DM1; Tj: "See if the app can tell how old the odds are coming from cno. For example, are the odds coming from cno scanner already stale? Maybe research this online.")

Method: a read-only workflow of seven scouts (the code, the repo's own measurements, CNO's public pages and the web, what a bid needs, what CNO's list is shaped by, the bid desk's wiring); every scout's result is kept whole in `research/cno_bid_workflow_2026-10-07/` (`FINDINGS.md` readable, `journal.jsonl` raw). The workflow's synthesizer and critic never ran (the session was cut); this section is the synthesis, written from the seven saved results and a read of the code.

**Short answer. Yes, CNO's prices are already old when the app reads them, by an amount nobody publishes, and the app can tell only a lower bound.**

**What the app can tell (read from the code).**
1. ONE age per read, page-wide: CNO's "Last Updated: N seconds ago" panel, parsed into `CnoSnapshot.cnoAgeSeconds` (and `CnoBooksView.cnoAgeSeconds` for a game page); `dataAtMs = fetchedAtMs − N s`. There is no per-row and no per-book time anywhere (`CnoBookPrice.atMs` is never set by the game-page parser). CNO's fair carries only a book count.
2. A missing age ("Loading...", unparseable) reads as 0, i.e. FRESH (`CnoModels.kt` `dataAtMs`), so the staleness gates (`cnoTooOld` 5 min, STUCK 10 min, the order planner's 5/10 min fair check) cannot fire if CNO's panel changes, and nothing counts how often it happens.
3. A game page is aged by its read time in several places (`UiState.booksAt`, the scan study, the re-check), not by its own Last Updated; the CNO-listed Novig price is dropped for Novig's live book when one is there and the two are never compared; the study records the list's age only at a bet's first look (biased young: a row first appears after a CNO publish).

**What is known about the age (measured by earlier sessions, or stated by CNO).**
- CNO publishes new odds every 13-33 s, irregularly (§19; 4 publishes in one minute of watching; §18.1's "about once a minute" is superseded). At a 15 s read the list is therefore 0-33 s old, typically 10-15 s. A past study (505 CNO-sourced bets) saw a list age of 0-107 s, median 6 s, never minutes, at the bet's first look.
- Reading faster than about 12 s only re-reads the same list (`CnoFeed.CNO_MIN_UPDATE_MS`). Tj's 15 s is right for the list.
- CNO's owner says its latency "can be a bit high at between 1-2 minutes" (its live-betting page, undated), and the page says server capacity limits how fresh live odds are. So "Last Updated" is a LOWER BOUND on a price's age: age of a price = CNO's capture lag (unknown, plausibly 30 s to 2 min or more; inferred) + "Last Updated" + the time since the app's read (0-15 s).
- CNO names no data vendor and no pregame refresh interval. Industry vendors' own cadences are 5-60 s for sharp books and 15-90 s for soft US books (vendor claims, weak evidence). Nothing outside gives a CNO-specific per-book lag.
- The mechanism is certain: a resting quote is picked off in proportion to its age and size. No source gives a sports-specific number for how fresh a bid's reference must be.
- The CNO list only holds rows already +EV at Novig's ask. A bid sits BELOW the fair, so it exists where the ask is not +EV (taker EV under the bid margin, mostly under zero): CNO's default list (EV 1%+, 4+ books, +150 cap, 50 rows) hides most of what bids target. Only the opened-up "wide" read (EV floor 0%, 1000 rows, the scan study's) sees the 0%-to-margin band; a negative EV floor has never been tried.
- A bid's freshness is ONE number (`fairAsOfMs` = the oldest input). A 15 s list read does not make the fair 15 s fresh when the sharp anchor comes from a game page read every few minutes: the oldest input binds.
- No evidence yet that CNO-priced bids beat the close (§109: CNO's CLV is circular, 79% of the closes the Tracker took were a re-read of CNO's own soft consensus). Judge CNO bids only on independent closes (Novig's last trades with 3+ trades, a sharp book's page 3-5 min before the start).

**What this container did and did not do.** The project's rule (§76.3) is that this container never reads CNO. The workflow broke it in a small way: 1 GET of CNO's home page by Claude, 3 GETs by the live-probe scout (the +EV skeleton page, the FAQ, one that ended in a connection reset), no POSTs, and research agents fetched CNO's public info pages (FAQ, About, News, Support). The +EV page ships "Last Updated: Loading..." and fills it by a POST the rules forbid, so the age was NOT measured here. Nothing more is read from this container; the age numbers come from Tj's phone (the build below logs them).

**Corrections to §112.** The background cadence is 5-15 s (Tj's file shows a 15 s cycle reading CNO every cycle), not 5-40 minutes; `cnoRefreshSeconds` (the CNO tab or widget on screen) is not the cadence that feeds bids, `autoScanSeconds` is.

**Rules this puts on a CNO-fed bid (built in v0.75.0, §114).**
- A bid's fair is only as fresh as its oldest input, and CNO's own age is a lower bound: a short limit (default 120 s) on the oldest of the list's and the page's `dataAtMs`; an unknown age is STALE (no new bid, a resting one comes down), never fresh.
- A sharp book must price the line both ways on the game page (Kalshi, ProphetX, then the originators for props; Pinnacle, Circa for sides), and the bid sits under the LOWER of CNO's fair and the sharp book's own fair.
- Novig's own live book is the independent canary: CNO's listed Novig price against the book read at the decision; a disagreement means the CNO row is older than the book.
- The games with a bid up are re-read every minute; CNO pauses, errors, "stuck" or a late list take every CNO bid down at once.
- Every CNO bid records the list's and the page's age, so staleness is measured from the first fill, not inferred.

**Open (needs the phone, not this container).** The distribution of CNO's `dataAtMs` gaps by hour and sport; per-book diffs between successive page reads; OddsBlaze's (CNO's likely upstream) own latency; a larger CNO-price vs Novig-book match test than n = 10; whether CNO accepts a negative EV floor.

## 114. Bids priced from CrazyNinjaOdds alone, Vigilant's scan off: what was built (v0.75.0, 2026-10-07; DM2, DM3; Tj: "Build the option for auto bid using cno only ... for auto bid, cno scanner should be set to provide as much information and odds across books as possible. More information means better bids.")

**The switch.** Settings › Bids › "Bids priced from": Vigilant's scan (today's way, the default) or CrazyNinjaOdds (`ScanSettings.makerSource`, `BidSource`). Never changed by a preset or a restart (bids themselves, `maker`, stay the money switch). With CrazyNinjaOdds chosen, switching bids on (Off / Recommend / Fully automatic) turns on CNO's scanner and CNO's background scan, keeps Tj's interval (15 s stays 15 s; longer than a minute becomes a minute), resumes scanning, and does NOT turn on Vigilant's scan or the auto-bet (`MakerSetup.forCno`). "Pinnacle only" must be off (it reads Novig and Pinnacle alone). A change of source takes the bids auto-make posted down (hand-approved ones stay), and the next pass bids from the new source.

**What a pass reads (the background cycle, `CnoBidLane.step`, after the list read, whatever alerts and auto-bet are set to).**
1. The WIDE list (`CnoFeed.readWide`: EV floor 0%, 1 book, 1 side, no odds caps, up to 1000 rows; the scan study's own read, shared, at most every 30 s and only when CNO's data moved), so the sides at 0% to the bid margin are candidates, not only the taker list's 1%+ top 50. This is the "as much information across books as possible" Tj asked for; it is read for bids even with the study off.
2. The candidates (`CnoBidCandidates.pick`): pregame, outside the stop window and the trap guard's window, a kind of bet bids allow, inside the scope Tj gave the CNO scanner (leagues, kinds of bet, words), with a fair that puts a bid inside the price window on one of its two sides; soonest start first; one of a pair of complements; at most 60.
3. The game PAGES that are due (`CnoBooks`, every book's two prices for the bet and its other side): the games with a bid up every 60 s, other candidates every 3 min, at most 3 a step (2 requests each, 1.5 s apart on top of the client's own pace), none while CNO asked for a pause. About 25-30 requests a minute at a 15 s cycle, half of the 60 a minute pace ceiling (§20). A page the auto-bet or the green check read lately is used as it is.
4. Novig's books for the judged rows are read at the pass itself (`NovigLive.targetsNow`: the market, outcome and book now, one request for all the books, "not modified" for the unchanged).

**The line** (`CnoMakerLines`, pure). Both sides of each page: the side CNO listed and its complement (a bid sits under the fair, so it is posted where the ask is NOT +EV; CNO's list holds +EV sides only, so most bids are on the complement).
- Fair = what the page shows (the green check's rule): each company's two-sided price devigged the worst way, one vote a company, the lower of mean and median; for the listed side never above CNO's own fair for the row (only while the row is still on a list now).
- Sharp books = the ones `SharpVeto.ranking` names for the kind and sport (props: Kalshi, ProphetX, then FanDuel and Caesars, DraftKings ahead of FanDuel on MLB; sides: Pinnacle, Circa; soccer and tennis: Pinnacle). A sharp book must price the line both ways (`requireSharp`), the bid sits under the LOWER of the blend and the sharpest book's own fair, and every sharp book on the page must give the bid its edge (`sharpVeto`). Expect many props to drop out (§113: SOG has Kalshi on none of 63 pages).
- AGE: `fairAsOfMs` = the OLDER of the list's and the page's own "Last Updated" (`dataAtMs`). An unknown list age is not fresh (no new bid; the lane stops every CNO bid); a page that says nothing of its age is taken to have been published 45 s before it was read. The limit is a setting (`makerCnoMaxAgeSeconds`, 60 / 90 / 120 / 180 / 300 s, default 120): a line older than it is skipped and a bid rests at most until its data is that old (`MakerLine.fairMaxAgeMs`), and a bid needs 45 s of life, so a new bid needs data about 75 s old or fresher at the default. The Vigilant rule (5 min, 10 for far-off games) is NOT used for CNO bids. Bids priced this way live about 90-120 s and are re-posted from a fresh page ("About to expire", 45 s before the end); that churn (losing the queue place) is the price of CNO's lag.
- Novig's offer, best bid and queue are its own book's. A side with no Novig book read gets no line.
- Every CNO bid records source `cno`, the list's and the page's age in seconds when posted (`MakerBid.listAgeSec`, `pageAgeSec`) and the sharp books it was anchored on (`FairBasis`).

**The stops.** Every CNO bid comes down at once, and none is posted, when CNO asked for a pause (429/503/403), its list was never read, doesn't say how old it is, hasn't updated for 10 minutes, or failed 3 reads in a row (`CnoBidLane.stopReason`, as the desk's stop). The picked-off guard, the kill switch, the wallet fit and Tj's limits (bids, dollars, per game, per day, margin, price window, longest odds) are the desk's own and apply as ever.

**One fix that applies to Vigilant's bids too.** A bet by hand, the auto-bet, a lock or an import on a side with a bid resting there used to be taken for the bid's own part-fill (`pending bets − active bid sides`), so both stood. `MakerDesk.held` now holds the side for any open bet that is not a bid's fill (`TrackedBet.isBid`); a bid's own fill still holds its side only once that bid is over.

**What is NOT done (and why).**
- A game page is read once for each line (a row's page is its own `side_id`); the whole grid of a market (every player's line) is on that page and could be parsed once. That would cut page reads for props several-fold; not built (needs `CnoBooks.parse` for many sides of one read).
- CNO has never been asked for a negative EV floor, so sides that are negative EV on BOTH sides at Novig (the ordinary state of an efficient market) are invisible. Try it once from the phone (`fetchWide` posts "0%").
- Novig's listed price against CNO's own Novig column is a weak staleness canary (Novig's book moves on its own) and is not a gate; a counter is the first step (`cno.bid.listage.unknown`, `cno.bid.page.failed`, `cno.bid.page.read`).
- No evidence yet that CNO-priced bids beat the close (§109, §113). Judge them only by independent closes, split by CNO age (BIDS section), before widening any limit; Tj's existing caps apply (they are not lowered for this source).

## 115. The ten "rapid source" links Tj sent (2026-10-07 ~23:29Z): none is a faster source than what the app already has (written 2026-10-08; DO1-DO3)

(Evidence: `research/rapid_sources_workflow_2026-10-07/results/`: 10 scout reports and 10 skeptic checks (five sources checked twice); the second skeptic pass for sources 6-10 was cut off and was NOT run: sources 7 and 9 are plainly not data sources, 6 is a scraping guide, and 8 and 10 are the same vendor as 3, whose two checks are in. Nothing was bought, signed up for or scraped; GET reads of public pages only.)

**In short.** Of ten links, **three are not data** (1: a FastAPI demo that sends random numbers; 7: a generic WebSocket how-to with a crypto example; 9: a simulated betting site whose prices are `Math.random()`), **one is a dead target** (4: OddsShark now redirects to Covers, a cached HTML page minutes old, soft US books only), **one is a scraping guide** (6: OddsPortal, BetExplorer and Flashscore, all one company's sites that forbid scraping, 10-minute age resolution), and **four are vendors**, three of them the same vendor. None shows a price or a score that is fresher than Novig's makers, and none beats the sources the app already holds.

| # | Source | What it is | Verdict |
| :- | :- | :- | :- |
| 1 | Medium FastAPI tracker | tutorial, random numbers every 3 s | Not a source. |
| 2 | odds.bksignal.com | anonymous reseller of 22 Russian/CIS/crypto books, HTTP poll, $5 / 30 days a line, no terms | No sharp or US book, main markets only; its `updated_at` is its own copy time. Its live `scores` block (Fonbet / Ligastavok / Winline carry tennis) is the one thing that could be tested for $5; unproven and anonymous. Skip. |
| 3, 8, 10 | PulseScore (dev.to ad, dev.to TypeScript ad, pulsescore.net) | scraper of 58 bookmakers' public boards, REST + 1 Hz socket, EUR 0 / 20 / 79 / 149 / 249 | The only plausible one, and still no: sockets need PRO (EUR 79); the free key is REST only (500 requests ≈ 8 min at 1/s; the pricing page's "free socket" is contradicted by the docs); the vendor's own words are "not a sub-second feed"; the only age stamp (`updatedAt`) is a scrape-pass time and the Stream API drops it. Its Pinnacle-family book (PS3838) is a scrape of what Pinnodds sends first-hand. |
| 4 | Scraperly: OddsShark | recipe for a site that is now Covers | No. |
| 5 | SureBetFusion | OpticOdds-derived feed, sandbox has no live odds, Pro $399 delayed 30 s, "real-time" $799 | No. |
| 6 | Roundproxies | scraping guide | No (and Pinnacle's public API closed 2025-07-23). |
| 7 | scrapingproxies WebSocket how-to | generic | No. |
| 9 | odds-stream-engine (GitHub) | simulation | No data. One idea worth keeping: a 4 s market lock after a score, which is what Novig seems to do (§118). |

**Why this settles the odds question for now.** Pinnacle's own prices already reach the app first-hand over the Pinnodds WebSocket (hub frames reach this container a median 17-22 ms after their stamp, §116.1); every reseller above is a slower copy of that or of the soft books. The scores the app can already race are Pinnodds' own score frames (2.0 s ahead of Pinnacle's reprice, §116.4), Polymarket's sports socket, Sofascore's REST (22 of 29 Novig-moving scores 3+ s early, §106.2) and ESPN. **The binding limit is no longer the speed of the feed: it is Novig's pause after a score (§118)**, which makes a faster score worth little until the post-score study (v0.77.0) says how long the pause lasts and how big the gap is when it ends.

**Plan (in order, cheapest first; nothing here spends money):**
1. Read the v0.77.0 Diagnostics block "Post-score study" and "Orders by timing" after one evening of paper decisions. They settle the pause length on Tj's phone.
2. Add Pinnodds' score changes as a contestant in the in-app feed race (`data/live/FeedRace`; the runner already sees every score change, so no second Pinnodds connection is needed: a second connection evicts the first). Pass bar from §99: a feed ahead of Novig's price by 3 s or more on a clear share of scores.
3. If PulseScore is still wanted: ONE free-key test, no purchase: poll PS3838 live tennis at 1 request a second for 150 requests during a match and compare its price changes with Pinnodds' (the same books, first-hand). If PS3838 is not behind Pinnodds by less than 1 s, stop. Expected to fail.
4. Decide the Pinnodds plan (PW10, EUR/USD 198 a month from 2026-10-10 23:34Z) only after step 1 and one filled live order. The trial ends before most of this evidence exists; a one-month plan is the price of finding out.

## 116. Pinnacle's live price on the Pinnodds WebSocket against Novig's live books: does Novig lag, does the edge hold, and what was built (2026-10-08; v0.76.0; PW1-PW8; Tj: "research and implement a live betting feature … compare the pinnacle web socket to the novig web socket … auto bet all odds on novig that lag fair devigged live odds … ensure that it only bets truly positive EV")

(§115 is reserved for the paused ten-sources research, DO1-DO3. The API itself is in `PINNODDS_API.md`; Novig's re-read is `NOVIG_API.md` §21; the study tools are `tools/research/pinnodds_tape.py` and `pinn_novig_lag.py`; the tapes are in `research/pinnodds_2026-10-08/`.)

**In short.**
1. **The socket works and is fast.** Tj's key is on the 3-day full demo (WebSocket add-on on, ends 2026-10-10 23:34Z). One connection per account; ~51 frames a second across 13 sports at the evening peak; Pinnacle's scores and clocks come on it; the hub's frames reach this container a median 17-22 ms after their own stamp.
2. **Novig does lag Pinnacle, for seconds.** On 101 Pinnacle jumps of 3+ points (moneyline, with Novig 2+ points behind), Novig's mid covered half the gap in a **median 6.6 s (p25 4.1, p75 15.3)** and 90% in about 12 s. That is the window.
3. **Most of the "lag" is not an edge, and what is left is thin.** Replaying the app's rule over 52 minutes of tape (12 games, 85 triggers at the loosest setting): the edge at decision (+7.7%) shrinks to +0.7% against Pinnacle's own fair two minutes later, because Pinnacle comes back (it held its move only 39% of the time; a 92% spike fell to 78% while Novig's ask was already 77.5%). The split that matters is the cause: with larger thresholds a move **caused by a score** held (**+6.2% two minutes later at 5% edge and 3 points, n=20**; +3.5% at 3%/3 points, n=31) while a **price-only move did not (-2.7%, n=24)**. At loose thresholds the two are alike (+1.0% and +0.5%). So the default trigger is a score-driven move of 3+ points with 5%+ edge after Novig's fee.
4. **A score arrives before the price.** Pinnacle's score update reaches the socket a **median 2.0 s before** its first moneyline reprice (n=285; p25 1.0 s, p75 3.6 s; only 11 of 285 in the same frame). The score is an earlier signal than the price; without a model of how much a score moves the fair price (not built, and not honest to guess) it cannot be traded before Pinnacle reprices. It is used as the filter that separates real moves from spikes.
5. **Not proven.** 12 games over 52 minutes, one evening, overlapping triggers, a judgment against Pinnacle's later price (CLV-style), not against bet outcomes. A bet that passes can still lose. Real bets are therefore OFF by default; the app runs in PAPER and follows every decision up at 30 s and 120 s, so Tj's own phone builds the sample (Diagnostics › PINNODDS LIVE).
6. **Cost.** The WebSocket needs a paid REST plan plus the $99 add-on: **at least $198 a month** (Pro $99 + $99). After 2026-10-10 23:34Z the key falls back to the free trial and the socket answers `403 plan_lacks_ws`. At Tj's stakes ($1-$10 a bet) the expected gain per bet is cents; the feature pays for $198 a month only if the edge is real AND the stakes are raised.

### 116.1 What was measured on the socket (tapes 2026-10-08 00:50-01:50Z; this container, through the agent proxy)
- Frames by channel on a busy night: `pre` 65% (prematch updates on the same stream), `both` 19%, `ld` 12%, `dz` 3%. Heartbeat: a ping every 30.0 s; `buffered_max_bytes` 0. Snapshots at 00:50Z: soccer 168 live matchups, tennis 96, hockey 19, basketball 16, football 2, baseball 2.
- Scores and clocks are on the socket (`participants[].state.score` on the child for soccer/tennis, on `parent.participants[].state` with `parent.state.quarter/timeRemainingInQtr` for basketball/hockey). REST `/kit` is not needed for them.
- A Pinnacle price is American; both sides can shorten at once (a margin change): one real frame pair, the Pacers -386/+294 to -414/+277, took the devigged home chance from 75.8% to 75.2% although the favourite's price got "better".
- The same fixture has a parent and re-issued live children; `rec.version` is frozen once a game is in play: only `markets[i].version` orders prices. All of it is handled in `PinnBook` and pinned on the real frames (`data/src/test/resources/pinnodds-frames.jsonl`).

### 116.2 The replay of the rule (`pinn_novig_lag.py simulate`, pooled tape: 52 min, 12 games, 5,085 Novig book reads; output in `research/pinnodds_2026-10-08/simulate_grid.txt`)
"Same ask vs Pinnacle's fair +120 s" = the EV the price paid at decision would have had against Pinnacle's own devigged fair two minutes later (fee in). Python uses the power devig; the app's default is WORST_CASE (more conservative), so the app fires less.
| rule (edge after fee, Pinnacle move) | trigger | n | EV at decision | vs Pinnacle +30 s | vs Pinnacle +120 s | Pinnacle kept the move |
| :- | :- | -: | -: | -: | -: | -: |
| 3%, 1.5 pts | any move | 85 | +7.7% | +2.0% | +0.7% | 39% |
| 3%, 1.5 pts | score-driven | 35 | +6.6% | +1.1% | +1.0% | 37% |
| 3%, 1.5 pts | price only | 50 | +8.6% | +2.7% | +0.5% | 40% |
| 3%, 3 pts | score-driven | 31 | +6.7% | +3.1% | +3.5% | 42% |
| 3%, 3 pts | price only | 40 | +8.4% | +2.5% | +1.1% | 42% |
| 5%, 3 pts | **score-driven (the app's default)** | 20 | +9.0% | +3.0% | **+6.2%** | 40% |
| 5%, 3 pts | price only | 24 | +11.3% | +3.6% | **-2.7%** | 29% |
| 8%, 3 pts | score-driven | 11 | +12.7% | +6.9% | +4.2% | 27% |
| 8%, 3 pts | price only | 14 | +15.0% | +6.2% | -2.1% | 36% |
The settle time (0.5 / 2 / 4 / 8 s) changed nothing worth keeping. Early samples swung hard (a first 10-trigger cut read -14% for price-only moves, the next 11-trigger cut +12%): the numbers above are pooled, still small and overlapping, and nothing here is a verdict. The "standing disagreement" rows of the replay (Novig off a steady Pinnacle price) include finished games whose last Pinnacle price is stale (+100% "EV") and are not reported.

### 116.3 What the burst-scoring research gets from the socket (Tj: "see if it is possible to implement the research you already found about burst scoring odds inefficiency")
- §95's cross-line cover (YES at the lower line + NOT at the higher, both stale after a play) needs no Pinnacle data: the burst recorder finds it in Novig's own books. Pinnacle adds nothing to a risk-free cover.
- What Pinnacle adds is a **direction**: after a play it says WHICH side of a stale Novig quote is wrong and by how much, which turns "stale" into a +EV single-leg bet (not risk-free). That is the live engine: the score-driven trigger is the same event §95 trades, seen on the sharp price.
- The lead the socket gives is small: the score precedes Pinnacle's reprice by ~2 s and Novig's makers re-quote 6.6 s (median, from Pinnacle's jump) to ~16 s (§95, from the play) after. The order's own delay in play is still unmeasured (§116.6).

### 116.4 What was built (v0.76.0; `data/.../pinnodds/`, 72 tests, 30 mutants killed)
`PinnSocket` (docs-compliant connection: key in a header, no compression, subscribe at once, pong, backoff, eviction and `plan_lacks_ws` told) → `PinnBook` (matchups, versions, `ld` vs `dz`, closes, scores, devig, history) → `LiveMatcher` (Pinnacle matchup ↔ Novig game, including swapped players; moneylines, half-point spreads and totals) → `LiveEdge.judge` (the rule: open line, live, no danger zone, settled 0.5 s, margin ≤ 9%, Pinnacle limit ≥ $100, fair 8-92%, the trigger, EV after fee, depth) → `PinnLiveTrader` (one `IOC` order at the worst price that still clears the edge; one bet per Pinnacle move per outcome; caps; wash check; halts on a lost answer or a day's loss; Tracker via `logApi`, source "Pinnodds live") inside `PinnLiveRunner` (one consumer coroutine, 100 ms ticks, follow-ups at 30 s and 120 s) with `PinnReport` (Diagnostics). Settings › **Pinnodds live**: key, **Test key** (one `GET /panel/api/me`, never the socket), feed switch (paper), "Place real bets" behind a confirmation, stake/game/day/loss chips, edge, move, trigger and devig chips. Real bets and pregame are OFF by default; STOP ALL stops it.
- Defaults: stake $2, $5 a game, $25 a day, halt at $10 lost, edge 5%, move 3 points, score-driven, WORST_CASE devig.
- **Outside live (Tj: "if opportunities also exist outside of live betting")**: the same engine runs on prematch lines (`pre` frames; Novig charges no taker fee before the game) behind "Also pregame moves" (off): untested, because the study recorded live games only. Prematch is 65% of the frames, so with it off they are not even parsed.
- Found by the tests: OkHttp runs no network interceptors for a WebSocket call (the compression offer is stripped by an application interceptor); a start race in the socket loop; "any edge" mode could never fire (games were judged only while armed by a Pinnacle change); a stage-match test that matched nothing; the feed-quiet limit (20 s) was shorter than the server's 30 s heartbeat.

### 116.5 Novig's API (NOVIG_API.md §21)
The changelog page is empty; 138 doc pages. New: **`place`, `cancel`, `cancel_all` verbs on the websocket** (no per-request signature; fills on the `orders` channel) and an optional `X-Novig-WS-Compress: deflate`. Not adopted in v0.76.0: orders go through REST `POST /v3/orders` (the verified path); an order over the open socket is a follow-up (PW9) to be tried at $1 once the REST path has met a real in-play order.

### 116.6 What is not known, and what Tj's phone will say
- **The in-play order delay** and whether `IOC` works on every live game line: no real in-play order has been sent. The first real order, or the first MISSED rate in Diagnostics ("the offer was gone"), says it.
- Whether the edge survives outcomes: only graded bets (Tracker, source "Pinnodds live") can say; CLV against the close comes from the Tracker's own closing-line capture.
- Whether this container's tape (a proxied path, one evening, preseason basketball and college football) matches the phone's. The app's PAPER follow-ups are the replacement for the tape: run it on the phone through the trial, then share Diagnostics.

## 118. Novig pauses live betting after a score: what that does to the Pinnodds lag trade, the cross-line burst, and what else the socket's speed can earn (2026-10-08; PY1-PY4; Tj: "The pinnodds live is not making any bets. There is a live bet pause after a score on novig. Research other ways to profit by taking advantage of the speed of the websocket. Consider the research you did before and the burst scoring. Does this ruin that?")

**In short.**
1. **What Tj's own file says (v0.76.2 Diagnostics, 04:52Z):** 25 real IOC orders, 0 filled, 23 missed ("the offer was gone"), 2 unconfirmed (the old 2.5 s halt). Every order was on the **Any edge** trigger (`move +0.0 pts`), so none was sent just after a score: **the pause does not explain those 23 misses**, a standing quote that cannot be hit does. The default "After a score" trigger sent none (it had been switched to Any edge).
2. **What the recorded tape shows (pool, 52 min, 12 games, 58 score-and-market samples):** Novig's displayed book does NOT go empty or wide after a score (0 of 58 markets turned non-tight in the next 40 s); it **stays as it was for a median 3.2 s (p25 2.2, p75 4.7)** and then re-quotes. A pause that only blocks orders (a bet delay, or `DELAYED`) would look exactly like this on the public book: the quote is on the screen and cannot be taken. The tape polls each market every 4.5 s (median; p90 12 s), so it **cannot see a pause shorter than that, nor any window under a second**. This was NOT proven either way; the diagnostics line added in v0.76.4 ("Orders by timing") decides it on real orders.
3. **Time between the first and last market of one game to change after a score:** median 3.7 s (p25 0.6, p75 13.2; 26 scores, ~3 markets each). Coarse, but lines are NOT re-quoted all at once.
4. **Reading of §116 under a pause:** "Novig lags Pinnacle 6.6 s" was measured on Novig's DISPLAYED mid, not on what a taker could buy. If orders in that window are held or refused, the trade as built (hit the stale ask) cannot work in the seconds after a score, which is where the SCORE trigger looks. The lag is real; the tradable part of it is not shown.

### 118.1 Does the pause ruin the cross-line burst (§83-§84, trader v0.70.0)?
- **The burst was seen in trades**: 9 windows of 0.3-2 s in one NFL game, 5-13 s after ESPN's stamp for the play, so trading WAS open in those windows on 2026-10-05. It needs the game's lines to be re-quoted one after another AFTER Novig reopens. A pause does not remove that by itself: if lines reopen one by one the neighbouring-line gap still opens; if the whole game reopens at once from a cleared book (`DELAYED` clears the book when the event reopens, NOVIG_API.md §21) the gap shrinks to nothing.
- **What decides it** is the order of events at reopen, which no tape here has (the tapes hold trades, not the lifecycle channel). The burst trader is still locked behind its recorder's proof (OFF, not switchable until the proof), so nothing is lost by the pause: its recorder is the instrument, and it also logs each window with its length.
- **So: not ruined, not confirmed.** The pause can only shrink the burst; it cannot create the competitor problem that already limited it (§84.4: rivals under 100 ms, a phone keeps under half).

### 118.2 Other uses of the socket's speed, ranked by what the pause can touch
1. **Pregame steam (PX3), nothing to do with the pause.** Pinnacle's prematch line moves on the `pre` frames (65% of all frames); Novig's pregame book has no live delay, no pause and no taker fee, and resting orders stay up for hours. A Novig order left up from before the move is exactly "the stale bet people leave up" Tj named. It is also the case where the displayed quote is the real quote. Not measured: no pregame tape of both books exists (`tools/research/pinn_pregame.py` is built for it).
2. **A defensive use of speed that is profit by not losing:** Pinnacle's danger-zone (`dz`) frame and a score frame arrive before Novig's makers pull. Any RESTING order of ours (a live bid) should be cancelled on them. Vigilant's bids are pregame only today, so this protects nothing yet; it matters the day a live bid is built.
3. **Be the first maker after the reopen (the only live trade that fits a pause).** At reopen the book is cleared and makers re-quote slowly (3-4 s here; up to 13 s for the last line of a game). Pinnacle's fresh fair is known before that. A post-only bid at fair minus a margin, posted the moment the market reopens, is a maker order: no taker fee, no hit-a-stale-quote race, filled by takers who like our price. Risks: a second score before our cancel (cancel on the next `dz`/score frame), a thin book where we are the only quote, and that post-only orders may be refused during the pause. **Unmeasured**; needs a recorder that logs the lifecycle channel (`DELAYED` -> open), the time the first maker quote appears, and whether a post-only order is accepted at once. Not built.
4. **Hold-off after a score** (a guard, not a profit): if the diagnostics show orders within N s of a score fill far less, the trader stops sending in that window instead of paying for misses (a miss costs nothing but also finds nothing).
5. **Not worth it:** trading the score before Pinnacle reprices (2.0 s lead, but no honest model of how much a score moves the price, §116.4); arbitrage across Pinnacle and other books (the socket is Pinnacle only).

### 118.3 What to switch on tonight
- Real bets OFF. Trigger "After a score" or "Stale orders", PAPER, until one paper decision is shown to be takeable. "Any edge" with real orders went 0 for 25.
- The v0.76.4 diagnostics line "Orders by timing" and the order-time line need real orders to say anything: use a $1 stake with a $2 daily cap for ONE evening after the paper numbers look right, then send the file.

### 118.4 What was built from this (v0.77.0, 2026-10-08)
- **Removed: Pinnacle only** (§88.5). The mode that priced every Novig bet against Pinnacle's devigged price alone is gone from the app, with its auto-bet pass, feed plumbing, settings, banner, Tracker chip, Diagnostics block and tests. Pinnacle is still a sharp book in the veto and in Low API usage bids; Pinnodds live is separate.
- **Pregame steam** (`LiveEdge.pregame`): off by default (Settings › Pinnodds live › Also pregame moves). Rule: Pinnacle's prematch fair for a side rose 2+ points within 15 min; the game is 5 min to 6 h from its start; the price has sat 3 s; Novig's ask is within 1.5 points of a Pinnacle price from before the move (a stale order); EV at least the minimum with NO fee; no more than 40%. A prematch line keeps 20 minutes of readings.
- **Hold-off after a score**: 0 (off) to 20 s, default off.
- **Post-score study** (`ReopenStudy`): reads each matched moneyline at the score and +1, 2, 3, 5, 8, 12, 20, 30 s, no order. Its Diagnostics lines say the median best gap to Pinnacle's fair and the share of asks that had moved, per offset.
- **Not built, and why:** a resting maker bid right after the reopen (unmeasured: the study above is its instrument) and a cancel-on-danger-frame guard (the app rests no live order, so there is nothing to cancel).



## 119. Why the auto bids look profitable, 12 h+ bids, how to bid more of what pays, and running for hours on a fixed API budget (v0.78.0, 2026-10-08; BA1-BA5; Tj, with the v0.77.0 Diagnostics, scan study and feed race: "Looking at my auto bids, so far they seem very profitable … leave the app on and background auto bid for hours, but I'm worried about api usage running out too fast … 1) figure out how the auto bid feature is profitable … 2) see if it is profitable to auto bid games that are 12+ hours away … 3) optimize … maximum positive EV and beating clv and profit … 4) the best way to let auto bid run for hours … 5) tell me which apis to add many free API keys to … 6) make any changes")

**In short.** (1) The bids' CLV is real and better than the same app's taker bets (+2.6% on 28 closes, 79% beat, against +0.6% on 587); the +32.5% ROI is not: it is 1.7σ of luck over expectation on 41 settled bids (+$26.72 against +$3.05 expected). (2) 12 h+ bids cannot be judged yet (13 fills, 3 settled, 1 close); the older evidence says they earn about a third as much per quote, so they are not cut, they are put last. (3) The wallet, not the API, is what binds the bids (55 bids waiting on a $143 wallet with $143 up), so what is ranked first matters: far games now go last. (4)-(6) The credits go on reads the bids never use; a lean background scan and a slow pace while every game is far off cut ParlayAPI's cost per league-scan from about 8-10 credits to 3 and halve the scans through the quiet hours, with no bid rule changed.

### 119.1 Why the bids profit, and what is luck (Tj's v0.77.0 files; bids 14 days, n small: say "the data suggests")
- **What was measured**: 7,213 bids posted, 51 filled (0.7%); 41 settled 26-15, +$26.72 on $82.23 staked (+32.5%); expected +$3.05, so **+1.7σ above expectation** ("normal luck over 41 bets, too soon to tell", the Tracker's own words). EV at post +3.7%, EV at fill +3.5% (39 judged, **0% picked off**). **CLV +2.6% on 28 closes, 79% beat the close.** The same app's taker bets: 716, ROI -1.6%, CLV +0.6% on 587, beat 64%.
- **So why is a bid better than a bet?** A taker pays Novig's price; a maker posts 3-4% under the fair and is filled only by someone who accepts that. The +3.7% claimed at post is kept at the fill (the fair a scan later is still above the price in all 39 judged) and 2.6 points survive the close, i.e. the fair drifts about 1 point against a filled bid, against about 2.5 for a taker's bet (EV +3.1% → CLV +0.6%). Nothing about the +32.5% ROI should be extrapolated: at +3.7% EV the sd of 41 bets is about ±17 points of ROI, and 26-15 is 63%.
- **Splits worth keeping an eye on (all small n, none acted on as a rule)**: Unders 33 of 51 fills, CLV **+3.0% (19 closes, 16 beat)** against Overs +0.7% (8, 5 beat). The takers' scan study points the opposite way (props bought at Novig's price: Over **+0.96% (352)**, Under **-0.92% (366)**, ±0.6 each): a taker buying the Under loses to the close, a maker who rests the Under bid and is filled by the Over buyers keeps it. That fits the usual lean of recreational flow to the Over (it is the flow that fills an Under bid) but 19 closes do not prove it. Fills within 2 min: CLV +3.5% (11 closes, all beat); 2-10 min: -0.2% (6, 2 beat) (a bid that waits is one the market went past). Margin 4%: CLV +2.8% (16); 3%: +2.6% (9); 2%: +1.6% (3). Leagues: MLB +2.4% (20), NHL +2.7% (4), NFL +1.6% (3). Fair age at posting: filled bids' oldest quote median 125 s, never-filled median 289 s (a bid priced from older quotes fills less: not a cause, a selection).
- **Which bid kinds**: props 41 fills CLV +2.6% (21 closes); team totals 8 fills +2.6% (5); one period, one total. Quick & likely to win: 34 fills, CLV +2.7% (11); Low API usage: 3 fills, all lost (-$5.73), CLV +1.6% (3): nothing to say about it from three.

### 119.2 Bids on games 12 h or more away
- **The bids' own record cannot settle it**: 6-24 h: 29 fills, CLV +2.7% (14 closes); of them 12-24 h: **13 fills, 3 settled (3 wins), 1 close (+6.7%)**. 0-12 h: 38 fills, 27 closes, CLV +2.5%.
- **What earlier work says** (RESEARCH.md §72.3, Novig's published trades, 10,130 markets, bids 4% under a stand-in fair, re-quoted every 10 min): value per quote by time to the close: 3-6 h +0.053%, 0-1 h +0.048%, 1-3 h +0.041%, 6-12 h +0.040%, **12-25 h +0.014% (1.0% filled)**. Takers' props in this scan study (CLV against the close, EV-outliers out): 1-3 h +1.5% (101), 3-6 h +1.6% (89), **6-12 h -0.7% (153), 12-24 h -0.7% (272), 24 h+ -5.3% (16)**. The consensus fair is weakest early (originators' news, §72.5), and a maker is the one who is hit when the fair moves.
- **Verdict (not proven either way)**: a 12-24 h bid is still positive in expectation (a 4% margin less about 2.4 points of drift is about +1.6% at the close if the takers' drift applied to bids, which it need not), but it earns about a third per quote and ties up money for hours. So it is not removed (Tj's "games within" stays his); it is **ranked after every nearer bid** when the wallet, the dollar limit or the most bids can't take them all (`MakerPlan.priority`, `farOf`, 12 h), and a far bid already up is the first to come down when the wallet falls short. With the wallet as the limit, that is the whole cost of keeping them.
- **How the next file settles it**: Diagnostics' BIDS block now has "every bid posted, by time to the start when posted" (posted, bid-hours up, fills, **fills per 100 bid-hours**, CLV of the fills), and the same by side (Over/Under) and by league (`BidReport.funnel`). Read it at about 30 fills with a close in the 12-24 h row; if its CLV is under +1% or its fills per 100 bid-hours are under a third of the 6-12 h row's, set "games within" to 12 h.

### 119.3 What was optimised for profit and CLV (and what was left alone)
- **Wallet first-come is the binding limit.** The log: "55 waiting: the wallet can't cover it beside the bids already up · wallet $143.49 with $143.25 resting", and "the most at risk on one game ($70) is reached". With every dollar up, which bids go up first is the optimisation. Order is now: leads its side → **nearer game** → popular kind → likeliest fill → most edge (Quick & likely); the other orders get the nearer-game tier after "leads". It decides nothing about which bids qualify.
- **Not changed, on the data**: the 4%/3% margins (4% CLV +2.8% on 16, 2% +1.6% on 3: too few to move), the 0.30-0.60 price band (0.40-0.50 CLV +1.6% on 12, 0.50-0.60 +4.1% on 12 and all beat: the band is working), sharp-book anchoring (0% picked off), Unders/Overs (no side rule: 19 and 8 closes), the 5-minute / 10-minute freshness limits (no loosening for any saving).
- **Taker side, for Tj to decide (not changed)**: props by EV band in the scan study: under 3% EV CLV -0.9% to -0.1% (n=598), **3% and over +1.7% to +5.5% (n=120)**; the 3.3% floor in the study's rules line sits where the data turns, so it is already in the right place.

### 119.4 Where the API usage goes (from the call prices in PARLAY_API.md §3 and this file's CONNECTIONS; a measurement of one scan needs "Last rounds", empty in this file because no scan had run since the app opened)
- **Credits**: ParlayAPI 5,674 of 26,000 used this month, about 750 a day (7.6 days) against a Starter day share of ~645 (WATCH); The Odds API 1,262 of 3,500, **SHORT, gone Oct 21**. Background scans keep half a day's share (`CreditPace.keepOfDay`), so the background spends about 320 ParlayAPI credits a day plus earlier days' leftover, then the call is held back and the other feeds read on without it.
- **What one league-scan of the old background scan bought**: ParlayAPI `/odds` for the game lines **3 credits (5 with alternates when no Pinnacle feed is on)**, `/props` 3, 1st-half board 2: **8-10 credits**; The Odds API game lines 1 credit a market a league. Quick & likely to win bids on props and team totals only, so the `/odds` and 1st-half reads and The Odds API's game lines priced bets no bid could use.
- **What a team total needs** (checked in the code): team totals come from PropLine's `totals`, Pinnacle's feed and Kalshi, never from ParlayAPI `/odds` (`TheOddsApiClient.marketsFor` maps only moneyline, spread and total), so dropping the game-line families takes nothing from a team-total bid's fair; PropLine still asks `totals` while TEAM_TOTAL is read.
- **The old pace**: a background Vigilant scan every 4 min (`AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS`) all day. A far game's fair may be 10 min old (`Freshness.FAR_OFF_AGE_MS`), so while nothing starts inside 3 h the 4-minute pace re-reads what has not gone old; the low-usage mode already proved an 8-minute pace keeps bids up (`LowUsagePaceTest`).
- **Churn (free but rate-limited)**: 5,156 of the 7,106 bids that ended without a fill were "About to expire: re-posted" (2,664) or "fair goes old within a minute: re-priced" (2,492): the roll-forward working, each a cancel and a place on Novig (`/v3/orders` 12,395 calls, 3,151 HTTP 429 in a week, the key's 16 a second). A slower far pace halves them for far games.

### 119.5 Which free keys to multiply (credits a key buys for the lean scan)
| Provider | Free allowance | Lean scan cost | A key buys (league-scans) | Verdict |
| :- | :- | :- | :- | :- |
| **PropLine** | **1,000 requests a day, resets daily** | 1 for the sport's list + 1 a game (a 15-game MLB slate ~16, an 8-game NHL slate ~9) | 60-110 a day, i.e. ~4-7 league-hours a day at the 4-min pace, 8-14 at the 8-min | **Best: daily reset, carries FanDuel, DraftKings, Pinnacle, Kalshi, Fanatics, team totals** |
| **PinnWire / pinnapi** (Pinnacle) | 100 requests a day a key | 1 a sport | 100 sport-scans a day: ~6.7 league-hours at 4 min | **Second**: Pinnacle is the anchor for team totals and many props; cheap per scan, small per key |
| ParlayAPI free key | 1,000 credits a month, 100 kept for closes | 3 a league (props) | ~300 league-scans a MONTH (~10 a day) | Weak: a tenth of a PropLine key's day; the only carrier of ProphetX and Caesars, so keep it for those |
| The Odds API | 500 credits a month | 0 for game lines now; props 1 a prop type a game | negligible | **Not worth more keys for bids** (it is the one that runs SHORT: turn "The Odds API" off in Settings rather than add keys) |
| Kalshi, Polymarket | free, no key | free | unlimited at 2-3 a second | Already used first |
The Starter plan itself (20,000 a month): 6,666 lean league-scans a month, about 14 league-hours a day at the 4-min pace (about 28 at 8 min) against about 5 before (the background keeps half a day's share when Tj also scans by hand). Many keys of one provider do not raise the pace per hour, they lengthen the day: `KeyPool` uses them in order. Pinnapi's terms forbid circumventing its limits (a warning is in Settings): Tj says he has the providers' approval.

### 119.6 What was built (v0.78.0)
- **`LongRunBids`** (`data/.../scanner/`): the long-run saver, Settings › Bids › **Long-run saver** (`ScanSettings.makerLongRun`, **on by default**, hidden for CNO-priced and Low API usage bids, which have their own). (a) **Lean background scan**: with Quick & likely to win, the background Vigilant scan reads only the families a bid can go on (props, team totals; Tj's families and kinds still bound it) over the hours a bid could be posted on (`LowUsageBids.windowHours`). Flag `ScanSettings.leanScan` (transient, set by `VigilantApp.startVigilantScan` for a background scan only, applied by `effective()`; Tj's own Scan, Check odds now and CNO never carry it). (b) **Slow far pace**: while no game a bid could still go on is inside 3 h 8 min of its start the background scan waits 8 min, else the usual 4 (`LongRunBids.gapSeconds`, `AutoScanner.vigilantGap`).
- **Far games last** (`MakerPlan.priority(rules, now)`, `farOf`, `FAR_HOURS` 12): after "leads its side", in every order; the trim at a short wallet drops far bids first.
- **The funnel** (`BidReport.funnel`): every bid posted by time to the start, side and league with bid-hours and fills per 100 bid-hours; in Diagnostics' BIDS block and the scan study's.
- **Tests**: `LongRunBidsTest` 11, `BidReportTest` +2 (and the summary's two-line test now allows the funnel after them); **5 mutants killed** (far tier out of the quick order, out of the trim, far gap off, lean keeping every family, lean ignoring the trap window).
- **What it costs Tj (said on the switch)**: Vigilant's *background* scan no longer alerts on game lines (the study's Vigilant game-line bets: totals CLV -0.6% on 19, spreads -1.2% on 11, so little is lost); the +EV tab shows props and team totals after a background scan until Tj's own Scan runs; and a bid on a game over 3 h away is re-priced every 8 min, not 4 (its quote may be 10 min old by the app's rule, so no bid is ever priced from an older quote than before).
- **Not built, on purpose**: per-league cadence (a league with only far games read at the far pace while another is near) would leave the other leagues' lines out of a scan and the planner would take their bids down; it needs "not judged" lines for the plan, a separate change. Loosening the freshness limit for far games (the 10 minutes was measured at 2.7% of Kalshi lines moving a point in 10 min; nothing here measures 12 h+ lines). A "bids only up to 12 h" default (§119.2 says wait for the funnel).
- **Read in the next file**: Diagnostics "Last rounds" (the lean scan's real cost per API: compare ParlayAPI credits a scan with the 8-10 above), the funnel's rows, "long-run saver" in the Make orders line, ParlayAPI's runway (should leave WATCH).

## 120. Novig's post-score pause, ParlayAPI "unavailable" answers, and the two websockets (2026-10-08; QA1-QA3; Tj: "Research how long novig pauses live betting after a team scores … if it is normal to get a lot of unavailable errors on parlayapi … how to best take advantage of my Pinnacle websocket and novig websocket")

Method: the repo's own research (§116, §118, NOVIG_API.md §21, PARLAY_API.md) plus three web searches (Novig live delay, ParlayAPI 503, Novig websocket docs). The web returned nothing Novig- or ParlayAPI-specific; no new fact below comes from outside the repo. Tj's Diagnostics file with real in-play orders and the post-score study (v0.77.0+) was not in this session, so none of it is read here.

- **QA1 (pause length): not known, and not findable online.** The only measurement is §118: Novig's displayed book stays unchanged a median 3.2 s after a score (p25 2.2, p75 4.7), then re-quotes; the tape polls every 4.5 s so it cannot see a pause under that. Whether orders are refused in that time is undecided until Diagnostics' "Orders by timing" and the "Post-score study" (`ReopenStudy`, +1..+30 s) have real data. No profitable bet-at-reopen strategy is proven; the repo's candidate is a post-only maker bid at reopen (§118.2.3), unbuilt and unmeasured.
- **QA2 (ParlayAPI unavailable):** PARLAY_API.md already records 503 "busy" bodies on heavy endpoints (`props_temporarily_busy`, `LINE_MOVEMENT_TIMEOUT`, the latter still charged 2 credits). The app already retries once, honoring Retry-After. Whether the rate Tj sees is "normal" cannot be said without his counters (Diagnostics CONNECTIONS / Last rounds). Nothing online documents it.
- **QA3 (websockets):** unchanged from §116/§118: pregame steam (PX3, built, off by default) is the use no live pause can touch; score-driven moves held (+6.2% at 5%/3 pts, n=20), price-only moves did not (-2.7%, n=24); Novig's `place`/`cancel` websocket verbs are unused. Pinnodds trial ends 2026-10-10 23:34Z; the socket costs at least $198 a month after.

### 120.1 Tj's v0.78.0 files read (Diagnostics 2026-10-08 17:02 EDT; burst study; feed test; scan study not read)
- **Live orders (Pinnodds live, real bets, 37 sent):** 0 filled, 34 missed ("the offer was gone"), 1 refused, 2 unconfirmed; 33 of 37 were tennis (ATP 29, WTA 4). **Order time, all orders: median 5,330 ms, slowest 5,716 ms** (a tight band), against `/v3/orders` averaging **94 ms to first byte over 14,039 calls** (pregame bids included). A fixed ~5.3 s answer on live orders only is what an in-play acceptance delay looks like (not proven: the 5.3 s may include the app's own queue; the Diagnostics line does not split it). With Novig covering half of a Pinnacle jump in a median 6.6 s (§116), an order that lands 5.3 s after the decision arrives after most re-quotes: this fits 0 for 37. "Pinnacle move to decision" median **30.6 s**: the decisions themselves were late, so most were not fresh lags. 24 of 34 misses had no Pinnacle move behind them (the "Any edge" standing disagreement, §118). "Novig followed 0%": the ask stayed put on paper, so the makers pulled depth rather than re-priced.
- **Post-score study: no score probed** (no matched live game at the time: "matched 0"). The pause question (QA1) is still open; the 5.3 s order time is the only evidence about orders, and it is not a post-score pause. Burst recorder: 1 MLB game, 20 min, 0 windows; delays still assumed (6 round trips, 0 push delays measured; 20 of each needed).
- **ParlayAPI:** 7,529 calls since Oct 1, **669 failed (8.9%)**: HTTP 500 x380 (5.0%), 503 x114 (1.5%), cancelled 70, DNS 55 (the phone's route), timeout 47, connect 3. p50 199 ms, p95 10,973 ms; NFL `/props` averages 8.2 s. The 500s came in bursts (Oct 8 15:09-15:21 local, NFL props, 1st half, odds board together); the 503s are "The odds board for this ... unavailable" on NFL/NCAAF and `props_temporarily_busy` on MLB props. Each carries a request id (e.g. e10a0f41e1941d33, 733e0501afa9ce11, fb326948ebaaa1c8) for ParlayAPI support. 91% succeeded and credits are not the problem (6,245 of 29,000 used, 10 keys): it is the provider's server errors, worst on football props, not a quota or key problem.
- **Network:** Novig refused 853 calls with 451 (383 of them on `/v3/orders`), plus 1,017 DNS failures: the Metro by T-Mobile address and dead spots (see the 451 answer).

## 120.2 Live Pinnacle (Pinnodds socket) against Novig's public live books: the study (2026-10-08 23:33Z-2026-10-09 00:00Z; QB1-QB4; Tj: "see if anything can be exploited on novig based on Pinnacle odds in real time … think outside the box")

**Sample, honestly.** ~24 minutes of live tape (two recorders; the second died at 00:00Z when the container restarted), 15 matched games (NBA preseason 4, NHL 7, football 4), 96 Novig markets, 6,656 clean (read x outcome) rows, 434 score changes of which 100 are NBA baskets. Tools: `tools/research/pinn_novig_deep.py`, `pinn_novig_maker.py`; data and every output in `research/pinnodds_2026-10-08b/`. Novig is polled about every 4.5 s per market, so nothing shorter than that is visible, and the tape sends no orders, so it cannot see Novig's order-side delay or any pause that only blocks orders. Rows where Novig's ask and Pinnacle's fair were more than 15 points apart (742 of 8,140, almost all basketball totals) are excluded as wrong line matches or stale quotes.

**What the tape says (n small; read as hints):**
1. **Novig lags Pinnacle by seconds, and the lag differs by sport.** After a Pinnacle move of 1.5+ fair points, Novig's best bid changes after a median **4.3 s** (p25 2.2, p75 9.4, p90 19.1; n=238, 8 with no change in 60 s): basketball 4.0 s, football 4.7 s, hockey 6.3 s (n=9). The lead-lag correlation (Pinnacle's 10 s fair change vs Novig's mid change) peaks at +0 to +5 s for basketball and football and **+20 s for hockey (0.44)**: hockey on Novig trails by about 20 s, though only 9 jumps stand behind it. Novig never LED Pinnacle in any sport (no positive correlation at negative lags). Order-book imbalance on Novig predicts nothing (corr +0.02, 222 of 496 heavy-imbalance moves went the heavy side's way).
2. **A score is seen on the socket before the price moves: median 1.7 s (n=100) from the score frame to Pinnacle's first reprice; Novig's first best-bid change after a score is a median 3.5 s (p25 1.6, p75 5.5; 91 of 102).** Pinnacle suspended none of 102 scores. Novig's displayed book kept quoting (410 reads before, 388 after, spread 6.5 -> 6.0 points, depth 69k -> 79k contracts): nothing was visibly paused. A pause that only blocks orders would look exactly like this, and the tape cannot tell.
3. **The edge at the ask is mostly not a lag.** 11.0% of rows are +2% EV or better against Pinnacle's power-devigged fair (fee in); the median EV of all rows is -3.6%. Basketball moneyline is 27.5% edge rows with a median +4.0% now but only +1.3% against Pinnacle 30 s later and +1.5% at 120 s: much of it is a standing disagreement or a devig/preseason-pricing offset, not a stale quote. Hockey moneyline: 0.7% edge rows. Edge rows by seconds since Pinnacle last moved: 12.3% at 0-2 s, 8.5% at 10-20 s, 6.3% at 20-60 s (a lag shows as a decay; this is mild).
4. **The order-delay test (G9):** buying the team that just scored at Novig's ask as it stood d seconds after the score, judged against Pinnacle's fair 30 s after: **d = 2 s: median EV -0.6%; 3.5 s: -0.0%; 5.3 s: -0.6%; 8 s: -0.7%; 12 s: -1.0%** (n=72-75, all basketball). Even an order landing 2 s after the score has no edge; the 5.3 s order time measured on Tj's phone (§120.1) is not what kills it. The reason is in (5).
5. **Pinnacle's own jumps overshoot after a score.** After a score-driven moneyline jump of 2+ points only a median **52% is still there after 30 s and 28% after 120 s** (n=93; flicker, 50% undone within 10 s, 21 of 93); price-only jumps keep 100% at 30 s and 85% at 120 s (n=108). A trade built on a score reading is fading something Pinnacle itself takes back. A basket moves the scorer's win chance a median +3.0 points (leading) to +4.3 (trailing); that is what Novig is repricing after 3.5 s.
6. **Nothing found in:** margin widening or limit cuts as a warning (n=13 and n=59, no signal); totals the score already decided and Novig still offering under 97 cents (0 of 1,220 reads); crossed or locked Novig books (0); half-cent versus whole-cent ask ticks (-3.7% vs -3.5% median EV). Market-type order after a score: moneyline 4.4 s, spread 4.1 s, total 4.0 s (n=90/46/50), no market type is reliably slower.
7. **Pinnacle limit as a quality filter:** edge rows with a Pinnacle limit under $2,000 are 20-21% of rows, with $2,000+ 3.9%: a small limit is a soft Pinnacle quote and its "edge" is likely Pinnacle's own noise.

**Verdict, live (hints, not proof):** no taker strategy on the live Pinnacle-vs-Novig lag is supported. The lag is real (4-6 s, up to 20 s in hockey) but the part that is takeable shrinks to nothing once Pinnacle's score overshoot and Novig's own re-quote (3.5 s) are counted, and Tj's real orders (0 of 37 filled, ~5.3 s) add a delay on top. The one place the numbers are interesting is hockey (a 20 s lag), where there were too few moves to say.

## 120.3 Make orders (resting bids) priced from Pinnacle's live fair (QB5; Tj: "Also include the possibility of 'make' orders (bids). This may be profitable")

`pinn_novig_maker.py`: a resting bid on each outcome at floor_to_half_cent(Pinnacle fair / (1 + margin)) when none is up, only if Pinnacle moved within 20 s, the price is under Novig's ask, and 0.05 <= p <= 0.95; fills counted from Novig's public trade history (a trade at or below p after a 0.3 s post latency = optimistic fill, strictly below = strict); value = Pinnacle's fair at the fill time (and 30 s / 120 s later) minus p, plus the live maker credit (50% of the taker fee 0.03 P(1-P)), over p. Variants: **none** (rests the whole ttl), **pin** (cancelled 2 s after a Pinnacle move that leaves it under +1% EV), **pin+score** (also not posted within 30 s of a score, cancelled after one). 24 min of live tape, ~11 bid-hours per row (700-840 bids), 25-70 fills per row.

| ttl 60 s, all sports | none: fills/bid-hour, EV at fill / +30 s / +120 s | pin: fills/bid-hour, EV at fill / +30 s / +120 s |
| :- | :- | :- |
| 1% margin | 6.6, +1.9% / +1.5% / +0.0% | 5.2, +2.1% / +2.0% / +1.5% |
| 2% | 4.6, +3.0% / +1.7% / +0.8% | 3.5, +3.2% / +3.2% / +2.7% |
| 3% | 4.7, +4.1% / +2.4% / +2.3% | 3.5, +4.3% / +4.2% / +4.0% |
| 4% | 3.9, +4.9% / +1.6% / +2.5% | 2.6, +5.2% / +4.9% / +4.5% |
(ttl 30 s and 180 s, and a 5.3 s cancel latency, are in `maker_live_*.txt`: same shape; a 5.3 s slower cancel keeps nearly the whole guard benefit, e.g. 3%: +4.3% -> +4.0%.)

**What it says:** (1) a Pinnacle-priced bid kept its EV at the fill (about the margin) when a cancel-on-Pinnacle-move guard was used, and lost 1-3 points by +120 s when it was not (the pick-off: fills that come right after Pinnacle moved against the bid). (2) The guard costs fills (about -25 to -35%), the score guard costs about 20% of the bid-hours and almost no EV. (3) Fill rates are low (2.6-6.6 fills per hour of one resting bid) and football fills most, hockey least. (4) The strict-fill numbers (trades strictly through the price) are fewer and noisier but point the same way. (5) The EV is in line with Tj's real bids (EV at fill +3.5%, CLV +2.6%, §119.1), which also sit 3-4% under a fair.
**Not known:** queue position (a bid joining a deep level fills later and only when the level before it is eaten), our size, Novig's acceptance of an order during a post-score pause, and the sample is 24 minutes. The cancel guard is the one idea worth carrying to Vigilant: its bids are pregame today, so the live version needs a live bid mode first.

### 120.4 Ideas for Vigilant (nothing built; for Tj to choose)
1. **Pregame/live bid cancel-on-Pinnacle-move.** If a Pinnacle feed (Pinnodds socket, PinnWire, PropLine) is on, cancel a resting bid within seconds when the book's fair for that side falls below price x 1.01 (the sim's "pin" guard). Today a bid waits for the next scan (up to 8 min) to be pulled; the simulation says that wait is where the 1-3 points of pick-off live. Cheapest: reuse the existing roll-forward cancel path with a Pinnacle trigger.
2. **Do not run the live lag taker** (Pinnodds live "Any edge" or "After a score") with real money on this evidence; keep paper until a hockey/tennis sample exists.
3. **Hockey is the sport to measure next** (a 20 s lag): record 2-3 hockey evenings with `pinn_novig_lag.py record` and rerun the deep tool.
4. **Soft-Pinnacle filter:** ignore a Pinnacle price whose limit is under $2,000 as a fair source.

**Pregame, first attempt (2026-10-09 01:02-01:09Z): too thin to say anything.** `pinn_pregame.py` was disconnected from the socket after about 7 minutes (it never reconnects, by design, so it cannot fight the phone for the socket): 7 matched games, 15 Novig markets, 31 Novig trades, **no Pinnacle move of 1% or more with a Novig read just before it**, so the lag question is unanswered. Pregame asks against Pinnacle (no fee): 10% of rows are +2% EV or better, none +3%; the median is -1.1% (n=1,246, almost all standing, 250+ s since Pinnacle last moved). Tape saved as `research/pinnodds_2026-10-08b/pre_20261009.ndjson.gz`. A proper pregame recording needs US daytime, when lines move for the evening games: scheduled for 2026-10-09 16:50Z (90 min), results in §120.5.

## 120.6 Alternate lines: Tj's live-NFL screenshots, what "99.9%" is, and Pinnacle's alternate lines against Novig's (2026-10-09; QC1-QC3; Tj: "under 52.5 is 99.9% and under 54.5 is 99.9%, yet under 53.5 is 90% … Do strange odds like these offer any value? Maybe the app should analyze real time alternate odds at other books vs alternate odds live on novig")

- **Recorders die on my side (QC1).** Every background recorder ended about 7 minutes in or when the session went idle ("ConnectionClosedError no close frame", then the process gone; Pinnodds `/health` shows `connected_clients` 0). Tj was not on the socket; my "evicted" guess in `pinn_pregame.py` was wrong (fixed: it reconnects on an abnormal drop). Recordings now run in foreground chunks of up to 10 minutes. A GitHub Actions workflow with the key as a repo secret would run for hours; Tj's choice.
- **What the screenshots show.** A tile's percent is what BUYING that outcome costs = 1 minus the best bid on the OTHER outcome; with no bid on the other side there is no price and the app shows 99.9% (a cap, not an offer): in the tape 5-15% of total sides had no price. TB 7 - DAL 10 at the half, 17 points scored: Under 52.5 and 54.5 at 99.9% have no seller; **Under 53.5 at 90%** exists because someone bids about 10 cents for Over 53.5 (and "Over 53.5 11%" is that same bid seen from the other side): a longshot bettor paying 10 for a thing worth a few cents. Over 54.5 at 47% is the mirror: a bid for Under 54.5 at 53 cents when it is worth about 95+: nothing for a taker to do, a seller of Under would have to accept 53.
- **Is Under 53.5 at 90-92.5% value?** Rough model (not data): at the half the market total was near 38 (the 35.5/38.5/39.5 ladder), so the second half would need 37+ points; with a second-half sd of about 9 points that is z of 1.8, about 3-5% (football's tails are fatter): Under 53.5 fair about 95-97% against an ask of 90-92.8% (Tj's position: $10 for $10.78, price 92.5%) = about +3 to +7% before a live fee of 0.03 x P x (1-P) = 0.2%. Probably +EV; small size (depth at alternate asks is about $800 in the tape's median) and the money is tied up to the end of the game. It is the favourite-longshot bias on the far alternate lines: retail bids the long shot, the other side is the nearly decided outcome.
- **Do not confuse that with Pinnacle's alternate lines (QC3).** Pinnacle's socket carries live alternate spreads and totals (35 s of frames: basketball 422 spreads + 442 totals, soccer 318 + 385, football 16 + 16, tennis, baseball); the recorder had been skipping them and reading only the Novig strikes equal to Pinnacle's MAIN line. It now records alternates (`alt` flag) and reads every Novig strike that matches ANY open Pinnacle line. First 9 minutes (13 games, 5 football, 68 Novig markets, 2,516 rows): **alternate lines: 13.1% of rows +2% EV or better at the ask, median +5.9% at the moment, but against Pinnacle 120 s later median -3.6% (n=131 rows, football totals; rows overlap, the independent count is far smaller)**; main lines +4.4% at 120 s (n=43). Read: Pinnacle's alternate quotes are thinner and move toward Novig after the read: an alternate "edge" against Pinnacle alternates did not hold. Baseball alternate rows show +47% "edge" with no follow-up: far strikes where Pinnacle's own price is an extreme long shot; not trusted.
- **Ladder consistency: no locked profit.** Over j + Under k with j <= k covers every score; 975 pairs in 95 snapshots (and 73 earlier): **0 covers under $1.00 and 0 under $1.01**. (`pinn_novig_ladder.py`.) The tape never saw the screenshots' kind of state (a late-game football ladder); that needs a recorder run on a live NFL/NCAAF game's second half.

**For the app (not built):** (1) an *alternate-line scan* needs a fair for an off-line strike; sources that exist today are Pinnacle/PropLine alternates (the evidence above says "thin, moves toward Novig": use only with 2+ books agreeing) or a model; (2) a *tail scanner* for late-game, near-decided strikes: fair = P(remaining points >= needed) from the live score, the live main total (Pinnacle/Kalshi/Novig itself) and a conservative sd, ask under fair by 3% or more, size capped, paper first with every would-be bet logged and graded; (3) *ladder-consistency detection* (the covers above) costs nothing to add as a log line and would have caught a locked profit if one existed.

## 121. Every way to profit through the Novig API: the catalogue, what is measured, what is ruled out, and the cheapest next test (2026-10-09; QD5; Tj: "This app is in 100% research and development. Research any possible ways to profit using the novig api")

Sources: NOVIG_API.md (§8 fees, §17 make orders, §18 in play, §21 websocket), RESEARCH.md §67, §70-§72, §83-§84, §95, §116-§120, the two cross-venue measurements below, and three web searches (third-party reviews only: nothing official on the Maker Credit Program's rates; Ludlow Exchange's CFTC filings of July 2026 describe a Member incentive program, a separate Liquidity Provider Program and "Novig Points", whose appendices are redacted).

**The structure that every idea sits on.** Novig is an exchange: no house edge, so a taker pays the book's spread (a median 4 points on live alternate lines, 0.5-2 on main lines) plus, in play only, 0.03 x P x (1-P) a contract (0.75 cents at even money); a maker pays nothing, and earns 50% of the taker's fee on live fills (0.375 cents at even money, paid in cash within 7 days; NFL/NCAAF futures 0.06 fee, 70% credit, always). Pregame is free for both sides. Orders: `IOC`/`FOK` takers, `PO` post-only makers with a `ttl`, batches of 256, `cancel`/`place` over the websocket. Live orders took a median 5.3 s to answer on Tj's phone (§120.1), pregame orders about 0.1 s. Anything below is about WHERE the price is wrong and WHO pays for being first.

### 121.1 Measured today (new)
- **Novig against Kalshi, same game, moneyline (`tools/research/xvenue_novig_kalshi.py`; public reads on both sides).** Games are matched only when the SET of team abbreviations is identical on both venues (a name match put Green Bay on Tampa Bay's game). A cover is YES at one venue + NO at the other; cost under $1 after Kalshi's 0.07 x P x (1-P) and Novig's live fee is a locked profit.
  - *Pregame, 20 games, 792 covers over 8 minutes: median net -2.6%, best -0.75%, none locked.* The two venues agree to within their spreads before the game.
  - *Live, 7 games read about every 3 s for 8 minutes (2,720 snapshots, the two reads 0.3 s apart): 106 snapshots (3.9%) were locked, in 44 runs, median 3 s, longest about 16 s, best net +4.2% (Tampa Bay @ Dallas, the game on Tj's screenshots; South Florida @ UTSA +4.1%).* Half the runs were a single read. Kalshi's size at the touch was from 0 to 300,000 contracts, Novig's 12 to 1.4 million.
  - *Reading:* the locked covers are real as prices, but they last about as long as a live Novig ORDER takes to answer (5.3 s), and they need an order on Kalshi in the same instant (leg risk, Kalshi's per-order fee rounding). As a TAKER-TAKER trade it is out of reach on Tj's phone. The part that survives is the MAKER side: a resting Novig bid is filled at its own price with no order delay, and the hedge on Kalshi is one fast REST order afterwards (idea M2 below). Not measured: Kalshi's order latency, how often a Novig bid at the cover price fills, the account on Kalshi (the app only reads Kalshi's public books today).
- **Ladder covers inside Novig:** 975 pairs in 95 snapshots (and 73 before): none under $1.00, none under $1.01 (`pinn_novig_ladder.py`, §120.6).
- **Pinnacle's lag, alternates, bids, outside-the-box patterns:** §120.2-§120.6.

### 121.2 The catalogue (T = take, M = make, S = structure; "Status" = what the repo knows)
| # | Idea | Why it might pay | Status / evidence | Cheapest next test |
| :- | :- | :- | :- | :- |
| T1 | Pregame +EV against a sharp fair, near the start | free pregame, small spread | The app's core; CLV +3.0% within 6 h, -0.9% beyond 24 h (§82) | Keep; bet inside 6 h |
| T2 | Pregame steam: Pinnacle's prematch line moves, Novig's resting ask does not | no fee, no pause, nobody races a phone for pregame | Built (PX3) and untested: the one pregame recording was cut at 7 minutes | A 90-minute `pinn_pregame.py` run in US daytime (scheduled 16:50Z) |
| T3 | Live lag trade (Pinnacle -> Novig) | Novig's mid trails by 4-6 s (hockey about 20 s) | 0 of 37 real orders filled; order answer 5.3 s; score jumps overshoot (§120.1-.2) | Do not run with money; a hockey evening of paper |
| T4 | Late-game tail strikes (far alternate lines the game has decided) | favourite-longshot bias on thin alternate books | Built as paper (lab, QD2); Tj's own Under 53.5 at 92.5% looks +3-7% by a rough model | The lab's graded paper record, 100+ would-be bets |
| T5 | Alternate lines against 2+ books' alternates | other books price the same strike | Built as paper (QD3); one book's alternates did NOT hold (-3.6% at 120 s) | Lab with the Pinnodds feed on; add a second source |
| T6 | Novig vs Kalshi / Polymarket / ProphetX cross-venue covers | two exchanges, different crowds | Live covers 3.9% of 3-s snapshots, median 3 s; none pregame (§121.1) | Fast watch on 3 evenings; Kalshi order latency test at $1 |
| T7 | Sportsbook promotions hedged on Novig (boosts, free bets, "arb" pages: matched betting) | Novig is the lowest-vig hedge there is | Not studied; the classic exchange use; Vigilant never prices a promo | Pull one promo's two-sided prices and compute the hedge on Novig: no model needed |
| T8 | Injury / lineup news: a teammate's props move, Novig's stay | news reaches books before Novig's props makers | ParlayAPI injuries are already read (1,333 kept) but not traded on | Log Novig's prop asks around each tagged injury; compare with the books' move |
| M1 | Pregame bids at fair - margin (the Bids tab) | spread paid by impatient takers, no fee either way | +2.6% CLV on 28 closes, 79% beat (§119); 0.7% of bids fill | Keep; the funnel table settles 12 h+ |
| M2 | Cross-venue maker: bid on Novig at Kalshi's fair - margin, hedge each fill on Kalshi | fills carry no 5.3 s delay; the credit (live) pays 0.375 cents at even money; hedged fills lock the margin | Not built; needs a Kalshi trading key and a hedge-latency measurement | Simulate on the fast-watch tape: bids at (1 - Kalshi ask - fee - 1%) and count trades through them |
| M3 | Live bids on a Pinnacle feed with a cancel-on-move guard | maker credit + fill at your price; the guard avoids pick-off | Simulation: the guard keeps the edge at +120 s (+4.0% vs +2.3% at a 3% margin); 25-70 fills (§120.3) | A 60-minute paper bid recorder on the phone; then $1 live bids |
| M4 | NFL/NCAAF futures market making (70% credit, 6% fee) | credit about 2% of cost per fill at even money, no live pause | Not studied; wash trades and credit farming are barred ("bona fide market risk") | Read the futures books and the trade tape for fill rates before any order |
| M5 | Post-reopen maker after a DELAYED event | the book is cleared at reopen; makers re-quote in 3-4 s | Unmeasured (§118.2); `DELAYED` is its own status | The post-score study (v0.77.0) on a live evening |
| S1 | Ladder covers inside one game | one number, several books | None in 1,048 pairs | Left on in the burst recorder |
| S2 | Locking your own bet by taking the other side later | no model, a hedge | Not guaranteed on Novig (§67, §83): the book moves against it | Settled |
| S3 | FMV and PUSH voids | rules, not prices | Nothing found: FMV pays the fair at void time | Skip |
| S4 | `GOLIVE` voids resting orders | a free option? | No: a void is not a profit; a bid cannot become a live fill (§17) | Skip |
| S5 | Points, referral and welcome bonuses | one-off cash for volume and make orders (third-party reviews: a $25 trade bonus; Points reward make orders) | Not verified against Novig; not an API matter | Read Novig's own terms before counting it |
| S6 | Liquidity Provider / Market Maker Agreement | rebates and size | Generally a $30,000 deposit, W-9 and QA access (NOVIG_API.md §18); excludes the Maker Credit | Not for this bankroll |

**Ranking for an R&D bankroll of about $250:** (1) T2 and M3 first, because neither fights the 5.3 s order delay (pregame has no pause; a maker's fill needs no order); (2) M2 and T6 are the new, concrete finding of today: two venues cross for seconds in play, so the tool to build next is a cross-venue paper recorder with a Kalshi hedge-latency test, then a hedged maker; (3) T4/T5 are already collecting paper data; (4) T7 (promotions) is the one idea with a guaranteed profit and no edge needed, and the only one nobody in this repo has priced.

**What would change this:** a measured Novig live order delay far below 5.3 s on a clean phone connection (the two 451 refusals and the Metro address may be part of it), a Kalshi account key (it turns T6 and M2 into something testable with $1), and a day of NFL second halves in the paper lab.

## 122. Make bids: what the real ones say, and the paper bid lab that answers the rest (2026-10-09; QE1-QE5; Tj: "spend a fair amount of research on make bids. So far my make bids have been more profitable than take bids. You take control of the research and development")

### 122.1 What Tj's 90 real filled bids say (v0.78.0 Diagnostics, 2026-10-08 17:02 EDT; 41 settled, 40 with a close; slices are tiny: "the data suggests")
Bids 90 (49 open) · 26-15 · ROI +32.5% (1.7 sigma of luck over expectation, §119.1) · EV at post +3.7% · **CLV +2.7% on 40, 73% beat the close**; the same app's taker bets: -1.6% ROI, CLV +0.6% on 592, 64% beat.
- **By lead time when posted (the answer to "shouldn't my bids be good after 6 hours?"):** 30 min-2 h CLV +3.6% (10); 2-6 h +1.2% (11); **6-24 h +3.2% (19)**, against takers +2.6% / +2.5% / -0.2% / -0.9% over the same buckets. A resting bid does NOT lose its edge with lead time the way a taker's does. (6-12 h +3.1% on 16, 12-24 h +3.7% on only 3: the funnel table in the next file settles 12 h+.)
- **By side:** Under +3.0% (29 closes), Over +1.2% (10): the maker who rests the Under is filled by the Overs' flow (the takers' scan study shows the reverse for TAKING: Over +0.96%, Under -0.92%, §119.1).
- **By kind:** props +2.8% (26 closes), team totals -0.3% (3 closes, 20 bids: too few), unlabelled 12 older bids +3.3%.
- **By league:** MLB +2.8% (23), WNBA +2.4% (8), NHL +2.4% (5), NFL +1.6% (3).
- **By price band:** 0.40-0.50 +2.4% (21), 0.50-0.65 +3.7% (16): the 0.30-0.60 window works; nothing under 0.30 or over 0.65 has data.
- **By edge at post:** 3-4% +3.5% (24), 4%+ +2.4% (12), under 3% -0.9% (4): a bid posted at under 3% EV does not keep it; the 3-4% band is where the real bids live.

### 122.2 The problem and the tool
90 fills and 40 closes cannot rank margins, rest times or guards: each question needs 30+ fills a cell. **The paper bid lab (`BidLab`, v0.80.0) runs 14 recipes side by side on every line the bid desk looks at** (pregame: margin 2/3/4/6% x rest 30 min, each with and without a cancel-when-the-fair-moves guard, plus 3% and 4% resting 2 h with the guard) **and on every Pinnacle-priced live strike** (margin 1/2/3/4% x rest 2 min, with and without the guard), against Novig's real trade tape. A fill is a trade at or under the bid's price after it went up (queue position unknown, so "strictly through" is also counted); each fill is followed to the last fair seen before the start (CLV) and to the settled result. The real desk posts about 500 bids a day (7,213 in 14 days, 51 fills); the lab posts the same lines 14 ways. Placing nothing, it needs no wallet.
**Decision rules (fixed before the data):** a recipe beats another only with 30+ fills each; rank by fills per bid-hour x CLV, never by ROI (luck); the guard is worth adopting if its CLV at +120 s beats the unguarded recipe's by a point with fills within 30%; a slice (kind, side, hours, league, books) becomes a rule only with 30 fills in it and a CLV a point and a half away from the recipe's.

### 122.3 Questions the file will answer (each is a table in the research file)
1. Which margin (2, 3, 4, 6%) maximises fills x CLV; is the real desk's 3-4% right?  2. Does resting 2 h beat 30 min?  3. Is the guard worth its lost fills (the live python sim says yes, +4.0% vs +2.3% at +120 s)?  4. Under vs Over, kind, league, hours to start, books behind the fair: where does the maker keep the edge?  5. Live bids on Pinnacle's price: do they fill, and do they keep the edge?  6. Does a 4-6% margin on big-fair lines fill at all?  7. Cross-venue maker (RESEARCH §121 M2): next, once a Kalshi key exists.

### 122.4 How Tj runs it (also in RUN_RESEARCH.md)
1. Settings > Research mode > turn on the switch. 2. Leave the app open and the phone charging through games and a few days of pregame (nothing is bet: it places no orders). 3. Whenever you like (best after 2-3 evenings): Settings > Research mode > "Share research file with Claude" and send me the file; also tap the existing "Share diagnostics" and send that one. That is all.

### 120.7 The bigger live sample (4 tapes, 153 minutes of clock, about 51 minutes of recording; 21 matched games: football 5, basketball 5, hockey 9, baseball 1; 10,754 clean rows) and what it changed
- **Re-quote latency after a Pinnacle move of 1.5+ points: median 4.2 s (p25 2.0, p75 9.0, p90 17.0; n=359, 25 with no change within 60 s)**: basketball 3.9 s (239), football 5.0 s (96; 15 of them unchanged in 60 s), hockey 4.6 s (14), baseball 3.6 s (10). **Hockey is two readings that disagree, not one that overturns the other**: Novig's FIRST best-bid change after a Pinnacle move comes in 4.6 s (14 moves), but the lead-lag correlation of the whole move still peaks at +20 s (0.41 on 1,711 grid points, as in §120.2): the first re-quote is not the full adjustment, hockey's mid seems to finish moving about 20 s later. Fourteen moves cannot settle which matters; a hockey evening of the paper lab is the test.
- **The order-delay test, n=87-90 scores (all but a few basketball baskets): buying the scorer at Novig's ask d seconds after the score, against Pinnacle's fair 30 s later: 2 s -1.4% median (mean -2.9%), 3.5 s -0.1%, 5.3 s -0.7%, 8 s -0.8%, 12 s -1.0%; 34-37% of orders >= +2%.** Same answer as §120.2: no taker edge after a score at any delay.
- **Edge rows against Pinnacle: 11.0% of rows are +2% or better (1,185), +4.5% now, +2.5% at +30 s and +120 s (n=679/849)**: unchanged.
- **Make orders on the bigger tape (trade tape restored for the early games from the saved file; 907-1,034 paper bids per recipe over about 14.5 bid-hours, 38-82 fills a recipe), ttl 60 s:** the guard keeps the edge, the unguarded bid loses it by +120 s.
  | margin | no guard: fills/bid-hour, EV at fill / +30 s / +120 s | guard on a Pinnacle move: same | guard + no posting within 30 s of a score |
  | :- | :- | :- | :- |
  | 2% | 5.4, +3.0% / +1.4% / +0.1% (82 strict -0.6%) | 3.9, +3.2% / +3.1% / +1.4% (strict +1.2%) | 4.4, +3.2% / +3.2% / +1.4% |
  | 3% | 5.6, +4.0% / +3.0% / +1.9% (strict -1.8%) | 4.1, +4.3% / +4.2% / +3.5% (strict +2.5%) | 5.1, +4.3% / +4.2% / +3.5% (strict +3.0%) |
  | 4% | 4.5, +4.7% / +1.9% / +1.1% (strict +0.3%) | 3.0, +5.2% / +4.4% / +3.7% (strict +3.5%) | 3.3, +5.2% / +5.0% / +4.3% (strict +5.1%) |
  So with 45-82 fills a row: **at +120 s a guarded 3-4% bid is worth +3.5-4.3% against +1.1-1.9% unguarded (a gain of 2-3 points for about a third of the fills)**; strictly-through fills agree (+2.5..+5.1% vs -1.8..+0.3%). This is the strongest make-bid result so far, and it is on PAPER: the queue, Novig's acceptance during a pause and the guard's real latency are not in it. (ttl 30 s and 180 s: `maker_union_ttl30.txt`, `maker_union_ttl180.txt`, same shape.)
- Files: `research/pinnodds_2026-10-08b/` (`deep_all_4tapes.txt`, `maker_union_*.txt`, `trades_union.ndjson.gz`). The pregame tape (7 minutes) still says nothing; a US-daytime pregame run is still to do (scheduled 16:50Z, which needs the session awake; the in-app paper bids cover pregame without it).


## 123. Live betting on Novig: finding +EV in SITTING live quotes, and a live make-bid engine that posts and pulls fast (2026-10-10; SV1-SV10; Tj: "I see wildly mispriced odds on live betting on novig everyday. Investigate how to incorporate a novig live betting feature that finds positive EV in sitting live bets available and also a system to auto bid live make bets that rapidly post and cancel and maintain positive EV")

Method: the repo's own studies (§21, §83, §95, §106, §116-§122, NOVIG_API.md §17/§18/§21), Tj's v0.78.0 / v0.83.4 Diagnostics and the v0.84.1 research file, the stored Pinnacle/Novig tapes (re-simulated, no Pinnodds call), and a new read-only measurement of Novig's PUBLIC live books on 2026-10-10 ~03:15-03:30Z (4 NCAAF games + 1 WNBA game, 462 books; `tools/research/novig_live_sitting.py`; data and outputs in `research/live_sitting_2026-10-10/`). No key, no order, no Pinnodds connection by this session. Nothing was built (an investigation request).

**In short.**
1. **What "wildly mispriced" is.** Live ladders are thin. Moneylines are tight (mean spread 0.7 points) but only 47% of live SPREAD markets and 17% of live TOTAL markets are two-sided within 6 points, and **84% of TEAM_TOTAL sides have no price at all** (82 of 356 TOTAL sides, 44 of 352 SPREAD sides too). Two-sided books average a 30-point spread on totals, 19 on spreads, 59 on team totals. Example (Iowa State @ BYU, live): ISU +3.5 bid 1.3c / ask 65.5c; BYU -1.5 bid 42c / ask 99.9c while ISU -1.5 sat at 9-14.5c (BYU -1.5 is worth about 85c). That is what Tj sees. It is a market with almost no market makers on the alternates, not a market full of free money: a TAKER pays that spread.
2. **Taker side (sitting quotes): a scanner is cheap and worth building as ALERTS, an auto-taker is not supported.** The three things that make a sitting quote +EV without an outside live price are already in the repo as paper detectors (tail strikes the game has decided, alternate vs main-line fair, ladder covers); a crude ladder-fit I ran today flagged 22 of 462 books, most of them tail artifacts (asks under 3c) or inside the fit's own error (rms 0.11-0.13 probit units): **no EV claim from it**. Resting quotes do sit (below), but the ones that are good get taken: Tj's 37 real live IOC orders filled 0 (34 "the offer was gone", 24 of them with no Pinnacle move behind them), and an in-play order takes about 5.3 s to be answered (point 4).
3. **Maker side (live bids that post and pull): this is the one the evidence favours, and it survives Novig's delay.** Re-running the stored 2026-10-08/09 tapes (21 games, ~50 min of recording, small) with the real in-play order time as BOTH the place and the cancel delay (5.3 s): a bid at Pinnacle fair /(1+3%) with a cancel-when-Pinnacle-moves guard is worth **+3.0% at +120 s (strict fills +1.9%..+2.5%)** against **+1.8% unguarded (strict -1.8%)**, unchanged from the 0.3 s / 2 s run (+3.45% / +1.94%). A 20 s ttl fills more (6.5-7.5 fills per bid-hour against 4.0-4.8 at 60 s) at +3.5% at +120 s. It is paper, queue position is ignored, and the fair is Pinnacle's: see 5.
4. **The order delay is real and is Novig's.** The app's "order time" is send-to-terminal with 80 ms then 400 ms polling, so the median 5,330 ms (slowest 5,716, a tight band) against `/v3/orders` averaging 94 ms to first byte pregame leaves about 4.9 s on Novig's side: an in-play acceptance delay that no Novig page documents. A taker needs a quote that stays wrong for 6+ s; a maker only needs its price to be right when it lands and its pull to be fast enough, which is why the maker survives and the taker does not. **Not known: whether a post-only order and a cancel are delayed the same way in play.** One measurement decides it (SV6).
5. **The live fair feed is the cost, and it expires tonight.** The only sub-second live sharp price is the Pinnodds socket: Tj's trial ends 2026-10-10 23:34Z and it costs at least $198 a month after (§116). SportsGameOdds Pro is not a live feed (about 30 s refresh at best, DraftKings read a median 523 s old in the GitHub lab's tape), so the 131 live paper fills in the v0.84.1 file are priced off a stale fair and prove nothing about live. Kalshi/Polymarket lag Novig (§21, §106). The free alternative to test is a **Novig-anchored fair**: Novig's own tight main lines (moneyline, main spread/total, halves) are re-quoted by its professional makers within about 3.5-4.3 s of a play, and the thin alternates, team totals and props can be priced from them (the repo's TailModel/AltLineScan idea) with the pull triggered by the anchors moving on Novig's own `book` stream. Unproven; it costs nothing to record on paper.
6. **Fee fact that changes the sums:** the live catalog now shows `fee.coefficient` **0.06** on every NCAAF game market (spread, total, team total, moneyline, 1H) and 0.03 on WNBA; NOVIG_API.md and the §116-§122 simulations used 0.03. A taker's live cost at even money is 1.5c a contract (3% of the price) on NCAAF, and the maker credit (50% of the taker's fee) is 0.75c a contract, **+1.5% of a 50c bid on its own**, paid in cash within 7 days. College football is the best place for a live maker by this alone. (The app reads `fee` per market, so nothing it does is wrong; the research numbers were.)
7. **Recommendation:** build the maker as a short-ttl, event-pulled engine in paper first, and the taker side as an alert-only tab. Do not pay $198 a month for the feed on this evidence; use tonight's remaining trial hours to record Saturday's college football (the biggest live slate of the week) in paper on BOTH fairs so the anchor-guard and the Pinnacle-guard can be compared, then decide. Stages and gates in 123.5; the one decision only Tj can make is at the end.

### 123.1 What the public live books look like (SV2; `research/live_sitting_2026-10-10/sweep1_report.txt`)
| kind | markets | two-sided | within 6 pts | empty | sides with no price | mean spread (two-sided) |
| :- | -: | -: | -: | -: | -: | -: |
| MONEY | 5 | 5 | 5 | 0 | 0 of 10 | 0.7 |
| SPREAD | 176 | 145 | 83 | 13 | 44 of 352 | 18.8 |
| TOTAL | 178 | 118 | 31 | 22 | 82 of 356 | 30.2 |
| TEAM_TOTAL | 103 | 6 | 0 | 76 | 173 of 206 | 59.1 |
Fee coefficients (live catalog): NCAAF 0.06 on all 390 game markets read, WNBA 0.03 on all 72. A book's ask is 1 minus the best bid on the OTHER outcome, so a side with no opposing bid shows 99.9% (§120.6). The ladder also holds plain inconsistencies a person sees at a glance (a 0.001 bid beside a 0.42 bid on the same number), which no taker can trade without a view of the true price.

### 123.2 How long a resting quote sits (SV2; `survive1_report.txt`)
24 random live books were re-read 31 times in 4 minutes (about 8 s a pass) and every resting order followed by its public order id (161 orders already resting at the start). Share still resting after: **5-8 s 80%, 30 s 65%, 60 s 36%, 120 s 10%** (top of book: 69% / 44% / 24% / 11%; orders of 50k+ contracts: 76% / 59% / 23% / 7%). The best bid on one side or the other changed between two reads 43% of the time. So an average quote outlives a 5.3 s order, and the top of the book turns over in tens of seconds, not milliseconds. **This is an average over all quotes. The profitable ones are selected for being taken**, which is why Tj's 37 orders at apparent edge missed; the sample cannot separate the 5.3 s delay from competing takers (revealed preference: a "+EV" quote that has sat for minutes is more likely a flaw in our fair than a gift).

### 123.3 Maker sensitivity to Novig's delay (SV2; `maker_latency_sensitivity.txt`, tapes `research/pinnodds_2026-10-08b/`)
`pinn_novig_maker.py sim` on the four tapes + the trade tape (the same inputs as §120.7), live, maker credit at 0.03, ALL sports. EV = median % of price, at the fill / +30 s / +120 s against Pinnacle's later fair; strict = trades strictly through the price.
| place / cancel delay, ttl | margin | unguarded: fills per bid-hour, EV at fill / +30 s / +120 s, strict +120 s | guarded (cancel on a Pinnacle move): same |
| :- | -: | :- | :- |
| 0.3 s / 2.0 s, 60 s | 3% | 5.6, +4.0 / +3.0 / +1.9%, strict -1.8% | 4.1, +4.3 / +4.2 / +3.5%, strict +2.5% |
| **5.3 s / 5.3 s, 60 s** | 3% | 5.5, +3.9 / +2.0 / +1.8%, strict -1.8% | 4.0, +4.3 / +4.0 / +3.0%, strict +1.9% |
| 5.3 s / 5.3 s, 60 s | 4% | 4.6, +3.4 / +1.4 / +1.0%, strict +0.2% | 3.1, +5.1 / +3.5 / +3.1%, strict +3.4% |
| 5.3 s / 5.3 s, 20 s | 3% | 7.4, +4.0 / +3.7 / +2.5%, strict -1.9% | 6.5, +4.2 / +3.9 / +3.5%, strict +2.5% |
| 5.3 s / 5.3 s, 120 s | 3% | 4.2, +4.0 / +3.0 / +1.9%, strict +1.2% | 2.9, +4.4 / +4.0 / +3.0%, strict +2.2% |
Read: (1) the guard is worth 1-2 points at +120 s at every delay tried and costs a quarter to a third of the fills; (2) a 5.3 s cancel keeps nearly all of that; (3) shorter ttl trades EV for volume and, importantly, **bounds the damage of a cancel that never lands** (a 451 or a dead connection: §123.4); (4) 45-82 fills a row, 24 minutes of mostly basketball and hockey: hints. Caveats the sim does not have: the queue ahead of the bid, whether Novig accepts a post-only order in play and how long it holds it, and the 201: the sim lets a pull issued while the bid is still in flight cancel it 5.3 s later (exposure from landing to the pull's arrival), which is only real if the cancel can be sent before the order has landed, and the bid price is fixed at decision time, so a score during the 5.3 s of flight lands the bid mispriced (up to ~10 s of exposure per bid at worst if the `201` itself is held for the delay and the cancel needs the order id).

### 123.4 Design

**A. Live Maker (LM): live bids that post and are pulled by events.** "Rapidly" is not the edge (the order delay is ~5 s each way); **being right when it lands and out in time when it is not** is.
- *Quotes:* for each live line with a trusted fair: `PO` bid at `floor(fair / (1 + margin))`, below the offer (never takes), margin 3% default (4% on thin lines), sized small (Kelly fraction of the edge, capped), `ttl` **15-20 s and re-posted by a timer** (no amend: the replacement joins the back of the queue, §17, cheap in thin books where little sits ahead). The ttl is the dead-man switch: if the phone dies, the socket drops or a cancel gets a 451, the exchange removes the bid by itself. Capacity: `place` refills 8 a second, so 15 s ttl supports about 120 concurrent bids.
- *Pulls (cancel at once, any one of them):* (1) the fair source moves so the bid is under +1% EV (the sim's guard); (2) a score, a `dz` danger-zone frame, a period change or a lifecycle event (`DELAYED`, `GOLIVE` voids by itself); (3) **anchor move**: the game's own tight Novig lines (moneyline, main spread/total) change by more than a threshold on the `book` stream (16 tokens a market against the 512 bucket: about 8 markets a game for 4 games; `bbo` at 8 tokens has no documented frame, §21); (4) the fair is older than N seconds (a stale feed pulls everything: `cancel_all` over the socket is one `cancel` token); (5) a fill: re-evaluate before re-posting that side.
- *Fair sources, in order of trust:* Pinnacle live (Pinnodds, only while paid), Novig-anchored fair for alternates/team totals (free, unproven), and nothing else live (SGO and Kalshi are too slow to be a fair, fine as a veto).
- *Risk limits (all in settings, defaults tiny):* stake per bid, open bids per game, **net exposure per game and per side** (one score fills every bid on one side at once: a correlated pick-off, the thing a per-bid cap misses), day loss halt, wallet counted against resting bids (Novig holds nothing back, §17), the existing STOP ALL, no bids in the last minutes of a decided game unless the tail model says the line is not decided, and a hold-off after a score for N seconds (the sim's pin+score variant).
- *Accounting:* fills enter the Tracker as source "Live bids" (the bids/bets split already exists, §111); Diagnostics shows place-to-open ms, cancel-to-cancel ms, pulls by reason, the pick-off rate (fills whose fair fell under +0% within 30 s), fill CLV at +30/+120 s, credit earned.
- *Compliance:* the Maker Credit terms bar wash trades and conduct "designed to generate Maker Credits without bona fide market risk". A bid with real risk that is pulled when the price moves is not that, but a high cancel-to-fill ratio is what exchanges review: the docs read in §18/§21 state no cancel limit (only the `cancel` bucket, 16 a second), and nothing here has been checked with Novig. Ask developers@novig.com before running at scale.

**B. Live Sitting (LS): an alert-only tab for sitting quotes.** Detectors that need no outside live price, each already in the lab as paper (`TailScan`, `AltLineScan`, `LadderScan`, `LabRecorder`): (1) tail strikes the score and clock have decided; (2) an alternate or team total priced against the game's own liquid lines with a conservative spread; (3) ladder covers (none seen in 1,048 pairs). With Pinnacle on, the stale-vs-Pinnacle detector (`LiveEdge`) is the same list's fourth source. Each alert shows the edge AFTER the live fee (0.06 on NCAAF), the fair's source and age, the depth, **how long the quote has sat (order id first seen)**, and a plain "expect the order to take about 5 s". Tap to open, no auto-bet until graded paper results exist for that detector (the v0.84.2 grader now grades TAIL/ALT from final scores).

### 123.5 Stages and gates (nothing is built until Tj says so)
| stage | what | gate to the next |
| :- | :- | :- |
| S0 (SV6) | **Live order-timing probe** (Settings › Research): on a live game, 20 times, post a 1-contract `PO` bid at 1-2c on a far outcome (cannot fill, risks cents), time REST `201`, the private-stream `open`, then `DELETE` and the `cancel` event; also ttl 2 s and 5 s to find the shortest accepted; **time the `201` itself, and whether a scope cancel (`DELETE /v3/orders?outcome=` or the socket's `cancel_all`) sent while an order is still in flight stops it landing** (a scope cancel needs no order id); a pregame control; record every refusal (`NOT_LIVE_TRADABLE`, 451). | place and cancel p90 under about 8 s, PO accepted in play on game lines and props |
| S1 (SV7) | **Live maker lab v2 (paper):** BidLab live recipes with the fair that is actually live (Pinnodds when on, Novig-anchored always), ttl 15/20/60 s, margins 2/3/4%, guards pinn / anchor / both, the measured delays, the per-market fee and maker credit. The GitHub lab runs the anchor recipes on public data all week. | 30+ fills a recipe, graded, an independent close, anchor-guard within 1 point of the Pinnacle-guard |
| S2 (SV8) | **Live Sitting tab (alerts only).** | graded paper TAIL/ALT results (about 2026-10-16) |
| S3 (SV9) | **Live Maker at $1**, a $5 day cap, one league (NCAAF/NFL), all pulls on, STOP ALL, ttl 15 s. | a week of fills with CLV and the pick-off rate |
| S4 (SV10) | Only if S0 shows REST is the bottleneck: `place`/`cancel` over the already-open websocket (NOVIG_API.md §21, PW9). | measured gain |

### 123.6 What the money is, honestly
At the sim's strict rates (about 2.2-2.5 fills per bid-hour at 3% margin, guarded, +2-2.5% at +120 s), 30 concurrent bids for a 5-hour slate is 150 bid-hours, about 330 fills; at $3 a fill that is $1,000 of volume and **about $20-25 an evening if the paper edge is real**, before the queue and adverse selection the tape cannot see, plus the credit (+1.5% of price on NCAAF). On Tj's bankroll that is the ceiling, not the forecast; the first evenings will be cents to a few dollars and are about learning. **$198 a month for the feed is more than that ceiling**: the free anchor variant is the one worth proving, with Pinnodds kept only if it beats the anchor guard by a clear margin.

### 123.7 Not known, and what answers it
- In-play `PO` acceptance, place delay and cancel delay: S0.
- Whether a Novig-anchored fair is good enough to price alternates and team totals, and whether its guard pulls fast enough: S1 on Saturday's slate.
- Whether the fills a thin-ladder bid gets are informed (the pick-off rate in S1/S3), and the queue: S3.
- Whether a high cancel-to-fill ratio is acceptable to Novig: ask.
- Whether the tape's 0.03 results change at 0.06 (the credit doubles, the taker's flow may thin): S1 records the per-market fee.
- Tj's 451 refusals (853, 383 on `/v3/orders`): a phone on an address Novig distrusts cannot run a maker safely except behind a short ttl.


## 124. Live bids, built: what the presets rest on, what the safeguards are, and what is not known (2026-10-10; SW1-SW6; Tj: "Build a live bid feature ... strong safeguards ... the live bids are truly EV ... presets I can save and manual fields ... a couple default presets that are safe positive EV ... I want to run this very soon with real money, but it will be ⅛ Kelly or something small. Build it for real money")

**What was built** (v0.85.0; code `data/.../livebid/`, `PinnLiveRunner` integration, `app/.../ui/LiveBidSettings.kt`; tests `LiveBidJudgeTest`, `LiveBidPresetsTest`, `LiveBidDeskTest`, `PinnLiveRunnerBidTest`, `LiveBidUiTest`): Settings › Live bids. A post-only (`PO`) bid on a side of a Novig live line, priced at `floor_tick(Pinnacle's live fair / (1 + margin))`, sized by a stake rule (⅛ Kelly of the bankroll by default, or any Kelly fraction / a fixed dollar amount), resting for a ttl that Novig itself enforces, renewed by a successor that goes up before it ends, pulled by events. Paper (default when the feature is on: nothing is sent, fills are inferred from Novig's book, with the measured 5.3 s in-play delay added to the post and the pull) and real (a second switch behind a confirmation in Tj's own numbers; switched off by STOP ALL, a crash, a pause and any halt).

**How a bid is kept from going stale and kept truly +EV** (each is a named reason in Diagnostics' "Why bids came down / Why no bid"):
1. *The price is judged every 500 ms and on every Pinnacle frame, score, danger-zone and close message.* The desk pulls any bid not vouched for within 3.5 s (a heartbeat/claim model: silence is a pull). A want older than 2 s is not acted on.
2. *EV is checked against a fresh, devigged fair* (worst-case devig by default, from the line's own American prices), never against a stale one: Pinnacle silent for more than `maxQuietSec`, a price that has sat for `maxFairAgeSec`, a book the app and Pinnacle disagree on by more than `maxBookGap`, an overround above `maxOverround`, a Pinnacle limit under `minPinnLimit`, the fair or the bid price outside their bands, a line type or league switched off, all skip. A posted bid comes down when its EV against the CURRENT fair falls to `pullBelowEv`.
3. *Events pull at once and hold off:* a score change (`scoreHoldSec`), a danger-zone frame (`dangerHoldSec`), the line closing or leaving the feed, Novig's own price moving toward the bid by `novigMovePull`, a side that has just filled (`coolOffSec`).
4. *Novig is the dead-man switch:* every order carries `ttl`, so a phone that dies leaves nothing up for more than the ttl (10 s floor, 30 s default). A refresh time longer than half the ttl is held to half (a misconfigured pair cannot make every bid renew the moment it is posted).
5. *Money is counted worst-case:* every bid not yet over counts as if it fills, the one being replaced too; the wallet must cover every bid up plus the reserve; caps per game, per day and on the number of bids up; the day's API limit. A lost answer is never re-sent (the order is looked up by its client id, and called gone after 90 s); an order Novig queued in play (`PENDING`) is waited for up to 30 s.
6. *The feature stops itself:* a run of picked-off fills (a real fill whose Pinnacle fair, 30 s later, is under the price paid: 3 of 5 in Careful, 4 of 6 in Balanced), a pull that measures slower than `maxCancelSec` (p90 of the last 10), a place slower than `maxPlaceSec`, three lost answers, a day's loss at `haltLoss`, posts far faster than the limits explain (a runaway-loop breaker), a network refusal (451/423/401/403: stood down 10 min), 429 (backs off 10 s), a market Novig will not trade in play (blocked 10 min). A halt switches the feature off until Tj presses Resume.
7. *Fills are read and measured:* a fill is recorded in the Tracker as a "Live bids" bet; for every fill the report keeps the EV at the post, and the EV of the SAME price against Pinnacle's fair 30 s and 120 s after the fill (with the share picked off) , plus the time the order took to be accepted, to be on the book and to be pulled: the means, not the medians.

**The presets (what each rests on; sample sizes are small, read them that way).** Stored Pinnacle + Novig tapes, 21 games, about 50 minutes, replayed with the 5.3 s in-play delay as both the place and the cancel time (`tools/research/live_bid_grid.py`, output kept in `research/live_sitting_2026-10-10/live_bid_grid_output.txt`; every cell below is n = 1-13 fills; none is significant alone, the TREND across margins is what the presets use):
- *Margin.* The mean EV per fill 120 s later rises with the margin (ttl 30 s, 1% floor, 30 s hold, bid behind the best: 2% +3.5% (n=5), 3% +8.0% (6), 4% +3.6% (8), 5% +6.3% (6), 6% +5.9% (6), 8% +8.9% (3), 10% +14% (1)); the fills per hour fall (1.4, 1.4, 1.7, 1.2, 1.1, 0.5, 0.2 per resting bid-hour). Fills times edge is flat from 3% to 6% (about 5-11 in these units, inside the noise) and worst at 2%; the share picked off is lowest at 3% and from 8% up. **Careful = 6%** (the safe end where there are still fills), **Balanced = 5%** (the middle of the flat part, with more fills than 6%), and 4% or under is paper-only (**Paper study**: 4%, ttl 60 s, +1% a fill in the earlier replay, inside the noise).
- *The guard's EV floor and the post-score hold* barely move the means at these sample sizes (a 0 s hold was the worst cell in four of the five margin rows at ttl 15 s, e.g. 2%: -1.8% vs +1.4% with 15 s; 4%: -1.7% vs +2.4%; at 5% it was the best, +6.2% vs +4.0%: n = 3-5), so both stay on (30 s after a score, 10-15 s after a danger frame) as a sound prior rather than a finding.
- *Position.* Bids that lead the book (price above the best bid) filled several times as often per bid-hour as bids behind it and were not visibly worse (5%, ttl 60 s: lead +7.4% mean (n=9), behind +4.2% (12)); bids that join the best bid filled most per bid-hour (11-24 a hour; at 3-4% means of +3% to +6%, n = 7-14). The sample cannot show leading is harmful, but a leading bid is the one an informed taker hits first, so **Careful keeps `neverLead`** as a prior; Balanced does not.
- *ttl.* 15 s bids had too few fills to tell (2-7); 30 s and 60 s were similar. Both presets use 30 s (a pull that fails costs less when the order dies by itself sooner).
- **A correction to §123:** its in-short line quoted per-fill MEDIANS (+3.0% at +120 s). The means (which count the fills that lose) are lower in the thin cells and the shares picked off are 17-40% in them. The presets and the report use means and picked-off shares.

**Stake and limits (Tj's money, never changed by a preset):** ⅛ Kelly of the bankroll on the edge (`fraction × (fair - price)/(1 - price) × bankroll`), raised to the minimum stake ($1), capped at the maximum ($5) and at Settings' API max stake; at most 8 bids up, 3 per game, $10 per game, $40 filled-plus-resting per day, a day's halt at $15 lost, $5 wallet reserve left alone. At a $1,000 bankroll, ⅛ Kelly on a 5-6% edge at about even money is roughly $5-$6, so the $5 cap is what bites: Tj should set his real bankroll in Settings › Betting & Novig account before turning real money on, because the stake is a share of THAT.

**NOT known (the first real order is the test):**
- No real post-only bid has been sent to Novig in play. Queue position, the real time to land and to be pulled, whether a `PO` with a short `ttl` is accepted in play on every market, and how often Novig refuses with 451/423 are all unmeasured. The desk measures them and halts on a slow pull, but the first real session is the experiment: **watch paper first, then real at $1 stakes (Custom $1, max stake $1) for the first hour.**
- Whether a high cancel-to-fill ratio is acceptable to Novig (TASKS.md SV11: ask developers@novig.com, Tj's word first).
- Whether the tapes' +4-6% survives a day of real flow: 21 games, n = 1-13 per cell. The live report (EV at post / +30 s / +120 s, picked-off share) is the instrument; judge it at 100 real fills before widening any limit.
- Pinnodds: this uses the app's own Pinnodds socket (one per account; no Claude session opens it). The trial ends 2026-10-10 23:34Z and a plan with the WebSocket add-on is at least $198 a month: without the key there is no live fair and no live bids (the feature says so in its status line).
- Main-line moneyline / spread / total only (what `LiveMatcher` pairs with Pinnacle); alternates, team totals and props have no live fair here (§123.4's later stages).

## 125. Why live bets and bids rarely fill, and the one-tap autopilot built on it (2026-10-10; TL1-TL3; Tj: "Very few of my live bets and bids are actually filled. I want to live bet and bid today ... One feature that can handle all of the following simultaneously ... figure out why I don't get a lot of action")

**Takers (IOC, Pinnodds trigger).** 21 of 23 (later 34 of 37) real orders MISSED, almost all "offer gone". Cause is structural, not a bug: a live order is answered by Novig about 5.3 s after it is sent (§120.1) and a stale quote lives about 2-4 s, so by the time the order lands the quote has been taken or repriced. `LiveEdge.reachPrice` already lets the limit climb to the last grid price that still clears the minimum EV, so a mere one-step requote is covered; only a vanished quote is not. What can be done: widen the funnel (more triggers, lower minimum edge) so more candidates reach the 5.3 s lottery, and rely on persistent mispricings. A "rest on miss" (turn a missed IOC into a resting bid) was designed and DROPPED: the bid desk already bids the same line at fair/(1+margin) whenever the offer is gone (the case where the IOC missed), so it adds nothing but a second code path.

**Bids (post-only).** The funnel before a bid is posted is long and every stage was tuned conservative: overnight slate was tennis (off by default) and alternate strikes (unpriced: "no Pinnacle line prices this market x46686, tennis off x40836"), then settle 3 s, 30 s hold after a score, Pinnacle limit >= $500-1000, overround <= 7.5-8%, book gap <= 8-10 points, one side per market, never-lead in Careful, 8 bids / 3 a game / $10 a game / $40 a day. A bid is priced at `floor(fair / (1 + margin))`: the margin IS the price, and on the tapes (research/live_sitting_2026-10-10) fills a bid-hour fell from about 2 at 3% to about 0 at 8%, while the edge kept per fill rose with it. So fills and edge trade directly; at 5% the book mostly sits above the bid.

**What was built (v0.87.0).**
1. `LiveBidPresets.FILL` ("More fills"): 3% margin, may lead, both sides, settle 2 s, holds 15 s / 6 s, Pinnacle limit >= $250 and margin <= 9%, book gap 12 points, 20 s bids refreshed at 6 s, credit counted (0.4-1.5% in play by league). Every pull rule stays on (score, danger frame, Pinnacle silence, edge under 0.5%, Novig middle falling 5 points, pick-off stop 4 of 6, slow pull/place stops).
2. `LiveBidLimits.fillWallet` + `effective(wallet)`: bids up and per game lifted to 60 / 6, a game may carry 25% of the wallet, a day twice the wallet; the wallet check (reserve kept, every resting bid counted) and the day-loss halt are unchanged. Tj's stake rule (1/8 Kelly, $1 minimum) sizes each bid, so ~60 bids is ~$60-100 at risk, not the wallet in one game.
3. `LiveTrigger.EITHER` for the taker: a score-driven Pinnacle move OR an ask left at Pinnacle's earlier price, judged on the regular sweep too.
4. `LiveAutopilot`: one tap (paper, or real behind a confirmation) turns both engines on with these rules, 4% minimum taker edge and 2-point move; Tj's own stakes and caps stay.
5. `LiveBidReport.whyFew` (Diagnostics "Why so few fills" and on the Live bids page): where each bid sat against the best bid when posted (led / level / behind) with fill rates, the median gap, how long unfilled bids lived and how they ended, what pulled them, and a one-line reading. This is how the next session finds the real daytime cause from Tj's Diagnostics instead of guessing.

**Not known (be honest).** Nothing here has been run on a live slate from the container. A 3% edge is thin and the 20-fill samples are inside the noise, so every number is a hypothesis until the "Why so few fills" block and the 30 s / 120 s follow-ups have data. **The Pinnodds trial ends 2026-10-10 23:34Z; every Pinnacle-fair feature (taker and bids) stops with it** (the feed costs at least $198 a month). The only live fair that survives is the ESPN-state tail model (TG, Phase B).

**Addendum (v0.87.1): the live tail bettor.** Because every Pinnacle-fair feature stops with the Pinnodds trial at 23:34Z, a third engine was added that needs no outside price: `novig/lab/TailTaker` (settings `ScanSettings.tailLive`, a `TailLiveSettings` group: ScanSettings was at 250 of the JVM's 255 constructor argument slots, `ScanSettingsSizeTest` guards it). The paper lab's `TailScan` (ESPN scoreboard score and clock via `LabClock`, the centre read off Novig's own liquid lines, the CONSERVATIVE `TailRules`: spread x1.25, centre 1.5 points against the bet, fair >= 90%) hands every conservative candidate to `LabRecorder.onTail`; the recorder still holds no trading client. The taker adds its own guards (fair >= 92%, edge >= Tj's minimum (5%) and <= 30% because a bigger one is a wrong game state, a minute between tries on an outcome, the conservative rules only), sizes at $1 (game $3, day $10, day-loss stop $5 by default), sends ONE IOC order whose limit climbs only while the edge stays at the minimum (`LiveEdge.reachPrice`), logs fills to the Tracker as source `tail` ("Live tail"), and stops on a lost answer, a day's loss, a 451/423 or a pending order. With only the tail switch on, the lab runs in `tailOnly` mode: up to 8 games (oldest first), none of the covers, outside quotes, paper bids or grading. The live autopilot turns it on with the other two. **Not known:** whether ESPN's score and clock lag the book enough to make a "decided" strike look cheaper than it is; the first real orders and the `tail-live` journal (Diagnostics › LIVE TAIL BETS) are the test, which is why the stake is $1.
