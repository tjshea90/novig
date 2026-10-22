# PinnWire / pinnapi were asked every scan though SharpAPI streams Pinnacle (2026-10-22, Tj chat t1792705696949-3544)

Tj: more PinnWire keys are coming; usage may be spent as needed "but you must make sure the usage is not being wasted if Pinnacle can be read elsewhere just as fresh or more fresh".

Finding: `ApiChain` said "Pinnacle's own hosts are not on a chain", so `pinnacle` (PinnWire, pinnapi, Pinnodds REST) was asked for every league every scan. At 21:38 UTC 11 of 12 keys were at 0/100. SharpAPI (Sharp plan) delivers Pinnacle by push (median 0.8 s, SHARPAPI.md) into the engine's store.

Change: `ApiChain.better("pinnacle")` = the SharpAPI feeds that are on, and `ApiChain.pinnacleNeeded` asks the hosts for a league only when SharpAPI did not answer it, sent no `pinnacle` book for it, left a Novig game without game lines, or player props are on, Pinnacle has props for the sport and SharpAPI sent no Pinnacle prop (`ScanContext.pinnacleProps`). SharpAPI off or failing = the hosts are asked as before. Test: `PinnacleHostsChainTest`.
Revert: remove the `PINNACLE_HOSTS` lines in `ApiChain.better/needed`.
Measure next: the `PinnWire accounts:` log line (left/100) a day later; fewer keys at 0 while the same leagues are priced.
