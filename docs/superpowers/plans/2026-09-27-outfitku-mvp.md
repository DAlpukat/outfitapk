# Plan â€” OutfitKu MVP (dari prd.md)

Sumber: `prd.md` v1.1. Scope: semua P0 â€” F1â€“F4, F6â€“F8, F14â€“F16 + onboarding + 4 tab.
Eksekusi inline (executing-plans), checkpoint per task.

## Environment (verified)

- JDK 17, Gradle 8.7 (`gradle.bat` di PATH), Android SDK: platform android-34, build-tools 34.0.0
- Stack (dipilih, PRD Â§14): Kotlin 1.9.24 + XML Views + ViewBinding, Room 2.6.1 (KSP), Coil 2.6.0, Material
- AGP 8.5.2, KSP 1.9.24-1.0.20, minSdk 26 / target+compile 34

## Struktur file (final, satu module `app`)

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew*
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/outfitku/app/
  data/Entities.kt        # Item, OutfitLog, WashEvent, DailyState, enums
  data/Daos.kt            # semua DAO
  data/AppDatabase.kt     # Room DB + singleton
  data/Repo.kt            # satu-satunya akses data + Settings (SharedPreferences)
  logic/Engine.kt         # auto-wear/trigger/stok â€” PURE, dipakai unit test
  logic/EngineTest.kt     # unit test matriks edge case PRD Â§9
  notif/Channels.kt       # 4 kanal + buat notif helper
  notif/Scheduler.kt      # exact alarm (setAlarmClock), re-schedule, catch-up
  notif/Alarms.kt         # receiver: AUTO_WEAR, LAUNDRY, ROLLOVER, AUTO_CONFIRM + aksi notif + BOOT
  ui/MainActivity.kt      # bottom nav 4 tab
  ui/TodayFragment.kt     # Beranda (F1, F2, ringkasan stok, nyuci cepat)
  ui/LemariFragment.kt    # F14: daftar per kategori, badge, multi-select cuci/pakai, batch add
  ui/KalenderFragment.kt  # F3: kalender + detail hari + koreksi hari ini
  ui/PengingatFragment.kt # F6/F8/F16 setting + health check + tes notif
  ui/OnboardingActivity.kt# 4 langkah (Â§11)
  ui/LogAdapter.kt, ItemAdapter.kt, DayAdapter.kt  # adapter RecyclerView tipis
app/src/main/res/         # layout fragment/item, values (strings id, colors, themes), menu, drawable icon
```

Deviasi sadar (dicatat, bukan lupa):
- Settings (cut-off, hari kuliah, auto-confirm delay) digabung ke tab Pengingat (Â§12 "gerigi" dipecah jadi section) â€” hemat 1 activity tanpa hilangkan fungsi.
- Foto: Photo Picker saja (tanpa intent kamera) â†’ tanpa izin CAMERA.
- F5/F9/F10/F11/F12/F13 (P1/P2) tidak dibangun.

## Task (checkbox saat eksekusi)

- [x] T1 Scaffold Gradle + Manifest kosong â†’ `gradlew assembleDebug` sukses
- [x] T2 Data layer: entities, DAOs, Room, Repo + Settings prefs
- [x] T3 Engine murni (stok, trigger, auto-wear decision, revert-auto, rollover) + unit test matriks edge Â§9 â†’ `gradlew test` sukses
- [x] T4 Notifikasi: kanal, Scheduler exact alarm, receivers (auto-wear 12:00, auto-confirm 14:00, rollover 00:00, laundry 20:00), boot, aksi notif (Sudah Nyuci/Besok/Libur/Koreksi/Ya)
- [x] T5 Onboarding 4 langkah + POST_NOTIFICATIONS
- [x] T6 UI: MainActivity + 4 fragment + adapter + layout (Beranda, Lemari, Kalender, Pengingat)
- [x] T7 Foto opsional: Photo Picker â†’ WebP â‰¤1080px/â‰¤150KB, Coil
- [x] T8 Health check (izin status, tes notif, checklist OEM)
- [x] T9 Full build `gradlew assembleDebug test` â†’ ukuran APK, laporkan

## Aturan lolos (dari PRD)

- Matriks edge case Â§9 (8 baris) â†’ unit test di `EngineTest.kt` wajib hijau.
- Notif: channel HIGH, exact alarm `setAlarmClock`, reschedule saat boot + app open, `canScheduleExactAlarms()` dicek.
- Data 100% lokal, tanpa backend.
- APK debug dibangun; ukuran dicatat (target rilis <10 MB dengan R8 â€” R8 rilis di luar cakupan sesi ini kecuali sisa waktu).

