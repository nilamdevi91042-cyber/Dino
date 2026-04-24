package com.dinorun.game.game

import kotlin.random.Random

class Cloud(var x: Float, var y: Float, val width: Float, val height: Float) {
    companion object {
        fun create(spawnX: Float, y: Float): Cloud {
            val r = Random.Default
            val w = 80f + r.nextFloat() * 50f
            val h = 24f + r.nextFloat() * 14f
            return Cloud(spawnX, y, w, h)
        }
    }
}
