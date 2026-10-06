#!/usr/bin/env python3
"""Phase 4 prompts for the v0.70.1 analysis: three section writers, the synthesis, the completeness critic and the final revision.

    python3 -I tools/research/study_v0701/genphase4.py <v0701 dir>
    python3 -I tools/research/study_v0701/genphase4.py --selftest

Reads  <v0701 dir>/prompts/_preamble.txt (written by genprompts.js: the data paths, the loader and the hard rules every analyst gets)
Writes <v0701 dir>/prompts/<label>.txt for the labels in plan.py's PHASE4: synth-diagnose, synth-study, synth-strategies (any order, three at a
time), then synthesis (needs the three), critic (needs synthesis), final (needs critic). Each agent reads the SAVED results in
research/v0701_partial/ (not a truncated bundle: the saved files are 1.7 MB, the old workflow's synthesizer saw the first 180 KB of them) and, as its
LAST step, writes research/v0701_partial/<label>.json, the file tools/save_agent.sh banks.

The questions are the READ ME's 8 steps (the scan study file) and CX1 (diagnose the diagnostics file), the same as workflow_files_analysis.js's
'YOU ARE THE SYNTHESIZER' / 'YOU ARE THE COMPLETENESS CRITIC', split so no single agent has to hold everything.
"""
import json
import os
import sys

REPO_PARTIAL = '/home/user/novig/research/v0701_partial'

S = {'type': 'string'}
N = {'type': 'number'}
B = {'type': 'boolean'}


def arr(item):
    return {'type': 'array', 'items': item}


def obj(props, required):
    return {'type': 'object', 'properties': props, 'required': required}


FIX_ITEM = obj({'title': S, 'evidence': S, 'code': S, 'fix': S, 'safe_without_asking': B, 'money_risk': S}, ['title', 'evidence', 'fix'])
QUESTION = obj({'question': S, 'recommendation': S, 'evidence': S, 'settles_with': S}, ['question', 'recommendation'])

SCHEMAS = {
    'synth-diagnose': obj({
        'headline': S,
        'app_bugs': arr(FIX_ITEM),
        'misleading_reports': arr(FIX_ITEM),
        'not_app_fault': arr(S),
        'fine': arr(S),
        'questions_for_tj': arr(QUESTION),
        'dropped_claims': arr(obj({'claim': S, 'why': S}, ['claim', 'why'])),
        'checked_in_code': arr(S),
    }, ['headline', 'app_bugs', 'not_app_fault', 'fine']),
    'synth-study': obj({
        'trust': S,
        'headline_edge': S,
        'listed_ev_vs_clv': S,
        'timing': S,
        'traps': arr(S),
        'props_sharp_book_answer': S,
        'hidden_bets_answer': S,
        'bids_answer': S,
        'contradictions_between_slices': arr(S),
        'recomputed': arr(obj({'what': S, 'value': S, 'matches_the_slice': B}, ['what', 'value', 'matches_the_slice'])),
        'thin_or_unknown': arr(S),
        'next_logging': arr(S),
    }, ['trust', 'headline_edge', 'timing', 'props_sharp_book_answer', 'hidden_bets_answer', 'recomputed']),
    'synth-strategies': obj({
        'rules_ranked': arr(obj({
            'rank': N, 'name': S, 'expr': S, 'bets': N, 'games': N, 'clv': N, 'clv_lo': N, 'clv_hi': N, 'first_half_clv': N, 'second_half_clv': N, 'per_day': N,
            'lens_verdicts': S, 'survives_count': N,
            'verdict': {'type': 'string', 'enum': ['worth_putting_to_tj', 'refuted', 'too_thin']},
            'on_independent_closes': S, 'setting_to_change': S, 'what_would_settle_it': S,
        }, ['rank', 'name', 'expr', 'survives_count', 'verdict'])),
        'unverified_weaker_rules': S,
        'multiple_comparisons': S,
        'simple_answer': S,
        'tracker_close_circularity': S,
        'builders_disagree_about': arr(S),
    }, ['rules_ranked', 'unverified_weaker_rules', 'multiple_comparisons', 'simple_answer']),
    'synthesis': obj({
        'headline': S,
        'app_bugs': arr(FIX_ITEM),
        'not_app_fault': arr(S),
        'fine': arr(S),
        'study_trust': S,
        'edge_is_real': S,
        'strategies': arr(obj({'rank': N, 'name': S, 'definition': S, 'evidence': S, 'luck_and_caveats': S, 'setting_to_change': S, 'bets_per_day': S},
                              ['rank', 'name', 'definition', 'evidence', 'setting_to_change'])),
        'traps': arr(S),
        'props_sharp_book_answer': S,
        'hidden_bets_answer': S,
        'timing_answer': S,
        'bids_answer': S,
        'proposals_for_tj': arr(QUESTION),
        'next_logging': arr(S),
        'not_verified': arr(S),
    }, ['headline', 'app_bugs', 'strategies', 'proposals_for_tj']),
    'critic': obj({
        'missing': arr(S), 'overstated': arr(S), 'recomputed': arr(S), 'follow_ups': arr(S),
        'contradictions': arr(S),
    }, ['missing', 'overstated', 'follow_ups']),
}
SCHEMAS['final'] = dict(SCHEMAS['synthesis'], properties=dict(SCHEMAS['synthesis']['properties'], critic_items_answered=arr(obj({'item': S, 'what_i_did': S}, ['item', 'what_i_did'])),
                                                              critic_items_left_open=arr(obj({'item': S, 'why': S}, ['item', 'why']))))

