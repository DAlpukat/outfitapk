package com.outfitku.app.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.outfitku.app.R
import com.outfitku.app.data.Kategori
import com.outfitku.app.data.Repo
import com.outfitku.app.databinding.FragmentPengingatBinding
import com.outfitku.app.databinding.RowThresholdBinding
import com.outfitku.app.notif.Jobs
import com.outfitku.app.notif.Notifier
import com.outfitku.app.notif.Scheduler
import kotlinx.coroutines.launch
import java.time.LocalTime

class PengingatFragment : Fragment(R.layout.fragment_pengingat) {

    private var b: FragmentPengingatBinding? = null
    private val repo by lazy { Repo.get(requireContext()) }
    private var ready = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPengingatBinding.bind(view)
        b = binding
        val ctx = requireContext()

        Kategori.entries.forEach { kat ->
            val row = RowThresholdBinding.inflate(layoutInflater, binding.thresholdContainer, false)
            row.tvLabel.text = kat.label
            row.etValue.setText(repo.threshold(kat.name).toString())
            row.etValue.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, n: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, c: Int, n: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    s?.toString()?.toIntOrNull()?.let { repo.setThreshold(kat.name, it.coerceIn(0, 10)) }
                }
            })
            binding.thresholdContainer.addView(row.root)
        }

        binding.btnReminderTime.setOnClickListener {
            pickTime(repo.reminderMin) { h, m ->
                repo.reminderMin = h * 60 + m
                labels()
                Scheduler.rescheduleAll(ctx)
            }
        }

        binding.btnCutoff.setOnClickListener {
            pickTime(repo.cutoffMin) { h, m ->
                repo.cutoffMin = h * 60 + m
                labels()
                Scheduler.rescheduleAll(ctx)
            }
        }

        binding.btnQuiet.setOnClickListener {
            pickTime(repo.quietStartMin) { hs, ms ->
                pickTime(repo.quietEndMin) { he, me ->
                    repo.quietStartMin = hs * 60 + ms
                    repo.quietEndMin = he * 60 + me
                    labels()
                    Scheduler.rescheduleAll(ctx)
                }
            }
        }

        binding.btnNagInterval.setOnClickListener {
            val cycle = listOf(1, 2, 3, 7)
            repo.nagIntervalDays = cycle[(cycle.indexOf(repo.nagIntervalDays) + 1) % cycle.size]
            labels()
        }

        binding.btnNagStop.setOnClickListener {
            val cycle = listOf(0, 7, 14, 30)
            repo.nagStopAfterDays = cycle[(cycle.indexOf(repo.nagStopAfterDays) + 1) % cycle.size]
            labels()
        }

        val switches = listOf(binding.sw1, binding.sw2, binding.sw3, binding.sw4, binding.sw5, binding.sw6, binding.sw7)
        switches.forEachIndexed { i, sw ->
            sw.isChecked = (i + 1) in repo.hariKuliah
            sw.setOnCheckedChangeListener { _, _ ->
                if (!ready) return@setOnCheckedChangeListener
                repo.hariKuliah = switches.mapIndexed { j, s -> if (s.isChecked) j + 1 else -1 }.filter { it > 0 }.toSet()
                Scheduler.rescheduleAll(ctx)
            }
        }
        ready = true

        binding.btnTest.setOnClickListener {
            if (!Notifier.canSend(ctx)) {
                Toast.makeText(ctx, "Izinkan notifikasi dulu ya", Toast.LENGTH_LONG).show()
            } else {
                Notifier.notifyTest(ctx)
            }
        }

        binding.btnExact.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                val am = ctx.getSystemService(AlarmManager::class.java)
                if (!am.canScheduleExactAlarms()) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    })
                } else Toast.makeText(ctx, "Alarm persis sudah aktif ✓", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNotifSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            })
        }

        binding.btnOem.setOnClickListener {
            binding.tvOem.visibility = if (binding.tvOem.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        labels()
    }

    override fun onResume() {
        super.onResume()
        val binding = b ?: return
        val ctx = requireContext()
        val notifOn = Notifier.canSend(ctx)
        val exactOk = Build.VERSION.SDK_INT < 31 ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        binding.tvHealth.text = buildString {
            append(if (notifOn) "✓ Notifikasi aktif" else "✗ Notifikasi nonaktif — klik tombol di bawah")
            append("\n")
            append(if (exactOk) "✓ Alarm persis aktif (anti-telat)" else "✗ Alarm persis belum diizinkan")
            append("\nJadwal: otomatis ${fmt(repo.cutoffMin)} · nyuci ${fmt(repo.reminderMin)} · rollover 00:00")
        }
        labels()
    }

    private fun labels() {
        val binding = b ?: return
        binding.btnReminderTime.text = "Jam pengingat: ${fmt(repo.reminderMin)}"
        binding.btnCutoff.text = "Cut-off auto-wear: ${fmt(repo.cutoffMin)}"
        binding.btnQuiet.text = "Quiet hours: ${fmt(repo.quietStartMin)}–${fmt(repo.quietEndMin)}"
        binding.btnNagInterval.text = "Interval nag: tiap ${repo.nagIntervalDays} hari"
        binding.btnNagStop.text =
            "Berhenti nag setelah: " + (if (repo.nagStopAfterDays == 0) "terus" else "${repo.nagStopAfterDays} hari")
    }

    private fun pickTime(current: Int, onPicked: (Int, Int) -> Unit) =
        android.app.TimePickerDialog(requireContext(), { _, h, m -> onPicked(h, m) }, current / 60, current % 60, true).show()

    private fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
}

