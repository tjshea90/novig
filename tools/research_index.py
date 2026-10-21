#!/usr/bin/env python3
"""research_index.py - the research library's catalogue and its "is it used?" audit (Tj, 2026-10-21).

Tj: make sure every research project, this session's and past ones, is organized, remembered, reachable at any
time, and USED to tune the engine for profit, not idle on GitHub.

  python3 -I tools/research_index.py build      write research/INDEX.md (every study: date, title, one line, and how it is used)
  python3 -I tools/research_index.py check      exit 1 if INDEX.md is stale or a LEVERS.md study file is missing
  python3 -I tools/research_index.py brief      one briefing line for resume.sh (counts, idle studies)
  python3 -I tools/research_index.py selftest

Status of a study, decided from the repo itself (nothing hand-maintained that can rot):
  APPLIED  named in research/review/CHANGES.md (a rule or setting was changed on its evidence)
  LEVER    named in research/LEVERS.md (it governs a tuning lever with a next-evidence number)
  CLOSED   listed under "## Closed" in LEVERS.md with its disposition (verdict taken, nothing left to tune)
  WATCH    named in an open TASKS.md item (a number still has to settle it)
  CODE     named in source, tools or CLAUDE.md/BRIEF.md (its finding is built into the app or a standing rule)
  IDLE     named nowhere: read it, then either apply it (CHANGES.md), put it on a lever, or mark it REFERENCE in its first lines
A study whose first 12 lines contain the word REFERENCE (an API sample, a raw output) is never counted idle.
"""
import os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
R = os.path.join(ROOT, "research")
SKIP_DIRS = {"uploads", "lab", "coordination", "review"}   # data, the lab's own log, the team's protocol, the review log
DATE = re.compile(r"(20\d\d-\d\d-\d\d)")


def read(p, n=None):
    try:
        with open(p, errors="replace") as f:
            return f.read() if n is None else f.read(n)
    except OSError:
        return ""


def entries():
    """(name, path-for-reading, is_dir): top-level studies of research/, sorted by name."""
    out = []
    for n in sorted(os.listdir(R)):
        p = os.path.join(R, n)
        if n.startswith(".") or n in SKIP_DIRS or n in ("INDEX.md", "LEVERS.md"):
            continue
        if os.path.isdir(p):
            doc = next((os.path.join(p, d) for d in ("README.md", "NOTES.md", "SUMMARY.md") if os.path.exists(os.path.join(p, d))), None)
            if doc is None:
                mds = sorted(x for x in os.listdir(p) if x.endswith(".md"))
                doc = os.path.join(p, mds[0]) if mds else None
            out.append((n, doc, True))
        else:
            out.append((n, p, False))
    return out


def title_and_line(doc):
    title, line = "", ""
    for raw in read(doc, 6000).splitlines():
        s = raw.strip()
        if not s:
            continue
        if not title and s.startswith("#"):
            title = s.lstrip("# ").strip()
            continue
        if title and not s.startswith("#") and len(s) > 30:
            line = s
            break
    return title, line


def haystacks():
    """The texts a study can be named in, by use. A study is referenced by its file name (or folder name)."""
    def t(*paths):
        return "\n".join(read(os.path.join(ROOT, p)) for p in paths)
    changes = t("research/review/CHANGES.md")
    levers = t("research/LEVERS.md")
    tasks = read(os.path.join(ROOT, "TASKS.md"))
    open_tasks = "\n".join(l for l in tasks.splitlines() if re.match(r"\s*- \[ \]", l))
    docs = t("CLAUDE.md", "BRIEF.md", "RESEARCH.md", "research/review/ROUTINE.md")
    code = ""
    try:
        code = subprocess.run(["git", "-C", ROOT, "grep", "-h", "-I", "research/", "--", "*.kt", "*.py", "*.sh", "*.yml", "*.json"],
                              capture_output=True, text=True, timeout=60).stdout
    except Exception:
        pass
    return changes, levers, open_tasks, docs + "\n" + code


def status(name, doc, hs):
    changes, levers, open_tasks, other = hs
    stem = name
    tags = []
    if stem in changes: tags.append("APPLIED")
    head, _, closed = levers.partition("## Closed")
    if stem in head: tags.append("LEVER")
    if stem in closed: tags.append("CLOSED")
    if stem in open_tasks: tags.append("WATCH")
    if stem in other: tags.append("CODE")
    if not tags and "REFERENCE" in read(doc, 1500) if doc else False:
        tags.append("REFERENCE")
    return tags or ["IDLE"]


