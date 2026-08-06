package ru.alemak.studentapp.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Swipe from the left edge to the right → [onBack].
 * Same idea as iOS interactive pop (when system gesture-nav is off or not used).
 */
fun Modifier.swipeBack(onBack: () -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val edgePx = with(density) { 36.dp.toPx() }
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxVerticalPx = with(density) { 120.dp.toPx() }
    val currentOnBack = rememberUpdatedState(onBack)

    pointerInput(edgePx, thresholdPx, maxVerticalPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x > edgePx) return@awaitEachGesture

            var totalX = 0f
            var totalY = 0f
            drag(down.id) { change ->
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                // Don't steal vertical scrolls in the middle of the screen
                if (abs(totalY) > abs(totalX) && abs(totalY) > 24f) {
                    return@drag
                }
                if (totalX > 0f) {
                    change.consume()
                }
            }
            if (totalX >= thresholdPx && abs(totalY) < maxVerticalPx) {
                currentOnBack.value()
            }
        }
    }
}
