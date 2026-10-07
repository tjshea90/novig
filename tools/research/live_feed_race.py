#!/usr/bin/env python3
"""Which free live-score feed sees a score first, and does any of them beat Novig's own price? RESEARCH.md §99, re-runnable.

Tj, 2026-10-06: "research if there is any way to build my own rapid live odds or scores feed ... fast enough to catch these opportunities ... right after a team
scores, there may be stale offers on novig".  The clock to beat (§95): Novig's makers move the moneyline a median 16 s after a play; a free ESPN read is 41 s late.
This records SEVERAL free feeds of the same live games at once, each stamped on arrival, plus Novig's own moneyline trades (public route, engine ms stamps):

    espn      ESPN scoreboard JSON (CDN max-age 6 s)                     poll every 2 s
    mlb       statsapi.mlb.com schedule+linescore (CDN max-age 20 s)     poll every 2 s, cache-busted (a random query) AND plain, to show what the cache costs
    mlbws     MLB's game push socket (wss://ws.statsapi.mlb.com/...)     push; on each frame it reads the game's linescore at once
    nhl       api-web.nhle.com/v1/score/<day> (CDN max-age 19 s)         poll every 2 s, cache-busted AND plain
    sofa      api.sofascore.com sport/<x>/events/live (max-age 5 s)      poll every 2.5 s per sport
    poly      Polymarket's public sports websocket (push, no key)        wss://sports-api.polymarket.com/ws
    novig     Novig public trades of each live game's moneyline         poll every ~2.5 s, engine timestamps (what MOVES the price)

A "score event" is a game's (home, away) score changing; each feed's first sighting of each new score is stamped with the MIDDLE of the request that saw it (the
push feeds with the arrival time of the frame).  There is no ground truth of when the goal happened, so the table is RELATIVE: how far behind the earliest feed
each feed is, and, against Novig, `novig_first_move - feed_time` (positive = the feed showed the score BEFORE Novig's price had moved: a window).

    python3 tools/research/live_feed_race.py record --out race.ndjson --minutes 300 --leagues espn=baseball/mlb,hockey/nhl,basketball/nba --sofa baseball,ice-hockey,basketball --mlb --nhl --poly --novig MLB,NHL,NBA
    python3 tools/research/live_feed_race.py analyze race.ndjson [--jump 0.03]
    python3 tools/research/live_feed_race.py --selftest

Needs `pip install websockets` for the two push feeds (the polls are stdlib).  Needs no key.  Polite: about 8 requests a second in all, over six hosts.
"""
import argparse, asyncio, collections, json, os, random, re, ssl, statistics, sys, threading, time, unicodedata, urllib.request
from datetime import datetime, timezone

UA = {'User-Agent': 'Mozilla/5.0'}
NOVIG = 'https://api.novig.com/v3/public'
FEE_C = 0.03   # Novig's in-play taker fee coefficient: 0.03 * p * (1 - p) per $1 of payout (NOVIG_API.md section 8)
STOP = {'fc', 'cf', 'sc', 'ac', 'the', 'de', 'of', 'city', 'united', 'real', 'club', 'st', 'saint', 'a', 'and', 'los', 'angeles', 'la', 'new', 'york', 'ny', 'san', 'north', 'south', 'west', 'east'}
_lock = threading.Lock()
_out = None
_stop = threading.Event()


def now():
    return time.time()


def emit(rec):
    with _lock:
        _out.write(json.dumps(rec, separators=(',', ':')) + '\n')
        _out.flush()


def http(url, timeout=15):
    """(json, t_send, t_recv, age_header) or None."""
    t0 = now()
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read()
            age = r.headers.get('Age')
        t1 = now()
        return json.loads(body.decode()), t0, t1, (int(age) if age and age.isdigit() else None)
    except Exception as e:
        print('  fetch failed', url[:70], type(e).__name__, str(e)[:60], file=sys.stderr, flush=True)
        return None


def norm(s):
    s = unicodedata.normalize('NFKD', s or '').encode('ascii', 'ignore').decode().lower()
    return re.sub(r'[^a-z0-9 ]', ' ', s)


def tokens(name):
    return {t for t in norm(name).split() if t not in STOP and len(t) > 1}


def same_team(a, b):
    ta, tb = tokens(a), tokens(b)
    return bool(ta & tb)


def same_game(g1, g2):
    """g = (home, away).  Unordered: some token of one team matches a team of the other, both ways."""
    (a, b), (c, d) = g1, g2
    return (same_team(a, c) and same_team(b, d)) or (same_team(a, d) and same_team(b, c))


