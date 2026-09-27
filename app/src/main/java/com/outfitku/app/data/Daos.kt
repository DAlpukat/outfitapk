package com.outfitku.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE aktif = 1")
    suspend fun active(): List<Item>

    @Query("SELECT * FROM items WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Item>

    @Insert
    suspend fun insertAll(items: List<Item>): List<Long>

    @Query("UPDATE items SET status='DIPAKAI', lastWorn=:day WHERE id IN (:ids)")
    suspend fun markWorn(ids: List<Long>, day: Long)

    @Query("UPDATE items SET status='BERSIH', lastWorn=NULL WHERE id IN (:ids)")
    suspend fun backToClean(ids: List<Long>)

    @Query("UPDATE items SET status='KOTOR', wearSinceWash=wearSinceWash+1 WHERE id IN (:ids)")
    suspend fun markDirty(ids: List<Long>)

    @Query("UPDATE items SET status='BERSIH', wearSinceWash=0, lastWashed=:now WHERE id IN (:ids)")
    suspend fun setWashed(ids: List<Long>, now: Long)

    @Query("UPDATE items SET aktif=0 WHERE id=:id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE items SET kategori=:kategori WHERE id=:id")
    suspend fun setKategori(id: Long, kategori: String)
}

@Dao
interface OutfitLogDao {
    @Query("SELECT * FROM outfit_logs WHERE tanggal=:tanggal ORDER BY id DESC")
    suspend fun byDate(tanggal: Long): List<OutfitLog>

    @Query("SELECT * FROM outfit_logs ORDER BY tanggal DESC, id DESC")
    fun observeAll(): Flow<List<OutfitLog>>

    @Query("SELECT * FROM outfit_logs WHERE tanggal=:tanggal AND sumber='AUTO'")
    suspend fun autoByDate(tanggal: Long): OutfitLog?

    @Query("SELECT * FROM outfit_logs ORDER BY tanggal DESC, id DESC LIMIT 1")
    suspend fun latest(): OutfitLog?

    @Insert
    suspend fun insert(log: OutfitLog): Long

    @androidx.room.Update
    suspend fun update(log: OutfitLog)

    @Query("DELETE FROM outfit_logs WHERE id=:id")
    suspend fun delete(id: Long)
}

@Dao
interface WashEventDao {
    @Insert
    suspend fun insert(event: WashEvent)

    @Query("SELECT * FROM wash_events ORDER BY tanggal DESC")
    fun observeAll(): Flow<List<WashEvent>>
}

@Dao
interface DailyStateDao {
    @Query("SELECT * FROM daily_states WHERE tanggal=:tanggal")
    suspend fun get(tanggal: Long): DailyState?

    @Upsert
    suspend fun upsert(state: DailyState)
}
