export const meta = {
  name: 'v0701-files-analysis',
  description: 'Diagnose the v0.70.1 diagnostics file and analyze the v0.70.1 scan study (8-step READ ME task): parallel analysts, strategy builders, adversarial verification, synthesis',
  phases: [
    { title: 'Diagnose', detail: '5 agents over the diagnostics file' },
    { title: 'Explore', detail: '9 analysts over the scan study' },
    { title: 'Strategies', detail: '3 strategy builders from different angles' },
    { title: 'Verify', detail: '3 skeptics per candidate rule' },
    { title: 'Synthesize', detail: 'ranked report + completeness critic' },
  ],
}

// args.sp = the out_dir given to extract.py (it holds study/ and lib/); the default is the original session's scratchpad.
const SP = (args && args.sp) || '/tmp/claude-0/-home-user-novig/e7298d8a-0231-53a8-924e-b6a6ddb983d4/scratchpad'
const STUDY = SP + '/study/'
const LIB = SP + '/lib'
const WORK = SP + '/work/'

const PREAMBLE = `
CONTEXT. Vigilant is Tj's Android app (Kotlin; repo /home/user/novig, modules engine/data/app) that finds +EV bets on the Novig sportsbook and auto-bets/bids with real money. At 12:08 AM EDT on Oct 6 2026 Tj sent two files from his phone (app v0.70.1, installed at 12:02 AM; the previous file was from v0.68.0 three hours earlier): a DIAGNOSTICS file and a SCAN STUDY file (a log of every +EV bet the two scanners found, graded and given closing lines). He sent no message. Your job is one slice of a thorough analysis. Be exhaustive on YOUR slice and put numbers in every claim.

HARD RULES.
- The repo is READ-ONLY for you: read and grep it freely (to point at code), but never edit, commit, push, stash or run anything that changes it. Write scratch files only under your own directory ${WORK}<your label>/ (create it). Do not write into the data directory ${STUDY}.
- Run Python as plain 'python3 script.py' from your own work directory (pandas 3.0, numpy, scipy are installed). Parse the data with code; do not read JSON lines by eye.
- Never recommend loosening a safety limit (auto-bet daily limit, price tolerance, pregame only, trap guard, halt on a lost order, the sharp check). Every change to a rule that decides which bets are placed is a QUESTION FOR TJ, not an action: say it as a proposal with the evidence and the number of bets that would settle it.
- Statistics honesty: the effective sample is GAMES, not bets (same-game bets are correlated): use the game-clustered CIs of the shared loader. Split by DATE, never at random. The study logged only about 2.26 days of first-looks (Oct 3 17:56 ET to Oct 6 00:05 ET; game days Oct 3-12, with some bets listed a week before their game) while the app's version and presets changed (0.57 to 0.70.1): so 'out of sample' here means the second half of ~2 days. Say 'the data suggests', say how likely a result is to be luck, and say what number of bets/games would settle it. Do not oversell a result that holds on one half only.
- Return RAW DATA in your structured result (no chatty prose): claims with numbers, so a later stage can verify them.
`

const STUDY_TOOLS = `
STUDY DATA (read-only, already extracted from Tj's file): ${STUDY}study_bets.jsonl (2080 bets, one JSON object per line), study_readme_dictionary.txt (the file's READ ME and DATA DICTIONARY: read it FIRST, it defines every field and the 8-step task), study_summary_splits.txt (the app's own SUMMARY and SPLITS added up on the phone, with CIs: use as a cross-check), study_whatif.txt (the props-need-a-sharp-book simulation), study_bids_summary.txt, study_filled_bids.jsonl (17), study_unfilled_bids.jsonl (300).
SHARED LOADER AND STATS (use them so every analyst defines numbers the same way; read ${LIB}/load.py first): in your script start with
    import sys; sys.path.insert(0, '${LIB}')
    from load import load, looks, clv_stats, roi_stats, by, split_by_date, rule_report
    df = load()    # one row per bet, flat columns (the docstring lists them); looks(df) explodes the per-look arrays 's'
CLV = closeFair/cost - 1 at the first-listed price (positive = beat the close). ROI is at the first-listed price, 1 unit a bet. 'shown' = the app's CNO list carried it at first look; the rest were found only by the wide read. close_ok drops Novig-trades closes of under 3 trades (a price, not a close): report CLV on all closes AND on close_ok when it matters. A CLV group needs ~200+ bets with a close before it says much.
`

const DIAG_TOOLS = `
DIAGNOSTICS DATA (read-only): ${STUDY}diag_text_no_bets.txt (the whole diagnostics file except the per-bet JSON lines: READ ME, ranked findings, since-previous-report, health checks, settings, scans, API usage, runway, background, tracker, accuracy splits, bids, memory, phone, how the app last ended, recent problems, CONNECTIONS, API ISSUES, PERFORMANCE, COUNTERS, EVENT TIMELINE, APP LOG, storage, code map), ${STUDY}diag_every_bet.jsonl (694 of Tj's own tracked bets: the bet, atBet = its record as placed, its close and result). Grep section headings ('== ') to navigate. The app's code is in /home/user/novig (grep the host/feature names the file points at) so each finding names the code that owns it.
`

const FINDINGS_SCHEMA = {
  type: 'object',
  properties: {
    area: { type: 'string' },
    summary: { type: 'string', description: 'three sentences: what is fine, what is wrong, what matters most' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          kind: { type: 'string', enum: ['BUG', 'FAILURE', 'OPTIMIZE', 'IMPROVE', 'WATCH', 'FINE'] },
          title: { type: 'string' },
          evidence: { type: 'string', description: 'numbers and the section/line they come from' },
          appFault: { type: 'boolean', description: 'true only when the app code is at fault (not the phone, network, a provider, or normal behaviour)' },
          confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
          codePointers: { type: 'array', items: { type: 'string' } },
          suggestedAction: { type: 'string' },
        },
        required: ['kind', 'title', 'evidence', 'appFault', 'confidence'],
      },
    },
  },
  required: ['area', 'summary', 'findings'],
}

