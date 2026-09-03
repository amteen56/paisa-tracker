package com.amteen.paisa

import android.app.Application
import android.content.Context
import com.amteen.paisa.di.AppContainer

/**
 * Application entry point, and the owner of the dependency graph.
 *
 * The container is built here rather than per-Activity so the repositories — and
 * the in-memory state they hold — survive configuration changes and are shared by
 * every screen. There is deliberately no DI framework; see CLAUDE.md.
 */
class PaisaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Fire-and-forget: reads the small reference files on a background
        // dispatcher and flips `container.ready`. Nothing blocks the main thread,
        // so a cold start still draws its first frame immediately.
        container.initialize()
    }
}

/**
 * Reaches the graph from anywhere with a [Context].
 *
 * Composables should prefer `LocalAppContainer` — see `MainActivity` — so previews
 * and tests can substitute a graph without an `Application` instance.
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as PaisaApp).container
