package cn.shakeguard.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cn.shakeguard.app.data.GuardRepository
import cn.shakeguard.app.model.AppPolicy
import cn.shakeguard.app.model.DeepLinkMode
import cn.shakeguard.app.service.ShakeGuardAccessibilityService
import cn.shakeguard.app.service.ShizukuSensorController

class MainActivity : Activity() {

    private var currentTab = 0
    private var logLayerFilter = "ALL"

    private lateinit var contentContainer: LinearLayout
    private val navTabViews = mutableListOf<TextView>()

    private val repoListener: () -> Unit = {
        renderCurrentTab()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardRepository.init(applicationContext)
        seedDemoLogsIfNeeded()
        setupShellLayout()
        GuardRepository.addListener(repoListener)
    }

    override fun onResume() {
        super.onResume()
        renderCurrentTab()
    }

    override fun onDestroy() {
        GuardRepository.removeListener(repoListener)
        super.onDestroy()
    }

    private fun seedDemoLogsIfNeeded() {
        if (GuardRepository.interceptLogs.isEmpty() &&
            GuardRepository.skippedSplashCount == 0 &&
            GuardRepository.blockedShakeJumpCount == 0
        ) {
            GuardRepository.recordInterception(
                layer = "L1",
                sourceApp = "Sample Reader / 示例应用 A (Demo)",
                targetDesc = "btn_skip",
                actionTaken = "Auto-Skip Splash / 毫秒级跳过",
                detail = "Auto-clicked generic countdown button within 120ms / 通用倒计时按钮辅助点击"
            )
            GuardRepository.recordInterception(
                layer = "L2",
                sourceApp = "Sample News / 示例应用 B (Demo)",
                targetDesc = "Sandbox Target A (shakeguarddemo://)",
                actionTaken = "Blocked Cross-App Jump / 阻断跨应用唤醒",
                detail = "Blocked shake-ad URI shakeguarddemo://sandbox/item?id=1001"
            )
            GuardRepository.recordInterception(
                layer = "L3",
                sourceApp = "Sample Media / 示例应用 C (Demo)",
                targetDesc = "OP_SENSORS Frozen 8s / 冻结8秒",
                actionTaken = "Splash Sensor Cutoff / 开屏禁传感器",
                detail = "Cut off accelerometer & gyroscope for 8s during cold start / 冷启动前8秒切断陀螺仪权限"
            )
        }
    }

