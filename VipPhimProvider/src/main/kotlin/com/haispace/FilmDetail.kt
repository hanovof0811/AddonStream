package com.haispace

import com.google.gson.annotations.SerializedName

data class FilmDetail(
    val statusCode : Int,
    val message : String,
    val data : Data
)
data class Data(
    val count: Int,
    val page : Int,
    val data: List<Film>,
    val video: Video
)
data class Film(
    val _id : String,
    val title : String,
    val title_eng : String,
    val video_id : String,
    val slug : String,
   @SerializedName("thumbnail") val poster: String,
    val rate : String,
    val type : String,
    val quality: String,
    val totalSeason: Int,
    val totalSubtitleEn: Int,
    val totalSubtitleVi: Int,
    val availableSeasons: Int,
    val slugUrl: String,// tập đầu tiên
    val filmCategories : List<Category>,
    val file_sub: String,
    val languageSub: String
)
data class Video(
    val _id : String,
    val title : String,
    val thumbnail: String,
    val type:String,
    val description: String,
    val video_location: String,
    val movie_release: String,
    val subtitle : String,
    val hlsPath: String,
    val parent : List<VideoParent>,
    val filmCategories : List<Category>,
    val country_name : String

)
data class VideoParent(
    val seasons: List<Seasons>
)
data class Seasons(
    val _id: String,
    val slugUrl: String,
    @SerializedName("priority") val epsiode: Int
)
data class Category(
    val _id: String,
    val name : String
)