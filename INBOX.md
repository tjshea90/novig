# INBOX — every message Tj sends, captured verbatim, before anyone reads it

This file exists for one failure mode: a session reads a request, starts
working, and is cut off by a usage cap before it ever writes that request
into `TASKS.md`. Without this file, the request is gone — not "hard to
find", gone, because nothing on disk ever said it happened.

A `UserPromptSubmit` hook (`tools/capture_inbox.sh`) appends every message
here, verbatim, and commits+pushes it the instant it arrives — before any
tool call, before any judgment about whether it's "worth" saving yet. See
`CLAUDE.md`'s "When Tj asks for something new" for the full reasoning
(ported from fantasy-football, where this gap caused a real loss on
2026-09-15).

This is not a substitute for `TASKS.md`. `TASKS.md` is the plan, written in
Tj's own words as real steps; this is the unfiltered raw log underneath it.
`tools/resume.sh` prints this file's tail at the start of every session
specifically so a request that never made it into `TASKS.md` is never missed
twice.

The first entry below predates the hook itself — it is the request that
caused this whole system to be built, written here by hand as an honest
record of what was actually asked, since the hook did not exist yet to
capture it automatically. Every entry after it is hook-captured.

## 2026-09-20 (recorded by hand — the hook did not exist yet)
```
All work will be done in the novig repo. Do not touch or make any changes to
the other repos. They are to be read only. I'm starting a new project called
novig. It will be an android 16 app optimized for a moto g 2026. The purpose
of the app is to profit using the novig sports book. I will have Claude make
the code, and trigger GitHub actions to sign and make the apk, then Claude
send me a link to the finished APK.

To begin, all work on this project needs to have a strong checkpoint system
in place to save all progress and work without losing any data even if
Claude usage is interrupted in the middle of a task.

For this, review the linked repos called fantasy-football and portfolio.
From those two repos, copy and adapt the checkpoint system for this
project. Look for all the parts of the repos that have checkpoint and data
save systems and figure out how they work then implement it in the novig
repo. Make sure everything is adapted for novig, because a lot of things you
read and copy only apply to the other projects. The checkpoint system you
make should be based on the other repos but not exact copies because you may
need to take out or alter wording or logic specific to those repos that
don't apply here.

Also copy and adapt the "run light test" and "run full test" systems in the
portfolio repo
```

## 2026-09-20T04:36:03Z
```
Research methods this app can use to find positive ev for novig sports book odds constantly updating in real time to capture odds movements and new positive EV bets. Currently I'm using oddsjam, but I cannot afford the subscription. Look for a way to do something just like oddsjam, but either free or less than 30 dollars per month. My goal is to build an app that is equal to or better than oddsjam at finding positive EV bets on novig. Begin deep research across the internet on what is needed for this app and how to do it so we can begin building it. Maybe make a research findings file permanently on GitHub in this repo so all research is saved. 
```

## 2026-09-20T05:01:28Z
```
Research odds assist pro. Does it actually find positive EV on novig? Is it as good as oddsjam?
```

## 2026-09-20T05:37:41Z
```
Begin basic coding of this app. Give it a catchy name, not something boring like "novig ev". Make a basic beta of the app, which should be able to act like oddsjam by devigging odds and using sharp books like Pinnacle or circa if possible or an average of major sports books. It should be able to pull this data real time or as frequent as possible to catch actual positive EV and not stale odds. Let me know if I need to do anything
```

## 2026-09-20T06:04:01Z
```
<task-notification>
<task-id>b5zwfkbnz</task-id>
<summary>Monitor event: "Poll GitHub Actions CI run for the setup-android fix commit until it completes"</summary>
<event>found run_id=35493239477 for commit 3db77be</event>
</task-notification>
```

## 2026-09-20T06:04:05Z
```
<task-notification>
<task-id>b5zwfkbnz</task-id>
<summary>Monitor event: "Poll GitHub Actions CI run for the setup-android fix commit until it completes"</summary>
<event>run 35493239477: status=in_progress conclusion=null</event>
</task-notification>
```

## 2026-09-20T06:04:38Z
```
<task-notification>
<task-id>b5zwfkbnz</task-id>
<summary>Monitor event: "Poll GitHub Actions CI run for the setup-android fix commit until it completes"</summary>
<event>run 35493239477: status=in_progress conclusion=null</event>
</task-notification>
```
