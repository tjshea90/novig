#!/usr/bin/env python3
"""Pinnacle PREMATCH moves (pinnodds /ws/feed `pre` frames) against Novig's OPEN_PREGAME books: when Pinnacle's devigged price moves, does Novig's ask lag it, and does the move hold?
TASKS.md PX3, RESEARCH.md section 117.  Records; `analyze` reads the tape.  Orders: none (public routes only).  Tape lines are the ones pinn_novig_lag.py writes (k=pin/nov/mkt/match).

    PINNODDS_KEY=... python3 tools/research/pinn_pregame.py record --out pre.ndjson --minutes 60 --hours-ahead 30
    python3 tools/research/pinn_pregame.py analyze pre.ndjson

ONE pinnodds socket per account (a second connection evicts the first): this recorder checks /health first and refuses to start if anything is connected, and EXITS (never reconnects) when it is
evicted (close 1001 / 1008 / 4xxx) so it can never fight Tj's phone for the socket.  The key is read from the environment only and is never written.
"""
import argparse, asyncio, bisect, collections, json, os, statistics, sys, threading, time, urllib.parse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pinn_novig_lag as L

NOVIG = L.NOVIG


class PrePinn(L.Pinn):
    """Pinnacle prematch state by matchup id: only non-live, regular, main matchups (type 'matchup'); the `pre` channel and the snapshot's rows."""

    def on_live(self, m, t):
        rec = m['rec']
        self.frames += 1; self.last_frame = t
        if m.get('op') == 'del':
            self.ev.pop(rec.get('id'), None); return
        if rec.get('type') != 'matchup':
            return   # specials/props use an older shape and are not read
        e, _score, _clock = self.meta(rec)
        if e['live'] or e.get('units') not in (None, 'Regular'):
            self.ev.pop(rec.get('id'), None); return
        for mk in rec.get('markets') or []:
            if mk.get('period') not in (0, None):
                continue
            key = mk.get('key'); ver = mk.get('version')
            if ver is not None and ver <= e['ver'].get(key, -1):
                continue
            if ver is not None:
                e['ver'][key] = ver
            st = mk.get('status')
            if st is not None and st != 'open':
                e['mk'][key] = {'status': st}
                self.emit({'k': 'close', 't': t, 'ts': m.get('ts'), 'pid': rec['id'], 'key': key, 'ch': 'pre', 'status': st})
                continue
            by = {p['designation']: (p['price'], p.get('points')) for p in (mk.get('prices') or []) if p.get('designation')}
            if len(by) < 2:
                continue
            e['mk'][key] = {'status': 'open', 'by': by, 'alt': mk.get('isAlternate'), 'type': mk.get('type'), 'lim': (mk.get('limits') or [{}])[0].get('amount')}
            if mk.get('isAlternate'):
                continue
            names = list(by.keys()); probs = [L.am2p(by[n][0]) for n in names]
            self.emit({'k': 'pin', 't': t, 'ts': m.get('ts'), 'pid': rec['id'], 'ch': 'pre', 'key': key, 'type': mk.get('type'), 'alt': False,
                       'by': {n: [by[n][0], by[n][1]] for n in names}, 'fair': dict(zip(names, L.devig(probs))), 'fairp': dict(zip(names, L.devig_power(probs))),
                       'vig': sum(probs) - 1, 'lim': e['mk'][key]['lim'], 'home': e.get('home'), 'away': e.get('away'), 'league': e.get('league'), 'sport': e.get('sport'),
                       'start': e.get('start'), 'ver': ver})


def start_ms(iso):
    try:
        import datetime
        return int(datetime.datetime.fromisoformat(iso.replace('Z', '+00:00')).timestamp() * 1000)
    except Exception:
        return 0


def health_clients(key):
    import urllib.request
    try:
        r = L._opener.open(urllib.request.Request('https://pinnodds.com/health', headers={'x-api-key': key, **L.UA}), timeout=15) if L._opener else None
        if r is None:
            L.http('https://example.invalid')   # builds the opener
            r = L._opener.open(urllib.request.Request('https://pinnodds.com/health', headers={'x-api-key': key, **L.UA}), timeout=15)
        return json.load(r).get('connected_clients')
    except Exception as ex:
        print('health check failed:', type(ex).__name__, file=sys.stderr)
        return None


