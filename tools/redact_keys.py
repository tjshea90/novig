#!/usr/bin/env python3
"""Masks anything shaped like an API key in text before it is written to a committed file (INBOX.md).

Why: capture_inbox.sh commits every message Tj sends to a PUBLIC repo, and tools/secretscan.sh only knows a few providers' key
shapes (Anthropic, GitHub, Google, AWS, Slack). A ParlayAPI, Odds API, PropLine, PinnWire or Novig key pasted into chat would have
been pushed as-is (found 2026-09-30, when Tj offered to share his ParlayAPI key). So: any word of 24+ key characters that mixes
letters and digits (and isn't part of a URL path), and any value after key=/apiKey=/token=/secret=, is replaced by a marker that
keeps its last 4 characters (the same ones Diagnostics shows), so the message still reads.

Usage: python3 tools/redact_keys.py < text > text   (or import redact)
"""
import re
import sys

_PARAM = re.compile(r'(?i)\b((?:api_?key|apikey|x-api-key|key|token|secret|password)\s*[=:]\s*)([^\s&"\'`]{8,})')
_WORD = re.compile(r'(?<![/\w.@%-])[A-Za-z0-9][A-Za-z0-9_\-]{23,}(?![\w-]*/)')


def _mask(tok: str) -> str:
    return "[key redacted …" + tok[-4:] + "]"


def redact(text: str) -> str:
    text = _PARAM.sub(lambda m: m.group(1) + _mask(m.group(2)), text)

    def word(m: "re.Match[str]") -> str:
        t = m.group(0)
        if re.search(r"[0-9]", t) and re.search(r"[A-Za-z]", t):
            return _mask(t)
        return t

    return _WORD.sub(word, text)


if __name__ == "__main__":
    sys.stdout.write(redact(sys.stdin.read()))
