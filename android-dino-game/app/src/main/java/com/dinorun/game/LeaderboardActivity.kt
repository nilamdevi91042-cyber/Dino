package com.dinorun.game

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dinorun.game.adapter.LeaderboardAdapter
import com.dinorun.game.databinding.ActivityLeaderboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private val adapter = LeaderboardAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        loadData()
    }

    private fun loadData() {
        val dao = (application as DinoApp).database.scoreDao()
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) { dao.leaderboard(50) }
            if (rows.isEmpty()) {
                binding.empty.visibility = android.view.View.VISIBLE
                binding.recycler.visibility = android.view.View.GONE
            } else {
                binding.empty.visibility = android.view.View.GONE
                binding.recycler.visibility = android.view.View.VISIBLE
                adapter.submit(rows)
            }
        }
    }
}
