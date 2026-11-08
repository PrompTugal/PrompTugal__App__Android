package ai.riskmandate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var config by mutableStateOf(ManagedConfig.DEFAULT)
    private var device by mutableStateOf(DeviceStatus(Management.NOT_DETECTED, null))
    private var message by mutableStateOf<String?>(null)

    private val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    HomeScreen(
                        version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        device = device,
                        message = message,
                        onOpen = { message = Browser.message(Browser.open(this, config.startUrl)) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Restrictions can change while the app is stopped, so read them again on every resume,
        // and listen for changes while the app is in front.
        refresh()
        ContextCompat.registerReceiver(
            this,
            restrictionsReceiver,
            IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPause() {
        unregisterReceiver(restrictionsReceiver)
        super.onPause()
    }

    private fun refresh() {
        config = ManagedConfig.read(this)
        device = DeviceStatus.detect(this, config.deviceLabel)
    }
}
