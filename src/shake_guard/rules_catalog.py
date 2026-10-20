"""shake_guard 通用传感器防误触与跨应用调用确认默认模板库(中立合规版)。

重要合规说明：
本文件仅包含与具体厂商无关的通用界面无障碍语义词与示例占位符(com.example.*)，
不内置针对任何特定第三方互联网企业、特定商业软件或特定商业 SDK 的定向规则。
"""
from __future__ import annotations

RULE_SET_META = {
    "name": "generic_motion_guard_template",
    "version": "2026.10.2",
    "desc": "通用传感器防误触、界面倒计时辅助与跨应用链接二次确认基础配置模板（不含特定第三方厂商规则）",
}

DEFENSE_LAYERS = [
    {
        "key": "layer-l1-accessibility",
        "name": "L1 无障碍交互辅助与防误触提示",
        "layer_level": "L1",
        "mechanism": "AccessibilityService 识别通用跳过/关闭文本及用户自定义控件 ID，并在出现摇动提示时开启防误触保护期",
        "requires_permission": "BIND_ACCESSIBILITY_SERVICE (本地无障碍服务)",
        "status": "ready",
    },
    {
        "key": "layer-l2-deeplink-guard",
        "name": "L2 跨应用调用二次确认",
        "layer_level": "L2",
        "mechanism": "IntentFilter 跨应用协议二次确认弹窗与非触屏突变跳转自动返回辅助",
        "requires_permission": "IntentFilter + AccessibilityService",
        "status": "ready",
    },
    {
        "key": "layer-l3-shizuku-sensor",
        "name": "L3 系统传感器权限管理",
        "layer_level": "L3",
        "mechanism": "通过 Shizuku / AppOpsManager 管理用户自选应用的 OP_SENSORS 传感器读取权限（防止颠簸或手部震颤误触发）",
        "requires_permission": "Shizuku / Root / ADB (可选)",
        "status": "ready",
    },
]

# 仅保留通用中文/英文自然语言界面词汇，不含任何第三方专有词汇
SKIP_KEYWORDS = [
    "跳过",
    "点击跳过",
    "关闭",
    "Skip",
    "SKIP",
    "Close",
]

SKIP_REGEX_PATTERNS = [
    r"^跳过[\s\d秒sS|｜]*$",
    r"^[\d秒sS|｜\s]*跳过$",
    r"^关闭[\s\d秒sS]*$",
]

# 仅保留 Android 通用命名惯例后缀，不含任何第三方商业 SDK 专有前缀
SKIP_VIEW_IDS = [
    "btn_skip",
    "tv_skip",
    "skip_view",
    "skip_button",
    "btn_close",
    "iv_close",
    "countdown_view",
]

SHAKE_AD_KEYWORDS = [
    "摇一摇",
    "摇动手机",
    "摇晃手机",
    "扭一扭",
    "扭动手机",
    "转动手机",
    "前倾",
    "倾斜手机",
    "晃动手机",
]

SHAKE_VIEW_IDS = [
    "shake_view",
    "shake_layout",
    "twist_view",
    "motion_view",
]

# 仅提供标准 RFC 风格示例占位符，不内置任何真实第三方公司 Scheme
BLOCKED_SCHEMES = [
    {"scheme": "shakeguarddemo", "target_app": "沙盒测试目标应用 A", "target_pkg": "com.example.target.alpha", "category": "示例协议", "default_action": "intercept"},
    {"scheme": "appjump",        "target_app": "沙盒测试目标应用 B", "target_pkg": "com.example.target.beta",  "category": "示例协议", "default_action": "intercept"},
    {"scheme": "extopen",        "target_app": "沙盒测试目标应用 C", "target_pkg": "com.example.target.gamma", "category": "示例协议", "default_action": "ask"},
    {"scheme": "deeplinkdemo",   "target_app": "沙盒测试目标应用 D", "target_pkg": "com.example.target.delta", "category": "示例协议", "default_action": "intercept"},
]

# 仅提供 com.example.* 教学演示占位项，用户可在手机本地自行扫描管理本机应用
TARGET_APPS = [
    {"package_name": "com.example.demo.reader", "app_name": "示例阅读应用 (Template)", "category": "示例模板", "block_sensor_window_sec": 6, "block_deeplink": True, "auto_skip_splash": True},
    {"package_name": "com.example.demo.news",   "app_name": "示例资讯应用 (Template)", "category": "示例模板", "block_sensor_window_sec": 6, "block_deeplink": True, "auto_skip_splash": True},
    {"package_name": "com.example.demo.media",  "app_name": "示例影音应用 (Template)", "category": "示例模板", "block_sensor_window_sec": 6, "block_deeplink": True, "auto_skip_splash": True},
    {"package_name": "com.example.demo.social", "app_name": "示例社区应用 (Template)", "category": "示例模板", "block_sensor_window_sec": 6, "block_deeplink": True, "auto_skip_splash": True},
]


def export_catalog_dict() -> dict:
    return {
        "meta": RULE_SET_META,
        "defense_layers": DEFENSE_LAYERS,
        "skip_keywords": SKIP_KEYWORDS,
        "skip_regex_patterns": SKIP_REGEX_PATTERNS,
        "skip_view_ids": SKIP_VIEW_IDS,
        "shake_ad_keywords": SHAKE_AD_KEYWORDS,
        "shake_view_ids": SHAKE_VIEW_IDS,
        "blocked_schemes": BLOCKED_SCHEMES,
        "target_apps": TARGET_APPS,
    }
