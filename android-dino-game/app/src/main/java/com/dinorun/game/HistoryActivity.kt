package com.dinorun.game

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dinorun.game.adapter.HistoryAdapter
import com.dinorun.game.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val adapter = HistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
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
            val rows = withContext(Dispatchers.IO) { dao.allHistory() }
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