    private fun setupShellLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.BG_ROOT)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = UiTheme.dp(context, 16f)
            val padV = UiTheme.dp(context, 13f)
            setPadding(padH, padV, padH, padV)
            setBackgroundColor(UiTheme.BG_SURFACE)
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = I18n.t("🛡️ 摇一摇守卫 · ShakeGuard", "🛡️ ShakeGuard · Anti-Shake Shield")
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        titleCol.addView(TextView(this).apply {
            text = I18n.t(
                "通用传感器防误触与跨应用调用确认辅助工具 v1.3",
                "Motion Mis-Touch Guard & URI Launch Confirmation Utility v1.3"
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        })
        topBar.addView(titleCol)

        // 中/英文一键切换按钮
        val langBtn = UiTheme.makeButton(
            this,
            I18n.t("🌐 EN", "🌐 中文"),
            bgColor = UiTheme.BG_CARD_SUBTLE,
            textColor = UiTheme.TEXT_PRIMARY,
            strokeColor = UiTheme.BORDER_SUBTLE
        ) {
            I18n.toggleLang()
            setupShellLayout()
        }.apply {
            val pH = UiTheme.dp(context, 10f)
            val pV = UiTheme.dp(context, 6f)
            setPadding(pH, pV, pH, pV)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = UiTheme.dp(context, 6f)
            }
        }
        topBar.addView(langBtn)

        val labQuickBtn = UiTheme.makeButton(
            this,
            I18n.t("🧪 靶场", "🧪 Lab"),
            bgColor = UiTheme.EMERALD_DARK,
            textColor = UiTheme.EMERALD_LIGHT,
            strokeColor = UiTheme.EMERALD
        ) {
            startActivity(Intent(this, ShakeAdLabActivity::class.java))
        }.apply {
            val pH = UiTheme.dp(context, 10f)
            val pV = UiTheme.dp(context, 6f)
            setPadding(pH, pV, pH, pV)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        topBar.addView(labQuickBtn)
        root.addView(topBar)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiTheme.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
        }
        scrollView.addView(contentContainer)
        root.addView(scrollView)

        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(UiTheme.BG_SURFACE)
            val padV = UiTheme.dp(context, 8f)
            val padH = UiTheme.dp(context, 8f)
            setPadding(padH, padV, padH, padV)
        }

        val tabs = listOf(
            I18n.t("🛡️ 护盾总览", "🛡️ Shield"),
            I18n.t("📱 应用策略", "📱 Apps"),
            I18n.t("⚙️ 拦截规则", "⚙️ Rules"),
            I18n.t("📋 拦截日志", "📋 Logs")
        )
        navTabViews.clear()
        for ((idx, label) in tabs.withIndex()) {
            val tabTv = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
                val pV = UiTheme.dp(context, 10f)
                setPadding(0, pV, 0, pV)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = UiTheme.dp(context, 3f)
                    rightMargin = UiTheme.dp(context, 3f)
                }
                setOnClickListener {
                    currentTab = idx
                    updateBottomNavHighlight()
                    renderCurrentTab()
                }
            }
            navTabViews.add(tabTv)
            bottomNav.addView(tabTv)
        }

        root.addView(bottomNav)
        setContentView(root)
        updateBottomNavHighlight()
        renderCurrentTab()
    }

    private fun updateBottomNavHighlight() {
        for ((idx, tv) in navTabViews.withIndex()) {
            if (idx == currentTab) {
                tv.setTextColor(UiTheme.EMERALD_LIGHT)
                tv.background = UiTheme.roundedBg(UiTheme.EMERALD_DARK, 12f, this, UiTheme.EMERALD, 1f)
            } else {
                tv.setTextColor(UiTheme.TEXT_SECONDARY)
                tv.background = null
            }
        }
    }

    private fun renderCurrentTab() {
        if (!::contentContainer.isInitialized) return
        contentContainer.removeAllViews()
        when (currentTab) {
            0 -> renderDashboardTab()
            1 -> renderAppPoliciesTab()
            2 -> renderRuleCenterTab()
            3 -> renderLogsTab()
        }
    }

    // ==================== TAB 0: 护盾总览 / Shield Dashboard ====================
    private fun renderDashboardTab() {
        val accEnabled = ShakeGuardAccessibilityService.isServiceEnabled(this)
        val privState = ShizukuSensorController.inspectPrivilegeState(this)
        val masterOn = GuardRepository.masterShieldEnabled

        val heroCard = UiTheme.makeCard(this).apply {
            background = UiTheme.gradientBg(
                if (masterOn) {
                    intArrayOf(Color.parseColor("#0D3B2E"), Color.parseColor("#13233F"))
                } else {
                    intArrayOf(Color.parseColor("#3B250D"), Color.parseColor("#182235"))
                },
                20f,
                context,
                if (masterOn) UiTheme.EMERALD else UiTheme.AMBER,
                1.4f
            )
        }

        val heroTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val radarView = RadarShieldView(this).apply {
            shieldActive = masterOn
            val size = UiTheme.dp(context, 78f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = UiTheme.dp(context, 14f)
            }
        }
        heroTopRow.addView(radarView)

        val heroTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        heroTextCol.addView(TextView(this).apply {
            text = if (masterOn) {
                I18n.t("三层防摇护盾 · 守护中", "3-Layer Anti-Shake Shield · Active")
            } else {
                I18n.t("防摇护盾已暂停", "Anti-Shake Shield · Paused")
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        heroTextCol.addView(TextView(this).apply {
            text = when {
                !masterOn -> I18n.t(
                    "点击下方按钮立即恢复全局防摇一摇与跨应用跳转保护",
                    "Tap the button below to resume global anti-shake & jump protection"
                )
                accEnabled && privState.canExecutePrivileged -> I18n.t(
                    "L1无障碍 + L2跳转拦截 + L3传感器阻断 全满血运行中",
                    "L1 Accessibility + L2 URI Guard + L3 Sensor Cutoff All Active"
                )
                accEnabled -> I18n.t(
                    "L1无障碍秒跳 + L2跨应用跳转拦截 已激活生效",
                    "L1 Splash Auto-Skip + L2 Cross-App Jump Guard Active"
                )
                else -> I18n.t(
                    "L2 DeepLink 协议拦截已就绪 · 建议开启 L1 无障碍服务",
                    "L2 DeepLink Guard Ready · Enable L1 Accessibility Below"
                )
            }
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, UiTheme.dp(context, 4f), 0, UiTheme.dp(context, 8f))
        })

        val toggleMasterBtn = UiTheme.makeButton(
            this,
            if (masterOn) {
                I18n.t("🟢 全局护盾已开启（点击暂停）", "🟢 Master Shield ON (Tap to Pause)")
            } else {
                I18n.t("🟠 立即恢复全局护盾", "🟠 Resume Master Shield")
            },
            bgColor = if (masterOn) UiTheme.EMERALD_DARK else UiTheme.AMBER,
            textColor = Color.WHITE,
            strokeColor = if (masterOn) UiTheme.EMERALD_LIGHT else Color.TRANSPARENT
        ) {
            GuardRepository.masterShieldEnabled = !masterOn
        }.apply {
            val pH = UiTheme.dp(context, 12f)
            val pV = UiTheme.dp(context, 7f)
            setPadding(pH, pV, pH, pV)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        }
        heroTextCol.addView(toggleMasterBtn)
        heroTopRow.addView(heroTextCol)
        heroCard.addView(heroTopRow)

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = UiTheme.dp(context, 14f)
            }
        }
        statsRow.addView(
            makeStatCell(
                I18n.t("L1 开屏秒跳", "L1 Skipped"),
                "${GuardRepository.skippedSplashCount}",
                UiTheme.EMERALD_LIGHT
            )
        )
        statsRow.addView(
            makeStatCell(
                I18n.t("L2 阻断误跳", "L2 Blocked"),
                "${GuardRepository.blockedShakeJumpCount}",
                UiTheme.AMBER
            )
        )
        statsRow.addView(
            makeStatCell(
                I18n.t("L3 冻结传感器", "L3 Sensor Cut"),
                "${GuardRepository.sensorCutoffCount}",
                UiTheme.BLUE
            )
        )
        heroCard.addView(statsRow)
        contentContainer.addView(heroCard)

        // 2. 真机摇一摇广告靶场入口卡片
        val labCard = UiTheme.makeCard(this).apply {
            background = UiTheme.roundedBg(UiTheme.BG_CARD_SUBTLE, 16f, context, UiTheme.BLUE, 1.2f)
        }
        labCard.addView(TextView(this).apply {
            text = I18n.t(
                "🧪 运动传感器防误触实验室（一键自测）",
                "🧪 Shake-Ad Hardware Sandbox Lab (Self-Test)"
            )
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        labCard.addView(TextView(this).apply {
            text = I18n.t(
                "内置真实加速度计/陀螺仪波形监测与模拟高灵敏度传感器视图，晃动手机即可亲眼验证 L1 自动秒跳、L2 跨应用调用二次确认弹窗与 L3 传感器归零效果。",
                "Live accelerometer/gyroscope waveform monitor & simulated rogue splash ad. Shake your phone to test L1 Auto-Skip, L2 DeepLink Interceptor, and L3 Sensor Cutoff."
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, UiTheme.dp(context, 4f), 0, UiTheme.dp(context, 10f))
        })
        labCard.addView(
            UiTheme.makeButton(
                this,
                I18n.t("🚀 打开真机摇一摇广告靶场", "🚀 Launch Shake-Ad Sandbox Lab"),
                bgColor = UiTheme.BLUE,
                textColor = Color.WHITE
            ) {
                startActivity(Intent(this, ShakeAdLabActivity::class.java))
            }
        )
        contentContainer.addView(labCard)

        // 3. L1 无障碍开屏秒跳与防摇护盾控制卡
        val l1Card = UiTheme.makeCard(this)
        val l1Header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        l1Header.addView(TextView(this).apply {
            text = I18n.t(
                "第一层 (L1) · 无障碍开屏秒跳与防摇护盾",
                "Layer 1 (L1) · Accessibility Auto-Skip & Shield"
            )
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        l1Header.addView(
            UiTheme.makeBadge(
                this,
                if (accEnabled) I18n.t("已激活", "Active") else I18n.t("待开启", "Inactive"),
                if (accEnabled) UiTheme.EMERALD_LIGHT else UiTheme.AMBER,
                if (accEnabled) UiTheme.EMERALD_DARK else UiTheme.AMBER_DARK
            )
        )
        l1Card.addView(l1Header)

        l1Card.addView(TextView(this).apply {
            text = I18n.t(
                "免 Root / 免 Shizuku：根据用户自定义规则识别通用「跳过/关闭」按钮并在 100ms 内跳过；发现「摇一摇/扭一扭」节点立即锁定 7 秒防跳期。",
                "Rootless & Shizuku-free: Automatically detects & clicks splash 'Skip' buttons within 100ms; activates a 7-second anti-jump lock upon detecting 'Shake/Twist' ad views."
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, UiTheme.dp(context, 6f), 0, UiTheme.dp(context, 10f))
        })

        if (!accEnabled) {
            val unlockBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = UiTheme.dp(context, 12f)
                setPadding(pad, pad, pad, pad)
                background = UiTheme.roundedBg(Color.parseColor("#2A1810"), 14f, context, UiTheme.AMBER, 1.2f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = UiTheme.dp(context, 10f)
                }
            }
            unlockBox.addView(TextView(this).apply {
                text = I18n.t(
                    "⚠️ 遇到「系统拒绝应用授权 / 受限设置」？按这两步 15 秒解除：",
                    "⚠️ Seeing 'Restricted Setting / System Refused Authorization'? Unlock in 2 steps:"
                )
                setTextColor(UiTheme.AMBER)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
            })
            unlockBox.addView(TextView(this).apply {
                text = I18n.t(
                    "原因：Android 13/14/15 及小米/vivo/OPPO/华为系统对非应用商店安装的 APK 默认开启「受限设置」保护。必须先在【应用详情页】右上角点「⋮」允许受限设置，才能开启无障碍！",
                    "Android 13/14/15 & Chinese ROMs block Accessibility for sideloaded APKs by default. First open App Info -> tap '⋮' (top-right) -> 'Allow restricted settings'."
                )
                setTextColor(UiTheme.TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setPadding(0, UiTheme.dp(context, 4f), 0, UiTheme.dp(context, 10f))
            })
            unlockBox.addView(
                UiTheme.makeButton(
                    this,
                    I18n.t(
                        "🔓 第 1 步：打开【本应用详情】→ 点右上角 ⋮ 允许受限设置",
                        "🔓 Step 1: Open App Info → Tap ⋮ → Allow Restricted Settings"
                    ),
                    bgColor = UiTheme.AMBER,
                    textColor = Color.parseColor("#0B1221")
                ) {
                    openAppDetailsForRestrictedUnlock()
                }.apply {
                    (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = UiTheme.dp(context, 8f)
                }
            )
            unlockBox.addView(
                UiTheme.makeButton(
                    this,
                    I18n.t(
                        "👉 第 2 步：解除后点此前往【无障碍设置】开启服务",
                        "👉 Step 2: Open Accessibility Settings & Enable ShakeGuard"
                    ),
                    bgColor = UiTheme.EMERALD,
                    textColor = Color.WHITE
                ) {
                    try {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (_: Throwable) {
                        Toast.makeText(
                            this,
                            I18n.t(
                                "请前往系统设置 -> 无障碍 -> 已下载的应用 -> 开启摇一摇守卫",
                                "Go to Settings -> Accessibility -> Downloaded Apps -> Enable ShakeGuard"
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = UiTheme.dp(context, 8f)
                    }
                }
            )
            unlockBox.addView(
                UiTheme.makeButton(
                    this,
                    I18n.t(
                        "📖 查看各品牌手机（小米/华为/vivo/OPPO）解除拒绝授权详解 & ADB直通命令",
                        "📖 Brand-Specific Unlock Guide (Xiaomi/Huawei/vivo/OPPO) & ADB Command"
                    ),
                    bgColor = UiTheme.BG_CARD_SUBTLE,
                    textColor = UiTheme.TEXT_PRIMARY,
                    strokeColor = UiTheme.BORDER_SUBTLE
                ) {
                    showRestrictedAuthHelpDialog()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = UiTheme.dp(context, 8f)
                    }
                }
            )
            l1Card.addView(unlockBox)
        } else {
            l1Card.addView(
                UiTheme.makeButton(
                    this,
                    I18n.t(
                        "✅ 无障碍服务已激活运行中（点击查看系统无障碍设置）",
                        "✅ Accessibility Service Active (Tap to View Settings)"
                    ),
                    bgColor = UiTheme.BG_CARD_SUBTLE,
                    textColor = UiTheme.EMERALD_LIGHT,
                    strokeColor = UiTheme.EMERALD
                ) {
                    try {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (_: Throwable) {
                    }
                }
            )
        }

        l1Card.addView(
            UiTheme.makeSwitchRow(
                this,
                I18n.t("开屏广告毫秒级自动跳过", "Millisecond Splash Ad Auto-Skip"),
                I18n.t(
                    "匹配 30+ 广告 SDK 跳过控件 ID 与正则文本，缩减摇一摇暴露时间",
                    "Matches 30+ ad SDK skip button IDs and regex patterns to shorten shake-ad exposure"
                ),
                GuardRepository.l1AutoSkipSplash
            ) { GuardRepository.l1AutoSkipSplash = it }
        )
        l1Card.addView(
            UiTheme.makeSwitchRow(
                this,
                I18n.t("摇一摇高危节点顶部护盾胶囊提示", "Floating Shield Pill on Shake-Ad Detection"),
                I18n.t(
                    "检测到屏幕出现「摇一摇/扭一扭/前倾」时在顶部弹出护盾并提供一键强退按钮",
                    "Displays a non-intrusive top shield banner with a quick-back button when a shake ad appears"
                ),
                GuardRepository.l1ShakeAlertOverlay
            ) { GuardRepository.l1ShakeAlertOverlay = it }
        )
        l1Card.addView(
            UiTheme.makeSwitchRow(
                this,
                I18n.t("非点击属性按钮坐标手势兜底", "Coordinate Gesture Tap Fallback"),
                I18n.t(
                    "针对故意将 clickable 设为 false 的无点击属性跳过按钮，自动派发中心坐标点击手势",
                    "Dispatches a coordinate tap gesture for rogue skip buttons with clickable=false"
                ),
                GuardRepository.l1GestureTapFallback
            ) { GuardRepository.l1GestureTapFallback = it }
        )
        contentContainer.addView(l1Card)

        // 4. L2 跨应用 DeepLink 唤醒拦截与突跳拉回控制卡
        val l2Card = UiTheme.makeCard(this)
        val l2Header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        l2Header.addView(TextView(this).apply {
            text = I18n.t(
                "第二层 (L2) · 跨应用唤醒拦截与强行拉回",
                "Layer 2 (L2) · Cross-App DeepLink Guard & Bounce-Back"
            )
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        l2Header.addView(
            UiTheme.makeBadge(
                this,
                I18n.t("常驻就绪", "Always Ready"),
                UiTheme.EMERALD_LIGHT,
                UiTheme.EMERALD_DARK
            )
        )
        l2Card.addView(l2Header)

        l2Card.addView(TextView(this).apply {
            text = I18n.t(
                "支持对跨应用 DeepLink URI 协议进行二次确认弹窗提示，并在摇一摇强行跨包突跳时自动执行系统返回拉回原应用。",
                "Provides secondary user confirmation for cross-app URI scheme launches and automatically triggers GLOBAL_ACTION_BACK on unauthorized cross-app jumps."
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, UiTheme.dp(context, 6f), 0, UiTheme.dp(context, 10f))
        })

        val currentMode = GuardRepository.l2DeepLinkMode
        for (m in DeepLinkMode.values()) {
            val selected = (m == currentMode)
            val modeBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val p = UiTheme.dp(context, 11f)
                setPadding(p, p, p, p)
                background = UiTheme.roundedBg(
                    if (selected) UiTheme.EMERALD_DARK else UiTheme.BG_ROOT,
                    12f,
                    context,
                    if (selected) UiTheme.EMERALD else UiTheme.BORDER_SUBTLE,
                    1.2f
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = UiTheme.dp(context, 8f)
                }
                setOnClickListener {
                    GuardRepository.l2DeepLinkMode = m
                }
            }
            modeBox.addView(TextView(this).apply {
                text = (if (selected) "🔘 " else "⚪ ") + m.label
                setTextColor(if (selected) UiTheme.EMERALD_LIGHT else UiTheme.TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
            })
            modeBox.addView(TextView(this).apply {
                text = m.desc
                setTextColor(UiTheme.TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setPadding(UiTheme.dp(context, 18f), UiTheme.dp(context, 2f), 0, 0)
            })
            l2Card.addView(modeBox)
        }

        l2Card.addView(
            UiTheme.makeSwitchRow(
                this,
                I18n.t(
                    "开屏期跨应用突跳自动拉回 (GLOBAL_ACTION_BACK)",
                    "Auto Bounce-Back on Unclicked Cross-App Jump"
                ),
                I18n.t(
                    "若在未触屏点击的情况下被摇一摇直接发生非触屏跨应用跳转，立即自动按返回键拉回",
                    "Automatically presses system Back if an app triggers an unclicked cross-app jump during splash without a user touch"
                ),
                GuardRepository.l2CrossAppBounceBack
            ) { GuardRepository.l2CrossAppBounceBack = it }
        )

        l2Card.addView(
            UiTheme.makeButton(
                this,
                I18n.t(
                    "⚡ 立即测试跨应用调用二次确认弹窗 (shakeguarddemo://)",
                    "⚡ Test Cross-App Confirmation Dialog (shakeguarddemo://)"
                ),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                textColor = UiTheme.EMERALD_LIGHT,
                strokeColor = UiTheme.EMERALD
            ) {
                val testIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("shakeguarddemo://sandbox/item?id=1001?source=dashboard_quick_test&sg_test=1")
                ).apply {
                    setPackage(packageName)
                }
                startActivity(testIntent)
            }
        )
        contentContainer.addView(l2Card)

        // 5. L3 Shizuku / AppOps 传感器权限物理阻断控制卡
        val l3Card = UiTheme.makeCard(this)
        val l3Header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        l3Header.addView(TextView(this).apply {
            text = I18n.t(
                "第三层 (L3) · Shizuku 传感器权限物理切断",
                "Layer 3 (L3) · Shizuku Hardware Sensor Cutoff"
            )
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        l3Header.addView(
            UiTheme.makeBadge(
                this,
                if (privState.canExecutePrivileged) {
                    I18n.t("特权已就绪", "Privileged Ready")
                } else {
                    I18n.t("支持免Root脚本", "ADB Script Ready")
                },
                if (privState.canExecutePrivileged) UiTheme.EMERALD_LIGHT else UiTheme.BLUE,
                if (privState.canExecutePrivileged) UiTheme.EMERALD_DARK else UiTheme.BLUE_DARK
            )
        )
        l3Card.addView(l3Header)

        l3Card.addView(TextView(this).apply {
            text = I18n.t(
                "当前状态：${privState.activeBackend}\n原理：通过系统 AppOps 将目标应用的 OP_SENSORS 设为 ignore，在开屏前 ${GuardRepository.l3DefaultCutoffSec} 秒（或全程）禁止其读取陀螺仪与加速度计，从物理根源废除摇一摇。",
                "Status: ${privState.activeBackend}\nMechanism: Sets OP_SENSORS to 'ignore' via Android AppOps for the first ${GuardRepository.l3DefaultCutoffSec}s of app launch (or permanently), blinding the ad SDK's gyroscope/accelerometer."
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, UiTheme.dp(context, 6f), 0, UiTheme.dp(context, 8f))
        })

        l3Card.addView(
            UiTheme.makeSwitchRow(
                this,
                I18n.t(
                    "应用冷启动开屏期自动冻结传感器权限",
                    "Auto-Freeze Sensors During App Cold Start"
                ),
                I18n.t(
                    "进入前台前 ${GuardRepository.l3DefaultCutoffSec} 秒自动禁用 OP_SENSORS，度过开屏后自动恢复",
                    "Disables OP_SENSORS for ${GuardRepository.l3DefaultCutoffSec}s when an app enters foreground, then auto-restores"
                ),
                GuardRepository.l3ShizukuSensorCutoff
            ) { GuardRepository.l3ShizukuSensorCutoff = it }
        )

        val secRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, UiTheme.dp(context, 6f), 0, UiTheme.dp(context, 10f))
        }
        secRow.addView(TextView(this).apply {
            text = I18n.t("默认开屏断连时长：", "Default Cutoff Window:")
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        })
        for (sec in listOf(5, 6, 8, 12)) {
            val active = (GuardRepository.l3DefaultCutoffSec == sec)
            secRow.addView(TextView(this).apply {
                text = "${sec}s"
                setTextColor(if (active) Color.WHITE else UiTheme.TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                val pH = UiTheme.dp(context, 10f)
                val pV = UiTheme.dp(context, 5f)
                setPadding(pH, pV, pH, pV)
                background = UiTheme.roundedBg(
                    if (active) UiTheme.EMERALD else UiTheme.BG_ROOT,
                    10f,
                    context,
                    UiTheme.BORDER_SUBTLE,
                    1f
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = UiTheme.dp(context, 6f)
                }
                setOnClickListener {
                    GuardRepository.l3DefaultCutoffSec = sec
                }
            })
        }
        l3Card.addView(secRow)

        l3Card.addView(
            UiTheme.makeButton(
                this,
                I18n.t(
                    "📋 生成免 Root ADB 传感器禁用指令（一键复制）",
                    "📋 Generate Rootless ADB Sensor Cutoff Script"
                ),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                textColor = UiTheme.TEXT_PRIMARY,
                strokeColor = UiTheme.BORDER_SUBTLE
            ) {
                showAdbScriptDialog()
            }
        )
        contentContainer.addView(l3Card)

        // 6. 国产系统防杀后台保活专享卡
        val pmPower = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBattery = pmPower?.isIgnoringBatteryOptimizations(packageName) ?: false
        val brand = "${Build.BRAND ?: ""} / ${Build.MANUFACTURER ?: ""}".trim()

        val romCard = UiTheme.makeCard(this)
        val romHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        romHeader.addView(TextView(this).apply {
            text = I18n.t(
                "🇨🇳 国产系统防杀后台保活专享 ($brand)",
                "🔋 Background Keep-Alive & ROM Whitelist ($brand)"
            )
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        romHeader.addView(
            UiTheme.makeBadge(
                this,
                if (ignoringBattery) {
                    I18n.t("电池无限制已生效", "Whitelisted")
                } else {
                    I18n.t("建议加入白名单", "Action Needed")
                },
                if (ignoringBattery) UiTheme.EMERALD_LIGHT else UiTheme.AMBER,
                if (ignoringBattery) UiTheme.EMERALD_DARK else UiTheme.AMBER_DARK
            )
        )
        romCard.addView(romHeader)

        romCard.addView(TextView(this).apply {
            text = I18n.t(
                "国内手机系统（小米澎湃OS/MIUI、华为鸿蒙、vivo OriginOS、OPPO ColorOS、荣耀 MagicOS）省电策略极严，锁屏后容易杀掉无障碍服务进程。请开启下方两项保活设置以确保全天候稳定拦截：",
                "Chinese ROMs (HyperOS/MIUI, HarmonyOS, OriginOS, ColorOS, MagicOS) aggressively kill background accessibility services when the screen locks. Enable the two keep-alive options below:"
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, UiTheme.dp(context, 6f), 0, UiTheme.dp(context, 10f))
        })

        romCard.addView(
            UiTheme.makeButton(
                this,
                if (ignoringBattery) {
                    I18n.t(
                        "✅ 第 1 项：已允许忽略电池优化（后台不休眠）",
                        "✅ Step 1: Battery Optimization Ignored (Unrestricted)"
                    )
                } else {
                    I18n.t(
                        "⚡ 第 1 项：一键申请【忽略电池优化 / 省电无限制】",
                        "⚡ Step 1: Request 'Ignore Battery Optimizations'"
                    )
                },
                bgColor = if (ignoringBattery) UiTheme.BG_CARD_SUBTLE else UiTheme.EMERALD,
                textColor = if (ignoringBattery) UiTheme.EMERALD_LIGHT else Color.WHITE,
                strokeColor = if (ignoringBattery) UiTheme.EMERALD else Color.TRANSPARENT
            ) {
                requestIgnoreBatteryOptimizations()
            }.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = UiTheme.dp(context, 8f)
            }
        )

        romCard.addView(
            UiTheme.makeButton(
                this,
                I18n.t(
                    "🛡️ 第 2 项：一键打开本机【自启动 / 后台保活】设置页",
                    "🛡️ Step 2: Open OEM Auto-Start & Background Settings"
                ),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                textColor = UiTheme.TEXT_PRIMARY,
                strokeColor = UiTheme.BORDER_SUBTLE
            ) {
                openDomesticRomAutoStartSettings()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = UiTheme.dp(context, 8f)
                }
            }
        )
        contentContainer.addView(romCard)

        // 7. 法律合规与免责声明卡片
        val legalCard = UiTheme.makeCard(this)
        legalCard.addView(TextView(this).apply {
            text = I18n.t("⚖️ 开源合规与免责声明 (Legal Disclaimer)", "⚖️ Open-Source Compliance & Legal Disclaimer")
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        legalCard.addView(TextView(this).apply {
            text = I18n.t(
                "本工具仅用于 Android 传感器防误触辅助、跨应用链接二次确认与无障碍技术学习研究。本软件不内置针对任何特定第三方企业或商业 SDK 的定向屏蔽规则，所有自定义规则均由用户在本地设备自行配置并独立承担责任，严禁用于任何商业用途或不正当竞争行为。",
                "This utility is provided strictly for educational research, motion-sensor mis-touch assistance, and user-confirmed URI routing. It contains NO hardcoded rules targeting any specific third-party vendor or commercial SDK. All custom rules are configured locally by the user at their own responsibility."
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, UiTheme.dp(context, 4f), 0, 0)
        })
        contentContainer.addView(legalCard)
    }

    private fun makeStatCell(title: String, value: String, valueColor: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = UiTheme.dp(context, 10f)
            setPadding(p, p, p, p)
            background = UiTheme.roundedBg(Color.parseColor("#880B1221"), 12f, context, UiTheme.BORDER_SUBTLE, 1f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = UiTheme.dp(context, 4f)
                rightMargin = UiTheme.dp(context, 4f)
            }
            addView(TextView(context).apply {
                text = value
                setTextColor(valueColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = title
                setTextColor(UiTheme.TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setPadding(0, UiTheme.dp(context, 2f), 0, 0)
            })
        }
    }

    // ==================== TAB 1: 应用策略 / App Policies ====================
    private fun renderAppPoliciesTab() {
        val headerCard = UiTheme.makeCard(this)
        headerCard.addView(TextView(this).apply {
            text = I18n.t(
                "📱 分应用防摇一摇与传感器管控策略 (${GuardRepository.appPolicies.size} 款受控应用)",
                "📱 Per-App Shake & Sensor Policies (${GuardRepository.appPolicies.size} Apps)"
            )
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        })
        headerCard.addView(TextView(this).apply {
            text = I18n.t(
                "本项目不预设针对任何特定第三方厂商的定向规则。您也可以一键扫描本机已安装的第三方应用加入守护。",
                "Vendor-neutral template with zero hardcoded third-party commercial app rules. Tap below to scan and add your installed apps."
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, UiTheme.dp(context, 4f), 0, UiTheme.dp(context, 10f))
        })
        headerCard.addView(
            UiTheme.makeButton(
                this,
                I18n.t("🔍 扫描本机已安装应用并纳入防摇保护", "🔍 Scan Installed Apps & Add to Shield"),
                bgColor = UiTheme.EMERALD
            ) {
                scanInstalledUserApps()
            }
        )
        contentContainer.addView(headerCard)

        for (policy in GuardRepository.appPolicies) {
            val itemCard = UiTheme.makeCard(this, padDp = 14f, bottomMarginDp = 10f)
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleCol.addView(TextView(this).apply {
                text = "${I18n.translateAppName(policy.appName)}  ·  ${I18n.translateCategory(policy.category)}"
                setTextColor(UiTheme.TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
            })
            titleCol.addView(TextView(this).apply {
                text = policy.packageName
                setTextColor(UiTheme.TEXT_MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            })
            topRow.addView(titleCol)

            val sensorBadgeText = if (policy.permanentBlockSensor) {
                I18n.t("永久禁陀螺仪", "Sensors Blocked")
            } else {
                I18n.t("开屏禁传感器 ${policy.blockSensorWindowSec}s", "Splash Cutoff ${policy.blockSensorWindowSec}s")
            }
            topRow.addView(
                UiTheme.makeBadge(
                    this,
                    sensorBadgeText,
                    if (policy.permanentBlockSensor) UiTheme.ROSE else UiTheme.EMERALD_LIGHT,
                    if (policy.permanentBlockSensor) UiTheme.ROSE_DARK else UiTheme.EMERALD_DARK
                )
            )
            itemCard.addView(topRow)

            val pillsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = UiTheme.dp(context, 10f)
                }
            }

            pillsRow.addView(
                makePolicyTogglePill(
                    label = if (policy.autoSkipSplash) {
                        I18n.t("✅ L1开屏秒跳", "✅ L1 Auto-Skip")
                    } else {
                        I18n.t("⚪ L1开屏秒跳", "⚪ L1 Auto-Skip")
                    },
                    active = policy.autoSkipSplash
                ) {
                    policy.autoSkipSplash = !policy.autoSkipSplash
                    GuardRepository.updateAppPolicy(policy)
                }
            )
            pillsRow.addView(
                makePolicyTogglePill(
                    label = if (policy.blockDeeplink) {
                        I18n.t("✅ L2禁跨包跳", "✅ L2 Block Jump")
                    } else {
                        I18n.t("⚪ L2禁跨包跳", "⚪ L2 Block Jump")
                    },
                    active = policy.blockDeeplink
                ) {
                    policy.blockDeeplink = !policy.blockDeeplink
                    GuardRepository.updateAppPolicy(policy)
                }
            )
            pillsRow.addView(
                makePolicyTogglePill(
                    label = if (policy.permanentBlockSensor) {
                        I18n.t("🔒 L3永久禁传感器", "🔒 L3 Always Cut")
                    } else {
                        I18n.t("⏱️ L3开屏禁${policy.blockSensorWindowSec}s", "⏱️ L3 Cut ${policy.blockSensorWindowSec}s")
                    },
                    active = true,
                    highlightAmber = policy.permanentBlockSensor
                ) {
                    policy.permanentBlockSensor = !policy.permanentBlockSensor
                    GuardRepository.updateAppPolicy(policy)
                    ShizukuSensorController.onAppEnteredForeground(this, policy.packageName)
                }
            )

            itemCard.addView(pillsRow)
            contentContainer.addView(itemCard)
        }
    }

    private fun makePolicyTogglePill(
        label: String,
        active: Boolean,
        highlightAmber: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                when {
                    highlightAmber -> UiTheme.AMBER
                    active -> UiTheme.EMERALD_LIGHT
                    else -> UiTheme.TEXT_MUTED
                }
            )
            val pV = UiTheme.dp(context, 7f)
            setPadding(UiTheme.dp(context, 6f), pV, UiTheme.dp(context, 6f), pV)
            background = UiTheme.roundedBg(
                when {
                    highlightAmber -> UiTheme.AMBER_DARK
                    active -> UiTheme.EMERALD_DARK
                    else -> UiTheme.BG_ROOT
                },
                10f,
                context,
                UiTheme.BORDER_SUBTLE,
                1f
            )
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = UiTheme.dp(context, 3f)
                rightMargin = UiTheme.dp(context, 3f)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun scanInstalledUserApps() {
        try {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            var added = 0
            for (ri in resolveInfos) {
                val appInfo = ri.activityInfo?.applicationInfo ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (isSystem || appInfo.packageName == packageName) continue
                if (GuardRepository.findPolicyForPackage(appInfo.packageName) == null) {
                    val label = ri.loadLabel(pm)?.toString() ?: appInfo.packageName
                    GuardRepository.appPolicies.add(
                        AppPolicy(
                            packageName = appInfo.packageName,
                            appName = label,
                            category = "本机已装",
                            blockSensorWindowSec = GuardRepository.l3DefaultCutoffSec,
                            blockDeeplink = true,
                            autoSkipSplash = true,
                            permanentBlockSensor = false
                        )
                    )
                    added++
                }
            }
            GuardRepository.notifyChanged()
            Toast.makeText(
                this,
                I18n.t(
                    "扫描完成：新增 $added 款本机桌面应用至守护列表",
                    "Scan complete: Added $added installed apps to protection list"
                ),
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Throwable) {
            Toast.makeText(
                this,
                I18n.t(
                    "若系统限制读取应用列表，可在应用详情->权限管理中开启",
                    "If app list query is restricted by ROM, enable it in App Info -> Permissions"
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openAppDetailsForRestrictedUnlock() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Toast.makeText(
                this,
                I18n.t(
                    "👉 请点击右上角【⋮】三个点（或滑到底部）→ 点击【允许受限设置】",
                    "👉 Tap '⋮' in the top-right corner (or bottom) → 'Allow restricted settings'"
                ),
                Toast.LENGTH_LONG
            ).show()
            startActivity(intent)
        } catch (_: Throwable) {
            Toast.makeText(
                this,
                I18n.t(
                    "请打开系统设置 -> 应用管理 -> 摇一摇守卫 -> 右上角三个点 -> 允许受限设置",
                    "Open Settings -> Apps -> ShakeGuard -> Top-right ⋮ -> Allow restricted settings"
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRestrictedAuthHelpDialog() {
        val adbCmd = """
# Unlock Restricted Settings & Enable ShakeGuard Accessibility via ADB:
adb shell appops set cn.shakeguard.app ACCESS_RESTRICTED_SETTINGS allow
adb shell settings put secure enabled_accessibility_services cn.shakeguard.app/cn.shakeguard.app.service.ShakeGuardAccessibilityService
adb shell settings put secure accessibility_enabled 1
""".trimIndent()

        val msgZh = """
为什么手机提示「系统拒绝应用授权 / 出于安全考虑此设置不可用」？

这是 Android 13 / 14 / 15 的【受限设置 (ECM)】安全机制，对所有通过微信/文件管理器/浏览器安装的 APK 默认锁死无障碍授权。无需电脑，按手机品牌操作即可解除：

1️⃣ 【小米 澎湃OS (HyperOS) / MIUI】
• 先去「设置 → 无障碍 → 已下载的应用 → 摇一摇守卫」点一下开启（触发受限记录）；
• 返回「设置 → 应用设置 → 应用管理 → 搜索“摇一摇守卫”」；
• 点击右上角「⋮」或滑到最底部，点击【允许受限设置 / 解除受限设置】，验证锁屏密码；
• 再回无障碍即可成功开启！

2️⃣ 【vivo OriginOS / iQOO】
• 先去无障碍点一下「摇一摇守卫」触发弹窗；
• 打开「设置 → 应用与权限 → 应用管理 → 摇一摇守卫」；
• 点击右上角「⋮」→【允许受限设置】→ 再回无障碍开启。

3️⃣ 【OPPO ColorOS / 一加 / Realme】
• 先去无障碍点一下「摇一摇守卫」；
• 打开「设置 → 应用 → 应用管理 → 摇一摇守卫」；
• 点击右上角「⋮」→【允许受限设置】→ 回无障碍开启。

4️⃣ 【华为 鸿蒙 HarmonyOS / 荣耀 MagicOS】
• 若被纯净模式阻拦：打开「设置 → 系统和更新 → 纯净模式」关闭增强防护；
• 打开「设置 → 应用和服务 → 应用管理 → 摇一摇守卫 → 右上角四个点 → 允许受限设置」。
""".trimIndent()

        val msgEn = """
Why does Android say "Restricted setting / System refused app authorization"?

Android 13/14/15 Enhanced Confirmation Mode (ECM) locks Accessibility Services for sideloaded APKs by default. To unlock in 15 seconds without a PC:

1️⃣ Trigger the prompt once:
• Go to Settings → Accessibility → Downloaded Apps → ShakeGuard, and tap the switch once.

2️⃣ Allow Restricted Settings:
• Tap "Step 1: Open App Info" in ShakeGuard (or go to Settings → Apps → ShakeGuard).
• Tap the "⋮" menu icon in the top-right corner (or scroll to the bottom on Xiaomi HyperOS).
• Tap "Allow restricted settings" and verify your lock screen PIN/fingerprint.

3️⃣ Enable Accessibility:
• Return to Accessibility Settings and toggle ShakeGuard ON!
""".trimIndent()

        AlertDialog.Builder(this)
            .setTitle(I18n.t("🔓 解决「系统拒绝应用授权」指南", "🔓 How to Unlock Restricted Settings"))
            .setMessage(I18n.t(msgZh, msgEn))
            .setPositiveButton(I18n.t("1. 去应用详情解除限制", "1. Open App Info")) { _, _ ->
                openAppDetailsForRestrictedUnlock()
            }
            .setNeutralButton(I18n.t("复制 ADB 强制开启命令", "Copy ADB Unlock Cmd")) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ShakeGuard_Unlock_ADB", adbCmd))
                Toast.makeText(
                    this,
                    I18n.t("✅ 已复制 ADB 强制授权命令", "✅ Copied ADB unlock command"),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(I18n.t("关闭", "Close"), null)
            .show()
    }

    // ==================== TAB 2: 拦截规则 / Rule Center ====================
    private fun renderRuleCenterTab() {
        val schemeCard = UiTheme.makeCard(this)
        schemeCard.addView(TextView(this).apply {
            text = I18n.t(
                "🔗 L2 跨应用唤醒协议拦截库 (${GuardRepository.schemeRules.size} 条 Scheme)",
                "🔗 L2 Cross-App DeepLink Scheme Rules (${GuardRepository.schemeRules.size} Schemes)"
            )
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        schemeCard.addView(TextView(this).apply {
            text = I18n.t(
                "点击任意协议条目的右侧按钮可在「弹窗拦截 / 静默阻断 / 允许放行」之间切换：",
                "Tap the action pill on any scheme rule to cycle between [Confirm / Silent Block / Allow]:"
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, UiTheme.dp(context, 4f), 0, UiTheme.dp(context, 10f))
        })

        schemeCard.addView(
            UiTheme.makeButton(
                this,
                I18n.t("➕ 新增自定义拦截 Scheme 协议头", "➕ Add Custom DeepLink Scheme Rule"),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                textColor = UiTheme.EMERALD_LIGHT,
                strokeColor = UiTheme.EMERALD
            ) {
                showAddSchemeDialog()
            }.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = UiTheme.dp(context, 10f)
            }
        )

        for (rule in GuardRepository.schemeRules) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pV = UiTheme.dp(context, 8f)
                val pH = UiTheme.dp(context, 10f)
                setPadding(pH, pV, pH, pV)
                background = UiTheme.roundedBg(UiTheme.BG_ROOT, 10f, context, UiTheme.BORDER_SUBTLE, 1f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = UiTheme.dp(context, 6f)
                }
            }
            val leftCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            leftCol.addView(TextView(this).apply {
                text = "${I18n.translateAppName(rule.targetApp)}  (${rule.scheme}://)"
                setTextColor(UiTheme.TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
            })
            leftCol.addView(TextView(this).apply {
                text = "${I18n.translateCategory(rule.category)} · ${rule.targetPkg.ifBlank { "Universal Scheme" }}"
                setTextColor(UiTheme.TEXT_MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
            row.addView(leftCol)

            val actionLabel = when (rule.defaultAction) {
                "block_silent" -> I18n.t("🔒 静默阻断", "🔒 Silent Block")
                "allow" -> I18n.t("✅ 允许放行", "✅ Allow")
                else -> I18n.t("🛡️ 拦截确认", "🛡️ Confirm")
            }
            val actionBtn = TextView(this).apply {
                text = actionLabel
                setTextColor(
                    when (rule.defaultAction) {
                        "block_silent" -> UiTheme.ROSE
                        "allow" -> UiTheme.TEXT_SECONDARY
                        else -> UiTheme.EMERALD_LIGHT
                    }
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                val pH = UiTheme.dp(context, 10f)
                val pV = UiTheme.dp(context, 6f)
                setPadding(pH, pV, pH, pV)
                background = UiTheme.roundedBg(UiTheme.BG_CARD_SUBTLE, 8f, context, UiTheme.BORDER_SUBTLE, 1f)
                setOnClickListener {
                    val next = when (rule.defaultAction) {
                        "intercept" -> "block_silent"
                        "block_silent" -> "allow"
                        else -> "intercept"
                    }
                    GuardRepository.addOrUpdateSchemeRule(rule.scheme, rule.targetApp, rule.targetPkg, next)
                }
            }
            row.addView(actionBtn)
            schemeCard.addView(row)
        }
        contentContainer.addView(schemeCard)

        val skipCard = UiTheme.makeCard(this)
        skipCard.addView(TextView(this).apply {
            text = I18n.t("⚡ L1 开屏跳过特征词与 SDK 控件库", "⚡ L1 Splash Skip Keywords & Ad SDK View IDs")
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        skipCard.addView(TextView(this).apply {
            text = I18n.t(
                "文本匹配词 (${GuardRepository.skipKeywords.size})：${GuardRepository.skipKeywords.joinToString(" · ")}\n\n内置广告联盟跳过 View ID (${GuardRepository.skipViewIds.size})：${GuardRepository.skipViewIds.joinToString(", ")}",
                "Skip Keywords (${GuardRepository.skipKeywords.size}): ${GuardRepository.skipKeywords.joinToString(" · ")}\n\nBuilt-in Ad SDK Skip View IDs (${GuardRepository.skipViewIds.size}): ${GuardRepository.skipViewIds.joinToString(", ")}"
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, UiTheme.dp(context, 6f), 0, UiTheme.dp(context, 10f))
        })
        skipCard.addView(
            UiTheme.makeButton(
                this,
                I18n.t("➕ 添加自定义开屏跳过词", "➕ Add Custom Skip Keyword"),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                textColor = UiTheme.TEXT_PRIMARY,
                strokeColor = UiTheme.BORDER_SUBTLE
            ) {
                showSingleInputDialog(
                    I18n.t("添加开屏跳过关键词", "Add Splash Skip Keyword"),
                    I18n.t("例如：跳过片头 / 关闭弹窗", "e.g., Skip Intro / Close Ad")
                ) { kw ->
                    GuardRepository.addCustomSkipKeyword(kw)
                }
            }
        )
        contentContainer.addView(skipCard)

        val shakeKwCard = UiTheme.makeCard(this)
        shakeKwCard.addView(TextView(this).apply {
            text = I18n.t("👋 摇一摇 / 扭一扭 / 前倾 识别词库", "👋 Shake / Twist / Tilt Ad Detection Keywords")
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        shakeKwCard.addView(TextView(this).apply {
            text = I18n.t(
                "摇晃诱导词 (${GuardRepository.shakeAdKeywords.size})：${GuardRepository.shakeAdKeywords.joinToString(" · ")}\n\n摇一摇 SDK 控件 ID (${GuardRepository.shakeViewIds.size})：${GuardRepository.shakeViewIds.joinToString(", ")}",
                "Shake Trigger Keywords (${GuardRepository.shakeAdKeywords.size}): ${GuardRepository.shakeAdKeywords.joinToString(" · ")}\n\nShake SDK View IDs (${GuardRepository.shakeViewIds.size}): ${GuardRepository.shakeViewIds.joinToString(", ")}"
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, UiTheme.dp(context, 6f), 0, UiTheme.dp(context, 10f))
        })
        shakeKwCard.addView(
            UiTheme.makeButton(
                this,
                I18n.t("➕ 添加自定义摇晃诱导特征词", "➕ Add Custom Shake-Ad Keyword"),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                textColor = UiTheme.TEXT_PRIMARY,
                strokeColor = UiTheme.BORDER_SUBTLE
            ) {
                showSingleInputDialog(
                    I18n.t("添加摇晃广告特征词", "Add Shake-Ad Keyword"),
                    I18n.t("例如：挥动手机 / 翻转跳转", "e.g., Shake to Open / Tilt Phone")
                ) { kw ->
                    GuardRepository.addCustomShakeKeyword(kw)
                }
            }
        )
        contentContainer.addView(shakeKwCard)
    }

    // ==================== TAB 3: 拦截日志 / Intercept Logs ====================
    private fun renderLogsTab() {
        val topCard = UiTheme.makeCard(this)
        topCard.addView(TextView(this).apply {
            text = I18n.t(
                "📋 实时防摇晃与跳转拦截台账 (共 ${GuardRepository.interceptLogs.size} 条)",
                "📋 Real-Time Interception Audit Log (${GuardRepository.interceptLogs.size} Entries)"
            )
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        })

        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, UiTheme.dp(context, 10f), 0, UiTheme.dp(context, 10f))
        }
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val filters = listOf(
            "ALL" to I18n.t("全部记录", "All"),
            "L1" to I18n.t("L1 开屏秒跳", "L1 Auto-Skip"),
            "L2" to I18n.t("L2 跳转拦截", "L2 URI Guard"),
            "L3" to I18n.t("L3 传感器阻断", "L3 Sensor Cut")
        )
        for ((code, name) in filters) {
            val active = (logLayerFilter == code)
            filterRow.addView(TextView(this).apply {
                text = name
                setTextColor(if (active) Color.WHITE else UiTheme.TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
                val pH = UiTheme.dp(context, 12f)
                val pV = UiTheme.dp(context, 6f)
                setPadding(pH, pV, pH, pV)
                background = UiTheme.roundedBg(
                    if (active) UiTheme.EMERALD else UiTheme.BG_ROOT,
                    12f,
                    context,
                    UiTheme.BORDER_SUBTLE,
                    1f
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = UiTheme.dp(context, 8f)
                }
                setOnClickListener {
                    logLayerFilter = code
                    renderCurrentTab()
                }
            })
        }
        filterScroll.addView(filterRow)
        topCard.addView(filterScroll)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        btnRow.addView(
            UiTheme.makeButton(
                this,
                I18n.t("📋 复制拦截报告", "📋 Copy Report"),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                textColor = UiTheme.TEXT_PRIMARY,
                strokeColor = UiTheme.BORDER_SUBTLE
            ) {
                copyLogsToClipboard()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = UiTheme.dp(context, 6f)
                }
            }
        )
        btnRow.addView(
            UiTheme.makeButton(
                this,
                I18n.t("🗑️ 清空日志", "🗑️ Clear Logs"),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                textColor = UiTheme.ROSE,
                strokeColor = UiTheme.BORDER_SUBTLE
            ) {
                GuardRepository.clearLogs()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = UiTheme.dp(context, 6f)
                }
            }
        )
        topCard.addView(btnRow)
        contentContainer.addView(topCard)

        val filtered = synchronized(GuardRepository.interceptLogs) {
            GuardRepository.interceptLogs.filter { logLayerFilter == "ALL" || it.layer == logLayerFilter }
        }

        if (filtered.isEmpty()) {
            val emptyCard = UiTheme.makeCard(this)
            emptyCard.addView(TextView(this).apply {
                text = I18n.t(
                    "暂无匹配该层级的拦截日志。您可前往「🧪 靶场」晃动手机测试实时拦截记录。",
                    "No logs matching this filter yet. Open the '🧪 Lab' to test shake-ad interception in real time."
                )
                setTextColor(UiTheme.TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(0, UiTheme.dp(context, 16f), 0, UiTheme.dp(context, 16f))
            })
            contentContainer.addView(emptyCard)
            return
        }

        for (rec in filtered) {
            val recCard = UiTheme.makeCard(this, padDp = 14f, bottomMarginDp = 10f)
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val (badgeColor, badgeBg) = when (rec.layer) {
                "L1" -> UiTheme.EMERALD_LIGHT to UiTheme.EMERALD_DARK
                "L2" -> UiTheme.AMBER to UiTheme.AMBER_DARK
                else -> UiTheme.BLUE to UiTheme.BLUE_DARK
            }
            head.addView(UiTheme.makeBadge(this, "${rec.layer} · ${rec.actionTaken}", badgeColor, badgeBg))
            head.addView(TextView(this).apply {
                text = rec.timestamp
                setTextColor(UiTheme.TEXT_MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            recCard.addView(head)

            recCard.addView(TextView(this).apply {
                text = I18n.t(
                    "来源应用：${rec.sourceApp}  →  目标：${rec.targetDesc}",
                    "Source: ${I18n.translateAppName(rec.sourceApp)}  →  Target: ${rec.targetDesc}"
                )
                setTextColor(UiTheme.TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, UiTheme.dp(context, 8f), 0, UiTheme.dp(context, 4f))
            })
            recCard.addView(TextView(this).apply {
                text = rec.detail
                setTextColor(UiTheme.TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            contentContainer.addView(recCard)
        }
    }

    private fun showAdbScriptDialog() {
        val script = ShizukuSensorController.generateAdbCommandScript(GuardRepository.appPolicies)
        AlertDialog.Builder(this)
            .setTitle(I18n.t("免 Root 系统级传感器禁用脚本", "Rootless ADB Sensor Cutoff Script"))
            .setMessage(script)
            .setPositiveButton(I18n.t("一键复制脚本", "Copy Script")) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ShakeGuard_ADB", script))
                Toast.makeText(
                    this,
                    I18n.t(
                        "✅ 已复制到剪贴板！可在电脑 ADB 或无线调试终端直接粘贴执行",
                        "✅ Copied to clipboard! Run in ADB or Wireless Debugging terminal."
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(I18n.t("关闭", "Close"), null)
            .show()
    }

    private fun showSingleInputDialog(title: String, hintStr: String, onConfirm: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = hintStr
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(I18n.t("确定添加", "Add")) { _, _ ->
                val v = input.text?.toString()?.trim().orEmpty()
                if (v.isNotEmpty()) {
                    onConfirm(v)
                    Toast.makeText(this, I18n.t("已添加：$v", "Added: $v"), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(I18n.t("取消", "Cancel"), null)
            .show()
    }

    private fun showAddSchemeDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiTheme.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
        }
        val schemeEt = EditText(this).apply {
            hint = I18n.t("协议头名称（例如：myapp 或 customscheme）", "Scheme name (e.g., amazon or shopee)")
        }
        val nameEt = EditText(this).apply {
            hint = I18n.t("目标应用名称（例如：自定义目标应用）", "Target App Name (e.g., Amazon Shopping)")
        }
        box.addView(schemeEt)
        box.addView(nameEt)

        AlertDialog.Builder(this)
            .setTitle(I18n.t("新增跨应用拦截 Scheme", "Add Custom DeepLink Scheme"))
            .setView(box)
            .setPositiveButton(I18n.t("保存并启用拦截", "Save & Intercept")) { _, _ ->
                val s = schemeEt.text?.toString()?.trim().orEmpty()
                val n = nameEt.text?.toString()?.trim().orEmpty()
                if (s.isNotEmpty()) {
                    GuardRepository.addOrUpdateSchemeRule(s, n.ifEmpty { s }, "", "intercept")
                    Toast.makeText(
                        this,
                        I18n.t("已添加协议拦截：$s://", "Added scheme rule: $s://"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(I18n.t("取消", "Cancel"), null)
            .show()
    }

    private fun copyLogsToClipboard() {
        val sb = StringBuilder()
        sb.appendLine(I18n.t("【摇一摇守卫 ShakeGuard 拦截诊断报告】", "[ShakeGuard Interception Diagnostic Report]"))
        sb.appendLine(
            I18n.t(
                "累计开屏秒跳：${GuardRepository.skippedSplashCount} 次 | 阻断误跳：${GuardRepository.blockedShakeJumpCount} 次 | 传感器冻结：${GuardRepository.sensorCutoffCount} 次",
                "L1 Skipped: ${GuardRepository.skippedSplashCount} | L2 Blocked: ${GuardRepository.blockedShakeJumpCount} | L3 Sensor Cut: ${GuardRepository.sensorCutoffCount}"
            )
        )
        sb.appendLine("----------------------------------------")
        for (rec in GuardRepository.interceptLogs.take(30)) {
            sb.appendLine("[${rec.timestamp}] [${rec.layer}] ${rec.sourceApp} -> ${rec.targetDesc} | ${rec.actionTaken}")
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ShakeGuard_Report", sb.toString()))
        Toast.makeText(
            this,
            I18n.t("✅ 拦截报告已复制到剪贴板", "✅ Diagnostic report copied to clipboard"),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isIgnoringBatteryOptimizations(packageName) == true) {
                Toast.makeText(
                    this,
                    I18n.t("✅ 当前已处于电池优化白名单（后台无限制）", "✅ Already in battery optimization whitelist"),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Throwable) {
                openAppDetailsForRestrictedUnlock()
            }
        }
    }

    private fun openDomesticRomAutoStartSettings() {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )
        for (comp in candidates) {
            try {
                val i = Intent().apply {
                    component = comp
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(i)
                Toast.makeText(
                    this,
                    I18n.t(
                        "👉 请将「摇一摇守卫」开启【允许自启动 / 允许后台运行】",
                        "👉 Enable 'Auto-Start / Allow Background Activity' for ShakeGuard"
                    ),
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (_: Throwable) {
            }
        }
        Toast.makeText(
            this,
            I18n.t(
                "👉 请在应用详情页中将【省电策略】设为【无限制】并开启【自启动】",
                "👉 Set Battery Saver to 'Unrestricted' and enable Auto-Start in App Info"
            ),
            Toast.LENGTH_LONG
        ).show()
        openAppDetailsForRestrictedUnlock()
    }
}
