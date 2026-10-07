export const meta = {
  name: 'rapid-odds-sources',
  description: 'Research ten sources for a rapid cheap-or-free odds/scores feed for Vigilant: scout each, two skeptics each, synthesize, completeness critic, gap-fill, final (resumable: args.skip = labels already done)',
  whenToUse: 'Tj asks whether named sites/APIs/tutorials can give Vigilant a rapid source of odds or scores; also resumes that research in a new session',
  phases: [
    { title: 'Scout', detail: 'one agent per source: read it, trace its upstream, probe public endpoints politely' },
    { title: 'Verify', detail: 'two skeptics per source: claims and latency; fit, terms and cost' },
    { title: 'Synthesize', detail: 'one agent ranks everything' },
    { title: 'Critic', detail: 'what is missing or unverified' },
    { title: 'Gaps', detail: 'agents for the critic\'s gaps' },
    { title: 'Final', detail: 'final ranked answer and RESEARCH.md section' },
  ],
}

// RESUMABLE (Tj, 2026-10-07: a new session on another account must be able to continue with no data loss). Every finished agent's result is a file
// research/rapid_sources_workflow_2026-10-07/results/<label>.json (tools/research/save_workflow.py writes them while the workflow runs). Launch with
// args = {skip: [labels already saved]} (python3 tools/research/rapid_resume.py prints it): a skipped agent is not run again; whatever needs its result
// is told to READ the file instead. An agent that is run again reads partial/<label>.md first when an earlier attempt left one. See RESUME.md there.
const OUT = 'research/rapid_sources_workflow_2026-10-07'
const skip = new Set((args && args.skip) || [])
const safe = (label) => label.replace(/[^A-Za-z0-9._-]+/g, '_')
const cached = (label) => ({ __cached: true, label, file: `${OUT}/results/${safe(label)}.json` })
const isCached = (x) => !!(x && x.__cached)

async function run(label, prompt, opts) {
  if (skip.has(label)) return cached(label)
  const notes = `\n\nPARTIAL NOTES: if the file ${OUT}/partial/${safe(label)}.md exists, an earlier attempt of THIS task was cut off (usage ran out or the session ended). Read it first: it is a trace of what that attempt had already read and said. Continue from there instead of starting over. Do not write to that file.`
  return await agent(prompt + notes, { ...opts, label })
}

// a cached result that control flow needs (the critic's gap list): one small agent reads the file back
async function load(obj, schema, label) {
  if (!isCached(obj)) return obj
  return await agent(`Read the file ${obj.file}. It is JSON with a "result" field. Return that result verbatim as the structured output, changing nothing.`, { label: `reload:${label}`, phase: 'Critic', schema })
}

const asText = (x, what) => isCached(x)
  ? `(${what} was finished in an earlier session: it is stored in the file ${x.file}, JSON with a "result" field. READ THAT FILE FIRST with the Read tool.)`
  : JSON.stringify(x)

