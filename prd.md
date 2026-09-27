
---

# PRD — OutfitKu v1.1 (Final untuk Development)

| | |
|---|---|
| **Produk** | OutfitKu — catat outfit harian + pengingat cuci baju |
| **Platform** | Android (APK native) |
| **Versi dokumen** | 1.1 — Final |
| **Status** | Ready for Development |
| **Target rilis MVP** | 8–10 minggu |

**Changelog v1.0 → v1.1**
- + F14 Lemari & checklist item (status baju per item)
- + F15 Auto-Wear Engine (cut-off jam 12, hybrid manual/auto)
- + F16 Nag Mode (notif berulang sampai dicuci)
- Revisi F1 (catat outfit berbasis lemari), F4 (cuci per item), F6 (notif berbasis stok kategori)
- Keputusan desain terkunci: D1–D3 (bagian 5)

---

## 1. Ringkasan Eksekutif

OutfitKu adalah aplikasi Android **super ringan (< 10 MB), offline-first, tanpa akun** untuk mahasiswa:

1. **Mencatat outfit harian** dengan cara mencentang baju dari "lemari" digital — proses ≤ 3 tap.
2. **Melacak status baju** (bersih/dipakai/kotor) per item, sehingga app tahu sisa stok baju bersih.
3. **Mengingatkan waktu nyuci** lewat notifikasi setingkat alarm, **berulang sampai ditandai sudah dicuci** — lebih andal daripada notifikasi aplikasi bank (Livin dkk) karena memakai exact alarm lokal, bukan push notification biasa.

---

## 2. Latar Belakang & Masalah

| # | Pain Point | Dampak |
|---|---|---|
| 1 | Lupa outfit apa yang dipakai minggu lalu | Malu repeated outfit, susah padu padanka |
| 2 | Lupa nyuci sampai baju bersih habis | Panik pagi hari sebelum kuliah |
| 3 | Notifikasi reminder sering telat berjam-jam | Reminder tidak berguna. Penyebab: FCM normal priority + Doze mode + battery optimizer OEM (Xiaomi/OPPO/Vivo paling agresif) |
| 4 | Melacak "sudah nyuci belum" dilakukan di kepala | Stok baju tidak akurat, kejutan saat pagi H |

**Insight teknis:** WA andal karena FCM *high priority* dari server. Untuk reminder terjadwal kita tidak butuh server — `AlarmManager.setAlarmClock()` diperlakukan OS seperti alarm: kebal Doze, tampil ikon alarm di status bar, presisi tinggi. Reliability bisa melampaui notifikasi ala Livin **tanpa backend sama sekali**.

---

## 3. Tujuan & Metrik Sukses

| Goal | Metrik | Target |
|---|---|---|
| Logging jadi kebiasaan | % hari kuliah dengan catatan outfit (manual/auto) | ≥ 90% |
| Logging cepat | Median waktu catat 1 outfit | < 30 detik (≤ 3 tap) |
| Notif tepat waktu | Reminder masuk ± 1 menit dari jadwal | ≥ 99% |
| Stok akurat | Selisih stok app vs realita saat user cek manual | Tidak pernah over-report baju bersih |
| Ringan | Ukuran APK | < 10 MB (ideal < 6 MB) |
| Retensi | Retensi D30 | ≥ 40% |

---

## 4. Persona

**"Raka", 20 tahun, mahasiswa semester 4.**
HP Android menengah-bawah (RAM 3–4 GB, Android 10–13), storage sering penuh → alergi app berat. Kuliah Sen–Jum, rotasi 2–3 set baju favorit. Jarang buka app; butuh notifikasi yang **benar-benar muncul jam 9 malam**. Mau catat cepat atau tidak sama sekali — app harus tetap pintar.

---

## 5. Keputusan Desain Terkunci

| ID | Keputusan | Rationale |
|---|---|---|
| **D1** | Hari kuliah = **toggle per-hari** (7 saklar), default **Sen–Jum** | Default masuk akal + fleksibel untuk jadwal unik |
| **D2** | Cut-off Auto-Wear default **12:00**, bisa diubah 10:00–15:00 | Mahasiswa umumnya sudah berangkat sebelum itu |
| **D3** | **Checklist item saja** — tanpa mode sederhana/counter global | Satu sumber kebenaran; kesederhanaan dijamin lewat preset onboarding, bukan mode kedua |
| D4 | Auto-wear memakai **Opsi A: notif konfirmasi**, dianggap "Ya" jika diabaikan 2 jam | Otomatis tapi masih bisa dikoreksi 1 tap |
| D5 | Tanpa akun, tanpa server, tanpa telemetri (v1) | Privasi + ukuran + kesederhanaan |