def build_text():
    hs = haystacks()
    rows, idle = [], []
    for name, doc, isdir in entries():
        title, line = title_and_line(doc) if doc and doc.endswith(".md") else ("", "")
        m = DATE.search(name) or (DATE.search(read(doc, 800)) if doc else None)
        date = m.group(1) if m else ""
        tags = status(name, doc, hs)
        if tags == ["IDLE"]:
            idle.append(name)
        rows.append((date, name + ("/" if isdir else ""), title or "(no title)", line, tags))
    rows.sort(key=lambda r: (r[0] or "0000", r[1]), reverse=True)
    out = ["# Research index (generated by tools/research_index.py build; do not edit by hand)", "",
           "Every study in `research/`, newest first, and how it is USED. Tuning levers and the number that settles each: `research/LEVERS.md`.",
           "Statuses: APPLIED (a change in `research/review/CHANGES.md` cites it), LEVER (on LEVERS.md), CLOSED (verdict and disposition on LEVERS.md), WATCH (an open TASKS.md item waits on its number),",
           "CODE (built into the app/engine/tools or a standing rule), REFERENCE (data or a sample, marked in its first lines), IDLE (cited nowhere: act on it or mark it).", "",
           "%d entries; %d idle%s" % (len(rows), len(idle), (": " + ", ".join(idle)) if idle else ""), "",
           "| Date | Study | What it found | Used as |", "|---|---|---|---|"]
    for date, name, title, line, tags in rows:
        what = (title + (" - " + line if line else "")).replace("|", "/")
        if len(what) > 230:
            what = what[:227] + "..."
        out.append("| %s | `%s` | %s | %s |" % (date, name, what, " ".join(tags)))
    return "\n".join(out) + "\n", len(rows), idle


def lever_files():
    miss = []
    for m in re.finditer(r"`(research/[^`\s]+)`", read(os.path.join(R, "LEVERS.md"))):
        p = m.group(1).rstrip(".,;:")
        if "*" in p or "<" in p:
            continue
        if not os.path.exists(os.path.join(ROOT, p)):
            miss.append(p)
    return miss


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "brief"
    if cmd == "build":
        text, n, idle = build_text()
        with open(os.path.join(R, "INDEX.md"), "w") as f:
            f.write(text)
        print("INDEX.md: %d studies, %d idle" % (n, len(idle)))
        return 0
    if cmd == "check":
        text, n, idle = build_text()
        miss = lever_files()
        # only the entries and their count must be current; statuses move as other files change
        have = [l.split("`")[1] for l in read(os.path.join(R, "INDEX.md")).splitlines() if l.startswith("| ") and "`" in l]
        want = [l.split("`")[1] for l in text.splitlines() if l.startswith("| ") and "`" in l]
        if have != want:
            print("FAIL research/INDEX.md lists %d studies, research/ holds %d: run python3 -I tools/research_index.py build" % (len(have), len(want)))
            return 1
        if miss:
            print("FAIL LEVERS.md names a missing file: " + ", ".join(miss[:5]))
            return 1
        print("ok   research index (%d studies, %d idle)" % (n, len(idle)))
        return 0
    if cmd == "brief":
        text, n, idle = build_text()
        print("  RESEARCH %d studies indexed (research/INDEX.md), tuning levers + the number that settles each: research/LEVERS.md%s" %
              (n, "; %d IDLE (cited nowhere, act or mark): %s" % (len(idle), ", ".join(idle[:4])) if idle else "; none idle"))
        return 0
    if cmd == "selftest":
        assert DATE.search("x_2026-10-21.md").group(1) == "2026-10-21"
        hs = ("see clv_consensus_2026-10-21.md", "", "- [ ] XOW9 read nhl_sog_matching_2026-10-13.md", "")
        assert status("clv_consensus_2026-10-21.md", None, hs) == ["APPLIED"]
        assert status("nhl_sog_matching_2026-10-13.md", None, hs) == ["WATCH"]
        assert status("never_cited.md", None, hs) == ["IDLE"]
        text, n, idle = build_text()
        assert n > 50 and "| Date | Study |" in text
        print("selftest ok")
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
