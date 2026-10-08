export const meta = {
  name: 'audit-pinnodds-live',
  description: 'Adversarially audit the Pinnodds live betting path (real money tonight) from ten lenses, then challenge every finding',
  phases: [
    { title: 'Audit', detail: 'ten independent lenses read the code and the real data' },
    { title: 'Verify', detail: 'skeptics try to refute every medium+ finding' },
    { title: 'Synthesize', detail: 'one prioritized, deduplicated fix plan' },
  ],
}

const CONTEXT = [
  'CONTEXT. Vigilant is a Kotlin/Compose Android app at /home/user/novig (public repo, v0.76.1). Its new feature "Pinnodds live" compares Pinnacle\'s live price (devigged) from the Pinnodds WebSocket with Novig\'s live order books and, when a switch is on, buys on Novig with ONE immediate-or-cancel order. Tj will run it with REAL MONEY tonight. His requirement: it must only bet truly positive-EV prices, and must REFUSE negative-EV bets, stale prices, wrong-side bets and anything outside its limits.',
  '',
  'PIPELINE (read the real code, never trust comments or docs): PinnSocket (OkHttp websocket) -> PinnLiveRunner.onFrame -> PinnBook.apply (state, versions, devig) -> PinnLiveRunner.rematch/LiveMatcher (Pinnacle matchup <-> Novig game <-> markets/outcomes) -> PinnLiveRunner.evaluate -> LiveEdge.judge (the rule) -> PinnLiveTrader.attempt (gate, caps, size, one IOC order via NovigTradingClient.placeOrder, fills, Tracker, journal). App wiring: app/src/main/kotlin/com/tjshea/vigilant/app/VigilantApp.kt (search "pinn"), PinnText.kt, ui/SettingsScreen.kt (PinnLiveSection). Math: engine/src/main/kotlin/com/tjshea/vigilant/engine/{EvMath,Fees,Devig,Odds}.kt. Novig side: data/src/main/kotlin/com/tjshea/vigilant/data/novig/{NovigModels.kt,stream/NovigStream.kt,stream/StreamBooks.kt,trading/NovigTradingClient.kt}. Tests: data/src/test/kotlin/com/tjshea/vigilant/data/pinnodds/. Docs: PINNODDS_API.md, NOVIG_API.md (sections 5-8, 17, 18, 21), RESEARCH.md section 116. Main code dir: data/src/main/kotlin/com/tjshea/vigilant/data/pinnodds/ (PinnBook, LiveEdge, LiveMatcher, PinnLiveRunner, PinnLiveTrader, PinnSocket, PinnKeyTest, PinnReport).',
  '',
  'RULES. READ-ONLY: never modify, create or delete any file in /home/user/novig (write scratch files only under your own scratchpad directory (the path is in your environment info), in a folder named audit_<yourlens>/). NEVER open the Pinnodds websocket and NEVER call any pinnodds.com API or use the Pinnodds key (the account allows ONE websocket and Tj\'s phone may be connected). Novig PUBLIC REST (https://api.novig.com/v3/public/...) is allowed at no more than 3 requests/second total; no signed Novig calls. Do NOT run gradle or any Kotlin build (agents running it at once would corrupt each other); use python3 -I for analysis, grep/sed/Read for code. Web docs may be fetched with curl/WebFetch.',
  '',
  'REAL DATA you may analyze (python3 -I). The raw tapes are saved in the repo, compressed: /home/user/novig/research/pinnodds_2026-10-08/tape1_raw_frames.ndjson.xz, tape2_raw_frames.ndjson.xz and pool.ndjson.gz (decompress COPIES into your own scratch folder, never into the repo; python: lzma.open / gzip.open). Once decompressed they are called: tape1.ndjson and tape2.ndjson = raw Pinnodds websocket frames ({"t": local ms, "m": frame}) with Pinnacle live records for all sports; pool.ndjson = pooled study tape (k=pin Pinnacle market changes with fair/fairp, k=nov Novig book reads {bb: {outcome name: [best bid, qty]}}, k=mkt Novig market descriptions {mt, strike, desc, outs}, k=match Pinnacle<->Novig game matches {pid, nid, desc (Novig "Away @ Home"), ph, pa (Pinnacle home/away names)}, k=score). tools/research/pinn_novig_lag.py is the study tool. data/src/test/resources/pinnodds-frames.jsonl = real frames used by tests.',
  '',
  'WHAT TO PRODUCE. Findings only where you can show a concrete failure: exact inputs -> wrong money outcome (a bet on the wrong side, a negative-EV or stale bet that passes every check, a limit bypassed, a duplicate order, an unconfirmed order treated as done, a crash/hang that leaves orders or state inconsistent). Give file and line, the scenario with numbers, the evidence you gathered (code you read, data you ran), and a minimal fix. Severity: critical = can lose real money on a bet that LOOKS +EV to the app (wrong side/market, stale or mismatched price, fee/price math error); high = bypasses a cap or places duplicates or sends when it should not; medium = weakens a safeguard; low = nit. Also list what you checked and found sound. Be exhaustive within your lens, and be honest: if the code is right, say so and say how you know.',
].join('\n')