const STUDY_SCHEMA = {
  type: 'object',
  properties: {
    area: { type: 'string' },
    trust_notes: { type: 'string', description: 'what you can and cannot trust in this slice, and the biases that matter' },
    key_numbers: { type: 'array', items: { type: 'string' }, description: 'the numbers that matter, each a sentence with n bets, n games, estimate and CI' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          claim: { type: 'string' },
          evidence: { type: 'string' },
          n_bets: { type: 'number' },
          n_games: { type: 'number' },
          effect: { type: 'string', description: 'the estimate with its 95% game-clustered CI' },
          holds_on_both_date_halves: { type: 'string', enum: ['yes', 'no', 'unknown', 'too-thin'] },
          luck_risk: { type: 'string', description: 'how likely this is luck / multiple-comparisons, in words and a number if you can' },
        },
        required: ['claim', 'evidence', 'n_bets', 'holds_on_both_date_halves'],
      },
    },
    candidate_rules: {
      type: 'array',
      description: 'rules that pick bets (and the price/moment to take them), each a precise pandas boolean expression over the loader df',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          expr: { type: 'string', description: "boolean pandas expression over df from load(), e.g. \"df.shown & (df.kind=='PROP') & (df.cnoBooks>=6)\"" },
          rationale: { type: 'string' },
          bets: { type: 'number' }, games: { type: 'number' },
          clv: { type: 'number' }, clv_lo: { type: 'number' }, clv_hi: { type: 'number' },
          roi: { type: 'number' },
          first_half_clv: { type: 'number' }, second_half_clv: { type: 'number' },
          per_day: { type: 'number' },
        },
        required: ['name', 'expr', 'rationale'],
      },
    },
    app_changes_implied: { type: 'array', items: { type: 'string' }, description: 'exact settings/code that would implement what you found (file + setting), each phrased as a PROPOSAL for Tj' },
    next_logging: { type: 'array', items: { type: 'string' }, description: 'what the log should record next time to settle what the data could not' },
  },
  required: ['area', 'trust_notes', 'key_numbers', 'findings'],
}

