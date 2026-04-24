package com.dinorun.game.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.dinorun.game.util.Prefs

/**
 * Single-player surface view. Owns its own render thread.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    interface GameListener {
        fun onScoreChanged(score: Int, level: Int, hi: Int)
        fun onShieldChanged(active: Boolean)
        fun onLevelUp(newLevel: Int)
        fun onGameOver(finalScore: Int, level: Int, obstacles: Int)
    }

    interface SoundCallback {
        fun onJump()
        fun onPickup()
        fun onCrash()
        fun onLevelUp()
    }

    private val engine = GameEngine()
    private val renderer = GameRenderer()
    private var listener: GameListener? = null
    private var soundCallback: SoundCallback? = null
    private val prefs by lazy { Prefs(context) }
    private var hiScore: Int = prefs.allTimeHi
    private var lastScore = -1
    private var lastShield = false
    private var lastLevel = 1

    private var thread: GameThread? = null
    private var pressDownTime = 0L

    init {
        holder.addCallback(this)
        engine.setListener { event ->
            when (event) {
                GameEngine.Event.Jumped -> soundCallback?.onJump()
                GameEngine.Event.Crashed -> soundCallback?.onCrash()
                GameEngine.Event.Picked -> soundCallback?.onPickup()
                is GameEngine.Event.LevelUp -> {
                    soundCallback?.onLevelUp()
                    listener?.onLevelUp(event.level)
                }
            }
        }
    }

    fun setListener(l: GameListener?) { listener = l }
    fun setSoundCallback(s: SoundCallback?) { soundCallback = s }

    fun pauseGame() { thread?.running = false }
    fun resumeGame() { ensureThreadRunning() }

    fun restart() {
        engine.reset()
        lastScore = -1
        lastShield = false
        lastLevel = 1
        ensureThreadRunning()
    }

    private fun ensureThreadRunning() {
        if (holder.surface == null || !holder.surface.isValid) return
        if (thread?.running == true) return
        thread = GameThread().also { it.start() }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        engine.resize(width.toFloat(), height.toFloat())
        engine.reset()
        ensureThreadRunning()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine.resize(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.running = false
        thread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressDownTime = System.currentTimeMillis()
                if (!engine.alive) {
                    return true
                }
                engine.jump()
            }
            MotionEvent.ACTION_MOVE -> {
                if (engine.dino.onGround && System.currentTimeMillis() - pressDownTime > 220) {
                    engine.setDucking(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> engine.setDucking(false)
        }
        return true
    }

    private inner class GameThread : Thread("game-thread") {
        @Volatile var running = true
        override fun run() {
            var last = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                engine.update(dt)
                drawFrame()
                emitState()
                if (!engine.alive) {
                    running = false
                    val finalScore = engine.score
                    if (finalScore > hiScore) {
                        hiScore = finalScore
                        prefs.allTimeHi = hiScore
                    }
                    listener?.onGameOver(finalScore, engine.level, engine.obstaclesAvoided)
                    break
                }
                val sleepMs = 16 - ((System.nanoTime() - now) / 1_000_000)
                if (sleepMs > 0) try { sleep(sleepMs) } catch (_: InterruptedException) {}
            }
        }

        private fun drawFrame() {
            val c: Canvas = holder.lockCanvas() ?: return
            try {
                renderer.draw(c, engine)
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }

        private fun emitState() {
            if (engine.score != lastScore || engine.level != lastLevel) {
                lastScore = engine.score
                lastLevel = engine.level
                listener?.onScoreChanged(engine.score, engine.level, hiScore)
            }
            if (engine.shieldActive != lastShield) {
                lastShield = engine.shieldActive
                listener?.onShieldChanged(lastShield)
            }
        }
    }
}

/** Stateless renderer used by both single & multiplayer views. */
class GameRenderer {
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
    private val dinoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val obsPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmpRect = RectF()

