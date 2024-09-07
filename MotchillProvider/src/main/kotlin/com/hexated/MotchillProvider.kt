package com.hexated

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.net.Proxy


class MotchillProvider : MainAPI() {
    override var mainUrl = "https://motchilltv.vc"
    override var name = "Motchill"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    val serverFixUrl = "https://proxycros.onrender.com/index.m3u8?url="
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/phim-moi?page=" to "Phim Mới",
        "$mainUrl/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/phim-bo?page=" to "Phim Bộ",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.group").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div > a > h3").text().trim()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("img")!!.attr("src")
        val temp = this.select("span.absolute").text()
        return if (temp.contains(Regex("\\d"))) {
            val episode = Regex("(\\((\\d+))|(\\s(\\d+))").find(temp)?.groupValues?.map { num ->
                num.replace(Regex("\\(|\\s"), "")
            }?.distinct()?.firstOrNull()?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else if (temp.contains(Regex("Trailer"))) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            val quality = temp.replace(Regex("(-.*)|(\\|.*)|(?i)(VietSub.*)|(?i)(Thuyết.*)"), "").trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search?searchText=$query"
        val document = app.get(link).document

        return document.select("article.group").map {
            it.toSearchResult()
        }
    }
    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val document = request.document

        val title = document.selectFirst("h1")?.text()?.trim().toString()
        val link = fixUrl(document.selectXpath("//*[@id=\"__nuxt\"]/div/div[1]/div/main/div[4]/div/section[1]/div[1]/div/div[1]/div[2]/div[3]/div[1]/a").attr("href"))
        val poster = document.selectXpath("//*[@id=\"__nuxt\"]/div/div[1]/div/main/div[4]/div/section[1]/div[1]/div/div[1]/div[1]/div/img").attr("src")
        val year = document.selectXpath("//*[@id=\"__nuxt\"]/div/div[1]/div/main/div[4]/div/section[1]/div[1]/div/div[1]/div[2]/div[1]/div/div[1]/span[1]").text().trim().toIntOrNull()
        var description = document.selectXpath("//*[@id=\"__nuxt\"]/div/div[1]/div/main/div[4]/div/section[1]/div[1]/div/div[3]/div/div/div/div/div[2]/div/div").text().trim()
        if (description.isEmpty()) {
            description = document.selectXpath("//*[@id=\"__nuxt\"]/div/div[1]/div/main/div[4]/div/section[1]/div[1]/div/div[2]/div/div/div/div/div[2]/div/div").text().trim()
        }
        val rating = document.selectXpath("//*[@id=\"__nuxt\"]/div/div[1]/div/main/div[4]/div/section[1]/div[1]/div/div[1]/div[2]/div[2]/div/div/span[1]").text().toRatingInt()
        val tvType = if (document.select("div.shadow a[href*='phim-bo'][title='Phim Bộ']").isNotEmpty()) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }
        return if (tvType == TvType.TvSeries) {
            val documentEpisode = app.get(link).document
            val episodes = documentEpisode.select("a[href^='/xem-phim-']").map {
                val href = fixUrl(it.attr("href"))
                val episode = it.text().replace(Regex("[^0-9]"), "").trim().toIntOrNull()
                val name = "Episode $episode"
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
                this.rating = rating
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
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
        val document = app.get(data).document
        var before = data.split("-").last()
        if (data.endsWith("-tap-full")) {
            before = "Full"
        }
        val item = document.selectFirst("#__NUXT_DATA__")?.data()?.substringAfter("},")?.substringBefore(",\"Tập $before\"")
        if (item != null) {
            val values = item.substringAfterLast("},").split(",")
            val movieId = values.last()
            val episodeId = values.first()
            val jsonResponse = app.get("$mainUrl/api/play/get?movieId=$movieId&episodeId=$episodeId&server=0").text

            val jsonArray: List<ServerInfo> = Gson().fromJson(jsonResponse, object : TypeToken<List<ServerInfo>>() {}.type)
            val listOfLinks = mutableListOf<Pair<String, String>>()
            if (jsonArray.size==1) {
                listOfLinks.add(Pair(jsonArray.get(0).Link, "Server 1"))
            }else {
                for (i in 0 until jsonArray.size) {
                    val jsonObject = jsonArray.get(i)
                    val url = jsonObject.Link
                    val isFrame = jsonObject.IsFrame
                    val name = jsonObject.ServerName
                    if ((!url.contains("https://player.cloudbeta.win"))&&(url.endsWith(".m3u8"))){
                        listOfLinks.add(Pair(serverFixUrl+url, "Server ${i + 1}"))
                    }
                }
            }

            listOfLinks.map { (link, source) ->
                println(link)
                callback.invoke(
                    ExtractorLink(
                        source,
                        source,
                        link,
                        referer = "$mainUrl/",
                        quality = Qualities.Unknown.value,
                        ExtractorLinkType.M3U8
                    )
                )
            }
        }

        return true
    }
    data class ServerInfo(
        val ServerName : String,
        val Link: String,
        val IsFrame: Boolean
    )

}