---

## 6. Konsep Inti & Flow Harian

**Satu kalimat:** centang baju yang dipakai pagi ini → jadi kotor malamnya → app hitung sisa baju bersih → mengingatkan nyuci dengan notif setingkat alarm, berulang sampai dicentang "sudah dicuci".

### 6.1 Alur Harian

```
┌─────────────────────────────────────────────────────────────────┐
│ PAGI (kapan pun sebelum jam 12)                                 │
│  ▸ Beranda tampil chip item BERSIH → centang yang dipakai       │
│  ▸ (Opsional) foto + tag. Total ≤ 3 tap.                        │
│  ▸ Tombol "⟳ Ulang outfit terakhir" = 1 tap.                    │
├─────────────────────────────────────────────────────────────────┤
│ SIANG — 12:00 (Auto-Wear Engine, F15)                           │
│  ▸ Sudah dicatat manual?  → SKIP (manual = sumber kebenaran)    │
│  ▸ Belum + hari kuliah (sesuai D1)?                             │
│     Notif: "Belum catat outfit hari ini.                        │
│             Anggap pakai: 👕 Kaos Hitam + 👖 Jeans Biru?"       │
│        [✅ Ya] [✏️ Koreksi] [🏖 Libur]                           │
│     • Diabaikan 2 jam → otomatis dianggap "Ya"                  │
│     • 🏖 Libur → stok tidak berkurang, hari ditandai libur      │
│  ▸ Weekend / toggle hari kuliah off → SKIP                      │
│  ▸ HP mati jam 12 → catch-up saat nyala (sampai 23:59, 1× saja) │
├─────────────────────────────────────────────────────────────────┤
│ TENGAH MALAM — 00:00 (Rollover)                                 │
│  ▸ Item DIPAKAI hari ini → jadi KOTOR, wearSinceWash +1         │
├─────────────────────────────────────────────────────────────────┤
│ MALAM — 20:00 (Pengingat Cuci + Nag, F16)                       │
│  ▸ Trigger: stok kategori ≤ 2  ATAU  ada item ≥ 2× pakai        │
│    → Notif HIGH: "🧺 Kemeja bersih tinggal 1, kaos tinggal 2"   │
│    [✅ Sudah Nyuci] [⏰ Besok]                                   │
│  ▸ Diabaikan & belum dicuci → notif ulang tiap hari, judul      │
│    naik level: "Masih belum nyuci? Tinggal 1!"                  │
│  ▸ Quiet hours 22:00–08:00 → ditunda ke 08:00                   │
│  ▸ Nag berhenti HANYA jika item ditandai dicuci / dinonaktifkan │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 State Machine per Item

```
BERSIH ──(centang dipakai / auto-wear)──> DIPAKAI ──(rollover 00:00)──> KOTOR
   ▲                                                                      │
   └──────────────(centang "sudah dicuci", counter reset)─────────────────┘