const SOURCES = [
  { n: 1, slug: 'medium-fastapi-odds-tracker', url: 'https://medium.com/@ayoubennaoui20/how-to-build-a-real-time-sports-odds-tracker-with-fastapi-websockets-angular-part-1-ff2de71c62d5',
    hint: 'A tutorial (Part 1; look for Part 2 and the author\'s repo). Find which odds API it polls and how often, how it pushes to the browser, and whether that upstream is fast. Medium may be paywalled or blocked: try the page, search snippets, and mirrors (say plainly what you could not read). If it uses The Odds API, read The Odds API\'s own docs for its real update interval and free-tier credit limits.' },
  { n: 2, slug: 'bksignal-odds', url: 'https://odds.bksignal.com/',
    hint: 'Unknown site. Find out what it is (odds comparison page, API, signal service), where its odds come from, how fresh the page says they are, whether it has an API or a socket, and its price.' },
  { n: 3, slug: 'devto-stop-scraping', url: 'https://dev.to/drengregious/stop-scraping-betting-sites-how-to-build-a-real-time-sports-tracker-in-python-46i9',
    hint: 'A dev.to tutorial arguing for an API over scraping. Find which API/vendor it recommends and uses (read its code), how it gets updates (poll interval or socket), what the vendor really offers (free tier, price, latency), and whether the article is an advertisement.' },
  { n: 4, slug: 'scraperly-oddsshark', url: 'https://scraperly.com/recipe/odds-shark/tutorial',
    hint: 'A scraping-service recipe for OddsShark. Find what Scraperly is and costs, how OddsShark\'s odds are loaded (JSON endpoint behind the page?), how stale OddsShark\'s odds are, and whether the page, a scraper over it, or OddsShark\'s own data could be a rapid source. OddsShark is an aggregator: say what it aggregates and how often it refreshes.' },
  { n: 5, slug: 'surebetfusion', url: 'https://surebetfusion.com/',
    hint: 'A surebet/arbitrage product. Find what it is, which books it covers (US? Novig? Pinnacle? exchanges?), what it says about odds refresh speed, whether it has an API or a stream Vigilant could use, its price, and whether its latency claims are measurable from public pages.' },
  { n: 6, slug: 'roundproxies-scrape-sportsbooks', url: 'https://roundproxies.com/blog/scrape-sports-betting-sites/',
    hint: 'A proxy vendor\'s guide to scraping sportsbooks. Find the techniques it teaches (hidden JSON endpoints, sockets, headless browsers), which sites/endpoints it names and whether they are fast, what blocking it says to expect, what the proxies cost, and what each route would break (terms, bans). Separate real technical facts from vendor marketing.' },
  { n: 7, slug: 'scrapingproxies-websocket-scraping', url: 'https://scrapingproxies.best/blog/web-scraping/websocket-scraping/',
    hint: 'A guide to scraping WebSocket feeds. Find the technique, whether it names real sportsbook or odds-feed sockets, which sportsbooks or exchanges actually expose a public socket a phone app could read without an account (verify at least two from their own pages or docs, e.g. Polymarket, Kalshi, Pinnacle, Betfair, Kambi), what it would break, and whether any of it gives a feed faster than polling.' },
  { n: 8, slug: 'devto-pulsescore-typescript', url: 'https://dev.to/pulsescore/how-to-fetch-live-sports-odds-via-api-with-typescript-bet365-paddy-power-more-o58',
    hint: 'A dev.to article by PulseScore on fetching live odds (bet365, Paddy Power ...). Find what the API call returns, whether it is polling or push, the claimed refresh rate, which books and sports (US coverage? props? sharp books?), the plan limits shown, and anything in the code that reveals the real latency.' },
  { n: 9, slug: 'github-odds-stream-engine', url: 'https://github.com/merlinfachetti/odds-stream-engine',
    hint: 'An open-source repository. Read the README and the source: what upstream odds API it uses, how it streams (SSE, WebSocket, queue), poll intervals, licence, activity (stars, last commit), tests, and what could be reused for an Android/Kotlin app that reads feeds directly on the phone. Say what is a design pattern worth copying and what is just a demo.' },
  { n: 10, slug: 'pulsescore-net', url: 'https://www.pulsescore.net/',
    hint: 'The PulseScore vendor site. Find pricing and any free tier (requests, websockets, live), endpoints and docs, sports and books covered (US sports, props, Pinnacle/Circa/exchanges), the refresh/latency claims and any measurement or third-party review, the terms of use (betting use, redistribution, automated use), and whether live scores or only odds are offered. Do not sign up.' },
]