class Tracker:
    """Emit a record whenever a game's (home, away) score changes (and a baseline record the first time a game is seen)."""

    def __init__(self, src):
        self.src, self.last = src, {}

    def see(self, gid, home, away, h, a, t_mid, rtt, extra=None, live=True):
        key = (h, a)
        prev = self.last.get(gid)
        if prev == key:
            return
        self.last[gid] = key
        rec = dict(k='s', src=self.src, id=str(gid), home=home, away=away, h=h, a=a, t=round(t_mid, 3), rtt=round(rtt, 3), init=prev is None, live=live)
        if extra:
            rec.update(extra)
        emit(rec)


def poll_loop(fn, every):
    while not _stop.is_set():
        t = now()
        try:
            fn()
        except Exception as e:
            print('  poll error', fn.__name__, type(e).__name__, str(e)[:80], file=sys.stderr, flush=True)
        _stop.wait(max(0.2, every - (now() - t)))


# ------------------------------------------------------------------------------------------------------------------------------------- feeds
def espn_feed(league, every=2.0):
    tr = Tracker('espn')
    url = f'https://site.api.espn.com/apis/site/v2/sports/{league}/scoreboard'

    def once():
        r = http(url)
        if not r:
            return
        d, t0, t1, age = r
        for e in d.get('events', []):
            st = e.get('status', {}).get('type', {}).get('state')
            c = e['competitions'][0]['competitors']
            home = next(x for x in c if x['homeAway'] == 'home')
            away = next(x for x in c if x['homeAway'] == 'away')
            try:
                h, a = int(home.get('score') or 0), int(away.get('score') or 0)
            except ValueError:
                continue
            tr.see(e['id'], home['team']['displayName'], away['team']['displayName'], h, a, (t0 + t1) / 2, t1 - t0, dict(age=age, st=st, lg=league), live=st == 'in')
    poll_loop(once, every)


def mlb_schedule_feed(bust, every=2.0):
    tr = Tracker('mlb_nc' if bust else 'mlb')

    def once():
        day = datetime.now(timezone.utc).strftime('%Y-%m-%d')
        yday = datetime.fromtimestamp(now() - 86400, timezone.utc).strftime('%Y-%m-%d')
        url = f'https://statsapi.mlb.com/api/v1/schedule?sportId=1&hydrate=linescore&startDate={yday}&endDate={day}'
        if bust:
            url += f'&_cb={int(now() * 1000)}{random.randint(0, 999)}'
        r = http(url)
        if not r:
            return
        d, t0, t1, age = r
        for dt in d.get('dates', []):
            for g in dt.get('games', []):
                st = g['status'].get('abstractGameState')
                t = g['teams']
                h, a = t['home'].get('score'), t['away'].get('score')
                if h is None or a is None:
                    h, a = 0, 0
                tr.see(g['gamePk'], t['home']['team']['name'], t['away']['team']['name'], h, a, (t0 + t1) / 2, t1 - t0, dict(age=age, st=st), live=st == 'Live')
    poll_loop(once, every)


def nhl_feed(bust, every=2.0):
    tr = Tracker('nhl_nc' if bust else 'nhl')

    def once():
        day = datetime.fromtimestamp(now() - 5 * 3600, timezone.utc).strftime('%Y-%m-%d')   # the NHL's "day" is the North American evening
        url = f'https://api-web.nhle.com/v1/score/{day}'
        if bust:
            url += f'?_cb={int(now() * 1000)}{random.randint(0, 999)}'
        r = http(url)
        if not r:
            return
        d, t0, t1, age = r
        for g in d.get('games', []):
            st = g.get('gameState')
            h, a = g['homeTeam'].get('score'), g['awayTeam'].get('score')
            if h is None or a is None:
                h, a = 0, 0
            nm = lambda x: ((x.get('placeName') or {}).get('default', '') + ' ' + (x.get('name') or {}).get('default', '')).strip()
            tr.see(g['id'], nm(g['homeTeam']), nm(g['awayTeam']), h, a, (t0 + t1) / 2, t1 - t0, dict(age=age, st=st), live=st in ('LIVE', 'CRIT'))
    poll_loop(once, every)


