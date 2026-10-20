package cn.shakeguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import cn.shakeguard.app.data.GuardRepository
import java.util.regex.Pattern

class ShakeGuardAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ShakeGuardAccessibilityService? = null
            private set

        fun isServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expectedComponentName = "${context.packageName}/${ShakeGuardAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true) ||
                    componentName.contains(context.packageName, ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentForegroundPkg: String = ""
    private var currentPkgEnteredAtMs: Long = 0L
    private var lastScanTimeMs: Long = 0L
    private var lastSkipClickTimeMs: Long = 0L
    private var lastShakeAlertTimeMs: Long = 0L
    private var lastUserTouchTimeMs: Long = 0L
    private var overlayBannerView: View? = null

    private val compiledSkipRegexes by lazy {
        GuardRepository.skipRegexPatterns.mapNotNull {
            try {
                Pattern.compile(it)
            } catch (_: Throwable) {
                null
            }
        }
    }

    // 动态读取用户在本地规则库中配置的受控目标包名，不硬编码任何特定第三方厂商包名
    private fun isConfiguredJumpTarget(pkg: String): Boolean {
        return GuardRepository.schemeRules.any { it.targetPkg.isNotBlank() && it.targetPkg == pkg }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        GuardRepository.init(applicationContext)
        instance = this
        GuardRepository.notifyChanged()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        removeOverlayBanner()
        instance = null
        GuardRepository.notifyChanged()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeOverlayBanner()
        instance = null
        GuardRepository.notifyChanged()
        super.onDestroy()
    }

    override fun onInterrupt() {
        removeOverlayBanner()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        GuardRepository.init(applicationContext)
        if (!GuardRepository.masterShieldEnabled) return

        val evtType = event.eventType
        if (evtType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            evtType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
        ) {
            lastUserTouchTimeMs = System.currentTimeMillis()
            return
        }

        val pkg = event.packageName?.toString() ?: return
        if (isIgnoredSystemPackage(pkg)) return

        val now = System.currentTimeMillis()

        if (evtType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handlePackageTransition(pkg, now)
        }

        // 节流：距离上次扫描不足 110ms 则跳过，保证零卡顿与极低耗电
        if (now - lastScanTimeMs < 110L && evtType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }
        lastScanTimeMs = now

        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return

        try {
            scanWindowTree(root, pkg, now)
        } catch (_: Throwable) {
        }
    }

    private fun handlePackageTransition(newPkg: String, now: Long) {
        if (newPkg == currentForegroundPkg) return
        val prevPkg = currentForegroundPkg
        val prevEnteredAt = currentPkgEnteredAtMs
        currentForegroundPkg = newPkg
        currentPkgEnteredAtMs = now

        // L2B: 跨应用突变强跳检测与自动拉回
        // 条件：前一个应用刚启动不足 7 秒或正处于摇一摇高戒备窗口，且 900ms 内用户没有主动触屏点击，却发生了未经触屏确认的跨包突变跳转
        val wasInSplashOrAlert = (now - prevEnteredAt in 150L..7000L) || GuardRepository.isInHighAlertWindow()
        val userDidNotClick = (now - lastUserTouchTimeMs) > 900L
        if (GuardRepository.l2CrossAppBounceBack &&
            prevPkg.isNotEmpty() &&
            prevPkg != packageName &&
            newPkg != packageName &&
            wasInSplashOrAlert &&
            userDidNotClick &&
            isConfiguredJumpTarget(newPkg)
        ) {
            val srcName = GuardRepository.getAppLabel(prevPkg)
            val dstName = GuardRepository.getAppLabel(newPkg)
            performGlobalAction(GLOBAL_ACTION_BACK)
            GuardRepository.recordInterception(
                layer = "L2",
                sourceApp = srcName,
                targetDesc = "跨应用突跳 -> $dstName",
                actionTaken = "自动拉回原应用",
                detail = "检测到开屏/摇一摇戒备期内未经手指点击强行唤醒 $dstName ($newPkg)，已触发系统级返回"
            )
            showAccessibilityShieldBanner("🛡️ 已拦截 [$srcName] 跨应用误触发至 [$dstName]", showBackButton = false)
            return
        }

        // L3: 新应用进入前台时，触发开屏期传感器断连保护
        if (newPkg != packageName) {
            GuardRepository.activateHighAlert(newPkg, GuardRepository.getAppLabel(newPkg), 6000L)
            ShizukuSensorController.onAppEnteredForeground(applicationContext, newPkg)
        }
    }

    private fun scanWindowTree(root: AccessibilityNodeInfo, pkg: String, now: Long) {
        val policy = GuardRepository.findPolicyForPackage(pkg)
        val allowAutoSkip = GuardRepository.l1AutoSkipSplash && (policy?.autoSkipSplash ?: true)
        val inSplashWindow = (now - currentPkgEnteredAtMs) < 9500L || pkg == packageName

        var skipHandled = false
        var shakeDetectedText: String? = null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < 200) {
            val node = queue.removeFirst()
            visited++

            val viewId = node.viewIdResourceName ?: ""
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""

            // 1. 检查是否为“跳过”按钮（仅在开屏窗口或我方沙盒靶场内执行）
            if (allowAutoSkip && inSplashWindow && !skipHandled && (now - lastSkipClickTimeMs > 900L)) {
                if (matchesSkipNode(viewId, text, desc)) {
                    val clicked = tryClickNodeOrGesture(node)
                    if (clicked) {
                        skipHandled = true
                        lastSkipClickTimeMs = now
                        val appLabel = if (pkg == packageName) "摇一摇靶场沙盒" else GuardRepository.getAppLabel(pkg)
                        val matchInfo = if (text.isNotEmpty()) "文本「$text」" else "控件「${viewId.substringAfterLast('/')}」"
                        GuardRepository.recordInterception(
                            layer = "L1",
                            sourceApp = appLabel,
                            targetDesc = matchInfo,
                            actionTaken = "毫秒级自动跳过",
                            detail = "无障碍引擎自动触发开屏跳过 ($matchInfo)"
                        )
                    }
                }
            }

            // 2. 检查是否包含“摇一摇 / 扭一扭 / 前倾”高危特征
            if (shakeDetectedText == null) {
                val shakeHit = matchesShakeAdNode(viewId, text, desc)
                if (shakeHit != null) {
                    shakeDetectedText = shakeHit
                }
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (shakeDetectedText != null && (now - lastShakeAlertTimeMs > 3200L)) {
            lastShakeAlertTimeMs = now
            val appLabel = if (pkg == packageName) "摇一摇靶场沙盒" else GuardRepository.getAppLabel(pkg)
            GuardRepository.activateHighAlert(pkg, appLabel, 7000L)
            if (pkg != packageName) {
                ShizukuSensorController.onAppEnteredForeground(applicationContext, pkg)
            }
            GuardRepository.recordInterception(
                layer = "L1",
                sourceApp = appLabel,
                targetDesc = "检测到「$shakeDetectedText」",
                actionTaken = "开启7秒防跳锁定",
                detail = "捕获摇一摇/扭一扭开屏诱导节点，已激活跨应用跳转拉回锁与传感器保护"
            )
            if (GuardRepository.l1ShakeAlertOverlay) {
                showAccessibilityShieldBanner("🛡️ 捕获「$shakeDetectedText」· 防摇护盾已锁定", showBackButton = true)
            }
        }
    }

    private fun matchesSkipNode(viewId: String, text: String, desc: String): Boolean {
        if (viewId.isNotEmpty()) {
            val idShort = viewId.substringAfterLast('/')
            if (GuardRepository.skipViewIds.any { idShort.equals(it, ignoreCase = true) || idShort.contains(it, ignoreCase = true) }) {
                return true
            }
        }
        val candidate = if (text.isNotEmpty()) text else desc
        if (candidate.isEmpty() || candidate.length > 12) return false
        // 排除我方应用主界面的设置说明文字
        if (candidate.contains("规则") || candidate.contains("模式") || candidate.contains("次数") || candidate.contains("自动跳过")) {
            return false
        }
        if (GuardRepository.skipKeywords.any { candidate.equals(it, ignoreCase = true) }) {
            return true
        }
        return compiledSkipRegexes.any { it.matcher(candidate).matches() }
    }

    private fun matchesShakeAdNode(viewId: String, text: String, desc: String): String? {
        if (viewId.isNotEmpty()) {
            val idShort = viewId.substringAfterLast('/')
            val hitId = GuardRepository.shakeViewIds.firstOrNull { idShort.contains(it, ignoreCase = true) }
            if (hitId != null) return "SDK控件:$hitId"
        }
        val combined = "$text $desc".trim()
        if (combined.isEmpty() || combined.length > 42) return null
        // 排除我方应用正常 UI 标题，仅保留靶场沙盒广告提示
        if (combined.contains("守卫") || combined.contains("靶场") || combined.contains("设置") || combined.contains("拦截日志")) {
            return null
        }
        val hitKw = GuardRepository.shakeAdKeywords.firstOrNull { combined.contains(it, ignoreCase = true) }
        return hitKw
    }

    private fun tryClickNodeOrGesture(node: AccessibilityNodeInfo): Boolean {
        var curr: AccessibilityNodeInfo? = node
        var depth = 0
        while (curr != null && depth < 4) {
            if (curr.isClickable) {
                if (curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            curr = curr.parent
            depth++
        }
        if (GuardRepository.l1GestureTapFallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 8 && rect.height() > 8 && rect.centerX() > 0 && rect.centerY() > 0) {
                val path = Path().apply {
                    moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, 45L)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                return dispatchGesture(gesture, null, null)
            }
        }
        return false
    }

    /**
     * 使用 AccessibilityService 自带的 TYPE_ACCESSIBILITY_OVERLAY 在屏幕顶部显示非侵入式防摇护盾胶囊，
     * 无需额外申请 SYSTEM_ALERT_WINDOW 悬浮窗权限。
     */
    fun showAccessibilityShieldBanner(message: String, showBackButton: Boolean) {
        mainHandler.post {
            try {
                removeOverlayBanner()
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val density = resources.displayMetrics.density
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        (16 * density).toInt(),
                        (10 * density).toInt(),
                        (16 * density).toInt(),
                        (10 * density).toInt()
                    )
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 28 * density
                        setColor(Color.parseColor("#E6064E3B"))
                        setStroke((1.5f * density).toInt(), Color.parseColor("#10B981"))
                    }
                }

                val tv = TextView(this).apply {
                    text = message
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                }
                container.addView(tv)

                if (showBackButton) {
                    val actionBtn = TextView(this).apply {
                        text = " 立即返回 "
                        setTextColor(Color.parseColor("#064E3B"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 14 * density
                            setColor(Color.parseColor("#34D399"))
                        }
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.leftMargin = (10 * density).toInt()
                        layoutParams = lp
                        setOnClickListener {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            removeOverlayBanner()
                        }
                    }
                    container.addView(actionBtn)
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (48 * density).toInt()
                }

                wm.addView(container, params)
                overlayBannerView = container
                mainHandler.postDelayed({ removeOverlayBanner() }, 3500L)
            } catch (_: Throwable) {
            }
        }
    }

    private fun removeOverlayBanner() {
        val v = overlayBannerView ?: return
        overlayBannerView = null
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Throwable) {
        }
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        return pkg == "com.android.systemui" ||
            pkg.startsWith("com.android.inputmethod") ||
            pkg.contains("inputmethod") ||
            pkg == "android"
    }
}