const CONTEXT = `
WHAT VIGILANT IS: an Android app (Kotlin, on Tj's phone) that finds +EV bets on the Novig sportsbook (a US exchange-style book) and posts bids/takes bets through Novig's API. Its fair price comes from other books (CrazyNinjaOdds "CNO" which publishes every 13-33 s and is itself 1-2 min behind by its owner's words; Vigilant's own scan of Kalshi/Pinnacle/Polymarket/The Odds API/ParlayAPI/PropLine). Tj asks: is any of the ten sources below (or an API it names) a RAPID source of ODDS or SCORES that can be built or used, CHEAP OR FREE?

WHAT "RAPID" MEANS HERE (the bar, from earlier measurements in /home/user/novig/RESEARCH.md sections 84, 95, 99 and 106; read section 99 "In short" and section 106 first, only for context):
 - SCORES: after a play, Novig's market makers re-quote the moneyline a median 16 s after ESPN's stamp of the play; cross-line bursts come 5-13 s after it. A score feed matters only if it shows the score BEFORE that (>= 3 s before Novig's price moves). Already measured on tennis: Sofascore's REST poll every 2.5 s beat Polymarket's score socket by a median 6.4 s and showed 22 of 29 Novig-moving scores >= 3 s early (median lead 15-27 s). ESPN's play feed was 40.7 s late. Public score endpoints sit behind CDNs (cache 5-20 s).
 - ODDS: a sharp-book (Pinnacle, Circa, Kalshi, ProphetX, Sporttrade, Novig) or consensus price that is fresher than CNO's 13-33 s cadence plus its lag, i.e. pushed or polled at a few seconds with a TIMESTAMP that proves its age. A "real-time" claim without a stated age of the underlying price is worthless.
 - ALREADY KNOWN AND NOT TO BE RE-RESEARCHED (only compare against them): Polymarket sports score socket and CLOB odds socket (free, push), Kalshi, Sofascore REST, ESPN scoreboard/FastCast, MLB statsapi + gameday socket, NHL api-web, NBA cdn json (403 here), ParlayAPI (Tj's $5 plan), PropLine, PinnWire/pinnapi, The Odds API (500 credits a month), Odds-API.io (socket carries odds only, "heavy delays in scores"), SportsGameOdds (from $49 a month, Pusher streaming claimed ~100 ms), TheRundown (WebSocket only on Ultra, $399), OpticOdds (SSE, sales-led), sportapi.io (14-day trial), Big Balls Sports Data, balldontlie ($9.99 a sport), Sportradar/Genius/Stats Perform (enterprise), Apify actors (a hop on cached endpoints), Scrapeless (a browser rental on ESPN's cached JSON).

HARD RULES FOR YOU (read-only research):
 - Do NOT edit, create or delete any file under /home/user/novig. Use /tmp for scratch if you must. Do not commit or push.
 - GET requests only, to public pages and public endpoints. No sign-ups, no logins, no payments, no API keys requested or used, no POST, no form submission, no CAPTCHA or bot-protection bypass, no proxy tricks. If a page refuses this container (403, challenge), say so and stop there; do not work around it.
 - Never request anything from crazyninjaodds.com (this project's rule: the container never reads CNO).
 - Never scrape a bookmaker's or exchange's private/consumer API. Read documentation and public pages; probe at most about 12 requests to any one host, at least 1 second apart, and only endpoints that are public and documented as free of keys. If a site's terms forbid automated access, read the page but do not probe its API; record the term.
 - The repo is public: write findings and numbers only; never copy a key, token or password you see on a page.
 - Verification discipline: tag every important fact VERIFIED (you read it on a primary page; give the URL and a short quote), CLAIM_ONLY (the vendor says it, nothing proves it), INFERRED (your reasoning, say from what), or CONTRADICTED (primary evidence says otherwise). Never present marketing latency numbers ("real-time", "100 ms", "instant") as measurements. Prefer dates: say when a page was last updated and when you read it. If you cannot read something, say exactly what and why; do not fill it in from memory.
 - The container's outbound traffic goes through a proxy: some hosts will fail for that reason; say so rather than concluding the site is down.
`

