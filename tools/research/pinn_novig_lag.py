#!/usr/bin/env python3
"""Pinnacle (via pinnodds /ws/feed) against Novig's public live books: does Novig lag Pinnacle's devigged fair price, by how much and for how long?
TASKS.md PW1/PW5, RESEARCH.md section 116.  Records; `analyze` reads the tape.  Orders: none (public routes only).

    PINNODDS_KEY=... python3 tools/research/pinn_novig_lag.py record --out lag.ndjson --minutes 120 [--leagues NBA,NHL,MLB,NFL,NCAAF,NCAAB,MLS,EPL,ATP,WTA]
    python3 tools/research/pinn_novig_lag.py analyze lag.ndjson [--min-ev 0.01]

ONE pinnodds socket per account: stop any other recorder first.  The key is read from the environment only and never written.
Tape lines: {"k":"pin","t":ms,...} a Pinnacle market change on a matched event; {"k":"nov","t":ms,...} a Novig book read; {"k":"score",...} a Pinnacle score/clock change;
{"k":"match",...} a Pinnacle<->Novig event match; {"k":"close",...} a Pinnacle market closing/reopening.
"""
import argparse, asyncio, collections, json, math, os, re, ssl, statistics, sys, threading, time, unicodedata, urllib.request, urllib.error

NOVIG = 'https://api.novig.com/v3/public'
PINN = 'wss://pinnodds.com/ws/feed?key='
FEE_C = 0.03
UA = {'User-Agent': 'Mozilla/5.0'}
LEAGUES = 'NBA,NHL,MLB,NFL,NCAAF,NCAAB,NCAAWB,WNBA,MLS,EPL,ATP,WTA,Bundesliga,Serie A,La Liga,Ligue 1,Champions League,Europa League'


def ctx():
    c = ssl.create_default_context()
    if os.path.exists('/root/.ccr/ca-bundle.crt'):
        c.load_verify_locations('/root/.ccr/ca-bundle.crt')
    return c


_opener = None
def http(url, etag=None, timeout=15):
    global _opener
    if _opener is None:
        px = os.environ.get('HTTPS_PROXY')
        _opener = urllib.request.build_opener(*( [urllib.request.ProxyHandler({'https': px})] if px else []), urllib.request.HTTPSHandler(context=ctx()))
    h = dict(UA)
    if etag:
        h['If-None-Match'] = etag
    try:
        r = _opener.open(urllib.request.Request(url, headers=h), timeout=timeout)
        return r.status, json.load(r), r.headers.get('ETag')
    except urllib.error.HTTPError as e:
        if e.code == 304:
            return 304, None, etag
        return e.code, None, e.headers.get('Retry-After')
    except Exception as e:   # a dropped connection, an SSL EOF, a timeout: a read that did not happen, never the end of the recording
        return 0, None, None


def norm(s):
    s = unicodedata.normalize('NFKD', s).encode('ascii', 'ignore').decode().lower()
    return re.sub(r'[^a-z0-9 ]', ' ', s)

STOP = {'fc', 'cf', 'sc', 'afc', 'the', 'de', 'club', 'ac', 'as', 'bk', 'fk', 'sk', 'ud', 'cd', 'cs', 'sv', 'united', 'city'}
def toks(s):
    return {t for t in norm(s).split() if t not in STOP and len(t) > 1}

def same_team(a, b):
    ta, tb = toks(a), toks(b)
    if not ta or not tb:
        return False
    if ta == tb or ta <= tb or tb <= ta:
        return True
    return len(ta & tb) / min(len(ta), len(tb)) >= 0.5 and bool(ta & tb)

def am2p(a):
    return 100.0 / (a + 100.0) if a > 0 else -a / (-a + 100.0)

def devig(ps):   # multiplicative; the Kotlin side uses engine Devig.POWER, compared in analyze
    s = sum(ps)
    return [p / s for p in ps]

def devig_power(ps):
    lo, hi = 0.2, 5.0
    for _ in range(60):
        k = (lo + hi) / 2
        if sum(p ** k for p in ps) > 1:
            lo = k
        else:
            hi = k
    return [p ** ((lo + hi) / 2) for p in ps]


