package com.dinorun.game.game

class PowerUp(var x: Float, var y: Float, val size: Float, val kind: Kind) {
    enum class Kind { SHIELD }
    fun bounds(): FloatArray = floatArrayOf(x, y - size, x + size, y)
    companion object {
        fun shield(spawnX: Float, y: Float): PowerUp = PowerUp(spawnX, y, 40f, Kind.SHIELD)
    }
}
