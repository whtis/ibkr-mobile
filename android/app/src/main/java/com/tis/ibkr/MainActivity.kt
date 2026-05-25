package com.tis.ibkr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tis.ibkr.ui.RootScreen
import com.tis.ibkr.ui.theme.IbkrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IbkrTheme {
                RootScreen()
            }
        }
    }
}
