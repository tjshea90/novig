import pickle,math,statistics as st,collections,sys
out=pickle.load(open(sys.argv[1],'rb'))
train=[r for r in out if r['t']<'2026-09-01']; test=[r for r in out if r['t']>='2026-09-01']
def logit(p): p=min(max(p,1e-4),1-1e-4); return math.log(p/(1-p))
def sig(x): return 1/(1+math.exp(-x))
def ll(S,b,key):
    return -st.mean(math.log(sig(logit(r['fo'])+b*max(-2,min(2,r[key])))) if r['won'] else math.log(1-sig(logit(r['fo'])+b*max(-2,min(2,r[key])))) for r in S)
for key in ('gap','hit','hitx'):
    best=min((ll(train,b/100,key),b/100) for b in range(-30,31,2))
    b=best[1]
    print('%-5s fitted b %+.2f (train)  test logloss close %.5f -> tilted %.5f  (diff %+.5f)'%(key,b,ll(test,0,key),ll(test,b,key),ll(test,b,key)-ll(test,0,key)))
# bootstrap the test improvement by game for 'gap'
import random
random.seed(1)
bg=min((ll(train,b/100,'gap'),b/100) for b in range(-30,31,2))[1]
byg=collections.defaultdict(list)
for r in test: byg[r['ev']].append(r)
gs=list(byg)
def d(sample):
    S=[r for g in sample for r in byg[g]]
    return ll(S,bg,'gap')-ll(S,0,'gap')
ds=sorted(d([random.choice(gs) for _ in gs]) for _ in range(200))
print('gap tilt test improvement 95%% CI by game: %+.5f .. %+.5f'%(ds[5],ds[194]))
# what the tilt is worth in edge points: mean |change in prob|
ch=[abs(sig(logit(r['fo'])+bg*max(-2,min(2,r['gap'])))-r['fo']) for r in test]
print('mean |fair change| %.2f pts, 90th pct %.2f pts'%(100*st.mean(ch),100*sorted(ch)[int(.9*len(ch))]))
# per sport fit on train, check on test
for sp in sorted({r['sport'] for r in out}):
    tr=[r for r in train if r['sport']==sp]; te=[r for r in test if r['sport']==sp]
    if len(tr)<2000 or len(te)<1000: continue
    b=min((ll(tr,x/100,'gap'),x/100) for x in range(-30,31,2))[1]
    print('  %-16s b %+.2f  test %d: %+.5f'%(sp,b,len(te),ll(te,b,'gap')-ll(te,0,'gap')))
