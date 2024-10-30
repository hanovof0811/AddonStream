package com.haispace

import com.google.gson.annotations.SerializedName

data class ReasponseBySearch (
 val status: String,//"success"
 val data: Data,//for search response
 val movie: Film,//for movie response
 val episodes: List<ServerEp>//for movie response

 )
data class Data(
 @SerializedName("items")val films : List<Film>
)

data class Film(
 val name : String,
 val slug : String,
 val origin_name: String,
 val type:String,
 @SerializedName("content")val description:String,
 val poster_url: String,
 val thumb_url:String,
 val episode_current: String,
 val quality: String,
 val lang: String,//Lồng Tiếng
 val year: Int,
 val category: List<Category>
)
data class ServerEp(
 val server_name: String,
 val server_data: List<Link>
)
data class Link(
 val name:String,//tap
 val filename: String,
 val link_m3u8: String
)
data class Category(
 val name: String,
)