def sofa_feed(sport, every=2.5):
    tr = Tracker('sofa')
    url = f'https://api.sofascore.com/api/v1/sport/{sport}/events/live'

    def once():
        r = http(url)
        if not r:
            return
        d, t0, t1, age = r
        for e in d.get('events', []):
            hs, as_ = e.get('homeScore', {}), e.get('awayScore', {})
            h, a = hs.get('current'), as_.get('current')
            if sport == 'tennis':
                # the score that changes every game: games in the current set, the set number folded in (set 2's 0-0 is not set 1's)
                ps = [k for k in hs if re.match(r'^period\d+$', k) and k in as_]
                if ps:
                    k = max(ps, key=lambda x: int(x[6:]))
                    sn = int(k[6:])
                    h, a = (sn - 1) * 100 + int(hs[k]), (sn - 1) * 100 + int(as_[k])
                else:
                    h, a = 0, 0
            if h is None or a is None:
                continue
            tr.see(e['id'], e['homeTeam']['name'], e['awayTeam']['name'], h, a, (t0 + t1) / 2, t1 - t0, dict(age=age, lg=(e.get('tournament') or {}).get('name')))
    poll_loop(once, every)


def ssl_ctx():
    cafile = '/root/.ccr/ca-bundle.crt'
    return ssl.create_default_context(cafile=cafile if os.path.exists(cafile) else None)


POLY_GAMES = {}   # gameId -> (home, away, league): what the score socket is carrying, for the odds feed to look up


def poly_feed():
    import websockets
    tr = Tracker('poly')

    async def run():
        while not _stop.is_set():
            try:
                async with websockets.connect('wss://sports-api.polymarket.com/ws', ssl=ssl_ctx(), proxy=os.environ.get('HTTPS_PROXY') or None, open_timeout=15) as ws:
                    print('  poly connected', file=sys.stderr, flush=True)
                    while not _stop.is_set():
                        m = await asyncio.wait_for(ws.recv(), 30)
                        t = now()
                        if isinstance(m, str) and m.strip() == 'ping':
                            await ws.send('pong')
                            continue
                        try:
                            d = json.loads(m)
                        except ValueError:
                            continue
                        mt = re.match(r'^(\d+)-(\d+)$', str(d.get('score', '')))
                        if not mt or not d.get('homeTeam') or not d.get('awayTeam'):
                            continue
                        POLY_GAMES[d.get('gameId')] = (d['homeTeam'], d['awayTeam'], d.get('leagueAbbreviation'))
                        hh, aa = int(mt.group(1)), int(mt.group(2))
                        pm = re.match(r'^S(\d+)$', str(d.get('period', '')))
                        if pm and d.get('leagueAbbreviation') in ('atp', 'wta', 'itf', 'challenger'):
                            sn = int(pm.group(1))
                            hh, aa = (sn - 1) * 100 + hh, (sn - 1) * 100 + aa
                        tr.see(d.get('gameId') or d.get('slug'), d['homeTeam'], d['awayTeam'], hh, aa, t, 0.0,
                               dict(lg=d.get('leagueAbbreviation'), per=d.get('period'), el=d.get('elapsed')), live=bool(d.get('live')))
            except Exception as e:
                print('  poly ws error', type(e).__name__, str(e)[:80], file=sys.stderr, flush=True)
                await asyncio.sleep(3)
    asyncio.run(run())


def polyclob_feed(leagues):
    """Polymarket's ODDS (a push channel, no key): for each live Novig game of `leagues`, the Polymarket match-winner market's best bid / ask as they change
    (wss://ws-subscriptions-clob.polymarket.com/ws/market; gamma-api.polymarket.com finds the tokens by player/team names).  Emits k='pm' records: the server's own
    timestamp (ms) and our arrival time, so the odds feed's own latency and its lead over Novig's trades can both be read."""
    import websockets
    subscribed = {}   # token -> (title, outcome name, names)
    done = set()
    leagues_l = {x.lower() for x in leagues}

    def discover():
        for gid, (home, away, lg) in list(POLY_GAMES.items()):
            if gid in done or lg not in leagues_l:
                continue
            done.add(gid)
            r = http(f'https://gamma-api.polymarket.com/events?game_id={gid}')
            if not r or not r[0]:
                continue
            ev = r[0][0]
            m = (ev.get('markets') or [None])[0]
            if not m:
                continue
            try:
                toks = json.loads(m['clobTokenIds']); outs = json.loads(m['outcomes'])
            except Exception:
                continue
            for tk, o in zip(toks, outs):
                subscribed[tk] = (ev.get('title', ''), o, (home, away))
            time.sleep(0.2)

    async def run():
        while not _stop.is_set():
            await asyncio.get_running_loop().run_in_executor(None, discover)
            if not subscribed:
                await asyncio.sleep(30)
                continue
            try:
                async with websockets.connect('wss://ws-subscriptions-clob.polymarket.com/ws/market', ssl=ssl_ctx(), proxy=os.environ.get('HTTPS_PROXY') or None, open_timeout=15) as ws:
                    await ws.send(json.dumps({'assets_ids': list(subscribed), 'type': 'market'}))
                    print('  poly clob connected', len(subscribed), 'tokens', file=sys.stderr, flush=True)
                    t_resub = now() + 120
                    while not _stop.is_set() and now() < t_resub:
                        try:
                            m = await asyncio.wait_for(ws.recv(), 20)
                        except asyncio.TimeoutError:
                            continue
                        t = now()
                        try:
                            d = json.loads(m)
                        except ValueError:
                            continue
                        for fr in (d if isinstance(d, list) else [d]):
                            ts = fr.get('timestamp')
                            changes = fr.get('price_changes') or ([fr] if fr.get('event_type') == 'book' or 'bids' in fr else [])
                            for c in changes:
                                tk = c.get('asset_id') or fr.get('asset_id')
                                if tk not in subscribed:
                                    continue
                                try:
                                    if 'best_bid' in c:
                                        bid, ask = float(c['best_bid']), float(c['best_ask'])
                                    else:
                                        bids = [float(x['price']) for x in fr.get('bids', [])]; asks = [float(x['price']) for x in fr.get('asks', [])]
                                        bid, ask = max(bids), min(asks)
                                except Exception:
                                    continue
                                tm = subscribed[tk]
                                emit(dict(k='pm', ev=tm[0], o=tm[1], bid=bid, ask=ask, ts=int(ts or fr.get('timestamp') or 0) / 1000.0 if (ts or fr.get('timestamp')) else None, seen=round(t, 3)))
            except Exception as e:
                print('  poly clob error', type(e).__name__, str(e)[:80], file=sys.stderr, flush=True)
                await asyncio.sleep(3)
    asyncio.run(run())


