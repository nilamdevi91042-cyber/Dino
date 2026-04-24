package com.dinorun.game.game

class Dino {
    var x: Float = 0f
    var y: Float = 0f
    var vy: Float = 0f
    var ducking: Boolean = false
    val standWidth: Float = 64f
    val standHeight: Float = 72f
    val duckWidth: Float = 84f
    val duckHeight: Float = 48f
    var onGround: Boolean = true
    var legPhase: Float = 0f

    private val gravity = 2400f
    private val jumpV = -1050f

    fun resetTo(startX: Float, groundY: Float) {
        x = startX
        y = groundY
        vy = 0f
        ducking = false
        onGround = true
        legPhase = 0f
    }

    fun reset(groundY: Float) {
        y = groundY
        vy = 0f
        ducking = false
        onGround = true
        legPhase = 0f
    }

    fun jump(): Boolean {
        if (!onGround) return false
        vy = jumpV
        onGround = false
        return true
    }

    fun update(dt: Float, groundY: Float) {
        if (!onGround) {
            vy += gravity * dt
            y += vy * dt
            if (y >= groundY) {
                y = groundY
                vy = 0f
                onGround = true
            }
        } else {
            legPhase += dt * 14f
        }
    }

    fun width(): Float = if (ducking) duckWidth else standWidth
    fun height(): Float = if (ducking) duckHeight else standHeight

    /** Bounds returned as [left, top, right, bottom] aligned so feet sit on ground. */
    fun bounds(): FloatArray {
        val w = width()
        val h = height()
        val left = x
        val top = y - h
        return floatArrayOf(left, top, left + w, y)
    }
}
