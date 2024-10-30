package com.example

import android.content.Context

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainActivity

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager



@CloudstreamPlugin
class IPTVPlugin : Plugin() {
    var activity: AppCompatActivity? = null
    lateinit var provider: IPTV
    override fun load(context: Context) {
        activity = context as AppCompatActivity
        reload(context,SettingFragment.getListLink(context))
        openSettings = { ctx ->
            val frag = SettingFragment(this,"fragment_iptv")
            frag.show(activity!!.supportFragmentManager, "Frag")
        }
    }
     fun reload(context: Context?, providers: List<Link>) {

        val pluginDatas = PluginManager.getPluginsOnline()
        providers.map {
            val providerLink = it
            val pluginData = pluginDatas.find { it.internalName.contains(providerLink.title) }
            if (pluginData!=null) {
                if (!providerLink.isActive) {
                    PluginManager.unloadPlugin(pluginData.filePath)
                }
            }else{
                if (providerLink.isActive) {
                    provider = IPTV(name = providerLink.title, mainUrl = providerLink.link)
                    registerMainAPI(provider)
                }
            }
        }
        PluginManager.loadAllOnlinePlugins(context ?: throw Exception("Unable to load plugins"))
        MainActivity.afterPluginsLoadedEvent.invoke(true)

    }
}
