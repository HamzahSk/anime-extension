package eu.kanade.tachiyomi.animeextension.all.moviebox

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers

@Serializable
data class ApiResponse<T>(
    val code: Int? = null,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
data class Pager(
    val hasMore: Boolean? = null,
    val nextPage: String? = null,
    val page: String? = null,
    val perPage: Int? = null,
    val totalCount: Int? = null,
)

@Serializable
data class HomeData(
    val platformList: List<PlatformObject>? = null,
    val operatingList: List<OperatingObject>? = null,
)

@Serializable
data class PlatformObject(
    val name: String? = null,
    val uploadBy: String? = null,
)

@Serializable
data class OperatingObject(
    val type: String? = null,
    val position: Int? = null,
    val title: String? = null,
    val subjects: List<SubjectObject>? = null,
    val banner: BannerObject? = null,
    val opId: String? = null,
    val url: String? = null,
    val liveList: List<JsonElement>? = null,
    val filters: List<JsonElement>? = null,
    val customData: JsonElement? = null,
    val genreTopId: String? = null,
    val detailPath: String? = null,
)

@Serializable
data class BannerObject(
    val items: List<BannerItemObject>? = null,
)

@Serializable
data class BannerItemObject(
    val id: String? = null,
    val title: String? = null,
    val image: ImageObject? = null,
    val url: String? = null,
    val subjectId: String? = null,
    val subjectType: Int? = null,
    val subject: SubjectObject? = null,
)

@Serializable
data class ImageObject(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
    val format: String? = null,
    val thumbnail: String? = null,
    val blurHash: String? = null,
    val gif: String? = null,
    val avgHueLight: String? = null,
    val avgHueDark: String? = null,
    val id: String? = null,
)

@Serializable
data class SubjectObject(
    val subjectId: String? = null,
    val subjectType: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val releaseDate: String? = null,
    val duration: Long? = null,
    val genre: String? = null,
    val cover: ImageObject? = null,
    val countryName: String? = null,
    val imdbRatingValue: String? = null,
    val subtitles: String? = null,
    val ops: String? = null,
    val hasResource: Boolean? = null,
    val trailer: JsonElement? = null,
    val detailPath: String? = null,
    val staffList: List<JsonElement>? = null,
    val appointmentCnt: Int? = null,
    val appointmentDate: String? = null,
    val corner: String? = null,
    val imdbRatingCount: Int? = null,
    val stills: JsonElement? = null,
    val postTitle: String? = null,
    val season: Int? = null,
    val dubs: List<String>? = null,
    val accessStrategy: JsonElement? = null,
) {
    fun toSAnime(): SAnime {
        val subject = this
        val anime = SAnime.create()
        anime.title = subject.title.orEmpty()
        anime.url = subject.detailPath ?: subject.subjectId ?: ""
        anime.thumbnail_url = subject.cover?.url
        anime.genre = subject.genre
        anime.author = subject.countryName
        anime.description = buildString {
            subject.imdbRatingValue?.takeIf { it.isNotBlank() }?.let {
                append("⭐ IMDb: $it")
            }
            subject.releaseDate?.takeIf { it.isNotBlank() }?.let {
                append(if (isNotEmpty()) "\n" else "")
                append("Release date: $it")
            }
            subject.duration?.takeIf { it > 0 }?.let {
                append(if (isNotEmpty()) "\n" else "")
                append("Duration: ${it / 60} min")
            }
            subject.countryName?.takeIf { it.isNotBlank() }?.let {
                append(if (isNotEmpty()) "\n" else "")
                append("Country: $it")
            }
            if (isNotEmpty()) append("\n\n")
            subject.description?.takeIf { it.isNotBlank() }?.let { append(it) }
        }
        return anime
    }
}

@Serializable
data class TrendingData(
    val subjectList: List<SubjectObject>? = null,
    val pager: Pager? = null,
)

@Serializable
data class FilterData(
    val pager: Pager? = null,
    val items: List<SubjectObject>? = null,
)

@Serializable
data class SearchData(
    val pager: Pager? = null,
    val items: List<SubjectObject>? = null,
    val counts: JsonElement? = null,
)

@Serializable
data class FilterRequest(
    val page: Int,
    val perPage: Int,
    val channelId: Int,
    val genre: String,
    val country: String,
    val year: String,
    val sort: String,
    val classify: String,
)

@Serializable
data class SearchRequest(
    val keyword: String,
    val page: Int,
    val perPage: Int,
    val subjectType: Int,
)

@Serializable
data class DetailData(
    val subject: SubjectObject? = null,
    val stars: JsonElement? = null,
    val resource: ResourceObject? = null,
    val metadata: MetadataObject? = null,
    val isForbid: Boolean? = null,
    val watchTimeLimit: JsonElement? = null,
    val postList: JsonElement? = null,
    val accessStrategy: JsonElement? = null,
)

@Serializable
data class ResourceObject(
    val seasons: List<SeasonObject>? = null,
    val source: JsonElement? = null,
    val uploadBy: String? = null,
)

@Serializable
data class SeasonObject(
    val se: Int? = null,
    val maxEp: Int? = null,
    val allEp: String? = null,
    val resolutions: List<ResolutionObject>? = null,
)

@Serializable
data class ResolutionObject(
    val resolution: Int? = null,
    val epNum: Int? = null,
)

@Serializable
data class MetadataObject(
    val title: String? = null,
    val description: String? = null,
    @SerialName("keyWords")
    val keyWords: String? = null,
    val image: String? = null,
)

@Serializable
data class PlayData(
    val streams: List<StreamObject>? = null,
    val freeNum: Int? = null,
    val limited: Boolean? = null,
    val limitedCode: String? = null,
    val dash: List<StreamObject>? = null,
    val hls: List<StreamObject>? = null,
    val hasResource: Boolean? = null,
    val vipLocked: Boolean? = null,
)

@Serializable
data class StreamObject(
    val format: String? = null,
    val id: String? = null,
    val url: String? = null,
    val resolutions: String? = null,
    val size: String? = null,
    val duration: String? = null,
    val codecName: String? = null,
    val signCookie: String? = null,
    val signHeaderKey: String? = null,
    val vipLocked: Boolean? = null,
) {
    fun toVideo(label: String, headers: Headers, subtitleTracks: List<Track>): Video = Video(
        url = url ?: "",
        quality = "$label ${resolutions.toQualityLabel()}",
        videoUrl = url ?: "",
        headers = headers,
        subtitleTracks = subtitleTracks,
        audioTracks = emptyList(),
    )
}

@Serializable
data class CaptionData(
    val captions: List<CaptionObject>? = null,
)

@Serializable
data class CaptionObject(
    val id: String? = null,
    val lan: String? = null,
    val lanName: String? = null,
    val url: String? = null,
)

@Serializable
data class EpisodeData(
    val subjectId: String,
    val se: Int,
    val ep: Int,
    val detailPath: String,
) {
    fun encode() = "$subjectId@$se@$ep@$detailPath"

    companion object {
        fun decode(url: String): EpisodeData {
            val parts = url.split("@", limit = 4)
            return EpisodeData(
                subjectId = parts.getOrNull(0).orEmpty(),
                se = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                ep = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                detailPath = parts.getOrNull(3).orEmpty(),
            )
        }
    }
}

private fun String?.toQualityLabel(): String {
    if (this.isNullOrBlank()) return ""
    val max = split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .maxOrNull()
    return max?.let { "${it}P" } ?: "${this}P"
}
