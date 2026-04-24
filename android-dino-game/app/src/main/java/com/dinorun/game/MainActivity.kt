package com.dinorun.game

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dinorun.game.databinding.ActivityMainBinding
import com.dinorun.game.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.cardPlay.setOnClickListener {
            startActivity(Intent(this, PlayerEntryActivity::class.java).apply {
                putExtra(PlayerEntryActivity.EXTRA_MODE, PlayerEntryActivity.MODE_SINGLE)
            })
        }
        binding.cardMultiplayer.setOnClickListener {
            startActivity(Intent(this, MultiplayerSetupActivity::class.java))
        }
        binding.cardLeaderboard.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }
        binding.cardHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
        binding.tvWelcome.text = getString(R.string.welcome_back, prefs.lastPlayer.ifBlank { "Runner" })
    }

    private fun animateEntrance() {
        val cards = listOf(
            binding.cardPlay, binding.cardMultiplayer,
            binding.cardLeaderboard, binding.cardHistory, binding.cardSettings
        )
        val anim = AnimationUtils.loadAnimation(this, R.anim.card_pop_in)
        cards.forEachIndexed { i, c ->
            c.alpha = 0f
            c.postDelayed({
                c.alpha = 1f
                c.startAnimation(anim)
            }, 80L * i)
        }
    }

    private fun loadStats() {
        val dao = (application as DinoApp).database.scoreDao()
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                Triple(dao.totalGames(), dao.allTimeBest() ?: 0, dao.totalPlayers())
            }
            binding.tvStatGames.text = stats.first.toString()
            binding.tvStatBest.text = stats.second.toString()
            binding.tvStatPlayers.text = stats.third.toString()
        }
    }
}