class Pinn:
    """Pinnacle state by matchup id, fed by /ws/feed frames."""
    def __init__(self, emit):
        self.ev = {}; self.emit = emit; self.matched = {}   # pin event id -> novig event id
        self.frames = 0; self.last_frame = 0

    def meta(self, rec):
        e = self.ev.setdefault(rec['id'], {'mk': {}, 'ver': {}, 'score': None, 'clock': None})
        parts = rec.get('participants') or []
        par = (rec.get('parent') or {}).get('participants') or []
        for p in parts:
            if p.get('alignment') in ('home', 'away'):
                e[p['alignment']] = p.get('name', e.get(p['alignment']))
        e['league'] = (rec.get('league') or {}).get('name', e.get('league'))
        e['sport'] = ((rec.get('league') or {}).get('sport') or {}).get('name', e.get('sport'))
        e['start'] = rec.get('startTime', e.get('start')); e['live'] = bool(rec.get('isLive')); e['parent'] = rec.get('parentId')
        e['units'] = rec.get('units')
        sc = {}
        for src in (par, parts):   # the child's own state wins (soccer), else the parent's (basketball)
            for p in src:
                st = p.get('state') or {}
                if 'score' in st and p.get('alignment') in ('home', 'away'):
                    sc[p['alignment']] = st['score']
        clock = dict((rec.get('parent') or {}).get('state') or {}); clock.update(rec.get('state') or {})
        return e, (sc.get('home'), sc.get('away')) if sc else None, clock

    def on_live(self, m, t):
        rec = m['rec']; ch = m['topic'].split('/')[-1]
        self.frames += 1; self.last_frame = t
        if m.get('op') == 'del':
            self.ev.pop(rec.get('id'), None); return
        e, score, clock = self.meta(rec)
        if ch == 'pre' or not e['live'] or 'Corners' in (e.get('units') or '') or (e.get('units') not in (None, 'Regular')):
            return
        if score and score != e['score']:
            self.emit({'k': 'score', 't': t, 'ts': m.get('ts'), 'pid': rec['id'], 'ch': ch, 'home': e.get('home'), 'away': e.get('away'), 'league': e.get('league'),
                       'old': e['score'], 'new': score, 'clock': clock})
            e['score'] = score
        if clock != e['clock']:
            e['clock'] = clock
        for mk in rec.get('markets') or []:
            if mk.get('period') != 0 and mk.get('period') is not None:
                continue
            key = mk.get('key')
            ver = mk.get('version')
            if ver is not None and ver <= e['ver'].get(key, -1):
                continue
            if ver is not None:
                e['ver'][key] = ver
            st = mk.get('status')
            was = e['mk'].get(key, {}).get('status')
            if st is not None and st != 'open':
                if was == 'open' or was is None:
                    self.emit({'k': 'close', 't': t, 'ts': m.get('ts'), 'pid': rec['id'], 'key': key, 'ch': ch, 'status': st})
                e['mk'][key] = {'status': st}
                continue
            prices = mk.get('prices') or []
            if not prices:
                continue
            by = {}
            for p in prices:
                d = p.get('designation')
                if d:
                    by[d] = (p['price'], p.get('points'))
            if len(by) < 2:
                continue
            old = e['mk'].get(key)
            e['mk'][key] = {'status': 'open', 'by': by, 'alt': mk.get('isAlternate'), 'type': mk.get('type'), 'lim': (mk.get('limits') or [{}])[0].get('amount')}
            if not mk.get('isAlternate'):
                names = list(by.keys())
                probs = [am2p(by[n][0]) for n in names]
                self.emit({'k': 'pin', 't': t, 'ts': m.get('ts'), 'pid': rec['id'], 'ch': ch, 'key': key, 'type': mk.get('type'), 'alt': bool(mk.get('isAlternate')),
                           'by': {n: [by[n][0], by[n][1]] for n in names}, 'fair': dict(zip(names, devig(probs))), 'fairp': dict(zip(names, devig_power(probs))),
                           'vig': sum(probs) - 1, 'lim': e['mk'][key]['lim'], 'home': e.get('home'), 'away': e.get('away'), 'league': e.get('league'),
                           'sport': e.get('sport'), 'score': e['score'], 'clock': clock, 'ver': ver})


