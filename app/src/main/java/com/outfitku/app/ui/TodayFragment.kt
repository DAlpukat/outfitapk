package com.outfitku.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.outfitku.app.R
import com.outfitku.app.data.ItemStatus
import com.outfitku.app.data.Kategori
import com.outfitku.app.data.Repo
import com.outfitku.app.databinding.FragmentTodayBinding
import com.outfitku.app.util.PhotoStore
import kotlinx.coroutines.launch
import java.io.File

class TodayFragment : Fragment(R.layout.fragment_today) {

    private var b: FragmentTodayBinding? = null
    private val repo by lazy { Repo.get(requireContext()) }
    private val checked = mutableSetOf<Long>()
    private var photoPath: String? = null

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@registerForActivityResult
            val path = PhotoStore.save(requireContext(), uri)
            if (path != null) {
                photoPath = path
                b?.ivPhoto?.let {
                    it.visibility = View.VISIBLE
                    it.setImageURI(android.net.Uri.fromFile(File(path)))
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentTodayBinding.bind(view)
        b = binding

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeItems().collect { refresh() }
        }

        binding.btnLog.setOnClickListener {
            val ids = checked.toList()
            if (ids.isEmpty()) {
                Toast.makeText(requireContext(), "Centang dulu baju yang dipakai", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val checkedChip = binding.tagGroup.checkedChipId
                val tag = when (checkedChip) {
                    R.id.tagKuliah -> "Kuliah"; R.id.tagUKM -> "UKM"; R.id.tagEvent -> "Event"
                    R.id.tagHujan -> "Hujan"; R.id.tagSantai -> "Santai"; else -> null
                }
                val note = binding.etCatatan.text.toString().trim().ifBlank { null }
                val catatan = listOfNotNull(tag, note).joinToString(" · ").ifBlank { null }
                repo.logManual(ids, photoPath, catatan)
                checked.clear()
                photoPath = null
                binding.ivPhoto.visibility = View.GONE
                binding.tagGroup.clearCheck()
                binding.etCatatan.text?.clear()
                Toast.makeText(requireContext(), "Outfit tercatat ✓", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }

        binding.btnReuse.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val last = repo.latestLog()
                val ids = last?.itemIds?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
                    .filter { id -> repo.activeItems().any { it.id == id && it.status == ItemStatus.BERSIH.name } }
                if (ids.isEmpty()) {
                    Toast.makeText(requireContext(), "Belum ada outfit sebelumnya / semua sudah dipakai", Toast.LENGTH_SHORT).show()
                } else {
                    repo.logManual(ids)
                    Toast.makeText(requireContext(), "Ulang outfit terakhir ✓", Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }

        binding.btnPhoto.setOnClickListener { pickPhoto.launch(null) }

        binding.btnWash.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.tab_lemari
        }

        refresh()
    }

    private fun refresh() {
        val binding = b ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val items = repo.activeItems()
            val log = repo.todayLog()
            binding.tvLogged.visibility = if (log != null) View.VISIBLE else View.GONE
            binding.tvStock.text = "🧺 " + repo.stockLine()

            val shownPhoto = photoPath ?: log?.foto
            if (shownPhoto != null) {
                binding.ivPhoto.visibility = View.VISIBLE
                binding.ivPhoto.setImageURI(android.net.Uri.fromFile(File(shownPhoto)))
            } else {
                binding.ivPhoto.visibility = View.GONE
            }

            binding.chipsContainer.removeAllViews()
            Kategori.entries.forEach { kat ->
                val clean = items.filter { it.kategori == kat.name && it.status == ItemStatus.BERSIH.name }
                if (clean.isEmpty()) return@forEach
                val label = android.widget.TextView(requireContext()).apply {
                    text = kat.label
                    textSize = 14f
                    setPadding(0, 16, 0, 4)
                }
                val group = ChipGroup(requireContext())
                clean.forEach { item ->
                    val chip = Chip(requireContext()).apply {
                        text = item.nama
                        isCheckable = true
                        isChecked = item.id in checked
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) checked.add(item.id) else checked.remove(item.id)
                        }
                    }
                    group.addView(chip)
                }
                binding.chipsContainer.addView(label)
                binding.chipsContainer.addView(group)
            }
            if (binding.chipsContainer.childCount == 0) {
                binding.chipsContainer.addView(
                    android.widget.TextView(requireContext()).apply {
                        text = "Tidak ada baju bersih — tambah di tab Lemari atau tandai sudah dicuci."
                        setPadding(0, 16, 0, 0)
                    }
                )
            }
        }
    }
}