const DIAG_DIMS = [
  {
    label: 'diag-network-performance',
    prompt: `YOUR SLICE: network and performance. Read: findings #1-#8 and #17 in WHAT TO DO, 'SINCE THE PREVIOUS REPORT' (perf.cycle.p95ms 2111 -> 5712, frames, net p95s), the CONNECTIONS, API ISSUES, PERFORMANCE and COUNTERS sections, the Background auto-scan block (2 cycles started late, worst 7 min), and the EVENT TIMELINE. Questions: (1) cloudflare-dns.com and dns.google fail 100% (and 'name lookups failed 635 times'): is that the phone having no route at certain minutes (compare the failure times in the timeline with the 'Retry-After'/451 events) or something in the app; the DoH fallback only runs when system DNS failed, so is the evidence consistent? (2) api.novig.com asked the app to slow down 2941 times (Retry-After: 1) with 4480 calls today: over what period is 2941 (counters since when?), which endpoints and callers, is the app exceeding the key's 16/s or the public routes' limit (read data/.../novig/NovigPublicClient.kt, RateGate.kt, signing/NovigSignedClient.kt, stream/NovigStream.kt) or are these mostly while the key route stood down, and is it harmful (retries wasted? bids not cancellable?) or already handled? (3) cycle p95 2111 -> 5712 ms: real regression, the app update restart, or a one-off (compare the performance block's run window)? (4) parlay-api.com slowness (738 ms to first byte, props 9860 ms average) and 51 KB/s bodies: avoidable by reading earlier/in parallel, or the provider? Does it slow the scan's 'fair odds 30 s' on the last scan? (5) background auto-scan every 15 s CNO + Vigilant: is it keeping up, why did 2 cycles start late, was the screen on? For every finding say whether the app is at fault, and name the code. Include FINE findings for things that are normal.`,
  },
  {
    label: 'diag-sources-credits',
    prompt: `YOUR SLICE: fair-odds sources, matching and API credits. Read the finding 'Source ParlayAPI 1st half: answered 5 leagues but matched no Novig game', the 'Last Vigilant scan' block (per-source fetched / matched), 'API usage', 'Runway' (ParlayAPI 3,363 of 20,000 credits used, 'last goes Oct 31 before the Nov 1 reset: SHORT'; The Odds API 950 of 3,500 over 7 keys, 'last goes Oct 20: SHORT'), 'Last rounds (what they cost each API)', and the Settings (fair-odds sources on, keys saved). Questions: (1) Is 'ParlayAPI 1st half matched no Novig game' a real bug (grep data/match/TeamMatcher.kt, PlayerNames.kt, reference/*ParlayAPI* and how 'matched' is counted for the 1st-half source) or benign (no Novig 1st-half market at that moment, or those markets are attached to games the full-game source already matched)? Does the app waste credits on a source that cannot match? (2) Are the Runway projections accurate or artifacts of the estimator (how many days of the month are the 3,363 spread over? is the pace estimate from a heavy day? find the Runway code and read its method), and what does the pace mean in practice: scans per day and credits per scan on each paid source, whether the 15-second background scan spends credits every cycle or re-uses answers, and what the cheapest way to make ParlayAPI last to Nov 1 is WITHOUT lowering the freshness limits the app uses for betting? (3) Anything in the scan that skews the results: the last scan priced 998 lines with 518 by live feed and 480 through the key; 27 +EV; 97 games past 'days ahead 1'. (4) Any provider returning errors, empty answers or odd matches. Name code for every finding; mark appFault honestly.`,
  },
  {
    label: 'diag-tracker-accuracy',
    prompt: `YOUR SLICE: Tj's own bets and how accurate the edges are. Read the Tracker, 'Accuracy by scanner and by market', 'Each scanner by market', 'The bet as placed' (all its splits), 'Bets by what made their fair odds', 'Pinnacle only', "Vigilant's own bets against the close (newest 30)", 'Open bets', the findings 'Early bets (CLV)', "Vigilant's edges (CLV): the EV it shows runs 2.6 points above what the close says", 'Grading: 2 bets started over 6 h ago still open', and the 694 bets in diag_every_bet.jsonl (parse with code). Questions: (1) CLV, beat-the-close share, results and ROI overall and split by scanner (CNO vs Vigilant), market kind, sport, the sharp veto, time to start, book count, preset and app version: where is the edge real and where is it not (n of bets WITH a close per group; the file warns about outliers and the 24 h split)? (2) The 'Early bets' finding: 238 bets placed more than 24 h before the start lost to the close (CLV -0.9%) and 16 were placed that early in the last 3 days: who placed those 16 (auto-bet, by hand, bid fills), under which preset/version, and does that mean the trap guard failed (auto-bet is supposed to stay out of games over 24 h away; check Settings 'games within 24 h' and when that setting was applied) or are they Tj's hand bets / old bids? (3) Vigilant's own edge runs 2.6 points above the close (CLV +0.9% on 54 bets vs EV +3.5% at placing): by market and by what made the fair, which is the weak spot? (4) AUDIT THE AUTO-BET: for each bet with the auto-bet's atBet/how markers, check it obeyed the rules printed in Settings (odds -200 to +130, only player props/moneylines/spreads, at least 3 books agreeing and 2 pricing both sides, edge >= 2.5% at placing, the sharp veto under 1%, within 24 h, stake caps $5/bet, $500/day, $70/game): list any violation as a BUG with the bet ids. (5) The 2 open bets that 'need a tap' (both sides of one market held): is the app's handling right? Name code; mark appFault honestly.`,
  },
  {
    label: 'diag-bids-autobet',
    prompt: `YOUR SLICE: the bids (make orders) and the auto-bet's behaviour, and whether the v0.68.1 fix for 'bids all cancel together' worked. Read Settings 'Make orders / Bids' (posted 3128, filled 17, rested 3 min at the median, 'last 24 h: 2201 posted, 119.8 bid-hours up (longest 9 min)', the cancel reasons: ~879 'About to expire: re-posted', ~854 'fair goes old within a minute: re-priced at the next scan', 97 'Auto-make switched off', 80 'Cancelled by you'), the fills-by-... splits, the newest fills, the finding 'Bids: most fills came within 2 minutes of posting', 'Auto-bet: running ... skipped: 2 it starts in more than 24 h', and ${STUDY}study_bids_summary.txt + study_filled_bids.jsonl + study_unfilled_bids.jsonl for the same bids from the study's side. Questions: (1) Is the bid desk behaving as designed: focus 'Quick & likely to win' (not Low API usage) with ttl up to 10 min, re-posting before expiry (REFRESH_BEFORE_MS) and re-pricing when the fair goes old; is there any gap (bid-hours up vs wall time), churn that wastes the 'place' token budget or the rate limit (the 2941 slow-downs), or cancellations that left money exposed (STOP ALL at some time, 'Auto-make switched off x138')? (2) Fill rate 17/3128 (0.5%) against the research's expectation (§70.3: ~19.2 expected in 24 h; the file says 'in line with the research'): is that good or poor, and what would raise fills without loosening any limit? (3) Fill quality: CLV +2.6% (16 closes, 75% beat), results -0.10 on 32.18 staked, picked-off 0%: any pattern in the 17 (n is tiny: say so). (4) What the unfilled bids' fields say about why they did not fill (price distance to Novig's price, kind, time to start, fair age). (5) Anything in the auto-bet (0 looked at, 0 met criteria, 'skipped 2 it starts in more than 24 h') that suggests it is blind or stuck: check the 'Last Vigilant scan' and Background auto-scan blocks and Settings. Name code for each finding (data/.../novig/trading/maker/*, app/MakerRunner.kt, app/AutoBet*.kt); mark appFault honestly.`,
  },
  {
    label: 'diag-lifecycle-errors',
    prompt: `YOUR SLICE: stability, errors, lifecycle and what the file does NOT show about v0.69-v0.70 features. Read 'Recent problems', 'How the app last ended', 'APP LOG', 'Memory', 'Phone' (battery, thermal, doze, standby, data saver), 'STORAGE', the whole EVENT TIMELINE (every warning and error of the last day, then the newest events), COUNTERS, the CODE MAP and MACHINE-READABLE blocks, and the READ ME's rules. Questions: (1) Is there any exception, crash, ANR, memory kill, stuck cycle, repeated error or silent failure that is the APP's fault? Group the timeline's warnings/errors by cause and say which are explained (carrier 451, no route) and which are not. (2) The app was updated to 0.70.1 at 12:02:59 AM and this file is made at 12:08 AM: what in the file is an artifact of the restart (late cycles, empty caches, 'since previous report' deltas, cycle p95)? (3) v0.69.0 added a live burst recorder (no orders) and v0.70.x a gated real-money burst trader: the file has no section for them. Read app/src/main/kotlin/com/tjshea/vigilant/app/BurstText.kt (diagnostics()) and Diagnostics.kt: confirm the block is omitted when the recorder was never switched on and nothing was recorded, say whether the Settings block of the file states the recorder/trader state anywhere (grep 'burst' in the file), and propose the exact one-line additions (IMPROVE) that make the NEXT file say 'burst recorder off / on, trader locked because ...' even when off. (4) Does the diagnostics' own bookkeeping have any wrong or misleading numbers (e.g. 'ParlayAPI runway', 'late cycles', 'bids ended x1245' counts vs the 24 h numbers, health checks that say OK but the numbers disagree)? Report only things you verified in the file and code. Mark appFault honestly.`,
  },
]

