package cn.shakeguard.app.ui

import cn.shakeguard.app.data.GuardRepository

object I18n {
    val isEn: Boolean
        get() = GuardRepository.appLang == "en"

    fun t(zh: String, en: String): String = if (isEn) en else zh

    fun toggleLang() {
        GuardRepository.appLang = if (isEn) "zh" else "en"
    }

    fun translateCategory(cat: String): String {
        if (!isEn) return cat
        return when (cat) {
            "示例协议" -> "Demo Scheme"
            "示例模板" -> "Template"
            "本机已装" -> "Installed App"
            "自定义规则" -> "Custom Rule"
            else -> cat
        }
    }

    fun translateAppName(name: String): String {
        if (!isEn) return name
        return when (name) {
            "沙盒测试目标应用 A" -> "Sandbox Target App A"
            "沙盒测试目标应用 B" -> "Sandbox Target App B"
            "沙盒测试目标应用 C" -> "Sandbox Target App C"
            "沙盒测试目标应用 D" -> "Sandbox Target App D"
            "示例阅读应用 (Template)" -> "Sample Reader App (Template)"
            "示例资讯应用 (Template)" -> "Sample News App (Template)"
            "示例影音应用 (Template)" -> "Sample Media App (Template)"
            "示例社区应用 (Template)" -> "Sample Forum App (Template)"
            "摇一摇靶场沙盒" -> "Motion Sensor Sandbox"
            else -> name
        }
    }
}
