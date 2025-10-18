package com.nigdroid.journal

import android.app.Application
import com.google.firebase.FirebaseApp

class JournalApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Schedule trash cleanup worker
        TrashCleanupWorker.scheduleCleanup(this)
    }
}