```

Setiap item menyimpan: nama, kategori, status, `wearSinceWash`, `lastWorn`, `lastWashed`.

### 6.3 Anti-Ribet (kunci adopsi)

| Masalah | Solusi UX |
|---|---|
| Setup awal capek | Batch add "+5 Kaos", nama generik (Kaos 1–5), foto opsional, preset onboarding 30 detik |
| Hari nyuci, capek centang satu-satu | Multi-select, item kotor **pre-checked**, tombol "Tandai semua terpilih dicuci" |
| Lupa item mana kotor | Badge "⚠️ 2× pakai" di Lemari; grup per kategori di Beranda |

---

## 7. Ruang Lingkup

**✅ In Scope (v1.0):** lemari item, catat outfit (centang + foto opsional), kalender riwayat, cuci per item, Auto-Wear Engine, pengingat cuci + nag, notifikasi anti-telat + health check, backup file lokal.

**❌ Out of Scope:** akun/cloud sync (v2), rekomendasi outfit AI (v2), integrasi cuaca, fitur sosial, mode sederhana tanpa lemari (ditolak — lihat D3).

---

## 8. User Stories

| ID | Story | Acceptance Criteria |
|---|---|---|
| U1 | Sebagai mahasiswa, saya ingin mencatat outfit ≤ 3 tap, supaya kebiasaan ini bertahan | Centang dari lemari; foto opsional terkompresi; tersimpan offline |
| U2 | Saya ingin isi lemari dalam 30 detik | Preset stepper; bisa langsung Next tanpa detail |
| U3 | Saya ingin lihat outfit tanggal berapa pun | Kalender bulanan + thumbnail; tap → detail |
| U4 | Saya ingin tahu kapan harus nyuci | Notif berbasis stok kategori + nag harian; tombol "✅ Sudah Nyuci" tanpa buka app |
| U5 | Saya lupa catat pagi, tapi app jangan salah hitung | Auto-wear jam 12 dengan konfirmasi; manual menimpa auto tanpa dobel-hitung |
| U6 | Saya ingin notif TIDAK telat seperti app bank | Exact alarm; Health Check + tes notif + panduan OEM |
| U7 | Saya ingin data aman saat ganti HP | Export/import satu file |

---

## 9. Kebutuhan Fungsional

| ID | Fitur | Spesifikasi Kunci | Prio |
|---|---|---|---|
| **F1** | Catat Outfit | Centang item dari lemari; foto opsional (kamera/Photo Picker, WebP ≤ 1080px, 80–150 KB); tag: `Kuliah`, `UKM`, `Event`, `Hujan`, `Santai`; catatan singkat | P0 |
| **F2** | Quick Reuse | "⟳ Ulang outfit terakhir" — 1 tap, bisa edit item | P0 |
| **F3** | Kalender Riwayat | Thumbnail bulanan; detail per hari; koreksi hanya hari ini | P0 |
| **F4** | Cuci per Item | Multi-select (kotor pre-checked) → "Tandai dicuci" → reset counter, simpan `WashEvent` | P0 |
| **F5** | Pengingat Jadwal (opsional) | Reminder tetap tiap N hari di jam pilihan, sebagai cadangan jika stok tidak representatif | P1 |
| **F6** | Pengingat Cuci Smart | Trigger: stok kategori ≤ threshold (default 2) ATAU item ≥ 2× pakai tanpa cuci; pesan menyebut item spesifik | P0 |
| **F7** | Aksi dari Notifikasi | "✅ Sudah Nyuci" (langsung reset via broadcast), "⏰ Besok", "🏖 Libur", "✏️ Koreksi" | P0 |
| **F8** | Notification Health Check | "Kirim Tes Sekarang", deteksi OEM + checklist (Autostart Xiaomi, dll.), status semua izin | P0 |
| **F9** | Reminder Sore (opsional) | Toggle "ingatkan catat outfit sore" — off default (jam 12 sudah berfungsi ganda) | P1 |
| **F10** | Statistik | Item paling/jarang dipakai, streak | P1 |
| **F11** | Backup/Restore | Export/import `.json` + folder foto | P1 |
| **F12** | Widget | Tombol catat cepat + sisa stok | P2 |
| **F13** | Dark Mode | Ikuti sistem | P2 |
| **F14** | Lemari | Inventaris per kategori (atasan/bawahan/outer/sepatu/aksesori); status badge; batch add; soft-delete (riwayat tetap utuh) | P0 |
| **F15** | Auto-Wear Engine | Evaluasi 12:00 sesuai flow 6.1; anti-dobel-hitung (`sumber` + flag harian); catch-up; refund libur | P0 |
| **F16** | Nag Mode | Notif ulang harian sampai dicuci; eskalasi judul; interval & "berhenti setelah N hari" bisa diatur (default: terus); quiet hours | P0 |

### Matriks Edge Case (Acceptance — wajib lolos test)

| Kasus | Hasil yang benar |
|---|---|
| Auto jam 12 → user catat manual jam 15 | Tidak dobel; item auto yang tak terpakai kembali BERSIH |
| Auto jam 12 → user tap 🏖 Libur | Stok utuh, hari ditandai libur |
| Notif jam 12 diabaikan 2 jam | Auto-confirm "Ya", tercatat `sumber=AUTO` |
| HP mati 11:00–16:00 | Catch-up saat nyala, dieksekusi 1×, tidak dobel |
| User centang dipakai jam 11:59 | Jam 12 engine skip |
| Item baru ditambah siang | Terhitung BERSIH, tidak ikut auto kecuali dicentang |
| Item dihapus saat status KOTOR | Soft-delete, riwayat lama utuh |
| User centang "sudah dicuci" | Nag berhenti, counter reset, `WashEvent` tersimpan |

---

## 10. Strategi Notifikasi Anti-Telat ⭐

### 10.1 Kenapa notif Livin/bank sering telat

| Penyebab | Penjelasan |
|---|---|
| FCM normal priority | OS menunda delivery saat device idle/Doze |
| Battery optimizer OEM | MIUI/ColorOS/OriginOS menahan notif sampai app dibuka |
| Battery saver aktif | Background network/job ditunda |

### 10.2 Solusi Berlapis

1. **`AlarmManager.setAlarmClock()`** — bukan WorkManager sebagai primer. Lolos Doze, tampil ikon alarm, presisi detik. WorkManager hanya fallback.
2. **Android 12+**: `SCHEDULE_EXACT_ALARM` → arahkan via `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. **Android 14+**: `USE_EXACT_ALARM` (sah untuk use case alarm/jadwal).
3. **Channel `IMPORTANCE_HIGH`** → heads-up, suara, getar (setara WA).
4. **`RECEIVE_BOOT_COMPLETED`** → re-schedule semua alarm setelah reboot.
5. **`POST_NOTIFICATIONS`** (Android 13+) diminta di onboarding dengan konteks.
6. **Jadwal alarm terdaftar:**

