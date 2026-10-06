#!/usr/bin/env python3
"""Cross-line bursts on Novig's live ladders: record a game's tape, and find the bursts in one. RESEARCH.md §83-§84, re-runnable.

Tj, 2026-10-05: "Short bursts after scores ... do research on how this happened and how vigilant can replicate it."

A game's lines are separate order books quoted for ONE number: the final margin (moneyline = margin > 0, a spread of -k = margin > k) or the total.
"margin > t" must never be worth less than "margin > t2" for t < t2, so buying YES at the lower line and NOT at the higher one pays at least $1
(both win when the margin falls between). If the two cost under $1 after the in-play taker fees (0.03*P*(1-P) a leg) that is a profit whatever the
game does; at a cost of 1 + P(margin between) it is a fair bet. After a play that moves the win probability the lines are re-quoted one after the
other, and for a second or less one of two neighbouring lines is stale. This script records that and measures it.

  record   poll the PUBLIC routes (no key; keep under ~4 requests a second, which it enforces) for every live game of one league: each near-the-money
           moneyline / spread / total market's recent trades (engine timestamps in ms, de-duplicated by trade id; a pass over ~25 markets takes about
           9 s, so every trade is kept but the book, every 4th pass, is only a coarse picture) and its top of book (local clock). One NDJSON tape per run.
  analyze  read tapes: the bursts (pairs of executed trades on two lines of one ladder within WINDOW_MS that cost under $1 after fees), how long
           each lasted, what it paid (the guaranteed floor and, adding the chance the margin lands between the lines, the expected value) and
           what a reaction time of L ms would have kept; plus the moneyline jumps (>= 4 cents) and which of them made a burst.

    python3 tools/research/novig_ladder_tape.py record --league NFL --minutes 200 --out tape-nfl.ndjson   (start it before the kickoff: --wait)
    python3 tools/research/novig_ladder_tape.py analyze tape-nfl.ndjson [--overlap 0.03]

What a tape cannot say: when a quote went stale (only when it was TRADED), who traded, or windows nobody took. The signed `book` websocket channel can
(NOVIG_API.md §6, §18); this is the keyless first look.
"""
import argparse, collections, json, re, sys, time, urllib.request
import numpy as np

API = 'https://api.novig.com/v3/public'
FEE_C = 0.03
WINDOW_MS = 1000
MIN_RATE_GAP = 0.27   # s between requests: ~3.7 a second (record --rate lowers it: two recorders on one address share the edge's ~4-6 a second)


def fee(p):
    return FEE_C * p * (1 - p)


def get(url, tries=3):
    for k in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(2 + 2 * k)
                continue
            if e.code == 404:
                return None
            raise
        except Exception:
            time.sleep(1 + k)
    return None


