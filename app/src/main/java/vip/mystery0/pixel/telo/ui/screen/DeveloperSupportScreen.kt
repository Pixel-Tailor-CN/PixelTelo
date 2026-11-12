package vip.mystery0.pixel.telo.ui.screen

import android.content.Intent
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.ui.components.LocalNavigationActive
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vip.mystery0.pixel.telo.data.model.SupportChannelId
import vip.mystery0.pixel.telo.util.openSupportApp
import vip.mystery0.pixel.telo.viewmodel.DeveloperSupportViewModel

@Composable
fun DeveloperSupportScreen(
    viewModel: DeveloperSupportViewModel,
    modifier: Modifier = Modifier,
) {
    val active = LocalNavigationActive.current
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var enlargedImage by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(active) { if (active) viewModel.reload() }
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText, active) {
        messageText?.takeIf { active }?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error || !state.enabled -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(if (state.error) R.string.support_unavailable else R.string.support_paused), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                if (state.error) {
                    FilledTonalButton(onClick = viewModel::reload) { Text(stringResource(R.string.action_retry)) }
                }
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://pixel.mystery0.app/#support".toUri()))
                    } catch (_: Exception) {
                        viewModel.showMessage(R.string.support_website_unavailable)
                    }
                }) { Text(stringResource(R.string.support_website)) }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.support_voluntary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.channels, key = { it.id.value }) { channel ->
                    val name = when (channel.id) {
                        SupportChannelId.ALIPAY -> stringResource(R.string.support_alipay)
                        SupportChannelId.WECHAT -> stringResource(R.string.support_wechat)
                    }
                    val image = state.images[channel.id]
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(name, style = MaterialTheme.typography.titleLarge)
                            when {
                                image?.bitmap != null -> Image(
                                    bitmap = image.bitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.support_qr_description, name),
                                    modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth()
                                        .height(260.dp)
                                        .background(Color.White)
                                        .clickable(onClickLabel = stringResource(R.string.support_enlarge)) { enlargedImage = image.bitmap },
                                    contentScale = ContentScale.Fit,
                                )
                                image?.loading == true -> CircularProgressIndicator()
                                else -> {
                                    Text(stringResource(R.string.support_image_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { viewModel.retryImage(channel) }) { Text(stringResource(R.string.support_reload)) }
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.saveImage(channel) },
                                    enabled = image?.bytes != null && !image.saving,
                                ) { Text(if (image?.saving == true) stringResource(R.string.support_saving) else stringResource(R.string.support_save)) }
                                FilledTonalButton(onClick = {
                                    if (!context.openSupportApp(channel)) {
                                        viewModel.showMessage(R.string.support_app_unavailable)
                                    }
                                }) { Text(stringResource(R.string.support_open_app, name)) }
                            }
                            Text(
                                stringResource(R.string.support_save_hint, name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    enlargedImage?.takeIf { active }?.let { bitmap ->
        Dialog(onDismissRequest = { enlargedImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(bitmap.asImageBitmap(), stringResource(R.string.support_qr), modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
                TextButton(onClick = { enlargedImage = null }) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}