const FINDINGS = {
  type: 'object',
  properties: {
    lens: { type: 'string' },
    summary: { type: 'string' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string' },
          severity: { type: 'string', enum: ['critical', 'high', 'medium', 'low'] },
          file: { type: 'string' },
          line: { type: 'number' },
          scenario: { type: 'string' },
          evidence: { type: 'string' },
          fix: { type: 'string' },
        },
        required: ['title', 'severity', 'file', 'scenario', 'evidence', 'fix'],
      },
    },
    checked_ok: { type: 'array', items: { type: 'string' } },
  },
  required: ['lens', 'summary', 'findings', 'checked_ok'],
}

const VERDICT = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['confirmed', 'refuted', 'partly'] },
    reasoning: { type: 'string' },
    corrected_severity: { type: 'string', enum: ['critical', 'high', 'medium', 'low', 'none'] },
    better_fix: { type: 'string' },
  },
  required: ['verdict', 'reasoning', 'corrected_severity'],
}

const LENSES = [
  {
    key: 'wrong-side',
    prompt: 'LENS: WRONG SIDE / WRONG MARKET. The worst failure is buying the opposite side or a different market than the one Pinnacle priced. Audit LiveMatcher (event matching incl. swapped home/away, doubleheaders, same-name teams, college abbreviations like KENN/JVST/NMSU, tennis initials, MLS/EPL clubs), money() outcome mapping via TeamMatcher.labelIsAway on abbreviations, spread() (strike sign, the home handicap convention, names that end in the handicap), total(), LiveTarget.line() (swapped sign), and runner.candidate() selection text. VALIDATE AGAINST REAL DATA: from pool.ndjson take every k=match and k=mkt record (Novig outcome names, strikes, descriptions) and the Pinnacle fair series, and check that the mapping the Kotlin code would produce (re-implement it faithfully in python, quoting the Kotlin lines you mirror) is consistent with prices: for each matched game compare Novig best-bid-implied mid for each outcome with Pinnacle fair for the side the code assigns it; a mapping error shows as the swapped assignment fitting far better. Also fetch Novig public live/pregame events and markets now (3 req/s max) for NBA, NHL, MLB, NCAAF, WNBA, ATP, WTA, MLS, EPL and test the Kotlin TeamMatcher logic (read TeamMatcher.kt) on the real outcome labels vs the real event description. Report any case where a label could map to the wrong team or be unmappable-but-guessed.',
  },
  {
    key: 'ev-fee-math',
    prompt: 'LENS: EV, FEE AND PRICE MATH. Verify, line by line, that a bet the app takes really has EV >= its minimum AFTER Novig\'s fee at the price it can actually fill. Audit engine EvMath.quote/positiveDepth, Fees.takerFee (coefficient*p*(1-p), per $1 payout, WHEN_LIVE vs ALWAYS; what about DELAYED events, and the fee object on each market), LiveEdge.judge (best ask vs depth walk, limitPrice = worst level, whether the EV at the limit price still meets the minimum INCLUDING the fee at that price), the grid of Novig prices (0.001-0.050 step 0.001, 0.055-0.945 step 0.005, 0.950-0.999 step 0.001: is every take level and limit price on the grid? is 1 - bid always on the grid?), PinnLiveTrader.size (contracts = floor(stake / (limit+fee)*...) units: a contract pays 1 cent), the order price string (NovigTradingClient.priceText), IOC partial fills at a worse level than judged, expected fee at fill vs fee assumed, and the Tracker record (logApi: price, cost, fee, EV). Check Devig WORST_CASE semantics (per-side minimum of four methods; does not sum to 1): is using it as the fair for ONE side conservative or can it ever overstate fair for a side? Test numerically with python for 2-way and 3-way lines across prices -1500..+1500 including heavy favourites. Look for off-by-one, unit and rounding errors that let a negative-EV order through.',
  },
  {
    key: 'stale-pinnacle',
    prompt: 'LENS: STALE PINNACLE DATA. Find every way a bet could rest on a Pinnacle price that is no longer true. Audit PinnBook (versions, ld/dz/both/pre channels, status!=open closes, period closes, op del, prune after 30 min, snapshot handling, score and clock), PinnSocket reconnects (what happens to PinnBook after a disconnect/reconnect: does it keep old lines open while the feed was gone? events that ended or were pulled during the gap never get a close), the runner\'s feedProblem (45 s quiet limit vs the 30 s heartbeat), the case where a game ended/was suspended/was abandoned, tennis retirements, MLB rain delays, lines that stop updating while the matchup stays alive, the STANDING trigger (price steady 30 s: how stale can that be?), the SCORE and MOVE triggers (is the 20 s window measured from Pinnacle\'s change or from local receipt; clock skew; frames delayed in the proxy), and whether judge() uses nowMs consistently. Use the raw tapes to find real examples: games that ended or went quiet and what frames (if any) closed their markets; whether lines of finished games stay open in PinnBook terms. Propose concrete staleness guards with thresholds justified by the data.',
  },
  {
    key: 'stale-novig',
    prompt: 'LENS: STALE / WRONG NOVIG BOOK. Audit how the runner reads Novig: PushedBooks.live(), NovigStream (subscribe throttle, bulk subscribe when the bucket is full, 2,048 market cap, SUBSCRIPTION_LIMIT_EXCEEDED, idle close, retry after failure, geolocation close, SLOW_CONSUMER), StreamBooks (snapshot, deltas, seq gaps, stale flag, CLEAR), the BookListener threading (delta applied on the socket thread, Msg.Book queued: is the book the consumer reads ever older than a price it just judged?), takeLadder semantics (taker price = 1 - best opposing bid, qty), DELAYED events (book cleared on reopen), GOLIVE voiding resting orders and the book clear, markets that closed/settled mid-bet, the runner\'s use of catalog markets (isOpen at catalog time vs now: a market that closed since the 30 s catalog read; status in the stream\'s lifecycle channel), the fee object, and what happens when watch() is never satisfied (book null -> STALE_BOOK skip: confirm no path bets without a pushed book). Find any way an old or partial book could be judged as current, and whether the order could be sent to a market that is closed/locked/voided.',
  },
  {
    key: 'trader-safety',
    prompt: 'LENS: ORDER EXECUTION SAFETY. Audit PinnLiveTrader and its wiring in VigilantApp (pinnOrders, pinnTradeRules, pinnGate, pinnLossToday, onHalt, lock). Look for: double orders (offer() from tick and book change racing; lastOffer/lastTry/lastMove maps; the mutex and the app-wide orderLock tryLock semantics), orders sent while paused/killed/halted, a halt that does not stick (settings written a moment later: HALT_GRACE_MS), unconfirmed answers (placeOrder throws after the exchange accepted it: is the order found by clientId? the trader halts, but does the Tracker learn of the fill?), partial fills and the Tracker, fills that show up late, cap accounting (maxPerGame by event id, maxPerDay from the journal vs real spending from Novig, paper records excluded?), day boundary (localMidnight), stake clamps (MIN 1 / MAX 25 / apiMaxStake), wallet gate (wallet*1.2), the daily loss limit (settled bets only: how stale?), 451/423 handling, ORDER_TOO_SMALL, journal write failure, a process kill between place and journal, scope cancellation (withContext(NonCancellable)), and every path by which PAPER mode could send an order (prove it cannot, by tracing call sites of placeOrder). Also the Tracker side: BetTarget fields, logApi dedupe by orderId, AtBet.',
  },
  {
    key: 'market-equivalence',
    prompt: 'LENS: DO PINNACLE AND NOVIG MEAN THE SAME BET? A price comparison is only valid if both markets settle identically. For each sport the app covers (NBA/WNBA/NCAAB basketball, NFL/NCAAF, MLB/KBO/NPB baseball, NHL hockey, ATP/WTA tennis, UFC/Boxing, MLS/EPL and other soccer spreads/totals, esports) work out what Pinnacle\'s period-0 ("Game"/"Match") moneyline, spread and total settle on (overtime, extra innings, shootouts, regulation only, retirements/walkovers, minimum games/sets, listed pitchers, push/void rules, abandoned games) versus what Novig\'s MONEY, SPREAD and TOTAL markets settle on (read docs.novig.com market rules pages via https://docs.novig.com/llms.txt and the voids field: PUSH vs FMV; NOVIG_API.md). Fetch what you can with curl. Identify every sport/market where they differ so that a "fair price" from Pinnacle would not be the right fair price for Novig\'s contract (e.g. hockey regulation vs OT-inclusive, soccer, tennis retirement, whole-number lines that can push, Novig FMV voids). State which are safe and which the app should exclude, and check what LiveMatcher currently excludes (whole numbers, draw lines, period != 0, units != Regular).',
  },
  {
    key: 'pinnbook-parsing',
    prompt: 'LENS: PARSING THE REAL FEED. Audit PinnBook.apply/applyRec/applyMarket against the docs (PINNODDS_API.md, https://pinnodds.com/llms-full.txt) and, above all, the REAL frames in tape1.ndjson/tape2.ndjson/pool.ndjson: every sport id 1-13, every market type, designations, points on spreads and totals (is the home handicap really on the home price? sign conventions for away), alternates, team totals, period numbers (tennis sets, quarters, halves vs period 0), prices outside +-100 (impliedProbability null), 3-way lines, missing version fields, op add/upd merge semantics (does an upd with a SUBSET of markets or participants wipe or corrupt state?), parent vs child matchups (names, score, startTime, isLive), `pre` frames for matchups that are also live, snapshot chunks, ping handling in PinnSocket (text.length<120 && contains "ping"), huge frames, JSON edge cases (numbers as strings, nulls), and the history ring (moveOver semantics at boundaries, first snapshot as baseline, vig changes moving the devigged fair: a margin change with no real move). Write python that replays the real tapes through a faithful port of the parsing rules and look for lines whose parsed fair differs from a straightforward independent calculation, lines with impossible values, and sides mislabeled. Report every divergence.',
  },
  {
    key: 'wiring-and-gates',
    prompt: 'LENS: SETTINGS, DEFAULTS AND THE REAL-MONEY GATE. Audit that real orders can only happen when Tj deliberately allowed them: ScanSettings defaults (pinnLive false, pinnLiveBet false), JSON migration/`migrate()` (could an old or restored settings file flip a switch?), the confirmation dialog, Resume semantics, STOP ALL / KillSwitch (does it stop PinnLiveRunner, the socket, LiveFeedService and any pending trader coroutine?), pause, the order lock shared with auto-bet/bids/burst trader (can this trader and the burst trader or bid desk trade the same market at once; does the wash check cover resting bids on the SAME outcome?), per-game exposure versus the other bettors (GameExposure ignores live bets?), apiMaxStake/apiMaxPerDay interplay, wallet reads, the service (LiveFeedService, manifest), process restart behaviour (what starts after boot or app update; can the runner start trading before settings load?), Diagnostics/log redaction of the Pinnodds key (the NetInterceptor, eventLog, Diagnostics file, Share files, the key store JSON and its backup/export), and the Settings UI (can a typed value bypass chip limits: TypedDollarField min/max; negative stake; 0 caps meaning no limit?). Read PinnLiveTrader.size for caps equal to 0 and for NaN/Infinity. Report anything that lets money move unexpectedly.',
  },
  {
    key: 'statistics',
    prompt: 'LENS: IS THE EDGE REAL, AND ARE THE DEFAULTS SAFE FOR REAL MONEY? Re-examine RESEARCH.md section 116 and tools/research/pinn_novig_lag.py (simulate) critically against research/pinnodds_2026-10-08/*.txt and pool.ndjson: selection effects (triggers overlap within a game: effective sample size), the judging yardstick (EV against Pinnacle\'s own fair 30 s/120 s later is CLV-style, not outcome-based), whether Python\'s power devig vs the app\'s WORST_CASE changes which bets fire, survivorship (finished games), the +100% "standing" artifacts, whether the score-driven result (+6.2% at 5%/3pts, n=20) is statistically distinguishable from zero (compute a bootstrap by game/cluster from the tape), how many bets per hour/day the default rule would place, expected profit per $2 bet and per day at those stakes with a realistic hit rate, variance and a sensible bankroll/stake and loss-limit for tonight, and what other safeguards the data suggests (e.g. cap EV at an implausible level: look at the distribution of EV at decision for triggers that later collapsed; Novig/Pinnacle disagreement magnitude beyond which a mapping or stale error is likelier than an edge; a minimum depth; time-of-game effects; sport-specific behaviour). Give a concrete recommended set of thresholds (min EV, max EV, min move, max disagreement, stake, caps) with the evidence, and say honestly how confident anyone can be.',
  },
  {
    key: 'pregame-design',
    prompt: 'LENS: PREGAME (NON-LIVE) PINNACLE STEAM vs STALE NOVIG QUOTES. Tj wants the app to also catch pregame opportunities: Pinnacle moves a prematch line (news, sharp money) on the Pinnodds socket before Novig\'s makers react, so one side of Novig is +EV; Novig charges NO taker fee before the game starts. Today the engine has rules.pregame (default off) but its triggers were designed for live (SCORE needs a score; MOVE window 20 s). Read PinnLiveRunner (discover with OPEN_PREGAME and PREGAME_HORIZON_MS, eligibleNow, sameStage), PinnBook (pre frames: isLive=false, startMs in future, status pending; how the first sighting of a line is the baseline; a parent matchup whose markets are stale prematch ones while the live child exists), LiveEdge (PREGAME skip, fee), LiveMatcher, and the raw tapes (tape1/tape2 contain thousands of `pre` frames: analyze them) to answer with data: how often and how big do Pinnacle prematch fair prices move, how long do moves persist (reversion), what do `pre` frames contain (full markets or changed ones, versions, limits, how close to start), what is the right trigger and its thresholds (move size, window, confirmation time, hold time, time-to-start bounds), what staleness/safety guards are needed (Pinnacle prematch limits are low early: use maxRisk; line near start; postponed games; lineup/pitcher changes; markets that Pinnacle pulls), how Novig pregame books behave (use the public API now to read a few pregame books and compare to Pinnacle fair from the tape for matched games), and which markets are comparable (period 0 only, half-point lines). Deliver a concrete implementable spec (rules, thresholds, defaults, safeguards) plus the list of code changes in the existing files, and any bug that would make today\'s pregame path unsafe if switched on.',
  },
]


