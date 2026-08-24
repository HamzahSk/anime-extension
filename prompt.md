
## Tugas Penyesuaian Ekstensi MovieBox
Kamu adalah seorang *AI Developer* ahli yang bertugas memperbaiki dan menyesuaikan *source code* dari ekstensi media. Lokasi file ekstensi yang perlu kamu perbaiki berada di dalam direktori src/all/moviebox.
Terdapat dua masalah utama yang dilaporkan dan harus diselesaikan:
 1. **Fitur Pencarian (Search) Rusak:** Pencarian tidak mengembalikan hasil yang sesuai atau gagal total.
 2. **Filter Tidak Berfungsi:** Parameter filter saat ini tidak singkron dengan website aslinya.
 
> **Catatan Penting:** Kamu **tidak perlu** melakukan *web scraping* atau analisis website dari awal untuk mencari parameter baru. Saya sudah membuat *scraper* terbaru yang sudah berfungsi. File *scraper* tersebut ada di *root directory* dengan nama moviebox.js.
> 
**Langkah-Langkah Eksekusi yang Harus Kamu Lakukan:**
 * **Analisis Referensi Scraper:** Buka dan periksa file moviebox.js di *root directory*. Pahami struktur URL, *endpoint*, parameter *query* (untuk *search* dan *filter*), serta logika ekstraksi data (CSS Selector atau *parsing* JSON) yang digunakan di dalamnya.
 * **Sesuaikan Kode Kotlin:**
   * Perbarui *endpoint* URL pencarian dan filter di dalam file Kotlin ekstensi agar selaras dengan apa yang ada di moviebox.js.
   * Perbarui logika *parsing* di Kotlin agar menggunakan struktur dan *selector* yang persis sama dengan yang ada di *script* moviebox.js.
 * **Verifikasi:** Pastikan tidak ada *error* sintaksis pada kode yang kamu ubah dan struktur kodenya tetap mengikuti standar ekstensi yang ada di repositori ini.
Silakan analisis logika dari moviebox.js sekarang, lalu terapkan perubahannya langsung pada file-file di dalam src/all/moviebox.

