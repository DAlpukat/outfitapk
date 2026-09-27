package com.outfitku.app.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.outfitku.app.R
import com.outfitku.app.data.ItemStatus
import com.outfitku.app.data.Kategori
import com.outfitku.app.data.Repo
import com.outfitku.app.databinding.DialogAddBinding
import com.outfitku.app.databinding.FragmentLemariBinding
import kotlinx.coroutines.launch

class LemariFragment : Fragment(R.layout.fragment_lemari) {

    private var b: FragmentLemariBinding? = null
    private val repo by lazy { Repo.get(requireContext()) }
    private lateinit var adapter: ItemAdapter
    private var allItems = emptyList<com.outfitku.app.data.Item>()
    private var filter = "ALL"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentLemariBinding.bind(view)
        b = binding

        adapter = ItemAdapter { updateBar() }
        binding.rvItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvItems.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeItems().collect { items ->
                allItems = items
                adapter.submitList(items.filter { filter == "ALL" || it.status == filter })
            }
        }

        binding.filterGroup.setOnCheckedChangeListener { _, checkedId ->
            filter = when (checkedId) {
                R.id.fBersih -> ItemStatus.BERSIH.name
                R.id.fDipakai -> ItemStatus.DIPAKAI.name
                R.id.fKotor -> ItemStatus.KOTOR.name
                else -> "ALL"
            }
            adapter.submitList(allItems.filter { filter == "ALL" || it.status == filter })
        }

        binding.btnAdd.setOnClickListener { showAddDialog() }

        binding.btnWashSelected.setOnClickListener {
            val ids = adapter.selected.toList()
            if (ids.isEmpty()) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                repo.wash(ids)
                adapter.clearSelection()
                Toast.makeText(requireContext(), "${ids.size} item dicuci ✓", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnWearSelected.setOnClickListener {
            val ids = adapter.selected.filter { id ->
                allItems.any { it.id == id && it.status == ItemStatus.BERSIH.name }
            }
            if (ids.isEmpty()) {
                Toast.makeText(requireContext(), "Pilih baju yang masih bersih", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                repo.logManual(ids)
                adapter.clearSelection()
                Toast.makeText(requireContext(), "Ditandai dipakai ✓", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            val ids = adapter.selected.toList()
            if (ids.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Hapus ${ids.size} item?")
                .setMessage("Riwayat catatan lama tetap tersimpan.")
                .setPositiveButton("Hapus") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        ids.forEach { repo.softDelete(it) }
                        adapter.clearSelection()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun updateBar() {
        b?.actionBar?.visibility = if (adapter.selected.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showAddDialog() {
        val dialog = DialogAddBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle("Tambah ke lemari")
            .setView(dialog.root)
            .setPositiveButton("Tambah") { _, _ ->
                val prefix = dialog.etPrefix.text.toString().ifBlank { "Item" }
                val count = dialog.etJumlah.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: 1
                val kat = Kategori.entries[dialog.spKategori.selectedItemPosition]
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.addItemsBatch(kat.name, count, prefix)
                    Toast.makeText(requireContext(), "$count ${kat.label} ditambahkan", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
