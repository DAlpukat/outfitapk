# Implementation Plan — OutfitKu MVP v1.0

| | |
|---|---|
| Spec | `prd.md` (PRD v1.1 Final) |
| Scope | MVP v1.0 = semua P0: F1–F4, F6–F8, F14–F16 |
| Deferred | F5, F9, F10, F11 (P1); F12, F13 (P2) — lihat PRD §16 |
| Repo state | Belum git, hanya `prd.md` + `AGENTS.md` |
| Machine | Windows, **belum ada JDK/Gradle/Android SDK** → Task 0 wajib |

**Default keputusan untuk open questions PRD §18** (di-revisit saat rilis):
1. Distribusi: APK langsung (sideload) → manifest boleh pakai `USE_EXACT_ALARM`.
2. Bahasa: Indonesia saja (satu set string resources).

---

## Konvensi

- Package: `com.outfitku.app`, satu module `:app`. Kotlin + XML Views + ViewBinding (PRD §14).
- Identitas kode English, teks UI Indonesia (`strings.xml`).
- **DI: manual** (`AppContainer` di `Application`). Tanpa Hilt/Koin — YAGNI.
- **Settings: `SharedPreferences`** langsung via `SettingsRepo` kecil. Tanpa DataStore — YAGNI.
- Git: init di Task 0, **commit setelah tiap task**.
- Verify tiap task: `.\gradlew testDebugUnitTest assembleDebug` (harus hijau sebelum lanjut).
- Test: TDD untuk logika domain (unit test JVM). UI = verifikasi manual via checklist PRD §9 (butuh device/emulator — tidak wajib hijau di mesin ini).
- UI test / Robolectric: **tidak dipakai** (tambah dep besar, APK tidak terpengaruh, PRD tidak minta).

### Peta dependency (app/build.gradle.kts)

```kotlin
plugins { id("com.android.application"); id("kotlin-android"); id("kotlin-kapt") }
android {
    namespace = "com.outfitku.app"; compileSdk = 34
    defaultConfig { minSdk = 26; targetSdk = 34; vectorDrawables.useSupportLibrary = true }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(...) } }
    buildFeatures { viewBinding = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("io.coil-kt:coil:2.6.0")
    testImplementation("junit:junit:4.13.2")
}
```

Root: AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7 wrapper, JDK 17.

---

## Task 0 — Toolchain + scaffold proyek

**Goal:** `.\gradlew assembleDebug` menghasilkan APK debug pertama.

**Setup (sekali saja):**
1. `winget install --id Microsoft.OpenJDK.17` → set `JAVA_HOME` ke `C:\Program Files\Microsoft\jdk-17*` (restart terminal).
2. Android SDK cmdline-tools: unduh `commandlinetools-win-*.zip` dari `https://developer.android.com/studio#command-line-tools-only`, ekstrak ke `$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest`, lalu:
   ```
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   sdkmanager --licenses
   ```
3. `winget install --id Gradle.Gradle` → pakai sekali untuk wrapper.

**Scaffold:**
1. `git init`, `.gitignore` (Android standard + `local.properties`).
2. `gradle wrapper --gradle-version 8.7` → commit `gradlew*`, `gradle/wrapper/*`.
3. Buat file: `settings.gradle.kts` (include `:app`, pluginManagement google/mavenCentral), `build.gradle.kts` root, `app/build.gradle.kts` (peta dependency di atas), `local.properties` (`sdk.dir`), `gradle.properties` (`android.useAndroidX=true`, `org.gradle.jvmargs=-Xmx2g`).
4. Minimal app: `AndroidManifest.xml` (permission `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`; `application` class `OutfitKuApp`), `MainActivity` kosong + layout, `strings.xml`, launcher icon (pakai `@mipmap/ic_launcher` default template Android Studio — kalau template tidak ada, generate icon sederhana via adaptive-icon XML).
5. Manifest tambahan: `android:usesCleartextTraffic="false"` tidak perlu (offline).

**Verify:** `.\gradlew assembleDebug` sukses; `.\gradlew testDebugUnitTest` sukses (0 test ok).
**Commit:** `chore: init android project (task 0)`

---

## Task 1 — Data layer (Room)

**Goal:** semua entitas PRD §15 + DAO tersimpan dan bisa dibaca.