# ---------------------------------------------------------------------------------------------------------------- record
def record(a):
    last = [0.0]
    gap = 1.0 / a.rate if getattr(a, 'rate', None) else MIN_RATE_GAP
    leagues = [x.strip() for x in a.league.split(',') if x.strip()]

    def paced(url):
        d = gap - (time.time() - last[0])
        if d > 0:
            time.sleep(d)
        last[0] = time.time()
        return get(url)

    out = open(a.out, 'a')
    end = time.time() + a.minutes * 60
    seen = set()
    meta_done = set()
    markets = {}      # marketId -> dict(event, type, strike, outs)
    chosen = []
    chosen_at = 0
    npass = 0
    print('recording', a.league, 'until', time.strftime('%H:%M:%S', time.localtime(end)), file=sys.stderr, flush=True)
    while time.time() < end:
        if time.time() - chosen_at > 90:
            events = []
            for lg in leagues:
                ev = paced(f'{API}/catalog/events?status=OPEN_INGAME&league={lg}&limit=50')
                events += (ev or {}).get('items', [])
            if not events:
                if not a.wait:
                    print('no live', a.league, 'game; use --wait to wait for a kickoff', file=sys.stderr)
                    return
                time.sleep(20)
                continue
            chosen = []
            for e in events:
                ms = paced(f'{API}/catalog/markets?event={e["eventId"]}&marketType=MONEY,SPREAD,TOTAL&limit=500') or {}
                for m in ms.get('items', []):
                    markets[m['marketId']] = dict(event=e['eventId'], ename=e['description'], start=e['startsTs'], type=m['marketType'], strike=m['strike'],
                                                  outs=[[o['outcomeId'], o['name']] for o in m['outcomes']])
                    b = paced(f'{API}/catalog/markets/{m["marketId"]}/book')
                    if not b or len(m['outcomes']) != 2:
                        continue
                    o = b.get('orders', {})
                    ids = [x['outcomeId'] for x in m['outcomes']]
                    if not o.get(ids[0]) or not o.get(ids[1]):
                        continue
                    pa, pb = float(o[ids[0]][0]['price']), float(o[ids[1]][0]['price'])
                    if 0.08 <= pa <= 0.92 and (1 - pa - pb) < 0.10:
                        chosen.append(m['marketId'])
            chosen_at = time.time()
            for mid in chosen:
                if mid not in meta_done:
                    out.write(json.dumps(dict(k='meta', m=mid, **markets[mid])) + '\n'); meta_done.add(mid)
            out.flush()
            print(time.strftime('%H:%M:%S'), len(events), 'live events,', len(chosen), 'near-the-money markets', file=sys.stderr, flush=True)
        npass += 1
        for mid in chosen:
            tr = paced(f'{API}/catalog/markets/{mid}/trades?limit=300')   # a 3 s burst is ~25 prints; 300 survives a slow pass
            for t in reversed((tr or {}).get('items', [])):
                if t['tradeId'] in seen:
                    continue
                seen.add(t['tradeId'])
                out.write(json.dumps(dict(k='t', m=mid, o=t['outcomeId'], p=float(t['price']), q=float(t['qty']), ts=t['ts'], id=t['tradeId'])) + '\n')
            b = paced(f'{API}/catalog/markets/{mid}/book') if npass % 4 == 0 else None   # books every 4th pass: the tape is the point
            if b:
                ids = [x[0] for x in markets[mid]['outs']]
                top = lambda l: [[float(x['price']), float(x['qty'])] for x in l[:3]]
                out.write(json.dumps(dict(k='b', m=mid, at=int(time.time() * 1000), seq=b.get('seq'), A=top(b['orders'].get(ids[0], [])), B=top(b['orders'].get(ids[1], [])))) + '\n')
            out.flush()
    print('done', file=sys.stderr)


# ---------------------------------------------------------------------------------------------------------------- analyze
def parse_market(meta):
    names = [o[1] for o in meta['outs']]
    ids = [o[0] for o in meta['outs']]
    if meta['type'] == 'TOTAL':
        io = [k for k, n in enumerate(names) if n.startswith('Over')]
        return None if not io else dict(yes=ids[io[0]], thr=float(meta['strike']), ladder='T', label='Total %s' % meta['strike'])
    if meta['type'] == 'MONEY':
        teams, vals = names, [0.0, 0.0]
    else:
        teams, vals = [], []
        for n in names:
            mm = re.match(r'(.+?)\s+([+-]?\d+(?:\.\d+)?)$', n.strip())
            if not mm:
                return None
            teams.append(mm.group(1)); vals.append(float(mm.group(2)))
    ref = sorted(teams)[0]
    ir = teams.index(ref)
    lab = ('ML ' + ref) if meta['type'] == 'MONEY' else 'Spr %s %+g' % (ref, vals[ir])
    return dict(yes=ids[ir], thr=-vals[ir], ladder='M', label=lab)


def load(paths):
    meta, prints = {}, []
    seen = set()
    for p in paths:
        for line in open(p):
            r = json.loads(line)
            if r['k'] == 'meta':
                meta[r['m']] = r
            elif r['k'] == 't' and r['id'] not in seen:
                seen.add(r['id']); prints.append(r)
    ladders = collections.defaultdict(list)
    info = {m: parse_market(v) for m, v in meta.items()}
    for t in prints:
        pm = info.get(t['m'])
        if not pm or t['ts'] / 1000 < meta[t['m']]['start'] / 1000 + 60:
            continue
        # the public route's outcome and price are the RESTING order's: the taker bought the other outcome at 1 - price
        kind = 'N' if t['o'] == pm['yes'] else 'Y'
        ladders[(meta[t['m']]['event'], pm['ladder'])].append(dict(ts=t['ts'], thr=pm['thr'], kind=kind, p=1 - t['p'], q=t['q'], m=t['m'], id=t['id'], label=pm['label']))
    for k in ladders:
        ladders[k].sort(key=lambda x: x['ts'])
    names = {v['event']: v['ename'] for v in meta.values()}
    return ladders, names


