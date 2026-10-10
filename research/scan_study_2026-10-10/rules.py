import pandas as pd, numpy as np, random
random.seed(1)
c=pd.read_pickle('clv.pkl')
def cls(v):
    v=str(v)
    if 'Pinnacle' in v or 'Circa' in v: return 'SHARP(Pinn/Circa)'
    if 'ESPN' in v and 'Tracker' not in v: return 'ESPN-DK'
    if "Novig's last trades" in v: return 'NOVIG-trades'
    if 'Tracker' in v: return 'TRACKER'
    return 'other'
c['cc']=c.closeVia.map(cls)
def boot(g,n=300):
    games=g.game.unique()
    if len(games)<5: return (np.nan,np.nan)
    grp={k:v.clv.values for k,v in g.groupby('game')}
    ms=[np.concatenate([grp[k] for k in random.choices(games,k=len(games))]).mean() for _ in range(n)]
    return np.percentile(ms,[2.5,97.5])
def row(name,g):
    if len(g)==0: return
    lo,hi=boot(g)
    print(f'{name:34s} n={len(g):5d} g={g.game.nunique():4d} CLV={g.clv.mean()*100:+.2f}% [{lo*100:+.2f},{hi*100:+.2f}] beat={(g.clv>0).mean()*100:.0f}% EV={g.ev.mean()*100:+.2f}%')
print('== by close class'); 
for k,g in c.groupby('cc'): row(k,g)
S=c[c.cc.isin(['SHARP(Pinn/Circa)','ESPN-DK'])].copy()
print('\n== SHARP closes only (Pinnacle/Circa/ESPN-DK): n',len(S),'games',S.game.nunique())
row('all sharp-close',S)
bins=[-1e9,30,120,360,720,1440,1e9]; labs=['<30m','30m-2h','2-6h','6-12h','12-24h','24h+']
S['tb']=pd.cut(S.minToStartFirst,bins,labels=labs)
for k,g in S.groupby('tb',observed=True): row('start in '+str(k),g)
S['evb']=pd.cut(S.ev,[-1,0.01,0.02,0.03,0.04,1],labels=['<1','1-2','2-3','3-4','4+'])
for k,g in S.groupby('evb',observed=True): row('EV '+str(k),g)
for k,g in S.groupby('src'): 
    if len(g)>=40: row('src '+k,g)
for k,g in S.groupby('kind'):
    if len(g)>=30: row('kind '+k,g)
S['lm']=pd.cut(S.listedMin,[-1,2,10,60,1e9],labels=['<2m','2-10m','10-60m','1h+'])
for k,g in S.groupby('lm',observed=True): row('listed '+str(k),g)
print('\n== candidate rules, sharp closes, by date half')
days=sorted(S.day.unique()); mid=days[len(days)//2]
def rule(name,m):
    g=S[m]; 
    if len(g)==0: return
    a=g[g.day<mid]; b=g[g.day>=mid]
    nd=len(days)
    print(f'{name}: bets={len(g)} games={g.game.nunique()} per-day={len(g)/nd:.1f}  ROI(settled)={g.profit.dropna().mean()*100 if g.profit.notna().any() else float("nan"):+.1f}%')
    row('   all',g); row('   first half',a); row('   second half',b)
rule('R0 every sharp-close bet',S.ev>-1)
rule('R1 EV>=3%',S.ev>=0.03)
rule('R2 EV>=3% & start<=6h',(S.ev>=0.03)&(S.minToStartFirst<=360))
rule('R3 EV>=2% & start<=6h',(S.ev>=0.02)&(S.minToStartFirst<=360))
rule('R4 start<=6h (any EV)',(S.minToStartFirst<=360))
rule('R5 EV>=3% & start>6h',(S.ev>=0.03)&(S.minToStartFirst>360))
rule('R6 EV>=3% & listed<=60m',(S.ev>=0.03)&(S.listedMin.fillna(0)<=60))
print('\n== NOVIG-trades-close bets (n trades>=10 only)')
N=c[(c.cc=='NOVIG-trades')]
N=N.assign(nt=N.closeVia.str.extract(r'\((\d+)\)')[0].astype(float))
row('trades>=10',N[N.nt>=10]); row('trades<10',N[N.nt<10])
