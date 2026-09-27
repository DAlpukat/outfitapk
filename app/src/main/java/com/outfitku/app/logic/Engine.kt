package com.outfitku.app.logic

import com.outfitku.app.data.DailyState
import com.outfitku.app.data.Item
import com.outfitku.app.data.ItemStatus
import com.outfitku.app.data.toIdList

/**
 * Murni tanpa Android — semua keputusan auto-wear / stok / trigger diuji unit test.
 * Matriks edge case PRD §9 ada di EngineTest.
 */
object Engine {

    fun canAsk(state: DailyState?, todayIsKuliah: Boolean): Boolean {
        if (!todayIsKuliah) return false
        if (state == null) return true
        return !state.isLibur && !state.countedToday && !state.autoAsked
    }

    fun canAutoConfirm(state: DailyState?): Boolean {
        if (state == null) return false
        return state.autoAsked && !state.isLibur && !state.countedToday && state.autoConfirmedAt == null
    }

    /** Pick auto-wear = outfit terakhir yang masih BERSIH. Item baru/tidak aktif tak pernah ikut. */
    fun pickAutoWear(lastOutfitIds: String?, items: List<Item>): List<Long> {
        val byId = items.filter { it.aktif }.associateBy { it.id }
        return lastOutfitIds.orEmpty().toIdList()
            .filter { byId[it]?.status == ItemStatus.BERSIH.name }
    }

    /** Rollover idempoten: hanya DIPAKAI yang lastWorn-nya sudah lewat hari ini. */
    fun pendingRollover(items: List<Item>, today: Long): List<Long> =
        items.filter {
            it.status == ItemStatus.DIPAKAI.name && (it.lastWorn ?: Long.MAX_VALUE) < today
        }.map { it.id }

    fun stokBersih(items: List<Item>): Map<String, Int> =
        items.filter { it.aktif && it.status == ItemStatus.BERSIH.name }
            .groupingBy { it.kategori }.eachCount()

    fun lowStockItems(items: List<Item>, thresholds: Map<String, Int>): List<Pair<String, Int>> =
        stokBersih(items).mapNotNull { (kat, n) ->
            if (n <= (thresholds[kat] ?: 2)) kat to n else null
        }

    fun overwornNames(items: List<Item>): List<String> =
        items.filter { it.aktif && it.wearSinceWash >= 2 }.map { it.nama }

    fun shouldRemind(items: List<Item>, thresholds: Map<String, Int>): Boolean =
        lowStockItems(items, thresholds).isNotEmpty() || overwornNames(items).isNotEmpty()

    /** Item yang dicentang auto (DIPAKAI) tapi tak terpakai manual → kembali BERSIH. */
    fun revertTargets(autoLogIds: List<Long>, manualIds: List<Long>, items: List<Item>): List<Long> {
        val stillDirty = items.filter { it.status == ItemStatus.DIPAKAI.name }.map { it.id }.toSet()
        return autoLogIds.filter { it !in manualIds && it in stillDirty }
    }
}
