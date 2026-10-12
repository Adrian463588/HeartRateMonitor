# Laporan Proyek: HeartRateMonitor (Dual-Module Vitals Tracking)

## 1. Pendahuluan
Proyek **HeartRateMonitor** adalah sebuah sistem pelacakan tanda-tanda vital ganda (Dual-Module) berbasis Android yang terdiri dari aplikasi **Mobile (Smartphone)** dan **Wear OS (Smartwatch)**. Proyek ini bertujuan untuk merekam, menyinkronkan, memvisualisasikan, dan mengumpulkan data fisiologis secara *real-time*.

Sistem ini menggabungkan dua sumber sensor utama:
1.  **Sensor PPG (Photoplethysmography)** dari Samsung Galaxy Watch (menggunakan Samsung Health SDK) untuk mengukur sirkulasi darah melalui sensor optik (Cahaya Hijau, Inframerah, dan Merah).
2.  **Sensor ECG (Electrocardiogram)** dari perangkat eksternal Polar (misal: Polar H10 atau Verity Sense) yang dihubungkan ke smartphone melalui Bluetooth Low Energy (BLE).

## 2. Struktur Modul Proyek
Repositori proyek ini dipecah menjadi tiga modul Gradle utama:
*   **`wear`**: Aplikasi Wear OS (Produsen Data). Berjalan di Samsung Galaxy Watch, bertugas merekam data PPG mentah beresolusi tinggi dan mengirimkannya ke smartphone.
*   **`mobile`**: Aplikasi Android (Konsumen & Hub Data). Berjalan di Smartphone Android, bertugas mengelola koneksi ke sensor Polar (ECG), menerima data dari smartwatch (PPG), memproses sinyal, dan memvisualisasikannya di antarmuka utama (Dashboard).
*   **`shared`**: Modul perpustakaan internal yang berisi kelas model data (seperti `PpgData`, `PpgType`), konstanta (`Constants.kt`), dan utilitas (*utils*) yang digunakan bersama oleh aplikasi mobile dan wear.

## 3. Arsitektur dan Pipeline Sistem

### A. Tahap Inisialisasi dan Koneksi
1.  **Wear OS**: Saat aplikasi smartwatch dijalankan, ia melakukan inisialisasi `ConnectionManager` untuk terhubung ke infrastruktur **Samsung Health Tracking Service**. Aplikasi akan memverifikasi ketersediaan dan kapabilitas pelacakan optik (PPG Green, IR, Red) di jam tangan pengguna. Selain itu, ia juga menginisialisasi `GoogleApiClient` dengan layanan Google Play Services (`Wearable API`) untuk membentuk jaringan komunikasi dengan smartphone.
2.  **Mobile**: Aplikasi smartphone diinisialisasi bersamaan dengan sebuah layanan *Foreground* (`MobileService`) yang terus menyala untuk mencegah OS mematikan proses perekaman di latar belakang. Aplikasi mengatur pencarian perangkat (`BluetoothService`) untuk memindai dan terhubung dengan sensor detak jantung Polar melalui BLE. Smartphone juga bertindak sebagai penerima (node) melalui Wearable API untuk merespons koneksi jam tangan.

### B. Pipeline Aliran Data (Data Flow)
Proses transmisi data bergantung pada sumber sensornya:

#### **Aliran Data PPG (Dari Wear ke Mobile)**
1.  **Perekaman (Sampling)**: Sensor optik menangkap gelombang pantulan cahaya pada kulit lewat observer (`PpgGreenListener`, `PpgIrListener`, `PpgRedListener`).
2.  **Batching (Pengumpulan)**: Karena frekuensi sampling (*sample rate*) cukup tinggi (puluhan data per detik), data akan diformat terlebih dahulu menggunakan `PpgRecording`. Pengiriman langsung (*streaming* tunggal) memakan banyak baterai, sehingga data ditampung secara lokal di smartwatch hingga mencapai batas *batch size* (misal: 100 data poin).
3.  **Transmisi (Sinkronisasi)**: Setelah satu *batch* penuh, `MainActivity` di `wear` mengubahnya menjadi *string* JSON menggunakan `Gson`. Kemudian JSON dikirim secara instan ke aplikasi smartphone dengan status "Urgent" melalui **`Wearable.DataApi`** (`PutDataRequest`).
4.  **Penerimaan**: Aplikasi `mobile` membaca perubahan pada jalur (*path*) data Wearable, menerima *payload* JSON, dan mengubahnya kembali menjadi objek `PpgData` yang siap untuk diproses dan di-plot.
5.  **Flushing**: Apabila pengguna menghentikan proses perekaman, sistem smartwatch akan menjalankan instruksi `flushRemainingData()` agar memastikan setiap sisa titik data yang ada di *buffer* dipaksa dikirim agar tidak ada bagian grafik yang terpotong/hilang.

#### **Aliran Data ECG & Heart Rate (Dari Polar ke Mobile)**
1.  **Perekaman & Transmisi**: Sensor sentuh pada tali (*strap*) dada/lengan perangkat Polar merekam sinyal elektrik (ECG) dan mengirimkan titik datanya secara *real-time stream* ke `BluetoothService` di modul mobile melalui paket protokol standar BLE detak jantung.

### C. Alur Pemrosesan dan Visualisasi
1.  **Pemrosesan Sinyal**: Saat `mobile` menerima kumpulan data mentah dari sensor Wear OS dan Polar, alur diarahkan ke analisis algoritmis. Terdapat penerapan `QrsDetector` pada struktur file yang mengindikasikan bahwa sistem menggunakan metode matematis / pemfilteran batas *(threshold)* untuk mendeteksi gelombang kompleks QRS secara dinamis.
2.  **Plotting / Visualisasi UI**: Agar interaksi grafis tidak menyebabkan HP macet (*hang*), komponen *Dashboard* di aplikasi mobile memisahkan tugas penggambarannya ke serangkaian modul `Plotter` yang diperbarui oleh *Thread / Scheduler* terpisah:
    *   `EcgPlotter.kt`: Membuat dan memperbarui kanvas grafik Elektrokardiogram.
    *   `PpgPlotter.kt`: Membuat kanvas visualisasi tiga kanal grafik cahaya optik PPG (Hijau, Merah, dan IR).
    *   `HrPlotter.kt` & `QrsPlotter.kt`: Memplot data agregat untuk denyut per menit (*Heart Rate*) serta visibilitas penanda puncak R.

### D. Alur Kontrol dan Sinkronisasi Status
Proyek ini mengimplementasikan sinkronisasi state (status) menggunakan `Wearable.MessageApi`.
*   Aplikasi *smartphone* memegang peranan sebagai *Remote Control*. Melalui aplikasi di HP, pengguna dapat memulai (*Start*), menghentikan (*Stop*), atau menjeda (*Pause*) perekaman.
*   Pesan kontrol ini menyeberang lewat Bluetooth internal ke smartwatch. `MainActivity` smartwatch langsung membaca paket kontrol (berdasarkan `ActivityCode`) dan mengatur kondisi sensor Samsung (menyalakan tracker atau mematikannya). 
*   Pendekatan kontrol dua sisi ini menjamin bahwa tidak akan terjadi ketidaksesuaian sinkronisasi di mana smartwatch masih membakar baterai memancarkan laser optik ketika ponsel sudah berhenti mengumpulkan datanya.

## 4. Kesimpulan
Basis kode **HeartRateMonitor** merupakan representasi luar biasa dari implementasi aplikasi biomedis yang terdistribusi (*distributed client-node*). Sistem ini menangani komplikasi perangkat keras dari dua produsen berbeda (Samsung dan Polar) dengan menciptakan "lem" integrasi mulus berupa protokol sinkronisasi batch Data API dan komunikasi BLE di *Foreground Service*. Pemisahan modul menjadi tiga segmen (*wear*, *mobile*, *shared*) memastikan stabilitas tinggi dan perawatan yang mudah dalam pengembangan *wearables* klinis/kesehatan secara mendalam.
