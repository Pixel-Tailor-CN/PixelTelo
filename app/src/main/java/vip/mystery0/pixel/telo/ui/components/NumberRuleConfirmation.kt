package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R

enum class NumberRuleAction { BLACKLIST, WHITELIST, TAG_WHITELIST }

/** 确认和提交作用域绑定当前目标，详情关闭或换目标即丢弃，取消不触发写入。 */
@Composable
fun NumberRuleConfirmation(
    callId: Long,
    phone: String,
    label: String?,
    onAdd: suspend (NumberRuleAction) -> Boolean,
    onComplete: (NumberRuleAction, Boolean) -> Unit,
    content: @Composable (request: (NumberRuleAction) -> Unit) -> Unit,
) {
    key(callId, phone, label) {
        val scope = rememberCoroutineScope()
        var pending by remember { mutableStateOf<NumberRuleAction?>(null) }
        var submitting by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        content { action ->
            if (!submitting && (action != NumberRuleAction.TAG_WHITELIST || label != null)) {
                failed = false
                pending = action
            }
        }
        pending?.let { action ->
            val title = when (action) {
                NumberRuleAction.BLACKLIST -> R.string.confirm_blacklist_title
                NumberRuleAction.WHITELIST -> R.string.confirm_whitelist_title
                NumberRuleAction.TAG_WHITELIST -> R.string.confirm_tag_whitelist_title
            }
            val confirm = when (action) {
                NumberRuleAction.BLACKLIST -> R.string.confirm_blacklist_action
                NumberRuleAction.WHITELIST -> R.string.confirm_whitelist_action
                NumberRuleAction.TAG_WHITELIST -> R.string.confirm_tag_whitelist_action
            }
            AlertDialog(
                onDismissRequest = { if (!submitting) pending = null },
                icon = {
                    Icon(when (action) {
                        NumberRuleAction.BLACKLIST -> Icons.Default.Block
                        NumberRuleAction.WHITELIST -> Icons.Default.CheckCircle
                        NumberRuleAction.TAG_WHITELIST -> Icons.AutoMirrored.Filled.Label
                    }, contentDescription = null,
                        tint = if (action == NumberRuleAction.BLACKLIST) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary)
                },
                title = { Text(stringResource(title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (action == NumberRuleAction.TAG_WHITELIST) label.orEmpty() else phone,
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(when (action) {
                            NumberRuleAction.BLACKLIST -> R.string.confirm_blacklist_message
                            NumberRuleAction.WHITELIST -> R.string.confirm_whitelist_message
                            NumberRuleAction.TAG_WHITELIST -> R.string.confirm_tag_whitelist_message
                        }))
                        if (failed) Text(stringResource(R.string.rule_add_failed), color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !submitting,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (action == NumberRuleAction.BLACKLIST) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        ),
                        onClick = {
                            if (!submitting) {
                                submitting = true
                                failed = false
                                scope.launch {
                                    try {
                                        val added = onAdd(action)
                                        ensureActive()
                                        pending = null
                                        onComplete(action, added)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        failed = true
                                    } finally {
                                        submitting = false
                                    }
                                }
                            }
                        },
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(confirm))
                    }
                },
                dismissButton = {
                    TextButton(enabled = !submitting, onClick = { pending = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}
