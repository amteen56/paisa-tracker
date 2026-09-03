package com.amteen.paisa.di

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The dependency graph, reachable from any composable.
 *
 * A `staticCompositionLocalOf` because the container never changes for the life of
 * the process — a dynamic one would add needless invalidation tracking.
 *
 * There is no default: a composable that reaches for the container outside the
 * provider is a wiring bug, and failing loudly at that point is far cheaper to
 * diagnose than an empty screen backed by a throwaway graph.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided. Wrap the content in a CompositionLocalProvider.")
}
