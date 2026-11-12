package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
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
    val searchBarState = rememberSearchBarState()
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(active) {
        if (!active && searchFocused) focusManager.clearFocus()
    }
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(
        canScroll = { active && !searchFocused },
    )
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppBarWithSearch(
                state = searchBarState,
                scrollBehavior = scrollBehavior,
                colors = SearchBarDefaults.appBarWithSearchColors(
                    searchBarColors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    scrolledSearchBarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    appBarContainerColor = MaterialTheme.colorScheme.background,
                    scrolledAppBarContainerColor = MaterialTheme.colorScheme.background,
                ),
                inputField = {
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
