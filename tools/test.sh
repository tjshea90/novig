#!/usr/bin/env bash
# tools/test.sh — run Gradle's tests and print a short summary instead of Gradle's whole log.
#
#   bash tools/test.sh                                   the floor: :engine:test :data:test :app:testDebugUnitTest
#   bash tools/test.sh :data:test                        one module
#   bash tools/test.sh :data:test --tests '*CnoChecksTest'
#   bash tools/test.sh -Pscreenshots :app:testDebugUnitTest      PNGs of every screen in app/screenshots/
#   VIGILANT_LIVE=1 bash tools/test.sh :data:test --tests '*LiveNovigSmokeTest'
#
# Any Gradle arguments pass through. Prints one line per test task (passed, skipped, failed, or
# "up to date" when Gradle didn't need to rerun it), then only what a failure needs: each failing
# test with the first lines of its message, else the compiler's errors or Gradle's "What went
# wrong". The full log is in /tmp/vigilant-test.log; the exit code is Gradle's. `--continue` is
# always on, so one run reports every module's failures. ANDROID_HOME defaults to
# /opt/android-sdk; with no SDK there, :app's tasks are left out (CI runs them) and it says so.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
LOG=/tmp/vigilant-test.log
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"

ARGS=()
HAS_TASK=0
HAS_FILTER=0
PREV=""
for a in "$@"; do
  case "$PREV" in
    --tests|-x|--exclude-task) ARGS+=("$a"); PREV=""; continue ;;
  esac
  case "$a" in
    --tests) HAS_FILTER=1 ;;
    -*) ;;
    *) HAS_TASK=1 ;;
  esac
  ARGS+=("$a")
  PREV="$a"
done
if [ "$HAS_TASK" = 0 ] && [ "$HAS_FILTER" = 1 ]; then
  echo "  name the test task with --tests, e.g.: bash tools/test.sh :data:test --tests '*CnoChecksTest'"
  exit 2
fi
if [ -d "$ANDROID_HOME/platforms" ]; then
  # The floor is `test`, exactly what CI runs: every module's unit tests (app's are debug only).
  [ "$HAS_TASK" = 0 ] && ARGS=(test "${ARGS[@]}")
else
  [ "$HAS_TASK" = 0 ] && ARGS=(:engine:test :data:test :app:testDebugUnitTest "${ARGS[@]}")
  KEPT=()
  DROPPED=0
  for a in "${ARGS[@]}"; do
    case "$a" in :app:*|test|check) DROPPED=1 ;; *) KEPT+=("$a") ;; esac
  done
  [ "$DROPPED" = 1 ] && echo "  note  no Android SDK at $ANDROID_HOME (bash tools/setup-android.sh): :app's tests left out, CI runs them"
  ARGS=(--configure-on-demand "${KEPT[@]}")
fi

START=$(date +%s)
./gradlew "${ARGS[@]}" --continue --console=plain > "$LOG" 2>&1
RC=$?
SECS=$(( $(date +%s) - START ))

python3 - "$LOG" "$RC" "$SECS" <<'PY'
import glob, os, re, sys, xml.etree.ElementTree as ET

log_path, rc, secs = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
log = open(log_path, encoding="utf-8", errors="replace").read().splitlines()

# Test tasks this run touched: "> Task :data:test" (+ " UP-TO-DATE" / " FAILED" / ...) whose
# results directory exists.
tasks = {}
for line in log:
    m = re.match(r"> Task :([\w-]+):(\w+)(?: (\S+))?$", line)
    if m and os.path.isdir(f"{m.group(1)}/build/test-results/{m.group(2)}"):
        tasks[(m.group(1), m.group(2))] = m.group(3) or ""

total = passed = 0
failures = []
for (mod, task), state in tasks.items():
    t = f = s = 0
    for p in sorted(glob.glob(f"{mod}/build/test-results/{task}/*.xml")):
        try:
            root = ET.parse(p).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            t += 1
            bad = case.find("failure")
            if bad is None:
                bad = case.find("error")
            if bad is not None:
                f += 1
                msg = (bad.get("message") or bad.text or "").strip().splitlines()
                failures.append((mod, case.get("classname", "").split(".")[-1], case.get("name", ""), msg[:4]))
            elif case.find("skipped") is not None:
                s += 1
    total += t
    passed += t - f - s
    parts = [f"{t - f - s} passed"]
    if s:
        parts.append(f"{s} skipped")
    if f:
        parts.append(f"{f} FAILED")
    note = {"UP-TO-DATE": "  (up to date: not rerun, last results)", "FROM-CACHE": "  (from cache)",
            "NO-SOURCE": "  (no tests)"}.get(state, "")
    name = f":{mod}:{task}"
    print(f"  {name:<24} {', '.join(parts)}{note}")

for mod, cls, name, msg in failures[:15]:
    print(f"  FAIL  {mod}: {cls} > {name}")
    for line in msg:
        print(f"          {line[:200]}")
if len(failures) > 15:
    print(f"  ...   {len(failures) - 15} more failing tests: grep -n FAILED {log_path}")

if rc != 0 and not failures:
    errs = [l for l in log if l.startswith("e: ")]
    for l in errs[:20]:
        print("  " + l[:240])
    if len(errs) > 20:
        print(f"  ...   {len(errs) - 20} more compiler errors in {log_path}")
    if not errs:
        out, on = [], False
        for l in log:
            if l.startswith("* What went wrong"):
                on = True
            elif l.startswith("* Try") or l.startswith("* Exception is"):
                on = False
            if on and l.strip():
                out.append(l)
        for l in (out or [l for l in log if l.strip()][-15:])[:20]:
            print("  " + l[:240])

verdict = "PASS" if rc == 0 else "FAIL"
print(f"  {verdict}  {passed} of {total} tests passed in {secs} s (Gradle exit {rc}; full log {log_path})")
PY
exit "$RC"