// ---- RESUME SUPPORT (Tj, 2026-10-08: usage may run out at any second; a new session on any account must be able to continue) ----------------------------------------------
// args = {skip: [labels already finished]}: tools/research/pinnodds_audit_resume.py prints it. A skipped label is NOT run again: a cheap 'load' agent reads its saved result
// (research/pinnodds_audit_2026-10-08/results/<label>.json, written by tools/research/save_workflow.py) and hands it on. Do not use resumeFromRunId across sessions.
const OUT = '/home/user/novig/research/pinnodds_audit_2026-10-08'
const safe = (l) => l.replace(/[^A-Za-z0-9._-]+/g, '_')
const skip = new Set((args && args.skip) || [])
async function run(label, prompt, schema, phaseTitle) {
  if (skip.has(label)) {
    const loaded = await agent('Read the file ' + OUT + '/results/' + safe(label) + '.json (JSON: {label, agentId, run, result}). Return its "result" field EXACTLY as your answer, changing nothing.', { label: 'load:' + label, phase: phaseTitle, schema })
    if (loaded) return loaded
    log('could not load the saved result of ' + label + ': running it again')
  }
  const hint = '\n\nRESUME NOTE: if the file ' + OUT + '/partial/' + safe(label) + '.md exists, an earlier run was cut off while doing this same job; read it first (it shows what had been read and said) and continue from there instead of starting over.'
  return agent(prompt + hint, schema ? { label, phase: phaseTitle, schema } : { label, phase: phaseTitle })
}

