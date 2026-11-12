package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 记录与备份分组；备份隐私边界由现有选择弹窗集中说明。 */
@Composable
fun BackupRestorePreferences(
    viewModel: SettingViewModel,
    onNavigateToMyReports: () -> Unit,
    onNavigateToLocalNumberLabels: () -> Unit,
    onRestore: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.setting_group_records)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("my-reports") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.community_my_reports),
                    summary = stringResource(R.string.community_official_history),
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    onClick = onNavigateToMyReports,
                )
            },
            SettingsRowItem("local-number-label-management") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_local_number_labels),
                    summary = stringResource(R.string.setting_local_number_labels_summary),
                    icon = Icons.AutoMirrored.Filled.ManageSearch,
                    onClick = onNavigateToLocalNumberLabels,
                )
            },
            ),
        )
    }

    SettingsSection(title = stringResource(R.string.setting_group_backup_and_restore)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("backup-data") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_backup_records),
                    enabled = viewModel.backupRestoreState !is vip.mystery0.pixel.telo.viewmodel.BackupRestoreState.Processing,
                    summary = stringResource(R.string.setting_backup_records_summary),
                    icon = Icons.Default.Backup,
                    onClick = viewModel::openBackupOptionsSheet,
                )
            },
            SettingsRowItem("restore-data") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_restore_records),
                    enabled = viewModel.backupRestoreState !is vip.mystery0.pixel.telo.viewmodel.BackupRestoreState.Processing,
                    summary = stringResource(R.string.setting_restore_records_summary),
                    icon = Icons.Default.RestorePage,
                    onClick = onRestore,
                )
            },
            ),
        )
    }
}
