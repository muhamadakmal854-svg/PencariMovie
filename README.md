# 🎬 PENCARIMOVIE v2.0.0

<div align="center">

![PencariMovie Logo](logo.png)

![PencariMovie](https://img.shields.io/badge/PENCARI-MOVIE-00E5FF?style=for-the-badge&labelColor=0B132B&color=00E5FF)
![Version](https://img.shields.io/badge/Versi-2.0.0-gold?style=for-the-badge&labelColor=0B132B)
![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=0B132B)
![Android TV](https://img.shields.io/badge/Android%20TV%20/%20Stick-Supported-blue?style=for-the-badge&labelColor=0B132B)

**Aplikasi Penstriman Video Eksklusif PencariMovie**<br/>
*DEV APP BY MTS 2026 • Enjin Penstriman Natif Rust Terbina Dalam (Port 8088)*

[📥 Muat Turun APK](https://github.com/muhamadakmal854-svg/MTSFlix/releases/latest) • [📊 Pantau Build](https://github.com/muhamadakmal854-svg/MTSFlix/actions)

</div>

---

## ⚡ Apa Yang Baru di Versi 2.0.0

### 🚀 1. Enjin Natif Rust Terbina Dalam (Port 8088 — Tanpa Companion APK!)
* **All-in-One Experience:** Anda **tidak perlu memasang APK luar** (`telegra.my/apk`) lagi!
* Enjin Rust MTProto (`libpencarimovie_rust.so`) dan `libcloudflared.so` dimasukkan terus ke dalam pustaka natif APK (`applicationInfo.nativeLibraryDir`).
* Apabila aplikasi dibuka, pelayan tempatan port 8088 dilancarkan secara automatik dan sah dari segi keselamatan SELinux / W^X Android 10 sehingga 14+.
* Dilengkapi dengan penguncian kuasa (`WakeLock`) dan Wi-Fi (`WifiLock`) untuk menjamin penstriman video yang licin tanpa sangkut.

### 🎯 2. Satu-Satunya Provider Eksklusif: PencariMovie
* **Total Overhaul:** Tiada lagi senarai puluhan provider luar yang tidak berkaitan.
* Aplikasi ini didedikasikan secara 100% untuk katalog **PencariMovie** (`flixtorbot.pencarimovie.com`):
  * **Terkini** (Katalog filem & siri terkini)
  * **Koleksi Genre:** Aksi, Animasi, Komedi, Drama, Seram, Sci-Fi, Romantik, Fantasi, Jenayah, dll.
  * **Pencarian Pantas** terus ke repositori fail Telegram PencariMovie.
  * Sokongan penstriman resolusi tinggi 1080p, 720p, dan Direct DDL.

### 🎨 3. Penjenamaan Baharu (PENCARIMOVIE DEV APP BY MTS 2026)
* Antaramuka serba baharu dengan logo bercahaya neon cyan & emas.
* Ikon pelancar TV dan banner rasmi 1024x1024 yang diperbaharui.
* Pengesahan peranti dan profil berintegrasi sepenuhnya.

---

## 📋 Penggunaan & Arahan

### 1. Menu Kawalan Pentadbir (`master.py`)
Jalankan skrip pentadbir di komputer:
```bash
python master.py
```
* **Bina APK Baharu:** `python master.py build`
* **Push ke GitHub:** `python master.py push`
* **Urus Lesen Peranti:** `python master.py list` / `add` / `ban`

### 2. Cara Kerja Penstriman Telegram Dalaman
```
App PencariMovie Dibuka
       ↓
Jalankan libpencarimovie_rust.so (Port 8088 di 127.0.0.1)
       ↓
Pengguna Tekan Filem / Siri
       ↓
PencariMovie Extension minta stream dari http://127.0.0.1:8088/api/download?short_code=...
       ↓
Enjin Rust nyahsulit MTProto Telegram secara langsung ke ExoPlayer
       ↓
Video Dimainkan Serta-Merta!
```

---
**PENCARIMOVIE DEV APP BY MTS 2026**