**Files:**
- `app/src/main/java/com/outfitku/app/data/Entity.kt` — `Item`, `OutfitLog`, `WashEvent`, `DailyState`
- `app/src/main/java/com/outfitku/app/data/Converters.kt` — `List<Long>` ↔ `String` (comma), `LocalDate`/`Instant` ↔ `Long`
- `app/src/main/java/com/outfitku/app/data/OutfitKuDatabase.kt` (version 1, `exportSchema=false`)
- `app/src/main/java/com/outfitku/app/data/Daos.kt` — `ItemDao`, `OutfitLogDao`, `WashEventDao`, `DailyStateDao`
- `app/src/main/java/com/outfitku/app/data/SettingsRepo.kt` — SharedPreferences wrapper
- `app/src/main/java/com/outfitku/app/OutfitKuApp.kt` — `AppContainer` (lazy singletons: db, settingsRepo, repo, scheduler)

**Skema (ikuti PRD §15 persis + 1 kolom tambahan `Item.createdAt` — dibutuhkan edge case "item baru siang tidak ikut auto"):**

```kotlin
@Entity data class Item(
  @PrimaryKey(autoGenerate=true) val id:Long=0,
  val nama:String, val kategori:String,          // atasan|bawahan|outer|sepatu|aksesori
  val status:String,                              // BERSIH|DIPAKAI|KOTOR
  val wearSinceWash:Int=0,
  val lastWorn:Long?=null, val lastWashed:Long?=null,
  val foto:String?=null,                          // path relatif file
  val aktif:Boolean=true,
  val createdAt:Long=System.currentTimeMillis()
)
@Entity data class OutfitLog(
  @PrimaryKey(autoGenerate=true) val id:Long=0,
  val tanggal:String,                             // ISO yyyy-MM-dd
  val itemIds:String, val foto:String?=null,
  val catatan:String?=null, val sumber:String,    // MANUAL|AUTO
  val tag:String?=null                            // Kuliah|UKM|Event|Hujan|Santai (F1)
)
@Entity data class WashEvent(
  @PrimaryKey(autoGenerate=true) val id:Long=0,
  val tanggal:String, val itemIds:String
)
@Entity data class DailyState(
  @PrimaryKey val tanggal:String,
  val isLibur:Boolean=false, val autoConfirmedAt:Long?=null,
  val countedToday:Boolean=false, val autoDone:Boolean=false
)
```

`ItemDao` kunci: `@Query("SELECT * FROM Item WHERE aktif=1 AND kategori=:k ORDER BY id")`, `SELECT * FROM Item WHERE aktif=1 AND status='BERSIH'`, `UPDATE Item SET aktif=0 WHERE id IN (:ids)` (soft-delete), `SELECT COUNT(*) ... `.

`SettingsRepo` keys: `cutoff` (Int menit, default 720), `hariKuliah` (String "1111100" default Sen–Jum), `reminderTime` (Int menit, default 1200), `thrAtasan..thrAksesori` (Int, default 2), `nagInterval` (Int hari, 0=tiap hari), `nagMaxDays` (Int, 0=terus — "berhenti setelah N hari", F16), `nagStreak`, `nagLastDate`, `quietStart`(1320)/`quietEnd`(480), `autoConfirmDelay` (menit, default 120), `onboardingDone` (Boolean).

**Test dulu (TDD):** `app/src/test/java/com/outfitku/app/data/ConvertersTest.kt` — round-trip `List<Long>`, tanggal ISO. Green sebelum implement.

**Verify:** `.\gradlew testDebugUnitTest assembleDebug`.
**Commit:** `feat: room entities, daos, settings repo (task 1)`

---

## Task 2 — Domain engine murni + test edge matrix

**Goal:** seluruh logika PRD §9 matriks edge case jadi fungsi murni + unit test hijau. **Tanpa Android API** (testable JVM).

