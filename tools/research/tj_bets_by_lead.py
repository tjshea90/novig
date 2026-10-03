#!/usr/bin/env python3
"""Tj's own bets against the close, by how long before the start they were placed. RESEARCH.md §71, re-runnable on any Diagnostics file.

Tj, 2026-10-03: "some of my 'gift' positive EV bets moved against me dramatically". The trap guard's first rule (TrapGuard.early, 6 h by default)
comes from this split of the bets in the file's EVERY BET block (closes as the app measured them; outliers over ±6% EV when bet and lock legs left
out, as the Tracker's stats leave them out).

    python3 tools/research/tj_bets_by_lead.py vigilant-diagnostics-<version>-<date>.txt

Also splits the bets by the sharpest book's own edge when they were placed (the sharp veto's bar, RESEARCH.md §72).
"""
import json, math, sys


def main(path):
    rows = []
    inside = False
    for line in open(path, encoding='utf-8'):
        if line.startswith('<<<JSONL'):
            inside = True
            continue
        if line.startswith('JSONL'):
            inside = False
        if inside and line.startswith('{"id"'):
            rows.append(json.loads(line))
    bets = [b for b in rows if b.get('clv') is not None and b.get('evAtBet') is not None and not b.get('outlier') and not b.get('lockFor')]
    print(f'{len(rows)} bets in the file, {len(bets)} with a true close and an EV when bet')

    def show(label, xs):
        if len(xs) < 3:
            return
        clv = [b['clv'] for b in xs]
        m = sum(clv) / len(clv)
        sd = math.sqrt(sum((c - m) ** 2 for c in clv) / max(len(clv) - 1, 1))
        ev = sum(b['evAtBet'] for b in xs) / len(xs)
        beat = sum(c > 0 for c in clv) / len(clv)
        settled = [b for b in xs if b.get('profit') is not None]
        roi = sum(b['profit'] for b in settled) / max(sum(b['stake'] for b in settled), 1e-9) if settled else float('nan')
        print(f'  {label:<26} n={len(xs):3d}  CLV {100 * m:+5.2f}% ±{196 * sd / math.sqrt(len(xs)):4.2f}  beat {100 * beat:3.0f}%  EV when bet {100 * ev:+.2f}%  '
              f'settled {len(settled):3d} ROI {100 * roi:+.1f}%')

    lead = lambda b: (b['startsAtMs'] - b['placedAtMs']) / 3_600_000
    for lo, hi in [(0, 1), (1, 3), (3, 6), (6, 12), (12, 24), (24, 48), (48, 1e9)]:
        show(f'{lo}-{hi} h before' if hi < 1e8 else f'{lo} h+ before', [b for b in bets if lo <= lead(b) < hi])
    show('under 6 h', [b for b in bets if lead(b) < 6])
    show('6 h or more', [b for b in bets if lead(b) >= 6])
    # Results over every settled bet with an EV (a close or not): the bigger sample for ROI.
    done = [b for b in rows if b.get('profit') is not None and b.get('evAtBet') is not None and not b.get('outlier') and not b.get('lockFor')]
    for label, xs in [('settled, under 6 h', [b for b in done if lead(b) < 6]), ('settled, 6 h or more', [b for b in done if lead(b) >= 6])]:
        print(f'  {label:<26} n={len(xs):3d}  ROI {100 * sum(b["profit"] for b in xs) / max(sum(b["stake"] for b in xs), 1e-9):+.1f}%')
    for s in sorted({b['scanner'] for b in bets}):
        show(f'{s} under 6 h', [b for b in bets if b['scanner'] == s and lead(b) < 6])
        show(f'{s} 6 h or more', [b for b in bets if b['scanner'] == s and lead(b) >= 6])

    # RESEARCH.md §72: the sharp veto's bar (1% by default since v0.56.0). What the sharpest book for the bet's kind gave Novig's price when it was
    # placed (atBet.sharpEv, on bets with a record as placed), against the close: the bar is right if 0-1% shows no CLV and 1%+ does.
    sharp = lambda b: (b.get('atBet') or {}).get('sharpEv')
    rec = [b for b in bets if b.get('atBet')]
    print(f'\n  By the sharpest book's own edge when bet ({len(rec)} bets with a record as placed; RESEARCH.md §72):')
    show('no sharp book on the page', [b for b in rec if sharp(b) is None])
    for lo, hi in [(-1, 0), (0, 0.01), (0.01, 0.02), (0.02, 1)]:
        show(f'sharp {100 * lo:+.0f}..{100 * hi:+.0f}%' if hi < 1 else f'sharp {100 * lo:+.0f}%+', [b for b in rec if sharp(b) is not None and lo <= sharp(b) < hi])


if __name__ == '__main__':
    main(sys.argv[1])