const EXPLORE_DIMS = [
  {
    label: 'study-data-quality',
    prompt: `YOUR SLICE: step 1 of the READ ME's task: CHECK THE DATA FIRST. Counts by status (WON/LOST/PENDING/VOID) and by close source; the bets with no close and why (closeNote), and WHO is missing a close (by kind, league, src, shown/hidden, minToStart): is the closes subset a biased sample of the 2080? Duplicates (same event+market+selection under different ids, or one bet counted twice across listers), odd values (cost vs american, ev vs fair/cost: does ev = fair/cost - 1 within rounding, the 'MISMATCH' screen, ev over 20%, absurd odds, negative or huge 'available', clv extremes), the study's time coverage (firstSeen range, game days, app versions and presets by day: which rules were in force when), the 'Novig last trades (N)' closes by N (1-2 trades is a price, not a close), whether CLV differs by close source for the same kind of bet (pinnacle vs espn vs tracker vs novig_trades) and whether any source is systematically more generous, whether 'profit' agrees with status and the first-listed price (recompute it), VOID/FMV handling, futures. Say exactly what you can and cannot trust, which robustness filter the others should apply (e.g. close_ok), and how much each issue moves the headline numbers (headline: 2080 bets, CLV +0.19% on 683 closes, ROI +1.58%). Also cross-check the loader's numbers against study_summary_splits.txt (they must match) and report any mismatch as a BUG in the study's own arithmetic.`,
  },
  {
    label: 'study-overall-edge',
    prompt: `YOUR SLICE: step 2 of the READ ME's task: overall edge. The share of bets that beat the close, the average CLV and ROI, and how both CHANGE WITH SAMPLE SIZE (cumulative by first-look time and by game: does CLV converge or drift; at what n would the CI exclude zero); the EV the lists showed against the CLV the close says (is the edge real? EV listed +1.22% overall, +2.31% shown, +0.54% hidden vs CLV +0.19%, +0.57%, -0.30%): calibration of listed EV against realized CLV in EV bands, and against realized ROI; the shrinkage factor between listed EV and CLV; the same for 'Vigilant's fair' vs CNO's fair (src v vs c); CLV by close source (pinnacle/espn/tracker/novig_trades) and on close_ok; ROI vs CLV consistency (do results confirm CLV or contradict it, with the right uncertainty: at 1-2% edge results are noise); outliers (EV over +-6%): effect of removing them; whether CLV is positive on both date halves. Put everything in numbers with game-clustered CIs.`,
  },
  {
    label: 'study-splits-bet-attributes',
    prompt: `YOUR SLICE: step 3 of the READ ME's task, the BET attributes, one at a time and then in pairs: kind/market (PROP, SPREAD, TOTAL, MONEYLINE, PERIOD, TEAM_TOTAL, OTHER; the market string, e.g. receiving yards vs receptions), league and sport, odds bands (american and cost bands: favourites vs longshots), EV bands (listed ev), books behind the fair (cnoBooks), booksTwoSided, booksAgreeing and agreeShare (null when no page was read: compare inside the judged set only), sharpVerdict (PASSED/VETOED/NO_SHARP), 'available' dollars (liquidity: does CLV hold where there is money to bet?). For each: bets, bets with a close, games, CLV with game-clustered CI, beat%, ROI, listed EV, and whether the sign holds on BOTH date halves; use by() and split_by_date(). Then the best 2-way and 3-way combinations (e.g. kind x league, kind x booksAgreeing, odds band x cnoBooks), counting how many you tried (multiple comparisons). List the groups with CLV clearly positive AND holding on both halves AND >= ~150 bets with a close, and the groups that are clearly negative (traps). Report candidate_rules as precise pandas expressions (shown vs all noted) with rule_report numbers. Compare your numbers with study_summary_splits.txt where the phone already printed the same split.`,
  },
  {
    label: 'study-splits-process-attributes',
    prompt: `YOUR SLICE: step 3 of the READ ME's task, the PROCESS attributes, one at a time then in pairs: who listed it (src: c, v, w and their combinations; lister_c/v/w), the screen reason (shown vs each hidden reason: that is Tj's open question about hidden bets), how long before the start it was first listed (minToStartFirst bands: <2 h, 2-6 h, 6-24 h, 1-3 days, over 3 days), how long it stayed listed (listedMin) and whether a scan dropped it (gone), the hour of day (Eastern) and day, whether the price got better or worse after the first look (bestAmerican/lastAmerican vs american: compute the price drift; clvBest and clvLast vs clv), placedByTj, the app version and preset in force (ab_version, ab_preset), the freshness of CNO's list (ab_cnoListAgeSec) and of the fair (ab_fairAgeSec), cnoOneWay. For each: bets, bets with a close, games, CLV with game-clustered CI, beat%, ROI, and whether it holds on both date halves. Single out TRAPS (groups clearly negative) and gifts (groups clearly positive on both halves and >= ~150 bets with a close). Count your comparisons (multiple-comparisons risk). Report candidate_rules as precise pandas expressions with rule_report numbers. Pay particular attention to minToStartFirst: the app's trap guard now keeps auto-bet, alerts and bids to games within 24 h; check what the data says about that threshold (is 24 h right, or 12 h, 6 h?).`,
  },
  {
    label: 'study-timing-looks',
    prompt: `YOUR SLICE: step 4 of the READ ME's task: TIMING from the looks (the 's' arrays: [minutesBeforeStart, kind, american, ev, fair, books, dollars, agreeing, companiesBothSides, checkEv, sharpVerdict]; use looks(df)). Questions: WHEN in a bet's life is the price best, and when is the close most often beaten? For the bets with a close, compute the CLV you would have got at each look (implied probability of that look's american price against closeFair) and aggregate by minutes-before-start bucket (e.g. >24 h, 12-24, 6-12, 3-6, 1-3 h, 30-60, <30 min) with game-clustered CIs: is CLV monotone in time to start (do prices shorten as the game nears: the classic 'early price is stale/poor' or the opposite)? Compare taking the FIRST listed price, the BEST price ever listed, the LAST listed price, and a rule like 'take at the first look at least X minutes before the start' or 'wait until Y minutes before the start if still listed at >= the first price'. Does a bet that stays listed a long time (listedMin, number of looks) tend to be a TRAP (the line moved against it) or a GIFT (nobody wanted it)? Separate by whether the EV rose or fell across the looks. What does 'dollars' available do across looks (liquidity decay)? What would 'bet immediately when first seen' vs 'wait for a better price' have made? Report candidate_rules (including timing rules expressed over df columns where possible; for rules that need the looks, give the exact pandas code in the rationale and your own rule_report-style numbers) and say how reliable each timing result is given only ~2 days of first-looks.`,
  },
  {
    label: 'study-traps',
    prompt: `YOUR SLICE: step 5 of the READ ME's task: TRAPS. Bets that look +EV on paper and lose to the close (the other side was a sharp's). Define a trap as clv_ok < -3% (also try < -2% and < -5%) and a non-trap as clv_ok >= 0 among bets with a close_ok. Find the signs that separate them: a sharp book dissenting (sharpVerdict VETOED, ab_sharpEv, ab_dissent, ab_agreeing / booksAgreeing / agreeShare, ab_checkEv), a falling EV across the looks (looks()), few books (cnoBooks), a long price, how long it stayed listed, one-way devig (ab_cnoOneWay), the fair's source (vig vs cno: the 'vig' and 'cno' fields), market kind (props at which stat), time to start, a large listed EV (too good: EV bands), Novig's own movement vs the fair (ab_novigAgeSec), the stale-fair signs (ab_fairAgeSec, ab_cnoListAgeSec). Fit SIMPLE rules (a few conditions) on the first date half, test on the second (precision/recall of 'trap' and the CLV of what the rule keeps vs drops, game-clustered CIs); also a depth-2/3 decision tree (scikit-learn may be unavailable: write it by hand or use a greedy search) to see which single signs carry the most information. Report: the traps' common signs ranked by how well they separate, with numbers on both halves; the share of bets each trap-filter would drop; and candidate_rules phrased as FILTERS (keep = not trap-like) with rule_report numbers. State plainly if the data cannot separate traps beyond what the app's veto already does.`,
  },
  {
    label: 'study-props-sharp-book',
    prompt: `YOUR SLICE: step 7 of the READ ME's task, TJ'S OPEN QUESTION (2026-10-03): should a prop bet need a sharp prop book to agree it is +EV before the app lists or bets it? Read study_whatif.txt (the app's own simulation of each rule over the logged props: kept, dropped, not judged, with CLV by closeVia, ROI, games not bets, split by date) and the PROPS splits inside study_summary_splits.txt, then answer it from the data yourself: among props (kind == 'PROP'), which bets have a sharp verdict (sharpVerdict PASSED / VETOED / NO_SHARP; ab_sharpBook = Kalshi, ProphetX, FanDuel, Caesars, DraftKings ...; ab_sharpEv = its own edge at Novig's price): CLV, ROI, games and bets per verdict; does requiring PASSED raise CLV enough to pay for the bets it drops; which book's agreement matters (exchanges Kalshi/ProphetX vs the originating books); would a higher edge floor for props with no sharp book (NO_SHARP) be the better rule (sweep the EV floor with CIs); how many bets a day each choice costs; and how the selection biases it (only judged props have a verdict: the top of each CNO scan, the green check reads the best ~10 every 4 min: compare judged vs not-judged props on observables like EV, books, kind, minToStart, available, and say what the not-judged ones would change). Hold the answer to both date halves. Deliver: a clear recommendation (or 'too thin: need N more props with a close') with the numbers, as a PROPOSAL for Tj, the exact setting/code it would touch (data/scanner/SharpVeto.kt, Presets.kt, the auto-bet rules), and candidate_rules with rule_report numbers.`,
  },
  {
    label: 'study-hidden-and-filters',
    prompt: `YOUR SLICE: THE QUESTION TJ ASKED OF THE HIDDEN ONES (README, top): the bets his filters hide ('screen' is set) are the ones he never sees. Compare hidden with shown (SUMMARY: shown 803 bets CLV +0.57% ROI -0.08%; hidden 1277 bets CLV -0.30% ROI +2.70%): do any hidden kinds beat the close or profit, and do his filters (EV floor, odds cap, book count, one-sided, complete book, row limit, TOO_GOOD over 20%, MISMATCH, NOT_A_GAME futures, NOT_LISTED) COST him edge or SAVE him from traps? For each screen reason (EV, BOOKS, ODDS, NOT_LISTED, MISMATCH, plus any others) report bets, closes, games, CLV with game-clustered CI, ROI, listed EV, the split by date halves, and by market kind inside each. Then the counterfactual per filter: if that one filter were relaxed (EV floor lowered to X, odds cap raised to Y, books count lowered to Z), what extra bets per day would appear and what would their CLV and ROI be (CLV is the leading indicator; hidden ROI is mostly noise: say how big the CIs are)? Which filter is worth the most to keep and which is the most expensive in lost edge, with numbers? The hidden group's CLV is 'mostly against the same closes': check whether hidden bets are the same bets as shown ones at other times (the same market first listed hidden and later shown). Phrase any relaxation as a PROPOSAL for Tj, never as something to loosen on safety grounds, and give candidate_rules with rule_report numbers.`,
  },
  {
    label: 'study-bids',
    prompt: `YOUR SLICE: the BIDS part of the scan study (Vigilant's make orders; last 14 days, 3128 posted; 17 filled): read study_bids_summary.txt, study_filled_bids.jsonl, study_unfilled_bids.jsonl (field list is in the section header inside study_readme_dictionary.txt / study_bids_summary.txt; parse with code). A bid is judged by CLV and results like any bet, but ALSO by how fast it was taken and by whether the fair on the next scan was still above the price they filled at (evAtFill): a fast fill is a symptom of a stale bid. Compute for filled bids: CLV, beat%, ROI, speed, evAtPost vs evAtFill, by focus (ALL / QUICK_LIKELY / LOW_USAGE), kind, league, price band, minutes to start at post, led-its-side, books behind the fair (fairBooks), fair age (fairAgeSec / fairNewestAgeSec). For the 300 newest unfilled: why they did not fill (distance to Novig's price in cents at the post, kind, time to start, how long they rested, fair age). Compare the bid fills' CLV with the TAKER bets in study_bets.jsonl of the same kinds (is making a better or a worse way to get the same edge, per dollar and per bet?), with n of 17 fills stated plainly (too thin for most splits: say what number of fills would settle each). Answer: are bids worth running at the current settings (3.5% under fair, quick-and-likely focus), which part of the desk's rules (margin, price window, time to start, kinds) the data suggests changing (as PROPOSALS), and what the next file should record about bids.`,
  },
]

