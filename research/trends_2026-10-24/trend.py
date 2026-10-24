import pickle,collections,math,statistics as st,sys
rows=pickle.load(open(sys.argv[1],'rb'))
def am2p(a):
    a=float(a); return 100/(a+100) if a>0 else -a/(-a+100)
def dec(a):
    a=float(a); return 1+a/100 if a>0 else 1+100/-a
# pair Over/Under props by (event, market, player, line)
pairs=collections.defaultdict(dict)
for r in rows:
    if r['player_name'] and r['outcome_name'] in ('Over','Under') and r['resolution'] in ('won','lost'):
        pairs[(r['event_id'],r['market'],r['player_name'],r['line'])][r['outcome_name']]=r
# player history: (player, market) -> [(time, event, actual, line)] one per event (main line = the one closest to 50/50)
hist=collections.defaultdict(dict)
recs=[]
for k,d in pairs.items():
    if 'Over' not in d or 'Under' not in d: continue
    o,u=d['Over'],d['Under']
    if not (o['closing_price'] and u['closing_price'] and o['actual_value']): continue
    po,pu=am2p(o['closing_price']),am2p(u['closing_price'])
    if po+pu<1.0 or po+pu>1.15: continue
    fo=po/(po+pu)
    av=float(o['actual_value']); ln=float(o['line'])
    t=o['commence_time']
    op=None
    if o['opening_price'] and u['opening_price'] and o['opening_point']==o['line']:
        a,b=am2p(o['opening_price']),am2p(u['opening_price'])
        if 1.0<=a+b<=1.15: op=a/(a+b)
    rec=dict(ev=k[0],mk=k[1],pl=k[2],ln=ln,t=t,av=av,won=1 if o['resolution']=='won' else 0,fo=fo,po=po,pu=pu,
             do=dec(o['closing_price']),du=dec(u['closing_price']),op=op,sport=o['sport_key'],pid=o['player_id'] or k[2])
    recs.append(rec)
    h=hist[(rec['pid'],k[1])]
    # one value per event; keep the line closest to 50/50 as that game's "main" line
    if k[0] not in h or abs(fo-.5)<abs(h[k[0]][3]-.5): h[k[0]]=(t,av,ln,fo)
for key in hist: hist[key]=sorted(hist[key].values())
print('pairs',len(recs))
import bisect
def prior(rec,n):
    h=hist[(rec['pid'],rec['mk'])]
    i=bisect.bisect_left(h,(rec['t'],))
    return h[max(0,i-n):i]
out=[]
for r in recs:
    p=prior(r,10)
    if len(p)<5: continue
    vals=[x[1] for x in p]
    m=st.mean(vals); sd=st.pstdev(vals) or 1.0
    hit=st.mean(1 if x[1]>x[2] else 0 for x in p)        # Over hit rate on that game's main line
    mkt=st.mean(x[3] for x in p)                           # what the market expected then
    r['gap']=(m-r['ln'])/sd                                # recent average above the line, in the player's sd
    r['hit']=hit; r['hitx']=hit-mkt                        # hit rate beyond what the prices then implied
    r['n']=len(p)
    out.append(r)
print('with 5+ prior games',len(out))
def month(r): return r['t'][:7]
def ev(sub, sig, side):
    # bet [side] at Novig's close price; return games, ROI, se, residual mean
    pnl=[];res=[]
    g=collections.defaultdict(list)
    for r in sub:
        if side=='over': w=r['won']; d=r['do']; f=r['fo']
        else: w=1-r['won']; d=r['du']; f=1-r['fo']
        g[r['ev']].append((w*d-1, w-f))
    gp=[st.mean(x[0] for x in v) for v in g.values()]; gr=[st.mean(x[1] for x in v) for v in g.values()]
    if len(gp)<30: return None
    return len(sub),len(gp),st.mean(gp),st.pstdev(gp)/math.sqrt(len(gp)),st.mean(gr),st.pstdev(gr)/math.sqrt(len(gr))
def show(name,sub):
    for side in ('over','under'):
        x=ev(sub,None,side)
        if x: print('  %-34s %-5s bets %6d games %5d ROI %+6.2f%% (se %.2f)  beat close %+6.2f pts (se %.2f)'%(name,side,x[0],x[1],100*x[2],100*x[3],100*x[4],100*x[5]))
train=[r for r in out if r['t']<'2026-09-01']; test=[r for r in out if r['t']>='2026-09-01']
for lab,S in (('TRAIN May-Aug',train),('TEST Sep-Oct',test)):
    print('==',lab,len(S))
    show('all',S)
    show('avg >= line +0.5sd',[r for r in S if r['gap']>=0.5])
    show('avg <= line -0.5sd',[r for r in S if r['gap']<=-0.5])
    show('hit rate >= 70%',[r for r in S if r['hit']>=0.7])
    show('hit rate <= 30%',[r for r in S if r['hit']<=0.3])
    show('hit beyond prices >= +20pts',[r for r in S if r['hitx']>=0.2])
    show('hit beyond prices <= -20pts',[r for r in S if r['hitx']<=-0.2])
    mv=[r for r in S if r['op'] is not None]
    show('steamed Over (+5pts open->close)',[r for r in mv if r['fo']-r['op']>=0.05])
    show('steamed Under (-5pts)',[r for r in mv if r['fo']-r['op']<=-0.05])
# correlation of each signal with the residual (Over won - fair), whole set and by sport
def corr(a,b):
    ma,mb=st.mean(a),st.mean(b); sa=st.pstdev(a); sb=st.pstdev(b)
    return sum((x-ma)*(y-mb) for x,y in zip(a,b))/len(a)/(sa*sb) if sa and sb else 0
print('== correlation with result beyond the close (n, r, r*sqrt(n) ~ z)')
for sig in ('gap','hit','hitx'):
    for lab,S in (('train',train),('test',test)):
        a=[r[sig] for r in S]; b=[r['won']-r['fo'] for r in S]; c=corr(a,b)
        print('  %-5s %-5s n %6d r %+.4f z~%+.1f'%(sig,lab,len(S),c,c*math.sqrt(len(S))))
print('== by sport, TEST, avg >= line +0.5sd (Over) and <= -0.5sd (Under)')
for sp in sorted({r['sport'] for r in out}):
    s=[r for r in test if r['sport']==sp]
    show(sp+' gap>=.5',[r for r in s if r['gap']>=0.5]); show(sp+' gap<=-.5',[r for r in s if r['gap']<=-0.5])
pickle.dump(out,open(sys.argv[2],'wb'))
