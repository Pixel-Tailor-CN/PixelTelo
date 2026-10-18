package vip.mystery0.pixel.telo.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

private const val FEEDBACK_URL = "https://github.com/Pixel-Tailor-CN/PixelTelo/issues/new"
private const val PIXEL_TAILOR_URL = "https://pixel.mystery0.app"
private const val TELEGRAM_URL = "https://t.me/pixel_tailor_cn"

/** 关于分组；版本点击仍用于解锁调试，刻意不显示普通导航箭头。 */
@Composable
fun AboutPreferences(viewModel: SettingViewModel) {
    val context = LocalContext.current
    val externalLinkUnavailableMessage = stringResource(R.string.error_external_link_unavailable)

    SettingsGroup(
        rows = listOf(
            SettingsRowItem("app-version") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_app_version),
                    summary = stringResource(
                        R.string.setting_app_version_summary,
                        viewModel.versionName,
                        viewModel.versionCode,
                    ),
                    icon = Icons.Default.Info,
                    trailing = SettingsRowTrailing.NONE,
                    onClick = viewModel::onVersionClick,
                )
            },
            SettingsRowItem("feedback-link") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_feedback),
                    summary = stringResource(R.string.setting_feedback_summary),
                    icon = Icons.Default.BugReport,
                    trailing = SettingsRowTrailing.EXTERNAL_LINK,
                    onClick = {
                        openExternalLink(context, FEEDBACK_URL, externalLinkUnavailableMessage)
                    },
                )
            },
            SettingsRowItem("pixel-tailor-link") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_pixel_tailor),
                    summary = stringResource(R.string.setting_pixel_tailor_summary),
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.ic_pixel_tailor),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    trailing = SettingsRowTrailing.EXTERNAL_LINK,
                    onClick = {
                        openExternalLink(context, PIXEL_TAILOR_URL, externalLinkUnavailableMessage)
                    },
                )
            },
            SettingsRowItem("telegram-link") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_telegram),
                    summary = stringResource(R.string.setting_telegram_summary),
                    icon = Icons.Default.Forum,
                    trailing = SettingsRowTrailing.EXTERNAL_LINK,
                    onClick = {
                        openExternalLink(context, TELEGRAM_URL, externalLinkUnavailableMessage)
                    },
                )
            },
        )
    )
}

/** 安全打开外部链接；设备没有可用处理程序时仅显示提示。 */
private fun openExternalLink(context: Context, url: String, unavailableMessage: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
    }
}
