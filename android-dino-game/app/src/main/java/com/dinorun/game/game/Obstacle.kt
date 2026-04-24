package com.dinorun.game.game

class Obstacle private constructor(
    val type: Type,
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
) {
    enum class Type { CACTUS_SMALL, CACTUS_LARGE, CACTUS_TRIPLE, BIRD_LOW, BIRD_HIGH }

    /** Returns [left, top, right, bottom]. */
    fun bounds(): FloatArray = floatArrayOf(x, y - height, x + width, y)

    companion object {
        fun create(type: Type, spawnX: Float, groundY: Float): Obstacle {
            return when (type) {
                Type.CACTUS_SMALL -> Obstacle(type, spawnX, groundY, 28f, 52f)
                Type.CACTUS_LARGE -> Obstacle(type, spawnX, groundY, 38f, 72f)
                Type.CACTUS_TRIPLE -> Obstacle(type, spawnX, groundY, 78f, 60f)
                Type.BIRD_LOW -> Obstacle(type, spawnX, groundY - 28f, 60f, 36f)
                Type.BIRD_HIGH -> Obstacle(type, spawnX, groundY - 130f, 60f, 36f)
            }
        }
    }
}
