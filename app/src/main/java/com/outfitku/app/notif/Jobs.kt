package com.outfitku.app.notif

import android.content.Context
import com.outfitku.app.data.Kategori
import com.outfitku.app.data.Repo
import com.outfitku.app.logic.Engine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Semua job idempoten — dipanggil dari alarm, boot, dan saat app dibuka.
 * Setelah selesai, jadwal berikutnya selalu didaftarkan ulang (self-healing).
 */
object Jobs {

    private fun inQuiet(repo: Repo): Boolean {
        val m = LocalTime.now().hour * 60 + LocalTime.now().minute
        val s = repo.quietStartMin; val e = repo.quietEndMin
        return if (s <= e) m in s until e else m >= s || m < e
    }

    private fun endOfDayMillis(): Long =
        LocalDate.now().atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    suspend fun rollover(context: Context) {
        Repo.get(context).rollover()
    }

    /** Jam cut-off (12:00) + catch-up: kirim konfirmasi auto-wear, sekali per hari. */
    suspend fun autoWearCheck(context: Context) {
        val repo = Repo.get(context)
        val today = repo.today()
        val state = repo.dailyState(today)
        if (!Engine.canAsk(state, repo.isKuliah(LocalDate.now().dayOfWeek.value))) return
        val pick = Engine.pickAutoWear(repo.latestLog()?.itemIds, repo.activeItems())
        val names = repo.itemsByIds(pick).map { it.nama }
        Notifier.notifyAutoConfirm(context, names)
        repo.upsertDaily(today) { it.copy(autoAsked = true) }
        val due = minOf(repo.now() + repo.autoConfirmDelayMin * 60_000L, endOfDayMillis())
        repo.confirmDue = due
        Scheduler.schedule(context, Scheduler.KIND_CONFIRM, due)
    }

    /** Auto-confirm: diabaikan 2 jam → dianggap "Ya" (sumber=AUTO). */
    suspend fun confirmCheck(context: Context) {
        val repo = Repo.get(context)
        val applied = repo.confirmAutoWear()
        if (applied || !Engine.canAutoConfirm(repo.dailyState(repo.today()))) {
            repo.confirmDue = -1
            Scheduler.cancel(context, Scheduler.KIND_CONFIRM)
        } else {
            Scheduler.scheduleNext(context, Scheduler.KIND_CONFIRM)
        }
    }

    /** Malam 20:00 — pengingat cuci + Nag Mode (F16). */
    suspend fun laundryCheck(context: Context) {
        val repo = Repo.get(context)
        if (inQuiet(repo)) return
        val t = repo.today()
        if (repo.lastLaundryDate == t) return
        if (!repo.shouldRemind()) {
            repo.nagCount = 0
            repo.lastNagDate = -1
            return
        }
        if (t < repo.nagSnoozeUntil) return
        if (repo.nagCount > 0 && t - repo.lastNagDate < repo.nagIntervalDays) return
        if (repo.nagStopAfterDays in 1..repo.nagCount) return

        val items = repo.activeItems()
        val low = Engine.lowStockItems(items, repo.thresholds())
            .joinToString(" · ") { (k, n) -> "${Kategori.valueOf(k).label} tinggal $n" }
        val over = Engine.overwornNames(items).joinToString(" · ") { "$it 2× pakai" }
        val body = listOf(low, over).filter { it.isNotBlank() }.joinToString(" · ")
        val title = when {
            repo.nagCount == 0 -> "🧺 Waktunya nyuci!"
            repo.nagCount == 1 -> "Belum nyuci juga? 🧺"
            else -> "Masih belum nyuci? 🧺"
        }
        Notifier.notifyLaundry(context, title, body)
        repo.nagCount += 1
        repo.lastNagDate = t
        repo.lastLaundryDate = t
    }

    /** Dipanggil saat app dibuka / boot: kejar semua alarm yang terlewat. */
    suspend fun catchUp(context: Context) {
        val repo = Repo.get(context)
        Notifier.ensureChannels(context)
        rollover(context)

        val now = repo.now()
        val cutoff = Scheduler.atMinutesToday(repo.cutoffMin)
        val reminder = Scheduler.atMinutesToday(repo.reminderMin)

        if (now >= cutoff) autoWearCheck(context)
        if (repo.confirmDue > 0 && repo.confirmDue <= now) confirmCheck(context)
        if (now >= reminder) laundryCheck(context)

        Scheduler.rescheduleAll(context)
    }
}
