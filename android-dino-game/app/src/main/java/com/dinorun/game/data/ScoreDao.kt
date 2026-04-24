package com.dinorun.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(score: ScoreEntity): Long

    @Query("SELECT * FROM scores ORDER BY playedAt DESC")
    suspend fun allHistory(): List<ScoreEntity>

    @Query("""
        SELECT playerName,
               MAX(score) AS bestScore,
               COUNT(*) AS games,
               MAX(level) AS highestLevel,
               MAX(playedAt) AS lastPlayed
        FROM scores
        GROUP BY playerName
        ORDER BY bestScore DESC
        LIMIT :limit
    """)
    suspend fun leaderboard(limit: Int): List<LeaderboardRow>

    @Query("SELECT MAX(score) FROM scores")
    suspend fun allTimeBest(): Int?

    @Query("SELECT MAX(score) FROM scores WHERE playerName = :name COLLATE NOCASE")
    suspend fun bestForPlayer(name: String): Int?

    @Query("SELECT COUNT(*) FROM scores")
    suspend fun totalGames(): Int

    @Query("SELECT COUNT(DISTINCT playerName) FROM scores")
    suspend fun totalPlayers(): Int

    @Query("SELECT DISTINCT playerName FROM scores ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recentPlayers(limit: Int): List<String>

    @Query("DELETE FROM scores")
    suspend fun deleteAll()
}