def find_pairs(L):
    ts = np.array([x['ts'] for x in L]); out = []
    for a in L:
        if a['kind'] != 'Y':
            continue
        lo = np.searchsorted(ts, a['ts'] - WINDOW_MS); hi = np.searchsorted(ts, a['ts'] + WINDOW_MS, side='right')
        for b in L[lo:hi]:
            if b['kind'] == 'N' and b['thr'] > a['thr'] and a['p'] + b['p'] < 1:
                net = 1 - a['p'] - b['p'] - fee(a['p']) - fee(b['p'])
                if net > 0:
                    out.append((a, b, net))
    return out


def bursts_of(L, gap_ms=10000):
    pairs = sorted(find_pairs(L), key=lambda x: min(x[0]['ts'], x[1]['ts']))
    res, cur = [], []
    for pr in pairs:
        t = min(pr[0]['ts'], pr[1]['ts'])
        if cur and t - min(cur[-1][0]['ts'], cur[-1][1]['ts']) > gap_ms:
            res.append(cur); cur = []
        cur.append(pr)
    if cur:
        res.append(cur)
    return res


def distinct(B):
    used, out = set(), []
    for a, b, n in sorted(B, key=lambda x: -x[2]):
        if a['id'] in used or b['id'] in used:
            continue
        used.add(a['id']); used.add(b['id']); out.append((a, b, n, min(a['q'], b['q'])))
    return out


def hms(ts):
    return time.strftime('%H:%M:%S', time.gmtime(ts / 1000)) + '.%03d' % (ts % 1000)


