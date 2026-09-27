package com.outfitku.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.outfitku.app.data.Item
import com.outfitku.app.data.ItemStatus
import com.outfitku.app.data.Kategori
import com.outfitku.app.databinding.ItemClothesBinding

class ItemAdapter(
    private val onSelectionChanged: () -> Unit
) : ListAdapter<Item, ItemAdapter.VH>(DIFF) {

    val selected = mutableSetOf<Long>()

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemClothesBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemClothesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.b) {
            tvName.text = item.nama
            tvKategori.text = Kategori.valueOf(item.kategori).label
            root.setOnClickListener { toggle(item) }
            cbSelect.setOnClickListener { toggle(item) }
            cbSelect.setOnCheckedChangeListener(null)
            cbSelect.isChecked = item.id in selected

            val (label, color) = when (item.status) {
                ItemStatus.BERSIH.name -> "Bersih" to Color.parseColor("#2E7D32")
                ItemStatus.DIPAKAI.name -> "Dipakai" to Color.parseColor("#EF6C00")
                else -> "Kotor" to Color.parseColor("#C62828")
            }
            tvBadge.text = if (item.wearSinceWash >= 2) "⚠️ $label · ${item.wearSinceWash}×" else label
            tvBadge.setTextColor(color)
        }
    }

    private fun toggle(item: Item) {
        if (!selected.remove(item.id)) selected.add(item.id)
        notifyItemChanged(currentList.indexOf(item))
        onSelectionChanged()
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
            override fun areContentsTheSame(a: Item, b: Item) = a == b
        }
    }
}
