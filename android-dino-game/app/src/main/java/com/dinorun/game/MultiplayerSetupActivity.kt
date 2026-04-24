package com.dinorun.game

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dinorun.game.databinding.ActivityMultiplayerSetupBinding

class MultiplayerSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMultiplayerSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiplayerSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnStart.setOnClickListener {
            val p1 = binding.etPlayer1.text?.toString()?.trim().orEmpty()
            val p2 = binding.etPlayer2.text?.toString()?.trim().orEmpty()
            var ok = true
            if (p1.isEmpty()) { binding.tilPlayer1.error = getString(R.string.name_required); ok = false }
            else binding.tilPlayer1.error = null
            if (p2.isEmpty()) { binding.tilPlayer2.error = getString(R.string.name_required); ok = false }
            else binding.tilPlayer2.error = null
            if (p1.equals(p2, ignoreCase = true)) {
                binding.tilPlayer2.error = getString(R.string.distinct_names_required)
                ok = false
            }
            if (!ok) return@setOnClickListener
            startActivity(
                Intent(this, MultiplayerActivity::class.java)
                    .putExtra(MultiplayerActivity.EXTRA_PLAYER1, p1)
                    .putExtra(MultiplayerActivity.EXTRA_PLAYER2, p2)
            )
            finish()
        }
    }
}
