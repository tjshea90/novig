# When did the engine start on 2026-10-19, and why not at 08:00 ET? (Tj, 2026-10-19 15:14Z)

**Answer:** 11:04 ET (run 39274341409, dispatched 15:02:03Z, job up 15:04:30Z, Novig address accepted on the first runner). It was started by Tj's phone: Vigilant pushed its settings at 15:01:54Z when he opened it (`settings rev 1792422112275` on cloud-config) and `CloudLink.maybeStart`/`requestScan` dispatched `cloud-scan.yml` 9 s later because the engine's heartbeat was dead.

**Why not 08:00 ET:** the night's engine ends at 01:00 ET by design, and the only thing that starts the morning engine is the `*/15` schedule of `cloud-scan.yml` (its gate checks the hours). GitHub fired that cron only a few times a day:

| workflow (cron) | Oct 17 | Oct 18 | Oct 19 |
| :- | -: | -: | -: |
| cloud-scan.yml (`*/15`, 96 expected) | 8 | 7 | 3 |
| engine-watchdog.yml (`*/5`, 288 expected) | 7 | 8 | 3 |
| deadman.yml (hourly, 24 expected) | 7 | 7 | 3 |

cloud-scan's schedule firings Oct 16-19 (UTC): 00:48 05:42 10:09 14:54 18:43 21:46 | 00:16 04:53 09:08 12:59 16:19 18:44 21:03 23:09 | 01:06 05:57 10:54 14:26 17:30 19:42 21:56 | 00:06 04:46 10:38. Today none fell between 12:00Z and 15:02Z; the 10:38Z one (06:38 ET) correctly declined. The watchdog ran once (14:32Z) and logged `watchdog: none no engine run in progress (the 15-minute schedule starts one)`: by design it never started a missing engine. GitHub's status page had no incident (scheduled events are best effort: "may be delayed or dropped during periods of high loads"). On Oct 17 and 18 the engine was up near 08:00 only because a dispatch (phone or session) happened to come.

**Fix (TASKS.md EA):** the morning start must not depend on cron: an alarm-clock workflow dispatched at the window's close waits on a free runner until 08:00 ET and dispatches the engine (workflow_dispatch is delivered, not dropped); the watchdog starts a missing engine inside the hours; the cron stays as a backstop.
