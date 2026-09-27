package com.outfitku.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.outfitku.app.R
import com.outfitku.app.data.Repo
import com.outfitku.app.databinding.ActivityMainBinding
import com.outfitku.app.notif.Jobs
import com.outfitku.app.notif.Notifier
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo.get(this)
        if (!repo.onboardingDone) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        Notifier.ensureChannels(this)

        if (savedInstanceState == null) show(TodayFragment())

        b.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.tab_lemari -> LemariFragment()
                R.id.tab_kalender -> KalenderFragment()
                R.id.tab_pengingat -> PengingatFragment()
                else -> TodayFragment()
            }
            show(fragment)
            true
        }

        // Self-healing: kejar alarm terlewat + verifikasi jadwal tiap kali app dibuka
        lifecycleScope.launch { Jobs.catchUp(this@MainActivity) }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}
