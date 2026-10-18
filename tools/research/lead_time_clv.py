import csv, gzip, glob, math, sys, collections
rows=[]
for f in sorted(glob.glob('/tmp/claude-0/ds/lines-*.csv.gz')):
    with gzip.open(f,'rt') as fh:
        for r in csv.DictReader(fh): rows.append(r)
print('rows',len(rows))
def fl(x):
    try: return float(x)
    except: return None
def bucket(m):
    if m is None: return '?'
    h=m/60
    for lo,hi,l in [(0,1,'<1h'),(1,3,'1-3h'),(3,6,'3-6h'),(6,12,'6-12h'),(12,24,'12-24h'),(24,48,'24-48h'),(48,1e9,'48h+')]:
        if lo<=h<hi: return l
    return '?'
def stats(sub, label):
    clv=[fl(r['clv']) for r in sub if fl(r['clv']) is not None]
    games=collections.defaultdict(list)
    for r in sub:
        c=fl(r['clv'])
        if c is not None: games[r['event']].append(c)
    gm=[sum(v)/len(v) for v in games.values()]
    res=[fl(r['profit_units']) for r in sub if r['status'] in ('WON','LOST','PUSH') and fl(r['profit_units']) is not None]
    def mt(xs):
        if len(xs)<2: return (sum(xs)/len(xs) if xs else float('nan'), float('nan'))
        m=sum(xs)/len(xs); sd=math.sqrt(sum((x-m)**2 for x in xs)/(len(xs)-1)); return m, m/(sd/math.sqrt(len(xs))) if sd>0 else float('nan')
    m1,t1=mt(clv); m2,t2=mt(gm); m3,t3=mt(res)
    print(f"{label:28s} n={len(sub):5d} clv_n={len(clv):5d} clv={m1*100:+6.2f}% t={t1:5.1f} | games={len(gm):4d} gclv={m2*100:+6.2f}% t={t2:5.1f} | res_n={len(res):4d} roi={m3*100:+6.1f}% t={t3:5.1f}")
pos=[r for r in rows if (fl(r['ev']) or -1)>0]
print('--- +EV at first look, by lead time')
for b in ['<1h','1-3h','3-6h','6-12h','12-24h','24-48h','48h+']:
    stats([r for r in pos if bucket(fl(r['min_to_start']))==b], b)
print('--- +EV by kind')
for k in sorted(set(r['kind'] for r in pos)):
    stats([r for r in pos if r['kind']==k], k)
print('--- +EV kind x lead (<6h vs >=6h)')
for k in sorted(set(r['kind'] for r in pos)):
    for early in (False, True):
        sub=[r for r in pos if r['kind']==k and fl(r['min_to_start']) is not None and ((fl(r['min_to_start'])>=360)==early)]
        if len(sub)>=20: stats(sub, f"{k} {'>=6h' if early else '<6h'}")
print('--- shown vs not')
stats([r for r in pos if r['shown']=='1'],'shown')
stats([r for r in pos if r['shown']!='1'],'not shown')
