package vip.mystery0.pixel.telo.ui.util

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import kotlin.math.absoluteValue

/**
 * 空的 Pager 嵌套滚动连接。
 *
 * 同方向嵌套 Pager 需要把边界手势交接逻辑集中到自定义连接里处理，避免默认连接提前消费
 * 内层 Pager 留下的横向速度。
 */
object EmptyPagerNestedScrollConnection : NestedScrollConnection

/**
 * 为内层 HorizontalPager 创建边界手势交接连接。
 *
 * 仅覆盖 PixelTelo 当前使用场景：LTR、非 reverseLayout、两层同方向 HorizontalPager。
 * 当内层 Pager 已经滑到首尾边界时，继续向边界外拖动或 fling 会交给父 Pager。
 */
@Composable
fun rememberPagerBoundaryHandoffConnection(
    parentState: PagerState,
    childState: PagerState,
): NestedScrollConnection {
    val minimumFlingVelocity = LocalViewConfiguration.current.minimumFlingVelocity
    return remember(parentState, childState, minimumFlingVelocity) {
        PagerBoundaryHandoffConnection(
            parentState = parentState,
            childState = childState,
            minimumFlingVelocity = minimumFlingVelocity
        )
    }
}

private class PagerBoundaryHandoffConnection(
    private val parentState: PagerState,
    private val childState: PagerState,
    private val minimumFlingVelocity: Float,
) : NestedScrollConnection {
    private var parentHandlingCurrentGesture = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val deltaX = available.x
        if (source != NestedScrollSource.UserInput || deltaX == 0f) return Offset.Zero

        if (parentHandlingCurrentGesture) {
            return dispatchToParent(deltaX)
        }

        if (!childState.isScrollInProgress) {
            return Offset.Zero
        }

        return if (childState.canMoveByFingerDelta(deltaX)) {
            Offset.Zero
        } else {
            dispatchToParent(deltaX)
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val deltaX = available.x
        if (source != NestedScrollSource.UserInput || deltaX == 0f) return Offset.Zero

        return if (
            parentHandlingCurrentGesture ||
            parentState.canMoveByFingerDelta(deltaX) ||
            parentState.isNotSettled()
        ) {
            dispatchToParent(deltaX)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val velocityX = available.x
        if (velocityX == 0f || !shouldParentHandlePreFling(velocityX)) {
            return Velocity.Zero
        }

        settleParent(velocityX)
        return Velocity(velocityX, 0f)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val velocityX = available.x
        if (velocityX == 0f && !parentState.isNotSettled()) {
            parentHandlingCurrentGesture = false
            return Velocity.Zero
        }

        if (!shouldParentSettleAfterFling(velocityX)) {
            parentHandlingCurrentGesture = false
            return Velocity.Zero
        }

        settleParent(velocityX)
        return Velocity(velocityX, 0f)
    }

    private fun dispatchToParent(fingerDeltaX: Float): Offset {
        if (!parentState.isNotSettled() && !parentState.canMoveByFingerDelta(fingerDeltaX)) {
            parentHandlingCurrentGesture = false
            return Offset.Zero
        }

        val consumedByParent = -parentState.dispatchRawDelta(-fingerDeltaX)
        if (consumedByParent.absoluteValue > 0f) {
            parentHandlingCurrentGesture = true
        }
        return Offset(consumedByParent, 0f)
    }

    private fun shouldParentHandlePreFling(velocityX: Float): Boolean {
        if (!parentHandlingCurrentGesture && !childState.isScrollInProgress) return false
        return shouldParentReactToVelocity(velocityX)
    }

    private fun shouldParentSettleAfterFling(velocityX: Float): Boolean {
        return parentHandlingCurrentGesture ||
                parentState.isNotSettled() ||
                shouldParentReactToVelocity(velocityX)
    }

    private fun shouldParentReactToVelocity(velocityX: Float): Boolean {
        if (velocityX.absoluteValue < minimumFlingVelocity) return false
        val childAtBoundary = !childState.canMoveByFingerDelta(velocityX)
        val parentCanMove = parentHandlingCurrentGesture ||
                parentState.canMoveByFingerDelta(velocityX) ||
                parentState.isNotSettled()
        return childAtBoundary && parentCanMove
    }

    private suspend fun settleParent(velocityX: Float) {
        val targetPage = parentState.targetPageForVelocity(velocityX, minimumFlingVelocity)
        parentHandlingCurrentGesture = false
        parentState.animateScrollToPage(targetPage)
    }
}

private fun PagerState.canMoveByFingerDelta(deltaX: Float): Boolean {
    return when {
        deltaX < 0f -> canScrollForward
        deltaX > 0f -> canScrollBackward
        else -> false
    }
}

private fun PagerState.targetPageForVelocity(velocityX: Float, minimumFlingVelocity: Float): Int {
    val targetPage = when {
        velocityX < -minimumFlingVelocity && canScrollForward -> currentPage + 1
        velocityX > minimumFlingVelocity && canScrollBackward -> currentPage - 1
        else -> currentPage
    }
    return targetPage.coerceIn(0, pageCount - 1)
}

private fun PagerState.isNotSettled(): Boolean {
    return currentPageOffsetFraction.absoluteValue > 0.001f
}
