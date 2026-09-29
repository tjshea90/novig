# Third-party skills in this folder

These four skill folders are copied **unchanged** from Chris Banes' skills repository, under the Apache License 2.0
(`LICENSE-chrisbanes-skills.txt`, a copy of the upstream `LICENSE`):

- `compose-performance`
- `compose-state-and-effects`
- `kotlin-concurrency-and-flow`
- `compose-ui-testing-patterns`

Source: https://github.com/chrisbanes/skills, commit `359126d6c13b9fe9f04cd190538813c2dfeda107` (2026-09-26), copied
2026-09-29 at Tj's request (TASKS.md X2, RESEARCH.md §33.4). Each file was read in full before it was committed: the
skills are guidance only, with no `allowed-tools`, no inline shell blocks, no hooks, no scripts and no URLs.

A few links inside them point at upstream skills that were not copied (`compose-component-design`,
`compose-focus-navigation`, `kotlin-control-flow`); they don't resolve here, and nothing depends on them.

To update: copy the same folders from a newer upstream commit, read the diff before committing it, and change the
commit above. Don't edit the copied files in place; if a change is ever needed, Apache-2.0 requires marking the
modified files.
