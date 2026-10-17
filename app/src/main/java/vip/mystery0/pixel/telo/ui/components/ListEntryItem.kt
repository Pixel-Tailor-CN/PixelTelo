package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.ui.util.formatMills

/** 规则类型次要信息：图标 + 短文案，与来电记录的来源/本地标签共用无底色行。 */
private data class RuleTypeLabel(
    val icon: ImageVector,
    val textRes: Int,
)

/**
 * 单条名单规则卡片。
 *
 * 身份行展示匹配目标（号码、标签名或归属地关键词）、添加时间与进入编辑的箭头；
 * 规则类型作为无底色的次要信息与备注同排，拦截状态独立成行，避免与匹配目标抢层级。
 * 字体缩放超过 1.3 时身份行改为上下堆叠，箭头移到末行，避免时间挤压长号码。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListEntryItem(
    entry: UserListEntry,
    invalid: Boolean,
    onClick: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    // 标签和归属地规则的 phoneNumber 存的是标签名或地名，只有号码前缀补显示出匹配范围。
    val displayValue = when {
        entry.tagMatch || entry.locationMatch -> entry.phoneNumber
        entry.isPrefix -> "${entry.phoneNumber}*"
        else -> entry.phoneNumber
    }
    val typeLabel = when {
        entry.tagMatch -> RuleTypeLabel(
            Icons.AutoMirrored.Filled.Label,
            if (entry.tagContains) R.string.label_tag_contains else R.string.label_tag_exact,
        )

        entry.locationMatch -> RuleTypeLabel(
            Icons.Default.LocationOn,
            R.string.label_location_match
        )

        entry.isPrefix -> RuleTypeLabel(Icons.Default.Numbers, R.string.label_prefix_match)
        else -> null
    }
    val remark = entry.remark?.takeIf(String::isNotBlank)
    // 目标在界面上是短词（400*、标签名、地名），读屏需要补语义，避免把 * 读成文字。
    val valueDescription = when {
        entry.tagMatch -> stringResource(R.string.cd_rule_tag_value, entry.phoneNumber)
        entry.locationMatch -> stringResource(R.string.cd_rule_location_value, entry.phoneNumber)
        entry.isPrefix -> stringResource(R.string.cd_rule_prefix_value, entry.phoneNumber)
        else -> null
    }
    val time = formatMills(entry.addedAt)
    // 整卡点击进入编辑，读屏需要说明动作，否则只提示“点按两次即可激活”。
    val editActionLabel = stringResource(R.string.cd_edit_user_rule)

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .semantics { onClick(label = editActionLabel, action = null) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (largeText) {
                        ListEntryValue(
                            value = displayValue,
                            description = valueDescription,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ListEntryTime(time)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            ListEntryValue(
                                value = displayValue,
                                description = valueDescription,
                                modifier = Modifier.weight(1f),
                            )
                            ListEntryTime(time)
                            ListEntryChevron()
                        }
                    }
                }
            }
            if (typeLabel != null || remark != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    typeLabel?.let {
                        RuleTypeMetaLabel(icon = it.icon, text = stringResource(it.textRes))
                    }
                    remark?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (largeText) {
                // 大字号时箭头固定在末行右侧；没有状态徽章时靠 Spacer 撑开，避免箭头落到行首。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RuleStatusColumn(entry = entry, invalid = invalid)
                    Spacer(Modifier.weight(1f))
                    ListEntryChevron()
                }
            } else {
                RuleStatusColumn(entry = entry, invalid = invalid)
            }
        }
    }
}

@Composable
private fun ListEntryValue(value: String, description: String?, modifier: Modifier = Modifier) {
    Text(
        text = value,
        modifier = if (description == null) modifier else modifier.semantics {
            contentDescription = description
        },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ListEntryTime(time: String) {
    Text(
        text = time,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ListEntryChevron() {
    Icon(
        Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

/** 类型使用无底色的图标与文字，与来电记录的来源/本地标签保持同一种弱强调。 */
@Composable
private fun RuleTypeMetaLabel(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 规则状态独立成行。
 *
 * 强制拦截使用拦截语义的强强调色；归属地规则因未联网暂不生效时保持中性灰并说明原因，
 * 避免两种状态同色同形，也避免把可恢复的临时状态渲染成错误。
 */
@Composable
private fun RuleStatusColumn(entry: UserListEntry, invalid: Boolean) {
    if (!entry.forceBlock && !invalid) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (entry.forceBlock) {
            StatusBadge(
                icon = Icons.Default.Block,
                text = stringResource(R.string.label_force_block_chip),
                description = stringResource(R.string.cd_rule_force_block),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        if (invalid) {
            StatusBadge(
                icon = Icons.Default.Warning,
                text = stringResource(R.string.label_rule_inactive),
                description = stringResource(R.string.cd_rule_inactive),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.msg_location_rule_needs_network),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
