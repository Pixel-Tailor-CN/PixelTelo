package vip.mystery0.pixel.telo.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 离场内容仍会保留到动画结束，相机等资源应只在当前页面启用。 */
internal val LocalNavigationActive = staticCompositionLocalOf { true }

/** 根据页面层级统一进入、返回方向；同级切换沿用进入方向，支持 RTL。 */
@Composable
internal fun <T> NavigationContent(
    targetState: T,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    backTarget: T? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val parentActive = LocalNavigationActive.current
    val layoutDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val state = remember { SeekableTransitionState(targetState) }
    val transition = rememberTransition(state, label = "navigation")
    val latestTarget by rememberUpdatedState(targetState)
    val latestBackTarget by rememberUpdatedState(backTarget)
    val latestOnBack by rememberUpdatedState(onBack)
    var predicting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var previewTarget by remember { mutableStateOf<T?>(null) }

    // 只在手势完成后提交导航。预览页不激活业务副作用，取消时保留当前页及表单。
    PredictiveBackHandler(enabled = parentActive && onBack != null) { events ->
        val start = latestTarget
        previewTarget = latestBackTarget
        progress = 0f
        predicting = previewTarget != null
        try {
            events.collect { event ->
                if (latestTarget != start) throw CancellationException("Navigation changed")
                progress = event.progress.coerceIn(0f, 1f)
            }
            if (latestTarget == start) latestOnBack?.invoke()
        } catch (_: CancellationException) {
            // 系统取消手势时不修改导航，下面的状态协调会把预览平滑退回原位。
        } finally {
            predicting = false
        }
    }

    LaunchedEffect(predicting, progress, previewTarget) {
        val previous = previewTarget
        if (predicting && previous != null) state.seekTo(progress, previous)
    }
    LaunchedEffect(targetState, predicting) {
        if (predicting) return@LaunchedEffect
        if (state.currentState != targetState) {
            state.animateTo(targetState)
        } else if (state.targetState != targetState) {
            // 当前状态未改变表示取消返回；沿同一转场倒放，避免重定向导致页面跳动。
            val duration = (state.fraction * transition.totalDurationNanos / 1_000_000).toInt()
            coroutineScope {
                animate(state.fraction, 0f, animationSpec = tween(duration)) { value, _ ->
                    launch { state.seekTo(value) }
                }
            }
            state.snapTo(targetState)
        }
    }

    transition.AnimatedContent(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clipToBounds(),
        transitionSpec = {
            // 预览时业务页面尚未切换，必须使用动画自身的目标判断方向。
            val forward = depth(this.targetState) >= depth(initialState)
            val enter = if (forward) navigationEnterTransition(layoutDirection)
                else navigationPopEnterTransition(layoutDirection)
            val exit = if (forward) navigationExitTransition(layoutDirection)
                else navigationPopExitTransition(layoutDirection)
            (enter togetherWith exit)
                .using(null)
                .apply { targetContentZIndex = if (forward) 1f else -1f }
        },
    ) { page ->
        val active = parentActive && page == targetState
        val interaction = if (active && !predicting) Modifier else Modifier
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
        CompositionLocalProvider(LocalNavigationActive provides active) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .then(interaction),
            ) {
                content(page)
            }
        }
    }
}
