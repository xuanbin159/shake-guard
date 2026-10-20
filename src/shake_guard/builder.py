"""shake_guard 构建器与本体状态同步器。

负责：
1. 将 rules_catalog 导出为 Android 资源文件 assets/default_rules.json
2. 调用 scripts/build_apk.sh 使用 Android SDK 34 + Kotlin 1.9.24 编译并签名 APK
3. 采集构建结果与规则库，调用 write_entities() 写入 osa/state/entities.json 实现深度纳管
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import time
from datetime import datetime, timezone, timedelta
from pathlib import Path

from .osa_state import write_entities
from .rules_catalog import (
    BLOCKED_SCHEMES,
    DEFENSE_LAYERS,
    RULE_SET_META,
    SHAKE_AD_KEYWORDS,
    SKIP_KEYWORDS,
    TARGET_APPS,
    export_catalog_dict,
)

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_ASSETS_DIR = PROJECT_ROOT / "android" / "app" / "src" / "main" / "assets"
DIST_DIR = PROJECT_ROOT / "dist"
BUILD_SCRIPT = PROJECT_ROOT / "scripts" / "build_apk.sh"

CN_TZ = timezone(timedelta(hours=8))


def now_cn_iso() -> str:
    return datetime.now(CN_TZ).strftime("%Y-%m-%d %H:%M:%S")


def export_rules_asset(default_lang: str = "zh") -> Path:
    """生成 android/app/src/main/assets/default_rules.json。"""
    ANDROID_ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    out_path = ANDROID_ASSETS_DIR / "default_rules.json"
    data = export_catalog_dict()
    data["meta"] = dict(data["meta"], default_lang=default_lang)
    out_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return out_path


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def sync_ontology_state(
    apk_path: Path | None = None,
    run_status: str = "succeeded",
    duration_sec: float = 0.0,
    detail: str = "APK compiled & signed with v1/v2/v3 signatures",
) -> dict:
    """向 osa/state/entities.json 写入最新领域对象与关系边。"""
    entities: list[dict] = []
    edges: list[dict] = []

    rule_key = RULE_SET_META["name"]
    entities.append(
        {
            "cls": "SgRuleSet",
            "key": rule_key,
            "props": {
                "name": RULE_SET_META["name"],
                "version": RULE_SET_META["version"],
                "skip_keywords_count": len(SKIP_KEYWORDS),
                "shake_keywords_count": len(SHAKE_AD_KEYWORDS),
                "blocked_schemes_count": len(BLOCKED_SCHEMES),
                "target_apps_count": len(TARGET_APPS),
            },
        }
    )

    for layer in DEFENSE_LAYERS:
        entities.append(
            {
                "cls": "SgDefenseLayer",
                "key": layer["key"],
                "props": {
                    "name": layer["name"],
                    "layer_level": layer["layer_level"],
                    "mechanism": layer["mechanism"],
                    "requires_permission": layer["requires_permission"],
                    "status": layer["status"],
                },
            }
        )
        edges.append(
            {
                "src": f"SgRuleSet:{rule_key}",
                "rel": "SG_ENFORCED_BY",
                "dst": f"SgDefenseLayer:{layer['key']}",
            }
        )

    for app in TARGET_APPS:
        pkg = app["package_name"]
        entities.append(
            {
                "cls": "SgTargetApp",
                "key": pkg,
                "props": {
                    "package_name": pkg,
                    "app_name": app["app_name"],
                    "category": app["category"],
                    "block_sensor_window_sec": app["block_sensor_window_sec"],
                    "block_deeplink": app["block_deeplink"],
                    "auto_skip_splash": app["auto_skip_splash"],
                },
            }
        )
        edges.append(
            {
                "src": f"SgTargetApp:{pkg}",
                "rel": "SG_GOVERNED_BY",
                "dst": f"SgRuleSet:{rule_key}",
            }
        )

    built_at = now_cn_iso()
    run_id = f"build-{datetime.now(CN_TZ).strftime('%Y%m%d-%H%M%S')}"
    entities.append(
        {
            "cls": "SgBuildRun",
            "key": run_id,
            "props": {
                "run_id": run_id,
                "status": run_status,
                "compiler": "Android SDK 34 (aapt2 + kotlinc 1.9.24 + d8 + apksigner)",
                "duration_sec": round(duration_sec, 2),
                "built_at": built_at,
                "detail": detail,
            },
        }
    )

    if apk_path and apk_path.exists():
        apk_key = "ShakeGuard-v1.0.0"
        entities.append(
            {
                "cls": "SgApkArtifact",
                "key": apk_key,
                "props": {
                    "name": apk_path.name,
                    "version_name": "1.0.0",
                    "version_code": 100,
                    "apk_path": str(apk_path),
                    "size_bytes": apk_path.stat().st_size,
                    "sha256": sha256_of(apk_path),
                    "min_sdk": 26,
                    "target_sdk": 34,
                    "built_at": built_at,
                },
            }
        )
        edges.append(
            {
                "src": f"SgBuildRun:{run_id}",
                "rel": "SG_PRODUCED_APK",
                "dst": f"SgApkArtifact:{apk_key}",
            }
        )
        edges.append(
            {
                "src": f"SgApkArtifact:{apk_key}",
                "rel": "SG_BUNDLES_RULES",
                "dst": f"SgRuleSet:{rule_key}",
            }
        )

    write_entities(entities, edges)
    return {"entities_count": len(entities), "edges_count": len(edges), "run_id": run_id}


def build_apk() -> dict:
    """导出规则库并编译生成中/英双版本签名 APK，随后上报本体状态。"""
    import shutil
    t0 = time.monotonic()
    DIST_DIR.mkdir(parents=True, exist_ok=True)

    # 1. 编译英文默认版 APK (ShakeGuard-v1.3.0-en-US.apk)
    export_rules_asset(default_lang="en")
    apk_en = DIST_DIR / "ShakeGuard-v1.3.0-en-US.apk"
    proc_en = subprocess.run(
        ["bash", str(BUILD_SCRIPT), str(apk_en)],
        cwd=str(PROJECT_ROOT),
        capture_output=True,
        text=True,
        check=False,
    )
    if proc_en.returncode != 0:
        raise RuntimeError(f"EN APK build failed (exit {proc_en.returncode}):\n{proc_en.stdout}\n{proc_en.stderr}")

    # 2. 编译中文默认版 APK (ShakeGuard-v1.3.0-zh-CN.apk)
    rules_path = export_rules_asset(default_lang="zh")
    apk_zh = DIST_DIR / "ShakeGuard-v1.3.0-zh-CN.apk"
    proc = subprocess.run(
        ["bash", str(BUILD_SCRIPT), str(apk_zh)],
        cwd=str(PROJECT_ROOT),
        capture_output=True,
        text=True,
        check=False,
    )
    elapsed = time.monotonic() - t0
    if proc.returncode != 0:
        sync_ontology_state(
            apk_path=None,
            run_status="failed",
            duration_sec=elapsed,
            detail=(proc.stderr or proc.stdout)[-400:],
        )
        raise RuntimeError(f"ZH APK build failed (exit {proc.returncode}):\n{proc.stdout}\n{proc.stderr}")

    # 保持历史版本文件名兼容
    for alias_name in ("ShakeGuard-v1.0.0.apk", "ShakeGuard-v1.1.0.apk", "ShakeGuard-v1.2.0.apk"):
        shutil.copy2(apk_zh, DIST_DIR / alias_name)

    onto_res = sync_ontology_state(
        apk_path=apk_zh,
        run_status="succeeded",
        duration_sec=elapsed,
        detail=f"Built bilingual APKs: {apk_zh.name} & {apk_en.name} ({apk_zh.stat().st_size} bytes)",
    )
    return {
        "apk_zh_path": str(apk_zh),
        "apk_en_path": str(apk_en),
        "size_bytes": apk_zh.stat().st_size,
        "sha256_zh": sha256_of(apk_zh),
        "sha256_en": sha256_of(apk_en),
        "duration_sec": round(elapsed, 2),
        "ontology": onto_res,
    }
