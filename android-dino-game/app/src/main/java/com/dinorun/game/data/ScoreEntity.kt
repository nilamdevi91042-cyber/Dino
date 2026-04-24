package com.dinorun.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerName: String,
    val score: Int,
    val level: Int,
    val obstaclesAvoided: Int,
    val durationMs: Long,
    val playedAt: Long,
    val mode: String,
)

data class LeaderboardRow(
    val playerName: String,
    val bestScore: Int,
    val games: Int,
    val highestLevel: Int,
    val lastPlayed: Long,
)
