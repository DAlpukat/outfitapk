package com.outfitku.app.data

import android.content.Context
import com.outfitku.app.logic.Engine
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Satu-satunya gerbang data + settings (SharedPreferences). 100% lokal.
 */
class Repo private constructor(private val ctx: Context) {

    private val db = AppDatabase.get(ctx)
    private val prefs = ctx.getSharedPreferences("outfitku", Context.MODE_PRIVATE)

    // ---------- settings ----------
    var cutoffMin: Int
        get() = prefs.getInt("cutoff", 12 * 60)
        set(v) = prefs.edit().putInt("cutoff", v).apply()
    var reminderMin: Int
        get() = prefs.getInt("reminder", 20 * 60)
        set(v) = prefs.edit().putInt("reminder", v).apply()
    var autoConfirmDelayMin: Int
        get() = prefs.getInt("autoDelay", 120)
        set(v) = prefs.edit().putInt("autoDelay", v).apply()
    var nagIntervalDays: Int
        get() = prefs.getInt("nagInterval", 1)
        set(v) = prefs.edit().putInt("nagInterval", v).apply()
    var nagStopAfterDays: Int
        get() = prefs.getInt("nagStop", 0) // 0 = terus sampai dicuci
        set(v) = prefs.edit().putInt("nagStop", v).apply()

    var hariKuliah: Set<Int>
        get() = prefs.getString("hari", "1,2,3,4,5")!!.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putString("hari", v.sorted().joinToString(",")).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding", false)
        set(v) = prefs.edit().putBoolean("onboarding", v).apply()

    fun isKuliah(dayOfWeek: Int): Boolean = dayOfWeek in hariKuliah

    fun threshold(kategori: String): Int =
        prefs.getInt("thr_$kategori", Kategori.valueOf(kategori).thresholdDefault)

    fun setThreshold(kategori: String, v: Int) = prefs.edit().putInt("thr_$kategori", v).apply()

    fun thresholds(): Map<String, Int> = Kategori.entries.associate { it.name to threshold(it.name) }

    var quietStartMin: Int
        get() = prefs.getInt("quietStart", 22 * 60)
        set(v) = prefs.edit().putInt("quietStart", v).apply()
    var quietEndMin: Int
        get() = prefs.getInt("quietEnd", 8 * 60)
        set(v) = prefs.edit().putInt("quietEnd", v).apply()

    // flags harian (nag, laundry) — sederhana, dipakai Scheduler
    var lastLaundryDate: Long
        get() = prefs.getLong("lastLaundry", -1)
        set(v) = prefs.edit().putLong("lastLaundry", v).apply()
    var lastNagDate: Long
        get() = prefs.getLong("lastNag", -1)
        set(v) = prefs.edit().putLong("lastNag", v).apply()
    var nagCount: Int
        get() = prefs.getInt("nagCount", 0)
        set(v) = prefs.edit().putInt("nagCount", v).apply()
    var nagSnoozeUntil: Long
        get() = prefs.getLong("nagSnooze", -1)
        set(v) = prefs.edit().putLong("nagSnooze", v).apply()
    var autoCancelToday: Long
        get() = prefs.getLong("autoCancel", -1)
        set(v) = prefs.edit().putLong("autoCancel", v).apply()

    /** Batas waktu auto-confirm (jam 12 + delay, dibatasi 23:59 hari yang sama). */
    var confirmDue: Long
        get() = prefs.getLong("confirmDue", -1)
        set(v) = prefs.edit().putLong("confirmDue", v).apply()

    // ---------- waktu ----------
    fun today(): Long = LocalDate.now().toEpochDay()
    fun now(): Long = System.currentTimeMillis()

    // ---------- data: lemari ----------
    fun observeItems(): Flow<List<Item>> = db.itemDao().observeAll()
    fun observeLogs(): Flow<List<OutfitLog>> = db.outfitLogDao().observeAll()
    suspend fun activeItems(): List<Item> = db.itemDao().active()
    suspend fun itemsByIds(ids: List<Long>): List<Item> = db.itemDao().byIds(ids)

    suspend fun addItemsBatch(kategori: String, count: Int, prefix: String) {
        val named = db.itemDao().active().count { it.nama.startsWith(prefix) }
        db.itemDao().insertAll(
            (1..count).map {
                Item(nama = "$prefix ${named + it}", kategori = kategori)
            }
        )
    }

    suspend fun addItem(nama: String, kategori: String) {
        db.itemDao().insertAll(listOf(Item(nama = nama, kategori = kategori)))
    }

    suspend fun softDelete(id: Long) = db.itemDao().softDelete(id)

