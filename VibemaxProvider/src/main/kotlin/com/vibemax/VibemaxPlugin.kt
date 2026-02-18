package com.vibemax

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class VibemaxPlugin : Plugin() {
    override fun load(context: Context) {
        // Register the main provider
        registerMainAPI(VibemaxProvider())
    }
}