**Files (baru):** `app/src/main/java/com/outfitku/app/domain/`:
- `Stock.kt` — `fun stokPerKategori(items:List<Item>):Map<String,Int>` (hitung status BERSIH saja, `aktif`)
- `Rollover.kt` — `fun rollover(items:List<Item>, now:Long):List<ItemUpdate>` — DIPAKAI→KOTOR, `lastWorn` di-set (dipanggil receiver jam 00:00)
- `AutoWear.kt` — keputusan jam 12:
  ```kotlin
  fun shouldAutoWear(state:DailyState?, hariKuliah:Boolean, now:LocalDateTime, cutoff:LocalDateTime):Boolean
  // false jika: state.countedToday (manual 11:59 → skip), autoDone, bukan hari kuliah, sudah lewat 23:59
  fun autoCandidates(items:List<Item>, scheduledAt:Long, now:Long):List<Item>
  // status==BERSIH && aktif && createdAt<=scheduledAt   ← edge "item baru siang" lolos
  fun applyAutoWear(items, candidateIds):List<ItemUpdate>
  // DIPAKAI, sumber=AUTO; item auto NON-terpilih yang berstatus DIPAKAI-dari-auto → kembali BERSIH
  fun refundLibur(state, items, autoIds):…  // 🏖 Libur → stok utuh, isLibur=true
  fun manualOverrideNow(state, items):…     // user catat manual jam 15 setelah auto → tidak dobel,
  //   item auto yang tak terpakai kembali BERSIH (flag sumber per OutfitLog)
  ```
- `Nag.kt` — `fun shouldNag(stokPerKat, items, threshold, quietNow):NagDecision?` — trigger stok kategori ≤ threshold **ATAU** item `wearSinceWash>=2`; level eskalasi = `nagStreak`; pesan menyebut item spesifik.
- `CatchUp.kt` — `fun catchUp(state:DailyState?, now:Long, scheduledAt:Long):Boolean` — true hanya jika `!autoDone && !countedToday && now<=hariYangSama 23:59` (idempoten: flag `autoDone` diset begitu dieksekusi).

**Tests (tulis dulu, lihat baris mana yang dipakai):** `app/src/test/java/com/outfitku/app/domain/`
`AutoWearTest.kt`, `NagTest.kt`, `RolloverTest.kt`, `StockTest.kt`, `CatchUpTest.kt`.

Peta matriks PRD §9 → test:

| Kasus | Test |
|---|---|
| Auto 12 → manual 15: tidak dobel, auto tak terpakai → BERSIH | `AutoWearTest.manualAfterAutoNoDoubleCount` |
| Auto 12 → 🏖 Libur: stok utuh, hari libur | `AutoWearTest.liburRefund` |
| Notif diabaikan 2 jam → auto-confirm `sumber=AUTO` | `AutoWearTest.autoConfirmAfterDelay` (helper `isConfirmExpired(issuedAt, now, delay=120m)`) |
| HP mati 11:00–16:00 → catch-up 1× tidak dobel | `CatchUpTest.*` |
| Centang 11:59 → engine 12:00 skip | `AutoWearTest.manualAt1159Skip` |
| Item baru 13:00 tidak ikut auto | `AutoWearTest.newItemAfterCutoffExcluded` |
| Item dihapus saat KOTOR → soft-delete | `SoftDelete` tes via `aktif=false` pada test repo (Task 3) |
| "Sudah dicuci" → nag berhenti, counter reset, `WashEvent` | `WashTest` (Task 8, fungsi `tandaiDicuci`) |

**Verify:** `.\gradlew testDebugUnitTest`.
**Commit:** `feat: domain engines with edge-case tests (task 2)`

---

## Task 3 — Layar Lemari (F14) + Cuci per item (F4)

**Goal:** inventaris per kategori, badge status, batch add, multi-select cuci/pakai, soft-delete.

**Files:**
- `data/WardrobeRepo.kt` — CRUD (tambah 1/batch "+5 Kaos" → nama generik `Kaos 1..5`, `tandaiDicuci(ids):List<WashEvent>` (reset `wearSinceWash=0`, status BERSIH, simpan `WashEvent`, reset `nagStreak`), `hapus(ids)` = soft-delete, `pakai(ids)` = DIPAKAI)
- `ui/lemari/LemariFragment.kt` + `fragment_lemari.xml` + `ItemAdapter.kt` (RecyclerView, checkbox mode multi-select, badge `⚠️ n× pakai` jika `wearSinceWash>=2`, status dot)
- `ui/lemari/TambahItemDialog.kt` (batch: jumlah + kategori + prefix nama; foto opsional)
- Navigation: `MainActivity` + `activity_main.xml` → `BottomNavigationView` (Beranda/Lemari/Kalender/Pengingat), 4 Fragment, `menu/bottom_nav.xml`
- `MainActivity` gerigi toolbar → `SettingsActivity` (Task 10; stub dulu)

**Test:** tidak ada — repo tipis di atas domain yang sudah teruji (Task 2). Verifikasi manual:

**Manual check:** tambah batch 5 → muncul; multi-select 2 kotor → "Tandai dicuci" → status BERSIH + badge hilang; hapus item → riwayat kalender lama tetap ada.

