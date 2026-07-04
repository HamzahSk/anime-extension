package eu.kanade.tachiyomi.animeextension.id.xvideos

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element


class Xvideos : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Xvideos"
    override val baseUrl = "https://www.xvideos.com"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    // ============================== Browser/Search ==============================
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/popular/?p=${page - 1}")

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/new/?p=${page - 1}")

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val prefType = preferences.getString(PREF_PREFERENCE_KEY, PREF_PREFERENCE_DEFAULT)!!
        val url = "$baseUrl/$prefType".toHttpUrl().newBuilder()
        
        if (query.isNotBlank()) {
            url.addQueryParameter("k", query)
        }
        
        val filterList = filters.ifEmpty { getFilterList() }
        filterList.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.getUriPart())
                is DurationFilter -> url.addQueryParameter("durf", filter.getUriPart())
                is QualityFilter -> url.addQueryParameter("qf", filter.getUriPart())
            }
        }
        
        url.addQueryParameter("p", (page - 1).toString())
        return GET(url.build().toString())
    }

    override fun popularAnimeParse(response: Response) = searchAnimeParse(response)
    override fun latestUpdatesParse(response: Response) = searchAnimeParse(response)
    
    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val videos = document.select(".thumb-block").map { element ->
            SAnime.create().apply {
                val a = element.selectFirst(".title a")
                title = a?.attr("title") ?: "Unknown"
                url = a?.attr("href") ?: ""
                thumbnail_url = element.selectFirst("img")?.attr("data-src")
            }
        }
        return AnimesPage(videos, true)
    }

    override fun searchAnimeSelector() = ".thumb-block"
    override fun searchAnimeFromElement(element: Element) = SAnime.create()
    override fun searchAnimeNextPageSelector() = ".pagination"

    // ============================== Details ==============================
    override fun animeDetailsParse(response: Response) = SAnime.create().apply {
        val doc = response.asJsoup()
        title = doc.select("h2.page-title").text()
        description = doc.select(".video-metadata").text()
    }

    // ============================== Episodes ==============================
    override fun episodeListParse(response: Response): List<SEpisode> {
        return listOf(SEpisode.create().apply {
            name = "Full Video"
            url = response.request.url.toString()
            episode_number = 1F
        })
    }

    // ============================== Videos ==============================
    override fun getVideoList(url: String, name: String): List<Video> {
        return listOf(Video(url, "Default", url))
    }

    // ============================== Filters ==============================
    override fun getFilterList() = AnimeFilterList(
        SortFilter(),
        DurationFilter(),
        QualityFilter()
    )

    private open class UriPartFilter(displayName: String, val pairs: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, pairs.map { it.first }.toTypedArray()) {
        fun getUriPart() = pairs[state].second
    }

    private class SortFilter : UriPartFilter("Sort By", arrayOf(
        Pair("Relevance", "relevance"),
        Pair("Upload Date", "uploaddate"),
        Pair("Rating", "rating"),
        Pair("Duration", "length"),
        Pair("Views", "views")
    ))

    private class DurationFilter : UriPartFilter("Duration", arrayOf(
        Pair("All", "allduration"),
        Pair("1-3 Min", "1-3min"),
        Pair("3-10 Min", "3-10min"),
        Pair("10 Min+", "10min_more")
    ))
    
    private class QualityFilter : UriPartFilter("Quality", arrayOf(
        Pair("All", "all"),
        Pair("HD", "hd"),
        Pair("1080P", "1080P")
    ))

    // ============================== Preferences ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_PREFERENCE_KEY
            title = "Content Preference"
            entries = arrayOf("Straight", "Gay", "Trans")
            entryValues = arrayOf("", "gay", "trans")
            setDefaultValue(PREF_PREFERENCE_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_PREFERENCE_KEY = "pref_content_type"
        private const val PREF_PREFERENCE_DEFAULT = ""
    }
}