def mlb_ws_feed():
    """MLB's game push socket: one connection per live game; every frame triggers an immediate cache-busted read of that game's linescore."""
    import websockets
    tr = Tracker('mlbws')
    names = {}

    async def watch(pk):
        url = f'wss://ws.statsapi.mlb.com/api/v1/game/push/subscribe/gameday/{pk}'
        while not _stop.is_set():
            try:
                async with websockets.connect(url, ssl=ssl_ctx(), proxy=os.environ.get('HTTPS_PROXY') or None, open_timeout=15) as ws:
                    print('  mlb ws connected', pk, file=sys.stderr, flush=True)
                    while not _stop.is_set():
                        m = await asyncio.wait_for(ws.recv(), 120)
                        t = now()
                        emit(dict(k='f', src='mlbws', id=str(pk), t=round(t, 3), raw=str(m)[:160]))
                        loop = asyncio.get_running_loop()
                        r = await loop.run_in_executor(None, http, f'https://statsapi.mlb.com/api/v1/game/{pk}/linescore?_cb={int(now() * 1000)}')
                        if r:
                            d, t0, t1, age = r
                            h, a = d['teams']['home'].get('runs', 0), d['teams']['away'].get('runs', 0)
                            nm = names.get(pk, ('', ''))
                            tr.see(pk, nm[0], nm[1], h, a, t, t1 - t0, dict(frame_to_read=round(t1 - t, 3)), live=True)
            except Exception as e:
                print('  mlb ws error', pk, type(e).__name__, str(e)[:80], file=sys.stderr, flush=True)
                await asyncio.sleep(5)

    async def run():
        tasks = {}
        while not _stop.is_set():
            r = await asyncio.get_running_loop().run_in_executor(None, http, f'https://statsapi.mlb.com/api/v1/schedule?sportId=1&_cb={int(now() * 1000)}')
            if r:
                for dt in r[0].get('dates', []):
                    for g in dt.get('games', []):
                        pk = g['gamePk']
                        if pk not in tasks and g['status'].get('abstractGameState') in ('Live', 'Preview'):
                            names[pk] = (g['teams']['home']['team']['name'], g['teams']['away']['team']['name'])
                            tasks[pk] = asyncio.create_task(watch(pk))
            await asyncio.sleep(120)
    asyncio.run(run())


