package com.outfitku.app

import android.app.Application
import com.outfitku.app.notif.Notifier

class OutfitKuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }
}
