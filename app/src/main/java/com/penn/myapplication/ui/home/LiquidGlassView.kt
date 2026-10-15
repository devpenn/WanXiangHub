package com.penn.myapplication.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.penn.myapplication.R

/**
 * 苹果 iOS 18 / visionOS 风格的液态玻璃 (Liquid Glass) 容器 View。
 *
 * 核心视觉特征：
 * 1. 半透明乳化玻璃基底 (Glass Body Gradient)
 * 2. 边缘双阶折射高光线条 (Specular Rim Light Stroke)
 * 3. 顶部弧形透镜高光 (Top Lens Gloss Sheen)
 * 4. 柔和悬浮弥散阴影 (Floating Ambient Drop Shadow)
 * 5. 液态光影流转动效 (Shimmer Light Sweep)
 * 6. API 31+ 硬件级高斯模糊 (RenderEffect Blur)
 */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 样式配置参数
    var cornerRadius: Float = 28f * resources.displayMetrics.density // 圆角大小 (px)
    var isPillMode: Boolean = false                                   // 是否为全圆角胶囊模式
    var showShimmer: Boolean = true                                  // 是否启用液态流光动效
    var enableTouchFeedback: Boolean = true                          // 是否启用按压缩放反馈

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val topGlossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rectF = RectF()
    private val shadowRectF = RectF()
    private val glossPath = Path()

    // 动效参数
    private var shimmerProgress = 0f
    private var shimmerAnimator: ValueAnimator? = null
    private var touchScale = 1.0f

    init {
        // 允许自定义绘制 background/shadow
        setWillNotDraw(false)
        setupAttributes(attrs)
        setupShimmerAnimation()
        setupBlurIfSupported()
    }

    private fun setupAttributes(attrs: AttributeSet?) {
        if (attrs == null) return
        val density = resources.displayMetrics.density
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.LiquidGlassView
        )
        try {
            cornerRadius = typedArray.getDimension(
                R.styleable.LiquidGlassView_lgCornerRadius,
                28f * density
            )
            isPillMode = typedArray.getBoolean(
                R.styleable.LiquidGlassView_lgIsPillMode,
                false
            )
            showShimmer = typedArray.getBoolean(
                R.styleable.LiquidGlassView_lgShowShimmer,
                true
            )
            enableTouchFeedback = typedArray.getBoolean(
                R.styleable.LiquidGlassView_lgEnableTouchFeedback,
                true
            )
        } catch (e: Exception) {
            // styleable 未声明时的默认回退
        } finally {
            typedArray.recycle()
        }
    }

    private fun setupBlurIfSupported() {
        // 多重半透明玻璃滤镜在 onDraw 中手动渲染，不使用 View 级 setRenderEffect，
        // 避免导致容器内部的文字和图标节点被模糊
    }

    private fun setupShimmerAnimation() {
        shimmerAnimator = ValueAnimator.ofFloat(-0.6f, 1.6f).apply {
            duration = 4200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                shimmerProgress = anim.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerAnimator?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val width = w.toFloat()
        val height = h.toFloat()
        if (width <= 0 || height <= 0) return

        rectF.set(0f, 0f, width, height)

        // 1. 烟熏底膜 (保证在浅色/白色壁纸上文字依然无比清晰)
        darkTintPaint.shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(
                Color.parseColor("#30000000"),
                Color.parseColor("#1C000000"),
                Color.parseColor("#0A000000")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        // 2. 液态玻璃半透明基底 Gradient (顶部透亮乳化，底部清透)
        bodyPaint.shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(
                Color.parseColor("#38FFFFFF"), // 左上亮白半透明
                Color.parseColor("#1CFFFFFF"), // 中间折射
                Color.parseColor("#0DFFFFFF")  // 右下深沉清透
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        // 2. 边缘双阶折射高光 Rim Light (左上极亮白，右下柔和回光)
        strokePaint.shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(
                Color.parseColor("#F5FFFFFF"), // 左上折射纯白高光
                Color.parseColor("#70FFFFFF"), // 高光过渡
                Color.parseColor("#20FFFFFF"), // 暗部柔和边界
                Color.parseColor("#50FFFFFF")  // 底部反光
            ),
            floatArrayOf(0f, 0.35f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )

        // 3. 顶部液态弧形透镜高光 (Top Lens Gloss)
        topGlossPaint.shader = LinearGradient(
            0f, 0f, 0f, height * 0.42f,
            intArrayOf(
                Color.parseColor("#55FFFFFF"),
                Color.parseColor("#18FFFFFF"),
                Color.parseColor("#00FFFFFF")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        // 4. 弥散悬浮阴影
        shadowPaint.color = Color.parseColor("#1A000000")
        try {
            shadowPaint.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        } catch (_: Throwable) {
        }
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) {
            super.onDraw(canvas)
            return
        }

        val actualRadius = if (isPillMode) height / 2f else cornerRadius

        canvas.save()
        if (touchScale != 1.0f) {
            canvas.scale(touchScale, touchScale, width / 2f, height / 2f)
        }

        // 1. 绘制弥散悬浮阴影
        shadowRectF.set(4f, 6f, width - 4f, height + 4f)
        canvas.drawRoundRect(shadowRectF, actualRadius, actualRadius, shadowPaint)

        // 2. 绘制烟熏对比底膜 (在浅色壁纸下呈现磨砂烟熏压暗效果，文字更清晰)
        canvas.drawRoundRect(rectF, actualRadius, actualRadius, darkTintPaint)

        // 3. 绘制液态白玻璃基底
        canvas.drawRoundRect(rectF, actualRadius, actualRadius, bodyPaint)

        // 3. 绘制顶部透镜弧形高光
        glossPath.reset()
        val glossHeight = height * 0.42f
        glossPath.addRoundRect(
            RectF(0f, 0f, width, glossHeight),
            floatArrayOf(actualRadius, actualRadius, actualRadius, actualRadius, 0f, 0f, 0f, 0f),
            Path.Direction.CW
        )
        canvas.drawPath(glossPath, topGlossPaint)

        // 4. 绘制液态光影流转 Shimmer
        if (showShimmer && width > 0) {
            canvas.save()
            canvas.clipRect(rectF)
            val shimmerX = width * shimmerProgress
            shimmerPaint.shader = LinearGradient(
                shimmerX - 100f, 0f, shimmerX + 100f, height,
                intArrayOf(
                    Color.parseColor("#00FFFFFF"),
                    Color.parseColor("#38FFFFFF"),
                    Color.parseColor("#00FFFFFF")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rectF, actualRadius, actualRadius, shimmerPaint)
            canvas.restore()
        }

        // 5. 绘制边缘折射高光线条 (Rim Stroke)
        val halfStroke = strokePaint.strokeWidth / 2f
        val strokeRect = RectF(
            halfStroke, halfStroke,
            width - halfStroke, height - halfStroke
        )
        canvas.drawRoundRect(strokeRect, actualRadius, actualRadius, strokePaint)

        canvas.restore()

        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enableTouchFeedback || !isEnabled) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                animateScale(0.972f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateScale(1.0f)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateScale(targetScale: Float) {
        val animator = ValueAnimator.ofFloat(touchScale, targetScale)
        animator.duration = 160
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            touchScale = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
        animator.start()
    }
}
