# AGENTS.md — System Specification & Agent Operational Guide

> **Architecture & Specification**: Dual-Module Vitals Tracking System (Samsung Galaxy Watch & Polar BLE Hub)  
> **Development Methodology**: Spec-Driven Development (SDD), Behavior-Driven Development (BDD), BMAD & OpenSpec  
> **Repository**: `HeartRateMonitor` (`Sample Wear Mobile App`)  

---

## 1. Executive Summary

Aplikasi **HeartRateMonitor** merupakan sistem akuisisi dan sinkronisasi biosinyal klinis/fisiologis multi-sensor terdistribusi (*distributed client-node*). Sistem ini menghubungkan dua perangkat keras fisiologis utama:
1. **Samsung Galaxy Watch (Wear OS)**: Mengukur Photoplethysmography optik multi-kanal (PPG Green @ 25 Hz, PPG IR @ 100 Hz, PPG Red @ 100 Hz) via Samsung Privileged Health Tracking SDK serta 3-Axis Accelerometer (X, Y, Z) via Android SensorManager.
2. **Polar H10 / Verity Sense (BLE Hub)**: Mengukur sinyal Elektrokardiogram elektrik (ECG @ 130 Hz) serta detak jantung perangkat (Device HR).
3. **Android Smartphone (Mobile Hub)**: Bertindak sebagai koordinator perekaman (master control), visualisasi gelombang *real-time* (60 FPS vsync via Choreographer scheduler), deteksi kompleks QRS real-time, dan penyimpanan data multi-kanal berformat CSV standar melalui Android Storage Access Framework (SAF).

---

## 2. Fitur-Fitur Aplikasi & Status Implementasi

| Fitur | Modul | Deskripsi | Status Implementasi |
| :--- | :--- | :--- | :--- |
| **PPG Green Tracking** | `wear` $\rightarrow$ `mobile` | Perekaman gelombang cahaya hijau (25 Hz), batching 300 data poin, transmisi DataApi, visualisasi grafik hijau. | ✅ Terimplementasi & Terverifikasi |
| **PPG IR & Red Tracking** | `wear` $\rightarrow$ `mobile` | Perekaman gelombang inframerah dan merah (100 Hz), batching 300 data poin, visualisasi grafik independen. | ✅ Terimplementasi & Terverifikasi |
| **Watch Accelerometer** | `wear` $\rightarrow$ `mobile` | Perekaman akselerasi 3-sumbu ($m/s^2$) dari sensor fisik jam, batching 100 poin, transmisi via `/dataaccelerometer`, dan ekspor CSV. | ✅ **Terimplementasi (Baru)** |
| **Polar ECG Streaming** | `mobile` | Streaming BLE 130 Hz dari Polar H10, visualisasi kanvas grid ECG, pembersihan data, sinkronisasi waktu. | ✅ Terimplementasi & Auto-start Terverifikasi |
| **QRS Peak Detection** | `mobile` | Filter Pan-Tompkins & Butterworth real-time untuk deteksi puncak gelombang R dan kalkulasi denyut jantung (QRS HR). | ✅ Terimplementasi |
| **Dual-Mode Control** | `mobile` $\rightarrow$ `wear` | Kontrol Start/Pause/Resume/Stop tersinkronisasi via `Wearable.MessageApi` (`ActivityCode`). | ✅ Terimplementasi |
| **Safe Buffer Drain (Flush)** | `wear` | Pengosongan paksa sisa data pada buffer lokal saat stop/pause untuk mencegah data terpotong di akhir sesi. | ✅ Terimplementasi & Off-by-one Diperbaiki |
| **Resilient Timestamping** | `shared` | Normalisasi timestamp lintas domain (Samsung wall-clock vs Polar epoch nanos) via `TimestampHelper`. | ✅ Terimplementasi & Unit Test Pass |
| **CSV Export SAF** | `mobile` | Ekspor CSV terstruktur (ECG, PPG Green, PPG IR, PPG Red, Accelerometer, atau All) dengan metadata lengkap. | ✅ Terimplementasi & Terintegrasi |
| **Foreground Service** | `mobile` & `wear` | Menjamin perekaman tidak terbunuh oleh optimasi baterai OS (`MobileService` & `MainService`). | ✅ Terimplementasi |

---

## 3. Spesifikasi Arsitektur & Data Pipeline

