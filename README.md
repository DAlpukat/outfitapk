# OutfitKu

Aplikasi Android **offline-first, tanpa akun, < 10 MB** untuk mahasiswa: catat outfit harian dengan ≤ 3 tap dan dapat pengingat nyuci yang **tidak telat** — memakai `AlarmManager.setAlarmClock()` setingkat alarm, bukan push notification biasa.

## Fitur (MVP v1.0)

| | |
|---|---|
| **Catat outfit** | Centang baju dari lemari digital, foto opsional (WebP ≤1080px), tag + catatan |
| **Quick Reuse** | "⟳ Ulang outfit terakhir" — 1 tap |
| **Lemari** | Status per item (Bersih/Dipakai/Kotor), badge ⚠️ 2× pakai, batch add, multi-select cuci |
| **Auto-Wear** | Lupa catat? Jam 12:00 app tawarkan outfit kemarin, diabaikan 2 jam → dianggap "Ya", bisa dikoreksi |
| **Pengingat cuci** | Trigger stok kategori ≤ threshold **atau** item ≥ 2× pakai; **Nag Mode** berulang harian sampai dicuci |
| **Anti-telat** | Exact alarm kebal Doze, re-schedule saat boot + buka app, Health Check + panduan OEM (Xiaomi/OPPO/Vivo/…) |
| **Aksi dari notif** | ✅ Sudah Nyuci · ⏰ Besok · 🏖 Libur · ✏️ Koreksi — tanpa buka app |
| **Kalender** | Riwayat bulanan, koreksi hari ini |

Data 100% lokal (Room), tanpa backend, tanpa telemetri.

## Build

```bash
# butuh JDK 17 + Android SDK (platform 34)
gradle assembleDebug          # atau ./gradlew assembleDebug
gradle :app:testDebugUnitTest # unit test logika (matriks edge case)
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (arsip rilis ada di `version/`).

## Struktur

```
app/src/main/java/com/outfitku/app/
  data/    # Room entities, DAO, Repo + settings
  logic/   # Engine murni (auto-wear, stok, trigger) — diuji unit test
  notif/   # Kanal, exact alarm scheduler, receivers, aksi notif
  ui/      # Onboarding + 4 tab (Beranda, Lemari, Kalender, Pengingat)
```

Spesifikasi lengkap: [`prd.md`](prd.md). Rencana implementasi: [`docs/superpowers/plans/`](docs/superpowers/plans/).

## Stack

Kotlin · XML Views + ViewBinding · Room · Coil · Material 3 · minSdk 26 / target 34
