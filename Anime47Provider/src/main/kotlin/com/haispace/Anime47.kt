package com.haispace


import android.util.Log
import com.google.gson.Gson
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
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
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.Interceptor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import okhttp3.OkHttpClient
import okhttp3.Response

class Anime47 : MainAPI() {
    override var mainUrl = "https://anime47.de"
    override var name = "Anime47(Anime)"
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
        "$mainUrl/danh-sach/phim-moi/" to "Phim Anime Mới Nhất",
        "$mainUrl/danh-sach/xem-nhieu-trong-mua/" to "Xem nhiều",
        "$mainUrl/the-loai/hoat-hinh-trung-quoc-75/" to "Hoạt hình Trung Quốc",
        "$mainUrl/danh-sach/jpdrama/" to "Live Action",

    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val resp = app.get(request.data + page+".html")
        val document = resp.document
        val home = document.select("ul#movie-last-movie > li").mapNotNull {
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

        val href = mainUrl + this.selectFirst("a").attr("href").substring(1)
        val movie_meta = this.selectFirst("div.movie-meta")
        val poster = this.selectFirst("div.public-film-item-thumb").attr("style").substringAfter("url('").substringBefore("')")
        val title = movie_meta.selectFirst("div.movie-title-1").text()
        val epsiodeStr = movie_meta.selectFirst("span.ribbon").text()
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
        val url = "$mainUrl/tim-nang-cao/?keyword=$newQuery&sapxep=1"
        return app.get(url).document.select("ul#movie-last-movie > li").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val wrapContent = request.document.selectFirst("div.movie-info")
        val imgDetail = wrapContent?.selectFirst("div.col-6.movie-image")
        val poster =  imgDetail?.selectFirst("img")?.attr("src")
        val movieDetail = wrapContent.selectFirst("div.col-6.movie-detail")
        val title = movieDetail.selectFirst("h1.movie-title").selectFirst("span").text()
        val dl = movieDetail.selectFirst("dl.movie-dl").select("dd.movie-dd")
        val tags = dl[1].select("a").map {
            it.text()
        }
        val tvType =  if (dl[2].selectFirst("a").text()=="Movie") TvType.Movie else TvType.TvSeries
        val year = dl[4].selectFirst("a").text().toIntOrNull()
        val trailer = imgDetail?.selectFirst("a#btn-film-trailer")?.attr("data-videourl")
        val link = mainUrl + imgDetail?.selectFirst("a.btn.btn-red")?.attr("href")?.substring(1)
        val descriptions =
            wrapContent.selectFirst("div#film-content").selectFirst("div.news-article").select("p")
        var description = ""
            for (i in descriptions.indices) {
            description+=descriptions[i].text()
        }
        return if (tvType == TvType.TvSeries) {

            val episodes = app.get(link).document.select("div.episodes.col-lg-12.col-md-12.col-sm-12 ul > li").map {
                val href =  it.selectFirst("a").attr("href")
                val episode =
                    Regex("\\d+").find(it.selectFirst("a").text())?.value?.toIntOrNull()
                val name = "Tập $episode"
                Episode(
                    data = href,
                    name = name,
                    episode = episode,
                )
            }

            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                this.tags = tags
                this.year = year
                this.plot = description
                this.addEpisodes(DubStatus.Subbed,episodes)
                addTrailer(trailer)

            }


        } else {
            newMovieLoadResponse(title, url, TvType.Anime,link) {
                this.posterUrl = poster
                this.tags = tags
                this.year = year
                this.plot = description
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
        val id = data.substringAfterLast("/").substringBefore(".html")
        val res = app.get(data)
        val doc = res.document
        val severs = doc.select("div#clicksv > span").map {
            Regex("\\d+").find(it.attr("id"))?.value +""
        }

           val document = app.post("https://anime47.de/player/player.php", headers = mapOf("X-Requested-With" to "XMLHttpRequest"), data = mapOf(
               "ID" to id,
               "SV" to severs[0],
               "SV4" to "4",
           )).document

           val subtitleLink =  "tracks: [{"+ document.data().substringAfterLast("tracks: [{").substringBefore("]")
           val json = document.data().substringAfter("jwplayer(\"player\").setup(").substringBefore("]")+"]}"

                val links = Gson().fromJson(json, Link::class.java)
                val tracks =Regex("""file:\s*"([^"]+)"""").findAll(subtitleLink).map{
                    it.groupValues[1] }.toList()
                links.tracks = tracks
            links.tracks.map {
                if (it.contains("vi.vtt")) {
                    subtitleCallback.invoke(SubtitleFile("vi",mainUrl+ it))
                }
            }
            links.sources.map {
                val host = if (severs[0]=="2") "hlsblg.animevui.com" else "cdn.animevui.com"
                callback.invoke(

                ExtractorLink(
                    source = it.type,
                     name =it.type,
                    url = it.file,
                    referer = "",
                    quality = Qualities.P1080.value,
                    headers = mapOf("Origin" to "https://anime47.de"),
                    isM3u8 = true,

                )
               )}

        return true
    }


    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"

        }
    }
}
data class Link (
    val sources : List<Source>,
    var tracks : List<String>
)
data class Source(
    val file: String,
    val type: String
)


