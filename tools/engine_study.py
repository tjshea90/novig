#!/usr/bin/env python3
"""Puts the engine's scan-study segments back together (Tj, 2026-10-15, TCL20e).

Each engine run writes its own segment of the scan study to the day's cloud-archive Release as study-<game day>-run<id>.jsonl.gz, and each grader run writes the
result lines it added as grades-<game day>-run<id>.jsonl.gz (release `engine-grades`). Nothing is ever rewritten. This joins them for ScanStudy / Review:

    python3 -I tools/engine_study.py assemble <dir of downloaded *.jsonl.gz> <out dir>   -> <out dir>/study-<day>.jsonl  (study segments by run id, then grade segments by run id)
    python3 -I tools/engine_study.py pack <dir of grades-<day>.jsonl> <run id> <out dir>  -> grades-<day>-run<id>.jsonl.gz for upload
    python3 -I tools/engine_study.py selftest
"""
import gzip, os, re, sys, tempfile

NAME = re.compile(r"^(study|grades)-(\d{4}-\d{2}-\d{2})-run(\d+)\.jsonl\.gz$")


def assemble(src, out):
    os.makedirs(out, exist_ok=True)
    days = {}
    for f in os.listdir(src):
        m = NAME.match(f)
        if m:
            kind, day, run = m.group(1), m.group(2), int(m.group(3))
            days.setdefault(day, []).append((0 if kind == "study" else 1, run, f))
    n = 0
    for day, parts in sorted(days.items()):
        with open(os.path.join(out, f"study-{day}.jsonl"), "wb") as w:
            for _, _, f in sorted(parts):
                try:
                    data = gzip.open(os.path.join(src, f)).read()
                except Exception as e:  # a torn upload is skipped, never fatal
                    print(f"skipped {f}: {e}", file=sys.stderr)
                    continue
                if data and not data.endswith(b"\n"):
                    data += b"\n"
                w.write(data)
                n += 1
    print(f"assembled {n} segments into {len(days)} day files")


def pack(src, run, out):
    os.makedirs(out, exist_ok=True)
    n = 0
    for f in os.listdir(src):
        m = re.match(r"^grades-(\d{4}-\d{2}-\d{2})\.jsonl$", f)
        if not m:
            continue
        data = open(os.path.join(src, f), "rb").read()
        if not data.strip():
            continue
        with gzip.open(os.path.join(out, f"grades-{m.group(1)}-run{run}.jsonl.gz"), "wb", 9) as w:
            w.write(data)
        n += 1
    print(f"packed {n} grade files")


def selftest():
    with tempfile.TemporaryDirectory() as t:
        src, out, new, packed = (os.path.join(t, d) for d in ("src", "out", "new", "packed"))
        os.makedirs(src)
        os.makedirs(new)
        def put(name, text):
            with gzip.open(os.path.join(src, name), "wb") as w:
                w.write(text.encode())
        put("study-2026-10-15-run20.jsonl.gz", "B2\n")          # a later run
        put("study-2026-10-15-run9.jsonl.gz", "B1")             # an earlier run, no trailing newline
        put("grades-2026-10-15-run30.jsonl.gz", "R1\n")
        put("study-2026-10-14-run9.jsonl.gz", "A\n")
        put("notes.txt", "ignored")
        open(os.path.join(src, "study-2026-10-16-run1.jsonl.gz"), "wb").write(b"torn")
        assemble(src, out)
        assert open(os.path.join(out, "study-2026-10-15.jsonl")).read() == "B1\nB2\nR1\n", "run ids sort as numbers, studies before grades"
        assert open(os.path.join(out, "study-2026-10-14.jsonl")).read() == "A\n"
        assert open(os.path.join(out, "study-2026-10-16.jsonl")).read() == "", "a torn segment is skipped"
        open(os.path.join(new, "grades-2026-10-15.jsonl"), "w").write("R2\n")
        open(os.path.join(new, "grades-2026-10-14.jsonl"), "w").write("")
        pack(new, 77, packed)
        assert os.listdir(packed) == ["grades-2026-10-15-run77.jsonl.gz"], "an empty grade file is not packed"
    print("engine_study selftest: ok")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["assemble"] and len(a) == 3: assemble(a[1], a[2])
    elif a[:1] == ["pack"] and len(a) == 4: pack(a[1], a[2], a[3])
    elif a[:1] == ["selftest"]: selftest()
    else: print(__doc__); sys.exit(2)
