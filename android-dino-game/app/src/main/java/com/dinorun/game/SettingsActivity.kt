package com.dinorun.game

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.dinorun.game.databinding.ActivitySettingsBinding
import com.dinorun.game.util.Prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        prefs = Prefs(this)

        binding.switchDark.isChecked = prefs.darkMode
        binding.switchSound.isChecked = prefs.soundEnabled
        binding.switchVibration.isChecked = prefs.vibrationEnabled
        binding.switchBark.isChecked = prefs.barkEnabled

        binding.switchDark.setOnCheckedChangeListener { _, on ->
            prefs.darkMode = on
            AppCompatDelegate.setDefaultNightMode(
                if (on) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }
        binding.switchSound.setOnCheckedChangeListener { _, on -> prefs.soundEnabled = on }
        binding.switchVibration.setOnCheckedChangeListener { _, on -> prefs.vibrationEnabled = on }
        binding.switchBark.setOnCheckedChangeListener { _, on -> prefs.barkEnabled = on }

        binding.btnClearScores.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_warning)
                .setPositiveButton(R.string.delete_all) { _, _ ->
                    val dao = (application as DinoApp).database.scoreDao()
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { dao.deleteAll() }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
