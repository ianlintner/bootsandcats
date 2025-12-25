#!/usr/bin/env python3
"""Compare secure-subdomain-client secrets across K8s + DB without printing secrets.

Outputs only SHA-256 hashes and booleans.

Requires:
- kubectl context configured
- access to namespaces: default, aks-istio-ingress
"""

from __future__ import annotations

import base64
import hashlib
import re
import subprocess
import sys


def sh(cmd: list[str]) -> str:
    return subprocess.check_output(cmd, text=True).strip()


def b64decode_str(b64: str) -> bytes:
    return base64.b64decode(b64.encode("utf-8"))


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def get_k8s_secret_key_b64(ns: str, name: str, key_jsonpath: str) -> str:
    return sh(
        [
            "kubectl",
            "get",
            "secret",
            "-n",
            ns,
            name,
            "-o",
            f"jsonpath={{{key_jsonpath}}}",
        ]
    )


def get_db_client_secret_hash(client_id: str) -> str:
    # Query inside the Postgres primary pod (postgres-ha-2)
    return (
        sh(
            [
                "kubectl",
                "exec",
                "-n",
                "default",
                "postgres-ha-2",
                "-c",
                "postgres",
                "--",
                "psql",
                "-U",
                "postgres",
                "-d",
                "oauth2db",
                "-tAc",
                f"select client_secret from oauth2_registered_client where client_id='{client_id}';",
            ]
        )
        .replace("\r", "")
        .replace("\n", "")
    )


def extract_inline_string(yaml_text: str) -> bytes | None:
    # Example line:
    #   inline_string: "<secret>"
    # or
    #   inline_string: <secret>
    for line in yaml_text.splitlines():
        if line.strip().startswith("inline_string:"):
            rem = line.split("inline_string:", 1)[1].strip()
            if rem.startswith('"') and rem.endswith('"') and len(rem) >= 2:
                rem = rem[1:-1]
            return rem.encode("utf-8")
    return None


def extract_inline_bytes(yaml_text: str) -> bytes | None:
    # Example line:
    #   inline_bytes: <base64>
    for line in yaml_text.splitlines():
        if line.strip().startswith("inline_bytes:"):
            b64 = line.split("inline_bytes:", 1)[1].strip().strip('"')
            return base64.b64decode(b64.encode("utf-8"))
    return None


def main() -> int:
    client_id = "secure-subdomain-client"

    oauth2_b64 = get_k8s_secret_key_b64(
        "default", "oauth2-app-secrets", ".data.secure-subdomain-client-secret"
    )
    oauth2_secret = b64decode_str(oauth2_b64).rstrip(b"\n")

    sds_b64 = get_k8s_secret_key_b64(
        "aks-istio-ingress",
        "secure-subdomain-oauth-sds",
        r".data.secure-subdomain-oauth-token\.yaml",
    )
    sds_yaml = b64decode_str(sds_b64).decode("utf-8", "replace")

    # Prefer inline_bytes (more robust), fallback to inline_string.
    sds_secret = extract_inline_bytes(sds_yaml)
    sds_mode = "inline_bytes"
    if sds_secret is None:
        sds_secret = extract_inline_string(sds_yaml)
        sds_mode = "inline_string"

    if sds_secret is None:
        print(
            "ERROR: Could not extract inline_bytes or inline_string from SDS token YAML",
            file=sys.stderr,
        )
        return 2

    db_hash = get_db_client_secret_hash(client_id)

    # Strip Spring Security's {id} prefix, like {bcrypt}
    db_hash_stripped = re.sub(r"^\{[^}]+\}", "", db_hash)

    # bcrypt check without printing secrets
    try:
        import bcrypt  # type: ignore

        db_match = bcrypt.checkpw(oauth2_secret, db_hash_stripped.encode("utf-8"))
        db_match_sds = bcrypt.checkpw(sds_secret, db_hash_stripped.encode("utf-8"))
    except Exception as e:  # noqa: BLE001
        print(f"ERROR: bcrypt verification failed: {e}", file=sys.stderr)
        db_match = False
        db_match_sds = False

    print("OAUTH2_SECRET_SHA256=" + sha256_hex(oauth2_secret))
    print("SDS_TOKEN_MODE=" + sds_mode)
    print("SDS_TOKEN_SECRET_SHA256=" + sha256_hex(sds_secret))
    print("OAUTH2_EQUALS_SDS=" + str(oauth2_secret == sds_secret))
    print("DB_BCRYPT_MATCHES_OAUTH2=" + str(db_match))
    print("DB_BCRYPT_MATCHES_SDS=" + str(db_match_sds))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
