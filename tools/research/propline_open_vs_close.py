import csv,gzip,collections,glob,statistics as st,sys
def p(a):
    a=float(a); return 100/(a+100) if a>0 else -a/(-a+100)
def dec(a):
    a=float(a); return 1+a/100 if a>0 else 1+100/-a
def pt(x):
    try: return round(float(x),3)
    except: return None
pin={}
for f in glob.glob('resolved-*-pinnacle.csv.gz'):
    for r in csv.DictReader(gzip.open(f,'rt')):
        if r['player_name'].startswith('(Water') or not r['closing_price'] or r['team']: continue
        pin[(r['event_id'],r['market'],r['player_name'],pt(r['closing_point'] if r['closing_point'] else r['line']),r['outcome_name'])]=p(r['closing_price'])
def other(side):
    return {'Over':'Under','Under':'Over'}.get(side)
res=collections.defaultdict(list)
for f in glob.glob('resolved-*-novig.csv.gz'):
    sport=f.split('-')[1]
    for r in csv.DictReader(gzip.open(f,'rt')):
        if r['player_name'].startswith('(Water') or r['team'] or not r['opening_price'] or r['resolution'] not in('won','lost'): continue
        m=r['market']; side=r['outcome_name']; player=r['player_name']; op=pt(r['opening_point'] if r['opening_point'] else r['line'])
        if m in ('h2h',): kind='h2h'
        elif m=='spreads': kind='spread'
        elif m=='totals': kind='total'
        elif player: kind='prop'
        else: continue
        if side in('Over','Under'): opp=other(side)
        else:
            # two-way h2h/spread: the other outcome is the other team; look it up by scanning the event's two outcomes
            opp=None
        a=pin.get((r['event_id'],m,player,op,side))
        if a is None: continue
        if opp is not None:
            b=pin.get((r['event_id'],m,player,op,opp))
            if b is None: continue
            fair=a/(a+b)
        else:
            # find the other side among pinnacle keys with same event/market/point (spreads point sign flips)
            cand=[v for k,v in pin.items() if False]
            continue
        d=dec(r['opening_price']); clv=fair*d-1
        win=r['resolution']=='won'
        sidek=side if side in('Over','Under') else 'side'
        res[(sport,kind,sidek)].append((clv,d,win))
        res[('ALL',kind,sidek)].append((clv,d,win))
def line(k,v):
    pos=[x for x in v if x[0]>=0.02]
    roi=st.mean((x[1]-1) if x[2] else -1 for x in v)
    s='%-34s n=%-6d meanCLV %+.3f  CLV>0 %3.0f%%  ROI@open %+.3f'%(k,len(v),st.mean(x[0] for x in v),100*sum(x[0]>0 for x in v)/len(v),roi)
    if len(pos)>=30: s+='   | CLV>=2%%: n=%d ROI %+.3f win %.3f'%(len(pos),st.mean((x[1]-1) if x[2] else -1 for x in pos),sum(x[2] for x in pos)/len(pos))
    return s
for k,v in sorted(res.items(),key=lambda kv:(kv[0][0]!='ALL',kv[0])):
    if len(v)>=300: print(line('/'.join(k),v))
