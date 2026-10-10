#!/usr/bin/env bash
# archive-uploads.sh — copy every file Tj sent this session (Claude Code's upload folder) into the repo, so none is lost when the container is reclaimed.
#
# Tj, 2026-10-09: "Save as much data and research as possible into the GitHub repo and make sure other Claude sessions do so as well".
# The diagnostics files, research files, scan studies, SGO/OddsPapi samples, crash files and screenshots he sends arrive in /root/.claude/uploads/<session>/ — an ephemeral
# container disk. Text files are gzipped into research/uploads/<YYYY-MM-DD of the file>/<name>.gz; images are copied as they are. Idempotent (a name already there is skipped),
# quiet, and it never fails the caller (hooks call it). A file with something shaped like a live credential (the same patterns as tools/secretscan.sh) is NOT copied and says so.
# Files over 60 MB after compression are skipped with a note (GitHub refuses 100 MB; put such a file in a Release by workflow instead).
#
#   bash tools/archive-uploads.sh              # the upload folders under /root/.claude/uploads
#   bash tools/archive-uploads.sh FILE...      # these files
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$D" || exit 0
SRC=()
if [ "$#" -gt 0 ]; then SRC=("$@"); else
  for d in /root/.claude/uploads/*/ /root/.claude/uploads/; do
    [ -d "$d" ] || continue
    for f in "$d"*; do [ -f "$f" ] && SRC+=("$f"); done
  done
fi
[ "${#SRC[@]}" -eq 0 ] && exit 0
PAT='sk-ant-[A-Za-z0-9_-]{40,}|sk-[A-Za-z0-9_-]{32,}|ghp_[A-Za-z0-9]{30,}|gho_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AIza[0-9A-Za-z_-]{30,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]{10,}|-----BEGIN [A-Z ]*PRIVATE KEY-----'
n=0
for f in "${SRC[@]}"; do
  [ -f "$f" ] || continue
  base="$(basename "$f")"
  day="$(date -u -r "$f" +%Y-%m-%d 2>/dev/null || date -u +%Y-%m-%d)"
  dir="research/uploads/$day"
  case "$base" in
    *.png|*.jpg|*.jpeg|*.gif|*.webp|*.gz|*.zip) out="$dir/$base"; plain=0 ;;
    *) out="$dir/$base.gz"; plain=1 ;;
  esac
  [ -e "$out" ] && continue
  if [ "$plain" -eq 1 ] && grep -aEq -e "$PAT" "$f" 2>/dev/null; then
    echo "  ARCHIVE  skipped $base: it looks like it holds a live credential (not copied; remove it and run again)"; continue
  fi
  mkdir -p "$dir"
  if [ "$plain" -eq 1 ]; then gzip -9 -c "$f" > "$out.tmp" 2>/dev/null; else cp "$f" "$out.tmp" 2>/dev/null; fi
  sz="$(stat -c %s "$out.tmp" 2>/dev/null || echo 0)"
  if [ "$sz" -gt $((60*1024*1024)) ]; then rm -f "$out.tmp"; echo "  ARCHIVE  skipped $base: $((sz/1048576)) MB is too big for git (use a Release)"; continue; fi
  mv "$out.tmp" "$out" && n=$((n+1))
done
[ "$n" -gt 0 ] && echo "  ARCHIVE  $n file(s) from Tj saved to research/uploads/ (autosave pushes them)"
exit 0