class Out:
    def __init__(self, path):
        self.f = open(path, 'a'); self.lock = threading.Lock(); self.n = 0
    def __call__(self, d):
        with self.lock:
            self.f.write(json.dumps(d, separators=(',', ':')) + '\n'); self.n += 1
            if self.n % 50 == 0:
                self.f.flush()


async def pinn_task(pn, key, stop, sports):
    import websockets
    backoff = 1
    while not stop.is_set():
        try:
            async with websockets.connect(PINN + key, ssl=ctx(), proxy=os.environ.get('HTTPS_PROXY') or None, open_timeout=20, max_size=8 << 20, compression=None) as ws:
                await ws.send(json.dumps({'type': 'subscribe', 'streams': ['live'], 'sport_ids': sports}))
                backoff = 1
                while not stop.is_set():
                    raw = await asyncio.wait_for(ws.recv(), timeout=90)
                    t = int(time.time() * 1000); m = json.loads(raw); ty = m.get('type')
                    if ty == 'ping':
                        await ws.send('{"type":"pong"}')
                    elif ty == 'snapshot':
                        for rec in m['events']:
                            pn.on_live({'rec': rec, 'topic': 'snap/ld', 'op': 'upd', 'ts': m.get('ts')}, t)
                    elif ty == 'live':
                        pn.on_live(m, t)
        except Exception as ex:
            print('pinn socket:', type(ex).__name__, ex, file=sys.stderr)
            await asyncio.sleep(backoff); backoff = min(30, backoff * 2)