| Waktu | Fungsi | Catatan |
|---|---|---|
| 12:00 (default) | Auto-Wear evaluasi | Skip jika sudah dicatat / bukan hari kuliah |
| 20:00 (default) | Laundry reminder + nag | Ekspor judul sesuai level nag |
| 00:00 | Rollover DIPAKAI → KOTOR | — |
| On boot / on open | Re-schedule + catch-up alarm yang terlewat | Idempoten |

7. **Self-healing**: alarm berikutnya dijadwalkan ulang setiap kali dieksekusi; saat app dibuka, verifikasi `canScheduleExactAlarms()`.
8. **v1.1 (opsional)**: `USE_FULL_SCREEN_INTENT` untuk tampilan layar kunci ala alarm.
9. **Ekspektasi jujur**: tanpa whitelist OEM tidak ada app 100% anti-telat → Health Check + panduan OEM adalah **fitur inti**, bukan pelengkap.

### 10.3 Spesifikasi Kanal

| Kanal | Importance | Konten | Aksi |
|---|---|---|---|
| `laundry_reminder` | HIGH | "🧺 Waktunya nyuci!" + item spesifik + level nag | Sudah Nyuci / Besok |
| `outfit_confirm` | HIGH | "Anggap pakai X+Y?" | Ya / Koreksi / Libur |
| `outfit_log` | DEFAULT | "Catat outfit hari ini 👕" (opsional sore) | Catat Sekarang |
| `test` | HIGH | Notifikasi tes dari Health Check | — |

### 10.4 Nag Mode (F16) — Alur

```
Trigger (stok ≤ 2 ATAU item ≥ 2× pakai)
 └─ Notif-1 (20:00) → [Sudah Nyuci] / [Besok] / diabaikan
     └─ Belum dicuci → Notif-2 besok, judul naik level
         └─ Notif-3+ → sesuai setting (tiap hari / 2 hari / berhenti)
Quiet hours 22:00–08:00. Nag berhenti hanya lewat aksi user.
```

---

## 11. Onboarding (4 langkah, total < 2 menit)

1. **Welcome** 1 layar, tanpa akun → izin notifikasi dengan penjelasan konteks.
2. **"Isi lemari 30 detik"**: stepper per kategori, preset terisi (Kaos 5 · Kemeja 2 · Celana 3 · Outer 1 · Sepatu 2) → otomatis bernama "Kaos 1…5"; foto & nama bisa dilewati.
3. **Jadwal kuliah**: 7 saklar, default Sen–Jum.
4. **Jam pengingat** (default 20:00) → **"Kirim Notif Tes"** → selesai.

Hari pertama app sudah berfungsi penuh tanpa input detail.

---

## 12. Struktur Layar (bottom nav, 4 tab)

| Tab | Isi |
|---|---|
| **Beranda** | Kartu "Hari Ini": chip item bersih untuk dicentang, "⟳ Ulang terakhir", ringkasan laundry ("Kaos bersih: 3 · Kemeja: 1"), tombol nyuci cepat |
| **Lemari** | Daftar per kategori, filter status, badge "⚠️ 2× pakai", multi-select → Cuci / Pakai |
| **Kalender** | Riwayat bulanan + koreksi hari ini |
| **Pengingat** | Jadwal, threshold stok per kategori, aturan nag, quiet hours, Health Check & tes notif |

**Settings (gerigi):** cut-off 12:00, saklar hari kuliah, auto-confirm delay, backup/restore.

**Prinsip:** catat outfit ≤ 3 tap; tidak ada empty state tanpa CTA.

---

## 13. Kebutuhan Non-Fungsional

