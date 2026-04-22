package com.example.xr_cinema_shell_spike

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Button as AndroidButton
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.scenecore.ActivityPanelEntity
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.Space
import androidx.xr.scenecore.scene
import java.util.Date

/**
 * CinemaManager encapsulates the logic for creating and managing the XR cinema environment.
 */
@SuppressLint("RestrictedApi")
class CinemaManager(private val session: Session) {
    private val TAG = "CinemaManager"
    
    var panelCreationAttempted: Boolean by mutableStateOf(false)
        private set
    var panelCreationSucceeded: Boolean by mutableStateOf(false)
        private set
    var activityLaunchAttempted: Boolean by mutableStateOf(false)
        private set
    var activityLaunchSucceeded: Boolean by mutableStateOf(false)
        private set

    private var activeActivityPanel: ActivityPanelEntity? = null
    private var activePlainPanel: PanelEntity? = null
    private var activeControllerPanel: PanelEntity? = null
    private var probeUpdateHandler: Handler? = null
    private var probeRunnable: Runnable? = null
    private var controllerUpdateHandler: Handler? = null
    private var controllerRunnable: Runnable? = null

    var qaMode: String = "DEBUG_OVERRIDE" // Local state for the spatial controller
    
    // Probe position state
    private val DEFAULT_PROBE_X = 0.0f
    private val DEFAULT_PROBE_Y = 0.4f
    private var currentProbeX = DEFAULT_PROBE_X
    private var currentProbeY = DEFAULT_PROBE_Y

    var lastErrorMessage: String by mutableStateOf("")
        private set

    /**
     * Resets all diagnostic state and attempts to clean up existing panels.
     */
    fun reset() {
        Log.i(TAG, "LOUD: State Reset Requested.")
        panelCreationAttempted = false
        panelCreationSucceeded = false
        activityLaunchAttempted = false
        activityLaunchSucceeded = false
        lastErrorMessage = ""
        
        probeUpdateHandler?.removeCallbacksAndMessages(null)
        probeUpdateHandler = null
        probeRunnable = null

        activeActivityPanel?.dispose()
        activeActivityPanel = null
        
        activePlainPanel?.dispose()
        activePlainPanel = null
    }

    /**
     * Disposes of everything including the spatial controller.
     */
    fun fullCleanup() {
        reset()
        controllerUpdateHandler?.removeCallbacksAndMessages(null)
        controllerUpdateHandler = null
        activeControllerPanel?.dispose()
        activeControllerPanel = null
    }