class Novig:
    def __init__(self, pn, out, leagues):
        self.pn = pn; self.out = out; self.leagues = leagues; self.targets = {}   # marketId -> info
        self.etag = {}; self.hot = {}; self.stop = False; self.reqs = 0; self.r429 = 0

    def refresh(self):
        evs = []
        for lg in self.leagues:
            st, b, _ = http(f'{NOVIG}/catalog/events?status=OPEN_INGAME&league={urllib.request.quote(lg)}&limit=100')
            if st == 200:
                evs += b.get('items', [])
        pin_live = {pid: e for pid, e in self.pn.ev.items() if e.get('live') and e.get('home') and e.get('away')}
        targets = {}
        for ne in evs:
            d = ne.get('description') or ''
            if ' @ ' not in d:
                continue
            na, nh = d.split(' @ ', 1)
            for pid, e in pin_live.items():
                if same_team(nh, e['home']) and same_team(na, e['away']):
                    if self.pn.matched.get(pid) != ne['eventId']:
                        self.pn.matched[pid] = ne['eventId']
                        self.out({'k': 'match', 't': int(time.time() * 1000), 'pid': pid, 'nid': ne['eventId'], 'desc': d, 'ph': e['home'], 'pa': e['away'], 'league': ne['league']})
                    st, b, _ = http(f"{NOVIG}/catalog/markets?event={ne['eventId']}&marketType=MONEY,SPREAD,TOTAL&limit=200")
                    if st != 200:
                        continue
                    pmk = e['mk']
                    for m in b.get('items', []):
                        if m.get('status') not in ('OPEN', 'OPEN_INGAME', None) and False:
                            continue
                        mt = m['marketType']; strike = m.get('strike')
                        keep = False
                        if mt == 'MONEY':
                            keep = True
                        else:
                            for k, v in pmk.items():
                                if v.get('status') == 'open' and not v.get('alt') and v.get('by'):
                                    if mt == 'TOTAL' and v['type'] == 'total':
                                        pts = next(iter(v['by'].values()))[1]
                                        keep = keep or (strike is not None and abs(float(strike) - float(pts)) < 1e-9)
                                    if mt == 'SPREAD' and v['type'] == 'spread' and 'home' in v['by']:
                                        keep = keep or (strike is not None and abs(float(strike) - float(v['by']['home'][1])) < 1e-9)
                        if keep:
                            if m['marketId'] not in self.targets:
                                self.out({'k': 'mkt', 't': int(time.time() * 1000), 'mid': m['marketId'], 'pid': pid, 'nid': ne['eventId'], 'mt': mt, 'strike': strike, 'desc': m.get('description'),
                                          'outs': [o['name'] for o in m['outcomes']], 'fee': m.get('fee'), 'status': m.get('status')})
                            targets[m['marketId']] = {'pid': pid, 'nid': ne['eventId'], 'mt': mt, 'strike': strike, 'outs': [(o['outcomeId'], o['name']) for o in m['outcomes']], 'fee': m.get('fee')}
                    break
        self.targets = targets
        return len(evs), len(targets)

    def read(self, mid):
        info = self.targets[mid]
        t0 = time.time()
        st, b, tag = http(f'{NOVIG}/catalog/markets/{mid}/book', etag=self.etag.get(mid))
        t1 = time.time(); self.reqs += 1
        if st == 429:
            self.r429 += 1; time.sleep(float(tag or 1)); return
        if st == 304 or st != 200:
            return
        self.etag[mid] = tag
        bb = {}
        for oid, name in info['outs']:
            lv = (b.get('orders') or {}).get(oid) or []
            if lv:
                best = max(float(x['price']) for x in lv)
                bb[name] = [best, sum(int(x['qty']) for x in lv if abs(float(x['price']) - best) < 1e-9)]
            else:
                bb[name] = None
        self.out({'k': 'nov', 't': int((t0 + t1) / 2 * 1000), 'rtt': int((t1 - t0) * 1000), 'mid': mid, 'pid': info['pid'], 'mt': info['mt'], 'strike': info['strike'], 'seq': b.get('seq'), 'bb': bb})

    def loop(self, until):
        last_refresh = 0; i = 0; slow = {}
        while time.time() < until and not self.stop:
            if time.time() - last_refresh > 40:
                try:
                    n, m = self.refresh(); print(f'[{time.strftime("%H:%M:%S")}] novig live events {n}, targets {m}, pinn frames {self.pn.frames}, novig reqs {self.reqs} (429s {self.r429}), tape lines {self.out.n}', flush=True)
                except Exception as ex:
                    print('refresh failed', ex, file=sys.stderr)
                last_refresh = time.time()
            ids = list(self.targets)
            if not ids:
                time.sleep(1); continue
            now = time.time()
            # hot markets: their game's Pinnacle price moved in the last 20 s -> every pass; the rest every 6th pass
            hot = [m for m in ids if now - self.hot.get(self.targets[m]['pid'], 0) < 20]
            cold = [m for m in ids if m not in hot]
            order = hot + ([cold[i % len(cold)]] if cold and (i % 2 == 0) else [])
            for mid in order:
                if mid in self.targets:
                    self.read(mid); time.sleep(0.16)
            i += 1
            time.sleep(0.05)


def record(a):
    key = os.environ.get('PINNODDS_KEY', '')
    if not key:
        sys.exit('set PINNODDS_KEY')
    out = Out(a.out); holder = {}
    def emit(d):
        out(d)
        if d['k'] == 'pin' and 'nv' in holder:
            holder['nv'].hot[d['pid']] = time.time()
    pn = Pinn(emit); nv = Novig(pn, out, a.leagues.split(',')); holder['nv'] = nv
    stop = asyncio.Event(); loop = asyncio.new_event_loop()
    th = threading.Thread(target=lambda: loop.run_until_complete(pinn_task(pn, key, stop, [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13])), daemon=True); th.start()
    time.sleep(8)   # the snapshot
    try:
        nv.loop(time.time() + a.minutes * 60)
    finally:
        loop.call_soon_threadsafe(stop.set); out.f.flush(); out.f.close()


def load(path):
    pin = collections.defaultdict(list); nov = collections.defaultdict(list); mkt = {}; match = {}; scores = collections.defaultdict(list); closes = []
    for line in open(path):
        try:
            d = json.loads(line)
        except Exception:
            continue
        k = d['k']
        if k == 'pin': pin[(d['pid'], d['key'])].append(d)
        elif k == 'nov': nov[d['mid']].append(d)
        elif k == 'mkt': mkt[d['mid']] = d
        elif k == 'match': match[d['pid']] = d
        elif k == 'score': scores[d['pid']].append(d)
        elif k == 'close': closes.append(d)
    return pin, nov, mkt, match, scores, closes


