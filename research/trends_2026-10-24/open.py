import pickle,math,statistics as st,collections,sys
out=[r for r in pickle.load(open(sys.argv[1],'rb')) if r['op'] is not None]
def corr(a,b):
    ma,mb=st.mean(a),st.mean(b); sa=st.pstdev(a); sb=st.pstdev(b); return sum((x-ma)*(y-mb) for x,y in zip(a,b))/len(a)/(sa*sb)
for lab,S in (('train',[r for r in out if r['t']<'2026-09-01']),('test',[r for r in out if r['t']>='2026-09-01'])):
    g=[max(-2,min(2,r['gap'])) for r in S]
    mv=[r['fo']-r['op'] for r in S]; res=[r['won']-r['op'] for r in S]
    c1=corr(g,mv); c2=corr(g,res)
    print('%s n %d  gap vs open->close move r %+.4f z~%+.1f   gap vs result beyond OPEN r %+.4f z~%+.1f'%(lab,len(S),c1,c1*math.sqrt(len(S)),c2,c2*math.sqrt(len(S))))
    for name,f,side in (('avg>=line+.5sd Over',lambda r:r['gap']>=.5,'o'),('avg<=line-.5sd Under',lambda r:r['gap']<=-.5,'u')):
        sub=[r for r in S if f(r)]
        m=[(r['fo']-r['op']) if side=='o' else (r['op']-r['fo']) for r in sub]
        allm=[(r['fo']-r['op']) if side=='o' else (r['op']-r['fo']) for r in S]
        print('   %-22s n %5d  close moved its way %+.2f pts (all %s: %+.2f)'%(name,len(sub),100*st.mean(m),side,100*st.mean(allm)))
