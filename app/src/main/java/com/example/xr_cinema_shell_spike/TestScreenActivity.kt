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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Date

class TestScreenActivity : ComponentActivity() {
    private val TAG = "XR_CINEMA_SPIKE_TEST"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "LOUD: TestScreenActivity onCreate")
        setContent {
            XrcinemashellspikeTheme {
                var ticks by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    while(true) {
                        delay(1000)
                        ticks++
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Yellow) // GIANT YELLOW BACKGROUND
                        .border(20.dp, Color.Red), // RED BORDER
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "XR TEST PANEL",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.Black
                        )
                        Text(
                            text = "Status: ACTIVE & VISIBLE",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.Red
                        )
                        Text(
                            text = "Counter: $ticks",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.Blue
                        )
                        Text(
                            text = "Last Update: ${Date()}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "LOUD: TestScreenActivity onResume")
    }
}
