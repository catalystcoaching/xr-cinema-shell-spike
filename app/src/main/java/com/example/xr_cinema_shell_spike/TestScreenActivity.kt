package com.example.xr_cinema_shell_spike

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.xr_cinema_shell_spike.ui.theme.XrcinemashellspikeTheme

class TestScreenActivity : ComponentActivity() {
    private val TAG = "XR_CINEMA_SPIKE_TEST"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "LOUD: TestScreenActivity onCreate")
        setContent {
            XrcinemashellspikeTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "INTERNAL TEST SCREEN",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "LOUD: TestScreenActivity onResume")
    }
}
