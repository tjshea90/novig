# BUILDLOG — the durable record of every release GitHub has actually built

One line per shipped version, written ONLY by `tools/record-release.sh`,
ONLY after the GitHub Actions run for that version is confirmed green. Never
add a line by hand and never before the run is green — see `tools/record-release.sh`'s
own header for why, and `CLAUDE.md`'s "Releasing" section for the full flow
this file is part of.

This file is empty because nothing has been built yet — there is no Android
project scaffold, so there is nothing to release. The first line lands the
day that changes.

| tag | code | when | what changed |
|-----|------|------|---------------|
| v0.1.0 | code 1 | 2026-09-20T16:13Z | First beta release — sample-data devig/EV scanner, no live credentials wired in yet
