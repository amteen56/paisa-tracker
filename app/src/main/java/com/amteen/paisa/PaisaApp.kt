package com.amteen.paisa

import android.app.Application

/**
 * Application entry point.
 *
 * In Phase 2 this becomes the owner of [com.amteen.paisa.di.AppContainer] — the
 * hand-written dependency graph holding the file store and repositories. There is
 * deliberately no DI framework; see CLAUDE.md.
 */
class PaisaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // TODO(Phase 2): container = AppContainer(applicationContext)
        //                container.initializeFirstRun()
    }
}
