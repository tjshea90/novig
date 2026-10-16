import csv,gzip,collections,glob,statistics as st
def p(a):
    a=float(a); return 100/(a+100) if a>0 else -a/(-a+100)
def dec(a):
    a=float(a); return 1+a/100 if a>0 else 1+100/-a
pin={}
for f in glob.glob('resolved-*-pinnacle.csv.gz'):
    for r in csv.DictReader(gzip.open(f,'rt')):
        if not r['player_name'] or r['player_name'].startswith('(Water') or r['outcome_name'] not in('Over','Under') or not r['closing_price']: continue
        pin[(r['event_id'],r['market'],r['player_name'],r['closing_point'] or r['line'],r['outcome_name'])]=float(p(r['closing_price']))
out=collections.defaultdict(list)
for f in glob.glob('resolved-*-novig.csv.gz'):
    sp=f.split('-')[1]
    for r in csv.DictReader(gzip.open(f,'rt')):
        if not r['player_name'] or r['player_name'].startswith('(Water') or r['outcome_name'] not in('Over','Under') or not r['closing_price'] or r['resolution'] not in('won','lost'): continue
        pt=r['closing_point'] or r['line']
        a=pin.get((r['event_id'],r['market'],r['player_name'],pt,r['outcome_name']))
        b=pin.get((r['event_id'],r['market'],r['player_name'],pt,'Under' if r['outcome_name']=='Over' else 'Over'))
        if a is None or b is None: continue
        fair=a/(a+b); d=dec(r['closing_price']); edge=fair*d-1
        out[(r['outcome_name'])].append((edge,d,r['resolution']=='won',fair))
for side,v in out.items():
    print(side,len(v))
    for lo,hi in [(-1,-.05),(-.05,0),(0,.02),(.02,.04),(.04,.07),(.07,.12),(.12,5)]:
        s=[x for x in v if lo<=x[0]<hi]
        if len(s)>=30:
            roi=st.mean((x[1]-1) if x[2] else -1 for x in s)
            print('  stated edge vs Pinnacle close [%+.2f,%+.2f) n=%d  actual ROI %+.3f  win %.3f  fair %.3f'%(lo,hi,len(s),roi,sum(x[2] for x in s)/len(s),st.mean(x[3] for x in s)))