TASKS = {
    'synth-diagnose': (
        "YOU ARE THE DIAGNOSIS SECTION WRITER (task CX1: diagnose the v0.70.1 diagnostics file). Read IN FULL the five saved diagnosis results "
        f"{REPO_PARTIAL}/diag-network-performance.json, diag-sources-credits.json, diag-tracker-accuracy.json, diag-bids-autobet.json, diag-lifecycle-errors.json "
        "(each slice analyst's summary and findings) and the diagnostics file's own text {S}/study/diag_text_no_bets.txt (its READ ME at the top says what Tj wants; grep it to check any claim; "
        "{S}/study/diag_every_bet.jsonl holds his own bets). Tj's question: what is wrong, what is fine, and what is the app's fault, so the app can be fixed.\n"
        "1. Merge findings several slices report about the same thing into ONE item each (the unreadable batch reply, the 120-line timeline cap, the bids 'most fills came within 2 minutes' alarm, "
        "the DoH hosts ranked FAILURE, the Runway SHORT verdicts, the maker.json rewrite, the 'cycle p95' comparison, the cumulative 429 count shown as current ...).\n"
        "2. For EVERY item you keep as an app fault, confirm it yourself in the code (read-only: grep and read /home/user/novig, cite file:line) and in the diagnostics text (cite a line number or quote a few words). "
        "Drop or downgrade what you cannot confirm and list it under dropped_claims with why.\n"
        "3. Rank by what could cost money or mislead Tj's decisions first. Separate: APP BUGS (a fix that is safe without asking: say exactly what to change and how to test it), MISLEADING REPORTS (the diagnostics' own wording or judging), NOT THE APP'S FAULT (phone, carrier, Novig, providers), FINE, and QUESTIONS FOR TJ (anything that changes which bets are placed, how many credits are spent, or loosens a limit).\n"
        "4. FACTS TO VERIFY, not to trust: the file prints \"Novig's answer to a batch of N bids couldn't be read\" 3 times (lines ~563-575 and ~878/903); MakerDesk.placeBatch sets batchUnreadable after the FIRST unreadable reply, "
        "so '3 of 3 first batches per run' means EVERY batch the app ever tried was unreadable and nothing is known about later batches; NovigTradingClient.placeOrders decodes a 201 as {accepted:[{orderId,clientId?}]} with the app's Json(ignoreUnknownKeys=true), "
        "which is what docs.novig.com documents (api-reference/execution/batch-place-orders); so the decode fails for a reason only the reply's shape can show (an empty body? different key names?), and v0.70.4's ReplyShape (task DA7, on main, not yet released) will print it. "
        "Say whether the burst trader shares that path (its first live trade would halt 'UNCONFIRMED') and whether anything else needs to change before then. Also check the versions: v0.70.2 added the always-on burst line; v0.70.3 added Low API usage's margin chip and the trap guard's typed hours; RELEASED already, so do not list them as open.\n"
        "5. Every number in your result must come from the saved slices or from your own check; say which."),
    'synth-study': (
        "YOU ARE THE STUDY SECTION WRITER (task CX2: the scan study's READ ME, steps 1-5 and 7, plus the hidden-bets question and the bids). Read IN FULL the nine saved study results "
        f"{REPO_PARTIAL}/study-data-quality.json, study-overall-edge.json, study-splits-bet-attributes.json, study-splits-process-attributes.json, study-timing-looks.json, study-traps.json, "
        "study-props-sharp-book.json, study-hidden-and-filters.json, study-bids.json, and the READ ME and data dictionary {S}/study/study_readme_dictionary.txt (the task Tj set, 8 steps; yours are 1-5, 7 and the hidden-bets question).\n"
        "1. For each READ ME step give the answer with numbers (n bets, n games, estimate, game-clustered CI) and say how far it can be trusted.\n"
        "2. RE-CHECK the 8 most load-bearing numbers yourself with the shared loader (all-bets CLV and ROI; CLV by time-to-start bands; EV>=2% inside 6 h; CLV on independent closes only (close_ok and close_src not 'tracker'); the hidden-vs-shown split; the props sharp-book what-ifs; the bids CLV; the listed-EV-to-CLV slope) and list each under recomputed with whether it matches the slice.\n"
        "3. Find CONTRADICTIONS between slices (one says X holds, another says it does not; different populations behind the same label) and resolve them with the loader or say why they cannot be resolved.\n"
        "4. Answer Tj's two open questions plainly: (a) should a prop need a sharp prop book to agree before it is listed or bet (kept/dropped/not-judged props, CLV by close source, ROI, games, by date half, bets a day each choice costs, how the judged-only selection biases it); (b) do the bets his filters hide (the `screen` flag) beat the close or profit, and do his filters cost edge or save him from traps (EV floor, odds cap, books, one-sided, complete book, row limit).\n"
        "5. Say what is too thin to say and how many bets or games would settle it, and what the log should record next time. Never recommend loosening a safety limit; every rule change is a QUESTION for Tj."),
    'synth-strategies': (
        "YOU ARE THE STRATEGY SECTION WRITER (task CX2 step 6 and 8: the rules worth trying). Read IN FULL the three saved builders "
        f"{REPO_PARTIAL}/strategy-simple-filters.json, strategy-timing-price.json, strategy-trap-avoid-and-props.json, {REPO_PARTIAL}/candidates.json (the 10 rules chosen for verification: the best CLV lower bounds after dedupe; 14 weaker rules were NOT verified, say so), "
        f"and ALL 30 verifier results {REPO_PARTIAL}/verify-<n>-reproduce.json, verify-<n>-luck.json, verify-<n>-feasibility.json for n = 1..10 (n is the rank in candidates.json).\n"
        "1. For each of the 10 candidates tally the three lenses (reproduce / luck / feasibility), what each said and why, and give a verdict: worth_putting_to_tj only if at least 2 of 3 lenses survive AND the reasons of the one that did not are not fatal; refuted; or too_thin (name the number of bets/games that would settle it).\n"
        "2. A known problem to chase: verify-1 found rule 1 ('Late sniper') does NOT survive because its close is partly the app's own Tracker read taken shortly before the start (circular: the price it buys at is nearly the close). Check EVERY candidate for the same circularity: recompute each with close_ok and close_src != 'tracker' (independent closes: pinnacle / espn / novig_trades with enough trades) using the shared loader and report the CLV, CI and n on independent closes only, next to the all-closes number.\n"
        "3. Multiple comparisons: add up how many rules and variants all three builders tried (tried_count) and say what that does to the best rule's p-value; check the builders' tried_count figures are consistent with their files.\n"
        "4. Give the simple answer first: which, if any, simple filter (listing time before the start, EV at the look, etc.) is credible, the exact app setting or code that would implement it (Settings or the file: data/.../Presets.kt, SharpVeto.kt, the trap guard, the auto-bet's rules, CNO filters), bets per day at the caps, and what it would NOT do. Each is a PROPOSAL; the app's current trap guard default is 6 h and the phone ran 24 h.\n"
        "5. Where the three builders disagree, say so and resolve it with the loader if you can."),
}

