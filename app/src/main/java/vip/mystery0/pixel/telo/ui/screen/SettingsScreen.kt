package vip.mystery0.pixel.telo.ui.screen

import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

@Composable
fun SettingsScreen(viewModel: SettingViewModel) {
    val context = LocalContext.current

    Button(onClick = {
        Toast.makeText(context, "Clicked Update", Toast.LENGTH_SHORT).show()
    }) {
        Text("更新离线数据")
    }
}
