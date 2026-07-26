package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 调试分类下的设置项。 */
@Composable
fun DebugPreferences(viewModel: SettingViewModel) {
    SwitchPreference(
        value = viewModel.forceDownload,
        onValueChange = { viewModel.forceDownload = it },
        title = { Text(stringResource(R.string.setting_force_download)) },
        summary = { Text(stringResource(R.string.setting_force_download_summary)) },
        icon = { Icon(Icons.Default.DownloadForOffline, contentDescription = null) }
    )

    Preference(
        title = { Text(stringResource(R.string.setting_delete_database)) },
        summary = { Text(stringResource(R.string.setting_delete_database_summary)) },
        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
        onClick = viewModel::deleteDatabase
    )
}
