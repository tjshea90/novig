#!/usr/bin/env python3
"""Split Tj's two phone exports (the scan-study file and the diagnostics file of 2026-10-06 00:08 EDT, v0.70.1) into the pieces the analysis reads.

    python3 tools/research/study_v0701/extract.py <vigilant-scan-study-....txt> <vigilant-diagnostics-....txt> <out_dir>

Makes <out_dir>/study/ (study_bets.jsonl, study_filled_bids.jsonl, study_unfilled_bids.jsonl, study_readme_dictionary.txt, study_summary_splits.txt, study_whatif.txt,
study_bids_summary.txt, diag_every_bet.jsonl, diag_text_no_bets.txt) and <out_dir>/lib/load.py (the shared loader; it finds ../study itself).
The raw files are NOT in the repo (public; they hold Tj's bets and wallet): if they are gone, ask Tj to send them again. Needs: pandas, numpy, scipy (pip install pandas scipy).
"""
import os, shutil, sys


def section(lines, prefix):
    for i, l in enumerate(lines):
        if l.startswith(prefix):
            return i
    raise SystemExit('section not found: ' + prefix)


def main(study_path, diag_path, out):
    sd = os.path.join(out, 'study'); ld = os.path.join(out, 'lib')
    os.makedirs(sd, exist_ok=True); os.makedirs(ld, exist_ok=True)
    shutil.copy(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'load.py'), os.path.join(ld, 'load.py'))
    L = open(study_path).read().split('\n')
    i_bets, i_end = section(L, '== EVERY BET'), section(L, '== END OF FILE')
    i_sum, i_wi, i_bd = section(L, '== SUMMARY'), section(L, '== WHAT IF'), section(L, '== BIDS')
    i_fb, i_nb = section(L, '== EVERY FILLED BID'), section(L, '== THE NEWEST 300 BIDS')
    js = lambda a, b: '\n'.join(l for l in L[a:b] if l.startswith('{')) + '\n'
    w = lambda name, text: open(os.path.join(sd, name), 'w').write(text)
    w('study_bets.jsonl', js(i_bets + 1, i_end)); w('study_filled_bids.jsonl', js(i_fb + 1, i_nb)); w('study_unfilled_bids.jsonl', js(i_nb + 1, i_bets))
    w('study_readme_dictionary.txt', '\n'.join(L[:i_sum]) + '\n'); w('study_summary_splits.txt', '\n'.join(L[i_sum:i_wi]) + '\n')
    w('study_whatif.txt', '\n'.join(L[i_wi:i_bd]) + '\n'); w('study_bids_summary.txt', '\n'.join(L[i_bd:i_fb]) + '\n')
    D = open(diag_path).read().split('\n')
    a, b = section(D, '== EVERY BET'), section(D, '== CODE MAP')
    w('diag_every_bet.jsonl', '\n'.join(l for l in D[a + 1:b] if l.startswith('{')) + '\n'); w('diag_text_no_bets.txt', '\n'.join(D[:a] + D[b:]) + '\n')
    print('wrote', sd, 'and', ld)


if __name__ == '__main__':
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
