#!/usr/bin/env python3
"""Phase 2 prompts for the v0.70.1 analysis: one strategy-builder prompt per angle, built from the 9 saved study results.

    python3 -I tools/research/study_v0701/genstrategy.py <v0701 dir>

Reads  <v0701 dir>/prompts/_preamble.txt, _strategy_angles.json, _schemas.json (written by genprompts.js)
       research/v0701_partial/study-*.json (the 9 saved study analysts: numbers only)
Writes <v0701 dir>/prompts/strategy-<angle>.txt (the full prompt; each builder writes its result to
       research/v0701_partial/<label>.json as its last step, like the phase-1 analysts).
The digest is compact (see digest()) so that ALL nine analysts fit; the full results are named in the prompt as files the builder may read.
"""
import glob
import json
import os
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
PARTIAL = os.path.join(REPO, 'research', 'v0701_partial')


def _cut(x, n):
    x = x if isinstance(x, str) else json.dumps(x)
    return x if len(x) <= n else x[:n] + '...'


def digest():
    """A compact digest of all 9 analysts (the workflow's plain 60000-char slice kept only the first one or two): per analyst the area, a short trust note,
    the key numbers, each finding's claim + effect, and each candidate rule's name/expr/bets/games/CLV (+ CI). The full JSON stays readable at research/v0701_partial/."""
    out = []
    for f in sorted(glob.glob(os.path.join(PARTIAL, 'study-*.json'))):
        e = json.load(open(f))
        kn = e.get('key_numbers') or []
        out.append({
            'file': os.path.basename(f),
            'area': e.get('area'),
            'trust': _cut(e.get('trust_notes') or '', 900),
            'key': [_cut(k, 330) for k in (kn if isinstance(kn, list) else [kn])[:9]],
            'findings': [{'claim': _cut(x.get('claim', ''), 260), 'effect': _cut(x.get('effect', ''), 220), 'n_games': x.get('n_games')} for x in (e.get('findings') or [])[:16]],
            'rules': [{k: x.get(k) for k in ('name', 'expr', 'bets', 'games', 'clv', 'clv_lo', 'clv_hi', 'clv_bets', 'clv_games', 'first_half_clv', 'second_half_clv', 'per_day')} for x in (e.get('candidate_rules') or [])[:12]],
        })
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
               "instead of trusting any number).\nANALYSTS' DIGEST:\n" + d + '\n(Full per-analyst results: /home/user/novig/research/v0701_partial/study-*.json; read them for any detail the digest cut.)\n\n' + ang['prompt']
               + "\nReturn at most 8 rules, strongest first, each reproducible from its 'expr'." + saving
               + '\nRESULT SCHEMA (write JSON that matches it):\n' + json.dumps(schema, indent=1))
        open(os.path.join(p, ang['label'] + '.txt'), 'w').write(txt)
        print('wrote', ang['label'] + '.txt', len(txt), 'chars (digest', len(d), ')')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
