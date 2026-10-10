import json, pandas as pd, numpy as np
F='/root/.claude/uploads/e24cce0e-cdce-51ce-b7d7-f01dce035d93/5d6ed178-vigilant-scan-study-v0.85.3-2026-10-10-0302.txt'
L=open(F).read().split('\n')
def section(start_prefix, end_prefix):
    i=next(i for i,l in enumerate(L) if l.startswith(start_prefix)); out=[]
    for l in L[i+1:]:
        if l.startswith(end_prefix) or l.startswith('== '): break
        if l.startswith('{'):
            try: out.append(json.loads(l))
            except: pass
    return out
filled=pd.DataFrame(section('== EVERY FILLED BID','== THE NEWEST'))
unf=pd.DataFrame(section('== THE NEWEST 300','== EVERY BET'))
print('filled bids',len(filled),'unfilled sample',len(unf))
print(list(filled.columns))
def cls(v):
    v=str(v)
    if 'Pinnacle' in v or 'Circa' in v: return 'SHARP'
    if 'ESPN' in v and 'Tracker' not in v: return 'ESPN'
    if "Novig's last trades" in v: return 'NOVIG-trades'
    if 'Tracker' in v: return 'TRACKER'
    return 'none' if v in('nan','None') else 'other'
filled['cc']=filled.closeVia.map(cls)
filled['day']=pd.to_datetime(filled.postedAtMs,unit='ms',utc=True).dt.tz_convert('America/New_York').dt.date
f=filled[filled.clv.notna()]
def r(n,g): print(f'{n:30s} n={len(g):4d} CLV={g.clv.mean()*100:+.2f}% beat={(g.clv>0).mean()*100:.0f}% evPost={g.evAtPost.mean()*100:+.2f}% events={g.event.nunique()}')
r('bids all',f)
for k,g in f.groupby('cc'): r('close '+k,g)
days=sorted(f.day.unique()); mid=days[len(days)//2]
r('first half',f[f.day<mid]); r('second half',f[f.day>=mid])
f=f.assign(tb=pd.cut(f.minToStartAtPost,[-1,120,360,720,1440,1e9],labels=['<2h','2-6h','6-12h','12-24h','24h+']))
for k,g in f.groupby('tb',observed=True): r('post '+str(k),g)
for k,g in f.groupby('kind'): r('kind '+str(k),g)
print('unfilled sample minToStart:',unf.minToStartAtPost.describe()[['count','mean','50%']].to_dict() if 'minToStartAtPost' in unf else unf.columns.tolist())
# ---- looks timing for sharp-close bets
c=pd.read_pickle('clv.pkl')
def cl(v):
    v=str(v); return ('Pinnacle' in v or 'Circa' in v or ('ESPN' in v and 'Tracker' not in v))
S=c[c.closeVia.map(cl)]
rows=[]
for _,b in S.iterrows():
    for lk in b.s or []:
        try:
            mins,kind,am,ev=lk[0],lk[1],lk[2],lk[3]
        except: continue
        if kind in('xc','xw','xv','k') or am is None: continue
        cost = 100/(am+100) if am>0 else -am/(-am+100)
        rows.append((b.id,mins,closeFair:=b.closeFair,cost,ev))
d=pd.DataFrame(rows,columns=['id','mins','closeFair','cost','ev']); d=d[d.closeFair.notna()]
d['clv']=d.closeFair/d.cost-1
d['b']=pd.cut(d.mins,[-1,30,120,360,720,1440,1e9],labels=['<30m','30m-2h','2-6h','6-12h','12-24h','24h+'])
print('\nCLV of the PRICE AT EACH LOOK (sharp-close bets) by minutes before start:')
for k,g in d.groupby('b',observed=True): print(f'  looks {k:7s} n={len(g):6d} bets={g.id.nunique():5d} CLV={g.clv.mean()*100:+.2f}%')
# within-bet: first look vs last look
first=d.groupby('id').head(1); last=d.groupby('id').tail(1)
print('first-look CLV',first.clv.mean()*100,'last-look CLV',last.clv.mean()*100,'bets',first.id.nunique())