    fun draw(c: Canvas, engine: GameEngine, viewport: RectF? = null) {
        val w = engine.width
        val h = engine.height
        val night = engine.isNight

        // Sky gradient
        val top = if (night) Color.parseColor("#0E1230") else Color.parseColor("#FFD89B")
        val bottom = if (night) Color.parseColor("#1B2150") else Color.parseColor("#FFF6E2")
        val skyPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, skyPaint)

        // Stars at night
        if (night) {
            starPaint.color = Color.WHITE
            for (i in 0 until 25) {
                val sx = ((i * 53) % w.toInt()).toFloat()
                val sy = ((i * 71) % (h * 0.5f).toInt()).toFloat()
                c.drawCircle(sx, sy, 1.6f, starPaint)
            }
        }

        // Clouds (or moons)
        cloudPaint.color = if (night) Color.parseColor("#5C6699") else Color.parseColor("#FFFFFF")
        for (cl in engine.clouds) {
            tmpRect.set(cl.x, cl.y, cl.x + cl.width, cl.y + cl.height)
            c.drawRoundRect(tmpRect, cl.height / 2f, cl.height / 2f, cloudPaint)
        }

        // Ground line + texture
        groundPaint.color = if (night) Color.parseColor("#7A86C4") else Color.parseColor("#5A4631")
        c.drawLine(0f, engine.groundY, w, engine.groundY, groundPaint)
        groundPaint.color = if (night) Color.parseColor("#3D4570") else Color.parseColor("#8C7155")
        var dx = (System.currentTimeMillis() / 6) % 40
        var x = -dx.toFloat()
        while (x < w) {
            c.drawLine(x, engine.groundY + 8f, x + 12f, engine.groundY + 8f, groundPaint)
            x += 40f
        }

        // Power-ups
        shieldPaint.color = Color.parseColor("#26C6DA")
        for (p in engine.powerUps) {
            shieldPaint.style = Paint.Style.FILL
            c.drawCircle(p.x + p.size / 2f, p.y - p.size / 2f, p.size / 2f, shieldPaint)
            shieldPaint.style = Paint.Style.STROKE
            shieldPaint.strokeWidth = 3f
            shieldPaint.color = Color.WHITE
            c.drawCircle(p.x + p.size / 2f, p.y - p.size / 2f, p.size / 2f - 4f, shieldPaint)
            shieldPaint.color = Color.parseColor("#26C6DA")
        }

        // Obstacles
        for (o in engine.obstacles) {
            when (o.type) {
                Obstacle.Type.CACTUS_SMALL,
                Obstacle.Type.CACTUS_LARGE,
                Obstacle.Type.CACTUS_TRIPLE -> drawCactus(c, o, night)
                Obstacle.Type.BIRD_LOW,
                Obstacle.Type.BIRD_HIGH -> drawBird(c, o, night)
            }
        }

        // Dino
        drawDino(c, engine, night)

        // Shield aura
        if (engine.shieldActive) {
            shieldPaint.style = Paint.Style.STROKE
            shieldPaint.strokeWidth = 4f
            shieldPaint.color = Color.parseColor("#26C6DA")
            val b = engine.dino.bounds()
            val cx = (b[0] + b[2]) / 2f
            val cy = (b[1] + b[3]) / 2f
            val r = ((b[2] - b[0]).coerceAtLeast(b[3] - b[1])) / 2f + 12f
            c.drawCircle(cx, cy, r, shieldPaint)
        }

