package com.haispace


import com.google.gson.Gson
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SettingsJson
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.addQuality
import com.lagradost.cloudstream3.addSeasonNames
import com.lagradost.cloudstream3.addSub
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder

class KKPhim : MainAPI() {
    override var mainUrl = "https://phimapi.com"
    override var name = "KKPhim"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    private val urlFilm = "$mainUrl/phim/"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    override val mainPage = mainPageOf(
        "$mainUrl/danh-sach/phim-moi-cap-nhat?page=" to "Phim mới nhất",
        "$mainUrl/v1/api/danh-sach/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/v1/api/danh-sach/phim-bo?page=" to "Phim Bộ ",
        "$mainUrl/v1/api/danh-sach/hoat-hinh?page=" to "Phim Hoạt Hình",
        "$mainUrl/v1/api/danh-sach/tv-shows?page=" to "TV-Shows",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val resp = app.get(request.data + page)
        val document = resp.document
        val home = toMainPage(document.body().text())
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }
    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val json = request.document.body().text()
       val data = Gson().fromJson(json, Data::class.java)
        val movie = data.movie
        val title = movie.name
        val poster = movie.poster_url
        val year = movie.year.toInt()
        val description = movie.content
        val trailer = movie.trailer_url
        val tags = movie.category.map { it.name }
        val actors = movie.actor
        val tvType =  if (movie.type == "single") TvType.Movie else if (movie.type == "hoathinh") TvType.Anime else TvType.TvSeries
        return if (tvType == TvType.TvSeries) {
            val episodes = data.episodes[0].server_data.map{

                Episode(
                    data = it.link_m3u8 ,
                    name = it.name,
                    episode = Regex("\\d+").find(it.name)?.value?.toInt(),
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            val link = data.episodes[0].server_data[0].link_m3u8
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
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
                    "KKPhim",
                    "KKPhim",
                    data,
                    referer = "",
                    quality = Qualities.P1080.value,
                    INFER_TYPE,
                )
            )
        return true
        }

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")

    override suspend fun search(query: String): List<SearchResponse> {
        val newQuery = decode(query)
        val link = "$mainUrl/v1/api/tim-kiem?keyword=$newQuery"
        val res = app.get(link)
        val json = res.document.body().text()

        return toMainPage(json)
    }
    private fun toMainPage( json: String) : List<SearchResponse>{
        val data = Gson().fromJson(json, DataSingle::class.java)

      return  if (data.data!=null) {
          data.data.items.map {
              val title = it.name
              val href = "$urlFilm${it.slug}"
              val posterUrl = it.poster_url
              val episode = Regex("\\d+").find(it.episode_current)?.value?.toIntOrNull()
              val type =
                  if (it.type == "single") TvType.Movie else if (it.type == "hoathinh") TvType.Anime else TvType.TvSeries
              if (type == TvType.TvSeries || type == TvType.Anime) {
                  newAnimeSearchResponse(title, href, type) {
                      this.posterUrl = posterUrl
                      addSub(episode)
                      addQuality(it.quality)
                  }
              } else {
                  newMovieSearchResponse(title, href, type) {
                      this.posterUrl = posterUrl
                      addQuality(it.quality)
                  }
              }
          }
      }else{
          data.items.map {
              val title = it.name
              val href = "$urlFilm${it.slug}"
              val posterUrl = it.poster_url
              newAnimeSearchResponse(title, href) {
                  this.posterUrl = posterUrl

              }
          }
      }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"

        }
    }
}