const STRATEGY_ANGLES = [
  {
    label: 'strategy-simple-filters',
    prompt: `ANGLE: a FEW SIMPLE filters on the bets the app lists or could list (the READ ME's step 6: prefer a few simple rules that hold on both halves of the period over many fitted ones). Build candidate rules with one to three conditions from the fields the findings single out (kind/market, league, odds band, EV band, cnoBooks, agreement/sharp verdict, available dollars, minToStart, lister). For each rule report with rule_report: bets, games, CLV with game-clustered CI (all closes and close_ok), beat%, ROI, expected bets and games a day, average available dollars, and the first-half vs second-half CLV; plus the baseline it must beat (the same universe without the rule). Fit on the FIRST date half only, then evaluate on the second; count every rule and threshold you try (tried_count) and apply a multiple-comparisons haircut in your luck estimate (e.g. a permutation test: shuffle clv_ok across bets within game-day and see how often a rule this good appears among the same number of tries).`,
  },
  {
    label: 'strategy-timing-price',
    prompt: `ANGLE: WHEN and AT WHAT PRICE to take a bet (the READ ME's steps 4 and 6). Using looks(df) and the close, build rules about the moment and the price: e.g. take only at the first look inside N hours of the start; wait until M minutes before the start and take only if still listed at no worse than the first price; take only if the EV did not fall across the first K looks; skip bets that stayed listed longer than T minutes without the price improving; take the best listed price instead of the first (only valid if it could have been known: do not use hindsight: a rule may use only looks up to the moment of the decision). Report each as a rule with an exact pandas/python definition, rule_report-style numbers (bets, games, CLV with game-clustered CI vs the first-price baseline of the same bets, ROI, per day, available dollars), the first/second date halves, tried_count, and a luck estimate (permutation within game-day). Be strict about look-ahead bias and survivorship (a bet 'still listed at the start' is a different population from one that was dropped).`,
  },
  {
    label: 'strategy-trap-avoid-and-props',
    prompt: `ANGLE: TRAP AVOIDANCE and the props/sharp-book rule layered on the universe the app already bets (shown bets, within the app's own rules: edge >= 2.5%, 3+ books agreeing, odds -200..+130, props/moneylines/spreads, within 24 h of the start). Build rules that REMOVE the bets most likely to lose to the close (sharp dissent or veto bar, falling EV, few books, long price, over-listed, specific prop stats) and rules that RAISE the bar where the evidence is thin (a higher edge floor for props with no sharp book), and rank by CLV gained per bet dropped. For each: rule_report numbers (bets kept/dropped, games, CLV kept vs dropped with game-clustered CIs, ROI, per day, available dollars), the date halves, tried_count, and a luck estimate (permutation within game-day). Also report the cost of each rule in bets/day and dollars/day the auto-bet could still place at its caps ($5/bet, $500/day, $70/game).`,
  },
]

