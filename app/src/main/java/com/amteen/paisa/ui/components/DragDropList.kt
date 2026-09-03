package com.amteen.paisa.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel

/**
 * Long-press drag reordering for a [androidx.compose.foundation.lazy.LazyColumn].
 *
 * Written by hand rather than pulled in as a dependency — the dependency list is
 * deliberately tiny (see CLAUDE.md) and this is the only screen that needs it.
 *
 * Reordering is expressed in **item keys, not indices**. A list with section
 * headers and a trailing button has indices that mean nothing to the data behind
 * it, and off-by-one bugs in that translation are exactly how a reorder ends up
 * moving the wrong row. The caller gets "move the row keyed A to where B is" and
 * resolves that against its own list.
 *
 * Rows the caller does not mark draggable are skipped as drop targets, so a
 * category can never be dropped into the middle of a header.
 *
 * Drag-and-drop is unusable with a screen reader, so every screen using this must
 * also offer an explicit move action. See the row overflow menus in the category
 * and payment-method screens.
 */
class DragDropState internal constructor(
    private val listState: LazyListState,
    private val isDraggable: (key: Any?) -> Boolean,
    private val onMove: (fromKey: Any, toKey: Any) -> Unit,
    private val onSettle: () -> Unit,
) {

    /** The key of the row currently under the user's finger, or null. */
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    internal val scrollChannel = Channel<Float>(Channel.CONFLATED)

    private val draggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggingItemKey }

    /**
     * How far the dragged row should be drawn from where the list laid it out.
     *
     * Derived from the item's *current* offset rather than accumulated separately,
     * so it self-corrects the moment a move re-lays-out the list — otherwise the
     * row would visibly jump by one row height on every swap.
     */
    val draggingItemOffset: Float
        get() = draggingItem?.let { initialOffset + draggedDistance - it.offset } ?: 0f

    internal fun onDragStart(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingItemKey = key
        initialOffset = item.offset
        draggedDistance = 0f
    }

    internal fun onDrag(delta: Float) {
        draggedDistance += delta

        val dragged = draggingItem ?: return
        val start = dragged.offset + draggingItemOffset
        val middle = start + dragged.size / 2f

        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.key != dragged.key &&
                isDraggable(candidate.key) &&
                middle.toInt() in candidate.offset..(candidate.offset + candidate.size)
        }

        val draggedKey = dragged.key
        if (target != null) {
            onMove(draggedKey, target.key)
        } else {
            // Past the top or bottom edge: scroll the list instead of stalling.
            scrollChannel.trySend(overscroll(start, start + dragged.size))
        }
    }

    /** How far to nudge the list when the dragged row is pushed past an edge. */
    private fun overscroll(start: Float, end: Float): Float {
        val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
        return when {
            draggedDistance > 0 -> (end - viewportEnd).coerceAtLeast(0f)
            draggedDistance < 0 -> (start - viewportStart).coerceAtMost(0f)
            else -> 0f
        }
    }

    internal fun onDragEnd() {
        val wasDragging = draggingItemKey != null
        draggingItemKey = null
        draggedDistance = 0f
        initialOffset = 0
        // Only now is the order final. Persisting on every swap instead would mean
        // a file write per row crossed, for an order the user is still choosing.
        if (wasDragging) onSettle()
    }
}

@Composable
fun rememberDragDropState(
    listState: LazyListState,
    isDraggable: (key: Any?) -> Boolean,
    onMove: (fromKey: Any, toKey: Any) -> Unit,
    onSettle: () -> Unit,
): DragDropState {
    // The state outlives any single composition, so it must not capture that
    // composition's lambdas — it would keep calling the first ones it ever saw.
    val currentIsDraggable by rememberUpdatedState(isDraggable)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnSettle by rememberUpdatedState(onSettle)

    val state = remember(listState) {
        DragDropState(
            listState = listState,
            isDraggable = { key -> currentIsDraggable(key) },
            onMove = { from, to -> currentOnMove(from, to) },
            onSettle = { currentOnSettle() },
        )
    }
    LaunchedEffect(state) {
        // A plain `scrollBy` inside onDrag would fight the gesture's own frame; the
        // channel hands it to the composition's scope instead.
        while (true) {
            val delta = state.scrollChannel.receive()
            if (delta != 0f) listState.scrollBy(delta)
        }
    }
    return state
}

/**
 * Starts a drag on long press. Apply to the whole row: a drag handle alone is a
 * 24dp target, and the row is already the thing the user is aiming at.
 */
fun Modifier.dragHandle(state: DragDropState, key: Any): Modifier =
    this.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key) },
            onDrag = { change, offset ->
                change.consume()
                state.onDrag(offset.y)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() },
        )
    }
