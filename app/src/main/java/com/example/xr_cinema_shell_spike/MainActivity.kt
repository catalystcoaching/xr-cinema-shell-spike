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
import java.util.function.Consumer

class MainActivity : ComponentActivity() {

    private val TAG = "XR_CINEMA_SPIKE"
    
    // --- QA MODE TOGGLE ---
    private enum class Mode { STRICT, DEBUG_OVERRIDE }
    private var qaMode by mutableStateOf(Mode.DEBUG_OVERRIDE)
    // ----------------------

    private var cinemaManager: CinemaManager? = null

    @SuppressLint("RestrictedApi")
    private fun runCinemaLogic(session: Session, isManual: Boolean = false) {
        val caps = session.scene.spatialCapabilities
        val hasEmbed = caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_EMBED_ACTIVITY)
        
        Log.i(TAG, "LOUD: --- RUN CINEMA LOGIC (Mode: $qaMode, Manual: $isManual) ---")
        Log.i(TAG, "LOUD: Reported Capabilities: $caps")

        if (cinemaManager == null) {
            cinemaManager = CinemaManager(session)
        }
        
        // Reset diagnostic state for every run
        cinemaManager?.reset()

        val shouldProceed = when (qaMode) {
            Mode.STRICT -> hasEmbed
            Mode.DEBUG_OVERRIDE -> true
        }

        if (!shouldProceed) {
            Log.i(TAG, "LOUD: $qaMode mode - aborting setup. Requirements not met (Embed Activity Capability: $hasEmbed).")
            return
        }

        val reason = when {
            hasEmbed -> "CAPABILITY FOUND (STRICT)"
            qaMode == Mode.DEBUG_OVERRIDE -> "DEBUG_OVERRIDE ACTIVE"
            else -> "UNKNOWN"
        }
        
        Log.i(TAG, "LOUD: $reason - Proceeding with setup.")
        when (qaMode) {
            Mode.STRICT -> cinemaManager?.setupActivityPanel(this)
            Mode.DEBUG_OVERRIDE -> cinemaManager?.setupPlainPanelProbe(this)
        }
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
                
                // Track Full Space status manually for UI
                var isFullSpace by remember { mutableStateOf(false) }

                LaunchedEffect(session) {
                    if (session != null) {
                        Log.i(TAG, "LOUD: Registering Spatial Capabilities Listener...")
                        session.scene.addSpatialCapabilitiesChangedListener(Consumer { caps ->
                            Log.i(TAG, "LOUD: Reported Capabilities changed: $caps")
                            if (caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_EMBED_ACTIVITY)) {
                                Log.i(TAG, "LOUD: EMBED_ACTIVITY capability detected!")
                                runCinemaLogic(session)
                            }
                        })

                        Log.i(TAG, "LOUD: Requesting Full Space Mode...")
                        spatialConfig.requestFullSpaceMode()
                        isFullSpace = true
                        Log.i(TAG, "LOUD: Full Space entered (requested/assumed)")
                        
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
                            val actualPanel = cinemaManager?.panelCreationSucceeded ?: false
                            
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("QA MODE: ", color = Color.White)
                                        Button(onClick = {
                                            qaMode = if (qaMode == Mode.STRICT) Mode.DEBUG_OVERRIDE else Mode.STRICT
                                            Log.i(TAG, "LOUD: QA Mode toggled to: $qaMode")
                                            session?.let { runCinemaLogic(it, isManual = true) }
                                        }) {
                                            Text(qaMode.name)
                                        }
                                    }
                                    Text("Full Space: $isFullSpace", color = if (isFullSpace) Color.Green else Color.Red)
                                    
                                    val cm = cinemaManager
                                    Text("Panel Attempted: ${cm?.panelCreationAttempted}", color = Color.White)
                                    Text("Panel Succeeded: ${cm?.panelCreationSucceeded}", color = if (cm?.panelCreationSucceeded == true) Color.Green else Color.White)
                                    Text("Activity Launched: ${cm?.activityLaunchSucceeded}", color = if (cm?.activityLaunchSucceeded == true) Color.Green else Color.White)
                                    
                                    val discrepancy = (cm?.panelCreationSucceeded == true) && !isSpatialUiEnabled
                                    Text("Discrepancy: $discrepancy", color = if (discrepancy) Color.Red else Color.White)
                                    
                                    Log.i(TAG, "LOUD: UI Log - Mode: $qaMode, Attempted: ${cm?.panelCreationAttempted}, Success: ${cm?.panelCreationSucceeded}, Launched: ${cm?.activityLaunchSucceeded}")
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
