"""CLI 入口: python3 -m shake_guard [build|export-rules|sync-onto]"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from .builder import DIST_DIR, build_apk, export_rules_asset, sync_ontology_state


def main() -> int:
    cmd = sys.argv[1] if len(sys.argv) > 1 else "build"
    if cmd == "export-rules":
        p = export_rules_asset()
        print(json.dumps({"status": "ok", "rules_asset": str(p)}, ensure_ascii=False))
        return 0
    if cmd == "sync-onto":
        apk = DIST_DIR / "ShakeGuard-v1.0.0.apk"
        res = sync_ontology_state(apk if apk.exists() else None)
        print(json.dumps({"status": "ok", **res}, ensure_ascii=False))
        return 0
    if cmd == "build":
        res = build_apk()
        print(json.dumps({"status": "ok", **res}, ensure_ascii=False, indent=2))
        return 0
    print(f"Unknown command: {cmd}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
