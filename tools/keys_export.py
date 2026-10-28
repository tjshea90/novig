#!/usr/bin/env python3
"""Export every repository secret as ONE encrypted file (Tj, 2026-10-28, KX1).

The repo is public, so nothing here ever writes a key in clear: the secrets are
sealed with AES-256-GCM under a key from PBKDF2-SHA256(passphrase), and only the
holder of the passphrase (repo secret KEYS_EXPORT_PASSPHRASE, set by Tj) can open
the file. Decrypt with tools/keys_decrypt.html (offline, in any browser) or
`python3 -I tools/keys_export.py decrypt FILE`.
Usage: keys_export.py export OUT.json   (env ALL_SECRETS = toJSON(secrets), PASSPHRASE)
"""
import base64, getpass, json, os, secrets as rnd, sys
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

ITER = 600_000
SKIP = {"github_token", "keys_export_passphrase"}  # the run's own token and the passphrase never go in
SKIP_PREFIX = ("claude_fire_",)  # routine triggers, not API keys


def _key(pw: str, salt: bytes, it: int) -> bytes:
    return PBKDF2HMAC(hashes.SHA256(), 32, salt, it).derive(pw.encode())


def seal(items: dict, pw: str) -> dict:
    if len(pw) < 16:
        raise SystemExit("passphrase must be at least 16 characters")
    salt, iv = rnd.token_bytes(16), rnd.token_bytes(12)
    ct = AESGCM(_key(pw, salt, ITER)).encrypt(iv, json.dumps(items, sort_keys=True).encode(), b"vigilant-keys-v1")
    b = lambda x: base64.b64encode(x).decode()
    return {"v": 1, "kdf": "pbkdf2-sha256", "iter": ITER, "salt": b(salt), "iv": b(iv), "ct": b(ct)}


def open_(blob: dict, pw: str) -> dict:
    d = lambda k: base64.b64decode(blob[k])
    return json.loads(AESGCM(_key(pw, d("salt"), blob["iter"])).decrypt(d("iv"), d("ct"), b"vigilant-keys-v1"))


def collect(all_secrets_json: str) -> dict:
    return {k: v for k, v in json.loads(all_secrets_json).items()
            if v and k.lower() not in SKIP and not k.lower().startswith(SKIP_PREFIX)}


def selftest():
    items = collect(json.dumps({"A_KEY": "SECRETVALUE123", "github_token": "t", "KEYS_EXPORT_PASSPHRASE": "p", "EMPTY": "", "CLAUDE_FIRE_1": "z"}))
    assert items == {"A_KEY": "SECRETVALUE123"}, items
    blob = seal(items, "correct horse battery")
    assert open_(blob, "correct horse battery") == items
    try:
        open_(blob, "wrong wrong wrong wrong"); raise AssertionError("opened with wrong passphrase")
    except Exception as e:
        assert not isinstance(e, AssertionError)
    try:
        seal(items, "short"); raise AssertionError("short passphrase accepted")
    except SystemExit:
        pass
    assert "SECRETVALUE123" not in json.dumps(blob)
    print("keys_export selftest ok")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["selftest"]:
        selftest()
    elif a[:1] == ["export"] and len(a) == 2:
        items = collect(os.environ["ALL_SECRETS"])
        blob = seal(items, os.environ["PASSPHRASE"])
        open(a[1], "w").write(json.dumps(blob))
        print(f"sealed {len(items)} secrets: {', '.join(sorted(items))}")  # names only, never values
    elif a[:1] == ["decrypt"] and len(a) == 2:
        for k, v in sorted(open_(json.load(open(a[1])), getpass.getpass("passphrase: ")).items()):
            print(f"{k}={v}")
    else:
        raise SystemExit(__doc__)
