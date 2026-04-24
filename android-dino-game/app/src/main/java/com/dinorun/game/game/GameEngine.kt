package com.dinorun.game.game

import kotlin.math.max
import kotlin.random.Random

/**
 * Pure-logic game engine — no Android dependencies, easy to reuse for both
 * single-player and multiplayer (one engine per dino).
 */
class GameEngine(var width: Float = 0f, var height: Float = 0f) {

    var groundY: Float = 0f
    val dino = Dino()
    val obstacles: MutableList<Obstacle> = mutableListOf()
    val clouds: MutableList<Cloud> = mutableListOf()
    val powerUps: MutableList<PowerUp> = mutableListOf()

    var score: Int = 0
        private set
    var level: Int = 1
        private set
    var alive: Boolean = true
        private set
    var obstaclesAvoided: Int = 0
        private set
    var shieldActive: Boolean = false
        private set
    var isNight: Boolean = false
        private set

    private var distance: Float = 0f
    private var spawnCooldown: Float = 0f
    private var cloudCooldown: Float = 0f
    private var powerUpCooldown: Float = 5f
    private var nextLevelThreshold = 350
    private var rnd = Random.Default
    private var listener: EngineListener? = null

    fun interface EngineListener {
        fun onEvent(e: Event)
    }

    sealed class Event {
        object Jumped : Event()
        object Crashed : Event()
        object Picked : Event()
        data class LevelUp(val level: Int) : Event()
    }

    fun setListener(l: EngineListener?) { listener = l }

    fun resize(w: Float, h: Float) {
        width = w
        height = h
        groundY = h * 0.82f
        dino.resetTo(w * 0.12f, groundY)
    }

    fun reset() {
        obstacles.clear()
        clouds.clear()
        powerUps.clear()
        score = 0
        level = 1
        alive = true
        obstaclesAvoided = 0
        shieldActive = false
        isNight = false
        distance = 0f
        spawnCooldown = 1.2f
        cloudCooldown = 0f
        powerUpCooldown = 8f
        nextLevelThreshold = 350
        dino.reset(groundY)
    }

    fun jump() {
        if (!alive) return
        if (dino.jump()) listener?.onEvent(Event.Jumped)
    }

    fun setDucking(d: Boolean) {
        if (!alive) return
        dino.ducking = d && dino.onGround
    }

    fun update(dt: Float) {
        if (!alive) return
        // Faster base, sharper acceleration per level so each level is noticeably quicker.
        val speed = 440f + (level - 1) * 110f
        distance += speed * dt
        score = (distance / 8f).toInt()

        // Level progression — promotes more often early so players see the speed-up sooner.
        if (score >= nextLevelThreshold) {
            level++
            nextLevelThreshold += 350 + level * 200
            isNight = level >= 4 && (level % 2 == 0)
            listener?.onEvent(Event.LevelUp(level))
        }

        dino.update(dt, groundY)

        // Spawn obstacles — denser and even denser at higher levels.
        spawnCooldown -= dt
        if (spawnCooldown <= 0f) {
            val type = pickObstacleType()
            obstacles.add(Obstacle.create(type, width, groundY))
            // Tighter gaps so obstacles come at varied, closer distances.
            val minGap = max(0.38f, 0.85f - level * 0.07f)
            val maxGap = max(0.70f, 1.45f - level * 0.10f)
            spawnCooldown = rnd.nextFloat() * (maxGap - minGap) + minGap
        }

        // Spawn clouds
        cloudCooldown -= dt
        if (cloudCooldown <= 0f) {
            clouds.add(Cloud.create(width, height * 0.18f + rnd.nextFloat() * height * 0.25f))
            cloudCooldown = 1.2f + rnd.nextFloat() * 1.6f
        }

        // Spawn power-ups (shield) occasionally
        powerUpCooldown -= dt
        if (powerUpCooldown <= 0f && level >= 2) {
            powerUps.add(PowerUp.shield(width, groundY - rnd.nextFloat() * 200f - 80f))
            powerUpCooldown = 12f + rnd.nextFloat() * 10f
        }

        // Move and clean obstacles
        val itO = obstacles.iterator()
        while (itO.hasNext()) {
            val o = itO.next()
            o.x -= speed * dt
            if (o.x + o.width < 0f) {
                itO.remove()
                obstaclesAvoided++
            }
        }
        clouds.forEach { it.x -= (speed * 0.35f) * dt }
        clouds.removeAll { it.x + it.width < 0f }

        val itP = powerUps.iterator()
        while (itP.hasNext()) {
            val p = itP.next()
            p.x -= speed * dt
            if (p.x + p.size < 0f) itP.remove()
            else if (rectsIntersect(dino.bounds(), p.bounds())) {
                shieldActive = true
                listener?.onEvent(Event.Picked)
                itP.remove()
            }
        }

        // Collision
        for (o in obstacles) {
            if (rectsIntersect(dino.bounds(), o.bounds())) {
                if (shieldActive) {
                    shieldActive = false
                    o.x = -9999f // consume
                } else {
                    alive = false
                    listener?.onEvent(Event.Crashed)
                    return
                }
            }
        }
    }

    private fun pickObstacleType(): Obstacle.Type {
        val r = rnd.nextFloat()
        return when {
            level >= 3 && r < 0.20f -> Obstacle.Type.BIRD_HIGH
            level >= 3 && r < 0.32f -> Obstacle.Type.BIRD_LOW
            r < 0.45f -> Obstacle.Type.CACTUS_SMALL
            r < 0.78f -> Obstacle.Type.CACTUS_LARGE
            else -> Obstacle.Type.CACTUS_TRIPLE
        }
    }

    private fun rectsIntersect(a: FloatArray, b: FloatArray): Boolean {
        val pad = 6f
        return a[0] + pad < b[2] && a[2] - pad > b[0] && a[1] + pad < b[3] && a[3] - pad > b[1]
    }
}
