package com.corriente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.corriente.app.navigation.CorrienteNavHost
import com.corriente.app.ui.theme.CorrienteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // F2.4: Android 15+ включает edge-to-edge принудительно — берём под контроль
        super.onCreate(savedInstanceState)
        setContent {
            CorrienteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CorrienteNavHost()
                }
            }
        }
    }
}
