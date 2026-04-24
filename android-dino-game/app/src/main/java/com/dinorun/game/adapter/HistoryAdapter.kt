package com.dinorun.game.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dinorun.game.R
import com.dinorun.game.data.ScoreEntity
import com.dinorun.game.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<ScoreEntity>()
    private val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submit(rows: List<ScoreEntity>) {
        items.clear(); items.addAll(rows); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        val ctx = holder.b.root.context
        holder.b.tvPlayer.text = s.playerName
        holder.b.tvScore.text = s.score.toString()
        holder.b.tvDate.text = dateFmt.format(Date(s.playedAt))
        holder.b.tvTime.text = timeFmt.format(Date(s.playedAt))
        holder.b.tvLevel.text = ctx.getString(R.string.history_level, s.level)
        holder.b.tvDuration.text = ctx.getString(R.string.history_duration, formatDuration(s.durationMs))
        holder.b.tvObstacles.text = ctx.getString(R.string.history_obstacles, s.obstaclesAvoided)
        holder.b.tvMode.text = if (s.mode == "multi") ctx.getString(R.string.mode_multi)
        else ctx.getString(R.string.mode_single)
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    class VH(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)
}
