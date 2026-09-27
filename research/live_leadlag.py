# Live in-play probe (RESEARCH.md §21): Novig live MONEY books vs Kalshi game markets, 1 s samples for 150 s.
# Who moves first, and how often Novig beats Kalshi's mid after Novig's live taker fee. Edit `want` for tonight's games.
# python3 research/live_leadlag.py   (public routes only; be gentle: Kalshi 429s bursts)
import json, time, urllib.request, concurrent.futures as cf
def get(url):
    req=urllib.request.Request(url, headers={"User-Agent":"Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=10) as r: return json.loads(r.read().decode())
ev=get("https://api.novig.com/v3/public/catalog/events?status=OPEN_INGAME&league=MLB&limit=50")
desc={e['eventId']:e['description'] for e in ev['items']}
mk=get("https://api.novig.com/v3/public/catalog/markets?league=MLB&marketType=MONEY&eventStatus=OPEN_INGAME&limit=200")
want={'Houston':('KXMLBGAME-26SEP262140HOUATH-HOU','HOU'),'Seattle':('KXMLBGAME-26SEP262140LAASEA-SEA','SEA'),'San Diego':('KXMLBGAME-26SEP262040AZSD-SD','SD')}
T=[]
for m in mk['items']:
    d=desc.get(m['eventId'],'')
    for k,(tk,team) in want.items():
        if k in d:
            oid=[o['outcomeId'] for o in m['outcomes'] if o['name']==team]
            other=[o['outcomeId'] for o in m['outcomes'] if o['name']!=team]
            if oid and other: T.append((team,m['marketId'],oid[0],other[0],tk))
def nov(t):
    team,mid,oid,other,tk=t; b=get(f"https://api.novig.com/v3/public/catalog/markets/{mid}/book")['orders']
    bid=float(b[oid][0]['price']) if b.get(oid) else None; ask=1-float(b[other][0]['price']) if b.get(other) else None
    return (bid,ask)
def kal(t):
    team,mid,oid,other,tk=t; ob=get(f"https://api.elections.kalshi.com/trade-api/v2/markets/{tk}/orderbook")
    ob=ob.get('orderbook_fp') or ob.get('orderbook') or {}
    y=ob.get('yes_dollars') or ob.get('yes') or []; n=ob.get('no_dollars') or ob.get('no') or []
    f=lambda l: (lambda p: p/100 if p>1 else p)(float(l[-1][0])) if l else None
    yb=f(y); nb=f(n); return (yb, 1-nb if nb is not None else None)
series={t[0]:[] for t in T}
ex=cf.ThreadPoolExecutor(8)
end=time.time()+150
while time.time()<end:
    t0=time.time()
    futs={(t[0],'N'):ex.submit(nov,t) for t in T}
    futs.update({(t[0],'K'):ex.submit(kal,t) for t in T})
    snap={}
    for k,f in futs.items():
        try: snap[k]=f.result()
        except Exception as e: snap[k]=(None,None)
    for t in T: series[t[0]].append((round(t0,1), snap[(t[0],'N')], snap[(t[0],'K')]))
    time.sleep(max(0,1-(time.time()-t0)))
mid=lambda q: (q[0]+q[1])/2 if q and q[0] is not None and q[1] is not None else None
for team,s in series.items():
    nm=[mid(x[1]) for x in s]; km=[mid(x[2]) for x in s]
    # find jumps >= 0.02 in each series and which moved first
    def jumps(a):
        out=[]
        for i in range(1,len(a)):
            if a[i] is not None and a[i-1] is not None and abs(a[i]-a[i-1])>=0.02: out.append(i)
        return out
    jn=jumps(nm); jk=jumps(km)
    # for each Kalshi jump, when did Novig mid move in the same direction by >=0.015 (within ±15 samples)?
    lags=[]
    for i in jk:
        d=km[i]-km[i-1]
        base=next((nm[j] for j in range(i-1,-1,-1) if nm[j] is not None), None)
        for j in range(max(1,i-15), min(len(nm),i+16)):
            if nm[j] is not None and base is not None and (nm[j]-base)*d>0 and abs(nm[j]-base)>=0.015:
                lags.append(j-i); break
    # time Novig's best take was below Kalshi mid - fee (apparent +EV) 
    ev=[]
    for x in s:
        (nb,na),(kb,ka)=x[1],x[2]
        if None in (na,kb,ka): continue
        fair=(kb+ka)/2; fee=0.03*na*(1-na); e=fair/(na+fee)-1
        ev.append(e)
    pos=[e for e in ev if e>0.01]
    print(f"{team}: samples {len(s)} | Novig jumps {len(jn)} Kalshi jumps {len(jk)} | Novig lag after Kalshi jump (s, negative = Novig first): {lags} | apparent EV>1% vs Kalshi mid after fee: {len(pos)} of {len(ev)} samples, max {max(ev) if ev else None:.3f}")