def novig_feed(leagues, every=2.5, max_markets=16):
    """Trades of each live game's moneyline (public route).  The engine ts is when the trade happened: the instant Novig's price MOVED."""
    seen = set()
    gap = 0.3

    def once():
        evs = []
        for lg in leagues:
            r = http(f'{NOVIG}/catalog/events?status=OPEN_INGAME&league={urllib.request.quote(lg)}&limit=50')
            if r:
                evs += r[0].get('items', [])
            time.sleep(gap)
        picked = []
        for e in evs[:max_markets]:
            r = http(f"{NOVIG}/catalog/markets?event={e['eventId']}&marketType=MONEY,MONEYLINE_3_WAY_WIN,1X2&limit=20")
            time.sleep(gap)
            if r and r[0].get('items'):
                picked.append((e, r[0]['items']))
        if not picked:
            return
        end = now() + 60   # re-list the games once a minute
        while now() < end and not _stop.is_set():
            for e, ms in picked:
                for m in ms[:1]:
                    r = http(f"{NOVIG}/catalog/markets/{m['marketId']}/trades?limit=100")
                    time.sleep(gap)
                    if not r:
                        continue
                    d, t0, t1, _ = r
                    names = {o['outcomeId']: o['name'] for o in m['outcomes']}
                    for tr in d.get('items', []):
                        if tr['tradeId'] in seen:
                            continue
                        seen.add(tr['tradeId'])
                        emit(dict(k='n', ev=e['description'], lg=e['league'], mk=m['marketId'], mt=m['marketType'], o=tr['outcomeId'], on=names.get(tr['outcomeId']),
                                  p=float(tr['price']), q=tr['qty'], ts=tr['ts'], seen=round((t0 + t1) / 2, 3)))
    poll_loop(once, 1.0)


# ------------------------------------------------------------------------------------------------------------------------------------ record
def record(a):
    global _out
    _out = open(a.out, 'a')
    threads = []

    def start(fn, *args):
        t = threading.Thread(target=fn, args=args, daemon=True)
        t.start()
        threads.append(t)
    emit(dict(k='meta', t=now(), argv=sys.argv[1:]))
    for item in (a.leagues or '').split('=')[-1].split(',') if a.leagues else []:
        if item.strip():
            start(espn_feed, item.strip())
    for sp in (a.sofa or '').split(','):
        if sp.strip():
            start(sofa_feed, sp.strip())
    if a.mlb:
        start(mlb_schedule_feed, True)
        start(mlb_schedule_feed, False)
        start(mlb_ws_feed)
    if a.nhl:
        start(nhl_feed, True)
        start(nhl_feed, False)
    if a.poly:
        start(poly_feed)
    if a.novig:
        start(novig_feed, [x.strip() for x in a.novig.split(',') if x.strip()])
        if a.poly:
            start(polyclob_feed, [x.strip() for x in a.novig.split(',') if x.strip()])
    print(f'recording {len(threads)} feeds for {a.minutes} min into {a.out}', file=sys.stderr, flush=True)
    end = now() + a.minutes * 60
    try:
        while now() < end:
            time.sleep(5)
            emit(dict(k='hb', t=now()))
    except KeyboardInterrupt:
        pass
    _stop.set()
    time.sleep(1)


# ---------------------------------------------------------------------------------------------------------------------------------- analyze
def load(path):
    scores, trades, frames = [], [], []
    global PM
    PM = []
    for line in open(path):
        try:
            r = json.loads(line)
        except ValueError:
            continue
        if r.get('k') == 'pm':
            PM.append(r)
            continue
        {'s': scores, 'n': trades, 'f': frames}.get(r.get('k'), []).append(r)
    return scores, trades, frames


PM = []


def poly_move(names, t_ref, jump, lookback=30.0, pre=15.0, horizon=90.0):
    """When Polymarket's mid for this game's outcomes first moved `jump`+ from its pre-score median (None if never / no series)."""
    mine = collections.defaultdict(list)
    for r in PM:
        title = r['ev'].split(': ')[-1]
        parts = re.split(r' vs\.? ', title)
        if len(parts) < 2 or not same_game((parts[0], parts[1]), names):
            continue
        mid = (r['bid'] + r['ask']) / 2.0
        if r['ask'] - r['bid'] > 0.10:
            continue
        mine[r['o']].append((r.get('ts') or r['seen'], mid))
    best = None
    for o, pts in mine.items():
        pts.sort()
        before = [m for t, m in pts if t_ref - pre - lookback <= t < t_ref - pre]
        if len(before) < 2:
            continue
        ref = statistics.median(before)
        for t, m in pts:
            if t >= t_ref - pre and t - t_ref <= horizon and abs(m - ref) >= jump:
                best = t if best is None else min(best, t)
                break
    return best


def cluster(scores):
    """Group score records into games across sources by team-name tokens (unordered pair).  Returns [{'names': (h,a), 'recs': [...]}]."""
    games = []
    for r in scores:
        if not (r['home'] and r['away']):
            continue
        for g in games:
            if same_game(g['names'], (r['home'], r['away'])):
                g['recs'].append(r)
                break
        else:
            games.append(dict(names=(r['home'], r['away']), recs=[r]))
    return games


