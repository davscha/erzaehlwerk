package de.davscha.erzaehlwerk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.davscha.erzaehlwerk.ui.App
import de.davscha.erzaehlwerk.ui.AppViewModel
import de.davscha.erzaehlwerk.ui.ErzaehlwerkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Zurück aus den Android-Einstellungen oder aus dem Hintergrund: alles neu prüfen (PC an?)
            LifecycleResumeEffect(Unit) {
                viewModel.refresh()
                onPauseOrDispose { }
            }
            ErzaehlwerkTheme {
                Surface(color = MaterialTheme.colorScheme.background) { App(viewModel) }
            }
        }
    }
}
