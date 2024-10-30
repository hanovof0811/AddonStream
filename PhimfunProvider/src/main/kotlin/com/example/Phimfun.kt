package com.example

import com.google.gson.Gson
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addSeasonNames
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URLDecoder

class Phimfun(val plugin: PhimfunPlugin) :
    MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://phimfun.com"
    override var name = "Phimfun"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    var token = ""
    lateinit var defaultCookies: Pair<String, String>
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    override val mainPage = mainPageOf(
        "$mainUrl/browse?page=" to "Phim Mới",
        "$mainUrl/type/movie?page=" to "Phim Lẻ",
        "$mainUrl/type/show?page=" to "Phim Bộ",
    )


    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        login(plugin.email, plugin.password)
        val resp = app.get(request.data + page, cookies = mapOf(defaultCookies))
        val document = resp.document
        val home = document.selectFirst("div.title-list")
            .select("div.grid.columns.is-mobile.is-multiline.is-variable.is-2 > div").mapNotNull {
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

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h3.name.vi").selectFirst("a").text().trim().toString()
        val href = mainUrl + this.selectFirst("a.cover").attr("href")
        val posterUrl = this.selectFirst("img").attr("src")
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url, cookies = mapOf(defaultCookies))
        val document = request.document
        val wrapContent = document.selectFirst("section.section")
        val titleMain = wrapContent.selectFirst("div.column.main")
        val title = titleMain.selectFirst("h1")?.text()?.trim().toString()
        val poster = wrapContent.selectFirst("img")?.attr("src")
        val tags = document.select("div.level.genres div.level-item > a")
            .map { it.text() }
        val year = titleMain.selectFirst("h2 a").attr("href").trim()
            .toIntOrNull()
        val tvType = if (titleMain.select("div.media").isEmpty()) TvType.Movie else TvType.TvSeries
        val description =
            document.select("div.intro.has-text-grey-light").text().trim()

        val rating =
            document.select("span.has-text-weight-bold").text().toRatingInt()


        return if (tvType == TvType.TvSeries) {
            var seasons = ArrayList<SeasonData>()
            var episodes = ArrayList<Episode>()
            titleMain.select("div.media").map {
                val seasonName = it.selectFirst("h3.title.is-6")?.text()?.trim().toString()
                var seasonValue = Regex("\\d+").find(seasonName)?.value?.toIntOrNull()
                if (seasonValue == null) {
                    seasonValue = 1
                }
                val link =
                 mainUrl+ it.selectFirst("div.media-content.has-text-grey-light a").attr("href")
                val webGetUrlAndImage =  app.get(link, cookies = mapOf(defaultCookies)).document
                val imgEpisodes = webGetUrlAndImage.select("div.column.main > div.media").map {
                    it.selectFirst("img").attr("src")
                }
                val parentId = Regex("\\d+").find(webGetUrlAndImage.selectFirst("a.watch.button.is-danger.is-medium.is-fullwidth").attr("href"))?.value.toString()
                episodes.addAll(getEpsiodes(parentId, seasonValue, imgEpisodes))
            }


            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.addSeasonNames(seasons)


            }
        } else {
            val link = wrapContent.selectFirst("div.column.is-one-quarter-tablet").selectFirst("a")
                .attr("href")
            val recommendations =
                titleMain.select("div.related-titles div.slick-list div.slick-track > div").map {
                    val title = it.selectFirst("h3.name").selectFirst("a").text().trim()
                    val href = mainUrl + it.selectFirst("a.cover").attr("href")
                    val posterUrl = it.selectFirst("img").attr("src")
                     newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    }
                }
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations

            }
        }
    }

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")


    override suspend fun search(query: String): List<SearchResponse> {
        val newQuery = decode(query)
        val link = "$mainUrl/search?q=$newQuery"
        val res = app.get(link)
        val document = res.document
        return document.selectFirst("div.title-list")
            .select("div.grid.columns.is-mobile.is-multiline.is-variable.is-2 > div").mapNotNull {
            it.toSearchResult()
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = Regex("\\d+").find(data)?.value.toString()
        subtitles(id,data).map { (link,vi) ->{
            subtitleCallback.invoke(SubtitleFile(link,vi))
        } }

            callback.invoke(
                ExtractorLink(
                    "$id HLS",
                    id,
                    linkMovieM3u8(id,data),
                    headers = mapOf("cookie" to "token=$token","Origin" to "https://phimfun.com","Accept-Encoding" to "gzip, deflate, br, zstd"),
                    referer = "https://phimfun.com/",
                    quality = Qualities.P1080.value,
                    isM3u8 = true,
                )
            )
        return true
    }
    suspend fun subtitles(id : String,refer :String) : List<Pair<String,String>> {
        val requestBody = ("{\"operationName\":\"Subtitles\",\"variables\":{\"titleId\":\"$id\"},\"query\":\"query Subtitles(\$titleId: String!) {\\n  subtitles(titleId: \$titleId) {\\n    id\\n    subsceneId\\n    language\\n    releaseNames\\n    files\\n    comment\\n    isDefault\\n    rand\\n    acted\\n    likes\\n    dislikes\\n    owner {\\n      id\\n      name\\n      __typename\\n    }\\n    __typename\\n  }\\n}\\n\"}").toRequestBody("application/json; charset=utf-8".toMediaType())
       val jsonResponse = app.post(
            "https://phimfun.com/b/g",
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = requestBody,
           cookies = mapOf(defaultCookies)
        ).text
        val data = Gson().fromJson(jsonResponse, ContainData::class.java)
        val result = ArrayList<Pair<String,String>>()
        data.data.subtitles.map {
            val language = it.language
            it.files.map {
              result.add(Pair(mainUrl+it,language))
            }
        }
        return result
    }
    suspend fun linkMovieM3u8(id : String,refer: String) : String{
        val server = 1
        val requestBody = ("{\"operationName\":\"TitleWatch\",\"variables\":{\"id\":\"$id\",\"server\":\"$server\"},\"query\":\"query TitleWatch(\$id: String!, \$server: String) {\\n  title(id: \$id, server: \$server) {\\n    id\\n    nameEn\\n    nameVi\\n    intro\\n    publishDate\\n    tmdbPoster\\n    tmdbBackdrop\\n    srcUrl\\n    srcServer\\n    canUseVpn\\n    vpnFee\\n    spriteUrl\\n    useVipLink\\n    reachedWatchLimit\\n    needImproveSubtitle\\n    needImproveVideo\\n    removed\\n    type\\n    number\\n    nextEpisodeId\\n    playedAt\\n    s3\\n    movieInfo {\\n      width\\n      height\\n      __typename\\n    }\\n    parent {\\n      id\\n      number\\n      intro\\n      publishDate\\n      tmdbPoster\\n      parent {\\n        id\\n        nameEn\\n        nameVi\\n        tmdbBackdrop\\n        __typename\\n      }\\n      __typename\\n    }\\n    children {\\n      id\\n      number\\n      __typename\\n    }\\n    relatedTitles {\\n      ...TitleBasics\\n      __typename\\n    }\\n    __typename\\n  }\\n}\\n\\nfragment TitleBasics on Title {\\n  id\\n  nameEn\\n  nameVi\\n  type\\n  postedAt\\n  tmdbPoster\\n  publishDate\\n  intro\\n  imdbRating\\n  countries\\n  genres {\\n    nameVi\\n    slug\\n    __typename\\n  }\\n  translation\\n  __typename\\n}\\n\"}").toRequestBody("application/json; charset=utf-8".toMediaType())
        val jsonResponse = app.post(
            "https://phimfun.com/b/g",
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = requestBody,
            cookies = mapOf(defaultCookies)
        ).text
        val data = Gson().fromJson(jsonResponse, ContainData::class.java)
        return data.data.title.srcUrl
    }


    suspend fun login(email: String, password: String) {
        val requestBody = ("{\n" +
                "  \"operationName\": \"Login\",\n" +
                "  \"variables\": {\n" +
                "    \"input\": {\n" +
                "      \"email\": \"$email\",\n" +
                "      \"password\": \"$password\",\n" +
                "      \"domain\": \"phimfun.com\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"query\": \"mutation Login(\$input: LoginInput, \$token: String) {\\n  login(input: \$input, token: \$token)\\n}\\n\"\n" +
                "}"
                ).toRequestBody("application/json; charset=utf-8".toMediaType())
        token = app.post(
            "https://phimfun.com/b/g",
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = requestBody
        ).text.substringAfter("\"login\":\"").substringBefore("\"}")
        defaultCookies = Pair("token", token)
    }

    suspend fun getEpsiodes(
        parentId: String,
        seasonValue: Int,
        urlImgs: List<String>
    ): List<Episode> {
        var indexEp = 0

        val requestBody = "{\"operationName\":\"EpisodesWatch\",\"variables\":{\"parentId\":\"$parentId\"},\"query\":\"query EpisodesWatch(\$parentId: String) {\\n  titles(first: 1200, order: \\\"asc\\\", parentId: \$parentId, watchable: true) {\\n    nodes {\\n      id\\n      number\\n      nameEn\\n      __typename\\n    }\\n    __typename\\n  }\\n}\\n\"}"
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val jsonString = app.post(
            "https://phimfun.com/b/g",
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = requestBody,
            cookies = mapOf(defaultCookies)
        ).text.substringAfter("\"titles\":").substringBeforeLast(",\"__typename\":\"TitleConnection\"}")
            .trim()

        return Gson().fromJson(jsonString+"}", Nodes::class.java).nodes.map {
            val ep = Episode(
                data = "https://phimfun.com/watch/" + it.id,
                name = "Tập ${it.number}",
                episode = it.number.toIntOrNull(),
                season = seasonValue,
                posterUrl = urlImgs[indexEp],
            )
            indexEp++
            ep
        }
    }


}

data class Node(
    val id: String,
    val number: String,
    val nameEn: String? = null,
)
data class ContainData(val data: Data)
data class Data(val title: Title,val subtitles:List<SubTitle>)
data class Title(
    val srcUrl : String,
    val srcServer : String
)
data class SubTitle(
    val files : List<String>,
    val language : String
)
data class Nodes(
    val nodes: List<Node>
)