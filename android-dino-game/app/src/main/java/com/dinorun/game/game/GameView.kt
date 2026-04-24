package com.dinorun.game.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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
        dinoPaint.color = if (night) Color.parseColor("#E0E5FF") else Color.parseColor("#37474F")
        val b = d.bounds()
        tmpRect.set(b[0], b[1], b[2], b[3])
        c.drawRoundRect(tmpRect, 8f, 8f, dinoPaint)
        // Eye
        dinoPaint.color = if (night) Color.parseColor("#0E1230") else Color.WHITE
        val eyeR = 3.5f
        val eyeX = b[2] - 12f
        val eyeY = b[1] + (if (d.ducking) 14f else 16f)
        c.drawCircle(eyeX, eyeY, eyeR, dinoPaint)
        // Legs animation
        if (d.onGround && !d.ducking) {
            dinoPaint.color = if (night) Color.parseColor("#E0E5FF") else Color.parseColor("#37474F")
            val phase = kotlin.math.sin(d.legPhase.toDouble()).toFloat()
            val legBase = b[3]
            val legTop = legBase - 8f
            val leftX = b[0] + 8f
            val rightX = b[2] - 14f
            tmpRect.set(leftX, legTop, leftX + 6f, legBase + 4f * phase)
            c.drawRect(tmpRect, dinoPaint)
            tmpRect.set(rightX, legTop, rightX + 6f, legBase - 4f * phase)
            c.drawRect(tmpRect, dinoPaint)
        }
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
