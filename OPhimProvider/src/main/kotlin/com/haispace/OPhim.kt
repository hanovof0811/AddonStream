package com.haispace


import com.google.gson.Gson
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.Interceptor
import okhttp3.Response

import java.net.URI
import java.net.URLDecoder

class OPhim : MainAPI() {
    override var mainUrl = "https://ophim1.com"
    override var name = "OPhim(OPhim1.com)"
    val urlImage = "https://img.ophim.live/uploads/movies/"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    override val mainPage = mainPageOf(
        "$mainUrl/v1/api/danh-sach/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/v1/api/danh-sach/phim-bo?page=" to "Phim Bộ",
        "$mainUrl/v1/api/danh-sach/hoat-hinh?page=" to "Phim Hoạt Hình",
        "$mainUrl/v1/api/danh-sach/phim-thuyet-minh?page=" to "Phim Thuyết Minh",
        "$mainUrl/v1/api/danh-sach/phim-long-tieng?page=" to "Phim Lồng Tiếng",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val resp = app.get(request.data + page)
        val jsonSearchResponse = resp.document.body().text()
        val home = Gson().fromJson(jsonSearchResponse, ReasponseBySearch::class.java).data.films.map{
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }
    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")

    private fun Film.toSearchResult(): SearchResponse {
        val href = "$mainUrl/phim/${this.slug}"
        val poster = urlImage+ this.poster_url
        val title = this.name
        return if (this.type.contains("series")) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                if (this@toSearchResult.lang.contains("Lồng Tiếng")&&this@toSearchResult.lang.contains("Vietsub")){
                    addDubStatus(DubStatus.Dubbed)
                    addDubStatus(DubStatus.Subbed)
                }else if (this@toSearchResult.lang.contains("Vietsub")) {
                    addDubStatus(DubStatus.Subbed)
                }else{
                    addDubStatus(DubStatus.Dubbed)
                }

            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

//    override suspend fun search(query: String): List<SearchResponse> {
//        val newQuery = decode(query)
//        val link = "$mainUrl/tim-kiem/$newQuery/"
//        val res = app.get(link)
//        val document = res.document
//
//        return document.select("ul.list-film li").map {
//            it.toSearchResult()
//        }
//    }

    override suspend fun load(url: String): LoadResponse {
        val jsonResponse = app.get(url).document.body().text()
        val reasponseBySearch = Gson().fromJson(jsonResponse,ReasponseBySearch::class.java)
        val movie = reasponseBySearch.movie
        val episodes = reasponseBySearch.episodes
        val poster = movie.poster_url

        val title = movie.name
        val year = movie.year.toInt()
        val tags = movie.category.map { it.name }
        val tvType = if (movie.type.contains("series")) TvType.TvSeries else TvType.Movie
        val description = movie.description
        return if (tvType == TvType.TvSeries) {
            val allEpisodes = ArrayList<Pair<String,List<Episode>>>()
            episodes.map {
                allEpisodes.add(Pair(it.server_name,it.server_data.map {
                    Episode(
                        data = it.link_m3u8 ,
                        name = "Tập ${it.name}",
                        episode = Regex("\\d+").find(it.name)?.value?.toInt(),
                    )
                }))
            }
            newAnimeLoadResponse(title, url, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                allEpisodes.map {
                    val (server,epsiodes) = it
                    if (!server.contains("Lồng Tiếng")) {
                        addEpisodes(DubStatus.Subbed,epsiodes)
                    }else{
                        addEpisodes(DubStatus.Dubbed,epsiodes)
                    }
                }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodes[0].server_data[0].link_m3u8) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating

            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
            callback.invoke(
                ExtractorLink(
                    "OPhim",
                    "OPhim",
                    data,
                    "",
                    quality = Qualities.P1080.value,
                    INFER_TYPE,
                )
            )
        return true
    }


    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"

        }
    }
}