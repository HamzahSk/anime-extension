package eu.kanade.tachiyomi.animeextension.all.moviebox

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.Source
import keiyoushi.utils.get
import keiyoushi.utils.parseAs
import keiyoushi.utils.post
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MovieBox(
    private val channelId: Int,
    private val trendingTabId: Int,
    private val channelName: String,
) : Source() {

    override val name = "MovieBox ($channelName)"

    override val baseUrl = "https://themoviebox.xyz"

    override val lang = "all"

    override val supportsLatest = true

    private val apiBase = "https://h5-api.aoneroom.com/wefeed-h5api-bff"

    private val homeUrl = "$apiBase/home?host=themoviebox.xyz"

    private val apiHeaders = headers.newBuilder()
        .add("Accept", "application/json, text/plain, */*")
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")
        .build()

    override val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    private var authToken: String? = null
    private var streamCookie: String? = null

    // ============================== Popular ===============================

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = "$apiBase/subject/trending".toHttpUrl().newBuilder()
            .addQueryParameter("tabId", trendingTabId.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", PAGE_SIZE.toString())
            .build()

        val data = client.get(url, apiHeaders).parseAs<ApiResponse<TrendingData>>(json).data
            ?: return AnimesPage(emptyList(), false)

        return data.subjectList.orEmpty().toAnimesPage(data.pager)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val body = FilterRequest(
            page = page,
            perPage = PAGE_SIZE,
            channelId = channelId,
            genre = "All",
            country = "All",
            year = "All",
            sort = "Latest",
            classify = "All",
        ).toJsonRequestBody(json)

        val data = client.post(
            url = "$apiBase/subject/filter",
            headers = apiHeaders,
            body = body,
        ).parseAs<ApiResponse<FilterData>>(json).data
            ?: return AnimesPage(emptyList(), false)

        return data.items.orEmpty().toAnimesPage(data.pager)
    }

    // =============================== Search ===============================

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        if (query.startsWith("https://")) {
            val httpUrl = query.toHttpUrlOrNull() ?: return AnimesPage(emptyList(), false)
            if (httpUrl.host != baseUrl.toHttpUrl().host) {
                return AnimesPage(emptyList(), false)
            }
            val detailPath = httpUrl.pathSegments.lastOrNull()
                ?: return AnimesPage(emptyList(), false)
            return AnimesPage(
                listOf(
                    SAnime.create().apply {
                        url = detailPath
                        title = detailPath
                    },
                ),
                false,
            )
        }

        if (query.isBlank()) {
            // Browse using the filter API
            val genre = filters.filterIsInstance<MovieBoxFilters.GenreFilter>().firstOrNull()?.selectedValue ?: "All"
            val country = filters.filterIsInstance<MovieBoxFilters.CountryFilter>().firstOrNull()?.selectedValue ?: "All"
            val year = filters.filterIsInstance<MovieBoxFilters.YearFilter>().firstOrNull()?.selectedValue ?: "All"
            val classify = filters.filterIsInstance<MovieBoxFilters.ClassifyFilter>().firstOrNull()?.selectedValue ?: "All"
            val sort = filters.filterIsInstance<MovieBoxFilters.SortFilter>().firstOrNull()?.selectedValue ?: "ForYou"

            val body = FilterRequest(
                page = page,
                perPage = PAGE_SIZE,
                channelId = channelId,
                genre = genre,
                country = country,
                year = year,
                sort = sort,
                classify = classify,
            ).toJsonRequestBody(json)

            val data = client.post(
                url = "$apiBase/subject/filter",
                headers = apiHeaders,
                body = body,
            ).parseAs<ApiResponse<FilterData>>(json).data
                ?: return AnimesPage(emptyList(), false)

            return data.items.orEmpty().toAnimesPage(data.pager)
        }

        // Text search (requires a bearer token)
        return searchPage(page, query) ?: AnimesPage(emptyList(), false)
    }

    private suspend fun searchPage(page: Int, query: String): AnimesPage? {
        val token = getAuthToken()
        if (token.isBlank()) return null
        val headers = apiHeaders.newBuilder()
            .add("Authorization", "Bearer $token")
            .build()
        val body = SearchRequest(
            keyword = query,
            page = page,
            perPage = PAGE_SIZE,
            subjectType = 0,
        ).toJsonRequestBody(json)

        val response = client.post(
            url = "$apiBase/subject/search",
            headers = headers,
            body = body,
        ).parseAs<ApiResponse<SearchData>>(json)

        // Token may have expired, retry once with a fresh token
        if (response.code != 0) {
            authToken = null
            val freshToken = getAuthToken()
            if (freshToken.isBlank()) return null
            val freshHeaders = apiHeaders.newBuilder()
                .add("Authorization", "Bearer $freshToken")
                .build()
            val retry = client.post(
                url = "$apiBase/subject/search",
                headers = freshHeaders,
                body = body,
            ).parseAs<ApiResponse<SearchData>>(json)
            return retry.data?.items.orEmpty().toAnimesPage(retry.data?.pager)
        }

        return response.data?.items.orEmpty().toAnimesPage(response.data?.pager)
    }

    private suspend fun getAuthToken(): String {
        authToken?.let { return it }
        val token = runCatching {
            client.newCall(GET(homeUrl, apiHeaders))
                .awaitSuccess()
                .use { response ->
                    val cookieToken = extractCookieValue(response.headers.values("Set-Cookie"), "token")
                    val xUserToken = response.headers["x-user"]
                        ?.let { json.parseToJsonElement(it).jsonObject["token"]?.jsonPrimitive?.content }
                    cookieToken ?: xUserToken
                }
        }.getOrNull()

        return token.orEmpty().also {
            authToken = it.ifBlank { null }
            if (it.isNotBlank()) {
                streamCookie = buildStreamingCookie(it)
            }
        }
    }

    private fun buildStreamingCookie(token: String): String = "token=$token; mb_token=%22$token%22"

    private fun streamingCookies(): String = streamCookie.orEmpty()

    private fun extractCookieValue(setCookieHeaders: List<String>, name: String): String? {
        for (header in setCookieHeaders) {
            val pair = header.substringBefore(";").trim()
            val (key, value) = pair.split("=", limit = 2).let { it[0] to it.getOrNull(1).orEmpty() }
            if (key == name) return value
        }
        return null
    }

    // =========================== Anime Details ============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val detail = fetchDetail(anime.url) ?: return anime
        return detail.subject?.toSAnime()?.apply {
            url = anime.url
        } ?: anime
    }

    // ============================== Episodes ==============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val detail = fetchDetail(anime.url) ?: return emptyList()
        val subject = detail.subject ?: return emptyList()

        // Collect every Sub/Dub variant that actually has episodes available.
        val variants = coroutineScope {
            subject.dubs.orEmpty().ifEmpty {
                listOf(
                    DubObject(
                        subjectId = subject.subjectId,
                        lanName = "Original",
                        detailPath = subject.detailPath,
                    ),
                )
            }.map { dub ->
                async {
                    val detailPath = dub.detailPath ?: return@async null
                    val variantDetail = if (detailPath == subject.detailPath) {
                        detail
                    } else {
                        runCatching { fetchDetail(detailPath) }.getOrNull()
                    } ?: return@async null
                    val variantSubject = variantDetail.subject ?: return@async null
                    if (variantSubject.hasResource != true) return@async null
                    val seasons = variantDetail.resource?.seasons.orEmpty()
                    if (seasons.isEmpty()) return@async null
                    VariantData(
                        lanName = dub.lanName ?: "Original",
                        subjectId = dub.subjectId ?: variantSubject.subjectId ?: return@async null,
                        detailPath = detailPath,
                        seasons = seasons,
                    )
                }
            }.awaitAll().filterNotNull()
        }

        val episodes = mutableListOf<SEpisode>()
        var globalNumber = 0f

        for (variant in variants) {
            for (season in variant.seasons) {
                val se = season.se ?: continue
                val maxEp = season.maxEp ?: 0

                if (maxEp <= 0) {
                    // Single resource (movie / standalone feature)
                    globalNumber += 1
                    episodes += SEpisode.create().apply {
                        name = "[${variant.lanName}] Movie"
                        episode_number = globalNumber
                        url = EpisodeData(variant.subjectId, se, 0, variant.detailPath, variant.lanName).encode()
                        scanlator = variant.lanName
                    }
                } else {
                    for (ep in 1..maxEp) {
                        globalNumber += 1
                        episodes += SEpisode.create().apply {
                            name = "[${variant.lanName}] S${se}E$ep"
                            episode_number = globalNumber
                            url = EpisodeData(variant.subjectId, se, ep, variant.detailPath, variant.lanName).encode()
                            scanlator = variant.lanName
                        }
                    }
                }
            }
        }

        return episodes.reversed()
    }

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val data = EpisodeData.decode(episode.url)
        if (data.subjectId.isBlank()) return emptyList()

        val url = "$apiBase/subject/play".toHttpUrl().newBuilder()
            .addQueryParameter("subjectId", data.subjectId)
            .addQueryParameter("se", data.se.toString())
            .addQueryParameter("ep", data.ep.toString())
            .addQueryParameter("detailPath", data.detailPath)
            .addQueryParameter("streamSignType", "1")
            .build()

        val play = client.get(url, playHeaders(data.detailPath)).parseAs<ApiResponse<PlayData>>(json).data
            ?: return emptyList()

        val videos = mutableListOf<Video>()

        val streams = play.streams.orEmpty().filter { it.url != null && it.resolutions != "0" }
        if (streams.isNotEmpty()) {
            val subtitles = fetchCaptions("MP4", data.subjectId, data.detailPath, streams.first().id)
            streams.forEach { stream ->
                videos += stream.toVideo("[MP4]", streamHeaders(stream, data.detailPath), subtitles)
            }
        }

        val dash = play.dash.orEmpty().filter { it.url != null && it.resolutions != "0" }
        if (dash.isNotEmpty()) {
            val subtitles = fetchCaptions("DASH", data.subjectId, data.detailPath, dash.first().id)
            dash.forEach { stream ->
                videos += stream.toVideo("[DASH]", streamHeaders(stream, data.detailPath), subtitles)
            }
        }

        val hls = play.hls.orEmpty().filter { it.url != null && it.resolutions != "0" }
        if (hls.isNotEmpty()) {
            val subtitles = fetchCaptions("HLS", data.subjectId, data.detailPath, hls.first().id)
            hls.forEach { stream ->
                videos += stream.toVideo("[HLS]", streamHeaders(stream, data.detailPath), subtitles)
            }
        }

        return videos
    }

    private suspend fun fetchCaptions(format: String, subjectId: String, detailPath: String, streamId: String?): List<Track> {
        if (streamId.isNullOrBlank()) return emptyList()
        val url = "$apiBase/subject/caption".toHttpUrl().newBuilder()
            .addQueryParameter("format", format)
            .addQueryParameter("id", streamId)
            .addQueryParameter("subjectId", subjectId)
            .addQueryParameter("detailPath", detailPath)
            .build()

        return runCatching {
            client.get(url, captionHeaders()).parseAs<ApiResponse<CaptionData>>(json).data
                ?.captions
                .orEmpty()
                .filter { !it.url.isNullOrBlank() }
                .map { Track(it.url!!, it.lanName ?: it.lan ?: "Subtitle") }
        }.getOrDefault(emptyList())
    }

    private fun captionHeaders(): Headers = apiHeaders.newBuilder()
        .apply {
            streamingCookies().takeIf { it.isNotBlank() }?.let { add("Cookie", it) }
        }
        .build()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        MovieBoxFilters.GenreFilter(),
        MovieBoxFilters.CountryFilter(),
        MovieBoxFilters.YearFilter(),
        MovieBoxFilters.ClassifyFilter(),
        MovieBoxFilters.SortFilter(),
    )

    // ============================= Utilities ==============================

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        // No preferences needed
    }

    override fun getAnimeUrl(anime: SAnime): String = "$baseUrl/detail/${anime.url}"

    override fun List<Video>.sort(): List<Video> = sortedByDescending {
        QUALITY_REGEX.find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private suspend fun fetchDetail(detailPath: String): DetailData? {
        if (detailPath.isBlank()) return null
        val url = "$apiBase/detail".toHttpUrl().newBuilder()
            .addQueryParameter("detailPath", detailPath)
            .build()
        return client.get(url, apiHeaders).parseAs<ApiResponse<DetailData>>(json).data
    }

    private fun List<SubjectObject>.toAnimesPage(pager: Pager?): AnimesPage {
        val hasNextPage = pager?.hasMore == true && isNotEmpty()
        return AnimesPage(map { it.toSAnime() }, hasNextPage)
    }

    private suspend fun playHeaders(detailPath: String): Headers {
        val token = getAuthToken()
        return apiHeaders.newBuilder()
            .apply {
                if (token.isNotBlank()) add("Authorization", "Bearer $token")
                set("Referer", "$baseUrl/movies/$detailPath")
                add("X-Client-Info", """{"timezone":"UTC"}""")
                add("X-Source", "")
                add("X-Vip-Restrict", "1")
                add("X-Request-Lang", "en")
                streamingCookies().takeIf { it.isNotBlank() }?.let { add("Cookie", it) }
            }
            .build()
    }

    private fun streamHeaders(stream: StreamObject, detailPath: String): Headers = headers.newBuilder()
        .apply {
            if (!stream.signHeaderKey.isNullOrBlank() && !stream.signCookie.isNullOrBlank()) {
                add(stream.signHeaderKey, stream.signCookie)
            }
            add("Referer", "$baseUrl/movies/$detailPath")
            streamingCookies().takeIf { it.isNotBlank() }?.let { add("Cookie", it) }
        }
        .build()

    private data class VariantData(
        val lanName: String,
        val subjectId: String,
        val detailPath: String,
        val seasons: List<SeasonObject>,
    )

    companion object {
        const val CHANNEL_MOVIE = 1
        const val CHANNEL_TV = 2
        const val CHANNEL_ANIME = 1006

        const val TAB_MOVIE = 2
        const val TAB_TV = 5
        const val TAB_ANIME = 8

        private const val PAGE_SIZE = 20

        private val QUALITY_REGEX = Regex("""(\d+)P""")
    }
}
