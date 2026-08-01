package eu.kanade.tachiyomi.animeextension.all.moviebox

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class MovieBoxFactory : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        MovieBox(MovieBox.CHANNEL_MOVIE, MovieBox.TAB_MOVIE, "Movies"),
        MovieBox(MovieBox.CHANNEL_TV, MovieBox.TAB_TV, "TV Series"),
        MovieBox(MovieBox.CHANNEL_ANIME, MovieBox.TAB_ANIME, "Anime"),
    )
}
