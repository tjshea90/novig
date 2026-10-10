import pandas as pd, numpy as np, random
df=pd.read_pickle('bets.pkl')
df['game']=df['event'].astype(str)+'|'+df['startsAtMs'].astype(str)
df['day']=pd.to_datetime(df['firstSeenMs'],unit='ms',utc=True).dt.tz_convert('America/New_York').dt.date
df['hour']=pd.to_datetime(df['firstSeenMs'],unit='ms',utc=True).dt.tz_convert('America/New_York').dt.hour
c=df[df.clv.notna()].copy()
print('bets',len(df),'with clv',len(c),'games',c.game.nunique())
print(df.status.value_counts().to_dict()); print(c.closeVia.value_counts().to_dict())
print('dup ids',df.id.duplicated().sum())
def boot(g,col='clv',n=400):
    games=g.game.unique(); 
    if len(games)<5: return (np.nan,np.nan)
    grp={k:v[col].values for k,v in g.groupby('game')}
    ms=[]
    for _ in range(n):
        s=np.concatenate([grp[k] for k in random.choices(games,k=len(games))]); ms.append(s.mean())
    return np.percentile(ms,[2.5,97.5])
def row(name,g):
    if len(g)==0: return
    lo,hi=boot(g)
    print(f'{name:38s} n={len(g):5d} games={g.game.nunique():4d} CLV={g.clv.mean()*100:+.2f}% [{lo*100:+.2f},{hi*100:+.2f}] beat={ (g.clv>0).mean()*100:.0f}%  EVlisted={g.ev.mean()*100:+.2f}%')
row('ALL with close',c)
# sanity: only real closes (not 1-2 trade Novig)
for v,g in c.groupby('closeVia'): row('close '+str(v)[:28],g)
print('--- date halves (by first seen day)')
days=sorted(c.day.unique()); print(days)
mid=days[len(days)//2]
row('first half',c[c.day<mid]); row('second half',c[c.day>=mid])
print('--- minutes to start at first listing')
bins=[-1e9,30,120,360,720,1440,1e9]; labs=['<30m','30m-2h','2-6h','6-12h','12-24h','24h+']
c['tb']=pd.cut(c.minToStartFirst,bins,labels=labs)
for k,g in c.groupby('tb',observed=True): row('start in '+str(k),g)
print('--- by kind'); 
for k,g in c.groupby('kind'): row(str(k),g)
print('--- by league')
for k,g in c.groupby('league'): 
    if len(g)>=40: row(str(k),g)
print('--- by src')
for k,g in c.groupby('src'): 
    if len(g)>=40: row(str(k),g)
print('--- by EV band listed')
c['evb']=pd.cut(c.ev,[-1,0.01,0.02,0.03,0.04,0.06,1],labels=['<1','1-2','2-3','3-4','4-6','6+'])
for k,g in c.groupby('evb',observed=True): row('EV '+str(k),g)
print('--- by books behind fair')
c['bk']=pd.cut(c.cnoBooks,[-1,2,3,4,6,9,100],labels=['<=2','3','4','5-6','7-9','10+'])
for k,g in c.groupby('bk',observed=True): row('books '+str(k),g)
print('--- by available $')
c['av']=pd.cut(c.available,[-1,10,25,50,100,250,1e9],labels=['<10','10-25','25-50','50-100','100-250','250+'])
for k,g in c.groupby('av',observed=True): row('avail '+str(k),g)
print('--- listedMin (how long stayed listed)')
c['lm']=pd.cut(c.listedMin,[-1,0.5,2,10,60,1e9],labels=['<30s','30s-2m','2-10m','10-60m','1h+'])
for k,g in c.groupby('lm',observed=True): row('listed '+str(k),g)
print('--- hour of day first seen (ET)')
for k,g in c.groupby(pd.cut(c.hour,[-1,5,11,17,23],labels=['0-5','6-11','12-17','18-23']),observed=True): row('hour '+str(k),g)
print('--- clv at first vs best vs last')
print('first',c.clv.mean(),'best',c.clvBest.mean(),'last',c.clvLast.mean())
print('--- sharp verdict'); 
for k,g in c.groupby(c.sharpVerdict.fillna('none')): row(str(k),g)
print('--- gone'); 
for k,g in c.groupby(c.gone.fillna('n/a')): row(str(k),g)
c.to_pickle('clv.pkl')
