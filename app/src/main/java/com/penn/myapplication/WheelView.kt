package com.penn.myapplication

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class WheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var options = listOf<String>()
    private var pointerRotation = 0f
    private var animator: ValueAnimator? = null
    
    private val colors = listOf(
        0xFF6366F1.toInt(), // 紫色
        0xFFEC4899.toInt(), // 粉色
        0xFFF59E0B.toInt(), // 橙色
        0xFF10B981.toInt(), // 绿色
        0xFF3B82F6.toInt(), // 蓝色
        0xFFEF4444.toInt(), // 红色
        0xFF8B5CF6.toInt(), // 紫罗兰
        0xFF14B8A6.toInt()  // 青色
    )
    
    init {
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 40f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        
        pointerPaint.color = 0xFFEF4444.toInt()
        pointerPaint.style = Paint.Style.FILL
    }
    
    fun setOptions(newOptions: List<String>) {
        options = newOptions
        invalidate()
    }
    
    fun spin(onResult: (String) -> Unit) {
        if (options.isEmpty()) return

        animator?.cancel()

        val randomSpins = 8 + (Math.random() * 4).toInt()
        val randomDegree = (Math.random() * 360).toFloat()
        val targetRotation = pointerRotation + randomSpins * 360 + randomDegree

        animator = ValueAnimator.ofFloat(pointerRotation, targetRotation).apply {
            duration = 5000
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                pointerRotation = it.animatedValue as Float
                invalidate()
            }
            start()

            // 计算最终结果 - 指针指向下方，所以加180度
            doOnEnd {
                val normalizedRotation = (pointerRotation + 180) % 360
                val sectorAngle = 360f / options.size
                val selectedIndex = ((normalizedRotation / sectorAngle).toInt()) % options.size
                onResult(options[selectedIndex])
            }
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (options.isEmpty()) return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(centerX, centerY) - 80f

        val sectorAngle = 360f / options.size

        // 绘制转盘阴影
        shadowPaint.color = 0x30000000
        shadowPaint.maskFilter = android.graphics.BlurMaskFilter(15f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(centerX, centerY + 8, radius + 10, shadowPaint)

        // 绘制扇形（固定不动）
        options.forEachIndexed { index, option ->
            val startAngle = index * sectorAngle - 90

            paint.color = colors[index % colors.size]
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, sectorAngle, true, paint
            )

            // 绘制分割线
            paint.color = Color.WHITE
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            val lineAngle = Math.toRadians(startAngle.toDouble())
            canvas.drawLine(
                centerX, centerY,
                centerX + radius * cos(lineAngle).toFloat(),
                centerY + radius * sin(lineAngle).toFloat(),
                paint
            )
            paint.style = Paint.Style.FILL

            // 绘制文字
            val angle = Math.toRadians((startAngle + sectorAngle / 2).toDouble())
            val textRadius = radius * 0.65f
            val textX = centerX + textRadius * cos(angle).toFloat()
            val textY = centerY + textRadius * sin(angle).toFloat() + 15

            canvas.save()
            canvas.rotate(startAngle + sectorAngle / 2 + 90, textX, textY)
            canvas.drawText(option, textX, textY, textPaint)
            canvas.restore()
        }

        // 绘制中心圆
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 50f, paint)
        paint.color = 0xFFF3F4F6.toInt()
        canvas.drawCircle(centerX, centerY, 45f, paint)

        // 绘制旋转的指针
        canvas.save()
        canvas.rotate(pointerRotation, centerX, centerY)
        drawPointer(canvas, centerX, centerY, radius)
        canvas.restore()
    }
    
    private fun drawPointer(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        // 绘制指针 - 从中心向下指
        val pointerLength = radius * 0.75f

        // 指针主体 - 三角形箭头
        val path = Path().apply {
            moveTo(centerX, centerY + pointerLength)  // 箭头尖端
            lineTo(centerX - 25f, centerY + 35)       // 左边
            lineTo(centerX - 10f, centerY + 35)       // 左内
            lineTo(centerX - 10f, centerY)            // 左上
            lineTo(centerX + 10f, centerY)            // 右上
            lineTo(centerX + 10f, centerY + 35)       // 右内
            lineTo(centerX + 25f, centerY + 35)       // 右边
            close()
        }

        // 指针阴影
        pointerPaint.color = 0x50000000
        pointerPaint.style = Paint.Style.FILL
        canvas.save()
        canvas.translate(0f, 4f)
        canvas.drawPath(path, pointerPaint)
        canvas.restore()

        // 指针边框
        pointerPaint.color = 0xFFDC2626.toInt()
        pointerPaint.style = Paint.Style.FILL
        canvas.drawPath(path, pointerPaint)

        // 指针内部
        paint.color = 0xFFEF4444.toInt()
        paint.style = Paint.Style.FILL
        val innerPath = Path().apply {
            moveTo(centerX, centerY + pointerLength - 3)
            lineTo(centerX - 22f, centerY + 38)
            lineTo(centerX - 8f, centerY + 38)
            lineTo(centerX - 8f, centerY + 3)
            lineTo(centerX + 8f, centerY + 3)
            lineTo(centerX + 8f, centerY + 38)
            lineTo(centerX + 22f, centerY + 38)
            close()
        }
        canvas.drawPath(innerPath, paint)

        // 中心大圆 - 外圈
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 35f, paint)

        // 中心圆 - 红色
        paint.color = 0xFFEF4444.toInt()
        canvas.drawCircle(centerX, centerY, 30f, paint)

        // 中心圆 - 内圈白色
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 12f, paint)

        // 中心小圆 - 红点
        paint.color = 0xFFDC2626.toInt()
        canvas.drawCircle(centerX, centerY, 6f, paint)
    }
    
    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action()
            }
        })
    }
}

