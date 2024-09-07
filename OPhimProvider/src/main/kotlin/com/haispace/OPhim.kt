package com.haispace


import com.google.gson.Gson
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
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
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import kotlin.reflect.typeOf

class OPhim : MainAPI() {
    override var mainUrl = "https://ophim.com.co"
    override var name = "OPhim"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    private var directUrl = mainUrl
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    override val mainPage = mainPageOf(
        "$mainUrl/chieu-rap/page/" to "Phim Chiếu Rạp",
        "$mainUrl/release/2024/page/" to "Phim Mới",
        "$mainUrl/hoat-hinh/page/" to "Phim Hoạt Hình",
        "$mainUrl/country/viet-nam/page/" to "Phim Việt Nam",
        "$mainUrl/country/han-quoc/page/" to "Phim Hàn Quốc",
        "$mainUrl/country/trung-quoc/page/" to "Phim Trung Quốc",
        "$mainUrl/country/thai-lan/page/" to "Phim Thái Lan",

        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val resp = app.get(request.data + page)
        val document = resp.document
        val home = document.select("a.halim-thumb").mapNotNull {
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

    private fun Element.toSearchResult(): SearchResponse {
        val href = this.attr("href")
        val poster = this.selectFirst("img").attr("data-src")
        val title = this.attr("title")
        val epsiodeStr = this.selectFirst("span.episode").text()
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
        val link = "$mainUrl/tim-kiem/$newQuery/"
        val res = app.get(link)
        val document = res.document

        return document.select("ul.list-film li").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val wrapContent = request.document.selectFirst("div.wrap-content")
        val poster =
            wrapContent.selectFirst("div.movie-poster")?.selectFirst("img.movie-thumb")?.attr("src")
        val movieDetail = wrapContent.selectFirst("div.movie-detail")
        val title = movieDetail.selectFirst("h1.entry-title").text()
        val year = movieDetail.selectFirst("span.released").selectFirst("a").text().toIntOrNull()
        val tags = movieDetail.selectFirst("p.category")?.select("a")?.map { it.text() }
        val link = wrapContent.selectFirst("div.movie-poster")?.selectFirst("div.halim-watch-box")
            ?.selectFirst("a")?.attr("href")
        val tvType =
            if (movieDetail.select("p.lastEp").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val description =
            wrapContent.selectFirst("div.halim-entry-box")?.selectFirst("article.item-content")
                ?.selectFirst("p")?.text()
        val rating =
            movieDetail.selectFirst("div.ratings_wrapper")?.selectFirst("span.score")?.text()
                .toRatingInt()
        val actors = movieDetail.select("p.actors")?.get(1)?.select("a")?.map { it.text() }


        return if (tvType == TvType.TvSeries) {
            val episodes =
                wrapContent.selectFirst("div.show_all_eps").select("ul.halim-list-eps > li").map {
                    val href = it.select("a").attr("href")
                    val episode =
                        it.selectFirst("a")?.text()?.replace(Regex("[^0-9]"), "")?.trim()
                            ?.toIntOrNull()
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
                addActors(actors)
            }
        } else {
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
        val res = app.get(data)
        val document = res.document

        val script = res.document.selectFirst("script:containsData(var halim_cfg)").data().substringAfter("var halim_cfg = ").substringBefore("</script>")
        val episode_slug = script.substringAfter("\"episode_slug\":").substringBefore(",").trim().split("\"")[1]


        val dataNonce = document.selectFirst("body").attr("data-nonce")
        val postId = document.selectFirst("body").attr("class").substringAfter("postid-").substringBefore(" ")
        val dataLink = app.get("https://ophim.com.co/wp-content/themes/halimmovies/player.php", params = mapOf("episode_slug" to episode_slug,
                "server_id" to "1","subsv_id" to "","post_id" to postId,"nonce" to dataNonce,"custom_var" to ""), headers = mapOf("x-requested-with" to "XMLHttpRequest")).text

        val link1 = Gson().fromJson(dataLink,  Data::class.java).data.sources.substringAfter("sources: [{\"file\":\"").substringBefore("\",\"type").replace("\\","")
        val link2 = link1.replace("index.m3u8","2000kb/hls/index.m3u8")
        listOf(
            Pair(link1, "HLS"),
            Pair(link2,"HLS")
        ).map { (link, source) ->
            println(link)
            callback.invoke(
                ExtractorLink(
                    source,
                    source,
                    link,
                    referer = "$directUrl/",
                    quality = Qualities.P1080.value,
                    INFER_TYPE,
                )
            )
        }
        return true
    }


    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"

        }
    }
}