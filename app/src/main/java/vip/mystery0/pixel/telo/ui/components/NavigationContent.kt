package vip.mystery0.pixel.telo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection

/** 离场内容仍会保留到动画结束，相机等资源应只在当前页面启用。 */
internal val LocalNavigationActive = staticCompositionLocalOf { true }

/** 根据页面层级统一进入、返回方向；同级切换沿用进入方向，支持 RTL。 */
@Composable
internal fun <T> NavigationContent(
    targetState: T,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val parentActive = LocalNavigationActive.current
    val layoutDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(
        targetState = targetState,
        modifier = modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val forward = depth(targetState) >= depth(initialState)
            val direction = if (forward) layoutDirection else -layoutDirection
            ((slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) {
                it / 4 * direction
            } + fadeIn(tween(210))) togetherWith
                (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) {
                    -it / 4 * direction
                } + fadeOut(tween(150))))
                .using(null)
                .apply { targetContentZIndex = if (forward) 1f else -1f }
        },
        label = "navigation",
    ) { page ->
        val active = parentActive && page == targetState
        val interaction = if (active) Modifier else Modifier
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
