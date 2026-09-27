package com.outfitku.app.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.outfitku.app.data.Repo
import com.outfitku.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarm exact (setAlarmClock) → job sesuai kind + re-schedule berantai. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (kind) {
                    Scheduler.KIND_AUTO -> {
                        Jobs.autoWearCheck(context)
                        Scheduler.scheduleNext(context, Scheduler.KIND_AUTO)
                    }
                    Scheduler.KIND_CONFIRM -> Jobs.confirmCheck(context)
                    Scheduler.KIND_ROLLOVER -> {
                        Jobs.rollover(context)
                        Scheduler.scheduleNext(context, Scheduler.KIND_ROLLOVER)
                    }
                    Scheduler.KIND_LAUNDRY -> {
                        Jobs.laundryCheck(context)
                        Scheduler.scheduleNext(context, Scheduler.KIND_LAUNDRY)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Aksi dari notifikasi (F7): Sudah Nyuci / Besok / Ya / Koreksi / Libur. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action") ?: return
        val repo = Repo.get(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    Notifier.A_WASH -> {
                        repo.washAllDirty()
                        repo.nagCount = 0
                        repo.lastNagDate = -1
                        repo.nagSnoozeUntil = -1
                        Scheduler.scheduleNext(context, Scheduler.KIND_LAUNDRY)
                    }
                    Notifier.A_SNOOZE -> {
                        repo.nagSnoozeUntil = repo.today() + repo.nagIntervalDays
                    }
                    Notifier.A_YA -> {
                        repo.confirmAutoWear()
                        repo.confirmDue = -1
                        Scheduler.cancel(context, Scheduler.KIND_CONFIRM)
                        Scheduler.scheduleNext(context, Scheduler.KIND_AUTO)
                    }
                    Notifier.A_KOREKSI -> {
                        repo.autoCancelToday = repo.today()
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra("koreksi", true)
                        )
                    }
                    Notifier.A_LIBUR -> {
                        repo.setLibur()
                        repo.confirmDue = -1
                        Scheduler.cancel(context, Scheduler.KIND_CONFIRM)
                        Scheduler.scheduleNext(context, Scheduler.KIND_AUTO)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Re-schedule semua alarm setelah reboot (PRD §10.2 butir 4). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Jobs.catchUp(context)
            } finally {
                pending.finish()
            }
        }
    }
}
