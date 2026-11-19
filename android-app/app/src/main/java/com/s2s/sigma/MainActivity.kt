package com.s2s.sigma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.s2s.sigma.ui.SigmaApp
import com.s2s.sigma.ui.theme.S2SSigmaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            S2SSigmaTheme { SigmaApp() }
        }
    }
}
