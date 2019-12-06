package io.gnosis.safe.authenticator.services

import com.crashlytics.android.Crashlytics
import timber.log.Timber

interface CrashReporter {
    fun init()
}

class FirebaseCrashReporter: CrashReporter {
    override fun init() {
        Timber.plant(Tree())
        Timber.plant(Timber.DebugTree())
    }

    inner class Tree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            t?.let { Crashlytics.logException(t) }
            Crashlytics.log(priority, tag, message)
        }
    }
}