def pin_series(pin, m):
    """The Pinnacle devigged series that corresponds to Novig market m (a 'mkt' record): list of (t, {side: fair}, record), sides home/away or over/under."""
    pid, mt, strike = m['pid'], m['mt'], m.get('strike')
    out = []
    for (p, key), v in pin.items():
        if p != pid: continue
        if mt == 'MONEY' and key == 's;0;m': out += v
        elif mt == 'TOTAL' and key.startswith('s;0;ou') and v and v[0]['by'].get('over'):
            out += [x for x in v if abs(float(x['by']['over'][1]) - float(strike)) < 1e-9]
        elif mt == 'SPREAD' and key.startswith('s;0;s;') and v and v[0]['by'].get('home'):
            out += [x for x in v if abs(float(x['by']['home'][1]) - float(strike)) < 1e-9]
    out.sort(key=lambda x: x['t'])
    return out


def side_map(m):
    """Novig outcome name -> 'home'|'away'|'over'|'under'."""
    outs = m['outs']; mt = m['mt']
    if mt == 'TOTAL':
        return {o: ('over' if o.lower().startswith('over') else 'under') for o in outs}
    if mt == 'MONEY':
        home = m['desc'].split('@')[-1].strip()   # a MONEY market's description is the HOME side's abbreviation
        return {o: ('home' if o == home else 'away') for o in outs}
    home = m['desc'].split()[0]
    return {o: ('home' if o.startswith(home + ' ') else 'away') for o in outs}


def fee_of(p, live=True):
    return FEE_C * p * (1 - p) if live else 0.0


