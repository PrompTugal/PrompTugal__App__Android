package ai.riskmandate.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    HomeScreen(
                        version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        onOpen = { open(Links.HOME) },
                    )
                }
            }
        }
    }

    private fun open(url: String) {
        if (!Links.isAllowed(url)) return
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
    }
}

@Composable
fun HomeScreen(version: String, onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RiskMandate", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpen) { Text("Open riskmandate.ai") }
        Spacer(Modifier.height(24.dp))
        Text("Build $version", style = MaterialTheme.typography.bodySmall)
    }
}

@Preview
@Composable
private fun HomePreview() = MaterialTheme { HomeScreen("0.0.0-local (1)") {} }