const CLAIM = {
  type: 'object',
  properties: {
    claim: { type: 'string' },
    status: { type: 'string', enum: ['VERIFIED', 'CLAIM_ONLY', 'INFERRED', 'CONTRADICTED'] },
    evidence_url: { type: 'string' },
    quote: { type: 'string' },
  },
  required: ['claim', 'status'],
}

const SCOUT_SCHEMA = {
  type: 'object',
  properties: {
    source_url: { type: 'string' },
    kind: { type: 'string', enum: ['tutorial', 'vendor-api', 'aggregator-site', 'open-source-code', 'scraping-service', 'blog-or-guide', 'signal-or-arbitrage-product', 'other'] },
    what_it_is: { type: 'string' },
    could_not_read: { type: 'array', items: { type: 'string' } },
    upstream_data_source: { type: 'string' },
    push_or_poll: { type: 'string' },
    claimed_latency_or_refresh: { type: 'string' },
    measured_or_verified_age: { type: 'string' },
    coverage: { type: 'string' },
    price_and_free_tier: { type: 'string' },
    terms_notes: { type: 'string' },
    key_claims: { type: 'array', items: CLAIM },
    live_probes: { type: 'array', items: { type: 'object', properties: { request: { type: 'string' }, result: { type: 'string' } }, required: ['request', 'result'] } },
    other_sources_it_names: { type: 'array', items: { type: 'object', properties: { name: { type: 'string' }, url: { type: 'string' }, why_it_matters: { type: 'string' } }, required: ['name'] } },
    verdict: {
      type: 'object',
      properties: {
        rapid_odds: { type: 'string', enum: ['yes', 'maybe', 'no', 'not-applicable'] },
        rapid_scores: { type: 'string', enum: ['yes', 'maybe', 'no', 'not-applicable'] },
        ahead_of_novig_makers: { type: 'string', enum: ['yes', 'unknown', 'no'] },
        ahead_of_cno: { type: 'string', enum: ['yes', 'unknown', 'no'] },
        cheap_or_free: { type: 'string', enum: ['free', 'cheap-under-50-a-month', 'expensive', 'unknown'] },
        usable_in_vigilant: { type: 'string', enum: ['yes-as-is', 'yes-with-work', 'technique-only', 'no'] },
        why: { type: 'string' },
      },
      required: ['rapid_odds', 'rapid_scores', 'ahead_of_novig_makers', 'ahead_of_cno', 'cheap_or_free', 'usable_in_vigilant', 'why'],
    },
    what_the_phone_should_test: { type: 'array', items: { type: 'string' } },
    open_questions: { type: 'array', items: { type: 'string' } },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
  },
  required: ['source_url', 'kind', 'what_it_is', 'upstream_data_source', 'push_or_poll', 'claimed_latency_or_refresh', 'measured_or_verified_age', 'coverage', 'price_and_free_tier', 'terms_notes', 'key_claims', 'verdict', 'confidence'],
}

const SKEPTIC_SCHEMA = {
  type: 'object',
  properties: {
    lens: { type: 'string' },
    claim_checks: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          claim: { type: 'string' },
          verdict: { type: 'string', enum: ['confirmed', 'overstated', 'unsupported', 'false', 'could-not-check'] },
          corrected_statement: { type: 'string' },
          evidence_url: { type: 'string' },
          quote: { type: 'string' },
        },
        required: ['claim', 'verdict'],
      },
    },
    contrary_evidence: { type: 'array', items: { type: 'object', properties: { what: { type: 'string' }, url: { type: 'string' } }, required: ['what'] } },
    corrected_verdict: { type: 'string' },
    verdict_changed_from_scout: { type: 'boolean' },
    details: { type: 'object', properties: {
      free_tier_limits: { type: 'string' },
      websocket_or_live_included_free: { type: 'string' },
      terms_of_use: { type: 'string' },
      betting_use_or_automated_use_allowed: { type: 'string' },
      us_sports_and_props_coverage: { type: 'string' },
      sharp_books_or_exchanges_covered: { type: 'string' },
      duplicates_what_the_app_already_has: { type: 'string' },
      signup_friction: { type: 'string' },
      android_phone_feasibility: { type: 'string' },
    } },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
  },
  required: ['lens', 'claim_checks', 'corrected_verdict', 'verdict_changed_from_scout', 'confidence'],
}

