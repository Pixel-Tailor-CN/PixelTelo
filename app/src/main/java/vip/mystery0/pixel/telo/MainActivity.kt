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
import vip.mystery0.pixel.telo.ui.screen.HomeScreen
import vip.mystery0.pixel.telo.ui.screen.SettingsScreen
import vip.mystery0.pixel.telo.ui.theme.PixelTeloTheme
import vip.mystery0.pixel.telo.viewmodel.HomeViewModel
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val settingViewModel: SettingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelTeloTheme {
                var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text(currentDestination.title) })
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
                                            contentDescription = destination.label
                                        )
                                    },
                                    label = { Text(destination.label) }
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

enum class AppDestinations(val title: String, val label: String, val icon: ImageVector) {
    HOME("Pixel Telo", "拦截记录", Icons.Default.Home),
    SETTINGS("设置", "设置", Icons.Default.Settings)
}
