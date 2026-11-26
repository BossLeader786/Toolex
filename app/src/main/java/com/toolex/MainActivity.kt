package com.toolex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolex.pdf.ui.PdfViewModel
import com.toolex.pdf.ui.ToolexScreen
import com.toolex.pdf.ui.theme.ToolexTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Latest "share to Toolex" intent — also catches a second share while open. */
    private val incoming = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming.value = intent

        setContent {
            ToolexTheme {
                val vm: PdfViewModel = viewModel()
                val shared by incoming.collectAsState()
                LaunchedEffect(shared) {
                    vm.consumeShareIntent(shared)
                }
                ToolexScreen(viewModel = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = intent
    }
}