const SYNTH_SCHEMA = {
  type: 'object',
  properties: {
    per_source: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          n: { type: 'number' }, slug: { type: 'string' }, url: { type: 'string' },
          what_it_is: { type: 'string' },
          one_line_verdict: { type: 'string' },
          rapid_odds: { type: 'string' }, rapid_scores: { type: 'string' }, cheap_or_free: { type: 'string' }, usable_in_vigilant: { type: 'string' },
          why: { type: 'string' },
          corrections_by_skeptics: { type: 'string' },
          evidence_urls: { type: 'array', items: { type: 'string' } },
          confidence: { type: 'string' },
        },
        required: ['n', 'slug', 'one_line_verdict', 'rapid_odds', 'rapid_scores', 'cheap_or_free', 'usable_in_vigilant', 'why'],
      },
    },
    ranked_worth_testing_or_building: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          rank: { type: 'number' }, what: { type: 'string' }, from_sources: { type: 'array', items: { type: 'string' } },
          cost: { type: 'string' }, terms_risk: { type: 'string' }, expected_edge_over_what_the_app_has: { type: 'string' },
          how_to_test_on_the_phone: { type: 'string' }, what_claude_would_build: { type: 'string' },
        },
        required: ['rank', 'what', 'cost', 'terms_risk', 'how_to_test_on_the_phone'],
      },
    },
    patterns_that_do_not_work: { type: 'array', items: { type: 'string' } },
    new_sources_surfaced: { type: 'array', items: { type: 'object', properties: { name: { type: 'string' }, url: { type: 'string' }, why: { type: 'string' } }, required: ['name'] } },
    answer_bullets_for_tj: { type: 'array', items: { type: 'string' } },
    research_section_markdown: { type: 'string' },
    open_questions: { type: 'array', items: { type: 'string' } },
  },
  required: ['per_source', 'ranked_worth_testing_or_building', 'answer_bullets_for_tj', 'research_section_markdown'],
}

const CRITIC_SCHEMA = {
  type: 'object',
  properties: {
    verdict_on_completeness: { type: 'string' },
    gaps: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' }, what_is_missing: { type: 'string' }, why_it_matters: { type: 'string' },
          task_prompt: { type: 'string' }, affects_sources: { type: 'array', items: { type: 'string' } },
          priority: { type: 'string', enum: ['high', 'medium', 'low'] },
        },
        required: ['id', 'what_is_missing', 'task_prompt', 'priority'],
      },
    },
    claims_still_unverified: { type: 'array', items: { type: 'string' } },
    overclaims_in_synthesis: { type: 'array', items: { type: 'string' } },
  },
  required: ['verdict_on_completeness', 'gaps'],
}

const GAP_SCHEMA = {
  type: 'object',
  properties: {
    gap_id: { type: 'string' },
    finding: { type: 'string' },
    evidence: { type: 'array', items: CLAIM },
    changes_verdict_for: { type: 'array', items: { type: 'string' } },
    could_not_resolve: { type: 'string' },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
  },
  required: ['gap_id', 'finding', 'confidence'],
}

// ---------------------------------------------------------------------------------------------------------------------
phase('Scout')
log(`Ten sources: scout, then two skeptics each. ${skip.size ? skip.size + ' agents already finished in an earlier run are skipped (their results are files).' : 'Fresh run.'}`)