async def pinn_task(pn, key, stop, sports, state):
    import websockets
    fails = 0
    while not stop.is_set():
        try:
            async with websockets.connect(L.PINN + key, ssl=L.ctx(), proxy=os.environ.get('HTTPS_PROXY') or None, open_timeout=20, max_size=8 << 20, compression=None) as ws:
                await ws.send(json.dumps({'type': 'subscribe', 'streams': ['live'], 'sport_ids': sports}))
                fails = 0
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
            code = getattr(getattr(ex, 'rcvd', None), 'code', None)
            print('pinn socket:', type(ex).__name__, code, str(ex)[:120], file=sys.stderr, flush=True)
            if code in (1001, 1008) or (code or 0) >= 4000 or 'evicted' in str(ex).lower():
                state['evicted'] = True; stop.set(); return   # someone else (Tj's phone) took the socket: step aside, never reconnect
            fails += 1
            if fails >= 4:
                state['evicted'] = True; stop.set(); return
            await asyncio.sleep(min(30, 2 ** fails))


class PreNovig:
    def __init__(self, pn, out, leagues, hours_ahead, state):
        self.pn = pn; self.out = out; self.leagues = leagues; self.ahead = hours_ahead * 3_600_000; self.state = state
        self.targets = {}; self.etag = {}; self.hot = {}; self.reqs = 0; self.r429 = 0; self.matched = {}

    def refresh(self):
        now = int(time.time() * 1000)
        evs = []
        for lg in self.leagues:
            st, b, _ = L.http(f'{NOVIG}/catalog/events?status=OPEN_PREGAME&league={urllib.parse.quote(lg)}&limit=100')
            if st == 200:
                evs += [e for e in b.get('items', []) if 0 < e.get('startsTs', 0) - now < self.ahead]
            time.sleep(0.3)
        pins = {pid: e for pid, e in self.pn.ev.items() if e.get('home') and e.get('away') and e['mk']}
        targets = {}
        for ne in evs:
            d = ne.get('description') or ''
            if ' @ ' not in d:
                continue
            na, nh = d.split(' @ ', 1)
            for pid, e in pins.items():
                if abs(start_ms(e.get('start') or '') - ne['startsTs']) > 5 * 3_600_000:
                    continue
                if L.same_team(nh, e['home']) and L.same_team(na, e['away']):
                    if self.matched.get(pid) != ne['eventId']:
                        self.matched[pid] = ne['eventId']
                        self.out({'k': 'match', 't': int(time.time() * 1000), 'pid': pid, 'nid': ne['eventId'], 'desc': d, 'ph': e['home'], 'pa': e['away'], 'league': ne['league'], 'starts': ne['startsTs']})
                    st, b, _ = L.http(f"{NOVIG}/catalog/markets?event={ne['eventId']}&marketType=MONEY,SPREAD,TOTAL&limit=200")
                    time.sleep(0.3)
                    if st != 200:
                        continue
                    for m in b.get('items', []):
                        mt = m['marketType']; strike = m.get('strike'); keep = mt == 'MONEY'
                        if not keep:
                            for v in e['mk'].values():
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

    read = L.Novig.read

    def loop(self, until):
        last_refresh = 0; i = 0
        while time.time() < until and not self.state.get('evicted'):
            if time.time() - last_refresh > 90:
                try:
                    n, m = self.refresh()
                    print(f'[{time.strftime("%H:%M:%S")}] novig pregame events {n}, targets {m}, pinn frames {self.pn.frames}, pinn pregame events {len(self.pn.ev)}, novig reqs {self.reqs} (429s {self.r429}), tape lines {self.out.n}', flush=True)
                except Exception as ex:
                    print('refresh failed', ex, file=sys.stderr)
                last_refresh = time.time()
            ids = list(self.targets)
            if not ids:
                time.sleep(1); continue
            now = time.time()
            hot = [m for m in ids if now - self.hot.get(self.targets[m]['pid'], 0) < 90][:30]
            cold = [m for m in ids if m not in hot]
            order = hot + ([cold[i % len(cold)], cold[(i + 7) % len(cold)]] if cold else [])
            for mid in order:
                if mid in self.targets:
                    self.read(mid); time.sleep(0.25)
            i += 1
            time.sleep(0.05)


