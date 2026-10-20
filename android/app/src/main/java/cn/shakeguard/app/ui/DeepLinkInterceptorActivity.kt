package cn.shakeguard.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import cn.shakeguard.app.data.GuardRepository
import cn.shakeguard.app.model.DeepLinkMode

class DeepLinkInterceptorActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var countdownSec = 3
    private var countdownTv: TextView? = null
    private var autoBlockRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardRepository.init(applicationContext)

        val uri: Uri? = intent?.data
        if (uri == null) {
            finish()
            return
        }

        val scheme = uri.scheme?.lowercase() ?: "unknown"
        val fullUriStr = uri.toString()
        val rule = GuardRepository.findSchemeRule(scheme)
        val rawTargetName = rule?.targetApp ?: I18n.t("第三方应用 ($scheme://)", "External App ($scheme://)")
        val targetAppName = I18n.translateAppName(rawTargetName)
        val targetPkg = rule?.targetPkg ?: ""
        val sourceApp = I18n.translateAppName(extractSourceApp())

        if (!GuardRepository.masterShieldEnabled) {
            forwardToTargetApp(uri, targetPkg)
            finish()
            return
        }

        val mode = GuardRepository.l2DeepLinkMode
        val ruleAction = rule?.defaultAction ?: "intercept"
        val isDemoTrigger = uri.getQueryParameter("sg_test") == "1"

        val shouldSilentBlock = !isDemoTrigger && (
            mode == DeepLinkMode.BLOCK_ALL ||
                ruleAction == "block_silent" ||
                (mode == DeepLinkMode.SILENT_IN_SPLASH && GuardRepository.isInHighAlertWindow())
            )

        if (shouldSilentBlock) {
            GuardRepository.recordInterception(
                layer = "L2",
                sourceApp = sourceApp,
                targetDesc = "$targetAppName ($scheme://)",
                actionTaken = I18n.t("开屏期静默阻断", "Silent Splash Block"),
                detail = I18n.t(
                    "拦截跨应用 DeepLink: ${fullUriStr.take(90)}",
                    "Blocked cross-app DeepLink: ${fullUriStr.take(90)}"
                )
            )
            Toast.makeText(
                applicationContext,
                I18n.t(
                    "🛡️ 摇一摇守卫已静默拦截跳往「$targetAppName」",
                    "🛡️ ShakeGuard silently blocked jump to $targetAppName"
                ),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        renderInterceptorSheet(uri, scheme, targetAppName, targetPkg, sourceApp, fullUriStr)
    }

    private fun extractSourceApp(): String {
        val ref = referrer?.host
        if (!ref.isNullOrBlank() && ref != packageName) {
            return GuardRepository.getAppLabel(ref)
        }
        if (GuardRepository.lastActiveSourceAppName.isNotBlank()) {
            return GuardRepository.lastActiveSourceAppName
        }
        return I18n.t("开屏/信息流广告", "Splash / Feed Ad")
    }

    private fun renderInterceptorSheet(
        uri: Uri,
        scheme: String,
        targetAppName: String,
        targetPkg: String,
        sourceApp: String,
        fullUriStr: String
    ) {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#88050811"))
            setPadding(UiTheme.dp(context, 16f), UiTheme.dp(context, 24f), UiTheme.dp(context, 16f), UiTheme.dp(context, 24f))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiTheme.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            background = UiTheme.roundedBg(UiTheme.BG_SURFACE, 22f, context, UiTheme.EMERALD, 1.5f)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(
            UiTheme.makeBadge(
                this,
                I18n.t("🛡️ L2 跨应用摇晃唤醒拦截", "🛡️ L2 Cross-App Shake Jump Guard"),
                UiTheme.EMERALD_LIGHT,
                UiTheme.EMERALD_DARK
            )
        )
        val timerBadge = TextView(this).apply {
            text = I18n.t("${countdownSec}秒后自动拦截", "Auto-block in ${countdownSec}s")
            setTextColor(UiTheme.AMBER)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        countdownTv = timerBadge
        topRow.addView(timerBadge)
        card.addView(topRow)

        card.addView(TextView(this).apply {
            text = I18n.t("已拦截疑似摇一摇广告跳转", "Blocked Suspected Shake-Ad Jump")
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, UiTheme.dp(context, 12f), 0, UiTheme.dp(context, 4f))
        })

        card.addView(TextView(this).apply {
            text = I18n.t(
                "检测到「$sourceApp」正尝试通过 DeepLink 协议唤醒外部应用「$targetAppName」，是否阻断？",
                "Detected [$sourceApp] attempting to launch external app [$targetAppName] via DeepLink URI. Block this jump?"
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        })

        val detailBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = UiTheme.dp(context, 12f)
            setPadding(p, p, p, p)
            background = UiTheme.roundedBg(UiTheme.BG_ROOT, 12f, context, UiTheme.BORDER_SUBTLE, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = UiTheme.dp(context, 12f)
                bottomMargin = UiTheme.dp(context, 16f)
            }
        }
        detailBox.addView(TextView(this).apply {
            text = I18n.t(
                "目标应用：$targetAppName  |  协议头：$scheme://",
                "Target App: $targetAppName  |  Scheme: $scheme://"
            )
            setTextColor(UiTheme.EMERALD_LIGHT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        detailBox.addView(TextView(this).apply {
            text = I18n.t("拦截地址：", "Intercepted URI: ") + fullUriStr.take(120)
            setTextColor(UiTheme.TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, UiTheme.dp(context, 4f), 0, 0)
        })
        card.addView(detailBox)

        val blockBtn = UiTheme.makeButton(
            this,
            I18n.t("🚫 立即拦截并留在原应用（推荐）", "🚫 Block Jump & Stay Here (Recommended)"),
            bgColor = UiTheme.EMERALD,
            textColor = Color.WHITE
        ) {
            performBlock(sourceApp, targetAppName, scheme, fullUriStr, I18n.t("用户拒绝跳转", "Rejected by User"))
        }
        card.addView(blockBtn)

        val bottomActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = UiTheme.dp(context, 10f)
            }
        }

        val alwaysSilentBtn = UiTheme.makeButton(
            this,
            I18n.t("🔒 以后静默阻断 $scheme://", "🔒 Always Block $scheme://"),
            bgColor = UiTheme.BG_CARD_SUBTLE,
            textColor = UiTheme.TEXT_PRIMARY,
            strokeColor = UiTheme.BORDER_SUBTLE
        ) {
            GuardRepository.addOrUpdateSchemeRule(scheme, targetAppName, targetPkg, "block_silent")
            performBlock(sourceApp, targetAppName, scheme, fullUriStr, I18n.t("设为永久静默阻断", "Set to Always Silent Block"))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = UiTheme.dp(context, 6f)
            }
        }

        val allowOnceBtn = UiTheme.makeButton(
            this,
            I18n.t("✅ 仅本次允许打开", "✅ Allow Once"),
            bgColor = UiTheme.BG_CARD_SUBTLE,
            textColor = UiTheme.TEXT_SECONDARY,
            strokeColor = UiTheme.BORDER_SUBTLE
        ) {
            cancelCountdown()
            GuardRepository.recordInterception(
                layer = "L2",
                sourceApp = sourceApp,
                targetDesc = "$targetAppName ($scheme://)",
                actionTaken = I18n.t("用户手动放行一次", "Allowed Once by User"),
                detail = I18n.t("放行地址: ", "Allowed URI: ") + fullUriStr.take(80)
            )
            forwardToTargetApp(uri, targetPkg)
            finish()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = UiTheme.dp(context, 6f)
            }
        }

        bottomActionRow.addView(alwaysSilentBtn)
        bottomActionRow.addView(allowOnceBtn)
        card.addView(bottomActionRow)

        root.addView(card)
        setContentView(root)

        startAutoBlockCountdown(sourceApp, targetAppName, scheme, fullUriStr)
    }

    private fun startAutoBlockCountdown(
        sourceApp: String,
        targetAppName: String,
        scheme: String,
        fullUriStr: String
    ) {
        val task = object : Runnable {
            override fun run() {
                countdownSec--
                if (countdownSec <= 0) {
                    performBlock(
                        sourceApp,
                        targetAppName,
                        scheme,
                        fullUriStr,
                        I18n.t("3秒超时自动拦截", "Auto-blocked after 3s timeout")
                    )
                } else {
                    countdownTv?.text = I18n.t("${countdownSec}秒后自动拦截", "Auto-block in ${countdownSec}s")
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        autoBlockRunnable = task
        handler.postDelayed(task, 1000L)
    }

    private fun cancelCountdown() {
        autoBlockRunnable?.let { handler.removeCallbacks(it) }
        autoBlockRunnable = null
    }

    private fun performBlock(
        sourceApp: String,
        targetAppName: String,
        scheme: String,
        fullUriStr: String,
        reason: String
    ) {
        cancelCountdown()
        GuardRepository.recordInterception(
            layer = "L2",
            sourceApp = sourceApp,
            targetDesc = "$targetAppName ($scheme://)",
            actionTaken = I18n.t("已阻断跨应用唤醒 ($reason)", "Blocked Cross-App Jump ($reason)"),
            detail = I18n.t("成功阻断跳转链接: ", "Blocked URI: ") + fullUriStr.take(90)
        )
        Toast.makeText(
            applicationContext,
            I18n.t("🛡️ 已为您阻断跳往「$targetAppName」", "🛡️ Blocked jump to $targetAppName"),
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun forwardToTargetApp(uri: Uri, targetPkg: String) {
        try {
            val forward = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (targetPkg.isNotBlank()) {
                    setPackage(targetPkg)
                }
            }
            val pm = packageManager
            if (forward.resolveActivity(pm) != null && forward.component?.packageName != packageName) {
                startActivity(forward)
            } else {
                Toast.makeText(
                    applicationContext,
                    I18n.t("本机未安装目标应用 ($targetPkg)", "Target app not installed ($targetPkg)"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Throwable) {
            Toast.makeText(
                applicationContext,
                I18n.t("无法拉起目标应用", "Unable to launch target app"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        cancelCountdown()
        super.onDestroy()
    }
}