        // Game-over overlay
        if (!engine.alive) {
            val gPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
            c.drawRect(0f, 0f, w, h, gPaint)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 56f
                textAlign = Paint.Align.CENTER
            }
            c.drawText("GAME OVER", w / 2f, h / 2f, tp)
        }
    }

    private fun drawDino(c: Canvas, engine: GameEngine, night: Boolean) {
        val d = engine.dino
        val b = d.bounds()
        val left = b[0]
        val top = b[1]
        val right = b[2]
        val bottom = b[3]
        val w = right - left
        val h = bottom - top

        val bodyColor = if (night) Color.parseColor("#F4D8B6") else Color.parseColor("#A0522D")
        val bellyColor = if (night) Color.parseColor("#FFF1DE") else Color.parseColor("#D8B48A")
        val accentColor = if (night) Color.parseColor("#5B3A20") else Color.parseColor("#3E2210")
        val noseColor = Color.parseColor("#1A1A1A")

        if (d.ducking) {
            drawDuckingDog(c, left, top, right, bottom, w, h, bodyColor, bellyColor, accentColor, noseColor)
        } else {
            drawRunningDog(c, d, left, top, right, bottom, w, h, bodyColor, bellyColor, accentColor, noseColor)
        }
    }

    /**
     * Draws a side-view dog: 4 legs (animated), oval body, round head, snout, ear, tail.
     */
    private fun drawRunningDog(
        c: Canvas, d: Dino,
        left: Float, top: Float, right: Float, bottom: Float, w: Float, h: Float,
        body: Int, belly: Int, accent: Int, nose: Int,
    ) {
        // Reserve top space for head & ear so the body fits in the lower portion.
        val bodyTop = top + h * 0.38f
        val bodyBottom = bottom - h * 0.18f
        val bodyLeft = left + w * 0.05f
        val bodyRight = right - w * 0.18f

        // Tail (wagging in sync with legs)
        val phase = kotlin.math.sin(d.legPhase.toDouble()).toFloat()
        dinoPaint.color = body
        dinoPaint.style = Paint.Style.FILL
        val tailBaseX = bodyLeft + 4f
        val tailBaseY = bodyTop + (bodyBottom - bodyTop) * 0.35f
        val tailTipX = left - 6f
        val tailTipY = tailBaseY - 14f + 8f * phase
        val tailPath = Path().apply {
            moveTo(tailBaseX, tailBaseY - 4f)
            quadTo(tailTipX - 6f, tailTipY + 6f, tailTipX, tailTipY)
            quadTo(tailTipX - 4f, tailTipY + 10f, tailBaseX, tailBaseY + 4f)
            close()
        }
        c.drawPath(tailPath, dinoPaint)

        // Body (oval)
        dinoPaint.color = body
        tmpRect.set(bodyLeft, bodyTop, bodyRight, bodyBottom)
        c.drawOval(tmpRect, dinoPaint)
        // Belly highlight
        dinoPaint.color = belly
        val bellyTop = bodyTop + (bodyBottom - bodyTop) * 0.55f
        tmpRect.set(bodyLeft + w * 0.10f, bellyTop, bodyRight - w * 0.05f, bodyBottom)
        c.drawOval(tmpRect, dinoPaint)

        // Head (circle near front-right of body)
        dinoPaint.color = body
        val headR = h * 0.30f
        val headCx = bodyRight + headR * 0.15f
        val headCy = bodyTop + headR * 0.05f
        c.drawCircle(headCx, headCy, headR, dinoPaint)

        // Snout (small rounded rect sticking out forward)
        val snoutW = headR * 0.95f
        val snoutH = headR * 0.55f
        val snoutLeft = headCx + headR * 0.45f
        val snoutTop = headCy + headR * 0.10f
        tmpRect.set(snoutLeft, snoutTop, snoutLeft + snoutW, snoutTop + snoutH)
        c.drawRoundRect(tmpRect, 6f, 6f, dinoPaint)

        // Nose
        dinoPaint.color = nose
        c.drawCircle(snoutLeft + snoutW - 3f, snoutTop + snoutH * 0.45f, 3.4f, dinoPaint)

        // Mouth line
        dinoPaint.color = accent
        dinoPaint.style = Paint.Style.STROKE
        dinoPaint.strokeWidth = 1.5f
        c.drawLine(snoutLeft + 4f, snoutTop + snoutH - 3f, snoutLeft + snoutW - 6f, snoutTop + snoutH - 3f, dinoPaint)
        dinoPaint.style = Paint.Style.FILL

        // Ear (floppy triangle)
        dinoPaint.color = accent
        val earPath = Path().apply {
            moveTo(headCx - headR * 0.55f, headCy - headR * 0.55f)
            lineTo(headCx + headR * 0.10f, headCy - headR * 1.15f)
            lineTo(headCx + headR * 0.20f, headCy - headR * 0.10f)
            close()
        }
        c.drawPath(earPath, dinoPaint)

        // Eye
        dinoPaint.color = Color.WHITE
        val eyeCx = headCx + headR * 0.30f
        val eyeCy = headCy - headR * 0.05f
        c.drawCircle(eyeCx, eyeCy, 3.5f, dinoPaint)
        dinoPaint.color = nose
        c.drawCircle(eyeCx + 0.8f, eyeCy, 2.0f, dinoPaint)

        // Collar
        dinoPaint.color = Color.parseColor("#E53935")
        val collarLeft = headCx - headR * 0.40f
        val collarRight = headCx + headR * 0.55f
        val collarTop = headCy + headR * 0.55f
        tmpRect.set(collarLeft, collarTop, collarRight, collarTop + 5f)
        c.drawRoundRect(tmpRect, 2f, 2f, dinoPaint)

        // Four animated legs (front pair vs back pair alternate)
        dinoPaint.color = accent
        val legW = 6f
        val legBaseY = bottom
        val legTopY = bodyBottom - 4f
        val frontPhase = phase
        val backPhase = -phase
        val backLegX1 = bodyLeft + w * 0.08f
        val backLegX2 = bodyLeft + w * 0.22f
        val frontLegX1 = bodyRight - w * 0.30f
        val frontLegX2 = bodyRight - w * 0.15f
        // Back legs
        drawLeg(c, backLegX1, legTopY, legBaseY + 4f * backPhase, legW)
        drawLeg(c, backLegX2, legTopY, legBaseY - 4f * backPhase, legW)
        // Front legs
        drawLeg(c, frontLegX1, legTopY, legBaseY + 4f * frontPhase, legW)
        drawLeg(c, frontLegX2, legTopY, legBaseY - 4f * frontPhase, legW)
    }

    private fun drawDuckingDog(
        c: Canvas,
        left: Float, top: Float, right: Float, bottom: Float, w: Float, h: Float,
        body: Int, belly: Int, accent: Int, nose: Int,
    ) {
        // Long, low body
        dinoPaint.color = body
        dinoPaint.style = Paint.Style.FILL
        val bodyTop = top + h * 0.20f
        tmpRect.set(left, bodyTop, right - w * 0.10f, bottom - 4f)
        c.drawOval(tmpRect, dinoPaint)
        // Belly
        dinoPaint.color = belly
        tmpRect.set(left + w * 0.10f, bodyTop + h * 0.40f, right - w * 0.18f, bottom - 4f)
        c.drawOval(tmpRect, dinoPaint)
        // Head & snout extended forward
        dinoPaint.color = body
        val headR = h * 0.45f
        val headCx = right - w * 0.18f
        val headCy = bodyTop + headR * 0.30f
        c.drawCircle(headCx, headCy, headR, dinoPaint)
        val snoutW = headR * 1.05f
        tmpRect.set(headCx + headR * 0.40f, headCy, headCx + headR * 0.40f + snoutW, headCy + headR * 0.55f)
        c.drawRoundRect(tmpRect, 6f, 6f, dinoPaint)
        // Nose
        dinoPaint.color = nose
        c.drawCircle(headCx + headR * 0.40f + snoutW - 3f, headCy + headR * 0.30f, 3.4f, dinoPaint)
        // Ear back
        dinoPaint.color = accent
        val earPath = Path().apply {
            moveTo(headCx - headR * 0.50f, headCy - headR * 0.30f)
            lineTo(headCx - headR * 0.10f, headCy - headR * 0.95f)
            lineTo(headCx + headR * 0.15f, headCy - headR * 0.20f)
            close()
        }
        c.drawPath(earPath, dinoPaint)
        // Eye
        dinoPaint.color = Color.WHITE
        c.drawCircle(headCx + headR * 0.30f, headCy + 2f, 3f, dinoPaint)
        dinoPaint.color = nose
        c.drawCircle(headCx + headR * 0.30f + 0.8f, headCy + 2f, 1.7f, dinoPaint)
        // Short stub legs
        dinoPaint.color = accent
        drawLeg(c, left + w * 0.15f, bottom - 6f, bottom, 6f)
        drawLeg(c, right - w * 0.40f, bottom - 6f, bottom, 6f)
    }

    private fun drawLeg(c: Canvas, x: Float, top: Float, bottom: Float, width: Float) {
        tmpRect.set(x, top, x + width, bottom)
        c.drawRoundRect(tmpRect, 2f, 2f, dinoPaint)
    }

    private fun drawCactus(c: Canvas, o: Obstacle, night: Boolean) {
        obsPaint.color = if (night) Color.parseColor("#3FAF6E") else Color.parseColor("#2E7D32")
        val left = o.x
        val top = o.y - o.height
        val right = o.x + o.width
        val bottom = o.y
        when (o.type) {
            Obstacle.Type.CACTUS_SMALL -> {
                tmpRect.set(left + 8f, top, right - 8f, bottom)
                c.drawRoundRect(tmpRect, 4f, 4f, obsPaint)
                tmpRect.set(left, top + 14f, left + 10f, top + 30f)
                c.drawRoundRect(tmpRect, 3f, 3f, obsPaint)
                tmpRect.set(right - 10f, top + 18f, right, top + 34f)
                c.drawRoundRect(tmpRect, 3f, 3f, obsPaint)
            }
            Obstacle.Type.CACTUS_LARGE -> {
                tmpRect.set(left + 12f, top, right - 12f, bottom)
                c.drawRoundRect(tmpRect, 5f, 5f, obsPaint)
                tmpRect.set(left, top + 20f, left + 14f, top + 42f)
                c.drawRoundRect(tmpRect, 4f, 4f, obsPaint)
                tmpRect.set(right - 14f, top + 26f, right, top + 48f)
                c.drawRoundRect(tmpRect, 4f, 4f, obsPaint)
            }
            Obstacle.Type.CACTUS_TRIPLE -> {
                val w3 = o.width / 3f
                tmpRect.set(left, top + 10f, left + w3 - 4f, bottom)
                c.drawRoundRect(tmpRect, 4f, 4f, obsPaint)
                tmpRect.set(left + w3, top, left + 2f * w3 - 4f, bottom)
                c.drawRoundRect(tmpRect, 4f, 4f, obsPaint)
                tmpRect.set(left + 2f * w3, top + 14f, right, bottom)
                c.drawRoundRect(tmpRect, 4f, 4f, obsPaint)
            }
            else -> {}
        }
    }

    private fun drawBird(c: Canvas, o: Obstacle, night: Boolean) {
        obsPaint.color = if (night) Color.parseColor("#B0BEC5") else Color.parseColor("#455A64")
        val cx = o.x + o.width / 2f
        val cy = o.y - o.height / 2f
        val flap = (System.currentTimeMillis() / 180) % 2 == 0L
        // body
        tmpRect.set(cx - 12f, cy - 6f, cx + 16f, cy + 8f)
        c.drawOval(tmpRect, obsPaint)
        // beak
        tmpRect.set(cx + 14f, cy - 2f, cx + 24f, cy + 4f)
        c.drawRect(tmpRect, obsPaint)
        // wings
        if (flap) {
            tmpRect.set(cx - 18f, cy - 18f, cx + 4f, cy - 6f)
        } else {
            tmpRect.set(cx - 18f, cy + 6f, cx + 4f, cy + 18f)
        }
        c.drawOval(tmpRect, obsPaint)
    }
}