def analyze(a):
    pin, nov, mkt, match, scores, closes = load(a.path)
    span = [d['t'] for v in nov.values() for d in v]
    print(f'games matched {len(match)}; pinnacle series {len(pin)}; novig markets {len(nov)} ({len(mkt)} described); pinnacle score changes {sum(len(v) for v in scores.values())}; closes/reopens {len(closes)}')
    if span: print(f'novig reads {sum(len(v) for v in nov.values())} over {(max(span)-min(span))/60000:.0f} min')
    rows = []   # one row per (novig read, outcome)
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m: continue
        series = pin_series(pin, m); sm = side_map(m)
        if not series: continue
        ts = [x['t'] for x in series]
        import bisect
        for o in obs:
            bb = o['bb']
            if len(bb) != 2 or any(v is None for v in bb.values()): continue
            k = bisect.bisect_right(ts, o['t']) - 1
            if k < 0: continue
            pr = series[k]; age = (o['t'] - pr['t']) / 1000.0
            names = list(bb.keys())
            for x in names:
                side = sm.get(x)
                if side not in pr['fairp']: continue
                other = [n for n in names if n != x][0]
                ask = 1.0 - bb[other][0]; depth = bb[other][1]
                fair = pr['fairp'][side]; fee = fee_of(ask)
                rows.append({'mid': mid, 'mt': m['mt'], 't': o['t'], 'x': x, 'side': side, 'ask': ask, 'depth': depth, 'fair': fair, 'age': age, 'ev': fair / (ask + fee) - 1.0,
                             'fairm': pr['fair'][side], 'pt': pr['t'], 'pid': m['pid'], 'k': k, 'n': len(series), 'bid_x': bb[x][0], 'league': match[m['pid']]['league'] if m['pid'] in match else ''})
    print(f'paired (read x outcome) rows: {len(rows)}')
    if not rows: return
    # 1. how often is there an edge, by size, and how stale was the Pinnacle price
    print('\n[1] EV of taking Novig at its ask against Pinnacle power-devigged fair, fee in (rows = Novig reads x outcomes; fresh = Pinnacle changed <= 10 s before the read)')
    for lo in (0.0, 0.01, 0.02, 0.03, 0.05, 0.10):
        sel = [r for r in rows if r['ev'] >= lo]; fr = [r for r in sel if r['age'] <= 10]
        print(f'  ev >= {lo:4.0%}: {len(sel):6d} rows ({len(sel)/len(rows):5.1%}), fresh {len(fr):5d}, median depth {statistics.median([r["depth"] for r in sel]) if sel else 0:8.0f} contracts')
    # 2. is the edge a lag (it appears right after a Pinnacle move and decays) or a standing disagreement?
    print('\n[2] by seconds since Pinnacle last changed that line: share of rows with ev >= 2%, and the median ev')
    for lo, hi in ((0, 2), (2, 5), (5, 10), (10, 20), (20, 60), (60, 1e9)):
        sel = [r for r in rows if lo <= r['age'] < hi]
        if sel: print(f'  {lo:3.0f}-{hi:4.0f}s: n={len(sel):6d}  ev>=2%: {sum(r["ev"]>=0.02 for r in sel)/len(sel):6.1%}  median ev {statistics.median([r["ev"] for r in sel]):+.3%}')
    # 3. lead-lag after a Pinnacle jump (fair moved >= 3 points in one change): how long until Novig's mid has moved most of the way?
    print('\n[3] after a Pinnacle jump of >= 3 fair points (moneyline): seconds until Novig\'s mid covers 50% / 90% of the gap')
    gaps = []
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['mt'] != 'MONEY': continue
        series = pin_series(pin, m); sm = side_map(m); home_name = [n for n, s in sm.items() if s == 'home']
        if not series or not home_name: continue
        hn = home_name[0]; an = [n for n in sm if n != hn][0]
        mids = []
        for o in obs:
            bb = o['bb']
            if bb.get(hn) is None or bb.get(an) is None: continue
            mids.append((o['t'], (bb[hn][0] + (1 - bb[an][0])) / 2.0))
        if len(mids) < 5: continue
        for a_, b_ in zip(series, series[1:]):
            d = b_['fairp']['home'] - a_['fairp']['home']
            if abs(d) < 0.03: continue
            before = [v for t, v in mids if t <= b_['t']]
            if not before: continue
            start = before[-1]; target = b_['fairp']['home']; gap = target - start
            if abs(gap) < 0.02: continue   # novig had already moved (or Pinnacle moved toward it)
            t50 = t90 = None
            for t, v in mids:
                if t < b_['t']: continue
                if t - b_['t'] > 120000: break
                frac = (v - start) / gap
                if t50 is None and frac >= 0.5: t50 = (t - b_['t']) / 1000
                if t90 is None and frac >= 0.9: t90 = (t - b_['t']) / 1000; break
            gaps.append((pin_age_label(b_), gap, t50, t90))
    if gaps:
        t50s = [g[2] for g in gaps if g[2] is not None]; t90s = [g[3] for g in gaps if g[3] is not None]
        print(f'  jumps with a novig gap >= 2 points: {len(gaps)}; novig covered 50% within 120 s in {len(t50s)}, 90% in {len(t90s)}')
        if t50s: print(f'  t50: median {statistics.median(t50s):.1f}s  p25 {sorted(t50s)[len(t50s)//4]:.1f}s  p75 {sorted(t50s)[3*len(t50s)//4]:.1f}s')
        if t90s: print(f'  t90: median {statistics.median(t90s):.1f}s')
    else:
        print('  none yet')
    return rows


def pin_age_label(p):
    return p['t']


