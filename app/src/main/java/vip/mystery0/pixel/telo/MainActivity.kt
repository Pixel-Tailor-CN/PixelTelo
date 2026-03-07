package vip.mystery0.pixel.telo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import vip.mystery0.pixel.telo.ui.screen.HomeScreen
import vip.mystery0.pixel.telo.ui.screen.ListScreen
import vip.mystery0.pixel.telo.ui.screen.SettingsScreen
import vip.mystery0.pixel.telo.ui.theme.PixelTeloTheme
import vip.mystery0.pixel.telo.viewmodel.HomeViewModel
import vip.mystery0.pixel.telo.viewmodel.ListViewModel
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val listViewModel: ListViewModel by viewModels()
    private val settingViewModel: SettingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelTeloTheme {
                var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }

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
                                    onClick = { currentDestination = destination },
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
                    Column(modifier = Modifier.padding(innerPadding)) {
                        when (currentDestination) {
                            AppDestinations.HOME -> {
                                HomeScreen(
                                    homeViewModel,
                                    onNavigateToSettings = {
                                        currentDestination = AppDestinations.SETTINGS
                                    }
                                )
                            }

                            AppDestinations.LIST -> {
                                ListScreen(listViewModel)
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