SYNTHESIS = (
    "YOU ARE THE SYNTHESIZER. Read IN FULL the three section reports "
    f"{REPO_PARTIAL}/synth-diagnose.json, synth-study.json, synth-strategies.json (each already checked against the code and the loader by its writer), and skim the raw slices they rest on "
    f"({REPO_PARTIAL}/diag-*.json, study-*.json, strategy-*.json, verify-*.json) wherever a claim looks odd. Then write the FINAL REPORT for Tj as a structured object: "
    "(1) diagnose: what is wrong (the app's fault, ranked, each with the code and the fix), what is not the app's fault, what is fine; "
    "(2) the study: what can be trusted, the headline edge (is it real), the ranked strategies worth trying with evidence (only rules that survived verification, with their luck and thin-sample caveats), traps, the answer to the props-need-a-sharp-book question, the answer to the hidden-bets question, timing, bids; "
    "(3) exact app settings or code changes as PROPOSALS for Tj (file + setting + the number of bets that would settle it), separating what you would change without asking (an app bug: say the test that proves it) from what needs Tj's decision (a rule that decides which bets are placed); "
    "(4) what the next files should record; (5) not_verified: what you did NOT check (the 14 weaker candidate rules, anything resting on one slice only).\n"
    "Re-check the 5 numbers that matter most yourself with the loader and put them in the report; resolve contradictions between the three sections; say 'the data suggests'; be concrete and short in each field; numbers everywhere. "
    "The sample is about 2.26 days of first-looks: do not oversell.")