def simulate(a):
    """Replays the app's rule (LiveEdge) over a recorded tape: when would it have bet, and did Pinnacle keep the move?
    Rule: Pinnacle's fair for the side rose >= min-move within 20 s, the price has sat >= 0.5 s, EV after Novig's fee >= min-ev at the ask, fair in [0.08, 0.92], overround <= 9%,
    >= 20 contracts at the ask; one bet per Pinnacle change per outcome.  Then: Pinnacle's fair 30 s and 120 s later, and Novig's ask 30 s and 120 s later."""
    import bisect
    pin, nov, mkt, match, scores, closes = load(a.path)
    fired = []; standing = []
    score_ts = {pid: sorted(x['t'] for x in v if x.get('old') is not None) for pid, v in scores.items()}
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m: continue
        series = pin_series(pin, m); sm = side_map(m)
        if not series: continue
        ts = [x['t'] for x in series]
        obs = sorted(obs, key=lambda o: o['t'])
        ot = [o['t'] for o in obs]
        done = set()
        for idx, o in enumerate(obs):
            bb = o['bb']
            if len(bb) != 2 or any(v is None for v in bb.values()): continue
            k = bisect.bisect_right(ts, o['t']) - 1
            if k < 0: continue
            pr = series[k]
            if o['t'] - pr['t'] < a.settle: continue
            if pr['vig'] > 0.09: continue
            if pr.get('lim') is not None and pr['lim'] < 100: continue
            names = list(bb.keys())
            for x in names:
                side = sm.get(x)
                if side not in pr['fairp']: continue
                fair = pr['fairp'][side]
                if not (0.08 <= fair <= 0.92): continue
                other = [n for n in names if n != x][0]
                ask = 1.0 - bb[other][0]; depth = bb[other][1]
                if depth < 20: continue
                ev = fair / (ask + fee_of(ask)) - 1.0
                if ev < a.min_ev: continue
                # Pinnacle's fair for the side 20 s before
                k0 = bisect.bisect_right(ts, o['t'] - 20000) - 1
                ref = series[max(0, k0)]
                move = fair - ref['fairp'][side]
                key = (mid, side, pr['t'])
                st = score_ts.get(m['pid'], [])
                si = bisect.bisect_right(st, o['t']) - 1
                recent_score = si >= 0 and o['t'] - st[si] <= 15000
                rec = {'score': recent_score, 't': o['t'], 'mid': mid, 'league': match[m['pid']]['league'] if m['pid'] in match else '', 'sel': x, 'ask': ask, 'fair': fair, 'ev': ev, 'move': move, 'depth': depth}
                for sec in (30, 120):
                    kk = bisect.bisect_right(ts, o['t'] + sec * 1000) - 1
                    if series[kk]['t'] + 0 < o['t'] - 1: kk = k
                    later = series[kk]['fairp'].get(side)
                    rec[f'fair{sec}'] = later
                    j = bisect.bisect_left(ot, o['t'] + sec * 1000)
                    if j < len(obs) and obs[j]['bb'].get(other) is not None:
                        rec[f'ask{sec}'] = 1.0 - obs[j]['bb'][other][0]
                    else:
                        rec[f'ask{sec}'] = None
                if move >= a.min_move:
                    if key in done: continue
                    done.add(key); fired.append(rec)
                else:
                    if (mid, side, 'standing') in done and o['t'] - 0 < 0: continue
                    standing.append(rec)
    def summ(name, l):
        if not l:
            print(f'{name}: none'); return
        def mean(xs): xs = [x for x in xs if x is not None]; return sum(xs) / len(xs) if xs else None
        n = len(l)
        ev30 = [r['fair30'] / (r['ask'] + fee_of(r['ask'])) - 1 for r in l if r['fair30']]
        ev120 = [r['fair120'] / (r['ask'] + fee_of(r['ask'])) - 1 for r in l if r['fair120']]
        held = [r['fair120'] >= r['fair'] - 0.01 for r in l if r['fair120']]
        fol = [r['ask120'] > r['ask'] + 0.004 for r in l if r['ask120'] is not None]
        f = lambda x: 'n/a' if x is None else f'{x:+.2%}'
        print(f'{name}: n={n}  EV at decision {mean([r["ev"] for r in l]):+.2%}  same ask vs Pinnacle fair +30s {f(mean(ev30))} (n={len(ev30)})  +120s {f(mean(ev120))} (n={len(ev120)})  Pinnacle kept the move {sum(held)/len(held) if held else float("nan"):.0%}  Novig followed by +120s {sum(fol)/len(fol) if fol else float("nan"):.0%}  median depth {statistics.median([r["depth"] for r in l]):.0f}')
    span = [o['t'] for v in nov.values() for o in v]
    hours = (max(span) - min(span)) / 3.6e6 if span else 0
    print(f'tape: {hours:.2f} h, {len(match)} games')
    summ('THE RULE (move + EV)', fired)
    summ('  ... with a score change in the last 15 s', [r for r in fired if r['score']])
    summ('  ... with no score change (a price move alone)', [r for r in fired if not r['score']])
    # collapse the "standing" rows to one per (market, side, pinnacle change) so repeated reads of the same quote are not counted many times
    uniq = {}
    for r in standing: uniq.setdefault((r['mid'], r['sel'], round(r['fair'], 3)), r)
    summ('STANDING disagreements (EV, no recent move; one per quote)', list(uniq.values()))
    if getattr(a, 'dump', None):
        json.dump({'fired': fired, 'standing': list(uniq.values())}, open(a.dump, 'w'))
    if fired:
        print(f'the rule fires about {len(fired)/hours:.1f} times an hour on this slate' if hours else '')
        bl = collections.Counter(r['league'] for r in fired)
        print('by league:', dict(bl.most_common(6)))
        for r in sorted(fired, key=lambda r: -r['ev'])[:8]:
            print(f"  t={time.strftime('%H:%M:%S', time.gmtime(r['t']/1000))} {r['league']:5s} {r['sel']:>12s} ask {r['ask']:.3f} fair {r['fair']:.3f} EV {r['ev']:+.1%} move {r['move']:+.3f} depth {r['depth']:.0f} -> fair+120s {r['fair120'] if r['fair120'] is None else round(r['fair120'],3)} ask+120s {r['ask120'] if r['ask120'] is None else round(r['ask120'],3)}")


