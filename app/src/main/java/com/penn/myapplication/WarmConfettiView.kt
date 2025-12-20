package com.penn.myapplication

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class WarmConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    
    // 暖色调配色方案
    private val warmColors = listOf(
        Color.parseColor("#FF7043"), // Deep Orange
        Color.parseColor("#FFAB91"), // Light Orange
        Color.parseColor("#FFD54F"), // Amber
        Color.parseColor("#F48FB1"), // Pink
        Color.parseColor("#FFCCBC")  // Very Light Orange
    )

    private inner class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var color: Int,
        var alpha: Int = 255,
        var size: Float,
        var rotation: Float = 0f,
        var rotationSpeed: Float
    ) {
        fun update() {
            x += vx
            y += vy
            // 重力
            vy += 0.8f
            // 空气阻力
            vx *= 0.95f
            vy *= 0.95f
            
            rotation += rotationSpeed
            if (alpha > 0) alpha -= 3 // 慢慢消失
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.update()
            
            if (p.alpha <= 0 || p.y > height) {
                iterator.remove()
                continue
            }

            paint.color = p.color
            paint.alpha = p.alpha
            
            canvas.save()
            canvas.rotate(p.rotation, p.x, p.y)
            // 绘制圆形粒子
            canvas.drawCircle(p.x, p.y, p.size, paint)
            canvas.restore()
        }

        if (particles.isNotEmpty()) {
            invalidate()
        }
    }

    fun burst(x: Float, y: Float) {
        // 生成 50 个粒子
        for (i in 0..50) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextDouble(10.0, 30.0).toFloat()
            
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat() - 15f, // 初始向上冲力
                    color = warmColors.random(),
                    size = Random.nextFloat() * 12f + 8f,
                    rotationSpeed = Random.nextFloat() * 10f - 5f
                )
            )
        }
        invalidate()
    }
}
