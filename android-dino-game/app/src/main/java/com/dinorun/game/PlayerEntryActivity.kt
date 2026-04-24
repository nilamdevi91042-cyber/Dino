package com.dinorun.game

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dinorun.game.databinding.ActivityPlayerEntryBinding
import com.dinorun.game.util.Prefs
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerEntryBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SINGLE
        val isMultiplayer = mode != MODE_SINGLE
        binding.tvTitle.setText(
            when (mode) {
                MODE_SINGLE -> R.string.enter_your_name
                MODE_MULTIPLAYER_P1 -> R.string.enter_player_1
                MODE_MULTIPLAYER_P2 -> R.string.enter_player_2
                else -> R.string.enter_your_name
            }
        )
        binding.etName.setText(if (mode == MODE_SINGLE) prefs.lastPlayer else "")

        loadRecentPlayers()

        binding.btnStart.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                binding.tilName.error = getString(R.string.name_required)
                return@setOnClickListener
            }
            binding.tilName.error = null

            when (mode) {
                MODE_SINGLE -> {
                    prefs.lastPlayer = name
                    startActivity(Intent(this, GameActivity::class.java).apply {
                        putExtra(GameActivity.EXTRA_PLAYER, name)
                    })
                    finish()
                }
                MODE_MULTIPLAYER_P1 -> {
                    val intent = Intent(this, PlayerEntryActivity::class.java)
                    intent.putExtra(EXTRA_MODE, MODE_MULTIPLAYER_P2)
                    intent.putExtra(EXTRA_PLAYER1, name)
                    startActivity(intent)
                    finish()
                }
                MODE_MULTIPLAYER_P2 -> {
                    val p1 = intent.getStringExtra(EXTRA_PLAYER1) ?: "Player 1"
                    val multiIntent = Intent(this, MultiplayerActivity::class.java)
                    multiIntent.putExtra(MultiplayerActivity.EXTRA_PLAYER1, p1)
                    multiIntent.putExtra(MultiplayerActivity.EXTRA_PLAYER2, name)
                    startActivity(multiIntent)
                    finish()
                }
            }
        }
    }

    private fun loadRecentPlayers() {
        val dao = (application as DinoApp).database.scoreDao()
        lifecycleScope.launch {
            val recent = withContext(Dispatchers.IO) { dao.recentPlayers(8) }
            binding.chipGroupRecent.removeAllViews()
            if (recent.isEmpty()) {
                binding.tvRecentLabel.visibility = android.view.View.GONE
                return@launch
            }
            binding.tvRecentLabel.visibility = android.view.View.VISIBLE
            recent.forEach { name ->
                val chip = Chip(this@PlayerEntryActivity).apply {
                    text = name
                    isClickable = true
                    isCheckable = false
                    setOnClickListener { binding.etName.setText(name) }
                }
                binding.chipGroupRecent.addView(chip)
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PLAYER1 = "player1"
        const val MODE_SINGLE = "single"
        const val MODE_MULTIPLAYER_P1 = "mp_p1"
        const val MODE_MULTIPLAYER_P2 = "mp_p2"
    }
}
