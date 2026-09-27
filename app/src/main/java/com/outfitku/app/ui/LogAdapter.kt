package com.outfitku.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.outfitku.app.data.OutfitLog
import com.outfitku.app.data.toIdList
import com.outfitku.app.databinding.ItemLogBinding

class LogAdapter(
    private val names: (OutfitLog) -> String,
    private val onClick: (OutfitLog) -> Unit
) : ListAdapter<OutfitLog, LogAdapter.VH>(DIFF) {

    inner class VH(val b: ItemLogBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val log = getItem(position)
        with(holder.b) {
            tvLogTitle.text = names(log)
            tvLogSub.text = "${log.itemIds.toIdList().size} item" + (log.catatan?.let { " · $it" } ?: "")
            tvSumber.text = if (log.sumber == "AUTO") "⚡ AUTO" else "✍ MANUAL"
            root.setOnClickListener { onClick(log) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<OutfitLog>() {
            override fun areItemsTheSame(a: OutfitLog, b: OutfitLog) = a.id == b.id
            override fun areContentsTheSame(a: OutfitLog, b: OutfitLog) = a == b
        }
    }
}