    // ---------- data: catat outfit ----------
    suspend fun todayLog(): OutfitLog? = db.outfitLogDao().byDate(today()).firstOrNull()
    suspend fun latestLog(): OutfitLog? = db.outfitLogDao().latest()
    suspend fun logByDate(date: Long): List<OutfitLog> = db.outfitLogDao().byDate(date)
    suspend fun dailyState(date: Long = today()): DailyState? = db.dailyStateDao().get(date)

    suspend fun logManual(ids: List<Long>, foto: String? = null, catatan: String? = null) {
        val t = today()
        val auto = db.outfitLogDao().autoByDate(t)
        if (auto != null) {
            val revert = Engine.revertTargets(auto.itemIds.toIdList(), ids, db.itemDao().active())
            db.itemDao().backToClean(revert)
            db.outfitLogDao().delete(auto.id)
        }
        if (ids.isNotEmpty()) db.itemDao().markWorn(ids, t)
        val existingManual = db.outfitLogDao().byDate(t).firstOrNull { it.sumber == "MANUAL" }
        if (existingManual != null) {
            val merged = (existingManual.itemIds.toIdList() + ids).distinct()
            db.outfitLogDao().update(
                existingManual.copy(
                    itemIds = merged.toIdCsv(),
                    foto = foto ?: existingManual.foto,
                    catatan = catatan ?: existingManual.catatan
                )
            )
        } else {
            db.outfitLogDao().insert(
                OutfitLog(tanggal = t, itemIds = ids.toIdCsv(), foto = foto, catatan = catatan, sumber = "MANUAL")
            )
        }
        upsertDaily(t) { it.copy(countedToday = true) }
    }

    suspend fun deleteLog(log: OutfitLog) {
        val ids = log.itemIds.toIdList()
        if (ids.isNotEmpty()) db.itemDao().backToClean(ids)
        db.outfitLogDao().delete(log.id)
        if (log.tanggal == today()) upsertDaily(today()) { it.copy(countedToday = false) }
    }

    /** Konfirmasi auto (Ya / auto-confirm 2 jam). Idempoten — bolak-balik tak dobel. */
    suspend fun confirmAutoWear(): Boolean {
        val t = today()
        val state = dailyState(t) ?: return false
        if (!Engine.canAutoConfirm(state)) return false
        if (db.outfitLogDao().autoByDate(t) != null) return false
        val pick = Engine.pickAutoWear(db.outfitLogDao().latest()?.itemIds, db.itemDao().active())
        if (pick.isNotEmpty()) db.itemDao().markWorn(pick, t)
        db.outfitLogDao().insert(
            OutfitLog(tanggal = t, itemIds = pick.toIdCsv(), sumber = "AUTO")
        )
        upsertDaily(t) { it.copy(countedToday = true, autoConfirmedAt = now()) }
        return true
    }

    suspend fun setLibur() {
        upsertDaily(today()) { it.copy(isLibur = true) }
    }

    suspend fun upsertDaily(t: Long, transform: (DailyState) -> DailyState) {
        val cur = db.dailyStateDao().get(t) ?: DailyState(tanggal = t)
        db.dailyStateDao().upsert(transform(cur))
    }

    // ---------- data: cuci ----------
    suspend fun wash(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.itemDao().setWashed(ids, now())
        db.washEventDao().insert(WashEvent(tanggal = today(), itemIds = ids.toIdCsv()))
    }

    /** Dari notif "✅ Sudah Nyuci": cuci semua yang bukan BERSIH. */
    suspend fun washAllDirty(): List<Long> {
        val ids = db.itemDao().active().filter { it.status != ItemStatus.BERSIH.name }.map { it.id }
        wash(ids)
        return ids
    }

    suspend fun dirtyIds(): List<Long> =
        db.itemDao().active().filter { it.status != ItemStatus.BERSIH.name }.map { it.id }

    // ---------- rollover ----------
    suspend fun rollover() {
        val pending = Engine.pendingRollover(db.itemDao().active(), today())
        if (pending.isNotEmpty()) db.itemDao().markDirty(pending)
    }

    // ---------- ringkasan ----------
    suspend fun stockLine(): String {
        val stok = Engine.stokBersih(db.itemDao().active())
        if (stok.isEmpty()) return "Lemari kosong — isi dulu ya"
        return stok.entries.joinToString(" · ") { (kat, n) ->
            "${Kategori.valueOf(kat).label} bersih: $n"
        }
    }

    suspend fun shouldRemind(): Boolean =
        Engine.shouldRemind(db.itemDao().active(), thresholds())

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo =
            instance ?: synchronized(this) {
                instance ?: Repo(context.applicationContext).also { instance = it }
            }
    }
}
