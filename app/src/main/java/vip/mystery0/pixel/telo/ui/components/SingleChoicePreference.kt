package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.zhanghai.compose.preference.Preference

/**
 * 点击后通过单选对话框修改值的 Preference。
 *
 * 整个选项行均可点击，包含 RadioButton、标签及行内空白区域。
 */
@Composable
fun <T> SingleChoicePreference(
    value: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    title: @Composable () -> Unit,
    optionContent: @Composable (T) -> Unit,
    summary: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }

    Preference(
        enabled = enabled,
        title = title,
        summary = summary,
        icon = icon,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = title,
            text = {
                Column {
                    options.forEach { option ->
                        val selectOption = {
                            onValueChange(option)
                            showDialog = false
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = selectOption),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = value == option,
                                onClick = selectOption
                            )
                            optionContent(option)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
