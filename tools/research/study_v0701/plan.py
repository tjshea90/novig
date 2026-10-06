#!/usr/bin/env python3
"""plan.py: what the v0.70.1 analysis has SAVED on GitHub, and which agents to launch next.

Tj's rule (2026-10-06 14:14Z): at most THREE agents in flight at once, and each agent's work goes to
GitHub the moment it finishes (tools/save_agent.sh <label>), so a session that dies loses at most the
agents that were still running. A new session runs this first; it never reruns a saved label.

  python3 tools/research/study_v0701/plan.py                # status + the next agents to launch
  python3 tools/research/study_v0701/plan.py --running study-traps,study-bids   # these are in flight: never suggested again, and they count against the 3
  python3 tools/research/study_v0701/plan.py --inflight 2   # same count, labels unknown
  python3 -I tools/research/study_v0701/genverify.py <v0701 dir>   # phase 3: writes prompts/verify-<n>-<lens>.txt once the 3 strategy-* are saved
  python3 tools/research/study_v0701/plan.py --selftest

A label is saved when research/v0701_partial/<label>.json exists and parses as JSON.
Labels: study-* (9) and diag-* (5) need only the extracted data; strategy-* (3) need all 9 study-*;
verify-<n>-<lens> (<=10 candidate rules x 3 lenses; candidates.json is made here from the strategy-* files,
same dedupe/cap as workflow_files_analysis.js) need the strategy-*; phase 4 needs everything before it: the three section reports
(synth-diagnose, synth-study, synth-strategies, any order), then synthesis, then critic, then final (the critic's follow-ups answered).
Prompts: node tools/research/study_v0701/genprompts.js ... writes prompts/<label>.txt for phase 1 and the strategy angles / lenses / schemas
for the rest; genverify.py writes phase 3's; genphase4.py writes phase 4's (python3 -I tools/research/study_v0701/genphase4.py <v0701 dir>).
"""
import json, os, re, sys, tempfile

STUDY = ['study-data-quality', 'study-overall-edge', 'study-splits-bet-attributes', 'study-splits-process-attributes',
         'study-timing-looks', 'study-traps', 'study-props-sharp-book', 'study-hidden-and-filters', 'study-bids']
DIAG = ['diag-network-performance', 'diag-sources-credits', 'diag-tracker-accuracy', 'diag-bids-autobet', 'diag-lifecycle-errors']
STRAT = ['strategy-simple-filters', 'strategy-timing-price', 'strategy-trap-avoid-and-props']
LENSES = ['reproduce', 'luck', 'feasibility']
SECTIONS = ['synth-diagnose', 'synth-study', 'synth-strategies']   # phase 4a: each reads the saved slices of its own part, any order
CAP = 10  # candidate rules to verify, as in the workflow script

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DIR = os.path.normpath(os.path.join(HERE, '..', '..', '..', 'research', 'v0701_partial'))


def saved(d, label):
    p = os.path.join(d, label + '.json')
    try:
        with open(p) as f:
            return json.load(f) is not None
    except (OSError, ValueError):
        return False


def candidates(d):
    """The <=10 rules to verify: dedupe by whitespace-free expr (best second-half CLV wins), best CLV lower bound first."""
    seen = {}
    for lab in STRAT:
        try:
            with open(os.path.join(d, lab + '.json')) as f:
                res = json.load(f)
        except (OSError, ValueError):
            continue
        for r in (res.get('rules') or []):
            key = re.sub(r'\s+', '', r.get('expr') or r.get('name') or '')
            prev = seen.get(key)
            if prev is None or (r.get('second_half_clv') if r.get('second_half_clv') is not None else -9) > \
                    (prev.get('second_half_clv') if prev.get('second_half_clv') is not None else -9):
                seen[key] = dict(r, angle=res.get('angle'), tried_count=res.get('tried_count'), baseline=res.get('baseline'))
    ranked = sorted(seen.values(), key=lambda r: -(r['clv_lo'] if r.get('clv_lo') is not None else -9))
    return ranked[:CAP], len(seen)


def plan(d):
    """Returns (rows, ready): rows = [(phase, label, saved?)], ready = labels launchable now, in priority order."""
    rows, ready = [], []
    done = lambda l: saved(d, l)
    for l in STUDY: rows.append(('1 study', l, done(l)))
    for l in DIAG: rows.append(('1 diagnose', l, done(l)))
    study_done = all(done(l) for l in STUDY)
    for l in STRAT: rows.append(('2 strategy', l, done(l)))
    strat_done = all(done(l) for l in STRAT)
    cands, total = [], 0
    if strat_done:
        cands, total = candidates(d)
        with open(os.path.join(d, 'candidates.json'), 'w') as f:
            json.dump({'cap': CAP, 'unique_rules': total, 'candidates': cands}, f, indent=1)
    verify_labels = [f'verify-{i}-{k}' for i in range(1, len(cands) + 1) for k in LENSES]
    for l in verify_labels: rows.append(('3 verify', l, done(l)))
    p1_done = study_done and all(done(l) for l in DIAG)
    verify_done = strat_done and all(done(l) for l in verify_labels)
    for l in SECTIONS: rows.append(('4 sections', l, done(l)))
    rows.append(('4 synthesis', 'synthesis', done('synthesis')))
    rows.append(('4 critic', 'critic', done('critic')))
    rows.append(('4 final', 'final', done('final')))
    # priority: study (they unlock the builders) > strategy > verify > diagnose > sections > synthesis > critic > final
    ready += [l for l in STUDY if not done(l)]
    if study_done: ready += [l for l in STRAT if not done(l)]
    if strat_done: ready += [l for l in verify_labels if not done(l)]
    ready += [l for l in DIAG if not done(l)]
    if p1_done and verify_done: ready += [l for l in SECTIONS if not done(l)]
    sections_done = all(done(l) for l in SECTIONS)
    if p1_done and verify_done and sections_done and not done('synthesis'): ready.append('synthesis')
    if done('synthesis') and not done('critic'): ready.append('critic')
    if done('critic') and not done('final'): ready.append('final')
    return rows, ready