> Catatan konflik internal PRD: §7 menaruh "backup file lokal" di in-scope v1.0, tapi F11 = P1/roadmap v1.1. Plan mengikuti roadmap (stub di Task 10) — resolusi: fitur backup mundur ke v1.1.

**Commit:** `feat: wardrobe screen + wash flow (task 3)`

---

## Task 4 — Beranda: Catat Outfit (F1) + Quick Reuse (F2)

**Goal:** ≤ 3 tap: centang chip → selesai. Foto opsional, tag, catatan.

**Files:**
- `ui/beranda/BerandaFragment.kt` + layout — kartu "Hari Ini": chip group item BERSIH (group per kategori), tombol `⟳ Ulang terakhir`, ringkasan stok (`Kaos: 3 · Kemeja: 1`), tombol "🧺 Nyuci cepat" → jumpa Lemari multi-select
- `OutfitRepo.kt` — `simpanHariIni(itemIds, foto, tag, catatan, sumber)`: upsert `OutfitLog` per tanggal; item tercentang → DIPAKAI + `wearSinceWash+1` + `lastWorn`; `DailyState.countedToday=true`
- Foto: `registerForActivityResult(PickVisualMedia)` (Photo Picker, tanpa izin) → kompres: skala sisi terpanjang 1080px, loop quality 85→40 sampai ≤150 KB, simpan WebP di `filesDir/photos/<ts>.webp` (helper `ImageStore.kt`, Coil untuk render)
- Tag: `Kuliah|UKM|Event|Hujan|Santai` (chip horizontal), catatan `EditText` ringkas — semua collapsible (default tersembunyi, tetap ≤3 tap)
- Quick Reuse: ambil `OutfitLog` terakhir → isi checkbox-nya → simpan (bisa diedit sebelum simpan)

**Test:** fungsi kompres + path naming = unit test `ImageStoreTest` (bitmap dari `Bitmap.createBitmap` di JVM? — Bitmap tidak ada di JVM → **skip test, verifikasi manual**; kompres adalah kode tipis).

**Manual check:** 3 tap tercatat; ulang terakhir 1 tap; data muncul di Kalender (Task 5).

**Commit:** `feat: home outfit logging + quick reuse (task 4)`

---

## Task 5 — Kalender Riwayat (F3)

**Goal:** bulanan + thumbnail, tap → detail, koreksi hanya hari ini.

**Files:**
- `ui/kalender/KalenderFragment.kt` — `CalendarView` native (bulan) + RecyclerView daftar hari berisi thumbnail outfit; tap tanggal → `DetailHariActivity` (daftar item + foto + tag); tombol koreksi hanya jika tanggal = hari ini (buka ulang checklist Beranda)

**Commit:** `feat: calendar history (task 5)`

---

## Task 6 — Layer scheduling anti-telat (PRD §10)

**Goal:** 3 exact alarm hidup, channel notif siap, selamat reboot.

**Files:**
- `notif/Channels.kt` — buat 4 channel PRD §10.3 (`laundry_reminder` HIGH, `outfit_confirm` HIGH, `outfit_log` DEFAULT, `test` HIGH) — dipanggil saat app start
- `sched/AlarmScheduler.kt`:
  ```kotlin
  fun schedule(kind:Kind, at:LocalDateTime)   // AlarmManager.setAlarmClock(RTC_WAKEUP, pi)
  fun scheduleConfirm(at:Long)                // alarm sementara ke-4 (issuedAt+2h), Task 7
  fun cancelConfirm()                         // saat user jawab notif auto-wear
  fun canExact():Boolean                      // SDK>=31 → canScheduleExactAlarms()
  fun openExactSettings(ctx)                  // ACTION_REQUEST_SCHEDULE_EXACT_ALARM
  fun rescheduleAll()                         // 12:00, 20:00, 00:00 berikutnya — idempoten
  // fallback: !canExact() → setExactAndAllowWhileIdle
  // total alarm harian tetap 3 (PRD §15); confirm hanya hidup saat ada pending auto-wear
  ```
- `sched/AlarmReceiver.kt` (abstract) + `AutoWearReceiver`, `RolloverReceiver`, `NagReceiver` — tiap receiver: **kerja → `scheduler.rescheduleAll()` (self-healing) → selesai**
- `sched/BootReceiver.kt` — `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` → `rescheduleAll()` + catch-up (jalan≤23:59)
- `ui/MainActivity.onCreate` → `rescheduleAll()` + `canExact()` check (kalau false → banner arahkan settings)
- Manifest: semua receiver + intent-filter; izin `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` + `RECEIVE_BOOT_COMPLETED` + `POST_NOTIFICATIONS`

