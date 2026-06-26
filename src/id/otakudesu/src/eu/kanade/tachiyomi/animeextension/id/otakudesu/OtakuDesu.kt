package eu.kanade.tachiyomi.animeextension.id.anichin

import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import aniyomi.lib.streamwishextractor.StreamWishExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.parallelMapNotNullBlocking
import keiyoushi.utils.useAsJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Anichin :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Anichin"

    override val baseUrl = "https://anichin.cafe"

    override val lang = "id"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    // =========================== Home/Latest ============================
    override fun latestUpdatesFromElement(element: Element): SAnime = SAnime.create().apply {
        title = element.selectFirst(".tt h2")?.text()?.trim() ?: ""
        setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
        thumbnail_url = element.selectFirst("img")?.attr("src") ?: ""
        // Type and episode info stored for later use
        genre = element.selectFirst(".typez")?.text()?.trim()
        status = parseEpisodeStatus(element.selectFirst(".bt .epx")?.text())
    }

    private fun parseEpisodeStatus(episodeText: String?): Int {
        if (episodeText.isNullOrBlank()) return SAnime.UNKNOWN
        val epNum = episodeText.split(" ").getOrNull(1)?.toIntOrNull()
        return if (epNum != null) SAnime.ONGOING else SAnime.COMPLETED
    }

    override fun latestUpdatesNextPageSelector(): String = ".pagination .next"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/complete/page/$page")

    override fun latestUpdatesSelector(): String = ".listupd article .bsx"

    // ============================== Popular ===============================
    override fun popularAnimeFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun popularAnimeNextPageSelector() = latestUpdatesNextPageSelector()
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/")
    override fun popularAnimeSelector(): String = ".bixbox:eq(0) article .bsx"

    // Override to handle popular anime from homepage
    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select(popularAnimeSelector()).map { popularAnimeFromElement(it) }
        return AnimesPage(animes, false)
    }

    // =============================== Latest ===============================
    // Override to handle latest from homepage
    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select(".bixbox:eq(1) article .bsx").map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null
        return AnimesPage(animes, hasNextPage)
    }

    // =============================== Search ===============================
    override fun searchAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        title = element.selectFirst(".tt h2")?.text()?.trim() ?: ""
        setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
        thumbnail_url = element.selectFirst("img")?.attr("src") ?: ""
        genre = element.selectFirst(".typez")?.text()?.trim()
        status = parseEpisodeStatus(element.selectFirst(".bt .epx")?.text())
    }

    override fun searchAnimeNextPageSelector(): String = ".pagination .next"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = "s=$query"
        return GET("$baseUrl/page/$page?$params")
    }

    override fun searchAnimeSelector(): String = ".bixbox article .bsx"

    // ============================ Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst(".entry-title")?.text() ?: ""
        thumbnail_url = document.selectFirst(".thumb img")?.attr("src") ?: ""
        description = buildString {
            document.selectFirst(".alter")?.text()?.let { append("Alternative Titles: $it\n") }
            document.select(".spe span").forEach { span ->
                val label = span.selectFirst("b")?.text()?.replace(":", "")?.trim() ?: return@forEach
                val time = span.selectFirst("time")
                if (time != null) {
                    append("$label: ${time.text()}\n")
                } else {
                    val links = span.select("a")
                    if (links.isNotEmpty()) {
                        append("$label: ${links.joinToString(", ") { it.text() }}\n")
                    } else {
                        val text = span.text().replace(Regex("$label\\s*:"), "").trim()
                        if (text.isNotEmpty()) append("$label: $text\n")
                    }
                }
            }
            document.selectFirst(".synp .entry-content")?.text()?.let { 
                append("\nSynopsis:\n$it") 
            }
        }
        genre = document.select(".genxed a").joinToString(", ") { it.text() }
    }

    // ============================== Episodes ==============================
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        
        val epText = element.selectFirst(".epl-num")?.text() ?: ""
        if (epText.contains("END")) {
            episode_number = epText.split(" ").getOrNull(0)?.toFloatOrNull() ?: 1F
            name = "Episode ${episode_number.toInt()} (END)"
        } else {
            episode_number = epText.toFloatOrNull() ?: 1F
            name = "Episode ${episode_number.toInt()}"
        }
        
        date_upload = element.selectFirst(".epl-date")?.text().let(DATE_FORMATTER::tryParse)
    }

    override fun episodeListSelector(): String = ".eplister ul li a"

    // Override to handle episode list from detail page
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.useAsJsoup()
        val elements = document.select(episodeListSelector())
        return elements.map { episodeFromElement(it) }.reversed()
    }

    // ============================ Video Links =============================
    override fun videoListSelector(): String = "select.mirror option[data-index]"

    override fun videoListParse(response: Response): List<Video> {
        val document = response.useAsJsoup()
        
        return document.select(videoListSelector())
            .parallelMapNotNullBlocking { option ->
                runCatching { getVideoFromOption(option) }.getOrNull()
            }
            .parallelCatchingFlatMapBlocking { video ->
                getVideosFromEmbed(video)
            }
    }

    private suspend fun getVideoFromOption(option: Element): Video? {
        val encodedUrl = option.attr("value")
        if (encodedUrl.isEmpty()) return null
        
        val decodedHtml = String(Base64.decode(encodedUrl, Base64.DEFAULT))
        val videoUrl = Regex("src=\"(.*?)\"").find(decodedHtml)?.groupValues?.get(1) ?: return null
        
        val quality = option.text().trim()
        return Video(videoUrl, quality, videoUrl, headers)
    }

    private val streamWishExtractor by lazy { StreamWishExtractor(client, headers) }

    private suspend fun getVideosFromEmbed(video: Video): List<Video> {
        val url = video.url
        
        return when {
            "anichin.stream" in url -> {
                try {
                    val streamUrl = url.replace("?id=", "hls/") + ".m3u8"
                    val response = client.newCall(GET(streamUrl, headers)).awaitSuccess()
                    val content = response.body?.string() ?: ""
                    
                    val variants = mutableListOf<Video>()
                    val lines = content.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    var currentAttrs: String? = null
                    
                    for (line in lines) {
                        if (line.startsWith("#EXT-X-STREAM-INF:")) {
                            currentAttrs = line.substringAfter("#EXT-X-STREAM-INF:")
                        } else if (currentAttrs != null && !line.startsWith("#")) {
                            val attrs = currentAttrs.split(",").associate { pair ->
                                val parts = pair.split("=", limit = 2)
                                parts[0] to parts.getOrNull(1)?.removeSurrounding("\"") ?: ""
                            }
                            
                            val resolution = attrs["RESOLUTION"] ?: ""
                            val bandwidth = attrs["BANDWIDTH"]?.toIntOrNull() ?: 0
                            
                            // Extract quality from resolution
                            val quality = when {
                                resolution.contains("1080") -> "1080p"
                                resolution.contains("720") -> "720p"
                                resolution.contains("480") -> "480p"
                                resolution.contains("360") -> "360p"
                                else -> "${bandwidth / 1000}k"
                            }
                            
                            val fullUrl = if (line.startsWith("http")) line else {
                                val base = streamUrl.substringBeforeLast("/")
                                "$base/$line"
                            }
                            
                            variants.add(Video(fullUrl, "Anichin - $quality", fullUrl, headers))
                            currentAttrs = null
                        }
                    }
                    
                    if (variants.isNotEmpty()) {
                        return variants
                    }
                } catch (e: Exception) {
                    // Fallback to original URL if parsing fails
                }
                listOf(video)
            }
            "streamwish" in url || "filelions" in url -> {
                streamWishExtractor.videosFromUrl(url, videoNameGen = { "StreamWish - $it" })
            }
            else -> listOf(video)
        }
    }

    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_ENTRIES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"
        }
        screen.addPreference(videoQualityPref)
    }

    // ============================= Utilities ==============================
    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
        return sortedWith(
            compareByDescending { it.quality.contains(quality) },
        )
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("d MMM,yyyy", Locale("id", "ID"))
        }

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
    }
}
