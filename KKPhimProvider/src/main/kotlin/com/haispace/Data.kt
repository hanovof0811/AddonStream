package com.haispace
data class TypeData(
    val status : String,
    val data : DataOfTypeData
)

data class DataOfTypeData(
    val items : List<Data>
)
data class DataSingle(
    val data: Data,
    val items : List<ItemMovie>
)
data class Data(
    val status : String,
    val items : List<MovideDetail>,// general info film
    val movie : MovideDetail,
    val episodes : List<Episodes>,
)
data class MovideDetail(
    val id : String,
    val name : String,
    val slug : String,
    val origin_name : String,
    val poster_url : String,
    val content: String,
    val type : String,//{series,hoathinh,single,tvshows..}
    val status : Boolean,
    val thumb_url : Boolean,
    val trailer_url : String,
    val episode_current : String,
    val episode_total: String,
    val quality : String,
    val year : String,
    val actor : List<String>,
    val category: List<Category>,
    val country : List<Country>,
)
data class ItemMovie(
    val id : String,
    val name : String,
    val slug : String,
    val origin_name : String,
    val poster_url : String,
    val thumb_url : String,
    val year : String
)
data class Episodes(
    val server_name : String,
    val server_data: List<Episode>
)
data class Episode(
    val name : String,
    val filename : String,//sourcename
    val link_m3u8 : String
)
data class Category(
    val name: String
)
data class Country(
    val name: String
)
data class Genre(
    val name: String
)

