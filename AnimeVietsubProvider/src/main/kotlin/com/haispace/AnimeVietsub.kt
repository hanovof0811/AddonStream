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
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addSub
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.jsoup.nodes.Element
import java.net.URLDecoder

class AnimeVietsub : MainAPI() {
    override var mainUrl = "https://animevietsub.pub"
    override var name = "AnimeVietsub"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )
    override val mainPage = mainPageOf(
        "$mainUrl/anime-le/page/" to "Anime Lẻ",
        "$mainUrl/anime-bo/page/" to "Anime Bộ",
        "$mainUrl/the-loai/hai-huoc/page/" to "Hài Hước",
        "$mainUrl/hoat-hinh-trung-quoc/trang-" to "HH TQ",
        "$mainUrl/danh-sach/list-dang-chieu/////trang-" to "DS Anime Đang Chếu",
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
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }
    private fun decode(input: String): String = URLDecoder.decode(input, "utf-8")
    override suspend fun search(query: String): List<SearchResponse>? {
        val resp = app.get(decode("$mainUrl/tim-kiem/$query/"))
        val document = resp.document
        return document.select("li.TPostMv").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
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

            val ep =link.substringAfterLast("a").substringBeforeLast("/") +" "+app.get(link).document.body().selectFirst("ul.list-episode > li > a").attr("data-hash")
            newMovieLoadResponse(title, url, TvType.Movie, ep) {
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
                val href = url.substringAfterLast("a").substringBeforeLast("/") +" "+a.attr("data-hash")
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.substringBefore(" ")
        val dataHash = data.substringAfter(" ")
        val json = app.post("$mainUrl/ajax/player", headers = mapOf("x-requested-with" to "XMLHttpRequest"),data = mapOf("id" to id, "link" to dataHash)).document.body().text()
        val data = Gson().fromJson(json, Data::class.java)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("text", data.link[0].file)
            .addFormDataPart("url", dataHash)
            .build()
       val link =  app.post("https://decode-api.vercel.app/animevietsub/get-link", headers = mapOf("content-type" to "multipart/form-data"), requestBody = requestBody).body.string().replace("\"", "")
        callback.invoke(
            ExtractorLink(
            source = "HLS",
            name = "HLS",
            url = link, referer = "$mainUrl/",
                quality = Qualities.P1080.value,
                isM3u8 = true
        )
        )

        return true
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
data class Data(
    val _fxStatus: Int,
    val success: Int,
    val title:String,
    val link: List<Link>

)
data class Link(
    val file: String
)