phase('Audit')
const results = await pipeline(
  LENSES,
  (lens) => run('audit:' + lens.key, CONTEXT + '\n\n' + lens.prompt, FINDINGS, 'Audit'),
  async (audit, lens) => {
    if (!audit) return { lens: lens.key, summary: 'agent failed', findings: [], checked_ok: [], verified: [] }
    const risky = (audit.findings || []).filter(f => f.severity !== 'low')
    log(lens.key + ': ' + (audit.findings || []).length + ' findings (' + risky.length + ' medium+)')
    const verified = await parallel(risky.map((f, i) => async () => {
      const lenses = ['REPRODUCE: re-run or re-derive the failure yourself from the code and data; try to make it NOT happen', 'ALREADY-PREVENTED: look for an existing guard (in the code paths, settings or tests) that makes this impossible or harmless in practice', 'IMPACT: judge the real-money impact tonight (Tj bets $1-5 per order, caps $5/game, $25/day) and whether the proposed fix is correct and minimal']
      const n = f.severity === 'medium' ? 2 : 3
      const votes = await parallel(lenses.slice(0, n).map((l, k) => () => run('verify:' + lens.key + ':' + i + ':' + k,
        CONTEXT + '\n\nYou are a SKEPTIC. A reviewer (lens ' + lens.key + ') claims this finding. Try to REFUTE it; default to refuted only if you can show why it cannot happen, otherwise confirm it. Your angle - ' + l + '.\n\nFINDING: ' + f.title + '\nSeverity claimed: ' + f.severity + '\nFile: ' + f.file + (f.line ? ':' + f.line : '') + '\nScenario: ' + f.scenario + '\nEvidence: ' + f.evidence + '\nProposed fix: ' + f.fix,
        VERDICT, 'Verify')))
      const vs = votes.filter(Boolean)
      const confirmed = vs.filter(v => v.verdict !== 'refuted').length
      return { finding: f, votes: vs, survives: vs.length > 0 && confirmed * 2 > vs.length }
    }))
    return { lens: lens.key, summary: audit.summary, findings: audit.findings, checked_ok: audit.checked_ok, verified: verified.filter(Boolean) }
  },
)