const perSource = await pipeline(
  SOURCES,
  // stage 1: the scout
  (src) => run(
    `scout:${src.n}-${src.slug}`,
    `${CONTEXT}

YOUR SOURCE (number ${src.n}): ${src.url}
${src.hint}

DO THIS, IN ORDER:
1. Read the source page itself (WebFetch, or curl through Bash). If it is blocked or paywalled, say so in could_not_read and use search snippets, the author's other pages or mirrors, clearly marked as second-hand.
2. Follow what it points to: the API/vendor it recommends or uses, its documentation, pricing and free-tier page, terms of use, status page, changelog, GitHub repo, and the code samples (read the code: poll intervals, sockets, headers, timestamps in the response).
3. Trace the UPSTREAM: where do these odds or scores really come from (a licensed feed, a scraper, a reseller of another vendor, the sportsbook's own socket)? Say how you know.
4. Find the age of what it relays. Prefer (a) a timestamp or age field in the vendor's documented response with its meaning, (b) a public endpoint you may probe politely (GET, no key) to compare its payload time with the Date header, (c) third-party measurements, complaints, GitHub issues, Reddit/HN threads. Marketing speed claims go in claimed_latency_or_refresh only; put what is verified in measured_or_verified_age (say "none" if nothing).
5. Coverage for Vigilant: US sports (NFL, NBA, MLB, NHL, college, tennis), player props, which books (sharp ones: Pinnacle, Circa, Kalshi, ProphetX, Sporttrade, Novig) and whether LIVE (in-play) odds and live SCORES are included.
6. Price and free tier: exact numbers from the pricing page with the date you read it (requests per month/minute, whether sockets/live/props are in the free plan, cheapest paid plan).
7. Terms: automated use, redistribution, use for betting, scraping clauses (quote them). If the source is a scraping tutorial, say what scraping its target would break.
8. Verdict, honestly: is it a rapid source of odds or scores that can be built or used, free or cheap? Compare with the known sources in the context (do not re-research them). If it is only a tutorial for a technique, say which technique and whether it applies to a phone app reading public feeds.
9. What should Tj's phone test (he can run things your container cannot: the phone has no proxy)? Be specific and cheap.

Return the structured result. Do not pad: every field must be something you actually found or an honest "not found".`,
    { phase: 'Scout', schema: SCOUT_SCHEMA },
  ),
  // stage 2: two independent skeptics, started the moment this source's scout finishes
  (scout, src) => {
    if (!scout) return null
    const dossier = asText(scout, 'the scout dossier')
    return parallel([
      () => run(
        `verify-claims:${src.slug}`,
        `${CONTEXT}

You are the CLAIMS-AND-LATENCY SKEPTIC for source ${src.n} (${src.url}). A scout produced the dossier below. Your job is to REFUTE it: default to "overstated" or "unsupported" unless you personally confirm a claim on a primary page.

DOSSIER:
${dossier}

DO THIS:
1. Re-read the cited primary pages yourself (WebFetch or curl the URLs in the dossier and the vendor's docs/pricing). For every key claim about latency, refresh rate, "real-time", push vs poll, upstream source, coverage and price, check that the primary page says it, that the scout quoted it correctly, and what it actually means (a "real-time" claim is a marketing claim unless the page defines an age).
2. Hunt for contrary evidence: GitHub issues, Reddit/HN/Trustpilot/Discord complaints, status pages, independent latency tests, changelogs that changed a plan, signs the site or API is dead or abandoned (dates!).
3. Check the upstream: is it really what the scout says? If the vendor resells another vendor, name it. If it scrapes a consumer site, then its lag is that site's plus its own.
4. Decide a corrected verdict for "is this a rapid odds or scores source for Vigilant, free or cheap?" and say whether it changed from the scout's.
Use lens = "claims-and-latency". Fill claim_checks for every claim you examined (at least the five most important).`,
        { phase: 'Verify', schema: SKEPTIC_SCHEMA },
      ),
      () => run(
        `verify-fit:${src.slug}`,
        `${CONTEXT}

You are the FIT-TERMS-AND-COST SKEPTIC for source ${src.n} (${src.url}). A scout produced the dossier below. Your job is to find every reason this CANNOT become a free or cheap part of Vigilant, and then say honestly what survives.

DOSSIER:
${dossier}

DO THIS:
1. Terms of use / API terms / robots.txt / licence (for a repo): read them and quote the clauses on automated access, scraping, redistribution, and use for placing bets or commercial use. Say what each route would break.
2. Free tier and real limits: exact requests per month/minute/day, whether WebSockets, live odds, live scores, player props and the US books Vigilant cares about are in the FREE or under-50-dollar plan, or only on a higher tier. Sign-up friction (card required? email? approval?) from the pages, without signing up.
3. Coverage: US sports and props; sharp books or exchanges (Pinnacle, Circa, Kalshi, ProphetX, Sporttrade, Novig); in-play or pregame only.
4. Duplication: does it give anything the app does not already have from the known sources in the context (ParlayAPI, PropLine, PinnWire, Kalshi, Polymarket sockets, Sofascore, The Odds API ...)? If it only resells one of those, say so.
5. Android feasibility: could Vigilant (Kotlin on a phone, background service) read it directly (REST/WebSocket/SSE) or would it need a server? Rate, battery, key storage, and whether the phone's network avoids the block this container sees.
6. Corrected verdict (usable_in_vigilant: yes-as-is / yes-with-work / technique-only / no) and whether it changed from the scout's.
Use lens = "fit-terms-and-cost". Fill details completely; use "not found" where true.`,
        { phase: 'Verify', schema: SKEPTIC_SCHEMA },
      ),
    ]).then(([claims, fit]) => ({ src, scout, claims, fit }))
  },
)