def record(a):
    key = os.environ.get('PINNODDS_KEY', '')
    if not key:
        sys.exit('set PINNODDS_KEY')
    L.http('https://api.novig.com/v3/public/catalog/events?limit=1')   # builds the proxy-aware opener
    c = health_clients(key)
    if c != 0:
        sys.exit(f'refusing to open the Pinnodds socket: connected_clients={c} (another connection would evict it)')
    out = L.Out(a.out); state = {}; holder = {}

    def emit(d):
        out(d)
        if d['k'] == 'pin' and 'nv' in holder:
            holder['nv'].hot[d['pid']] = time.time()
    pn = PrePinn(emit); nv = PreNovig(pn, out, a.leagues.split(','), a.hours_ahead, state); holder['nv'] = nv
    stop = asyncio.Event(); loop = asyncio.new_event_loop()
    th = threading.Thread(target=lambda: loop.run_until_complete(pinn_task(pn, key, stop, list(range(1, 14)), state)), daemon=True); th.start()
    time.sleep(10)
    try:
        nv.loop(time.time() + a.minutes * 60)
    finally:
        loop.call_soon_threadsafe(stop.set); out.f.flush(); out.f.close()
        print('evicted/stopped early' if state.get('evicted') else 'done', flush=True)


def analyze(a):
    pin, nov, mkt, match, _s, closes = L.load(a.path)
    span = [d['t'] for v in nov.values() for d in v]
    n_pin = sum(len(v) for v in pin.values())
    print(f'games matched {len(match)}; pinnacle series {len(pin)} ({n_pin} changes); novig markets {len(nov)} ({len(mkt)} described); closes {len(closes)}')
    if span:
        print(f'novig reads {sum(len(v) for v in nov.values())} over {(max(span) - min(span)) / 60000:.0f} min')
    # --- standing disagreement: Novig's ask against Pinnacle's latest fair, fee-free (pregame) ---
    rows = []
    moves = []   # (market, side, t, move, ask_before, ask_after..., fair after)
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m:
            continue
        series = L.pin_series(pin, m); sm = L.side_map(m)
        if not series:
            continue
        ts = [x['t'] for x in series]
        obs = sorted(obs, key=lambda o: o['t']); ot = [o['t'] for o in obs]
        for o in obs:
            bb = o['bb']
            if len(bb) != 2 or any(v is None for v in bb.values()):
                continue
            k = bisect.bisect_right(ts, o['t']) - 1
            if k < 0:
                continue
            pr = series[k]; age = (o['t'] - pr['t']) / 1000.0
            names = list(bb.keys())
            for x in names:
                side = sm.get(x)
                if side not in pr['fairp']:
                    continue
                other = [n for n in names if n != x][0]
                ask = 1.0 - bb[other][0]; depth = bb[other][1]
                rows.append({'mid': mid, 'x': x, 'side': side, 't': o['t'], 'ask': ask, 'depth': depth, 'fair': pr['fairp'][side], 'age': age, 'ev': pr['fairp'][side] / ask - 1, 'vig': pr['vig'], 'lim': pr.get('lim'),
                             'league': match[m['pid']]['league'] if m['pid'] in match else ''})
        # Pinnacle moves on this market, and what Novig did next
        for j in range(1, len(series)):
            prev, cur = series[j - 1], series[j]
            for side in cur['fairp']:
                if side not in prev['fairp']:
                    continue
                d = cur['fairp'][side] - prev['fairp'][side]
                if abs(d) < a.min_move:
                    continue
                names = [n for n, s in sm.items() if s == side]
                if not names:
                    continue
                x = names[0]; other = [n for n in sm if n != x][0]
                # Novig's ask for x just before Pinnacle's change and at +10/30/60/180/600 s
                def ask_at(tt):
                    q = bisect.bisect_right(ot, tt) - 1
                    if q < 0 or obs[q]['bb'].get(other) is None:
                        return None
                    return 1.0 - obs[q]['bb'][other][0], tt - obs[q]['t']
                before = ask_at(cur['t'] - 1)
                if before is None or before[1] > 120000:
                    continue
                rec = {'mid': mid, 'x': x, 'side': side, 't': cur['t'], 'move': d, 'fair0': prev['fairp'][side], 'fair1': cur['fairp'][side], 'ask0': before[0], 'vig': cur['vig'], 'lim': cur.get('lim'),
                       'league': match[m['pid']]['league'] if m['pid'] in match else ''}
                for sec in (10, 30, 60, 180, 600):
                    r = ask_at(cur['t'] + sec * 1000)
                    rec[f'ask{sec}'] = r[0] if r and r[1] < 120000 and cur['t'] + sec * 1000 <= (max(span) if span else 0) else None
                    kk = bisect.bisect_right(ts, cur['t'] + sec * 1000) - 1
                    rec[f'fair{sec}'] = series[kk]['fairp'][side] if cur['t'] + sec * 1000 <= ts[-1] and side in series[kk]['fairp'] else None
                moves.append(rec)
    print(f'paired rows {len(rows)}; pinnacle moves >= {a.min_move:.1%} with a novig read just before: {len(moves)}')
    if rows:
        print('\n[1] EV of buying Novig at its ask against Pinnacle power-devigged fair (pregame: no fee)')
        for lo in (0.0, 0.01, 0.02, 0.03, 0.05, 0.10):
            sel = [r for r in rows if r['ev'] >= lo]
            print(f'  ev >= {lo:4.0%}: {len(sel):6d} rows ({len(sel) / len(rows):5.1%}), median depth {statistics.median([r["depth"] for r in sel]) if sel else 0:7.0f}, median seconds since Pinnacle changed {statistics.median([r["age"] for r in sel]) if sel else 0:7.0f}')
        print('\n[2] by seconds since Pinnacle last changed that line: share of rows with ev >= 3%, and median ev')
        for lo, hi in ((0, 5), (5, 15), (15, 60), (60, 300), (300, 1800), (1800, 1e12)):
            sel = [r for r in rows if lo <= r['age'] < hi]
            if sel:
                print(f'  {lo:5.0f}-{hi:7.0f}s: n={len(sel):6d}  ev>=3%: {sum(r["ev"] >= 0.03 for r in sel) / len(sel):6.1%}  median ev {statistics.median([r["ev"] for r in sel]):+.3%}')
    if moves:
        def mean(xs):
            xs = [x for x in xs if x is not None]
            return (sum(xs) / len(xs), len(xs)) if xs else (None, 0)
        print('\n[3] after a Pinnacle move TOWARD the side (move > 0): was Novig behind, did it follow, did Pinnacle keep the move?')
        ups = [r for r in moves if r['move'] > 0]
        print(f'  moves up: {len(ups)} (median {statistics.median([r["move"] for r in ups]):+.3f})' if ups else '  none')
        for r in ups[:0]:
            pass
        if ups:
            ev0 = [r['fair1'] / r['ask0'] - 1 for r in ups]
            print(f'  EV of the OLD Novig ask against the NEW Pinnacle fair: mean {sum(ev0) / len(ev0):+.2%}; share >= 3%: {sum(e >= .03 for e in ev0) / len(ev0):.0%}; >= 5%: {sum(e >= .05 for e in ev0) / len(ev0):.0%}')
            for sec in (10, 30, 60, 180, 600):
                f, nf = mean([r[f'fair{sec}'] for r in ups]); k, nk = mean([r[f'ask{sec}'] for r in ups])
                held = [r[f'fair{sec}'] >= r['fair1'] - 0.005 for r in ups if r[f'fair{sec}'] is not None]
                follow = [r[f'ask{sec}'] > r['ask0'] + 0.004 for r in ups if r[f'ask{sec}'] is not None]
                ev_now = [r['fair%d' % sec] / r['ask0'] - 1 for r in ups if r['fair%d' % sec] is not None]
                print(f'  +{sec:3d}s: Pinnacle kept the move (within 0.5 pt) {sum(held) / len(held) if held else float("nan"):4.0%} (n={len(held)}); Novig ask rose by >0.4 pt {sum(follow) / len(follow) if follow else float("nan"):4.0%} (n={len(follow)}); '
                      f'OLD ask vs Pinnacle fair then: {sum(ev_now) / len(ev_now) if ev_now else float("nan"):+.2%} (n={len(ev_now)})')
            big = [r for r in ups if r['fair1'] / r['ask0'] - 1 >= 0.03]
            print(f'  the ones where the old ask was >= 3% EV at the new fair: n={len(big)}')
            for sec in (30, 180, 600):
                ev_now = [r['fair%d' % sec] / r['ask0'] - 1 for r in big if r['fair%d' % sec] is not None]
                follow = [r[f'ask{sec}'] > r['ask0'] + 0.004 for r in big if r[f'ask{sec}'] is not None]
                if ev_now:
                    print(f'    +{sec:3d}s: old ask vs fair then {sum(ev_now) / len(ev_now):+.2%} (n={len(ev_now)}), Novig ask had risen {sum(follow) / len(follow) if follow else float("nan"):.0%}')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); sp = ap.add_subparsers(dest='cmd', required=True)
    r = sp.add_parser('record'); r.add_argument('--out', required=True); r.add_argument('--minutes', type=float, default=60); r.add_argument('--leagues', default=L.LEAGUES); r.add_argument('--hours-ahead', type=float, default=30)
    z = sp.add_parser('analyze'); z.add_argument('path'); z.add_argument('--min-move', type=float, default=0.01)
    a = ap.parse_args()
    {'record': record, 'analyze': analyze}[a.cmd](a)
