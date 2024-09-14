package com.haispace


import com.google.gson.Gson
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addQuality
import com.lagradost.cloudstream3.addSub
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import java.net.URLDecoder

class NguonC : MainAPI() {
    override var mainUrl = "https://phim.nguonc.com"
    override var name = "NguonC"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    private val urlFilm = "$mainUrl/api/film/"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    override val mainPage = mainPageOf(
        "$mainUrl/api/films/phim-moi-cap-nhat?page=" to "Phim mới cập nhật",
        "$mainUrl/api/films/danh-sach/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/api/films/danh-sach/phim-bo?page=" to "Phim Bộ ",
        "$mainUrl/api/films/danh-sach/phim-dang-chieu?page=" to "Phim Đang Chiếu",
        "$mainUrl/api/films/danh-sach/tv-shows?page=" to "TV-Shows",
        "$mainUrl/api/films/the-loai/hoat-hinh?page=" to "Hoạt Hình",
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

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
    }
    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val json = request.document.body().text()
       val data = Gson().fromJson(json, Data::class.java)
        val movie = data.movie
        val title = movie.name
        val poster = movie.poster_url
        val year = movie.category.nam.list[0].name.toIntOrNull()
        val description = movie.description

        val tags = movie.category.theloai.list.map { it.name }
        val actors = movie?.casts?.split(",")
        val tvType =  if (movie.total_episodes == 1) TvType.Movie else TvType.TvSeries
        return if (tvType == TvType.TvSeries) {
            val episodes = data.movie.episodes[0].items.map{
                Episode(
                    data = it.embed ,
                    name = "Tập ${it.name}",
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
            }
        } else {
            val link = data.movie.episodes[0].items[0].embed
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
            }
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
            val refer = data.substringBefore("embed.php")
            callback.invoke(
                ExtractorLink(
                    "NguonC",
                    "NguonC",
                    data.replace("embed.php","get.php"),
                    referer = refer,
                    quality = Qualities.P1080.value,
                    isM3u8 = true,
                )
            )
        return true
        }

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")

    override suspend fun search(query: String): List<SearchResponse> {
        val newQuery = decode(query)
        val link = "$mainUrl/api/films/search?keyword=$newQuery"
        val res = app.get(link)
        val json = res.document.body().text()

        return toMainPage(json)
    }
    private fun toMainPage( json: String) : List<SearchResponse>{
        val data = Gson().fromJson(json, DataSingle::class.java)

      return data.items.map {
              val title = it.name
              val href = "$urlFilm${it.slug}"
              val posterUrl = it.poster_url
              val episode = Regex("\\d+").find(it.current_episode)?.value?.toIntOrNull()
              val type =
                  if (it.total_episodes == 1) TvType.Movie else TvType.TvSeries
              if (type == TvType.TvSeries ) {
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
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"

        }
    }
}