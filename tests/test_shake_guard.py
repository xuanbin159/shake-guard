"""shake_guard 自动化测试：验证合规通用模板、APK 产物签名与结构、以及本体上报格式。"""
from __future__ import annotations

import json
import zipfile
from shake_guard.builder import DIST_DIR, PROJECT_ROOT, export_rules_asset
from shake_guard.rules_catalog import (
    BLOCKED_SCHEMES,
    DEFENSE_LAYERS,
    SHAKE_AD_KEYWORDS,
    SKIP_KEYWORDS,
    SKIP_VIEW_IDS,
    TARGET_APPS,
)


def test_rules_catalog_integrity() -> None:
    assert len(DEFENSE_LAYERS) == 3
    assert {l["layer_level"] for l in DEFENSE_LAYERS} == {"L1", "L2", "L3"}
    assert len(SKIP_KEYWORDS) >= 4
    assert len(SKIP_VIEW_IDS) >= 5
    assert len(SHAKE_AD_KEYWORDS) >= 5
    assert len(BLOCKED_SCHEMES) >= 3
    assert len(TARGET_APPS) >= 3

    asset_path = export_rules_asset()
    assert asset_path.exists()
    data = json.loads(asset_path.read_text(encoding="utf-8"))
    assert data["meta"]["name"] == "generic_motion_guard_template"
    assert len(data["blocked_schemes"]) == len(BLOCKED_SCHEMES)


def test_apk_artifact_and_ontology_entities() -> None:
    apk_path = DIST_DIR / "ShakeGuard-v1.3.0-zh-CN.apk"
    assert apk_path.exists(), f"APK not found at {apk_path}"
    assert apk_path.stat().st_size > 100_000

    with zipfile.ZipFile(apk_path, "r") as zf:
        names = set(zf.namelist())
        assert "AndroidManifest.xml" in names
        assert "classes.dex" in names
        assert "assets/default_rules.json" in names

    entities_path = PROJECT_ROOT / "osa" / "state" / "entities.json"
    assert entities_path.exists()
    payload = json.loads(entities_path.read_text(encoding="utf-8"))
    cls_set = {e["cls"] for e in payload["entities"]}
    assert {"SgRuleSet", "SgDefenseLayer", "SgTargetApp", "SgApkArtifact", "SgBuildRun"}.issubset(cls_set)


if __name__ == "__main__":
    test_rules_catalog_integrity()
    test_apk_artifact_and_ontology_entities()
    print("ALL_TESTS_PASSED")
