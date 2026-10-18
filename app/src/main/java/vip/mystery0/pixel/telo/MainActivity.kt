package vip.mystery0.pixel.telo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.outlined.Assignment
import vip.mystery0.pixel.telo.viewmodel.CallReportViewModel
import vip.mystery0.pixel.telo.viewmodel.MyReportsViewModel
import vip.mystery0.pixel.telo.ui.screen.MyReportsScreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import vip.mystery0.pixel.telo.smartspacer.SmartspacerInterceptRepository
import vip.mystery0.pixel.telo.ui.screen.HomeScreen
import vip.mystery0.pixel.telo.ui.screen.ListScreen
import vip.mystery0.pixel.telo.ui.screen.LocalNumberLabelsScreen
import vip.mystery0.pixel.telo.ui.screen.SettingsScreen
import vip.mystery0.pixel.telo.ui.screen.settings.SettingsCategory
import vip.mystery0.pixel.telo.ui.screen.settings.SelfHostedManagementScreen
import vip.mystery0.pixel.telo.ui.screen.settings.SelfHostedPairingEntry
import vip.mystery0.pixel.telo.ui.screen.settings.SelfHostedPairingScreen
import vip.mystery0.pixel.telo.viewmodel.SelfHostedPairingPage
import vip.mystery0.pixel.telo.ui.theme.PixelTeloTheme
import vip.mystery0.pixel.telo.ui.util.EmptyPagerNestedScrollConnection
import vip.mystery0.pixel.telo.viewmodel.HomeViewModel
import vip.mystery0.pixel.telo.viewmodel.ListViewModel
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.viewmodel.LocalNumberLabelEditorViewModel
import vip.mystery0.pixel.telo.viewmodel.LocalNumberLabelsViewModel
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

class MainActivity : ComponentActivity() {
    private val callReportViewModel: CallReportViewModel by viewModels()
    private val myReportsViewModel: MyReportsViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val listViewModel: ListViewModel by viewModels()
    private val settingViewModel: SettingViewModel by viewModels()
    private val localNumberLabelEditorViewModel: LocalNumberLabelEditorViewModel by viewModels()
    private val localNumberLabelsViewModel: LocalNumberLabelsViewModel by viewModels()
    private val smartspacerInterceptRepository: SmartspacerInterceptRepository by inject()

