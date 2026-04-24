package com.dinorun.game.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Landscape split-screen 2-player view.
 *
 * Two independent [GameEngine] instances share the same surface. Touch dispatch is
 * routed by the X coordinate of each pointer:
 *   - Pointer X < width/2  → Player 1 jump
 *   - Pointer X >= width/2 → Player 2 jump
 *
 * Each pointer is tracked individually via ACTION_POINTER_DOWN / ACTION_POINTER_UP so
 * a touch on one half NEVER triggers the other player. Both players can tap simultaneously
 * with no interference.
 */
class MultiplayerGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    interface Listener {
        fun onScoreChanged(p1Score: Int, p2Score: Int, p1Alive: Boolean, p2Alive: Boolean)
        fun onMatchOver(p1Score: Int, p2Score: Int)
    }

    interface SoundCallback {
        fun onJump()
        fun onCrash()
    }

    private val engineP1 = GameEngine()
    private val engineP2 = GameEngine()
    private val renderer = GameRenderer()
    private var listener: Listener? = null
    private var soundCallback: SoundCallback? = null
    private var p1Name = "P1"
    private var p2Name = "P2"

    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        strokeWidth = 4f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
    }

    private var thread: MpThread? = null
    private var lastP1Score = -1
    private var lastP2Score = -1
    private var lastP1Alive = true
    private var lastP2Alive = true

    init {
        holder.addCallback(this)
        val l: GameEngine.EngineListener = GameEngine.EngineListener { e ->
            when (e) {
                GameEngine.Event.Jumped -> soundCallback?.onJump()
                GameEngine.Event.Crashed -> soundCallback?.onCrash()
                else -> {}
            }
        }
        engineP1.setListener(l)
        engineP2.setListener(l)
    }

    fun setListener(l: Listener?) { listener = l }
    fun setSoundCallback(s: SoundCallback?) { soundCallback = s }
    fun setPlayers(p1: String, p2: String) { p1Name = p1; p2Name = p2 }

    fun pauseGame() { thread?.running = false }
    fun resumeGame() { ensureThreadRunning() }

    fun restart() {
        engineP1.reset()
        engineP2.reset()
        lastP1Score = -1; lastP2Score = -1
        lastP1Alive = true; lastP2Alive = true
        ensureThreadRunning()
    }

    private fun ensureThreadRunning() {
        if (holder.surface == null || !holder.surface.isValid) return
        if (thread?.running == true) return
        thread = MpThread().also { it.start() }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        resizeEngines()
        engineP1.reset()
        engineP2.reset()
        ensureThreadRunning()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        resizeEngines()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.running = false
        thread = null
    }

    private fun resizeEngines() {
        val halfW = width.toFloat() / 2f
        val h = height.toFloat()
        engineP1.resize(halfW, h)
        engineP2.resize(halfW, h)
    }

    /**
     * Touch routing — every pointer is mapped to its half independently. Pointers in
     * the left half only trigger Player 1; pointers in the right half only trigger
     * Player 2. Simultaneous taps work because we look at the specific pointer index
     * for ACTION_POINTER_DOWN events.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx)
                if (x < width / 2f) {
                    if (engineP1.alive) engineP1.jump()
                } else {
                    if (engineP2.alive) engineP2.jump()
                }
            }
        }
        return true
    }

    private inner class MpThread : Thread("mp-game-thread") {
        @Volatile var running = true
        override fun run() {
            var last = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                if (engineP1.alive) engineP1.update(dt)
                if (engineP2.alive) engineP2.update(dt)
                drawFrame()
                emitState()
                if (!engineP1.alive && !engineP2.alive) {
                    running = false
                    listener?.onMatchOver(engineP1.score, engineP2.score)
                    break
                }
                val sleepMs = 16 - ((System.nanoTime() - now) / 1_000_000)
                if (sleepMs > 0) try { sleep(sleepMs) } catch (_: InterruptedException) {}
            }
        }

        private fun drawFrame() {
            val c: Canvas = holder.lockCanvas() ?: return
            try {
                val halfW = width / 2f
                // Left half — clip then draw P1
                c.save()
                c.clipRect(0f, 0f, halfW, height.toFloat())
                renderer.draw(c, engineP1)
                c.restore()
                // Right half — translate so engineP2 (which thinks width=halfW) renders correctly
                c.save()
                c.translate(halfW, 0f)
                c.clipRect(0f, 0f, halfW, height.toFloat())
                renderer.draw(c, engineP2)
                c.restore()
                // Divider
                c.drawLine(halfW, 0f, halfW, height.toFloat(), divider)
                // Player labels (top of each half)
                c.drawText(p1Name, halfW / 2f, 44f, labelPaint)
                c.drawText(p2Name, halfW + halfW / 2f, 44f, labelPaint)
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }

        private fun emitState() {
            if (engineP1.score != lastP1Score || engineP2.score != lastP2Score
                || engineP1.alive != lastP1Alive || engineP2.alive != lastP2Alive) {
                lastP1Score = engineP1.score
                lastP2Score = engineP2.score
                lastP1Alive = engineP1.alive
                lastP2Alive = engineP2.alive
                listener?.onScoreChanged(lastP1Score, lastP2Score, lastP1Alive, lastP2Alive)
            }
        }
    }
}
