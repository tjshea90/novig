#!/usr/bin/env python3
"""Phase 3 prompts for the v0.70.1 analysis: one adversarial-verifier prompt per (candidate rule, lens).

    python3 -I tools/research/study_v0701/genverify.py <v0701 dir>
    python3 -I tools/research/study_v0701/genverify.py --selftest

Reads  <v0701 dir>/prompts/_preamble.txt, _lenses.json, _schemas.json (written by genprompts.js)
       research/v0701_partial/candidates.json (written by plan.py once the 3 strategy builders are saved)
Writes <v0701 dir>/prompts/verify-<n>-<lens>.txt, n = 1-based rank in candidates.json, lens = reproduce | luck | feasibility.
Each verifier writes its verdict to <v0701 dir>/work/<label>/result.json and, as its LAST step, a numbers-only copy to
research/v0701_partial/<label>.json (label = verify-<n>-<lens>), the file tools/save_agent.sh banks. The prompt text is the one in
workflow_files_analysis.js ('YOU ARE AN ADVERSARIAL VERIFIER ...'), so a run here and a Workflow run ask the same question.
"""
import json
import os
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
CANDIDATES = os.path.join(REPO, 'research', 'v0701_partial', 'candidates.json')


def val(r, k):
    v = r.get(k)
    return 'n/a' if v is None else v


def prompt(preamble, r, lens, sp, label, schema):
    saving = (f"\nSAVING (this overrides the READ-ONLY rule for exactly ONE file): you may write a second copy of your final structured result, as JSON, to "
              f"/home/user/novig/research/v0701_partial/{label}.json (create the directory if missing; write NOTHING else into the repo). That copy is public on GitHub: it holds "
              f"numbers and findings only: no bet row dumps, no wallet balance, no keys, no account or user ids. Do it as your LAST step, after writing {sp}/work/{label}/result.json. "
              f"Your final chat reply must be a summary of at most 300 words with the numbers you recomputed and your verdict.\n")
    return (preamble +
            "\nYOU ARE AN ADVERSARIAL VERIFIER. Try to REFUTE this candidate rule; default to survives=false if you are not convinced. "
            f"Rule: {val(r, 'name')}\nexpr: {val(r, 'expr')}\nrationale: {val(r, 'rationale')}\n"
            f"Builder's numbers: bets {val(r, 'bets')}, games {val(r, 'games')}, CLV {val(r, 'clv')} [{val(r, 'clv_lo')}, {val(r, 'clv_hi')}], ROI {val(r, 'roi')}, "
            f"first half {val(r, 'first_half_clv')}, second half {val(r, 'second_half_clv')}, baseline CLV {val(r, 'baseline_clv')}, per day {val(r, 'per_day')}, "
            f"tried_count {val(r, 'tried_count')}, luck {val(r, 'luck_probability')}.\n"
            f"The builder's angle: {val(r, 'angle')}\nYOUR LENS: {lens['text']}\n" + saving +
            "\nRESULT SCHEMA (write JSON that matches it):\n" + json.dumps(schema, indent=1))


def build(v0701, candidates_path=CANDIDATES):
    pdir = os.path.join(v0701, 'prompts')
    preamble = open(os.path.join(pdir, '_preamble.txt')).read()
    lenses = json.load(open(os.path.join(pdir, '_lenses.json')))
    schema = json.load(open(os.path.join(pdir, '_schemas.json')))['VERDICT_SCHEMA']
    cands = json.load(open(candidates_path))['candidates']
    out = []
    for i, r in enumerate(cands, 1):
        for lens in lenses:
            label = f"verify-{i}-{lens['key']}"
            with open(os.path.join(pdir, label + '.txt'), 'w') as f:
                f.write(prompt(preamble, r, lens, v0701, label, schema))
            out.append(label)
    return out


def selftest():
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, 'prompts'))
        open(os.path.join(d, 'prompts', '_preamble.txt'), 'w').write('PREAMBLE\n')
        json.dump([{'key': 'reproduce', 'text': 'T1'}, {'key': 'luck', 'text': 'T2'}], open(os.path.join(d, 'prompts', '_lenses.json'), 'w'))
        json.dump({'VERDICT_SCHEMA': {'type': 'object'}}, open(os.path.join(d, 'prompts', '_schemas.json'), 'w'))
        c = os.path.join(d, 'c.json')
        json.dump({'candidates': [{'name': 'R1', 'expr': 'df.x > 1', 'bets': 5, 'clv': 0.01}, {'name': 'R2', 'expr': 'df.y'}]}, open(c, 'w'))
        labels = build(d, c)
        assert labels == ['verify-1-reproduce', 'verify-1-luck', 'verify-2-reproduce', 'verify-2-luck'], labels
        t = open(os.path.join(d, 'prompts', 'verify-2-luck.txt')).read()
        assert 'Rule: R2' in t and 'expr: df.y' in t and 'YOUR LENS: T2' in t and 'bets n/a' in t       # missing numbers say n/a, never crash
        assert 'research/v0701_partial/verify-2-luck.json' in t and f'{d}/work/verify-2-luck/result.json' in t and 'REFUTE' in t
    print('genverify.py selftest: ok')


if __name__ == '__main__':
    if sys.argv[1:] == ['--selftest']:
        selftest()
    elif len(sys.argv) == 2:
        labels = build(os.path.abspath(sys.argv[1]))
        print(f'wrote {len(labels)} verifier prompts: {labels[0]} ... {labels[-1]}')
    else:
        raise SystemExit(__doc__)
