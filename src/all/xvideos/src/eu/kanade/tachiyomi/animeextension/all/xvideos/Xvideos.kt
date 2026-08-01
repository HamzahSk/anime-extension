package eu.kanade.tachiyomi.animeextension.all.xvideos

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Xvideos : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Xvideos"
    override val baseUrl = "https://www.xvideos.com"
    override val lang = "all"
    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    // Client dengan Interceptor untuk menyuntikkan Cookie berdasarkan settingan di PreferenceScreen
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val country = preferences.getString(PREF_COUNTRY_KEY, PREF_COUNTRY_DEFAULT) ?: ""
            val type = preferences.getString(PREF_TYPE_KEY, PREF_TYPE_DEFAULT) ?: ""
            
            val builder = request.newBuilder()
            if (country.isNotEmpty() || type.isNotEmpty()) {
                val cookies = mutableListOf<String>()
                if (country.isNotEmpty()) cookies.add("country=$country")
                if (type.isNotEmpty()) cookies.add("type=$type")
                builder.addHeader("Cookie", cookies.joinToString("; "))
            }
            chain.proceed(builder.build())
        }
        .build()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val type = preferences.getString(PREF_TYPE_KEY, PREF_TYPE_DEFAULT)
        val url = "$baseUrl/?k=${query.replace(" ", "+")}&p=$page" +
            (if (type.isNotEmpty()) "&typef=$type" else "")
        return GET(url, headers)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Pengaturan Negara
        ListPreference(screen.context).apply {
            key = PREF_COUNTRY_KEY
            title = "Country"
            entries = arrayOf("Global", "Indonesia", "USA", "Japan")
            entryValues = arrayOf("", "id", "us", "jp")
            setDefaultValue(PREF_COUNTRY_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        // Pengaturan Preferensi Konten
        ListPreference(screen.context).apply {
            key = PREF_TYPE_KEY
            title = "Content Preference"
            entries = arrayOf("Straight", "Gay", "Trans")
            entryValues = arrayOf("", "gay", "trans")
            setDefaultValue(PREF_TYPE_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        // Pengaturan Kualitas
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
        private const val PREF_COUNTRY_KEY = "pref_country_key"
        private const val PREF_COUNTRY_DEFAULT = ""
        
        private const val PREF_TYPE_KEY = "pref_content_type"
        private const val PREF_TYPE_DEFAULT = ""
        
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "HLS"
    }

    // --- Boilerplate method yang diperlukan ---
    override fun popularAnimeSelector() = "div#main div#content div.mozaique.cust-nb-cols > div"
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/new/$page")
    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.select("div.thumb-inside div.thumb a").attr("href"))
        anime.title = element.select("div.thumb-under p.title").text()
        anime.thumbnail_url = element.select("div.thumb-inside div.thumb a img").attr("data-src")
        return anime
    }
    override fun popularAnimeNextPageSelector() = "a.no-page.next-page"
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()
    override fun animeDetailsParse(document: Document) = SAnime.create().apply { title = document.select("h2.page-title").text() }
    override fun episodeListParse(response: Response) = listOf(SEpisode.create().apply { name = "Video"; setUrlWithoutDomain(response.request.url.toString()) })
    
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val script = document.select("script:containsData(html5player.setVideoUrl)").toString()
        return listOf(
            Video(script.substringAfter("VideoUrlLow('").substringBefore("')"), "Low", null),
            Video(script.substringAfter("setVideoHLS('").substringBefore("')"), "HLS", null),
            Video(script.substringAfter("VideoUrlHigh('").substringBefore("')"), "High", null)
        )
    }

    override fun episodeListSelector() = throw Exception("not used")
    override fun episodeFromElement(element: Element) = throw Exception("not used")
    override fun videoListSelector() = throw Exception("not used")
    override fun videoUrlParse(document: Document) = throw Exception("not used")
    override fun videoFromElement(element: Element) = throw Exception("not used")
    override fun getFilterList() = AnimeFilterList()
}