```
+-----------------------------------------------------------------------------------+
|                            SAMSUNG GALAXY WATCH (Wear OS)                        |
|                                                                                   |
|  +--------------------+   +-----------------------+   +------------------------+  |
|  | Samsung Health SDK |   | SensorManager Native  |   | Foreground MainService |  |
|  | (PPG Green,IR,Red) |   | (TYPE_ACCELEROMETER)  |   +------------------------+  |
|  +---------+----------+   +-----------+-----------+                               |
|            |                          |                                           |
|            v                          v                                           |
|     [PpgRecording]          [AccelerometerRecording]                              |
|   (Batch Size = 300)          (Batch Size = 100)                                  |
|            |                          |                                           |
|            +------------+-------------+                                           |
|                         | Gson JSON Serialize                                     |
|                         v                                                         |
|             Wearable.DataApi.putDataItem()                                        |
|         (/datappggreen, /datappgir, /datappgred, /dataaccelerometer)              |
+-------------------------+---------------------------------------------------------+
                          | Bluetooth Wearable Transport
                          v
+-------------------------+---------------------------------------------------------+
|                              ANDROID SMARTPHONE (Mobile)                          |
|                                                                                   |
|            Wearable.DataApi.addListener()                                         |
|                         |                                                         |
|       +-----------------+-------------------+------------------+                  |
|       v                                     v                  v                  |
| [PpgData Deserializer]             [AccelerometerData]   [Polar BLE Service]      |
|       |                                     |            (ECG @ 130 Hz)           |
|       v                                     v                  |                  |
| [PpgPlotters (Gr/IR/Red)]           [accX/Y/Z/Time]            v                  |
|       |                                     |            [EcgPlotter & QRS]       |
|       +-----------------+-------------------+------------------+                  |
|                         |                                                         |
|                         v                                                         |
|           [PlotUpdateScheduler (Choreographer)] -> UI 60 FPS                      |
|                         |                                                         |
|                         v                                                         |
|        [SAF CSV Exporter (ECG, PPG 3-Ch, Accelerometer, All)]                     |
+-----------------------------------------------------------------------------------+
```

---

## 4. Invarian Sistem (Non-Negotiable Guardrails)

1. **Zero Data Fabrication**: Tidak ada interpolasi palsu atau nilai nol sintetis untuk akselerometer atau PPG. Jika sensor tidak aktif, data ditandai kosong secara eksplisit.
2. **Deterministic Lifecycle**:
   - `START_ACTIVITY`: Listener akselerometer dan PPG aktif; counter dan buffer sesi di-reset bersih.
   - `PAUSE_ACTIVITY`: Buffer tersisa di-flush ke ponsel; listener sensor di-unreg untuk efisiensi daya.
   - `STOP_ACTIVITY`: Semua buffer di-flush tuntas; foreground service dihentikan; listener dilepas.
3. **Clean Message Handling**: Seluruh rute `MessagePath` (`COMMAND`, `REQUEST`, `INFO`, data path) ditangani secara aman dengan pola non-throwing (tidak ada `TODO()` yang menyebabkan aplikasi crash).
4. **Decoupled Plot Scheduling**: Ingest sinyal berfrekuensi tinggi (ECG 130 Hz, PPG 100 Hz) dipisahkan dari thread rendering UI menggunakan penjadwalan frame sinkron.

---

## 5. Acceptance Scenarios (BDD Gherkin)

### Skenario 1: Perekaman Accelerometer Galaxy Watch
```gherkin
Given Galaxy Watch terhubung dengan Smartphone
When smartphone mengirim pesan COMMAND dengan kode START_ACTIVITY
Then smartwatch mengaktifkan listener Sensor.TYPE_ACCELEROMETER
And data akselerasi (x, y, z, timestamp) dikumpulkan dalam AccelerometerRecording
And ketika ukuran batch mencapai 100 sampel, data dikirim via Wearable DataApi ke /dataaccelerometer
```

### Skenario 2: Stop & Pengosongan Buffer (Flush Safety)
```gherkin
Given proses perekaman sedang aktif dan tersisa 42 sampel accelerometer di buffer
When smartphone mengirim perintah STOP_ACTIVITY
Then smartwatch memanggil flushRemainingData()
And 42 sampel accelerometer dikirim ke smartphone sebelum listener dimatikan
And listener sensor di-unregister dari SensorManager
```

### Skenario 3: Penerimaan & Ekspor CSV di Smartphone
```gherkin
Given sesi perekaman telah selesai dan data accelerometer tersimpan di ponsel
When pengguna memilih menu "Save" -> "Save All" atau "Save Accelerometer Data"
Then berkas "<nama>-Accelerometer-<timestamp>.csv" dibuat melalui DocumentTree SAF
And baris header mencatat metadata (versi aplikasi, datatype, samplescount, samplingrate)
And baris data memuat format "x,y,z,timestamp,formatted_date"
```

---

## 6. Verifikasi & Checklist Pemeliharaan

- [x] Modul `shared` lulus pengujian unit: `MessageTest`, `PpgDataTest`, `TimestampHelperTest`, `AccelerometerDataTest`.
- [x] Modul `mobile` lulus pengujian unit: `FixedSizeListTest`, `RunningAverageTest`, `RunningMaxTest`, `AppUtilsTest`.
- [x] Modul `wear` lulus kompilasi tanpa unhandled stubs atau dependensi hilang.
- [x] Linter & compiler warnings bebas dari error pemblokir.
- [x] Konfigurasi JDK terstandardisasi pada Java 17 via `gradle.properties` (`org.gradle.java.home`).
- [x] Versi aplikasi dimutakhirkan (`versionCode` dan `versionName`) untuk kemudahan distribusi user.
