package com.haispace


import com.google.gson.Gson
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addSub
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import java.net.URLDecoder

class Anime47 : MainAPI() {
    override var mainUrl = "https://www.vipphim.wiki"
    val baseAPI = "https://api.adda.link"
    val baseVideo = "https://cdn-stream.go9.me/"
    override var name = "Vip Phim"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true



    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
    }
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )
    override val mainPage = mainPageOf(
        "$baseAPI/api/films/latest?isLatest=true&pageSize=25&pageIndex=" to "Phim Mới",
        "$baseAPI/api/films/odd?pageSize=25&type=odd&pageIndex=" to "Phim Lẻ",
        "$baseAPI/api/films/series?pageSize=25&type=series&pageIndex=" to "Phim Bộ",
        "$baseAPI/api/films/latest?category_id=64b7468242dc7256350907e6&isLatest=true&pageSize=25&pageIndex=" to "Phim Chiếu Rạp",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home =Gson().fromJson(app.get(request.data +page).document.body().text(),FilmDetail::class.java).data.data.map {
            it.toSearchResult(null)
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")

    private fun Film.toSearchResult(isSearch : Boolean?): SearchResponse {

        val href = "$baseAPI/api/films/film-by-slug/${this.slugUrl} ${if (isSearch==true) "true" else "false"}"
        val poster = if(!this.poster.contains("https://")) "${if (isSearch == true) baseAPI else baseVideo}${if (this.poster.contains("//")) this.poster.replace("//", "/") else this.poster}" else this.poster
        val title = this.title
        val nameEnglish = this.title_eng
        val type = if (this.type.contains("series")) TvType.TvSeries else TvType.Movie
        return if (type == TvType.TvSeries) {
            val episodes = this.availableSeasons
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.otherName = nameEnglish
                this.addSub(episodes)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.quality = SearchQuality.UHD
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val newQuery = decode(query)
        val url = "$baseAPI/api/films/search-film?pageSize=25&pageIndex=0&searchKeyword=$newQuery"
        return Gson().fromJson(app.get(url).document.body().text(),FilmDetail::class.java).data.data.map {
            it.toSearchResult(true)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val datas = url.split(" ")
        val urlFix = datas[0]
        val isSearch = datas[1].toBoolean()
        val video =
            Gson().fromJson(app.get(urlFix).document.body().text(), FilmDetail::class.java).data.video

        val poster = if (!video.thumbnail.contains("https://")) "${if (isSearch) baseAPI+"/" else baseVideo}${video.thumbnail.replace("//", "/")}" else video.thumbnail

        val title = video.title

        val tags = video.filmCategories.map { it.name }
        val tvType =  if (video.type.contains("series")) TvType.TvSeries else TvType.Movie
        val year = video.movie_release.toIntOrNull()
        var idCategories = ""
        video.filmCategories.forEach {
            idCategories+=it._id+","
        }
        val recommendations = suggest(idCategories)
        val description = video.description
        return if (tvType == TvType.TvSeries) {
            val episodes = video.parent[0].seasons.map {
                val href =  "$baseAPI/api/films/film-by-slug/${it.slugUrl} ${isSearch.toString()}"
                val episode = it.epsiode
                val name = "Tập $episode"
                Episode(
                    data = href,
                    name = name,
                    episode = episode,
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries,episodes) {
                this.recommendations = recommendations
                this.posterUrl = poster
                this.tags = tags
                this.year = year
                this.plot = description
            }


        } else {
            newMovieLoadResponse(title, url, TvType.Anime,url) {
                this.recommendations = recommendations
                this.posterUrl = poster
                this.tags = tags
                this.year = year
                this.plot = description
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val datas = data.split(" ")
        val urlFix = datas[0]
        val isSearch = datas[1].toBoolean()
        val video =
            Gson().fromJson(app.get(urlFix).document.body().text(), FilmDetail::class.java).data.video
        val videoUrl  = if(video.hlsPath.isNullOrEmpty()) "${if (isSearch) baseAPI else baseVideo}${video.video_location}" else "${if (isSearch) baseAPI else baseVideo}${video.hlsPath}"
        Gson().fromJson(app.get("$baseAPI/api/subtitle/${video._id}/?pageSize=5&pageIndex=0&languageSub=vietnamese").document.body().text(),FilmDetail::class.java).data.data.map {
            val link = baseAPI+it.file_sub
            subtitleCallback.invoke(SubtitleFile("vietnam",link))
        }

        var isM3u8 : Boolean
        var source : String
        if (videoUrl.contains(".m3u8")) {
            source = "HLS"
            isM3u8 = true
        }else {
            source = "MP4"
            isM3u8 = false
        }
        callback.invoke(
                ExtractorLink(
                    source = source,
                    name =source,
                    url = videoUrl,
                    referer = "https://www.vipphim.wiki/",
                    quality = Qualities.P1080.value,
                    isM3u8 = isM3u8,
                )
               )
        return true
    }
    private suspend fun suggest(id : String) : List<SearchResponse>{
      return Gson().fromJson(app.get("$baseAPI/api/films/relation?category_id=$id").document.body().text(),FilmDetail::class.java).data.data.map {
           it.toSearchResult(null)
       }

    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"

        }
    }
}