| Kategori | Requirement |
|---|---|
| Ukuran | APK < 10 MB (R8 + shrinkResources) |
| Min SDK / Target | Android 8.0 (API 26) / target terbaru (34+) |
| Performa | Cold start < 1,5 detik di RAM 3 GB, tanpa jank |
| Offline | 100% offline-first, tanpa akun, tanpa backend |
| Baterai | Tanpa background service persisten; hanya exact alarm |
| Privasi | Semua data & foto lokal; tanpa tracking/telemetri |
| Storage foto | WebP sisi terpanjang 1080px, 80–150 KB/foto; 1 tahun pemakaian < 50 MB |

---

## 14. Arsitektur & Teknologi

| Layer | Pilihan | Alasan |
|---|---|---|
| Bahasa/UI | Kotlin + XML Views + ViewBinding | Lebih ringan dari Compose untuk APK kecil |
| Database | Room | Relasi Item ↔ OutfitLog ↔ WashEvent |
| Gambar | Coil + Photo Picker/intent kamera | Hemat ukuran, tanpa izin storage di Android 13+ |
| Scheduling | `AlarmManager.setAlarmClock()` + `BroadcastReceiver` + re-schedule berantai + boot receiver | Anti-Doze |
| Build | App Bundle/APK, minify + shrinkResources | Target < 10 MB |

---

## 15. Model Data

```
Item       : id, nama, kategori(atasan|bawahan|outer|sepatu|aksesori),
             status(BERSIH|DIPAKAI|KOTOR), wearSinceWash, lastWorn, lastWashed,
             foto?, aktif
OutfitLog  : id, tanggal, itemIds[], foto?, catatan?, sumber(MANUAL|AUTO)
WashEvent  : id, tanggal, itemIds[]
DailyState : tanggal, isLibur?, autoConfirmedAt?, countedToday?
Settings   : cutoff(12:00), hariKuliah[7], reminderTime,
             threshold{per kategori}, nagInterval, quietHours(22–08),
             autoConfirmDelay(2 jam)
```

100% lokal, tanpa server. Tambahan vs v1.0: 2 tabel + 1 alarm (total 3 exact alarm) — dampak ukuran dapat diabaikan.

---

## 16. Roadmap

| Fase | Isi | Durasi |
|---|---|---|
| **MVP v1.0** | F1–F4, F6–F8, F14–F16 (semua P0) | **8–10 minggu** |
| *Cut-line (opsional, 7 minggu)* | Defer: catch-up catch-all, eskalasi nag → alarm jadwal murni + checklist dasar | — |
| **v1.1** | Statistik, backup, "Siapkan outfit besok" (auto-wear jadi 100% akurat), reminder jadwal F5 | +3 minggu |
| **v1.2** | Widget, dark mode | +2 minggu |
| **v2.0** | Cloud sync opsional, rekomendasi outfit dari riwayat | TBD |

---

## 17. Risiko & Mitigasi

| Risiko | Mitigasi |
|---|---|
| OEM tetap blokir alarm walau di-whitelist | Health Check interaktif + fallback `setExactAndAllowWhileIdle` + panduan per-OEM |
| Onboarding terasa ribet → uninstall | Preset terisi default, semua bisa di-skip, langsung pakai |
| Notif jam 12 diabaikan → stok meleset | Auto-confirm 2 jam + refund "🏖 Libur" + koreksi manual hari ini |
| Bug dobel-hitung merusak kepercayaan | Flag `DailyState` + `sumber` + unit test semua baris matriks edge case (bagian 9) |
| Nag terlalu nyebelin | Quiet hours + interval/berhenti bisa diatur |
| User bosan logging | Quick Reuse 1-tap, auto-wear, streak (P1) |
| Storage bengkak foto | Kompresi otomatis + opsi "hapus foto, simpan teks" |
| Android membatasi exact alarm ke depan | Layer scheduling terabstraksi, mudah ganti strategi |

---

## 18. Open Questions (sisa)

| # | Pertanyaan | Dampak jika diputuskan |
|---|---|---|
| 1 | Distribusi: Play Store atau APK langsung? | Mempengaruhi kebijakan izin `USE_EXACT_ALARM` (Play punya aturan deklarasi khusus) |
| 2 | Bahasa: Indonesia saja atau bilingual? | Copywriting + ukuran string resources |

---

**Lampiran — Status & istilah:**
- **BERSIH**: siap dipakai, masuk hitungan stok.
- **DIPAKAI**: dicentang hari ini (belum rollover).
- **KOTOR**: sudah dipakai ≥ 1× sejak terakhir dicuci, tidak masuk stok.
- **wearSinceWash**: jumlah pemakaian sejak terakhir dicuci; ≥ 2 memicu badge & trigger notif.
- **Auto-Wear**: pencatatan otomatis oleh engine jam 12, selalu bertanda `sumber=AUTO` dan bisa ditimpa manual.

---