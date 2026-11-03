package com.corriente.app.ui.txnentry

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Панель категорий на экране ввода тянется вниз за пальцем — за ручку под суммой или потягом списка, когда он уже
 * в начале. Отпустили дальше [CLOSE_FRACTION] высоты (или смахнули быстрее [FLING_CLOSE_VELOCITY]) — панель уезжает
 * вниз из того места, где её отпустили, и возвращается клавиатура; иначе пружиной встаёт на место.
 *
 * Только геометрия жеста (пиксели, скорость пальца) — к суммам отношения не имеет; поэтому отдельный файл.
 */
@Stable
class CategoryPanelDrag internal constructor(
    private val scope: CoroutineScope,
    private val minClosePx: Float,
    private val flingClosePx: Float,
    private val onClose: () -> Unit,
) {
    /** Насколько панель стянута вниз, px. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var height by mutableIntStateOf(0)

    fun reset() {
        offset = 0f
    }

    private fun drag(dy: Float) {
        offset = (offset + dy).coerceAtLeast(0f)
    }

    private fun settle(velocityY: Float) {
        val far = offset > maxOf(minClosePx, height * CLOSE_FRACTION)
        if (velocityY > flingClosePx || (far && velocityY > -flingClosePx)) {
            onClose() // смещение не сбрасываем: панель уезжает вниз из того места, где её отпустили
        } else {
            scope.launch {
                animate(offset, 0f, initialVelocity = velocityY, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                    offset = value
                }
            }
        }
    }

    /** Сама панель: сдвиг вниз и лёгкое затухание по мере стягивания. */
    fun panelModifier(): Modifier = Modifier
        .onSizeChanged { height = it.height }
        .graphicsLayer {
            translationY = offset
            alpha = 1f - (if (height > 0) (offset / height).coerceIn(0f, 1f) else 0f) * 0.5f
        }

    /** Ручка над списком: тянется в обе стороны, вверх — не дальше исходного места. */
    fun handleModifier(): Modifier = Modifier.pointerInput(Unit) {
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onVerticalDrag = { change, dy ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                drag(dy)
            },
            onDragEnd = { settle(tracker.calculateVelocity().y) },
            onDragCancel = { settle(0f) },
        )
    }

    /** Список категорий: потяг вниз, когда он уже в начале, стягивает панель; движение вверх сначала возвращает её. */
    val nestedScroll: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && offset > 0f) {
                val take = maxOf(available.y, -offset)
                drag(take)
                return Offset(0f, take)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0f) {
                drag(available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset > 0f) {
                settle(available.y)
                return available
            }
            return Velocity.Zero
        }
    }

    private companion object {
        const val CLOSE_FRACTION = 0.25f
        val FLING_CLOSE_VELOCITY = 600.dp // в секунду
        val MIN_CLOSE_DISTANCE = 48.dp
    }

    internal object Factory {
        @Composable
        fun remember(onClose: () -> Unit): CategoryPanelDrag {
            val scope = rememberCoroutineScope()
            val close by rememberUpdatedState(onClose)
            val density = LocalDensity.current
            return remember(density) {
                with(density) {
                    CategoryPanelDrag(scope, MIN_CLOSE_DISTANCE.toPx(), FLING_CLOSE_VELOCITY.toPx(), onClose = { close() })
                }
            }
        }
    }
}

@Composable
internal fun rememberCategoryPanelDrag(onClose: () -> Unit): CategoryPanelDrag = CategoryPanelDrag.Factory.remember(onClose)
