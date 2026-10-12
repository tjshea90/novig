# TAN1: full test floor (2026-10-12, before any TAN change; HEAD = v0.89.34 + docs)
`bash tools/test.sh` (engine, lab, data, app unit tests) -> **PASS 3245 tests: 3216 passed, 29 skipped, 405 s, Gradle exit 0.**
- :engine:test 54 passed · :lab:test 2 passed · :data:test 2081 passed, 23 skipped · :app:testDebugUnitTest 1079 passed, 6 skipped.
- Skips are the `VIGILANT_LIVE`-gated live tests and the screenshot tests (`-Pscreenshots`), run separately at the end of TAN (screenshots + final regression).
Final regression for the TAN ship is recorded below (append, do not overwrite).
