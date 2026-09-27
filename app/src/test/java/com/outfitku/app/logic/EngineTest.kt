package com.outfitku.app.logic

import com.outfitku.app.data.DailyState
import com.outfitku.app.data.Item
import com.outfitku.app.data.ItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private fun item(
        id: Long, kat: String = "ATASAN", status: String = ItemStatus.BERSIH.name,
        wsw: Int = 0, lastWorn: Long? = null, aktif: Boolean = true
    ) = Item(id = id, nama = "Item$id", kategori = kat, status = status, wearSinceWash = wsw, lastWorn = lastWorn, aktif = aktif)

    private val today = 100L

    // 1. Auto jam 12 → user catat manual jam 15: tidak dobel, item auto yang tak terpakai kembali BERSIH
    @Test
    fun autoLaluManual_revertHanyaItemAuto() {
        val items = listOf(
            item(1, status = ItemStatus.DIPAKAI.name),
            item(2, status = ItemStatus.DIPAKAI.name)
        )
        val revert = Engine.revertTargets(listOf(1, 2), listOf(1), items)
        assertEquals(listOf(2L), revert)
        assertTrue(Engine.revertTargets(listOf(1, 2), listOf(1, 2), items).isEmpty())
    }

    // 2. Auto jam 12 → user tap Libur: stok utuh, hari ditandai libur
    @Test
    fun libur_tidakAuto() {
        val state = DailyState(tanggal = today, isLibur = true, autoAsked = true)
        assertFalse(Engine.canAsk(state, todayIsKuliah = true))
        assertFalse(Engine.canAutoConfirm(state))
    }

    // 3. Notif jam 12 diabaikan 2 jam → auto-confirm "Ya"
    @Test
    fun diabaikan2jam_autoConfirm() {
        val state = DailyState(tanggal = today, autoAsked = true)
        assertTrue(Engine.canAutoConfirm(state))
        assertFalse(Engine.canAutoConfirm(state.copy(countedToday = true)))
        assertFalse(Engine.canAutoConfirm(state.copy(autoConfirmedAt = 1L)))
    }

    // 4. HP mati → catch-up 1× saja (idempoten via autoAsked)
    @Test
    fun catchUp_idempoten() {
        assertTrue(Engine.canAsk(null, todayIsKuliah = true))
        assertFalse(Engine.canAsk(DailyState(tanggal = today, autoAsked = true), todayIsKuliah = true))
    }

    // 5. User centang dipakai jam 11:59 → jam 12 engine skip
    @Test
    fun manualSebelumCutoff_skip() {
        val state = DailyState(tanggal = today, countedToday = true, autoAsked = true)
        assertFalse(Engine.canAsk(state, todayIsKuliah = true))
        assertFalse(Engine.canAutoConfirm(state))
    }

    // 6. Item baru ditambah siang → tidak ikut auto kecuali dicentang
    @Test
    fun itemBaru_tidakIkutAuto() {
        val lastOutfit = "1,2"
        val items = listOf(item(1), item(2), item(3)) // item 3 baru
        assertEquals(listOf(1L, 2L), Engine.pickAutoWear(lastOutfit, items))
    }

    // 6b. Item non-aktif / sudah dipakai tidak dipick auto
    @Test
    fun pickAutoWear_hanyaBersihDanAktif() {
        val items = listOf(
            item(1, status = ItemStatus.KOTOR.name),
            item(2, aktif = false),
            item(3)
        )
        assertEquals(listOf(3L), Engine.pickAutoWear("1,2,3", items))
        assertTrue(Engine.pickAutoWear(null, items).isEmpty())
    }

    // 7. Item dihapus saat KOTOR → soft-delete, tak masuk hitungan
    @Test
    fun softDeleteKotor_keluarHitungan() {
        val items = listOf(
            item(1, status = ItemStatus.KOTOR.name, aktif = false),
            item(2),
            item(3, status = ItemStatus.BERSIH.name)
        )
        assertEquals(mapOf("ATASAN" to 2), Engine.stokBersih(items))
        assertTrue(Engine.overwornNames(items).isEmpty())
    }

    // 8. User centang "sudah dicuci" → trigger berhenti, counter reset
    @Test
    fun setelahDicuci_tidakRemind() {
        val clean = listOf(
            item(1, wsw = 0), item(4, wsw = 0), item(5, wsw = 0),
            item(2, kat = "BAWAHAN", wsw = 0), item(6, kat = "BAWAHAN", wsw = 0),
            item(7, kat = "BAWAHAN", wsw = 0)
        )
        assertFalse(Engine.shouldRemind(clean, mapOf("ATASAN" to 2, "BAWAHAN" to 2)))
        // stok tipis → remind
        val few = listOf(item(1))
        assertTrue(Engine.shouldRemind(few, mapOf("ATASAN" to 2)))
        // 2× pakai tanpa cuci → remind
        val worn = listOf(item(1, status = ItemStatus.KOTOR.name, wsw = 2))
        assertTrue(Engine.shouldRemind(worn, mapOf("ATASAN" to 0)))
    }

    // Rollover idempoten: DIPAKAI hari ini belum diroll; kemarin diroll; KOTOR tak diroll lagi
    @Test
    fun rollover_idempoten() {
        val items = listOf(
            item(1, status = ItemStatus.DIPAKAI.name, lastWorn = today),     // hari ini → belum
            item(2, status = ItemStatus.DIPAKAI.name, lastWorn = today - 1), // kemarin → roll
            item(3, status = ItemStatus.KOTOR.name, lastWorn = today - 1)    // sudah kotor → tidak
        )
        assertEquals(listOf(2L), Engine.pendingRollover(items, today))
        val afterRoll = items.map { if (it.id == 2L) it.copy(status = ItemStatus.KOTOR.name, wearSinceWash = 1) else it }
        assertTrue(Engine.pendingRollover(afterRoll, today).isEmpty())
    }

    // Stok per kategori: hanya BERSIH & aktif
    @Test
    fun stok_hanyaBersihAktif() {
        val items = listOf(
            item(1, "ATASAN", ItemStatus.BERSIH.name),
            item(2, "ATASAN", ItemStatus.DIPAKAI.name),
            item(3, "ATASAN", ItemStatus.KOTOR.name),
            item(4, "ATASAN", ItemStatus.BERSIH.name, aktif = false),
            item(5, "BAWAHAN", ItemStatus.BERSIH.name)
        )
        assertEquals(mapOf("ATASAN" to 1, "BAWAHAN" to 1), Engine.stokBersih(items))
    }
}