def transitions(g):
    """{(h, a): {src: t}}: for each feed, the first time it showed a score above the one it first showed (its baseline), per new score."""
    out = {}
    by_src = collections.defaultdict(list)
    for r in sorted(g['recs'], key=lambda r: r['t']):
        by_src[r['src']].append(r)
    for src, rs in by_src.items():
        top = None
        for r in rs:
            tot = r['h'] + r['a']
            if top is None:
                top = tot
                continue
            if tot > top:
                out.setdefault((r['h'], r['a']), {}).setdefault(src, r['t'])
                top = tot
    return out


def novig_series(trades, ev_names):
    """{(market, outcome): [(ts_s, price, contracts)]} of the moneyline trades of the Novig event matching these team names."""
    ser = collections.defaultdict(list)
    for t in trades:
        a, _, b = t['ev'].partition(' @ ')
        if same_game((b, a), ev_names) or same_game((a, b), ev_names):
            ser[(t['mk'], t['o'])].append((t['ts'] / 1000.0, t['p'], t.get('q', 0)))
    for v in ser.values():
        v.sort()
    return ser


def first_move(ser, t_ref, jump, lookback=30.0, pre=15.0, horizon=90.0):
    """Earliest trade time at/after t_ref - pre whose price is `jump` or more from the median price of the `lookback` s before t_ref - pre.
    Returns (time, signed move, {series key: its pre-score median}) or None if no series moved."""
    best = None
    refs = {}
    for key, pts in ser.items():
        before = [p for t, p, *_ in pts if t_ref - pre - lookback <= t < t_ref - pre]
        if len(before) < 2:
            continue
        refs[key] = ref = statistics.median(before)
        for t, p, *_ in pts:
            if t >= t_ref - pre and t - t_ref <= horizon and abs(p - ref) >= jump:
                if best is None or t < best[0]:
                    best = (t, p - ref)
                break
    return (best[0], best[1], refs) if best else None


def stale_fills(ser, refs, t_feed, t_move, jump, settle=3.0, after=20.0):
    """What a taker who saw the score at t_feed could have bought at the OLD price, from what really traded: trades at or after t_feed (and before the market had settled,
    t_move + settle) on an outcome whose price then FELL by `jump` or more, still at (within jump/2 of) its pre-score price.  A trade on outcome o' at price p' is a resting bid
    on o'; its taker bought the other outcome at 1 - p', worth 1 - new_level: the gain per $1 of payout is p' - new_level, less the in-play taker fee 0.03 p (1 - p).
    Returns (trades, payout dollars, net gain dollars)."""
    n = 0
    payout = gain = 0.0
    for key, pts in ser.items():
        ref = refs.get(key)
        if ref is None:
            continue
        post = [p for t, p, *_ in pts if t_move <= t <= t_move + after]
        if not post:
            continue
        new = statistics.median(post)
        if ref - new < jump:
            continue   # this outcome did not fall: its resting bids were not too high
        for t, p, qty in pts:
            if t_feed <= t <= t_move + settle and p >= ref - jump / 2:
                g = p - new - FEE_C * p * (1 - p)
                if g > 0:
                    n += 1
                    payout += qty / 100.0
                    gain += g * qty / 100.0
    return n, payout, gain


def q(xs, f):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(f * len(xs)))]


