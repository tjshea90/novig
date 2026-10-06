#!/usr/bin/env python3
"""Phase 2 prompts for the v0.70.1 analysis: one strategy-builder prompt per angle, built from the 9 saved study results.

    python3 -I tools/research/study_v0701/genstrategy.py <v0701 dir>

Reads  <v0701 dir>/prompts/_preamble.txt, _strategy_angles.json, _schemas.json (written by genprompts.js)
       research/v0701_partial/study-*.json (the 9 saved study analysts: numbers only)
Writes <v0701 dir>/prompts/strategy-<angle>.txt (the full prompt; each builder writes its result to
       research/v0701_partial/<label>.json as its last step, like the phase-1 analysts).
Same digest fields as workflow_files_analysis.js (area, trust, key numbers, <=14 findings, <=10 rules per analyst), but each analyst gets an equal share of 60000 chars.
"""
import glob
import json
import os
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
PARTIAL = os.path.join(REPO, 'research', 'v0701_partial')


def digest(cap=60000):
    """Every analyst gets an equal share of `cap` characters (the workflow's plain slice would keep only the first one or two)."""
    es = [json.load(open(f)) for f in sorted(glob.glob(os.path.join(PARTIAL, 'study-*.json')))]
    share = cap // max(1, len(es))
    out = []
    for e in es:
        ent = {'area': e.get('area'), 'trust': (e.get('trust_notes') or '')[:1500], 'key': e.get('key_numbers'),
               'findings': (e.get('findings') or [])[:14], 'rules': (e.get('candidate_rules') or [])[:10]}
        # shed the tail (rules first, then findings) until this analyst fits its share
        while len(json.dumps(ent)) > share and (ent['rules'] or ent['findings']):
            (ent['rules'] if ent['rules'] else ent['findings']).pop()
        if len(json.dumps(ent)) > share:
            ent['key'] = json.dumps(ent['key'])[:max(200, share - 2500)]
        out.append(ent)
    return json.dumps(out)


def main(v):
    p = os.path.join(v, 'prompts')
    pre = open(os.path.join(p, '_preamble.txt')).read()
    angles = json.load(open(os.path.join(p, '_strategy_angles.json')))
    schema = json.load(open(os.path.join(p, '_schemas.json')))['RULE_SCHEMA']
    d = digest()
    # the SAVING paragraph is the same one the phase-1 prompts carry: lift it from one of them
    ref = open(os.path.join(p, 'study-overall-edge.txt')).read()
    a, b = ref.index('\nSAVING'), ref.index('\nRESULT SCHEMA')
    saving = ref[a:b]
    for ang in angles:
        txt = (pre + "\nYOU ARE A STRATEGY BUILDER (step 6 of the READ ME's task). Nine analysts have already explored the data; their findings, "
               "trust notes and candidate rules are below as JSON (they may contradict each other; re-check what matters yourself with the loader "
               "instead of trusting any number).\nANALYSTS' DIGEST:\n" + d[:60000] + '\n\n' + ang['prompt']
               + "\nReturn at most 8 rules, strongest first, each reproducible from its 'expr'." + saving
               + '\nRESULT SCHEMA (write JSON that matches it):\n' + json.dumps(schema, indent=1))
        open(os.path.join(p, ang['label'] + '.txt'), 'w').write(txt)
        print('wrote', ang['label'] + '.txt', len(txt), 'chars (digest', min(len(d), 60000), 'of', len(d), ')')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
