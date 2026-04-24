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
 * Two independent [GameEngine] instances share the same surface. Each finger is
 * **claimed** by whichever side it first touched, and only that side is affected by
 * its lifetime — even if it later drifts across the divider. This guarantees
 * Player 1 and Player 2 can tap simultaneously and one player's tap NEVER affects
 * the other player's dino.
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

    /**
     * Maps active pointerId -> the side it belongs to (1 = left/P1, 2 = right/P2).
     * Recorded on DOWN, removed on UP, used to keep each finger isolated to its side.
     */
    private val pointerSide = HashMap<Int, Int>(8)

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
        // Make sure this view actually receives touches even though it sits beneath HUD chips.
        isClickable = true
        isFocusable = true
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
        pointerSide.clear()
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
     * Per-pointer touch routing. Each finger is assigned a side on DOWN and stays
     * locked to that side until UP — so simultaneous taps register independently and
     * one player can never trigger the other player's dino, even if their finger
     * drifts across the divider.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0) return true
        val half = width / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val x = event.getX(idx)
                val side = if (x < half) 1 else 2
                pointerSide[pid] = side
                if (side == 1) {
                    if (engineP1.alive) engineP1.jump()
                } else {
                    if (engineP2.alive) engineP2.jump()
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                pointerSide.remove(pid)
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerSide.clear()
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