def analyze(a):
    scores, trades, frames = load(a.path)
    games = cluster(scores)
    srcs = sorted({r['src'] for r in scores})
    print(f'{len(scores)} score records from {len(srcs)} feeds ({", ".join(srcs)}); {len(trades)} Novig trades; {len(games)} games')
    rows = []   # (game names, key, {src: t})
    for g in games:
        for key, d in transitions(g).items():
            rows.append((g['names'], key, d, g))
    multi = [r for r in rows if len(r[2]) >= 2]
    print(f'{len(rows)} new scores seen live; {len(multi)} seen by two or more feeds')
    lag = collections.defaultdict(list)
    first = collections.Counter()
    seen = collections.Counter()
    for names, key, d, g in multi:
        t0 = min(d.values())
        for s, t in d.items():
            lag[s].append(t - t0)
            seen[s] += 1
            if t == t0:
                first[s] += 1
    print('\nHOW FAR BEHIND THE EARLIEST FEED (s; 0 = it was first) over the scores two or more feeds saw')
    print(f'  {"feed":8} {"scores":>6} {"first":>6} {"median":>8} {"p25":>7} {"p75":>7} {"p90":>7} {"max":>7}')
    for s in sorted(lag, key=lambda s: statistics.median(lag[s])):
        L = lag[s]
        print(f'  {s:8} {seen[s]:6d} {first[s]:6d} {statistics.median(L):8.1f} {q(L, .25):7.1f} {q(L, .75):7.1f} {q(L, .9):7.1f} {max(L):7.1f}')
    if trades:
        print(f'\nAGAINST NOVIG (moneyline moved {a.jump:.2f}+ from its median of the 30 s before; M - feed > 0 = the feed showed the score BEFORE Novig moved)')
        lead = collections.defaultdict(list)
        stale = collections.defaultdict(lambda: [0, 0.0, 0.0])
        n_ev = n_mv = 0
        detail = []
        for names, key, d, g in rows:
            ser = novig_series(trades, names)
            if not ser:
                continue
            n_ev += 1
            t_first = min(d.values())
            mv = first_move(ser, t_first, a.jump)
            if not mv:
                continue
            n_mv += 1
            for s_, t in d.items():
                lead[s_].append(mv[0] - t)
                n, pay, gain = stale_fills(ser, mv[2], t, mv[0], a.jump)
                st = stale[s_]
                st[0] += n
                st[1] += pay
                st[2] += gain
            detail.append((t_first, names, key, mv, d))
        print(f'  {n_ev} scores on games Novig traded; {n_mv} moved the moneyline')
        pl = []
        n_poly_series = 0
        for t_first, names, key, mv, d in detail:
            pm = poly_move(names, t_first, a.jump)
            if pm is not None:
                pl.append((mv[0] - pm, pm - t_first))
        if PM:
            print(f'  Polymarket ODDS (CLOB socket): {len(PM)} quotes; of those {n_mv} Novig moves, {len(pl)} had a Polymarket mid that also moved {a.jump:.2f}+')
            if pl:
                L = [x for x, _ in pl]
                print(f'    Novig move minus Polymarket move (>0: Polymarket odds moved FIRST): before {sum(1 for x in L if x > 0)} of {len(L)}, by 3s+ {sum(1 for x in L if x >= 3)}, median {statistics.median(L):.1f} p10 {q(L, .1):.1f} p90 {q(L, .9):.1f}')
                R = [y for _, y in pl]
                print(f'    Polymarket mid move minus the first score feed (s; its reaction time to the score): median {statistics.median(R):.1f} p10 {q(R, .1):.1f} p90 {q(R, .9):.1f}')
        print(f'  {"feed":8} {"scores":>6} {"before":>7} {"by 3s+":>7} {"median":>8} {"p10":>7} {"p90":>7}')
        for s_ in sorted(lead, key=lambda s_: -statistics.median(lead[s_])):
            L = lead[s_]
            print(f'  {s_:8} {len(L):6d} {sum(1 for x in L if x > 0):7d} {sum(1 for x in L if x >= 3):7d} {statistics.median(L):8.1f} {q(L, .1):7.1f} {q(L, .9):7.1f}')
        print('\n  STALE FILLS A TAKER COULD HAVE HAD at each feed\'s time (what really traded at the old price after the feed showed the score, before the market settled;')
        print('  a floor on the opportunity: only fills that happened; net of the in-play taker fee; payout dollars = contracts / 100):')
        print(f'  {"feed":8} {"trades":>7} {"payout $":>10} {"net gain $":>11}')
        for s_ in sorted(stale, key=lambda s_: -stale[s_][2]):
            n, pay, gain = stale[s_]
            print(f'  {s_:8} {n:7d} {pay:10.2f} {gain:11.2f}')
        print('\n  per score (UTC, game, score, Novig move, then each feed: its time minus the first feed, and M - feed):')
        for t_first, names, key, mv, d in sorted(detail)[:60]:
            cells = ' '.join(f'{s_}:{d[s_] - t_first:+.1f}/{mv[0] - d[s_]:+.1f}' for s_ in sorted(d))
            print(f"    {time.strftime('%H:%M:%S', time.gmtime(t_first))} {names[0][:14]} v {names[1][:14]} {key[0]}-{key[1]} move {mv[1]:+.2f}  {cells}")
    ages = collections.defaultdict(list)
    for r in scores:
        if r.get('age') is not None:
            ages[r['src']].append(r['age'])
    if ages:
        print('\nCDN "Age" header the polls saw (s; a cached copy that old): ' + '; '.join(f'{s} median {statistics.median(v):.0f} max {max(v)}' for s, v in sorted(ages.items())))
    rtt = collections.defaultdict(list)
    for r in scores:
        if r.get('rtt'):
            rtt[r['src']].append(r['rtt'])
    if rtt:
        print('request round trip (s): ' + '; '.join(f'{s} median {statistics.median(v):.2f}' for s, v in sorted(rtt.items())))
    if frames:
        print(f'MLB push frames: {len(frames)}')