    /**
     * Initializes a spatial controller panel (STARTUP CONTROLLER).
     */
    fun setupStartupControllerPanel(context: Context) {
        Log.i(TAG, "LOUD: --- SETUP STARTUP CONTROLLER PANEL ---")
        try {
            activeControllerPanel?.dispose()

            val titleView = TextView(context).apply {
                text = "XR STARTUP CONTROLLER"
                textSize = 30f
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
            }

            val statusView = TextView(context).apply {
                text = "Mode: $qaMode\nProbe: None"
                textSize = 18f
                setTextColor(android.graphics.Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            }

            val runButton = AndroidButton(context).apply {
                text = "RUN PROBE"
                setOnClickListener {
                    Log.i(TAG, "LOUD: [Spatial UI] RUN PROBE pressed.")
                    setupPlainPanelProbe(context)
                }
            }

            val toggleButton = AndroidButton(context).apply {
                text = "TOGGLE MODE"
                setOnClickListener {
                    qaMode = if (qaMode == "STRICT") "DEBUG_OVERRIDE" else "STRICT"
                    Log.i(TAG, "LOUD: [Spatial UI] Mode toggled to: $qaMode")
                }
            }

            val downButton = AndroidButton(context).apply {
                text = "PROBE DOWN (-0.2)"
                setOnClickListener {
                    currentProbeY -= 0.2f
                    Log.i(TAG, "LOUD: PROBE DOWN pressed. New Y: $currentProbeY")
                    setupPlainPanelProbe(context)
                }
            }

            val upButton = AndroidButton(context).apply {
                text = "PROBE UP (+0.2)"
                setOnClickListener {
                    currentProbeY += 0.2f
                    Log.i(TAG, "LOUD: PROBE UP pressed. New Y: $currentProbeY")
                    setupPlainPanelProbe(context)
                }
            }

            val resetButton = AndroidButton(context).apply {
                text = "RESET HEIGHT"
                setOnClickListener {
                    currentProbeY = DEFAULT_PROBE_Y
                    Log.i(TAG, "LOUD: RESET PROBE HEIGHT pressed. Y: $currentProbeY")
                    setupPlainPanelProbe(context)
                }
            }

            val leftButton = AndroidButton(context).apply {
                text = "PROBE LEFT (-0.2)"
                setOnClickListener {
                    currentProbeX -= 0.2f
                    Log.i(TAG, "LOUD: PROBE LEFT pressed. New X: $currentProbeX")
                    setupPlainPanelProbe(context)
                }
            }

            val rightButton = AndroidButton(context).apply {
                text = "PROBE RIGHT (+0.2)"
                setOnClickListener {
                    currentProbeX += 0.2f
                    Log.i(TAG, "LOUD: PROBE RIGHT pressed. New X: $currentProbeX")
                    setupPlainPanelProbe(context)
                }
            }

            val resetXButton = AndroidButton(context).apply {
                text = "RESET X"
                setOnClickListener {
                    currentProbeX = DEFAULT_PROBE_X
                    Log.i(TAG, "LOUD: RESET X pressed. X: $currentProbeX")
                    setupPlainPanelProbe(context)
                }
            }

            val layout = ColumnLayout(context).apply {
                setBackgroundColor(android.graphics.Color.BLUE)
                setPadding(40, 40, 40, 40)
                addView(titleView)
                addView(statusView)
                addView(runButton)
                addView(toggleButton)
                addView(downButton)
                addView(upButton)
                addView(resetButton)
                addView(leftButton)
                addView(rightButton)
                addView(resetXButton)
            }

            // Fixed Pose: slightly to the left, 1.2m high, 1.0m ahead
            val controllerPose = Pose(Vector3(-0.6f, 1.2f, -1.0f), Quaternion.Identity)
            
            val panel = PanelEntity.create(
                session,
                layout,
                FloatSize2d(0.8f, 0.6f),
                "ControllerPanel",
                controllerPose
            )
            
            activeControllerPanel = panel
            Log.i(TAG, "LOUD: Startup Controller Panel SUCCESS.")

            controllerUpdateHandler = Handler(Looper.getMainLooper())
            controllerRunnable = object : Runnable {
                override fun run() {
                    val probeStatus = if (activePlainPanel != null) "ACTIVE" else "None"
                    statusView.text = "Mode: $qaMode\nProbe: $probeStatus\nX: %.1f, Y: %.1f".format(currentProbeX, currentProbeY)
                    controllerUpdateHandler?.postDelayed(this, 500)
                }
            }
            controllerUpdateHandler?.post(controllerRunnable!!)

        } catch (e: Exception) {
            Log.e(TAG, "LOUD: Startup Controller Panel FAILURE: ${e.message}", e)
        }
    }

    /**
     * Helper layout for the spatial controller
     */
    private class ColumnLayout(context: Context) : android.widget.LinearLayout(context) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }
    }

    /**
     * Initializes a standard ActivityPanel (STRICT mode).
     */
    fun setupActivityPanel(context: Context) {
        Log.i(TAG, "LOUD: --- SETUP ACTIVITY PANEL (STRICT) ---")
        panelCreationAttempted = true
        try {
            val panel = ActivityPanelEntity.create(session, IntSize2d(1920, 1080), "StrictPanel")
            panel.setPose(Pose(Vector3(0f, 1.2f, -2.0f), Quaternion.Identity), Space.ACTIVITY)
            activeActivityPanel = panel
            panelCreationSucceeded = true

            val intent = Intent(context, TestScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityLaunchAttempted = true
            panel.launchActivity(intent)
            activityLaunchSucceeded = true
            Log.i(TAG, "LOUD: Activity Panel SUCCESS.")
        } catch (e: Exception) {
            Log.e(TAG, "LOUD: Activity Panel FAILURE: ${e.message}")
        }
    }

    /**
     * Initializes a plain PanelEntity probe (DEBUG_OVERRIDE mode).
     * USES A FIXED POSE TO AVOID HEAD-TRACKING DEPENDENCY.
     */
    fun setupPlainPanelProbe(context: Context) {
        Log.i(TAG, "LOUD: --- SETUP PLAIN PANEL PROBE (DEBUG_OVERRIDE) ---")
        Log.i(TAG, "LOUD: [BYPASS] Head-tracking dependency removed. Using Fixed Pose.")
        Log.i(TAG, "LOUD: Current Probe X: $currentProbeX, Y: $currentProbeY used.")
        
        // Dispose of any old probe first
        activePlainPanel?.dispose()
        activePlainPanel = null
        probeUpdateHandler?.removeCallbacksAndMessages(null)

        panelCreationAttempted = true
        try {
            // Fixed Pose: currentProbeX offset, currentProbeY high, 1.5m ahead
            val finalPose = Pose(Vector3(currentProbeX, currentProbeY, -1.5f), Quaternion.Identity)
            Log.i(TAG, "LOUD: Fixed Probe Pose used: $finalPose")

            val textView = TextView(context).apply {
                text = "PLAIN PANEL PROBE\n(INITIALIZING)"
                textSize = 50f
                setTextColor(android.graphics.Color.BLACK)
                setBackgroundColor(android.graphics.Color.YELLOW)
                gravity = Gravity.CENTER
                setPadding(50, 50, 50, 50)
            }
            
            val container = FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.RED)
                setPadding(20, 20, 20, 20)
                addView(textView)
            }

            val panelSize = FloatSize2d(1.5f, 1.0f)
            val panel = PanelEntity.create(
                session,
                container,
                panelSize,
                "ProbePanel",
                finalPose
            )
            
            activePlainPanel = panel
            panelCreationSucceeded = true
            
            Log.i(TAG, "LOUD: Plain Panel Probe SUCCESS.")
            Log.i(TAG, "LOUD: Probe Pose: $finalPose")
            Log.i(TAG, "LOUD: Probe Size: $panelSize")

            // Live update counter
            probeUpdateHandler = Handler(Looper.getMainLooper())
            probeRunnable = object : Runnable {
                var ticks = 0
                override fun run() {
                    textView.text = "PLAIN PANEL PROBE\nTicks: ${ticks++}\n${Date()}"
                    probeUpdateHandler?.postDelayed(this, 1000)
                }
            }
            probeUpdateHandler?.post(probeRunnable!!)
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Unknown Error"
            Log.e(TAG, "LOUD: Plain Panel Probe FAILURE: ${e.message}", e)
        }
    }

    /**
     * Stubs for compilation
     */
    fun setPassthroughOpacity(opacity: Float) {}
    fun getPreferredPassthroughOpacity(): Float = 1.0f
    fun isSpatialEnvironmentActive(): Boolean = false
}
