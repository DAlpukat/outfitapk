package com.outfitku.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ItemStatus { BERSIH, DIPAKAI, KOTOR }

enum class Kategori(val label: String, val thresholdDefault: Int) {
    ATASAN("Atasan", 2),
    BAWAHAN("Bawahan", 2),
    OUTER("Outer", 1),
    SEPATU("Sepatu", 1),
    AKSESORI("Aksesoris", 1)
}

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val kategori: String,
    val status: String = ItemStatus.BERSIH.name,
    val wearSinceWash: Int = 0,
    val lastWorn: Long? = null,
    val lastWashed: Long? = null,
    val foto: String? = null,
    val aktif: Boolean = true
)

@Entity(tableName = "outfit_logs")
data class OutfitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tanggal: Long,
    val itemIds: String,
    val foto: String? = null,
    val catatan: String? = null,
    val sumber: String = "MANUAL"
)

@Entity(tableName = "wash_events")
data class WashEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tanggal: Long,
    val itemIds: String
)

@Entity(tableName = "daily_states")
data class DailyState(
    @PrimaryKey val tanggal: Long,
    val isLibur: Boolean = false,
    val countedToday: Boolean = false,
    val autoAsked: Boolean = false,
    val autoConfirmedAt: Long? = null
)

fun String.toIdList(): List<Long> =
    if (isEmpty()) emptyList() else split(",").mapNotNull { it.toLongOrNull() }

fun List<Long>.toIdCsv(): String = joinToString(",")
