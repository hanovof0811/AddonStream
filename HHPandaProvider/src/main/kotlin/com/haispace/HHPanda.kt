package com.haispace


import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
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
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder

class HHPanda : MainAPI() {
    override var mainUrl = "https://hhpanda.city"
    override var name = "HHPanda(Anime)"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    override val mainPage = mainPageOf(
        "$mainUrl/filter?t=1&y=&od=publish_date&page=" to "Phim Anime Mới Nhất",
        "$mainUrl/moi-cap-nhat/trang-" to "Phim Mới Cập Nhật(Đa số TQ)",
        "$mainUrl/the-loai/harem/trang-" to "Phim Harem",
        "$mainUrl/the-loai/anime/trang-" to "Phim Anime",
        "$mainUrl/hoan-thanh/trang-" to "Phim đã hoàn thành",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val resp = app.get(request.data + page)
        val document = resp.document
        val home = document.select("div.film-list > li").mapNotNull {
            it.toSearchResult()
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

    private fun Element.toSearchResult(): SearchResponse {
        val sourceAll = this.selectFirst("a.myui-vodlist__thumb")
        val href = mainUrl + sourceAll.attr("href")

        val poster = sourceAll.attr("data-src")
        val title = this.selectFirst("h4.title").selectFirst("a").text()
        val epsiodeStr = sourceAll.selectFirst("span.pic-tag").text()
        var epsiode = Regex("\\d+").find(epsiodeStr)?.value?.toIntOrNull()
        return if (epsiode != null) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.addSub(epsiode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val newQuery = decode(query)
        val link = "$mainUrl/search?q=$newQuery"
        val res = app.get(link)
        val document = res.document

        return document.select("div.film-list > li").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val wrapContent = request.document.selectFirst("div.detail-bl")
        val poster =
            wrapContent?.selectFirst("div.myui-content__thumb")?.selectFirst("img")?.attr("src")
        val movieDetail = wrapContent.selectFirst("div.myui-content__detail")
        val title = movieDetail.selectFirst("h1.title").text()
        val year =
            Regex("\\d+").find(movieDetail.selectFirst("h2.title2").text())?.value?.toIntOrNull()
//        val link =
//            mainUrl + wrapContent.selectFirst("div.btn-block")?.selectFirst("a")?.attr("href")
        val id = url.substringBefore(".html").substringAfterLast("/")
        val tvEpisode = app.post(
            mainUrl + "/ajax-list-ep?film_id=$id",
            headers = mapOf("x-requested-with" to "XMLHttpRequest")
        ).document.selectFirst("div.myui-panel_bd").select("div.tab-pane").take(2)
        val tvType = if (tvEpisode.isNotEmpty()) TvType.TvSeries else TvType.Movie
        val description =
            movieDetail.selectFirst("div.myui-panel-box")?.selectFirst("div.myui-panel_bd")
                ?.selectFirst("p")?.text()
        val rating =
            wrapContent.selectFirst("div#star")?.attr("data-score")?.toRatingInt()
        val recommendations = getRecomendation(request.document)
        return if (tvType == TvType.TvSeries) {

            val episodesSub =
                tvEpisode[0].select("ul.myui-content__list > li").map {
                    val href = mainUrl + it.selectFirst("a").attr("href")
                    val episode =
                        Regex("\\d+").find(it.selectFirst("a").text())?.value?.toIntOrNull()
                    val name = "Tập $episode"
                    Episode(
                        data = href,
                        name = name,
                        episode = episode,
                    )
                }.reversed()

            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.rating = rating
                this.recommendations = recommendations
                if (tvEpisode.size>1&&tvEpisode[1].select("h3.server_text").text().contains("Thuyết Minh")) {
                    val episodesDub =
                        tvEpisode[1].select("ul.myui-content__list > li").map {
                            val href = mainUrl + it.selectFirst("a").attr("href")
                            val episode =
                                Regex("\\d+").find(it.selectFirst("a").text())?.value?.toIntOrNull()
                            val name = "Tập $episode"
                            Episode(
                                data = href,
                                name = name,
                                episode = episode,
                            )
                        }
                    this.addEpisodes(DubStatus.Dubbed,episodesDub.reversed())
                }
                this.addEpisodes(DubStatus.Subbed,episodesSub)
            }


        } else {
            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.rating = rating
                this.recommendations = recommendations
            }
        }
    }
   suspend fun getRecomendation(document: Document) : List<SearchResponse> {
        return document.select("ul#type > li").map {
           it.toSearchResult()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data)
        val document = res.document
        val data_href = document.selectFirst("div#ploption").selectFirst("a").attr("data-href")
        val referString = getSourceEmbed(data_href)
        val link = "https://cloudasiatv.xyz" + app.get(url = referString,
            headers = mapOf("referer" to mainUrl + "/")
        ).document.body().toString().substringAfter("const options = {")
            .substringBefore("};").substringAfter(" file: \"").substringBefore("\"")
        listOf(
            Pair(link,"HLS")
        ).map { (link, source) ->
            callback.invoke(
                ExtractorLink(
                    source,
                    source,
                    link,
                    referer = referString,
                    quality = Qualities.P1080.value,
                    isM3u8 = true,
                )
            )
        }
        return true
    }

    suspend fun getSourceEmbed(url: String): String {
        return app.post(
            "$mainUrl$url", headers = mapOf("x-requested-with" to "XMLHttpRequest")
        ).document.body().toString().substringAfter("src=\"\\&quot;").substringBefore("\\")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"

        }
    }
}