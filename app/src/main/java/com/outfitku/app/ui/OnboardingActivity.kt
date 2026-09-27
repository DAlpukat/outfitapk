package com.outfitku.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.outfitku.app.data.Repo
import com.outfitku.app.databinding.ActivityOnboardingBinding
import com.outfitku.app.databinding.RowThresholdBinding
import com.outfitku.app.notif.Notifier
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var b: ActivityOnboardingBinding
    private val repo by lazy { Repo.get(this) }
    private var step = 0
    private var presetDone = false

    private val presets = listOf(
        Triple("Kaos (atasan)", "ATASAN", 5),
        Triple("Kemeja (atasan)", "ATASAN", 2),
        Triple("Celana (bawahan)", "BAWAHAN", 3),
        Triple("Outer", "OUTER", 1),
        Triple("Sepatu", "SEPATU", 2)
    )
    private val presetEdits = mutableListOf<EditText>()

    private val askNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            Toast.makeText(this, if (it) "Notifikasi diizinkan ✓" else "Pengingat mungkin telat. Bisa diatur nanti.", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)

        presets.forEach { (label, _, count) ->
            val row = RowThresholdBinding.inflate(layoutInflater, b.presetContainer, false)
            row.tvLabel.text = label
            row.etValue.setText(count.toString())
            presetEdits.add(row.etValue)
            b.presetContainer.addView(row.root)
        }

        b.btnAskNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Toast.makeText(this, "Notifikasi aktif ✓", Toast.LENGTH_SHORT).show()
        }

        b.btnNext.setOnClickListener {
            if (step == 1) commitPresets()
            if (step == 2) saveHariKuliah()
            if (step == 3) {
                repo.onboardingDone = true
                finish()
                return@setOnClickListener
            }
            showStep(step + 1)
        }

        b.btnBack.setOnClickListener { showStep(step - 1) }

        b.btnOwReminder.setOnClickListener {
            pickTime(repo.reminderMin) { h, m ->
                repo.reminderMin = h * 60 + m
                b.btnOwReminder.text = "Jam pengingat: ${fmt(repo.reminderMin)}"
            }
        }

        b.btnOwTest.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            Notifier.notifyTest(this)
        }

        showStep(0)
    }

    private fun showStep(s: Int) {
        step = s
        b.tvStep.text = "Langkah ${s + 1}/4"
        val views = listOf(b.step1, b.step2, b.step3, b.step4)
        views.forEachIndexed { i, v -> v.visibility = if (i == s) View.VISIBLE else View.GONE }
        b.btnBack.visibility = if (s == 0) View.INVISIBLE else View.VISIBLE
        b.btnNext.text = if (s == 3) "Selesai" else "Next"
    }

    private fun commitPresets() {
        if (presetDone) return
        presetDone = true
        lifecycleScope.launch {
            presets.forEachIndexed { i, (_, kat, _) ->
                val n = presetEdits[i].text.toString().toIntOrNull() ?: 0
                if (n > 0) repo.addItemsBatch(kat, n, presets[i].first.split(" (")[0])
            }
        }
    }

    private fun saveHariKuliah() {
        val switches = listOf(b.ow1, b.ow2, b.ow3, b.ow4, b.ow5, b.ow6, b.ow7)
        repo.hariKuliah = switches.mapIndexed { i, sw -> if (sw.isChecked) i + 1 else -1 }.filter { it > 0 }.toSet()
    }

    private fun pickTime(current: Int, onPicked: (Int, Int) -> Unit) =
        TimePickerDialog(this, { _, h, m -> onPicked(h, m) }, current / 60, current % 60, true).show()

    private fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
}
