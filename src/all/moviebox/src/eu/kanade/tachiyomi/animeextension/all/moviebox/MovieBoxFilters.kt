package eu.kanade.tachiyomi.animeextension.all.moviebox

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object MovieBoxFilters {

    const val DEFAULT_VALUE = "All"

    val GENRES = arrayOf(
        "Action" to "Action",
        "Adventure" to "Adventure",
        "Animation" to "Animation",
        "Biography" to "Biography",
        "Comedy" to "Comedy",
        "Crime" to "Crime",
        "Documentary" to "Documentary",
        "Drama" to "Drama",
        "Family" to "Family",
        "Fantasy" to "Fantasy",
        "Film-Noir" to "Film-Noir",
        "Game-Show" to "Game-Show",
        "History" to "History",
        "Horror" to "Horror",
        "Music" to "Music",
        "Musical" to "Musical",
        "Mystery" to "Mystery",
        "News" to "News",
        "Reality-TV" to "Reality-TV",
        "Romance" to "Romance",
        "Sci-Fi" to "Sci-Fi",
        "Short" to "Short",
        "Sport" to "Sport",
        "Talk-Show" to "Talk-Show",
        "Thriller" to "Thriller",
        "War" to "War",
        "Western" to "Western",
        "Other" to "Other",
    )

    val COUNTRIES = arrayOf(
        "United States" to "United States",
        "United Kingdom" to "United Kingdom",
        "Korea" to "Korea",
        "Japan" to "Japan",
        "Bangladesh" to "Bangladesh",
        "China" to "China",
        "Egypt" to "Egypt",
        "France" to "France",
        "Germany" to "Germany",
        "India" to "India",
        "Indonesia" to "Indonesia",
        "Iraq" to "Iraq",
        "Italy" to "Italy",
        "Ivory Coast" to "Ivory Coast",
        "Kenya" to "Kenya",
        "Lebanon" to "Lebanon",
        "Mexico" to "Mexico",
        "Morocco" to "Morocco",
        "Nigeria" to "Nigeria",
        "Pakistan" to "Pakistan",
        "Philippines" to "Philippines",
        "Russia" to "Russia",
        "Saudi Arabia" to "Saudi Arabia",
        "South Africa" to "South Africa",
        "Spain" to "Spain",
        "Syria" to "Syria",
        "Thailand" to "Thailand",
        "Malaysia" to "Malaysia",
        "Turkey" to "Turkey",
        "Other" to "Other",
    )

    val YEARS = arrayOf(
        "2026" to "2026",
        "2025" to "2025",
        "2024" to "2024",
        "2023" to "2023",
        "2022" to "2022",
        "2021" to "2021",
        "2020" to "2020",
        "2010s" to "2010s",
        "2000s" to "2000s",
        "1990s" to "1990s",
        "1980s" to "1980s",
        "Other" to "Other",
    )

    val CLASSIFIES = arrayOf(
        "English dub" to "English dub",
        "French dub" to "French dub",
        "Hindi dub" to "Hindi dub",
        "Bengali dub" to "Bengali dub",
        "Urdu dub" to "Urdu dub",
        "Punjabi dub" to "Punjabi dub",
        "Tamil dub" to "Tamil dub",
        "Telugu dub" to "Telugu dub",
        "Malayalam dub" to "Malayalam dub",
        "Kannada dub" to "Kannada dub",
        "Arabic dub" to "Arabic dub",
        "Arabic sub" to "Arabic sub",
        "Tagalog dub" to "Tagalog dub",
        "Indonesian dub" to "Indonesian dub",
        "Russian dub" to "Russian dub",
        "Kurdish sub" to "Kurdish sub",
        "Spanish dub" to "Spanish dub",
        "Spanish sub" to "Spanish sub",
        "SpanishLatam dub" to "SpanishLatam dub",
    )

    val SORTS = arrayOf(
        "ForYou" to "ForYou",
        "Hottest" to "Hottest",
        "Latest" to "Latest",
        "Rating" to "Rating",
    )

    open class SelectFilter(name: String, pairs: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(
            name,
            (arrayOf(DEFAULT_VALUE to DEFAULT_VALUE) + pairs).map { it.first }.toTypedArray(),
            0,
        ) {
        private val allPairs = arrayOf(DEFAULT_VALUE to DEFAULT_VALUE) + pairs

        val selectedValue: String
            get() = allPairs.getOrNull(state)?.second ?: DEFAULT_VALUE
    }

    class GenreFilter : SelectFilter("Genre", GENRES)
    class CountryFilter : SelectFilter("Country", COUNTRIES)
    class YearFilter : SelectFilter("Year", YEARS)
    class ClassifyFilter : SelectFilter("Language", CLASSIFIES)
    class SortFilter : SelectFilter("Sort by", SORTS)
}
