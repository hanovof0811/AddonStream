package com.haispace

import android.webkit.CookieManager
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.net.CookieStore
import java.util.TreeMap

suspend fun main(){

    val phim = Phimchill()

    val link = phim.search("tang+hai+hoa").get(0).url

//    val resp = phim.getApp().get("https://phimmoichillv.net/")
    val tiviseri = phim.load(link) as TvSeriesLoadResponse

    phim.loadLinks(tiviseri.episodes[0].data,false,{},{})


}