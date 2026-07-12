package vip.mystery0.pixel.telo.smartspacer

import android.content.Intent
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider
import com.kieronquinn.app.smartspacer.sdk.utils.ComplicationTemplate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.MainActivity
import vip.mystery0.pixel.telo.R
import android.graphics.drawable.Icon as AndroidIcon

/**
 * Smartspacer Complication：显示静默拦截数量。
 *
 * 当来电在用户没有任何感知的情况下被直接挂断时计数，
 * 点按打开应用；用户进入应用后计数清零（见 [SmartspacerInterceptRepository.acknowledge]），
 * 数量为 0 时不显示。
 */
class SilentInterceptComplicationProvider : SmartspacerComplicationProvider(), KoinComponent {
    private val repository: SmartspacerInterceptRepository by inject()

    override fun getSmartspaceActions(smartspacerId: String): List<SmartspaceAction> {
        val count = repository.getUnseenSilentInterceptCount()
        if (count <= 0) return emptyList()
        val context = provideContext()
        val label = if (count > 99) {
            context.getString(R.string.smartspacer_intercept_count_overflow)
        } else {
            context.getString(R.string.smartspacer_intercept_count, count)
        }
        return listOf(
            ComplicationTemplate.Basic(
                id = "pixel_telo_silent_intercept_$smartspacerId",
                icon = Icon(
                    AndroidIcon.createWithResource(context, R.drawable.ic_qs_tile),
                    contentDescription = context.getString(R.string.smartspacer_intercept_label)
                ),
                content = Text(label),
                onClick = TapAction(intent = openAppIntent())
            ).create()
        )
    }

    override fun getConfig(smartspacerId: String?): Config {
        val context = provideContext()
        return Config(
            label = context.getString(R.string.smartspacer_intercept_label),
            description = context.getString(R.string.smartspacer_intercept_description),
            icon = AndroidIcon.createWithResource(context, R.drawable.ic_qs_tile)
        )
    }

    private fun openAppIntent(): Intent {
        return Intent(provideContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}
