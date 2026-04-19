package com.example.xr_cinema_shell_spike

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import java.util.function.Consumer

class MainActivity : ComponentActivity() {

    private val TAG = "XR_CINEMA_SPIKE"
    
    // --- QA MODE TOGGLE ---
    private enum class Mode { STRICT, SOFT_STRICT, DEBUG_OVERRIDE }
    private var QA_MODE = Mode.SOFT_STRICT
    // ----------------------

    private var cinemaManager: CinemaManager? = null

    @SuppressLint("RestrictedApi")
    private fun runCinemaLogic(session: Session, isManual: Boolean = false) {
        val caps = session.scene.spatialCapabilities
        val hasEmbed = caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_EMBED_ACTIVITY)
        val hasUi = caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_UI)
        
        Log.i(TAG, "LOUD: --- RUN CINEMA LOGIC (Mode: $QA_MODE, Manual: $isManual) ---")
        Log.i(TAG, "LOUD: Reported Capabilities: $caps")

        val shouldProceed = when (QA_MODE) {
            Mode.STRICT -> hasEmbed
            Mode.SOFT_STRICT -> hasEmbed || hasUi
            Mode.DEBUG_OVERRIDE -> true
        }

        if (!shouldProceed) {
            Log.i(TAG, "LOUD: $QA_MODE mode - aborting setup. Requirements not met.")
            return
        }

        val reason = when {
            hasEmbed -> "CAPABILITY FOUND"
            QA_MODE == Mode.SOFT_STRICT && hasUi -> "SOFT_STRICT FALLBACK (UI enabled)"
            QA_MODE == Mode.DEBUG_OVERRIDE -> "DEBUG_OVERRIDE ACTIVE"
            else -> "UNKNOWN"
        }
        
        Log.i(TAG, "LOUD: $reason - Proceeding with setup.")
        
        if (cinemaManager == null) {
            cinemaManager = CinemaManager(session)
        }
        
        cinemaManager?.setupCinema(this)
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

        val sessionResult = Session.create(this)
        if (sessionResult is SessionCreateSuccess) {
            Log.i(TAG, "LOUD: Session created successfully")
            // In a real app we might wait for Full Space, but we'll try once here for the log.
            runCinemaLogic(sessionResult.session)
        } else {
            Log.e(TAG, "LOUD: Failed to create session: $sessionResult")
        }

        setContent {
            XrcinemashellspikeTheme {
                val session = LocalSession.current
                val capabilities = LocalSpatialCapabilities.current
                val spatialConfig = LocalSpatialConfiguration.current
                
                // Drive the UI status from Compose-layer capabilities
                val isSpatialUiEnabled = capabilities.isSpatialUiEnabled
                val isAppEnvironmentEnabled = capabilities.isAppEnvironmentEnabled
                val isPassthroughControlEnabled = capabilities.isPassthroughControlEnabled
                val isSpatialAudioEnabled = capabilities.isSpatialAudioEnabled

                LaunchedEffect(session) {
                    if (session != null) {
                        Log.i(TAG, "LOUD: Registering Spatial Capabilities Listener...")
                        session.scene.addSpatialCapabilitiesChangedListener(Consumer { caps ->
                            Log.i(TAG, "LOUD: Capabilities Changed: $caps")
                            if (caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_EMBED_ACTIVITY)) {
                                Log.i(TAG, "LOUD: EMBED_ACTIVITY capability detected!")
                                runCinemaLogic(session)
                            }
                        })

                        Log.i(TAG, "LOUD: Requesting Full Space Mode...")
                        spatialConfig.requestFullSpaceMode()
                        
                        // Check initial state
                        runCinemaLogic(session)
                    }
                }

                Subspace {
                    // Ensures spatial mode
                }

                Surface {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "XR Cinema QA Controller",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(16.dp)
                            )

                            // --- DISCREPANCY WARNING BANNER ---
                            val reportedEmbed = isSpatialUiEnabled // Corrected: Use value from LocalSpatialCapabilities
                            val actualPanel = cinemaManager?.isPanelCreated ?: false
                            
                            if (actualPanel && !reportedEmbed) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .background(Color(0xFFFFCC00), RoundedCornerShape(8.dp))
                                        .border(2.dp, Color.Red, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color.Red)
                                        Column(modifier = Modifier.padding(start = 12.dp)) {
                                            Text(
                                                "CAPABILITY DISCREPANCY DETECTED",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.Black
                                            )
                                            Text(
                                                "System reports NO Embed Activity capability, but the Cinema Panel was successfully created and launched. This confirms a reporting bug in the current firmware/SDK.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                            // ----------------------------------
                            
                            // Debug Overlay
                            Box(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .background(Color.DarkGray.copy(alpha = 0.8f))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text("QA MODE: $QA_MODE", color = Color.White)
                                    val reportedEmbedStr = if (reportedEmbed) "YES" else "NO"
                                    Text("Reported Embed Cap: $reportedEmbedStr", color = if (reportedEmbed) Color.Green else Color.Red)
                                    Text("isSpatialUiEnabled: $isSpatialUiEnabled", color = if (isSpatialUiEnabled) Color.Green else Color.Red)
                                    Text("isAppEnvEnabled: $isAppEnvironmentEnabled", color = if (isAppEnvironmentEnabled) Color.Green else Color.Red)
                                    Text("isPassthroughEnabled: $isPassthroughControlEnabled", color = if (isPassthroughControlEnabled) Color.Green else Color.Red)
                                    Text("isSpatialAudioEnabled: $isSpatialAudioEnabled", color = if (isSpatialAudioEnabled) Color.Green else Color.Red)
                                    Text("Current Opacity Pref: ${cinemaManager?.getPreferredPassthroughOpacity() ?: "N/A"}", color = Color.White)
                                    Text("Spatial Env Active: ${cinemaManager?.isSpatialEnvironmentActive() ?: "N/A"}", color = Color.White)
                                    Text("Panel Created: ${cinemaManager?.isPanelCreated ?: "false"}", color = Color.White)
                                    Text("Test Activity Launched: ${cinemaManager?.isActivityLaunched ?: "false"}", color = Color.White)
                                }
                            }

                            Row {
                                Button(
                                    onClick = { 
                                        Log.i(TAG, "LOUD: Manual Trigger Pressed")
                                        session?.let { runCinemaLogic(it, isManual = true) } 
                                    },
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text("Run Logic")
                                }
                                Button(
                                    onClick = { 
                                        cinemaManager?.setPassthroughOpacity(1.0f)
                                    },
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text("Reset Passthrough")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