**Test:** logika "alarm berikutnya kapan" → fungsi murni `NextAlarmTime.kt` (`nextOccurrence(hour, minute, now)`) + `NextAlarmTimeTest` (lewat jam → besok, sebelum → hari ini). Sisanya manual.

**Commit:** `feat: exact alarm scheduling + boot receiver (task 6)`

---

## Task 7 — Auto-Wear Engine (F15) + konfirmasi (D4)

**Goal:** flow siang PRD §6.1 berjalan penuh.

**Wiring:**
- `AutoWearReceiver.onReceive` → engine `AutoWear.kt` (Task 2):
  1. `DailyState` hari ini `countedToday`? → skip. Bukan hari kuliah / weekend? → skip.
  2. Kandidat = autoCandidates → kosong? → skip.
  3. Notif `outfit_confirm`: "Belum catat outfit hari ini. Anggap pakai: X + Y?" aksi `[✅ Ya] [✏️ Koreksi] [🏖 Libur]` — simpan `pendingAutoIds` (SharedPreferences) + `issuedAt`.
  4. **Auto-confirm 2 jam (D4):** saat notif diterbitkan, `AlarmScheduler.scheduleConfirm(issuedAt + autoConfirmDelay)` → `ConfirmReceiver`: `pendingAutoIds` masih ada & belum dijawab → terapkan `sumber=AUTO`, `DailyState.autoDone=true`, hapus pending. Dibatalkan begitu user menjawab (Ya/Koreksi/Libur). Bukan bagian `rescheduleAll()` — hanya hidup saat ada pending.
  5. `[🏖 Libur]` → `refundLibur`. `[✏️ Koreksi]` → buka Beranda (edit manual, `manualOverrideNow` — tidak dobel).
  6. Catch-up: `CatchUp.kt` saat boot/app-open/alarm terlewat, ≤23:59, 1× (flag `autoDone`).
- `NagReceiver` jam 20:00 **sebelum** cek: `countedToday`? kalau belum & hari kuliah → kirim pengingat `outfit_log` "Catat outfit hari ini 👕"? → **TIDAK** (PRD: cut-off 12 sudah ganda, F9 P1 off default). Lanjut ke laundry reminder (Task 8).

**Manual check:** semua baris matriks §9 yang terkait auto (ubah jam device / `adb shell am broadcast` untuk test receiver).

**Commit:** `feat: auto-wear engine + confirm flow (task 7)`

---

## Task 8 — Pengingat Cuci Smart (F6) + Nag Mode (F16) + Aksi Notif (F7)

**Goal:** notif malam 20:00, berulang sampai dicuci, aksi dari notification.

**Files:**
- `NagReceiver` (dari Task 6): `Nag.shouldNag(...)` true? → level = `nagStreak+1` → notif `laundry_reminder`, judul eskalasi ("🧺 Kaos tinggal 1" → "Masih belum nyuci? Tinggal 1!"), isi sebut item spesifik. Simpan `nagStreak++`, `nagLastDate`. Quiet hours (`quietStart`–`quietEnd`, default 22:00–08:00) → tunda ke 08:00 via `AlarmScheduler`. `nagInterval>0` → lompat hari sesuai interval; `0` → tiap hari; `nagMaxDays>0 && nagStreak>=nagMaxDays` → nag berhenti otomatis (default `0` = terus).
- `notif/Actions.kt` + `ActionReceiver.kt` — 4 aksi (PRD F7):
  - `SUDAH_NUCI` → `WardrobeRepo.tandaiDicuci(kotorIds)` → notif cancel, `nagStreak=0`
  - `BESOK` → `nagStreak` tetap, jadwalkan ulang besok
  - `LIBUR` (dari outfit_confirm) → lihat Task 7
  - `KOREKSI` → open Beranda
  - Semua `PendingIntent.getBroadcast` + `FLAG_IMMUTABLE`, `setAutoCancel(false)` untuk aksi utama
- **Nag berhenti HANYA** via `SUDAH_NUCI`/item dinonaktifkan — jangan auto-cancel.

**Test dulu:** `NagTest.kt` sudah di Task 2 — tambah kasus eskalasi judul, interval, quiet-now → tunda. Green.

