package com.dinorun.game

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dinorun.game.data.ScoreEntity
import com.dinorun.game.databinding.ActivityGameBinding
import com.dinorun.game.databinding.DialogGameOverBinding
import com.dinorun.game.game.GameView
import com.dinorun.game.util.Prefs
import com.dinorun.game.util.SoundManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameActivity : AppCompatActivity(), GameView.GameListener {

    private lateinit var binding: ActivityGameBinding
    private lateinit var sound: SoundManager
    private lateinit var prefs: Prefs
    private var playerName: String = "Runner"
    private var paused = false
    private var startTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = Prefs(this)
        sound = SoundManager(this)
        playerName = intent.getStringExtra(EXTRA_PLAYER) ?: prefs.lastPlayer.ifBlank { "Runner" }
        binding.tvPlayer.text = playerName

        binding.gameView.setListener(this)
        binding.gameView.setSoundCallback(object : GameView.SoundCallback {
            override fun onJump() { if (prefs.barkEnabled && prefs.soundEnabled) sound.bark() }
            override fun onPickup() { if (prefs.soundEnabled) sound.pickup() }
            override fun onCrash() {
                if (prefs.soundEnabled) sound.crash()
                if (prefs.vibrationEnabled) sound.vibrate(220)
            }
            override fun onLevelUp() { if (prefs.soundEnabled) sound.levelUp() }
        })

        binding.btnPause.setOnClickListener { togglePause() }
        startTime = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (!paused) binding.gameView.resumeGame()
    }

    override fun onPause() {
        super.onPause()
        binding.gameView.pauseGame()
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
    }

    private fun togglePause() {
        paused = !paused
        if (paused) {
            binding.gameView.pauseGame()
            showPauseDialog()
        } else {
            binding.gameView.resumeGame()
        }
    }

    private fun showPauseDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.paused)
            .setMessage(R.string.paused_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.resume) { d, _ ->
                paused = false
                binding.gameView.resumeGame()
                d.dismiss()
            }
            .setNegativeButton(R.string.quit) { _, _ -> finish() }
            .show()
    }

    override fun onScoreChanged(score: Int, level: Int, hi: Int) {
        runOnUiThread {
            binding.tvScore.text = score.toString().padStart(5, '0')
            binding.tvLevel.text = getString(R.string.level_label, level)
            binding.tvHi.text = getString(R.string.hi_label, hi)
        }
    }

    override fun onShieldChanged(active: Boolean) {
        runOnUiThread { binding.tvShield.visibility = if (active) View.VISIBLE else View.GONE }
    }

    override fun onLevelUp(newLevel: Int) {
        runOnUiThread {
            binding.tvLevelUp.text = getString(R.string.level_up, newLevel)
            binding.tvLevelUp.alpha = 0f
            binding.tvLevelUp.visibility = View.VISIBLE
            binding.tvLevelUp.animate().alpha(1f).setDuration(200).withEndAction {
                binding.tvLevelUp.animate().setStartDelay(900).alpha(0f).setDuration(400)
                    .withEndAction { binding.tvLevelUp.visibility = View.GONE }.start()
            }.start()
        }
    }

    override fun onGameOver(finalScore: Int, level: Int, obstacles: Int) {
        val durationMs = System.currentTimeMillis() - startTime
        runOnUiThread { saveAndShowGameOver(finalScore, level, obstacles, durationMs) }
    }

    private fun saveAndShowGameOver(score: Int, level: Int, obstacles: Int, durationMs: Long) {
        val dao = (application as DinoApp).database.scoreDao()
        lifecycleScope.launch {
            val playerBest = withContext(Dispatchers.IO) {
                dao.insert(
                    ScoreEntity(
                        playerName = playerName,
                        score = score,
                        level = level,
                        obstaclesAvoided = obstacles,
                        durationMs = durationMs,
                        playedAt = System.currentTimeMillis(),
                        mode = "single"
                    )
                )
                dao.bestForPlayer(playerName) ?: score
            }
            showGameOverDialog(score, level, obstacles, durationMs, playerBest)
        }
    }

    private fun showGameOverDialog(score: Int, level: Int, obstacles: Int, durationMs: Long, best: Int) {
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        dialogBinding.tvFinalScore.text = score.toString()
        dialogBinding.tvBest.text = getString(R.string.your_best, best)
        dialogBinding.tvLevelReached.text = getString(R.string.level_reached, level)
        dialogBinding.tvObstacles.text = getString(R.string.obstacles_avoided, obstacles)
        dialogBinding.tvDuration.text = getString(R.string.duration_label, formatDuration(durationMs))
        if (score >= best) {
            dialogBinding.tvNewBest.visibility = View.VISIBLE
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnReplay.setOnClickListener {
            dialog.dismiss()
            startTime = System.currentTimeMillis()
            binding.gameView.restart()
        }
        dialogBinding.btnLeaderboard.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, LeaderboardActivity::class.java))
            finish()
        }
        dialogBinding.btnHome.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = binding.gameView.onTouchEvent(event)

    companion object {
        const val EXTRA_PLAYER = "player_name"
    }
}
