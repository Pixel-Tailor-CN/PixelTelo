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
import vip.mystery0.pixel.telo.data.model.UserListWriteResult

enum class NumberRuleAction { BLACKLIST, WHITELIST, TAG_WHITELIST }

/**
 * 确认和提交作用域绑定当前目标，详情关闭或换目标即丢弃，取消不触发写入。
 * onComplete 只处理成功和真正重复；false 仅表示重复，不包含校验或写入失败。
 */
@Composable
fun NumberRuleConfirmation(
    callId: Long,
    phone: String,
    label: String?,
    onAdd: suspend (NumberRuleAction) -> UserListWriteResult,
    onComplete: (NumberRuleAction, Boolean) -> Unit,
    content: @Composable (request: (NumberRuleAction) -> Unit) -> Unit,
) {
    key(callId, phone, label) {
        val scope = rememberCoroutineScope()
        var pending by remember { mutableStateOf<NumberRuleAction?>(null) }
        var submitting by remember { mutableStateOf(false) }
        var failureMessage by remember { mutableStateOf<Int?>(null) }
        content { action ->
            if (!submitting && (action != NumberRuleAction.TAG_WHITELIST || label != null)) {
                failureMessage = null
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
                        failureMessage?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
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
                                failureMessage = null
                                scope.launch {
                                    try {
                                        val result = onAdd(action)
                                        ensureActive()
                                        when (result) {
                                            UserListWriteResult.SAVED, UserListWriteResult.DUPLICATE -> {
                                                pending = null
                                                onComplete(action, result == UserListWriteResult.SAVED)
                                            }
                                            UserListWriteResult.INVALID_INPUT -> {
                                                failureMessage = if (action == NumberRuleAction.TAG_WHITELIST) {
                                                    R.string.error_tag_empty
                                                } else R.string.error_phone_invalid
                                            }
                                            UserListWriteResult.NOT_FOUND -> {
                                                failureMessage = R.string.error_rule_no_longer_exists
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        failureMessage = R.string.rule_add_failed
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
