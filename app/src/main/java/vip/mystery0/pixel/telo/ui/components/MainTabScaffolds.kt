package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import vip.mystery0.pixel.telo.R

/** 搜索直接筛选原记录列表，不创建第二份搜索结果或展开弹层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScaffold(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(active) {
        if (!active && searchFocused) focusManager.clearFocus()
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        canScroll = { active && !searchFocused },
    )
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // 折叠态 AppBarWithSearch 会拦截平台输入；原地筛选必须使用可直接编辑的顶部栏。
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    AppSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onClear = { onQueryChange("") },
                        placeholder = stringResource(R.string.history_search_hint),
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.onFocusChanged { searchFocused = it.isFocused },
                    )
                },
                actions = actions,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

/** 每个主 Tab 在各自的 Pager 页面中持有顶部栏和滚动状态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScaffold(
    title: String,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { active })
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
    }
}