const dossiers = perSource.filter(Boolean)
log(`${dossiers.length} of ${SOURCES.length} sources made it through scout and skeptics`)
const missing = SOURCES.filter(s => !dossiers.some(d => d.src.n === s.n)).map(s => s.slug)
if (missing.length) log(`NOT COVERED (scout failed or was skipped): ${missing.join(', ')}`)

// ---------------------------------------------------------------------------------------------------------------------
phase('Synthesize')
const packed = JSON.stringify(dossiers.map(d => ({ n: d.src.n, slug: d.src.slug, url: d.src.url, scout: d.scout, skeptic_claims_and_latency: d.claims, skeptic_fit_terms_cost: d.fit })))
const PACKED_NOTE = 'Any object below with "__cached": true was finished in an earlier session: its full result is the JSON file named in its "file" field (a "result" field inside). READ each such file with the Read tool before you use it.'

const SYNTH_RULES = `
Write for Tj, who owns the app and decides any key, plan or build: short, plain, honest; no hype. Where the skeptics corrected the scout, use the correction and say so. A vendor's "real-time", "instant" or "100 ms" is a CLAIM, never a measurement; do not rank on it. The bar is: a SCORE feed that shows the score >= 3 s before Novig's price moves (earlier measured: Sofascore's REST poll, median lead 15-27 s on tennis), or an ODDS feed with a timestamp that proves the price is fresher than CNO's 13-33 s cadence plus lag. For each of the ten sources give one honest verdict line. Then rank what is actually worth testing on Tj's phone or building into Vigilant (free or cheap only), including patterns taken from tutorials (for example reading a public socket directly on the phone), with cost, terms risk, the edge over what the app already has, and a cheap test. Say plainly when the answer is "nothing here beats what the app already has". research_section_markdown is the body of a new section "## 115. Rapid odds and scores: the ten sources Tj sent (2026-10-07; DO1-DO3; Tj: \\"Research each of the following sources to see if a rapid source of odds or scores can be built or if any of the apis can be used for rapid odds or scores either cheap or free\\")" for RESEARCH.md: start with an "In short" list, then a table (source, what it is, upstream, push/poll, verified age, price, verdict), then per-source notes with evidence URLs and the date read, then what to build or test and what is not worth it, then what could not be verified. answer_bullets_for_tj: at most 14 short bullets (one per source, then the one or two things worth doing).`

