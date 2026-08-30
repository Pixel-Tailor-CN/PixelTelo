package vip.mystery0.pixel.telo.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.zhanghai.compose.preference.Preference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 关于分类下的设置项。 */
@Composable
fun AboutPreferences(viewModel: SettingViewModel) {
    val context = LocalContext.current
    val externalLinkUnavailableMessage = stringResource(R.string.error_external_link_unavailable)

    Preference(
        title = { Text(stringResource(R.string.setting_version_name)) },
        summary = { Text(viewModel.versionName) },
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        onClick = viewModel::onVersionClick
    )

    Preference(
        title = { Text(stringResource(R.string.setting_version_code)) },
        summary = { Text(viewModel.versionCode.toString()) },
        icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) }
    )

    Preference(
        title = { Text(stringResource(R.string.setting_feedback)) },
        summary = { Text(stringResource(R.string.setting_feedback_summary)) },
        icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
        onClick = {
            openExternalLink(
                context,
                "https://github.com/Pixel-Tailor-CN/PixelTelo/issues/new",
                externalLinkUnavailableMessage,
            )
        }
    )

    Preference(
        title = { Text(stringResource(R.string.setting_pixel_tailor)) },
        summary = { Text(stringResource(R.string.setting_pixel_tailor_summary)) },
        icon = {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(R.drawable.ic_pixel_tailor),
                    contentDescription = null
                )
            }
        },
        onClick = {
            openExternalLink(
                context,
                "https://pixel.mystery0.app",
                externalLinkUnavailableMessage,
            )
        }
    )

    Preference(
        title = { Text(stringResource(R.string.setting_telegram)) },
        summary = { Text(stringResource(R.string.setting_telegram_summary)) },
        icon = { Icon(Icons.Default.Forum, contentDescription = null) },
        onClick = {
            openExternalLink(
                context,
                "https://t.me/pixel_tailor_cn",
                externalLinkUnavailableMessage,
            )
        }
    )
}

/** 安全打开外部链接；设备没有可用处理程序时仅显示提示，不中断设置页。 */
private fun openExternalLink(context: Context, url: String, unavailableMessage: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
    }
}
