package cn.shakeguard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import cn.shakeguard.app.model.AppPolicy
import cn.shakeguard.app.model.DeepLinkMode
import cn.shakeguard.app.model.InterceptRecord
import cn.shakeguard.app.model.SchemeRule
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object GuardRepository {
    private const val PREF_NAME = "shake_guard_store_v1"
    private var prefs: SharedPreferences? = null
    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var ruleVersion: String = "2026.10.1"
        private set
    private var assetDefaultLang: String = "zh"

    val skipKeywords = mutableListOf<String>()
    val skipRegexPatterns = mutableListOf<String>()
    val skipViewIds = mutableListOf<String>()
    val shakeAdKeywords = mutableListOf<String>()
    val shakeViewIds = mutableListOf<String>()
    val schemeRules = mutableListOf<SchemeRule>()
    val appPolicies = mutableListOf<AppPolicy>()
    val interceptLogs = mutableListOf<InterceptRecord>()

    // 实时高戒备窗口状态：记录最近检测到开屏或摇一摇广告的包名及戒备截止时间戳
    @Volatile
    var lastActiveSourcePkg: String = ""
    @Volatile
    var lastActiveSourceAppName: String = "当前应用"
    @Volatile
    var highAlertUntilMs: Long = 0L

    fun init(context: Context) {
        if (initialized) return
        val appCtx = context.applicationContext
        prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadDefaultRulesFromAssets(appCtx)
        loadCustomOverrides()
        loadLogs()
        initialized = true
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        mainHandler.post {
            for (l in listeners) {
                try {
                    l.invoke()
                } catch (_: Throwable) {
                }
            }
        }
    }

    var appLang: String
        get() = prefs?.getString("app_lang", assetDefaultLang) ?: assetDefaultLang
        set(value) {
            prefs?.edit()?.putString("app_lang", value)?.apply()
            notifyChanged()
        }

    var masterShieldEnabled: Boolean
        get() = prefs?.getBoolean("master_shield_enabled", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("master_shield_enabled", value)?.apply()
            notifyChanged()
        }

    var l1AutoSkipSplash: Boolean
        get() = prefs?.getBoolean("l1_auto_skip_splash", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("l1_auto_skip_splash", value)?.apply()
            notifyChanged()
        }

    var l1ShakeAlertOverlay: Boolean
        get() = prefs?.getBoolean("l1_shake_alert_overlay", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("l1_shake_alert_overlay", value)?.apply()
            notifyChanged()
        }

    var l1GestureTapFallback: Boolean
        get() = prefs?.getBoolean("l1_gesture_tap_fallback", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("l1_gesture_tap_fallback", value)?.apply()
            notifyChanged()
        }

    var l2CrossAppBounceBack: Boolean
        get() = prefs?.getBoolean("l2_cross_app_bounce_back", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("l2_cross_app_bounce_back", value)?.apply()
            notifyChanged()
        }

    var l2DeepLinkMode: DeepLinkMode
        get() = DeepLinkMode.fromCode(
            prefs?.getString("l2_deeplink_mode", DeepLinkMode.CONFIRM_DIALOG.code)
                ?: DeepLinkMode.CONFIRM_DIALOG.code
        )
        set(value) {
            prefs?.edit()?.putString("l2_deeplink_mode", value.code)?.apply()
            notifyChanged()
        }

    var l3ShizukuSensorCutoff: Boolean
        get() = prefs?.getBoolean("l3_shizuku_sensor_cutoff", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("l3_shizuku_sensor_cutoff", value)?.apply()
            notifyChanged()
        }

    var l3DefaultCutoffSec: Int
        get() = prefs?.getInt("l3_default_cutoff_sec", 6) ?: 6
        set(value) {
            prefs?.edit()?.putInt("l3_default_cutoff_sec", value.coerceIn(3, 30))?.apply()
            notifyChanged()
        }

    var skippedSplashCount: Int
        get() = prefs?.getInt("stat_skipped_splash", 0) ?: 0
        private set(value) {
            prefs?.edit()?.putInt("stat_skipped_splash", value)?.apply()
        }

    var blockedShakeJumpCount: Int
        get() = prefs?.getInt("stat_blocked_jump", 0) ?: 0
        private set(value) {
            prefs?.edit()?.putInt("stat_blocked_jump", value)?.apply()
        }

    var sensorCutoffCount: Int
        get() = prefs?.getInt("stat_sensor_cutoff", 0) ?: 0
        private set(value) {
            prefs?.edit()?.putInt("stat_sensor_cutoff", value)?.apply()
        }

    fun activateHighAlert(pkg: String, appName: String, durationMs: Long = 6500L) {
        lastActiveSourcePkg = pkg
        lastActiveSourceAppName = appName
        val targetUntil = System.currentTimeMillis() + durationMs
        if (targetUntil > highAlertUntilMs) {
            highAlertUntilMs = targetUntil
        }
    }

    fun isInHighAlertWindow(): Boolean = System.currentTimeMillis() < highAlertUntilMs

    fun recordInterception(
        layer: String,
        sourceApp: String,
        targetDesc: String,
        actionTaken: String,
        detail: String
    ) {
        when (layer) {
            "L1" -> skippedSplashCount += 1
            "L2" -> blockedShakeJumpCount += 1
            "L3" -> sensorCutoffCount += 1
        }
        val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
        val rec = InterceptRecord(
            id = System.currentTimeMillis(),
            timestamp = sdf.format(Date()),
            layer = layer,
            sourceApp = sourceApp,
            targetDesc = targetDesc,
            actionTaken = actionTaken,
            detail = detail
        )
        synchronized(interceptLogs) {
            interceptLogs.add(0, rec)
            if (interceptLogs.size > 120) {
                interceptLogs.removeAt(interceptLogs.lastIndex)
            }
            saveLogsLocked()
        }
        notifyChanged()
    }

    fun clearLogs() {
        synchronized(interceptLogs) {
            interceptLogs.clear()
            saveLogsLocked()
        }
        notifyChanged()
    }

    fun addCustomSkipKeyword(kw: String) {
        val trimmed = kw.trim()
        if (trimmed.isNotEmpty() && !skipKeywords.contains(trimmed)) {
            skipKeywords.add(0, trimmed)
            saveCustomOverrides()
            notifyChanged()
        }
    }

    fun addCustomShakeKeyword(kw: String) {
        val trimmed = kw.trim()
        if (trimmed.isNotEmpty() && !shakeAdKeywords.contains(trimmed)) {
            shakeAdKeywords.add(0, trimmed)
            saveCustomOverrides()
            notifyChanged()
        }
    }

    fun addOrUpdateSchemeRule(scheme: String, targetApp: String, targetPkg: String, action: String) {
        val clean = scheme.trim().lowercase(Locale.ROOT).removeSuffix("://")
        if (clean.isEmpty()) return
        val existing = schemeRules.firstOrNull { it.scheme.equals(clean, ignoreCase = true) }
        if (existing != null) {
            existing.defaultAction = action
        } else {
            schemeRules.add(0, SchemeRule(clean, targetApp.ifBlank { clean }, targetPkg, "自定义规则", action))
        }
        saveCustomOverrides()
        notifyChanged()
    }

    fun updateAppPolicy(policy: AppPolicy) {
        val idx = appPolicies.indexOfFirst { it.packageName == policy.packageName }
        if (idx >= 0) {
            appPolicies[idx] = policy
        } else {
            appPolicies.add(0, policy)
        }
        saveCustomOverrides()
        notifyChanged()
    }

    fun findPolicyForPackage(pkg: String): AppPolicy? =
        appPolicies.firstOrNull { it.packageName == pkg }

    fun findSchemeRule(scheme: String): SchemeRule? =
        schemeRules.firstOrNull { it.scheme.equals(scheme, ignoreCase = true) }

    fun getAppLabel(pkg: String): String {
        return appPolicies.firstOrNull { it.packageName == pkg }?.appName
            ?: schemeRules.firstOrNull { it.targetPkg == pkg }?.targetApp
            ?: pkg
    }

    private fun loadDefaultRulesFromAssets(context: Context) {
        try {
            val raw = context.assets.open("default_rules.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val meta = root.optJSONObject("meta")
            if (meta != null) {
                ruleVersion = meta.optString("version", ruleVersion)
                assetDefaultLang = meta.optString("default_lang", "zh")
            }
            skipKeywords.clear()
            root.optJSONArray("skip_keywords")?.let { arr ->
                for (i in 0 until arr.length()) skipKeywords.add(arr.getString(i))
            }
            skipRegexPatterns.clear()
            root.optJSONArray("skip_regex_patterns")?.let { arr ->
                for (i in 0 until arr.length()) skipRegexPatterns.add(arr.getString(i))
            }
            skipViewIds.clear()
            root.optJSONArray("skip_view_ids")?.let { arr ->
                for (i in 0 until arr.length()) skipViewIds.add(arr.getString(i))
            }
            shakeAdKeywords.clear()
            root.optJSONArray("shake_ad_keywords")?.let { arr ->
                for (i in 0 until arr.length()) shakeAdKeywords.add(arr.getString(i))
            }
            shakeViewIds.clear()
            root.optJSONArray("shake_view_ids")?.let { arr ->
                for (i in 0 until arr.length()) shakeViewIds.add(arr.getString(i))
            }
            schemeRules.clear()
            root.optJSONArray("blocked_schemes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    schemeRules.add(
                        SchemeRule(
                            scheme = o.getString("scheme"),
                            targetApp = o.getString("target_app"),
                            targetPkg = o.optString("target_pkg", ""),
                            category = o.optString("category", "推广唤醒"),
                            defaultAction = o.optString("default_action", "intercept")
                        )
                    )
                }
            }
            appPolicies.clear()
            root.optJSONArray("target_apps")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    appPolicies.add(
                        AppPolicy(
                            packageName = o.getString("package_name"),
                            appName = o.getString("app_name"),
                            category = o.optString("category", "应用"),
                            blockSensorWindowSec = o.optInt("block_sensor_window_sec", 6),
                            blockDeeplink = o.optBoolean("block_deeplink", true),
                            autoSkipSplash = o.optBoolean("auto_skip_splash", true),
                            permanentBlockSensor = false
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            if (skipKeywords.isEmpty()) {
                skipKeywords.addAll(listOf("跳过", "跳过广告", "点击跳过", "关闭广告", "Skip"))
            }
            if (shakeAdKeywords.isEmpty()) {
                shakeAdKeywords.addAll(listOf("摇一摇", "摇动手机", "扭一扭", "前倾", "跳转详情页或第三方应用"))
            }
        }
    }

    private fun loadCustomOverrides() {
        val p = prefs ?: return
        val customPoliciesJson = p.getString("custom_app_policies", null)
        if (!customPoliciesJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(customPoliciesJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pkg = o.getString("packageName")
                    val existing = appPolicies.firstOrNull { it.packageName == pkg }
                    if (existing != null) {
                        existing.blockSensorWindowSec = o.optInt("blockSensorWindowSec", existing.blockSensorWindowSec)
                        existing.blockDeeplink = o.optBoolean("blockDeeplink", existing.blockDeeplink)
                        existing.autoSkipSplash = o.optBoolean("autoSkipSplash", existing.autoSkipSplash)
                        existing.permanentBlockSensor = o.optBoolean("permanentBlockSensor", existing.permanentBlockSensor)
                    } else {
                        appPolicies.add(
                            AppPolicy(
                                packageName = pkg,
                                appName = o.optString("appName", pkg),
                                category = o.optString("category", "本地应用"),
                                blockSensorWindowSec = o.optInt("blockSensorWindowSec", 6),
                                blockDeeplink = o.optBoolean("blockDeeplink", true),
                                autoSkipSplash = o.optBoolean("autoSkipSplash", true),
                                permanentBlockSensor = o.optBoolean("permanentBlockSensor", false)
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }
        val customSchemesJson = p.getString("custom_scheme_actions", null)
        if (!customSchemesJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(customSchemesJson)
                for (s in schemeRules) {
                    if (obj.has(s.scheme)) {
                        s.defaultAction = obj.getString(s.scheme)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun saveCustomOverrides() {
        val p = prefs ?: return
        val policyArr = JSONArray()
        for (pol in appPolicies) {
            val o = JSONObject()
            o.put("packageName", pol.packageName)
            o.put("appName", pol.appName)
            o.put("category", pol.category)
            o.put("blockSensorWindowSec", pol.blockSensorWindowSec)
            o.put("blockDeeplink", pol.blockDeeplink)
            o.put("autoSkipSplash", pol.autoSkipSplash)
            o.put("permanentBlockSensor", pol.permanentBlockSensor)
            policyArr.put(o)
        }
        val schemeObj = JSONObject()
        for (s in schemeRules) {
            schemeObj.put(s.scheme, s.defaultAction)
        }
        p.edit()
            .putString("custom_app_policies", policyArr.toString())
            .putString("custom_scheme_actions", schemeObj.toString())
            .apply()
    }

    private fun loadLogs() {
        val p = prefs ?: return
        val raw = p.getString("intercept_logs_json", null)
        interceptLogs.clear()
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    interceptLogs.add(
                        InterceptRecord(
                            id = o.optLong("id", i.toLong()),
                            timestamp = o.optString("timestamp", ""),
                            layer = o.optString("layer", "L1"),
                            sourceApp = o.optString("sourceApp", ""),
                            targetDesc = o.optString("targetDesc", ""),
                            actionTaken = o.optString("actionTaken", ""),
                            detail = o.optString("detail", "")
                        )
                    )
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun saveLogsLocked() {
        val p = prefs ?: return
        val arr = JSONArray()
        for (rec in interceptLogs) {
            val o = JSONObject()
            o.put("id", rec.id)
            o.put("timestamp", rec.timestamp)
            o.put("layer", rec.layer)
            o.put("sourceApp", rec.sourceApp)
            o.put("targetDesc", rec.targetDesc)
            o.put("actionTaken", rec.actionTaken)
            o.put("detail", rec.detail)
            arr.put(o)
        }
        p.edit().putString("intercept_logs_json", arr.toString()).apply()
    }
}
