"""领域对象上报(统一格式):程序在关键节点调用 write_entities(),图 30 秒内采到。"""
import json, os
from pathlib import Path

_STATE = Path(__file__).resolve().parents[2] / "osa" / "state" / "entities.json"


def write_entities(entities, edges=None):
    """entities: [{"cls","key","props",("desc")}]; edges: [{"src":"Cls:key","rel","dst":"Cls:key"}]"""
    _STATE.parent.mkdir(parents=True, exist_ok=True)
    tmp = _STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps({"entities": entities, "edges": edges or []}, ensure_ascii=False, indent=1))
    os.replace(tmp, _STATE)
