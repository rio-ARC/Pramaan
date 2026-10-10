package app.pramaan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import app.pramaan.ui.PramaanNavHost
import app.pramaan.ui.theme.PramaanSurface
import app.pramaan.ui.theme.PramaanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PramaanApp).container

        setContent {
            PramaanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PramaanSurface
                ) {
                    val navController = rememberNavController()
                    PramaanNavHost(navController = navController, container = container)
                }
            }
        }
    }
}