phase('Synthesize')
const done = results.filter(Boolean)
const survivors = done.flatMap(r => (r.verified || []).filter(v => v.survives).map(v => ({ lens: r.lens, ...v.finding, votes: v.votes.map(x => x.verdict + '/' + x.corrected_severity + ': ' + x.reasoning.slice(0, 400)), better_fix: v.votes.map(x => x.better_fix).filter(Boolean) })))
const lows = done.flatMap(r => (r.findings || []).filter(f => f.severity === 'low').map(f => ({ lens: r.lens, ...f })))
log('surviving medium+ findings: ' + survivors.length + '; low: ' + lows.length)
const plan = await agent(
  CONTEXT + '\n\nYou are the synthesizer. Below are the VERIFIED findings (medium or higher, confirmed by skeptics), the low findings, each lens summary, and what each lens found sound. Produce ONE deduplicated, prioritized implementation plan for the engineers: group duplicates, order by real-money risk (critical first), give for each item the exact files/functions to change, the precise rule or threshold to implement (with the data-backed numbers the lenses gave), and the test that proves it. Separately give: (1) the recommended safe defaults for tonight, (2) the full spec for the pregame mode, (3) anything that should make Tj NOT turn real bets on tonight. Be concrete and complete.\n\nSURVIVING FINDINGS:\n' + JSON.stringify(survivors, null, 1) + '\n\nLOW:\n' + JSON.stringify(lows, null, 1) + '\n\nLENS SUMMARIES AND CHECKED-OK:\n' + JSON.stringify(done.map(r => ({ lens: r.lens, summary: r.summary, checked_ok: r.checked_ok })), null, 1),
  { label: 'synthesize', phase: 'Synthesize' },
)
return { plan, survivors, lows, lenses: done.map(r => ({ lens: r.lens, summary: r.summary, n: (r.findings || []).length, checked_ok: r.checked_ok, refuted: (r.verified || []).filter(v => !v.survives).map(v => ({ title: v.finding.title, severity: v.finding.severity, votes: v.votes.map(x => x.verdict + ': ' + x.reasoning.slice(0, 300)) })) })) }