const synth = await run(
  'synth',
  `${CONTEXT}

You are the SYNTHESIZER. Below are, for each source, the scout's dossier and two skeptics' checks. Produce the ranked, honest answer.

${SYNTH_RULES}

SOURCES NOT COVERED (scouting failed): ${missing.length ? missing.join(', ') : 'none'}

${PACKED_NOTE}

DOSSIERS (JSON):
${packed}`,
  { phase: 'Synthesize', schema: SYNTH_SCHEMA },
)

// ---------------------------------------------------------------------------------------------------------------------
phase('Critic')
const critic0 = await run(
  'critic',
  `${CONTEXT}

You are the COMPLETENESS CRITIC. Below are the ten dossiers (scout plus two skeptics each) and the synthesizer's answer. Find what is missing or overclaimed: sources the scouts could not read (paywall, 403, proxy) and whether a cheap alternative route exists; upstream vendors named but never examined; claims the synthesis states as fact that only one source (or only a vendor) supports; a free-tier limit or latency number nobody verified; an obvious public endpoint that was never probed (politely, GET, no key); whether any recommendation would actually beat the bar in the context or merely repeats a known source. Return at most 8 gaps, each with a self-contained task_prompt another agent can run read-only under the same hard rules, ordered by priority. Return an empty gaps list only if you truly find nothing.

${PACKED_NOTE}

SYNTHESIS (JSON):
${asText(synth, 'the synthesis')}

DOSSIERS (JSON):
${packed}`,
  { phase: 'Critic', schema: CRITIC_SCHEMA },
)
const critic = await load(critic0, CRITIC_SCHEMA, 'critic')

// ---------------------------------------------------------------------------------------------------------------------
phase('Gaps')
const allGaps = (critic && critic.gaps) || []
const gaps = allGaps.filter(g => g.priority !== 'low').slice(0, 8)
if (allGaps.length > gaps.length) log(`${allGaps.length - gaps.length} low-priority gaps not run (listed in the critic result)`)
const gapResults = gaps.length
  ? (await parallel(gaps.map((g, i) => () => run(
      `gap:${i + 1}-${String(g.id).slice(0, 30)}`,
      `${CONTEXT}

You are a GAP-FILLING agent. The completeness critic found this gap in the research on Tj's ten sources:

GAP ${g.id}: ${g.what_is_missing}
WHY IT MATTERS: ${g.why_it_matters || ''}
AFFECTS: ${(g.affects_sources || []).join(', ')}

YOUR TASK:
${g.task_prompt}

Answer only this gap, with evidence tagged VERIFIED / CLAIM_ONLY / INFERRED / CONTRADICTED and URLs. If you cannot resolve it from here, say exactly why in could_not_resolve and what the phone or Tj would have to do.`,
      { phase: 'Gaps', schema: GAP_SCHEMA },
    )))).filter(Boolean)
  : []

// ---------------------------------------------------------------------------------------------------------------------
phase('Final')
const final = await run(
  'final',
  `${CONTEXT}

You are the FINAL SYNTHESIZER. You already have the first synthesis, the critic's findings and the results of the gap-filling agents. Produce the FINAL answer: correct anything the critic showed to be overclaimed, integrate every gap result that changes a verdict (name the gap), keep what survived, and say what is still unverified.

${SYNTH_RULES}

Also add to open_questions every claim that remains unverified and the cheap phone-side tests that would settle it.

${PACKED_NOTE}

FIRST SYNTHESIS (JSON):
${asText(synth, 'the first synthesis')}

CRITIC (JSON):
${JSON.stringify(critic)}

GAP RESULTS (JSON):
${JSON.stringify(gapResults)}

DOSSIERS (JSON, for reference):
${packed}`,
  { phase: 'Final', schema: SYNTH_SCHEMA },
)

return { covered: dossiers.map(d => d.src.slug), not_covered: missing, gaps_run: gaps.length, final: isCached(final) ? 'already finished: see results/final.json' : final }
