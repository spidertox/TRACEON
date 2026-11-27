package com.traceon

import android.app.Application
import com.traceon.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TraceonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TraceonApp)
            modules(appModule)
        }
    }
}
