package vip.mystery0.pixel.telo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.ui.HomeViewModel
import vip.mystery0.pixel.telo.ui.theme.PixelTeloTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelTeloTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: HomeViewModel) {
    val blockedCalls by viewModel.blockedCalls.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("Pixel Telo - 拦截记录") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addDummyData() }) {
                Icon(Icons.Default.Add, contentDescription = "Simulate Block")
            }
        }
    ) { innerPadding ->
        BlockedCallsList(
            calls = blockedCalls,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun BlockedCallsList(calls: List<BlockedCall>, modifier: Modifier = Modifier) {
    if (calls.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text("暂无拦截记录", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            items(calls, key = { it.id }) { call ->
                BlockedCallItem(call)
            }
        }
    }
}

@Composable
fun BlockedCallItem(call: BlockedCall) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = call.phoneNumber,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = SimpleDateFormat(
                        "MM-dd HH:mm",
                        Locale.getDefault()
                    ).format(Date(call.blockTime)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!call.remark.isNullOrEmpty()) {
                Text(
                    text = "备注: ${call.remark}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}