def scorelag(a):
    """Pinnacle's score frame against its first moneyline price change: is a score an earlier signal than the price?"""
    import bisect
    pin, nov, mkt, match, scores, closes = load(a.path)
    deltas = []; jumps = []
    for pid, sc in scores.items():
        ser = pin.get((pid, 's;0;m'))
        if not ser: continue
        ts = [x['t'] for x in ser]
        for s_ in sc:
            if s_.get('old') is None: continue
            k = bisect.bisect_left(ts, s_['t'] - 50)
            nxt = [x for x in ser[k:k + 6] if x['t'] >= s_['t'] - 50]
            if not nxt: continue
            deltas.append(nxt[0]['t'] - s_['t'])
            prev = ser[k - 1] if k > 0 else None
            after = [x for x in ser[k:] if x['t'] - s_['t'] <= 10000]
            if prev and after: jumps.append(abs(after[-1]['fairp']['home'] - prev['fairp']['home']))
    if not deltas:
        print('no score changes with a following reprice'); return
    deltas.sort(); jumps.sort(); q = lambda l, f: l[min(len(l) - 1, int(len(l) * f))]
    print(f'score frame -> first Pinnacle moneyline price change: n={len(deltas)}  median {q(deltas,.5)} ms  p25 {q(deltas,.25)}  p75 {q(deltas,.75)}  p90 {q(deltas,.9)}')
    print(f'same frame (<= 50 ms): {sum(1 for d in deltas if d <= 50)} of {len(deltas)}')
    if jumps: print(f'fair move within 10 s of a score: median {q(jumps,.5):.3f}  p75 {q(jumps,.75):.3f}  max {jumps[-1]:.3f}  (>= 1.5 pts in {sum(1 for j in jumps if j >= 0.015)} of {len(jumps)})')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); sp = ap.add_subparsers(dest='cmd', required=True)
    r = sp.add_parser('record'); r.add_argument('--out', required=True); r.add_argument('--minutes', type=float, default=60); r.add_argument('--leagues', default=LEAGUES)
    z = sp.add_parser('analyze'); z.add_argument('path'); z.add_argument('--min-ev', type=float, default=0.01)
    w = sp.add_parser('scorelag'); w.add_argument('path')
    y = sp.add_parser('simulate'); y.add_argument('path'); y.add_argument('--min-ev', type=float, default=0.03); y.add_argument('--min-move', type=float, default=0.015); y.add_argument('--settle', type=int, default=500); y.add_argument('--dump')
    a = ap.parse_args()
    {'record': record, 'analyze': analyze, 'simulate': simulate, 'scorelag': scorelag}[a.cmd](a)