const RULE_SCHEMA = {
  type: 'object',
  properties: {
    angle: { type: 'string' },
    tried_count: { type: 'number', description: 'how many rules/thresholds/splits you tried in total (for the multiple-comparisons haircut)' },
    baseline: { type: 'string', description: 'the universe and its CLV/ROI the rules are measured against' },
    rules: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          expr: { type: 'string', description: 'exact boolean pandas expression over df = load() (or exact python for look-based rules)' },
          rationale: { type: 'string' },
          bets: { type: 'number' }, games: { type: 'number' }, per_day: { type: 'number' }, avg_available: { type: 'number' },
          clv: { type: 'number' }, clv_lo: { type: 'number' }, clv_hi: { type: 'number' },
          roi: { type: 'number' },
          baseline_clv: { type: 'number' },
          first_half_clv: { type: 'number' }, second_half_clv: { type: 'number' },
          luck_probability: { type: 'string' },
        },
        required: ['name', 'expr', 'rationale', 'bets', 'games', 'clv', 'clv_lo', 'clv_hi', 'first_half_clv', 'second_half_clv'],
      },
    },
    notes: { type: 'string' },
  },
  required: ['angle', 'tried_count', 'rules'],
}

const VERDICT_SCHEMA = {
  type: 'object',
  properties: {
    lens: { type: 'string' },
    rule: { type: 'string' },
    numbers_reproduced: { type: 'string', description: 'what you recomputed (bets, games, CLV with CI, halves) and whether it matches the builder' },
    survives: { type: 'boolean', description: 'true only if, from YOUR lens, the rule is a credible improvement worth putting to Tj' },
    reason: { type: 'string' },
    caveats: { type: 'array', items: { type: 'string' } },
  },
  required: ['lens', 'rule', 'survives', 'reason'],
}

