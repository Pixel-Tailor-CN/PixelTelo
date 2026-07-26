package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.zhanghai.compose.preference.Preference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 备份与恢复分类下的设置项。 */
@Composable
fun BackupRestorePreferences(
    viewModel: SettingViewModel,
    onRestore: () -> Unit,
) {
    Preference(
        title = { Text(stringResource(R.string.setting_backup_records)) },
        summary = { Text(stringResource(R.string.setting_backup_records_summary)) },
        icon = { Icon(Icons.Default.Backup, contentDescription = null) },
        onClick = viewModel::openBackupOptionsSheet
    )

    Preference(
        title = { Text(stringResource(R.string.setting_restore_records)) },
        summary = { Text(stringResource(R.string.setting_restore_records_summary)) },
        icon = { Icon(Icons.Default.RestorePage, contentDescription = null) },
        onClick = onRestore
    )
}
