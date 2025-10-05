package com.nigdroid.journal

import android.app.Application


class JournalUser : Application() {
    var username: String? = null
    var userId: String? = null

    companion object {
        @get:Synchronized
        var instance: JournalUser? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}