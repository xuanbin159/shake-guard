package cn.shakeguard.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cn.shakeguard.app.data.GuardRepository
import kotlin.math.sqrt

class ShakeAdLabActivity : Activity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private var simulateL3SensorFreeze = false
    private var adSkipped = false
    private var lastTriggerTimeMs = 0L

    private var waveView: SensorWaveView? = null
    private var readoutTv: TextView? = null
    private var adStatusTv: TextView? = null
    private var skipBtnTv: TextView? = null
    private var shakePromptTv: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardRepository.init(applicationContext)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        buildUi()
    }

    override fun onResume() {
        super.onResume()
        accelSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.BG_ROOT)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = UiTheme.dp(context, 16f)
            val padV = UiTheme.dp(context, 14f)
            setPadding(padH, padV, padH, padV)
            setBackgroundColor(UiTheme.BG_SURFACE)
        }
        val backBtn = UiTheme.makeButton(
            this,
            I18n.t("← 返回", "← Back"),
            bgColor = UiTheme.BG_CARD_SUBTLE,
            textColor = UiTheme.TEXT_PRIMARY
        ) {
            finish()
        }.apply {
            val pH = UiTheme.dp(context, 12f)
            val pV = UiTheme.dp(context, 6f)
            setPadding(pH, pV, pH, pV)
        }
        header.addView(backBtn)

        header.addView(TextView(this).apply {
            text = I18n.t("🧪 运动传感器防误触实验室", "🧪 Shake-Ad Hardware Sandbox Lab")
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(UiTheme.dp(context, 12f), 0, 0, 0)
        })
        root.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiTheme.dp(context, 16f)
            setPadding(pad, pad, pad, pad)
        }

        val sensorCard = UiTheme.makeCard(this)
        sensorCard.addView(
            UiTheme.makeBadge(
                this,
                I18n.t(
                    "📡 实时硬件传感器波形监测 (Accelerometer + Gyroscope)",
                    "📡 Live Hardware Sensor Waveform (Accelerometer + Gyroscope)"
                ),
                UiTheme.EMERALD_LIGHT,
                UiTheme.EMERALD_DARK
            )
        )

        val rTv = TextView(this).apply {
            text = I18n.t(
                "当前合成加速度: 9.81 m/s² | 晃动增量 Δa: 0.00 m/s² (红线阈值 1.80 m/s²)",
                "Total Accel: 9.81 m/s² | Shake Delta Δa: 0.00 m/s² (Trigger Threshold 1.80 m/s²)"
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, UiTheme.dp(context, 8f), 0, UiTheme.dp(context, 8f))
        }
        readoutTv = rTv
        sensorCard.addView(rTv)

        val wView = SensorWaveView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiTheme.dp(context, 130f)
            )
        }
        waveView = wView
        sensorCard.addView(wView)

        sensorCard.addView(
            UiTheme.makeSwitchRow(
                this,
                I18n.t(
                    "模拟 L3 传感器物理切断 (OP_SENSORS = ignore)",
                    "Simulate L3 Sensor Cutoff (OP_SENSORS = ignore)"
                ),
                I18n.t(
                    "开启后向广告 SDK 返回恒定 0 波形，无论如何用力摇晃手机均不会触发广告",
                    "Clamps sensor events to 0.00 m/s² so shake ads cannot trigger no matter how hard you shake"
                ),
                simulateL3SensorFreeze
            ) { checked ->
                simulateL3SensorFreeze = checked
                waveView?.sensorFrozen = checked
                if (checked) {
                    GuardRepository.recordInterception(
                        layer = "L3",
                        sourceApp = I18n.t("摇一摇靶场沙盒", "Shake Ad Sandbox"),
                        targetDesc = I18n.t("OP_SENSORS 归零保护", "OP_SENSORS Zeroed"),
                        actionTaken = I18n.t("冻结陀螺仪与加速度计", "Frozen Gyro & Accelerometer"),
                        detail = I18n.t(
                            "靶场开启 L3 传感器阻断后，广告组件收到的加速度增量恒为 0.00 m/s²",
                            "L3 sensor cutoff enabled in sandbox; ad SDK receives 0.00 m/s² delta"
                        )
                    )
                }
            }
        )
        content.addView(sensorCard)

        val adSimCard = UiTheme.makeCard(this).apply {
            background = UiTheme.gradientBg(
                intArrayOf(Color.parseColor("#2E1065"), Color.parseColor("#1E1B4B")),
                18f,
                context,
                UiTheme.AMBER,
                1.5f
            )
        }

        val adTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        adTopRow.addView(
            UiTheme.makeBadge(
                this,
                I18n.t("模拟开屏广告沙盒", "Simulated Splash Ad"),
                Color.WHITE,
                Color.parseColor("#6D28D9")
            )
        )
        adTopRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val skipBtn = TextView(this).apply {
            text = I18n.t("跳过广告", "Skip Ad")
            contentDescription = "跳过广告"
            isClickable = true
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            val pH = UiTheme.dp(context, 12f)
            val pV = UiTheme.dp(context, 5f)
            setPadding(pH, pV, pH, pV)
            background = UiTheme.roundedBg(Color.parseColor("#88000000"), 14f, context, Color.WHITE, 1f)
            setOnClickListener {
                adSkipped = true
                adStatusTv?.text = I18n.t(
                    "✅ 广告已被「跳过」！无障碍 L1 或手动跳过成功，摇一摇监听已终止。",
                    "✅ Splash Ad Skipped! L1 Auto-Skip or manual tap terminated the shake listener."
                )
                adStatusTv?.setTextColor(UiTheme.EMERALD_LIGHT)
                shakePromptTv?.text = I18n.t("🛡️ 开屏广告已跳过", "🛡️ Splash Ad Skipped")
                Toast.makeText(
                    applicationContext,
                    I18n.t("✅ 开屏广告已跳过！", "✅ Splash Ad Skipped!"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        skipBtnTv = skipBtn
        adTopRow.addView(skipBtn)
        adSimCard.addView(adTopRow)

        val promptTv = TextView(this).apply {
            text = I18n.t(
                "👋 摇一摇 或 前倾手机\n跳转详情页或第三方应用",
                "👋 Shake or Tilt Phone\nto Open Third-Party Shopping App"
            )
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, UiTheme.dp(context, 22f), 0, UiTheme.dp(context, 12f))
        }
        shakePromptTv = promptTv
        adSimCard.addView(promptTv)

        val statusTv = TextView(this).apply {
            text = I18n.t(
                "⚡ 当前状态：正在监听手机晃动（试着晃一下手机，或点击下方按钮直接测试拦截弹窗）",
                "⚡ Status: Listening for phone shake (Shake your phone now or tap a test button below)"
            )
            setTextColor(UiTheme.AMBER)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiTheme.dp(context, 14f))
        }
        adStatusTv = statusTv
        adSimCard.addView(statusTv)

        adSimCard.addView(
            UiTheme.makeButton(
                this,
                I18n.t("🔄 重新装载模拟开屏广告", "🔄 Reload Simulated Splash Ad"),
                bgColor = Color.parseColor("#4C1D95"),
                textColor = Color.WHITE
            ) {
                adSkipped = false
                shakePromptTv?.text = I18n.t(
                    "👋 摇一摇 或 前倾手机\n跳转详情页或第三方应用",
                    "👋 Shake or Tilt Phone\nto Open Third-Party Shopping App"
                )
                adStatusTv?.text = I18n.t(
                    "⚡ 已重新装载模拟摇一摇广告，请晃动手机或点击下方测试按钮",
                    "⚡ Simulated shake ad reloaded. Shake your phone or tap a button below."
                )
                adStatusTv?.setTextColor(UiTheme.AMBER)
            }
        )
        content.addView(adSimCard)

        val triggerCard = UiTheme.makeCard(this)
        triggerCard.addView(TextView(this).apply {
            text = I18n.t("🎯 直接测试 L2 跨应用唤醒拦截弹窗", "🎯 Test L2 Cross-App DeepLink Interceptor")
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
        })
        triggerCard.addView(TextView(this).apply {
            text = I18n.t(
                "无需等待真实开屏广告，点击下方任意按钮即可模拟广告 SDK 强行发起跨应用 DeepLink 协议调用，检验我方 L2 拦截器弹窗：",
                "Tap any button below to simulate a rogue ad SDK firing a DeepLink URI via custom URI schemes:"
            )
            setTextColor(UiTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, UiTheme.dp(context, 4f), 0, UiTheme.dp(context, 12f))
        })

        triggerCard.addView(
            UiTheme.makeButton(
                this,
                I18n.t(
                    "⚡ 模拟触发「跨应用调用测试 A」(shakeguarddemo://)",
                    "⚡ Simulate Cross-App URI Launch A (shakeguarddemo://)"
                ),
                bgColor = UiTheme.EMERALD
            ) {
                fireSimulatedAdScheme("shakeguarddemo://sandbox/item?id=1001&source=motion_lab&sg_test=1")
            }.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = UiTheme.dp(context, 8f)
            }
        )

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = UiTheme.dp(context, 8f)
            }
        }
        row2.addView(
            UiTheme.makeButton(
                this,
                I18n.t("⚡ 模拟「测试协议 B」", "⚡ Simulate Scheme B"),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                strokeColor = UiTheme.BORDER_SUBTLE
            ) {
                fireSimulatedAdScheme("appjump://sandbox/open?target=demo_b&sg_test=1")
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = UiTheme.dp(context, 6f)
                }
            }
        )
        row2.addView(
            UiTheme.makeButton(
                this,
                I18n.t("⚡ 模拟「测试协议 C」", "⚡ Simulate Scheme C"),
                bgColor = UiTheme.BG_CARD_SUBTLE,
                strokeColor = UiTheme.BORDER_SUBTLE
            ) {
                fireSimulatedAdScheme("extopen://sandbox/open?target=demo_c&sg_test=1")
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = UiTheme.dp(context, 6f)
                }
            }
        )
        triggerCard.addView(row2)
        content.addView(triggerCard)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (simulateL3SensorFreeze) {
                readoutTv?.text = I18n.t(
                    "🛡️ L3 传感器阻断生效中: 输出恒为 0.00 m/s² | 晃动增量 Δa: 0.00 m/s²",
                    "🛡️ L3 Sensor Cutoff Active: Output clamped to 0.00 m/s² | Δa: 0.00 m/s²"
                )
                waveView?.pushSample(0f)
                return
            }
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val mag = sqrt(ax * ax + ay * ay + az * az)
            val delta = kotlin.math.abs(mag - SensorManager.GRAVITY_EARTH)
            waveView?.pushSample(delta)

            readoutTv?.text = if (I18n.isEn) {
                String.format(
                    "Total Accel: %.2f m/s² | Shake Delta Δa: %.2f m/s² (Threshold 1.80 m/s²)",
                    mag,
                    delta
                )
            } else {
                String.format(
                    "当前合成加速度: %.2f m/s² | 晃动增量 Δa: %.2f m/s² (红线阈值 1.80 m/s²)",
                    mag,
                    delta
                )
            }

            val now = System.currentTimeMillis()
            if (!adSkipped && delta > 1.80f && (now - lastTriggerTimeMs > 3000L)) {
                lastTriggerTimeMs = now
                adStatusTv?.text = if (I18n.isEn) {
                    String.format(
                        "⚠️ Shake Δa=%.2f m/s² exceeded threshold! Firing cross-app scheme jump...",
                        delta
                    )
                } else {
                    String.format(
                        "⚠️ 检测到晃动 Δa=%.2f m/s² 突破广告阈值！正在触发跨应用 Scheme 跳转...",
                        delta
                    )
                }
                adStatusTv?.setTextColor(UiTheme.ROSE)
                fireSimulatedAdScheme("shakeguarddemo://sandbox/item?source=real_sensor_shake&delta=${"%.2f".format(delta)}&sg_test=1")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun fireSimulatedAdScheme(uriStr: String) {
        GuardRepository.activateHighAlert(packageName, I18n.t("摇一摇靶场沙盒", "Shake Ad Sandbox"), 6000L)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                setPackage(packageName)
            }
            startActivity(intent)
        } catch (_: Throwable) {
            Toast.makeText(
                applicationContext,
                I18n.t("拦截器启动异常", "Failed to launch interceptor"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

class SensorWaveView(context: Context) : View(context) {
    private val samples = FloatArray(90)
    private var writeIdx = 0
    var sensorFrozen = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B1221")
        style = Paint.Style.FILL
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.ROSE
        style = Paint.Style.STROKE
        strokeWidth = UiTheme.dp(context, 1.5f).toFloat()
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.EMERALD_LIGHT
        style = Paint.Style.STROKE
        strokeWidth = UiTheme.dp(context, 2.2f).toFloat()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.ROSE
        textSize = UiTheme.dp(context, 10.5f).toFloat()
    }
    private val path = Path()

    fun pushSample(delta: Float) {
        samples[writeIdx] = delta.coerceIn(0f, 6f)
        writeIdx = (writeIdx + 1) % samples.size
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, 16f, 16f, bgPaint)

        val threshY = h - (1.8f / 5.0f) * (h * 0.82f) - h * 0.08f
        canvas.drawLine(0f, threshY, w, threshY, thresholdPaint)
        canvas.drawText(
            I18n.t(
                "高灵敏度传感器触发参考线 (Δa = 1.8 m/s²)",
                "High-Sensitivity Motion Trigger Line (Δa = 1.8 m/s²)"
            ),
            UiTheme.dp(context, 8f).toFloat(),
            threshY - 6f,
            textPaint
        )

        path.reset()
        val stepX = w / (samples.size - 1)
        for (i in samples.indices) {
            val idx = (writeIdx + i) % samples.size
            val v = samples[idx]
            val x = i * stepX
            val y = h - (v / 5.0f) * (h * 0.82f) - h * 0.08f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        wavePaint.color = if (sensorFrozen) UiTheme.BLUE else UiTheme.EMERALD_LIGHT
        canvas.drawPath(path, wavePaint)
    }
}
