package com.example.xr_cinema_shell_spike

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration
import androidx.xr.compose.spatial.Subspace
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.scenecore.SpatialCapabilities
import androidx.xr.scenecore.scene
import com.example.xr_cinema_shell_spike.ui.theme.XrcinemashellspikeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val TAG = "XR_CINEMA_SPIKE"
    private var xrSession: Session? = null
    private var cinemaManager: CinemaManager? = null

    @SuppressLint("RestrictedApi")
    private fun runSpike(session: Session) {
        if (cinemaManager == null) {
            cinemaManager = CinemaManager(session)
        }
        
        cinemaManager?.setupCinema(this)
        // Initial setup to 0.0 for cinema feel
        cinemaManager?.setPassthroughOpacity(0.0f)
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "LOUD: onCreate")
        enableEdgeToEdge()

        // Bypass Keyguard
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                Log.i(TAG, "LOUD: Lifecycle CREATED")
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.i(TAG, "LOUD: Lifecycle STARTED")
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                Log.i(TAG, "LOUD: Lifecycle RESUMED")
            }
        }

        val sessionResult = Session.create(this)
        if (sessionResult is SessionCreateSuccess) {
            xrSession = sessionResult.session
            Log.i(TAG, "LOUD: Session created successfully in onCreate")
            runSpike(xrSession!!)
        } else {
            Log.e(TAG, "LOUD: Failed to create session in onCreate: $sessionResult")
        }

        setContent {
            XrcinemashellspikeTheme {
                val session = LocalSession.current
                val capabilities = LocalSpatialCapabilities.current
                val spatialConfig = LocalSpatialConfiguration.current

                Log.i(TAG, "LOUD: Composition cycle - session is ${if (session == null) "NULL" else "AVAILABLE"}")
                Log.i(TAG, "LOUD: Current Spatial Bounds: ${spatialConfig.bounds}")

                // Also run spike from compose if it wasn't run or if session becomes available here
                LaunchedEffect(session) {
                    if (session != null) {
                        Log.i(TAG, "LOUD: LaunchedEffect(session) triggered")
                        
                        // Request Full Space explicitly
                        Log.i(TAG, "LOUD: Requesting Full Space Mode...")
                        spatialConfig.requestFullSpaceMode()

                        // Wait for spatial mode and capabilities to settle
                        for (i in 1..15) {
                            val currentCaps = session.scene.spatialCapabilities
                            Log.i(TAG, "LOUD: Iteration $i - Caps: $currentCaps")
                            
                            if (currentCaps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_EMBED_ACTIVITY)) {
                                Log.i(TAG, "LOUD: SUCCESS! EMBED_ACTIVITY found on iteration $i")
                                runSpike(session)
                                break
                            }
                            
                            // Even if not found yet, try running spike anyway on iteration 5, 10, 15
                            if (i % 5 == 0) {
                                Log.i(TAG, "LOUD: Periodic spike attempt on iteration $i")
                                runSpike(session)
                            }

                            delay(2000)
                        }
                    }
                }

                Subspace {
                    // Empty subspace to ensure spatial mode is active
                }

                Surface {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Cinema Shell Spike Controller",
                                modifier = Modifier.padding(8.dp)
                            )
                            Button(onClick = { 
                                Log.i(TAG, "LOUD: Manual Spike Triggered")
                                session?.let { runSpike(it) } 
                            }) {
                                Text("Run Full Spike")
                            }
                            
                            Row {
                                Button(
                                    onClick = { 
                                        Log.i(TAG, "LOUD: Manual Passthrough -> 0.0")
                                        cinemaManager?.setPassthroughOpacity(0.0f)
                                    },
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Text("Passthrough 0.0")
                                }
                                Button(
                                    onClick = { 
                                        Log.i(TAG, "LOUD: Manual Passthrough -> 1.0")
                                        cinemaManager?.setPassthroughOpacity(1.0f)
                                    },
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Text("Passthrough 1.0")
                                }
                            }

                            Text(
                                text = "Capabilities: $capabilities",
                                modifier = Modifier.padding(8.dp)
                            )
                            Text(
                                text = "Bounds: ${spatialConfig.bounds}",
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
