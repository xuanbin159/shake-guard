package cn.shakeguard.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

object UiTheme {
    val BG_ROOT = Color.parseColor("#0B1221")
    val BG_SURFACE = Color.parseColor("#131E32")
    val BG_CARD = Color.parseColor("#18263F")
    val BG_CARD_SUBTLE = Color.parseColor("#1E2F4D")
    val BORDER_SUBTLE = Color.parseColor("#2A3F66")

    val EMERALD = Color.parseColor("#10B981")
    val EMERALD_LIGHT = Color.parseColor("#34D399")
    val EMERALD_DARK = Color.parseColor("#064E3B")

    val BLUE = Color.parseColor("#3B82F6")
    val BLUE_DARK = Color.parseColor("#1E3A8A")

    val AMBER = Color.parseColor("#F59E0B")
    val AMBER_DARK = Color.parseColor("#78350F")

    val ROSE = Color.parseColor("#EF4444")
    val ROSE_DARK = Color.parseColor("#7F1D1D")

    val TEXT_PRIMARY = Color.parseColor("#F8FAFC")
    val TEXT_SECONDARY = Color.parseColor("#94A3B8")
    val TEXT_MUTED = Color.parseColor("#64748B")

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun roundedBg(
        fillColor: Int,
        radiusDp: Float,
        context: Context,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Float = 0f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(fillColor)
            if (strokeWidthDp > 0f && strokeColor != Color.TRANSPARENT) {
                setStroke(dp(context, strokeWidthDp), strokeColor)
            }
        }
    }

    fun gradientBg(
        colors: IntArray,
        radiusDp: Float,
        context: Context,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Float = 0f
    ): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeWidthDp > 0f && strokeColor != Color.TRANSPARENT) {
                setStroke(dp(context, strokeWidthDp), strokeColor)
            }
        }
    }

    fun makeCard(context: Context, padDp: Float = 16f, bottomMarginDp: Float = 12f): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(context, padDp)
            setPadding(pad, pad, pad, pad)
            background = roundedBg(BG_CARD, 18f, context, BORDER_SUBTLE, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, bottomMarginDp)
            }
        }
    }

    fun makeBadge(context: Context, textStr: String, textColor: Int, bgColor: Int): TextView {
        return TextView(context).apply {
            text = textStr
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            val pxH = dp(context, 9f)
            val pxV = dp(context, 3.5f)
            setPadding(pxH, pxV, pxH, pxV)
            background = roundedBg(bgColor, 10f, context)
        }
    }

    fun makeButton(
        context: Context,
        label: String,
        bgColor: Int = EMERALD,
        textColor: Int = Color.WHITE,
        strokeColor: Int = Color.TRANSPARENT,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val padH = dp(context, 14f)
            val padV = dp(context, 11f)
            setPadding(padH, padV, padH, padV)
            background = roundedBg(
                bgColor,
                12f,
                context,
                strokeColor,
                if (strokeColor != Color.TRANSPARENT) 1.2f else 0f
            )
            setOnClickListener { onClick() }
        }
    }

    fun makeSwitchRow(
        context: Context,
        title: String,
        subtitle: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padV = dp(context, 8f)
            setPadding(0, padV, 0, padV)

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(context, 10f)
                }
            }
            textCol.addView(TextView(context).apply {
                text = title
                setTextColor(TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                typeface = Typeface.DEFAULT_BOLD
            })
            if (subtitle.isNotEmpty()) {
                textCol.addView(TextView(context).apply {
                    text = subtitle
                    setTextColor(TEXT_SECONDARY)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(context, 2f), 0, 0)
                })
            }

            val sw = Switch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
            }

            addView(textCol)
            addView(sw)
        }
    }
}

/**
 * 动态雷达护盾 Canvas 视图：实时渲染防摇一摇护盾脉冲波纹与中心盾牌标识。
 */
class RadarShieldView(context: Context) : View(context) {
    var shieldActive: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private var phase = 0f
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = UiTheme.dp(context, 2f).toFloat()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shieldPath = Path()
    private val checkPath = Path()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(width, height) / 2f * 0.92f
        val baseColor = if (shieldActive) UiTheme.EMERALD else UiTheme.AMBER

        // 绘制两层扩散脉冲环
        for (i in 0..1) {
            val p = (phase + i * 0.5f) % 1f
            val r = maxR * (0.48f + 0.52f * p)
            val alpha = ((1f - p) * 140).toInt().coerceIn(0, 255)
            ringPaint.color = baseColor
            ringPaint.alpha = alpha
            canvas.drawCircle(cx, cy, r, ringPaint)
        }

        // 中心圆盘
        val coreR = maxR * 0.54f
        fillPaint.shader = LinearGradient(
            cx - coreR, cy - coreR, cx + coreR, cy + coreR,
            if (shieldActive) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"),
            if (shieldActive) Color.parseColor("#047857") else Color.parseColor("#B45309"),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreR, fillPaint)
        fillPaint.shader = null

        // 绘制盾牌轮廓
        val s = coreR * 0.58f
        shieldPath.reset()
        shieldPath.moveTo(cx, cy - s)
        shieldPath.lineTo(cx + s * 0.85f, cy - s * 0.5f)
        shieldPath.lineTo(cx + s * 0.85f, cy + s * 0.15f)
        shieldPath.quadTo(cx + s * 0.65f, cy + s * 0.85f, cx, cy + s * 1.08f)
        shieldPath.quadTo(cx - s * 0.65f, cy + s * 0.85f, cx - s * 0.85f, cy + s * 0.15f)
        shieldPath.lineTo(cx - s * 0.85f, cy - s * 0.5f)
        shieldPath.close()

        fillPaint.color = Color.parseColor("#0B1221")
        fillPaint.alpha = 210
        canvas.drawPath(shieldPath, fillPaint)

        // 绘制对勾或暂停符号
        ringPaint.color = Color.WHITE
        ringPaint.alpha = 255
        ringPaint.strokeWidth = UiTheme.dp(context, 2.8f).toFloat()
        checkPath.reset()
        if (shieldActive) {
            checkPath.moveTo(cx - s * 0.36f, cy + s * 0.02f)
            checkPath.lineTo(cx - s * 0.08f, cy + s * 0.32f)
            checkPath.lineTo(cx + s * 0.42f, cy - s * 0.24f)
        } else {
            checkPath.moveTo(cx - s * 0.22f, cy - s * 0.28f)
            checkPath.lineTo(cx - s * 0.22f, cy + s * 0.32f)
            checkPath.moveTo(cx + s * 0.22f, cy - s * 0.28f)
            checkPath.lineTo(cx + s * 0.22f, cy + s * 0.32f)
        }
        canvas.drawPath(checkPath, ringPaint)
    }
}
