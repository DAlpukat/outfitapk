package com.outfitku.app.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.outfitku.app.data.Repo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 3 exact alarm (setAlarmClock — kebal Doze) + self-healing: setiap kali alarm
 * mati, jadwal berikutnya langsung didaftarkan ulang (lihat Jobs).
 */
object Scheduler {

    const val KIND_AUTO = "AUTO_WEAR"
    const val KIND_CONFIRM = "AUTO_CONFIRM"
    const val KIND_ROLLOVER = "ROLLOVER"
    const val KIND_LAUNDRY = "LAUNDRY"

    private fun pendingIntent(context: Context, kind: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            kind.hashCode(),
            Intent(context, AlarmReceiver::class.java).putExtra("kind", kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun cancel(context: Context, kind: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, kind))
    }

    fun schedule(context: Context, kind: String, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            context, kind.hashCode(),
            Intent(context, com.outfitku.app.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), pendingIntent(context, kind))
    }

    private fun atToday(hour: Int, minute: Int, plusDays: Long = 0): Long =
        LocalDateTime.of(LocalDate.now().plusDays(plusDays), java.time.LocalTime.of(hour, minute))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Epoch milli hari ini pada menit ke-`minuteOfDay` (boleh sudah lewat — untuk deteksi catch-up). */
    fun atMinutesToday(minuteOfDay: Int): Long = atToday(minuteOfDay / 60, minuteOfDay % 60)

    private fun inQuiet(repo: Repo, minuteOfDay: Int): Boolean {
        val s = repo.quietStartMin; val e = repo.quietEndMin
        return if (s <= e) minuteOfDay >= s && minuteOfDay < e else minuteOfDay >= s || minuteOfDay < e
    }

    /** Waktu berikutnya untuk kind; null = tak ada (mis. confirm sudah selesai). */
    fun nextTime(repo: Repo, kind: String, now: Long = System.currentTimeMillis()): Long? =
        when (kind) {
            KIND_AUTO -> {
                val t = atToday(repo.cutoffMin / 60, repo.cutoffMin % 60)
                if (t > now) t else atToday(repo.cutoffMin / 60, repo.cutoffMin % 60, 1)
            }
            KIND_CONFIRM -> {
                val due = repo.confirmDue
                if (due > now) due else null
            }
            KIND_ROLLOVER -> atToday(0, 0, 1)
            KIND_LAUNDRY -> {
                var m = repo.reminderMin
                if (inQuiet(repo, m)) m = repo.quietEndMin
                val t = atToday(m / 60, m % 60)
                if (t > now) t else {
                    val tm = if (inQuiet(repo, m)) repo.quietEndMin else m
                    atToday(tm / 60, tm % 60, 1)
                }
            }
            else -> null
        }

    fun scheduleNext(context: Context, kind: String) {
        val repo = Repo.get(context)
        val t = nextTime(repo, kind)
        if (t != null) schedule(context, kind, t) else cancel(context, kind)
    }

    fun rescheduleAll(context: Context) {
        listOf(KIND_AUTO, KIND_CONFIRM, KIND_ROLLOVER, KIND_LAUNDRY).forEach {
            cancel(context, it)
            scheduleNext(context, it)
        }
    }
}
