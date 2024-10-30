package com.example

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app

import com.lagradost.cloudstream3.newHomePageResponse

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.m3uparser.Entry
import com.m3uparser.Parser
import kotlinx.coroutines.runBlocking
import java.net.URL


class IPTV( mainUrl: String, name: String) :MainAPI() {
    override var mainUrl = mainUrl
    override var name =name
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = false


    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val mainPage = runBlocking {
        getMainDataPage()
    }

    private suspend fun getMainDataPage(): List<MainPageData> {
        if (mainUrl==null||!mainUrl.contains("http")) return emptyList()
        return Parser().parse(app.get(mainUrl).body.byteStream()).groupBy {
            it.groupTitle
        }.map {
            MainPageData(
                name = it.key.toString(),
                data = it.key.toString(),
            )
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home =
            Parser().parse(app.get(mainUrl).body.byteStream()).mapNotNull {
                if (it.groupTitle != request.data) {
                    null
                } else {
                    it.toSearchResult()
                }
            }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true,
            ),
            hasNext = true
        )
    }

    private fun Entry.toSearchResult(): SearchResponse {
        val href = this.channelUri.toString()
        val posterHome = this.tvgLogo
        val title = this.channelName.toString()
        return LiveSearchResponse(title, href, name, TvType.Live, posterHome)
    }

    override suspend fun load(url: String): LoadResponse {
        val data = Parser().parse(app.get(mainUrl).body.byteStream()).find {
            it.channelUri == url
        }
        return LiveStreamLoadResponse(
            data?.channelName.toString(),
            url,
            name,
            "$url ${data?.userAgent}",
            data?.tvgLogo.toString(),
            null,
            "",
            TvType.Live
        )
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val datas = data.split(" ")
        val userAgent = if (datas.size > 1) datas[1] else ""

        if (userAgent != "") {
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    datas[0],
                    getBaseUrl(datas[0]),
                    Qualities.Unknown.value,
                    ExtractorLinkType.M3U8,
                    headers = mapOf("User-Agent" to userAgent)
                )
            )
        } else {
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    datas[0],
                    getBaseUrl(datas[0]),
                    Qualities.Unknown.value,
                    ExtractorLinkType.M3U8,
                )
            )
        }
        return true
    }
    fun getBaseUrl(url: String): String {
        val parsedUrl = URL(url)
        return "${parsedUrl.protocol}://${parsedUrl.host}/"
    }
}