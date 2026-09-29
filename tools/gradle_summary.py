#!/usr/bin/env python3
"""The short summary tools/test.sh prints after a Gradle test run (tools/test_gradle_summary.sh checks it).

Usage: python3 tools/gradle_summary.py <gradle log> <gradle exit code> <seconds>, run from the repo root:
reads each test task's JUnit XML under <module>/build/test-results/<task>/ for the test tasks the log
names. The verdict line follows Gradle's exit code, never the parsed results alone.
"""
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
