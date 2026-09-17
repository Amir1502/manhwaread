package com.manhwaread.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manhwaread.app.navigation.ManhwareadRoot
import com.manhwaread.core.designsystem.ManhwareadTheme
import dagger.hilt.android.AndroidEntryPoint

/** Единственная Activity: edge-to-edge + корневой Compose-контент с навигацией. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ManhwareadTheme {
                ManhwareadRoot()
            }
        }
    }
}
