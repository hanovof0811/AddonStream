package com.haispace

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addSub
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import org.jsoup.nodes.Element

class AnimeVietsub : MainAPI() {
    override var mainUrl = "https://thuviencine.com/"
    override var name = "ThuVienCine"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )
    override val mainPage = mainPageOf(
        "$mainUrl/movies/page/" to "Phim Lẻ",
        "$mainUrl/tv-series/page/" to "Phim Bộ",
        "$mainUrl/top/page/" to "Xu Hướng",
        "$mainUrl/country/vietnam/page/" to "Phim Việt Nam",
        "$mainUrl/danh-sach/list-dang-chieu////trang-" to "DS Anime Đang Chếu",
        "$mainUrl/danh-sach/list-tron-bo/////trang-" to "DS Anime Trọn Bộ",
        )
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val resp = app.get(request.data + page)
        val document = resp.document
        val home = document.select("li.TPostMv").mapNotNull {
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

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val document = request.document
        val wrapContent = document.selectFirst("article.TPost")
        val title = wrapContent.selectFirst("h1.Title").text()
        val description = wrapContent.selectFirst("div.Description").text()
        val poster = wrapContent.selectFirst("img.attachment-img-mov-md").attr("src")
        val year = wrapContent.selectFirst("p.Info").selectFirst("span.Date").selectFirst("a").text().toIntOrNull()
        val infoList = document.selectFirst("ul.InfoList")
        val tags = infoList.select("li").find { it.selectFirst("strong").text() == "Thể loại:" }?.select("a")?.map { it.text() }
        val rating = document.selectFirst("div#star")?.attr("data-score").toRatingInt()
        val tvType  =
            infoList.selectFirst("li.latest_eps").select("a").find { it.text() == "Full" }?.let { TvType.AnimeMovie }
        return if (tvType!=null) {// Movie
            val link = infoList.selectFirst("li.latest_eps").selectFirst("a").attr("href")
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
            }
        }else{
            val directLink = wrapContent.selectFirst("a.watch_button_more").attr("href")
            val episodes = app.get(directLink).document.select("ul.list-episode > li").map {
               val a = it.selectFirst("a")
                val href = a.attr("href")
                val episode = a.text().toIntOrNull()
                val name = "Tập $episode"
                Episode(
                    data = href,
                    name = name,
                    episode = episode,
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val href = this.selectFirst("a").attr("href")
        val poster = this.selectFirst("img.attachment-thumbnail").attr("src")
        val title = this.selectFirst("h2.Title").text()
        var epsiodeStr = this.selectFirst("span.mli-eps")?.text()
        if (epsiodeStr==null) {
            epsiodeStr = ""
        }
        var epsiode = Regex("\\d+").find(epsiodeStr)?.value?.toIntOrNull()
        val  quality = this.selectFirst("span.mli-quality")?.text()
        return if (epsiode != null) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.addSub(epsiode)
            }
        } else if (epsiodeStr!="") {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.addSub(99999)
            }
        }else{
            newMovieSearchResponse(title, href, TvType.AnimeMovie) {
                this.posterUrl = poster
                this.quality =convertQuality(
                    quality.toString()
                )
            }
        }
    }
    private fun convertQuality(quality: String): SearchQuality {
        return when (quality) {
            "CAM FULL HD" -> SearchQuality.HdCam
            "BD FHD" -> SearchQuality.UHD
            "FHD" -> SearchQuality.UHD
            "SD" -> SearchQuality.SD
            "HD" -> SearchQuality.HD
            "CAM" -> SearchQuality.Cam
            else -> SearchQuality.HD
        }
    }
}