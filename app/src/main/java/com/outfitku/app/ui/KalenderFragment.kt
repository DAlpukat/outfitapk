package com.outfitku.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.outfitku.app.R
import com.outfitku.app.data.Item
import com.outfitku.app.data.OutfitLog
import com.outfitku.app.data.Repo
import com.outfitku.app.data.toIdList
import com.outfitku.app.databinding.FragmentKalenderBinding
import kotlinx.coroutines.launch
import java.time.LocalDate

class KalenderFragment : Fragment(R.layout.fragment_kalender) {

    private var b: FragmentKalenderBinding? = null
    private val repo by lazy { Repo.get(requireContext()) }
    private lateinit var adapter: LogAdapter
    private var selectedDate = LocalDate.now()
    private var namesById = mapOf<Long, String>()
    private var currentLogs = listOf<OutfitLog>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentKalenderBinding.bind(view)
        b = binding

        adapter = LogAdapter(
            names = { log -> log.itemIds.toIdList().mapNotNull { namesById[it] }.joinToString(", ").ifBlank { "(tanpa item)" } },
            onClick = { showDetail(it) }
        )
        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeItems().collect { items ->
                namesById = items.associate { it.id to it.nama }
                loadDay()
            }
        }

        binding.calendar.setOnDateChangeListener { _, y, m, d ->
            selectedDate = LocalDate.of(y, m + 1, d)
            loadDay()
        }

        loadDay()
    }

    private fun loadDay() {
        viewLifecycleOwner.lifecycleScope.launch {
            val epoch = selectedDate.toEpochDay()
            val logs = repo.logByDate(epoch)
            currentLogs = logs
            adapter.submitList(logs)
            val empty = b?.tvEmpty ?: return@launch
            empty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
            empty.text = if (epoch == LocalDate.now().toEpochDay())
                "Belum ada catatan hari ini.\nCatat outfit di tab Beranda →"
            else
                "Tidak ada catatan di tanggal ini."
        }
    }

    private fun showDetail(log: OutfitLog) {
        val names = log.itemIds.toIdList().mapNotNull { namesById[it] }.joinToString(", ")
        val isToday = log.tanggal == LocalDate.now().toEpochDay()
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(if (log.sumber == "AUTO") "Otomatis (Auto-Wear)" else "Catatan manual")
            .setMessage("$names\n\n${log.catatan ?: ""}".trim())
            .setPositiveButton("Tutup", null)
        if (isToday) {
            builder.setNeutralButton("Koreksi: hapus") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.deleteLog(log)
                    Toast.makeText(requireContext(), "Catatan dihapus", Toast.LENGTH_SHORT).show()
                    loadDay()
                }
            }
        }
        builder.show()
    }
}
