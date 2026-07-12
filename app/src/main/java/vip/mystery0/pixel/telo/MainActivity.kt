package vip.mystery0.pixel.telo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import vip.mystery0.pixel.telo.smartspacer.SmartspacerInterceptRepository
import vip.mystery0.pixel.telo.ui.screen.HomeScreen
import vip.mystery0.pixel.telo.ui.screen.ListScreen
import vip.mystery0.pixel.telo.ui.screen.SettingsScreen
import vip.mystery0.pixel.telo.ui.theme.PixelTeloTheme
import vip.mystery0.pixel.telo.ui.util.EmptyPagerNestedScrollConnection
import vip.mystery0.pixel.telo.viewmodel.HomeViewModel
import vip.mystery0.pixel.telo.viewmodel.ListViewModel
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val listViewModel: ListViewModel by viewModels()
    private val settingViewModel: SettingViewModel by viewModels()
    private val smartspacerInterceptRepository: SmartspacerInterceptRepository by inject()

    override fun onResume() {
        super.onResume()
        // 进入应用即视为已知晓静默拦截，清零 Smartspacer 计数
        smartspacerInterceptRepository.acknowledge(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelTeloTheme {
                val pagerState = rememberPagerState(pageCount = { AppDestinations.entries.size })
                val currentDestination = AppDestinations.entries[pagerState.currentPage]
                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text(stringResource(currentDestination.titleResId)) })
                    },
                    bottomBar = {
                        NavigationBar {
                            AppDestinations.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentDestination == destination,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                destination.ordinal
                                            )
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = stringResource(destination.labelResId)
                                        )
                                    },
                                    label = { Text(stringResource(destination.labelResId)) }
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        pageNestedScrollConnection = EmptyPagerNestedScrollConnection
                    ) { page ->
                        when (AppDestinations.entries[page]) {
                            AppDestinations.HOME -> {
                                HomeScreen(
                                    homeViewModel,
                                    onNavigateToSettings = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                AppDestinations.SETTINGS.ordinal
                                            )
                                        }
                                    },
                                    onNavigateToSourceSettings = {
                                        // 先打开 source 设置 BottomSheet，再切换到设置页，
                                        // 保证用户到达设置页时直接看到 source 配置
                                        settingViewModel.openQuerySourceSettings()
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                AppDestinations.SETTINGS.ordinal
                                            )
                                        }
                                    }
                                )
                            }

                            AppDestinations.LIST -> {
                                ListScreen(
                                    viewModel = listViewModel,
                                    parentPagerState = pagerState
                                )
                            }

                            AppDestinations.SETTINGS -> {
                                SettingsScreen(settingViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class AppDestinations(val titleResId: Int, val labelResId: Int, val icon: ImageVector) {
    HOME(R.string.app_name, R.string.nav_home, Icons.Default.Home),
    LIST(R.string.nav_list, R.string.nav_list, Icons.AutoMirrored.Filled.Rule),
    SETTINGS(R.string.nav_settings, R.string.nav_settings, Icons.Default.Settings)
}