// ---------------------------------------------------------------- run
const diagThunks = DIAG_DIMS.map(d => () => agent(PREAMBLE + DIAG_TOOLS + '\n' + d.prompt, { label: d.label, phase: 'Diagnose', schema: FINDINGS_SCHEMA }))
const exploreThunks = EXPLORE_DIMS.map(d => () => agent(PREAMBLE + STUDY_TOOLS + '\n' + d.prompt, { label: d.label, phase: 'Explore', schema: STUDY_SCHEMA }))

const [diag, explore] = await Promise.all([parallel(diagThunks), parallel(exploreThunks)])
const diagOk = diag.filter(Boolean)
const exploreOk = explore.filter(Boolean)
log(`diagnose: ${diagOk.length}/${DIAG_DIMS.length} agents returned; explore: ${exploreOk.length}/${EXPLORE_DIMS.length}`)

const exploreDigest = JSON.stringify(exploreOk.map(e => ({ area: e.area, trust: e.trust_notes, key: e.key_numbers, findings: (e.findings || []).slice(0, 14), rules: (e.candidate_rules || []).slice(0, 10) })))

phase('Strategies')
const builders = await parallel(STRATEGY_ANGLES.map(a => () => agent(
  PREAMBLE + STUDY_TOOLS + `\nYOU ARE A STRATEGY BUILDER (step 6 of the READ ME's task). Nine analysts have already explored the data; their findings, trust notes and candidate rules are below as JSON (they may contradict each other; re-check what matters yourself with the loader instead of trusting any number).\nANALYSTS' DIGEST:\n${exploreDigest.slice(0, 60000)}\n\n` + a.prompt + `\nReturn at most 8 rules, strongest first, each reproducible from its 'expr'.`,
  { label: a.label, phase: 'Strategies', schema: RULE_SCHEMA })))
const builtOk = builders.filter(Boolean)
const allRules = builtOk.flatMap(b => (b.rules || []).map(r => ({ ...r, angle: b.angle, tried_count: b.tried_count, baseline: b.baseline })))
log(`strategies: ${allRules.length} candidate rules from ${builtOk.length} builders (tried ${builtOk.map(b => b.tried_count).join(', ')} variants)`)

// dedupe by normalized expr, keep the strongest second-half CLV, cap the verification set
const seen = new Map()
for (const r of allRules) {
  const key = (r.expr || r.name).replace(/\s+/g, '')
  const prev = seen.get(key)
  if (!prev || (r.second_half_clv ?? -9) > (prev.second_half_clv ?? -9)) seen.set(key, r)
}
const candidates = [...seen.values()].sort((a, b) => ((b.clv_lo ?? -9) - (a.clv_lo ?? -9))).slice(0, 10)
if (seen.size > candidates.length) log(`verification cap: ${seen.size - candidates.length} weaker rules not verified (kept the 10 with the best CLV lower bound)`)

const LENSES = [
  { key: 'reproduce', text: 'REPRODUCE AND DATE-SPLIT: re-implement the rule from its expr yourself with the loader (do not reuse the builder\'s code), recompute bets, games, CLV with game-clustered CI (all closes and close_ok), ROI, and the first/second date-half CLVs; compare with the builder\'s numbers; look for look-ahead bias, a mask that silently drops NaN, a population that differs from the stated baseline, and whether the second half is large enough (games) to mean anything.' },
  { key: 'luck', text: 'LUCK AND MULTIPLE COMPARISONS: estimate how likely a rule this good is to come from chance given tried_count variants and the number of games: run a permutation test (shuffle clv_ok within game-day among the same-sized subsets, or randomly re-draw same-sized subsets of the same universe 2000 times) and report the p-value / percentile of the observed CLV, with and without a Bonferroni-style haircut for tried_count; check that the CI excludes zero on game-clustered resampling, and that the result is not driven by one or two games (leave-one-game-out, drop the top 3 games).' },
  { key: 'feasibility', text: 'APP FEASIBILITY AND SAFETY: could the app actually do this with its existing machinery (read data/scanner/Presets.kt, SharpVeto.kt, ScanSettings.kt, the auto-bet rules in app/AutoBet*.kt and data/.../trading) - which exact setting or code change; how many bets a day would it place at the caps ($5 a bet, $500 a day, $70 a game) given the \'available\' dollars; does it loosen ANY safety limit (then it fails); does it depend on information the app does not have at decision time (look-ahead, a close, a page that is read only for the top ~10 bets); is the close available for the kinds of bet it picks (a rule that selects bets with no close cannot be evaluated: say so)?' },
]