def selftest():
    assert same_game(('Los Angeles Dodgers', 'Atlanta Braves'), ('Atlanta Braves', 'Los Angeles Dodgers'))
    assert same_game(('Montréal Canadiens', 'Carolina Hurricanes'), ('Montreal', 'Hurricanes'))
    assert not same_game(('Manchester United', 'Newcastle United'), ('Manchester City', 'Chelsea'))
    assert same_game(('Czechia', 'England'), ('England', 'Czechia'))
    global _out
    import io
    _out = io.StringIO()
    tr = Tracker('x')
    tr.see(1, 'A', 'B', 0, 0, 10.0, 0.1)
    tr.see(1, 'A', 'B', 0, 0, 11.0, 0.1)
    tr.see(1, 'A', 'B', 1, 0, 12.0, 0.1)
    recs = [json.loads(x) for x in _out.getvalue().splitlines()]
    assert len(recs) == 2 and recs[0]['init'] and not recs[1]['init'], recs
    # two feeds, one goal, Novig moves 5 s after the faster feed
    S = lambda src, t, h, a, init=False: dict(k='s', src=src, id='1', home='Spain', away='Croatia', h=h, a=a, t=t, rtt=0.1, init=init, live=True)
    scores = [S('fast', 100, 0, 0, True), S('slow', 100, 0, 0, True), S('fast', 200, 1, 0), S('slow', 207, 1, 0)]
    g = cluster(scores)
    assert len(g) == 1
    tr = transitions(g[0])
    assert tr == {(1, 0): {'fast': 200, 'slow': 207}}, tr
    trades = [dict(k='n', ev='Croatia @ Spain', mk='m', o='o1', p=0.50, q=100, ts=(150 + i) * 1000) for i in range(0, 40, 4)]
    trades += [dict(k='n', ev='Croatia @ Spain', mk='m', o='o1', p=0.60, q=100, ts=205 * 1000), dict(k='n', ev='Croatia @ Spain', mk='m', o='o1', p=0.62, q=100, ts=206 * 1000)]
    # the other outcome: its price falls from 0.50 to 0.40 at 205 s, but a stale bid at 0.50 is hit at 201 s and 203 s, 1,000 and 500 contracts
    trades += [dict(k='n', ev='Croatia @ Spain', mk='m', o='o2', p=0.50, q=100, ts=(150 + i) * 1000) for i in range(0, 40, 4)]
    trades += [dict(k='n', ev='Croatia @ Spain', mk='m', o='o2', p=0.50, q=1000, ts=201 * 1000), dict(k='n', ev='Croatia @ Spain', mk='m', o='o2', p=0.50, q=500, ts=203 * 1000),
               dict(k='n', ev='Croatia @ Spain', mk='m', o='o2', p=0.40, q=100, ts=205 * 1000), dict(k='n', ev='Croatia @ Spain', mk='m', o='o2', p=0.38, q=100, ts=206 * 1000)]
    ser = novig_series(trades, ('Spain', 'Croatia'))
    mv = first_move(ser, 200, 0.03)
    assert mv and abs(mv[0] - 205) < 1e-6, mv
    n, pay, gain = stale_fills(ser, mv[2], 200, mv[0], 0.03)
    assert n == 2 and abs(pay - 15.0) < 1e-9 and gain > 0, (n, pay, gain)      # 1,500 contracts = $15 of payout at ~10 c: about $1.5 less fees
    n2, pay2, _ = stale_fills(ser, mv[2], 202, mv[0], 0.03)
    assert n2 == 1 and abs(pay2 - 5.0) < 1e-9, (n2, pay2)                      # a slower feed (202 s) had only the later fill
    print('selftest ok')


def main():
    if '--selftest' in sys.argv:
        selftest()
        return
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest='cmd', required=True)
    r = sub.add_parser('record')
    r.add_argument('--out', required=True)
    r.add_argument('--minutes', type=float, default=300)
    r.add_argument('--leagues', help='espn=<sport/league>,...  e.g. espn=baseball/mlb,hockey/nhl,basketball/nba,soccer/uefa.nations')
    r.add_argument('--sofa', help='sofascore sports, comma separated: football,baseball,ice-hockey,basketball')
    r.add_argument('--mlb', action='store_true')
    r.add_argument('--nhl', action='store_true')
    r.add_argument('--poly', action='store_true')
    r.add_argument('--novig', help="Novig league names, comma separated: MLB,NHL,NBA,'UEFA Nations League'")
    z = sub.add_parser('analyze')
    z.add_argument('path')
    z.add_argument('--jump', type=float, default=0.03)
    a = ap.parse_args()
    {'record': record, 'analyze': analyze}[a.cmd](a)


if __name__ == '__main__':
    main()
