package eu.kanade.tachiyomi.animeextension.all.xvideos

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import aniyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Xvideos :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Xvideos"

    override val baseUrl = "https://www.xvideos.com"

    override val lang = "all"

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    private val categoryPath: String
        get() = when (preferences.getString(PREF_TYPE_KEY, PREF_TYPE_DEFAULT)) {
            TYPE_GAY -> "gay"
            TYPE_TRANS -> "trans"
            else -> ""
        }

    override fun popularAnimeRequest(page: Int): Request {
        val category = categoryPath
        val url = if (category.isEmpty()) {
            if (page <= 1) "$baseUrl/" else "$baseUrl/new/${page - 1}"
        } else {
            if (page <= 1) "$baseUrl/$category" else "$baseUrl/$category/${page - 1}"
        }
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.thumb-block"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain("$baseUrl${element.selectFirst("div.thumb-inside div.thumb a")?.attr("href").orEmpty()}")
        element.selectFirst("div.thumb-under p.title a")?.let { titleLink ->
            titleLink.select("span").remove()
            anime.title = titleLink.text()
        }
        anime.thumbnail_url = element.selectFirst("div.thumb-inside div.thumb img")?.attr("data-src")
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "a.no-page.next-page"

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isBlank()) return popularAnimeRequest(page)
        val type = preferences.getString(PREF_TYPE_KEY, PREF_TYPE_DEFAULT)
        val url = buildString {
            append("$baseUrl/?k=${URLEncoder.encode(query, "UTF-8")}&p=${page - 1}")
            if (!type.isNullOrEmpty()) append("&typef=$type")
        }
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("not used")

    override fun latestUpdatesSelector(): String = throw Exception("not used")

    override fun latestUpdatesFromElement(element: Element): SAnime = throw Exception("not used")

    override fun latestUpdatesNextPageSelector(): String = throw Exception("not used")

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        document.selectFirst("h2.page-title")?.let { titleElement ->
            titleElement.select("span").remove()
            title = titleElement.text()
        }
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        author = document.selectFirst("li.main-uploader span.name")?.text()
        genre = document.select("div.video-metadata ul li a.is-keyword").joinToString { it.text() }
        status = SAnime.COMPLETED
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> = listOf(
        SEpisode.create().apply {
            name = "Video"
            setUrlWithoutDomain(response.request.url.toString())
            date_upload = System.currentTimeMillis()
        },
    )

    override fun episodeListSelector(): String = throw Exception("not used")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("not used")

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val script = document.select("script:containsData(html5player.setVideoUrlLow)")
            .toString()
            .ifBlank { document.select("script:containsData(html5player)").toString() }

        val low = script.substringAfter("setVideoUrlLow('", "").substringBefore("')")
        val high = script.substringAfter("setVideoUrlHigh('", "").substringBefore("')")
        val hls = script.substringAfter("setVideoHLS('", "").substringBefore("')")

        return buildList {
            if (low.isNotEmpty()) add(Video(low, "Low", low, videoHeaders))
            if (high.isNotEmpty() && high != low) add(Video(high, "High", high, videoHeaders))
            if (hls.isNotEmpty()) {
                val hlsVideos = runCatching {
                    playlistUtils.extractFromHls(
                        playlistUrl = hls,
                        masterHeaders = videoHeaders,
                        videoHeaders = videoHeaders,
                        videoNameGen = { "HLS - $it" },
                    )
                }.getOrElse { emptyList() }
                if (hlsVideos.isEmpty()) {
                    add(Video(hls, "HLS", hls, videoHeaders))
                } else {
                    addAll(hlsVideos)
                }
            }
        }
    }

    override fun videoListSelector(): String = throw Exception("not used")

    override fun videoFromElement(element: Element): Video = throw Exception("not used")

    override fun videoUrlParse(document: Document): String = throw Exception("not used")

    // ========================== Video sorting =============================

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT
        return sortedWith(
            compareByDescending<Video> { it.quality.contains(quality, ignoreCase = true) }
                .thenByDescending { RESOLUTION_REGEX.find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
        )
    }

    // ============================= Settings ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_TYPE_KEY
            title = "Content Preference"
            entries = arrayOf("Straight", "Gay", "Trans")
            entryValues = arrayOf("", TYPE_GAY, TYPE_TRANS)
            setDefaultValue(PREF_TYPE_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Quality"
            entries = arrayOf("High", "Low", "HLS")
            entryValues = arrayOf("High", "Low", "HLS")
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)
    }

    // ============================= Utilities ==============================

    private val videoHeaders: Headers
        get() = headers.newBuilder()
            .set("Referer", "$baseUrl/")
            .build()

    companion object {
        private const val PREF_TYPE_KEY = "pref_content_type"
        private const val PREF_TYPE_DEFAULT = ""
        private const val TYPE_GAY = "gay"
        private const val TYPE_TRANS = "shemale"

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "HLS"

        private val RESOLUTION_REGEX = Regex("""(\d{3,4})p""")
    }
}