phase('Verify')
const verified = await parallel(candidates.map(r => () =>
  parallel(LENSES.map(l => () => agent(
    PREAMBLE + STUDY_TOOLS + `\nYOU ARE AN ADVERSARIAL VERIFIER. Try to REFUTE this candidate rule; default to survives=false if you are not convinced. Rule: ${r.name}\nexpr: ${r.expr}\nrationale: ${r.rationale}\nBuilder's numbers: bets ${r.bets}, games ${r.games}, CLV ${r.clv} [${r.clv_lo}, ${r.clv_hi}], ROI ${r.roi}, first half ${r.first_half_clv}, second half ${r.second_half_clv}, baseline CLV ${r.baseline_clv}, per day ${r.per_day}, tried_count ${r.tried_count}, luck ${r.luck_probability}.\nYOUR LENS: ${l.text}`,
    { label: `verify:${l.key}:${(r.name || '').slice(0, 28)}`, phase: 'Verify', schema: VERDICT_SCHEMA }))).then(vs => ({ rule: r, verdicts: vs.filter(Boolean) }))))
const verifiedOk = verified.filter(Boolean).map(v => ({ ...v, survivesCount: v.verdicts.filter(x => x.survives).length }))
log(`verification: ${verifiedOk.filter(v => v.survivesCount >= 2).length}/${verifiedOk.length} rules survive 2 of 3 lenses`)

phase('Synthesize')
const bundle = {
  diagnose: diagOk,
  explore: exploreOk,
  builders: builtOk.map(b => ({ angle: b.angle, tried_count: b.tried_count, baseline: b.baseline, notes: b.notes })),
  verified: verifiedOk.map(v => ({ rule: v.rule, survivesCount: v.survivesCount, verdicts: v.verdicts })),
}
const synth = await agent(
  PREAMBLE + STUDY_TOOLS + DIAG_TOOLS + `\nYOU ARE THE SYNTHESIZER. Below is everything the other agents found (5 diagnosis slices, 9 study analysts, 3 strategy builders, 3 verifiers per rule). Re-check the 5 numbers that matter most with the loader yourself, resolve contradictions, and write the FINAL REPORT for Tj as a structured object: (1) diagnose: what is wrong (app's fault, ranked, each with the code and the fix), what is not the app's fault, what is fine; (2) the study: what can be trusted, the headline edge (is it real), the ranked list of strategies worth trying with evidence (only rules that survived verification, with their luck and thin-sample caveats), traps, the answer to the props-need-a-sharp-book question, the answer to the hidden-bets question, timing, bids; (3) exact app settings/code changes as PROPOSALS for Tj (file + setting + the number of bets that would settle it), clearly separating what you would change without asking (an app bug) from what needs Tj's decision (a rule that decides which bets are placed); (4) what the next files should record. Be concrete and short in each field; numbers everywhere; say 'the data suggests'.\nBUNDLE:\n${JSON.stringify(bundle).slice(0, 180000)}`,
  {
    label: 'synthesis', phase: 'Synthesize', schema: {
      type: 'object',
      properties: {
        headline: { type: 'string' },
        app_bugs: { type: 'array', items: { type: 'object', properties: { title: { type: 'string' }, evidence: { type: 'string' }, code: { type: 'string' }, fix: { type: 'string' } }, required: ['title', 'evidence', 'fix'] } },
        not_app_fault: { type: 'array', items: { type: 'string' } },
        fine: { type: 'array', items: { type: 'string' } },
        study_trust: { type: 'string' },
        edge_is_real: { type: 'string' },
        strategies: { type: 'array', items: { type: 'object', properties: { rank: { type: 'number' }, name: { type: 'string' }, definition: { type: 'string' }, evidence: { type: 'string' }, luck_and_caveats: { type: 'string' }, setting_to_change: { type: 'string' }, bets_per_day: { type: 'string' } }, required: ['rank', 'name', 'definition', 'evidence', 'setting_to_change'] } },
        traps: { type: 'array', items: { type: 'string' } },
        props_sharp_book_answer: { type: 'string' },
        hidden_bets_answer: { type: 'string' },
        timing_answer: { type: 'string' },
        bids_answer: { type: 'string' },
        proposals_for_tj: { type: 'array', items: { type: 'object', properties: { question: { type: 'string' }, recommendation: { type: 'string' }, evidence: { type: 'string' }, settles_with: { type: 'string' } }, required: ['question', 'recommendation'] } },
        next_logging: { type: 'array', items: { type: 'string' } },
      },
      required: ['headline', 'app_bugs', 'strategies', 'proposals_for_tj'],
    },
  })

const critic = await agent(
  PREAMBLE + `\nYOU ARE THE COMPLETENESS CRITIC. Below is the synthesizer's final report and the READ ME's 8-step task (${STUDY}study_readme_dictionary.txt) plus the diagnostics READ ME (${STUDY}diag_text_no_bets.txt, top). Say what is MISSING or UNVERIFIED: a step of the task not answered, a claim without numbers, a contradiction between slices, a number that is not reproducible from the loader (recompute the 3 most load-bearing ones), a place the report oversells given only ~2.26 days of first-looks, an app bug that should be double-checked in the code, and any question Tj asked that is unanswered. Return concrete follow-ups, most important first.\nREPORT:\n${JSON.stringify(synth).slice(0, 60000)}`,
  {
    label: 'critic', phase: 'Synthesize', schema: {
      type: 'object',
      properties: { missing: { type: 'array', items: { type: 'string' } }, overstated: { type: 'array', items: { type: 'string' } }, recomputed: { type: 'array', items: { type: 'string' } }, follow_ups: { type: 'array', items: { type: 'string' } } },
      required: ['missing', 'overstated', 'follow_ups'],
    },
  })

return { synth, critic, verified: verifiedOk.map(v => ({ name: v.rule.name, expr: v.rule.expr, clv: v.rule.clv, lo: v.rule.clv_lo, hi: v.rule.clv_hi, h1: v.rule.first_half_clv, h2: v.rule.second_half_clv, per_day: v.rule.per_day, survives: v.survivesCount, verdicts: v.verdicts.map(x => ({ lens: x.lens, survives: x.survives, reason: x.reason })) })), diagnose: diagOk, explore: exploreOk }
