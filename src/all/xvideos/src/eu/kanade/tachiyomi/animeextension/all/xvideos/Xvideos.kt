package eu.kanade.tachiyomi.animeextension.all.xvideos

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Xvideos :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Xvideos"
    override val baseUrl = "https://www.xvideos.com"
    override val lang = "all"
    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    override fun popularAnimeSelector(): String = "div[id*='video_']"

    override fun popularAnimeRequest(page: Int): Request {
        val type = preferences.getString(PREF_TYPE_KEY, PREF_TYPE_DEFAULT) ?: ""
        val base = when (type) {
            "gay" -> "/gay"
            "shemale" -> "/trans"
            else -> ""
        }
        val url = if (page == 1) "$baseUrl$base" else "$baseUrl$base/${page - 1}"
        return GET(url, headers)
    }

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.select("div.thumb-inside div.thumb a").attr("href"))
        anime.title = element.select("div.thumb-under p.title a").attr("title")
        anime.thumbnail_url = element.select("div.thumb-inside div.thumb a img").attr("data-src")
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "a.no-page.next-page"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val episode = SEpisode.create().apply {
            name = "Video"
            setUrlWithoutDomain(response.request.url.toString())
        }
        return listOf(episode)
    }

    override fun episodeListSelector() = throw Exception("not used")

    override fun episodeFromElement(element: Element) = throw Exception("not used")

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val sources = document.select("script:containsData(html5player.setVideoUrl)").toString()
        val lowQuality = sources.substringAfter("VideoUrlLow('").substringBefore("')")
        val hlsQuality = sources.substringAfter("setVideoHLS('").substringBefore("')")
        val highQuality = sources.substringAfter("VideoUrlHigh('").substringBefore("')")
        return listOf(
            Video(lowQuality, "Low", lowQuality),
            Video(hlsQuality, "HLS", hlsQuality),
            Video(highQuality, "High", highQuality),
        )
    }

    override fun videoListSelector() = throw Exception("not used")

    override fun videoUrlParse(document: Document) = throw Exception("not used")

    override fun videoFromElement(element: Element) = throw Exception("not used")

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)
        if (quality != null) {
            val newList = mutableListOf<Video>()
            var preferred = 0
            for (video in this) {
                if (video.quality == quality) {
                    newList.add(preferred, video)
                    preferred++
                } else {
                    newList.add(video)
                }
            }
            return newList
        }
        return this
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val type = preferences.getString(PREF_TYPE_KEY, PREF_TYPE_DEFAULT) ?: ""
        val url = "$baseUrl/?k=${query.replace(" ", "+")}&p=$page" +
            (if (type.isNotEmpty()) "&typef=$type" else "")
        return GET(url, headers)
    }

    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.select("h2.page-title").text()
            .removeSuffix(" " + document.select("h2.page-title span.duration").text())
            .removeSuffix(" " + document.select("h2.page-title span.video-hd-mark").text())
            .trim()
        anime.author = document.select("li.main-uploader a span.name").text()
        anime.genre = document.select("div.video-tags-list a.is-keyword").eachText().joinToString()
        anime.description = document.select("meta[name=description]").attr("content")
        anime.status = SAnime.COMPLETED
        return anime
    }

    override fun latestUpdatesNextPageSelector() = throw Exception("not used")

    override fun latestUpdatesFromElement(element: Element) = throw Exception("not used")

    override fun latestUpdatesRequest(page: Int) = throw Exception("not used")

    override fun latestUpdatesSelector() = throw Exception("not used")

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_TYPE_KEY
            title = "Content Preference"
            entries = arrayOf("Straight", "Gay", "Trans")
            entryValues = arrayOf("", "gay", "shemale")
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

    companion object {
        private const val PREF_TYPE_KEY = "pref_content_type"
        private const val PREF_TYPE_DEFAULT = ""

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "HLS"
    }
}