def report(d, inflight, maxn=3, running=()):
    rows, ready = plan(d)
    ready = [l for l in ready if l not in running]
    inflight = max(inflight, len(running))
    out = []
    for phase in dict.fromkeys(p for p, _, _ in rows):
        ls = [(l, s) for p, l, s in rows if p == phase]
        out.append(f'  {phase:<12} {sum(s for _, s in ls)}/{len(ls)} saved')
    slots = max(0, maxn - inflight)
    out.append('')
    if not ready:
        out.append('ALL SAVED: nothing to launch. Next: write research/scan_study_analysis_2026-10-06_v0.70.1.md and RESEARCH.md §97 from final.json (+ synthesis.json, critic.json), tick CX1/CX2/DA1.')
    else:
        out.append(f'NEXT: launch {min(slots, len(ready))} now (max {maxn} in flight, {inflight} running); after EACH one finishes run: bash tools/save_agent.sh <label>')
        for l in ready[:slots]: out.append(f'  -> {l}')
        if len(ready) > slots: out.append(f'  then, as slots free up: {", ".join(ready[slots:slots + 6])}{" ..." if len(ready) > slots + 6 else ""}')
    return '\n'.join(out)


def selftest():
    with tempfile.TemporaryDirectory() as d:
        mk = lambda l, obj=None: open(os.path.join(d, l + '.json'), 'w').write(json.dumps(obj if obj is not None else {'ok': 1}))
        rows, ready = plan(d)
        assert ready[:3] == STUDY[:3] and 'strategy-simple-filters' not in ready, ready   # builders wait for the 9 study results
        assert ready[9:] == DIAG, ready
        assert 'ALL SAVED' not in report(d, 0) and report(d, 2).count('  -> ') == 1 and report(d, 3).count('  -> ') == 0
        r = report(d, 0, running=(STUDY[0], STUDY[1]))                                          # running labels are never suggested again
        assert '-> ' + STUDY[0] not in r and '-> ' + STUDY[1] not in r and '-> ' + STUDY[2] in r and r.count('  -> ') == 1, r
        open(os.path.join(d, STUDY[0] + '.json'), 'w').write('{half written')                  # a torn write is not saved
        assert not saved(d, STUDY[0])
        for l in STUDY: mk(l)
        rows, ready = plan(d)
        assert ready[:3] == STRAT and ready[3:] == DIAG, ready
        for l in DIAG: mk(l)
        rule = lambda n, e, lo, h2: {'name': n, 'expr': e, 'clv_lo': lo, 'second_half_clv': h2}
        mk(STRAT[0], {'angle': 'a', 'tried_count': 5, 'rules': [rule('r1', 'a > 1', 0.5, 0.2), rule('r2', 'b>2', -0.1, 0.1)]})
        mk(STRAT[1], {'angle': 'b', 'tried_count': 7, 'rules': [rule('r1b', 'a>1', 0.4, 0.9), rule('r3', 'c==1', 0.9, 0.0)]})
        rows, ready = plan(d)
        assert ready == [STRAT[2]], ready
        mk(STRAT[2], {'angle': 'c', 'tried_count': 2, 'rules': []})
        rows, ready = plan(d)
        c = json.load(open(os.path.join(d, 'candidates.json')))
        assert [r['name'] for r in c['candidates']] == ['r3', 'r1b', 'r2'], c      # whitespace-free dedupe, better 2nd half wins, sorted by CLV lower bound
        assert ready == [f'verify-{i}-{k}' for i in (1, 2, 3) for k in LENSES], ready
        for l in ready: mk(l)
        rows, ready = plan(d)
        assert ready == SECTIONS, ready                                                          # the three section reports, any order, before the synthesis
        mk(SECTIONS[0]); mk(SECTIONS[1]); assert plan(d)[1] == [SECTIONS[2]], plan(d)[1]
        mk(SECTIONS[2]); assert plan(d)[1] == ['synthesis']
        mk('synthesis'); assert plan(d)[1] == ['critic']
        mk('critic'); assert plan(d)[1] == ['final'] and 'ALL SAVED' not in report(d, 0)
        mk('final'); assert plan(d)[1] == [] and 'ALL SAVED' in report(d, 0)
        many = {'rules': [rule(f'x{i}', f'v>{i}', i / 100, 0) for i in range(15)]}
        mk(STRAT[0], many)
        assert len(candidates(d)[0]) == CAP and candidates(d)[1] == 15 + 2
    print('plan.py selftest: ok')


if __name__ == '__main__':
    a = sys.argv[1:]
    if '--selftest' in a:
        selftest(); sys.exit(0)
    d = a[a.index('--dir') + 1] if '--dir' in a else DEFAULT_DIR
    n = int(a[a.index('--inflight') + 1]) if '--inflight' in a else 0
    run = tuple(x for x in a[a.index('--running') + 1].split(',') if x) if '--running' in a else ()
    os.makedirs(d, exist_ok=True)
    print(f'v0.70.1 analysis: saved results in {os.path.relpath(d)}')
    print(report(d, n, running=run))