**Commit:** `feat: smart laundry reminder + nag mode + notif actions (task 8)`

---

## Task 9 — Onboarding 4 langkah (PRD §11)

**Goal:** < 2 menit, hari pertama app langsung jalan.

**Files:**
- `ui/onboarding/OnboardingActivity.kt` — 4 step (ViewPager2 atau sederhana: satu Activity + 4 View berikutnya/sebelumnya, `isOnboardingDone` di SettingsRepo):
  1. Welcome + `POST_NOTIFICATIONS` (API 33+) dengan penjelasan konteks
  2. "Isi lemari 30 detik": stepper kategori, preset (Kaos 5 · Kemeja 2 · Celana 3 · Outer 1 · Sepatu 2) → batch add generik; Next boleh langsung
  3. Jadwal kuliah: 7 `Switch`, default Sen–Jum
  4. Jam pengingat (default 20:00, `TimePicker`) → tombol "Kirim Notif Tes" → Selesai → `rescheduleAll()`
- `OutfitKuApp.onCreate`: `onboardingDone`? MainActivity : OnboardingActivity

**Commit:** `feat: onboarding flow (task 9)`

---

## Task 10 — Tab Pengingat (Health Check F8) + Settings

**Goal:** PRD §10.2 poin 9: Health Check = fitur inti.

**Files:**
- `ui/pengingat/PengingatFragment.kt` — status izin (notifikasi, `canScheduleExactAlarms()`), tombol "Buka pengaturan exact alarm" / "Izinkan notifikasi", **"Kirim Tes Sekarang"** (channel `test`), deteksi OEM (`Build.MANUFACTURER`: Xiaomi/OPPO/Vivo/Samsung/Huawei) + checklist per-OEM (Autostart MIUI, dll.), threshold stok per kategori (Seek/Number), jam reminder, aturan nag (interval / berhenti setelah N), quiet hours
- `ui/settings/SettingsActivity.kt` — cut-off 10:00–15:00 (default 12:00), saklar hari kuliah, auto-confirm delay, backup/restore **stub disabled** (F11 P1 — tombol nonaktif + teks "segera")

**Commit:** `feat: reminder tab, health check, settings (task 10)`

---

## Task 11 — MVP QA + ukuran APK + polish

**Checklist wajib (PRD §3, §9, §13):**
- [ ] Semua baris matriks edge case §9 terpenuhi (manual di device/emulator; logika sudah teruji unit test)
- [ ] `.\gradlew assembleRelease` + R8 → ukuran APK `< 10 MB` (ideal < 6 MB). Kalau lewat: cek `shrinkResources`, hapus lib tak terpakai
- [ ] Cold start dicek, tidak ada jank kasar
- [ ] Notif tes ± 1 menit masuk (channel HIGH, heads-up)
- [ ] Reboot → alarm hidup lagi; kill app dari recent → alarm tetap jalan
- [ ] Tidak ada empty state tanpa CTA (PRD §12)
- [ ] Ikon app + splash default rapi, semua teks Indonesia
- [ ] `.\gradlew testDebugUnitTest` tetap hijau

**Commit:** `chore: mvp qa + release shrink (task 11)`

---

## Peta risiko → mitigasi (dari PRD §17, relevan eksekusi)

| Risiko | Di task |
|---|---|
| Exact alarm butuh izin user (API 31+) | Task 6: `canExact()` banner + fallback `setExactAndAllowWhileIdle` |
| Bug dobel-hitung | Task 2 unit test matriks §9 — blokir sebelum UI ada |
| OEM blokir notif | Task 10 Health Check — fitur inti, bukan pelengkap |
| APK bengkak | Task 0 pakai minify dari awal; Task 11 verify ukuran |
| Belum ada toolchain | Task 0 — blokir paling awal |

## Yang SENGaja TIDAK dibuat (YAGNI)

- F5, F9, F10, F11 (P1), F12, F13 (P2) — roadmap §16.
- Hilt/Koin, DataStore, Compose, WorkManager primer, backend, telemetri.
- Robolectric/UI test otomatis (PRD tidak minta; manual checklist cukup untuk MVP).
- Database migration path (v1 = fresh install).
- Intent kamera (FileProvider + izin) — F1 menyebut "kamera/Photo Picker"; MVP pakai Photo Picker saja (tanpa izin, kode jauh lebih sedikit). Tambah kamera hanya kalau user minta.
