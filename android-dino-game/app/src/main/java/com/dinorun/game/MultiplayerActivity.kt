package com.dinorun.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dinorun.game.data.ScoreEntity
import com.dinorun.game.databinding.ActivityMultiplayerBinding
import com.dinorun.game.databinding.DialogGameOverMpBinding
import com.dinorun.game.game.MultiplayerGameView
import com.dinorun.game.util.Prefs
import com.dinorun.game.util.SoundManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultiplayerActivity : AppCompatActivity(), MultiplayerGameView.Listener {

    private lateinit var binding: ActivityMultiplayerBinding
    private lateinit var sound: SoundManager
    private lateinit var prefs: Prefs
    private var p1Name = "Player 1"
    private var p2Name = "Player 2"
    private var startTime = 0L
    private var ended = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = Prefs(this)
        sound = SoundManager(this)
        p1Name = intent.getStringExtra(EXTRA_PLAYER1) ?: "Player 1"
        p2Name = intent.getStringExtra(EXTRA_PLAYER2) ?: "Player 2"
        binding.tvP1Name.text = p1Name
        binding.tvP2Name.text = p2Name

        binding.gameView.setPlayers(p1Name, p2Name)
        binding.gameView.setListener(this)
        binding.gameView.setSoundCallback(object : MultiplayerGameView.SoundCallback {
            override fun onJump() { if (prefs.barkEnabled && prefs.soundEnabled) sound.bark() }
            override fun onCrash() {
                if (prefs.soundEnabled) sound.crash()
                if (prefs.vibrationEnabled) sound.vibrate(220)
            }
        })

        startTime = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (!ended) binding.gameView.resumeGame()
    }

    override fun onPause() {
        super.onPause()
        binding.gameView.pauseGame()
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
    }

    override fun onScoreChanged(p1Score: Int, p2Score: Int, p1Alive: Boolean, p2Alive: Boolean) {
        runOnUiThread {
            binding.tvP1Score.text = p1Score.toString().padStart(5, '0')
            binding.tvP2Score.text = p2Score.toString().padStart(5, '0')
            binding.tvP1Status.visibility = if (p1Alive) View.GONE else View.VISIBLE
            binding.tvP2Status.visibility = if (p2Alive) View.GONE else View.VISIBLE
        }
    }

    override fun onMatchOver(p1Score: Int, p2Score: Int) {
        if (ended) return
        ended = true
        val durationMs = System.currentTimeMillis() - startTime
        runOnUiThread { saveAndShow(p1Score, p2Score, durationMs) }
    }

    private fun saveAndShow(p1Score: Int, p2Score: Int, durationMs: Long) {
        val dao = (application as DinoApp).database.scoreDao()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                dao.insert(ScoreEntity(playerName = p1Name, score = p1Score, level = 1,
                    obstaclesAvoided = 0, durationMs = durationMs, playedAt = now, mode = "multi"))
                dao.insert(ScoreEntity(playerName = p2Name, score = p2Score, level = 1,
                    obstaclesAvoided = 0, durationMs = durationMs, playedAt = now, mode = "multi"))
            }
            showResult(p1Score, p2Score)
        }
    }

    private fun showResult(p1Score: Int, p2Score: Int) {
        val dialogBinding = DialogGameOverMpBinding.inflate(layoutInflater)
        dialogBinding.tvP1.text = "$p1Name: $p1Score"
        dialogBinding.tvP2.text = "$p2Name: $p2Score"
        val winnerText = when {
            p1Score > p2Score -> getString(R.string.winner_is, p1Name)
            p2Score > p1Score -> getString(R.string.winner_is, p2Name)
            else -> getString(R.string.tie_game)
        }
        dialogBinding.tvWinner.text = winnerText

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnReplay.setOnClickListener {
            dialog.dismiss()
            startTime = System.currentTimeMillis()
            ended = false
            binding.gameView.restart()
        }
        dialogBinding.btnHome.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
        dialog.show()
    }

    companion object {
        const val EXTRA_PLAYER1 = "p1"
        const val EXTRA_PLAYER2 = "p2"
    }
}
