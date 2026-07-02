package eu.kanade.tachiyomi.animeextension.id.anichin

import android.util.Log
import aniyomi.lib.dailymotionextractor.DailymotionExtractor
import aniyomi.lib.okruextractor.OkruExtractor
import aniyomi.lib.rumbleextractor.RumbleExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.multisrc.animestream.AnimeStream
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element

class Anichin :
    AnimeStream(
        "id",
        "Anichin",
        "https://anichin.cafe",
    ) {

    // ============================== Halaman Utama / Browse ==============================
    override val animeListUrl = "$baseUrl/complete"

    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/complete/page/$page/?sort=views")

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/complete/page/$page/?sort=updated")

    // =============================== Pencarian ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("page")
                addPathSegment(page.toString())
                addQueryParameter("s", query)
            } else {
                addPathSegment("complete")
                addPathSegment(page.toString())
            }
        }
        return GET(urlBuilder.build().toString())
    }

    override fun searchAnimeSelector() = "div.bixbox article .bsx a, div.listupd article .bsx a"

    override fun searchAnimeFromElement(element: Element) = SAnime.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst(".tt h2")?.text()?.trim() ?: element.attr("title")
        thumbnail_url = element.selectFirst("img")?.getImageUrl()
    }

    override fun searchAnimeNextPageSelector() = ".pagination .next"

    // =========================== Parsing Detail Anime ============================
    override fun parseStatus(statusString: String?): Int = when (statusString?.trim()?.lowercase()) {
        "completed", "end" -> SAnime.COMPLETED
        "ongoing", "currently airing" -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
    }

    // ============================== Parsing Episode ==============================
    override fun episodeListSelector() = ".eplister ul li a"

    override fun episodeFromElement(element: Element) = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        val epText = element.selectFirst(".epl-num")?.text().orEmpty()
        
        val numOnly = epText.replace("ep", "", true)
            .replace("episode", "", true)
            .replace("end", "", true)
            .trim()

        name = "Episode $epText"
        episode_number = numOnly.toFloatOrNull() ?: 1F
        date_upload = element.selectFirst(".epl-date")?.text().let { dateFormatter.tryParse(it) }
    }

    // ============================ Pengekstrakan Video Links =============================
    // Menginisialisasi extractor dengan context client & headers dari base class AnimeStream
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val rumbleExtractor by lazy { RumbleExtractor(client, headers) }
    private val okruExtractor by lazy { OkruExtractor(client) }

    override suspend fun getVideoList(url: String, name: String): List<Video> {
        val videoList = mutableListOf<Video>()
        val prefix = "$name - " // Menambahkan penanda nama server pada resolusi video

        when {
            // 1. Dailymotion Extractor (Mendukung link geo.dailymotion / dailymotion biasa)
            url.contains("dailymotion") || name.contains("dailymotion", true) -> {
                runCatching {
                    videoList.addAll(dailymotionExtractor.videosFromUrl(url, prefix = prefix))
                }
            }

            // 2. Rumble Extractor
            url.contains("rumble") || name.contains("rumble", true) -> {
                runCatching {
                    videoList.addAll(rumbleExtractor.videosFromUrl(url, prefix = prefix))
                }
            }

            // 3. OK.ru Extractor
            url.contains("ok.ru") || name.contains("ok.ru", true) -> {
                runCatching {
                    videoList.addAll(okruExtractor.videosFromUrl(url, prefix = prefix))
                }
            }

            // 4. Custom Stream m3u8 parser untuk anichin.stream (Premium 1)
            name.contains("Premium", true) || url.contains("anichin.stream") -> {
                runCatching {
                    val fixUrl = if (url.contains("?id=")) {
                        url.replace("?id=", "hls/") + ".m3u8"
                    } else {
                        url
                    }
                    videoList.add(Video(fixUrl, "${prefix}HLS Stream", fixUrl, headers))
                }
            }

            else -> {
                Log.i("Anichin", "Unrecognized Host => Name -> $name || URL -> $url")
            }
        }

        return videoList
    }
}
