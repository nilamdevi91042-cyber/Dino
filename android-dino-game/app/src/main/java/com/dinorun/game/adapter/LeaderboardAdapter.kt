package com.dinorun.game.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dinorun.game.R
import com.dinorun.game.data.LeaderboardRow
import com.dinorun.game.databinding.ItemLeaderboardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.VH>() {

    private val items = mutableListOf<LeaderboardRow>()
    private val dateFmt = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

    fun submit(rows: List<LeaderboardRow>) {
        items.clear(); items.addAll(rows); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val rank = position + 1
        holder.b.tvRank.text = rank.toString()
        holder.b.tvName.text = row.playerName
        holder.b.tvScore.text = row.bestScore.toString()
        holder.b.tvSub.text = holder.b.root.context.getString(
            R.string.leaderboard_sub,
            row.games, row.highestLevel, dateFmt.format(Date(row.lastPlayed))
        )
        // Medal styling for top 3
        val medalColor = when (rank) {
            1 -> 0xFFFFD54F.toInt()
            2 -> 0xFFB0BEC5.toInt()
            3 -> 0xFFD7A86E.toInt()
            else -> 0
        }
        if (medalColor != 0) {
            holder.b.rankBadge.setCardBackgroundColor(medalColor)
            holder.b.tvRank.setTextColor(0xFF1B1B1B.toInt())
        } else {
            holder.b.rankBadge.setCardBackgroundColor(0xFF263159.toInt())
            holder.b.tvRank.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    class VH(val b: ItemLeaderboardBinding) : RecyclerView.ViewHolder(b.root)
}
