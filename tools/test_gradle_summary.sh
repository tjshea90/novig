#!/usr/bin/env bash
# tools/test_gradle_summary.sh — fast check (tools/ckpt.sh and ship.sh run every tools/test_*.sh).
# tools/gradle_summary.py is what tools/test.sh shows of a Gradle test run, so it must name every
# failing test with its message, show the compiler's errors when no test ran, say when a task was
# up to date, count only the tasks the log names, and never call a failed run a pass.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
FAILS=0

check() { # name, output, expected text
  if grep -qF -- "$3" <<<"$2"; then
    echo "  ok    $1"
  else
    echo "  FAIL  $1: expected \"$3\" in:"
    sed 's/^/        /' <<<"$2"
    FAILS=$((FAILS + 1))
  fi
}
summary() { (cd "$T" && python3 "$ROOT/tools/gradle_summary.py" "$1" "$2" 7) }

mkdir -p "$T/engine/build/test-results/test" "$T/data/build/test-results/test" "$T/app/build/test-results/testDebugUnitTest"
cat > "$T/engine/build/test-results/test/TEST-x.DevigTest.xml" <<'XML'
<testsuite name="x.DevigTest" tests="2"><testcase classname="x.DevigTest" name="a"/><testcase classname="x.DevigTest" name="b"/></testsuite>
XML
cat > "$T/data/build/test-results/test/TEST-x.CnoChecksTest.xml" <<'XML'
<testsuite name="x.CnoChecksTest" tests="3">
  <testcase classname="x.CnoChecksTest" name="games pass"/>
  <testcase classname="x.CnoChecksTest" name="live only"><skipped/></testcase>
  <testcase classname="x.CnoChecksTest" name="futures are left out"><failure message="expected:&lt;2&gt; but was:&lt;3&gt;">java.lang.AssertionError: expected:&lt;2&gt; but was:&lt;3&gt;
	at x.CnoChecksTest.futures(CnoChecksTest.kt:41)</failure></testcase>
</testsuite>
XML
cat > "$T/app/build/test-results/testDebugUnitTest/TEST-x.ScreenshotTest.xml" <<'XML'
<testsuite name="x.ScreenshotTest" tests="1"><testcase classname="x.ScreenshotTest" name="feed"/></testsuite>
XML

# 1. A red test: named with its message; the up-to-date module still counted; app not in the log, so not counted.
printf '%s\n' '> Task :engine:test UP-TO-DATE' '> Task :data:compileKotlin' '> Task :data:test FAILED' \
  'CnoChecksTest > futures are left out FAILED' 'BUILD FAILED in 7s' > "$T/red.log"
OUT="$(summary "$T/red.log" 1)"
check "up-to-date task marked" "$OUT" ":engine:test             2 passed  (up to date: not rerun, last results)"
check "per-task counts" "$OUT" ":data:test               1 passed, 1 skipped, 1 FAILED"
check "failing test named" "$OUT" "FAIL  data: CnoChecksTest > futures are left out"
check "failure message shown" "$OUT" "expected:<2> but was:<3>"
check "red verdict" "$OUT" "FAIL  3 of 5 tests passed in 7 s (Gradle exit 1"
if grep -q ScreenshotTest <<<"$OUT" || grep -q ':app:' <<<"$OUT"; then
  echo "  FAIL  a task the log doesn't name was counted"; FAILS=$((FAILS + 1))
else
  echo "  ok    only the log's tasks counted"
fi

# 2. A compile error: no test ran, so the compiler's own lines are what's shown.
printf '%s\n' '> Task :data:compileKotlin FAILED' "e: file:///repo/data/src/main/kotlin/Foo.kt:3:5 Unresolved reference 'bar'." \
  '* What went wrong:' "Execution failed for task ':data:compileKotlin'." '* Try:' 'BUILD FAILED in 7s' > "$T/compile.log"
OUT="$(summary "$T/compile.log" 1)"
check "compiler error shown" "$OUT" "e: file:///repo/data/src/main/kotlin/Foo.kt:3:5 Unresolved reference 'bar'."
check "no test ran, still red" "$OUT" "FAIL  0 of 0 tests passed"

# 3. Any other failure: Gradle's "What went wrong" block.
printf '%s\n' '* What went wrong:' 'Could not resolve all files for configuration :app:debugRuntimeClasspath.' \
  '* Try:' '> Run with --stacktrace' 'BUILD FAILED in 7s' > "$T/other.log"
OUT="$(summary "$T/other.log" 1)"
check "what went wrong shown" "$OUT" "Could not resolve all files for configuration :app:debugRuntimeClasspath."

# 4. Green: every task's line, and a pass.
printf '%s\n' '> Task :engine:test' '> Task :app:testDebugUnitTest' 'BUILD SUCCESSFUL in 7s' > "$T/green.log"
OUT="$(summary "$T/green.log" 0)"
check "green task line" "$OUT" ":app:testDebugUnitTest   1 passed"
check "green verdict" "$OUT" "PASS  3 of 3 tests passed in 7 s (Gradle exit 0"

if [ "$FAILS" -gt 0 ]; then
  echo "  FAIL  $FAILS check(s) on tools/gradle_summary.py"
  exit 1
fi
echo "  OK    tools/gradle_summary.py reports runs correctly"
