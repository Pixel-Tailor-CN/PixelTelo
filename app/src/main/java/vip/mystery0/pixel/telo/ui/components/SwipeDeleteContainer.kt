package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 滑动删除容器。
 *
 * @param onDelete 删除回调
 * @param contentVerticalPadding 内容的垂直 padding，用于使背景高度与内容一致
 * @param fullAlphaThreshold 背景色达到完全不透明所需的滑动距离
 */
@Composable
fun SwipeToDeleteContainer(
    onDelete: () -> Unit,
    contentVerticalPadding: Dp = 0.dp,
    fullAlphaThreshold: Dp = 64.dp,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
        positionalThreshold = with(LocalDensity.current) { { 24.dp.toPx() } },
    )
    val scope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        onDismiss = {
            if (it == SwipeToDismissBoxValue.StartToEnd || it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                scope.launch {
                    dismissState.reset()
                }
            }
        },
        backgroundContent = {
            val direction = dismissState.dismissDirection
            // 读取真实偏移量，计算滑动进度（0~1）
            val offsetPx = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
            val thresholdPx = with(LocalDensity.current) { fullAlphaThreshold.toPx() }
            val progress = (abs(offsetPx) / thresholdPx).coerceIn(0f, 1f)
            // 根据滑动进度线性插值背景透明度
            val color = MaterialTheme.colorScheme.error.copy(alpha = progress)
            // 图标随滑动进度缩放，从 0.75f 到 1f
            val iconScale = 0.75f + 0.25f * progress
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.CenterEnd
            }

            Box(
                Modifier
                    .fillMaxSize()
                    // 垂直 padding 与卡片一致，使背景不超出卡片高度
                    .padding(vertical = contentVerticalPadding)
                    .clip(MaterialTheme.shapes.medium)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.scale(iconScale),
                    tint = Color.White
                )
            }
        },
        content = { content() }
    )
}
