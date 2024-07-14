package dev.anilbeesetti.nextplayer.core.remotesubs.api.opensubtitles.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureDetails(
    @SerialName("episode_number")
    val episodeNumber: Int,
    @SerialName("feature_id")
    val featureId: Int,
    @SerialName("feature_type")
    val featureType: String,
    @SerialName("imdb_id")
    val imdbId: Int,
    @SerialName("movie_name")
    val movieName: String,
    @SerialName("parent_feature_id")
    val parentFeatureId: Int,
    @SerialName("parent_imdb_id")
    val parentImdbId: Int,
    @SerialName("parent_title")
    val parentTitle: String,
    @SerialName("parent_tmdb_id")
    val parentTmdbId: Int,
    @SerialName("season_number")
    val seasonNumber: Int,
    @SerialName("title")
    val title: String,
    @SerialName("tmdb_id")
    val tmdbId: Int,
    @SerialName("year")
    val year: Int
)