CRITIC = (
    "YOU ARE THE COMPLETENESS CRITIC. Read IN FULL "
    f"{REPO_PARTIAL}/synthesis.json (the synthesizer's final report), the READ ME's 8-step task ({{S}}/study/study_readme_dictionary.txt) and the diagnostics READ ME (the top of {{S}}/study/diag_text_no_bets.txt). "
    "Say what is MISSING or UNVERIFIED: a step of the task not answered; a claim without numbers; a contradiction between the section reports "
    f"({REPO_PARTIAL}/synth-*.json); a number that is not reproducible from the loader (recompute the 3 most load-bearing ones and say what you got); "
    "a place the report oversells given only ~2.26 days of first-looks; an app bug that should be double-checked in the code (check it); a question Tj asked that is unanswered; "
    "a proposal that would loosen a safety limit; any wallet balance, account id or key in the text (it is going into a public repo). "
    "Return concrete follow-ups, most important first, each answerable from the data or the code.")

FINAL = (
    "YOU ARE THE FINAL EDITOR. Read IN FULL "
    f"{REPO_PARTIAL}/synthesis.json and {REPO_PARTIAL}/critic.json. Answer every critic item you can from the data (the loader) or the code (read-only), and fix what is overstated or missing; "
    "for each item that cannot be answered, say so in critic_items_left_open with why. Return the REVISED final report in the same schema as the synthesis, plus critic_items_answered and critic_items_left_open. "
    "Keep every number traceable; do not add a claim you did not check; no wallet balance, account id or key; every rule change stays a QUESTION for Tj.")

TEXT = dict(TASKS, synthesis=SYNTHESIS, critic=CRITIC, final=FINAL)
LABELS = list(TEXT)


def prompt(preamble, label, sp):
    saving = (f"\nSAVING (this overrides the READ-ONLY rule for exactly ONE file): you may write a second copy of your final structured result, as JSON, to "
              f"{REPO_PARTIAL}/{label}.json (create the directory if missing; write NOTHING else into the repo). That copy is public on GitHub: it holds "
              f"numbers and findings only: no bet row dumps, no wallet balance, no keys, no account or user ids. Do it as your LAST step, after writing {sp}/work/{label}/result.json. "
              f"Your final chat reply must be a summary of at most 400 words with your 10 most important numbers.\n")
    return (preamble + "\n" + TEXT[label].replace('{S}', sp) + "\n" + saving +
            "\nRESULT SCHEMA (write JSON that matches it):\n" + json.dumps(SCHEMAS[label], indent=1))


def build(v0701):
    pdir = os.path.join(v0701, 'prompts')
    preamble = open(os.path.join(pdir, '_preamble.txt')).read()
    for label in LABELS:
        with open(os.path.join(pdir, label + '.txt'), 'w') as f:
            f.write(prompt(preamble, label, v0701))
    return LABELS


def selftest():
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, 'prompts'))
        open(os.path.join(d, 'prompts', '_preamble.txt'), 'w').write('PREAMBLE\n')
        labels = build(d)
        assert labels == ['synth-diagnose', 'synth-study', 'synth-strategies', 'synthesis', 'critic', 'final'], labels
        for l in labels:
            t = open(os.path.join(d, 'prompts', l + '.txt')).read()
            assert t.startswith('PREAMBLE') and f'research/v0701_partial/{l}.json' in t and f'{d}/work/{l}/result.json' in t and 'RESULT SCHEMA' in t, l
            assert '{S}' not in t, l                                    # every {S} placeholder is the scratch dir
            assert json.loads(t[t.index('RESULT SCHEMA (write JSON that matches it):\n') + 43:])['type'] == 'object'
        assert f'{d}/study/diag_text_no_bets.txt' in open(os.path.join(d, 'prompts', 'synth-diagnose.txt')).read()
        assert 'verify-<n>-reproduce.json' in open(os.path.join(d, 'prompts', 'synth-strategies.txt')).read()
        f = SCHEMAS['final']['properties']
        assert 'critic_items_answered' in f and 'strategies' in f and 'critic_items_answered' not in SCHEMAS['synthesis']['properties']   # final = synthesis + two fields
    print('genphase4.py selftest: ok')


if __name__ == '__main__':
    if sys.argv[1:] == ['--selftest']:
        selftest()
    elif len(sys.argv) == 2:
        labels = build(os.path.abspath(sys.argv[1]))
        print(f'wrote {len(labels)} phase-4 prompts: {", ".join(labels)}')
    else:
        raise SystemExit(__doc__)