    override fun onResume() {
        super.onResume()
        // 进入应用即视为已知晓静默拦截，清零 Smartspacer 计数
        smartspacerInterceptRepository.acknowledge(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelTeloTheme {
                val settingsSaveableStateHolder = rememberSaveableStateHolder()
                val pagerState = rememberPagerState(pageCount = { AppDestinations.entries.size })
                val currentDestination = AppDestinations.entries[pagerState.currentPage]
                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                val hasBlockedCallRecords by homeViewModel.hasBlockedCallRecords
                    .collectAsStateWithLifecycle()
                var showClearRecordsDialog by remember { mutableStateOf(false) }
                var showRecordsMenu by remember { mutableStateOf(false) }
                var showLocalNumberLabelsScreen by rememberSaveable { mutableStateOf(false) }
                var showMyReportsScreen by rememberSaveable { mutableStateOf(false) }
                var settingsCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
                fun openMyReports() {
                    showClearRecordsDialog = false
                    homeViewModel.closeQuickAdd()
                    callReportViewModel.close()
                    showLocalNumberLabelsScreen = false
                    showMyReportsScreen = true
                }
                BackHandler(enabled = showMyReportsScreen) { showMyReportsScreen = false }
                val showSelfHostedSettings = settingViewModel.showSelfHostedSettings
                val showPairingScreen = showSelfHostedSettings &&
                    settingViewModel.selfHostedConfigEditing
                var pairingEntry by remember { mutableStateOf(SelfHostedPairingEntry.MANUAL) }
                var pairingBackRequest by remember { mutableStateOf(0) }
                var pairingTitle by remember { mutableStateOf(R.string.title_pairing_manual) }

                LaunchedEffect(showSelfHostedSettings) {
                    if (showSelfHostedSettings) {
                        showMyReportsScreen = false
                        settingsCategory = SettingsCategory.ONLINE_QUERY
                        pagerState.requestScrollToPage(AppDestinations.SETTINGS.ordinal)
                    }
                }
                fun requestSelfHostedBack() {
                    if (showPairingScreen) {
                        pairingBackRequest++
                    } else if (showSelfHostedSettings) {
                        settingViewModel.closeSelfHostedConfig()
                    }
                }
                BackHandler(enabled = showSelfHostedSettings) {
                    requestSelfHostedBack()
                }

                fun closeLocalNumberLabelsScreen() {
                    showLocalNumberLabelsScreen = false
                    localNumberLabelEditorViewModel.clearTarget()
                }

                BackHandler(enabled = showLocalNumberLabelsScreen) {
                    closeLocalNumberLabelsScreen()
                }
                BackHandler(
                    enabled = settingsCategory != null &&
                        currentDestination == AppDestinations.SETTINGS &&
                        !showSelfHostedSettings &&
                        !showMyReportsScreen &&
                        !showLocalNumberLabelsScreen,
                ) {
                    settingsCategory = null
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    stringResource(
                                        if (showSelfHostedSettings) {
                                            if (showPairingScreen) pairingTitle
                                            else R.string.title_self_hosted_management
                                        } else if (showMyReportsScreen) {
                                            R.string.community_my_reports
                                        } else if (showLocalNumberLabelsScreen) {
                                            R.string.title_local_number_labels
                                        } else if (currentDestination == AppDestinations.SETTINGS &&
                                            settingsCategory != null
                                        ) {
                                            settingsCategory!!.titleResId
                                        } else {
                                            currentDestination.titleResId
                                        }
                                    )
                                )
                            },
                            navigationIcon = {
                                if (showSelfHostedSettings) {
                                    IconButton(
                                        onClick = ::requestSelfHostedBack,
                                        enabled = !settingViewModel.selfHostedValidationInProgress,
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.action_back))
                                    }
                                } else if (showMyReportsScreen) {
                                    IconButton(onClick = { showMyReportsScreen = false }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.action_back))
                                    }
                                } else if (showLocalNumberLabelsScreen) {
                                    IconButton(onClick = { closeLocalNumberLabelsScreen() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.action_back),
                                        )
                                    }
                                } else if (
                                    currentDestination == AppDestinations.SETTINGS &&
                                    settingsCategory != null
                                ) {
                                    IconButton(onClick = { settingsCategory = null }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.action_back),
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (!showMyReportsScreen && !showLocalNumberLabelsScreen && !showSelfHostedSettings &&
                                    currentDestination == AppDestinations.HOME) {
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                            TooltipAnchorPosition.Above,
                                        ),
                                        tooltip = {
                                            PlainTooltip {
                                                Text(stringResource(R.string.community_my_reports))
                                            }
                                        },
                                        state = rememberTooltipState(),
                                    ) {
                                        IconButton(onClick = ::openMyReports) {
                                            Icon(
                                                Icons.AutoMirrored.Outlined.Assignment,
                                                contentDescription = stringResource(R.string.community_my_reports),
                                            )
                                        }
                                    }
                                }
                                if (
                                    !showMyReportsScreen && !showLocalNumberLabelsScreen && !showSelfHostedSettings &&
                                    currentDestination == AppDestinations.HOME &&
                                    hasBlockedCallRecords
                                ) {
                                    Box {
                                        IconButton(onClick = { showRecordsMenu = true }) {
                                            Icon(Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.action_more))
                                        }
                                        DropdownMenu(
                                            expanded = showRecordsMenu,
                                            onDismissRequest = { showRecordsMenu = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.action_clear_all_records)) },
                                                onClick = {
                                                    showRecordsMenu = false
                                                    showClearRecordsDialog = true
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        if (!showMyReportsScreen && !showLocalNumberLabelsScreen &&
                            !showSelfHostedSettings && settingsCategory == null
                        ) {
                            val largeNavigationText = LocalDensity.current.fontScale > 1.3f
                            NavigationBar(
                                modifier = Modifier.heightIn(min = if (largeNavigationText) 104.dp else 80.dp),
                            ) {
                                AppDestinations.entries.forEach { destination ->
                                    NavigationBarItem(
                                        selected = currentDestination == destination,
                                        onClick = {
                                            settingsCategory = null
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    destination.ordinal
                                                )
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                destination.icon,
                                                contentDescription = null,
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(destination.labelResId),
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    if (showPairingScreen) {
                        SelfHostedPairingScreen(
                            viewModel = settingViewModel,
                            initialEntry = pairingEntry,
                            backRequest = pairingBackRequest,
                            onBackToManagement = settingViewModel::returnToSelfHostedManagement,
                            onTitleChange = { pairingTitle = it },
                            modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        )
                    } else if (showSelfHostedSettings) {
                        SelfHostedManagementScreen(
                            viewModel = settingViewModel,
                            onPairing = { entry ->
                                pairingEntry = entry
                                pairingTitle = when (entry) {
                                    SelfHostedPairingEntry.SCAN -> R.string.title_pairing_scan
                                    SelfHostedPairingEntry.PASTE -> R.string.title_pairing_paste
                                    SelfHostedPairingEntry.MANUAL -> R.string.title_pairing_manual
                                }
                                settingViewModel.beginSelfHostedPairing(
                                    when (entry) {
                                        SelfHostedPairingEntry.SCAN -> SelfHostedPairingPage.SCAN
                                        SelfHostedPairingEntry.PASTE -> SelfHostedPairingPage.PASTE
                                        SelfHostedPairingEntry.MANUAL -> SelfHostedPairingPage.MANUAL
                                    },
                                )
                            },
                            modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        )
                    } else if (showMyReportsScreen) {
                        MyReportsScreen(myReportsViewModel, Modifier.padding(innerPadding).fillMaxSize())
                    } else if (showLocalNumberLabelsScreen) {
                        LocalNumberLabelsScreen(
                            viewModel = localNumberLabelsViewModel,
                            editorViewModel = localNumberLabelEditorViewModel,
                            onNavigateToCallHistory = {
                                settingsCategory = null
                                closeLocalNumberLabelsScreen()
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(AppDestinations.HOME.ordinal)
                                }
                            },
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = settingsCategory == null,
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            pageNestedScrollConnection = EmptyPagerNestedScrollConnection
                        ) { page ->
                            when (AppDestinations.entries[page]) {
                                AppDestinations.HOME -> {
                                    HomeScreen(
                                        viewModel = homeViewModel,
                                        listViewModel = listViewModel,
                                        localLabelEditorViewModel = localNumberLabelEditorViewModel,
                                        callReportViewModel = callReportViewModel,
                                        onNavigateToMyReports = ::openMyReports,
                                        onNavigateToListRule = { entry: UserListEntry ->
                                            homeViewModel.closeQuickAdd()
                                            listViewModel.navigateToEntry(entry)
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    AppDestinations.LIST.ordinal,
                                                )
                                            }
                                        },
                                        onNavigateToSettings = {
                                            settingsCategory = null
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    AppDestinations.SETTINGS.ordinal
                                                )
                                            }
                                        },
                                        onNavigateToSourceSettings = {
                                            // 先打开 source 设置 BottomSheet，再切换到设置页，
                                            // 保证用户到达设置页时直接看到 source 配置
                                            settingsCategory = SettingsCategory.ONLINE_QUERY
                                            settingViewModel.openQuerySourceSettings()
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    AppDestinations.SETTINGS.ordinal
                                                )
                                            }
                                        },
                                        onNavigateToSelfHostedSettings = {
                                            // 打开自建服务管理入口后再切换页面，避免用户在设置中二次查找。
                                            settingsCategory = SettingsCategory.ONLINE_QUERY
                                            settingViewModel.openSelfHostedConfig()
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    AppDestinations.SETTINGS.ordinal
                                                )
                                            }
                                        },
                                    )
                                }

                                AppDestinations.LIST -> {
                                    ListScreen(
                                        viewModel = listViewModel,
                                        parentPagerState = pagerState
                                    )
                                }

                                AppDestinations.SETTINGS -> {
                                    SettingsScreen(
                                        viewModel = settingViewModel,
                                        saveableStateHolder = settingsSaveableStateHolder,
                                        settingsCategory = settingsCategory,
                                        onNavigateToCategory = { category -> settingsCategory = category },
                                        onNavigateToMyReports = ::openMyReports,
                                        onNavigateToLocalNumberLabels = {
                                            showLocalNumberLabelsScreen = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (showClearRecordsDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearRecordsDialog = false },
                        title = { Text(stringResource(R.string.title_clear_all_records)) },
                        text = { Text(stringResource(R.string.msg_clear_all_records_confirm)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showClearRecordsDialog = false
                                    homeViewModel.deleteAll()
                                }
                            ) {
                                Text(stringResource(R.string.action_clear_all_records))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearRecordsDialog = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
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
