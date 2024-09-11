package com.example

import android.content.Context

import androidx.appcompat.app.AppCompatActivity

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class PhimfunPlugin : Plugin() {
    var activity: AppCompatActivity? = null
    var email = ""
    var password = ""
    lateinit var provider: Phimfun
    override fun load(context: Context) {
        activity = context as AppCompatActivity
        // All providers should be added in this manner
        val sharedPreferences = activity!!.getSharedPreferences("Phimfun", 0)
        val savedUsername = sharedPreferences.getString("email", null)
        val savedPassword = sharedPreferences.getString("password", null)
        if (savedUsername != null && savedPassword != null) {
           email = savedUsername
            password = savedPassword
        }
        provider = Phimfun(this)
        registerMainAPI(provider)

        openSettings = { ctx ->
            val frag = LoginFragment(this)
            frag.show(activity!!.supportFragmentManager, "Frag")
        }
    }
}
