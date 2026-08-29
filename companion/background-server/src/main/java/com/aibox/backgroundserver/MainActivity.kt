package com.aibox.backgroundserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.aibox.backgroundserver.ui.BackgroundServerApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BackgroundServerApp(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAll()
    }

    override fun onPause() {
        viewModel.restoreSoftBlank()
        super.onPause()
    }
}
