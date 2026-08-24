# Tugas Perbaikan Ekstensi MovieBox

Kamu adalah seorang *AI Developer* ahli yang bertugas memperbaiki *source code* dari ekstensi media. Lokasi file yang perlu kamu perbaiki berada di dalam direktori `src/all/moviebox`.

Terdapat dua masalah utama yang dilaporkan dan harus diselesaikan:
1. **Fitur Pencarian (Search) Rusak:** Pencarian tidak mengembalikan hasil yang sesuai atau gagal total.
2. **Filter Tidak Berfungsi:** Parameter filter saat ini tidak singkron dengan website aslinya.

**Langkah-Langkah Eksekusi yang Harus Kamu Lakukan:**

*   **Identifikasi Target Web:** Temukan URL *base* dari website MovieBox yang digunakan di dalam *source code* ekstensi saat ini.
*   **Lakukan Web Scraping & Analisis:** 
    *   Akses dan lakukan *scraping* pada halaman pencarian dan halaman filter website aslinya.
    *   Analisis struktur HTML terbaru, struktur JSON (jika menggunakan API), serta parameter URL yang digunakan website tersebut untuk melakukan pencarian dan filter (misalnya parameter *genre*, *tahun*, dll).
*   **Perbaiki Kode Kotlin:**
    *   Perbarui *endpoint* URL pencarian dan filter di dalam file ekstensi.
    *   Perbarui logika *parsing* (CSS Selector atau JSON *parsing*) agar sesuai dengan struktur website yang baru kamu *scrape*.
*   **Verifikasi:** Pastikan tidak ada *error* sintaksis pada kode yang kamu ubah dan struktur kodenya tetap mengikuti standar ekstensi yang ada di repositori ini.

Silakan analisis kodenya sekarang, lakukan *scraping* ke web target, dan terapkan perubahannya langsung pada file-file di dalam `src/all/moviebox`.