def analyze(a):
    ladders, names = load(a.tapes)
    allb = []
    for (ev, kind), L in ladders.items():
        for B in bursts_of(L):
            allb.append((names.get(ev, ev), kind, B, L))
    allb.sort(key=lambda x: min(min(p[0]['ts'], p[1]['ts']) for p in x[2]))
    print('%d ladders, %d prints, %d bursts' % (len(ladders), sum(len(v) for v in ladders.values()), len(allb)))
    Ls = [0, 0.1, 0.2, 0.3, 0.5, 1.0, 1.5]
    floor = {L_: 0.0 for L_ in Ls}
    tot_con = 0
    for name, kind, B, L in allb:
        d = distinct(B)
        ts = [min(x[0]['ts'], x[1]['ts']) for x in B] + [max(x[0]['ts'], x[1]['ts']) for x in B]
        con = sum(x[3] for x in d); fl = sum(x[2] * x[3] / 100 for x in d)
        tot_con += con
        legs = collections.Counter(x[0]['label'] + ' YES / ' + x[1]['label'] + ' NOT' for x in d)
        print('  %s %-28s %s %s-%s %.2fs  %2d pairs %6d contracts  floor $%.2f  expected $%.2f (overlap %.0f%%)  %s' % (
            name[:10], name[:28], kind, hms(min(ts))[-12:], hms(max(ts))[-6:], (max(ts) - min(ts)) / 1000, len(d), con, fl, fl + a.overlap * con / 100, a.overlap * 100, dict(legs)))
        t0 = min(x[0]['ts'] for x in B) / 1000
        t0 = min(t0, min(x[1]['ts'] for x in B) / 1000)
        for L_ in Ls:
            used = set(); dol = 0.0
            for x, y, n in sorted(B, key=lambda z: -z[2]):
                if x['ts'] / 1000 < t0 + L_ - 1e-4 or y['ts'] / 1000 < t0 + L_ - 1e-4 or x['id'] in used or y['id'] in used:
                    continue
                used.add(x['id']); used.add(y['id']); dol += n * min(x['q'], y['q']) / 100
            floor[L_] += dol
    if allb:
        print('\nguaranteed floor kept by a reaction time of L after the first trade of each burst:')
        for L_ in Ls:
            print('  L=%.1fs  $%.2f  (%.0f%% of L=0)' % (L_, floor[L_], 100 * floor[L_] / max(floor[0], 1e-9)))
        print('adding the overlap lottery at %.0f%% on %d contracts: expected +$%.2f' % (a.overlap * 100, tot_con, a.overlap * tot_con / 100))
    # moneyline jumps: the busiest market of each margin ladder
    for (ev, kind), L in ladders.items():
        if kind != 'M':
            continue
        bym = collections.defaultdict(list)
        for x in L:
            bym[x['m']].append(x)
        R = bym[max(bym, key=lambda m: len(bym[m]))]
        t = np.array([x['ts'] for x in R]) / 1000.0
        pr = np.array([x['p'] if x['kind'] == 'Y' else 1 - x['p'] for x in R])
        cands = []
        for i in range(len(t)):
            pre = pr[(t > t[i] - 6) & (t < t[i] - 0.3)]; post = pr[(t >= t[i]) & (t < t[i] + 3)]
            if len(pre) >= 2 and len(post) >= 2 and abs(np.median(post) - np.median(pre)) >= 0.04:
                cands.append((t[i], np.median(post) - np.median(pre)))
        jumps = []
        for tt, d in sorted(cands, key=lambda c: -abs(c[1])):
            if all(abs(tt - j[0]) > 20 for j in jumps):
                jumps.append((tt, d))
        bt = [min(min(p[0]['ts'], p[1]['ts']) for p in B) / 1000 for n_, k_, B, L_ in allb if n_ == names.get(ev, ev)]
        print('\n%s: %d moneyline jumps >= 4c' % (names.get(ev, ev), len(jumps)))
        for lo, hi in ((0.04, 0.05), (0.05, 0.07), (0.07, 0.12), (0.12, 1)):
            sel = [j for j in jumps if lo <= abs(j[1]) < hi]
            print('  %2.0f-%2.0fc: %d jumps, %d with a burst within -2..+4 s' % (lo * 100, min(hi, 1) * 100, len(sel), sum(1 for j in sel if any(-2 <= b - j[0] <= 4 for b in bt))))
    # the calm cost of a cover: executed YES at the lower line + NOT at the higher one within 2 s, outside the bursts
    sums = []
    for (ev, kind), L in ladders.items():
        ts = np.array([x['ts'] for x in L])
        bw = [(min(min(p[0]['ts'], p[1]['ts']) for p in B) - 3000, max(max(p[0]['ts'], p[1]['ts']) for p in B) + 3000) for B in bursts_of(L)]
        for x in L:
            if x['kind'] != 'Y' or any(lo <= x['ts'] <= hi for lo, hi in bw):
                continue
            lo_i = np.searchsorted(ts, x['ts'] - 2000); hi_i = np.searchsorted(ts, x['ts'] + 2000, side='right')
            for y in L[lo_i:hi_i]:
                if y['kind'] == 'N' and y['thr'] > x['thr'] and y['m'] != x['m']:
                    sums.append((y['thr'] - x['thr'], x['p'] + y['p']))
    if sums:
        s = np.array(sums)
        print('\ncover cost outside the bursts (n=%d): median %.3f, 5th %.3f, share under $1: %.1f%%' % (len(s), np.median(s[:, 1]), np.percentile(s[:, 1], 5), 100 * (s[:, 1] < 1).mean()))


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest='cmd', required=True)
    r = sub.add_parser('record'); r.add_argument('--league', default='NFL', help='one league or several, comma separated'); r.add_argument('--rate', type=float, default=None, help='requests a second (default 3.7)'); r.add_argument('--minutes', type=float, default=200); r.add_argument('--out', default='tape.ndjson'); r.add_argument('--wait', action='store_true')
    z = sub.add_parser('analyze'); z.add_argument('tapes', nargs='+'); z.add_argument('--overlap', type=float, default=0.03)
    a = ap.parse_args()
    record(a) if a.cmd == 'record' else analyze(a)
