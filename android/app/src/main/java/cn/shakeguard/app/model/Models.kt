package cn.shakeguard.app.model

import cn.shakeguard.app.ui.I18n

enum class DeepLinkMode(
    val code: String,
    val labelZh: String,
    val labelEn: String,
    val descZh: String,
    val descEn: String
) {
    SILENT_IN_SPLASH(
        "silent_splash",
        "智能模式（保护期内静默阻断 + 平时弹窗确认）",
        "Smart Mode (Silent in Guard Window + Confirm Dialog)",
        "在传感器防误触保护窗口（6秒）内自动阻断未确认的跨应用调用，其余时段弹出二次确认卡片",
        "Blocks unconfirmed cross-app URI launches within the 6s motion-guard window; prompts confirmation otherwise"
    ),
    CONFIRM_DIALOG(
        "confirm_dialog",
        "二次确认弹窗模式（推荐）",
        "Confirmation Dialog Mode (Recommended)",
        "每次触发受控跨应用 Scheme 时弹出半透明二次确认卡片，3秒不操作自动取消跳转",
        "Shows a bottom-sheet confirmation card on monitored cross-app URI launches; auto-cancels after 3 seconds"
    ),
    BLOCK_ALL(
        "block_all",
        "全量静默保护模式（严格）",
        "Strict Mode (Silent Block All)",
        "静默阻断所有已配置黑名单的跨应用 URI 唤醒，仅在本地日志中留痕",
        "Silently blocks all user-configured URI schemes and records events locally"
    );

    val label: String
        get() = I18n.t(labelZh, labelEn)

    val desc: String
        get() = I18n.t(descZh, descEn)

    companion object {
        fun fromCode(code: String): DeepLinkMode =
            values().firstOrNull { it.code == code } ?: CONFIRM_DIALOG
    }
}

data class SchemeRule(
    val scheme: String,
    val targetApp: String,
    val targetPkg: String,
    val category: String,
    var defaultAction: String
)

data class AppPolicy(
    val packageName: String,
    val appName: String,
    val category: String,
    var blockSensorWindowSec: Int,
    var blockDeeplink: Boolean,
    var autoSkipSplash: Boolean,
    var permanentBlockSensor: Boolean = false
)

data class InterceptRecord(
    val id: Long,
    val timestamp: String,
    val layer: String,
    val sourceApp: String,
    val targetDesc: String,
    val actionTaken: String,
    val detail: String
)

data class PrivilegeState(
    val shizukuInstalled: Boolean,
    val shizukuBinderAlive: Boolean,
    val rootAvailable: Boolean,
    val activeBackend: String,
    val canExecutePrivileged: Boolean
)
