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
    private var activeBackplatePanel: PanelEntity? = null
    private var activeScreenPanel: PanelEntity? = null
    private var activeContentStatus: String = "None"
    private var activeControllerPanel: PanelEntity? = null
    private var probeUpdateHandler: Handler? = null
    private var probeRunnable: Runnable? = null
    private var controllerUpdateHandler: Handler? = null
    private var controllerRunnable: Runnable? = null

    var qaMode: String = "DEBUG_OVERRIDE" // Local state for the spatial controller
    
    // Probe position state
    private val DEFAULT_PROBE_X = 0.0f
    private val DEFAULT_PROBE_Y = 0.0f
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
        
        activeBackplatePanel?.dispose()
        activeBackplatePanel = null

        activeScreenPanel?.dispose()
        activeScreenPanel = null
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
                text = "RUN SCREEN ASSEMBLY"
                setOnClickListener {
                    Log.i(TAG, "LOUD: [Spatial UI] RUN SCREEN ASSEMBLY pressed.")
                    setupScreenAssembly(context)
                }
            }

            val runContentButton = AndroidButton(context).apply {
                text = "RUN CONTENT PANEL TEST"
                setOnClickListener {
                    Log.i(TAG, "LOUD: [Spatial UI] RUN CONTENT PANEL TEST pressed.")
                    setupContentPanelTest(context)
                }
            }

            val runExternalButton = AndroidButton(context).apply {
                text = "RUN EXTERNAL APP TEST"
                setOnClickListener {
                    Log.i(TAG, "LOUD: [Spatial UI] RUN EXTERNAL APP TEST pressed.")
                    setupExternalAppTest(context)
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
                text = "SCREEN DOWN (-0.2)"
                setOnClickListener {
                    currentProbeY -= 0.2f
                    Log.i(TAG, "LOUD: SCREEN DOWN pressed. New Y: $currentProbeY")
                    setupScreenAssembly(context)
                }
            }

            val upButton = AndroidButton(context).apply {
                text = "SCREEN UP (+0.2)"
                setOnClickListener {
                    currentProbeY += 0.2f
                    Log.i(TAG, "LOUD: SCREEN UP pressed. New Y: $currentProbeY")
                    setupScreenAssembly(context)
                }
            }

            val resetButton = AndroidButton(context).apply {
                text = "RESET HEIGHT"
                setOnClickListener {
                    currentProbeY = DEFAULT_PROBE_Y
                    Log.i(TAG, "LOUD: RESET SCREEN HEIGHT pressed. Y: $currentProbeY")
                    setupScreenAssembly(context)
                }
            }

            val leftButton = AndroidButton(context).apply {
                text = "SCREEN LEFT (-0.2)"
                setOnClickListener {
                    currentProbeX -= 0.2f
                    Log.i(TAG, "LOUD: SCREEN LEFT pressed. New X: $currentProbeX")
                    setupScreenAssembly(context)
                }
            }

            val rightButton = AndroidButton(context).apply {
                text = "SCREEN RIGHT (+0.2)"
                setOnClickListener {
                    currentProbeX += 0.2f
                    Log.i(TAG, "LOUD: SCREEN RIGHT pressed. New X: $currentProbeX")
                    setupScreenAssembly(context)
                }
            }

            val resetXButton = AndroidButton(context).apply {
                text = "RESET X"
                setOnClickListener {
                    currentProbeX = DEFAULT_PROBE_X
                    Log.i(TAG, "LOUD: RESET X pressed. X: $currentProbeX")
                    setupScreenAssembly(context)
                }
            }

            val layout = ColumnLayout(context).apply {
                setBackgroundColor(android.graphics.Color.BLUE)
                setPadding(40, 40, 40, 40)
                addView(titleView)
                addView(statusView)
                addView(runButton)
                addView(runContentButton)
                addView(runExternalButton)
                addView(toggleButton)
                addView(downButton)
                addView(upButton)
                addView(resetButton)
                addView(leftButton)
                addView(rightButton)
                addView(resetXButton)
            }

            // Fixed Pose: slightly to the left, 0.4m high (lowered from 1.2m), 1.0m ahead
            val controllerPose = Pose(Vector3(-0.6f, 0.4f, -1.0f), Quaternion.Identity)
            Log.i(TAG, "LOUD: Creating Startup Controller at $controllerPose")
            
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
                    val slotActive = activeScreenPanel != null
                    val internalActive = activeActivityPanel != null && activeContentStatus.contains("CONTENT")
                    val externalActive = activeActivityPanel != null && activeContentStatus.contains("EXTERNAL")
                    
                    val slotStatus = when {
                        slotActive -> "SLOT ACTIVE"
                        internalActive -> "INTERNAL ACTIVE"
                        externalActive -> "EXTERNAL ACTIVE"
                        else -> "None"
                    }
                    
                    statusView.text = "Mode: $qaMode\nAssembly: $slotStatus\nStatus: $activeContentStatus\nX: %.1f, Y: %.1f".format(currentProbeX, currentProbeY)
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
     * Initializes a anchored screen assembly (backplate + slot).
     */
    fun setupScreenAssembly(context: Context) {
        Log.i(TAG, "LOUD: --- SETUP SCREEN ASSEMBLY ---")
        Log.i(TAG, "LOUD: screen assembly creation attempted.")
        activeContentStatus = "SLOT_RUN"
        
        // Dispose of any old assembly first
        activeActivityPanel?.dispose()
        activeActivityPanel = null
        activeScreenPanel?.dispose()
        activeScreenPanel = null
        probeUpdateHandler?.removeCallbacksAndMessages(null)

        panelCreationAttempted = true
        try {
            // Poses
            val backplatePose = Pose(Vector3(currentProbeX, currentProbeY, -1.52f), Quaternion.Identity)
            val screenPose = Pose(Vector3(currentProbeX, currentProbeY, -1.50f), Quaternion.Identity)
            
            // Sizes
            val backplateSize = FloatSize2d(3.2f, 1.8f) // Larger than screen
            val screenSize = FloatSize2d(1.6f, 0.9f) // 16:9 ratio

            // 1. Create Backplate
            val backplateView = View(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A")) // Dark matte gray
            }
            activeBackplatePanel = PanelEntity.create(session, backplateView, backplateSize, "Backplate", backplatePose)
            Log.i(TAG, "LOUD: backplate pose: $backplatePose size: $backplateSize")

            // 2. Create Screen Slot
            val textView = TextView(context).apply {
                text = "SCREEN SLOT"
                textSize = 40f
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
            }

            val timestampView = TextView(context).apply {
                text = Date().toString()
                textSize = 12f
                setTextColor(android.graphics.Color.GRAY)
                gravity = Gravity.BOTTOM or Gravity.END
            }
            
            val innerScreen = FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.DKGRAY)
                addView(textView)
                addView(timestampView)
            }

            val outerFrame = FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setPadding(20, 20, 20, 20)
                addView(innerScreen)
            }

            activeScreenPanel = PanelEntity.create(session, outerFrame, screenSize, "ScreenSlot", screenPose)
            Log.i(TAG, "LOUD: screen slot pose: $screenPose size: $screenSize")

            panelCreationSucceeded = true
            Log.i(TAG, "LOUD: screen assembly creation succeeded.")

            // Live update timestamp
            probeUpdateHandler = Handler(Looper.getMainLooper())
            probeRunnable = object : Runnable {
                override fun run() {
                    timestampView.text = Date().toString()
                    probeUpdateHandler?.postDelayed(this, 1000)
                }
            }
            probeUpdateHandler?.post(probeRunnable!!)
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Unknown Error"
            Log.e(TAG, "LOUD: Screen Assembly FAILURE: ${e.message}", e)
        }
    }

    /**
     * Initializes a anchored screen assembly with an ActivityPanelEntity (CONTENT PANEL TEST).
     */
    fun setupContentPanelTest(context: Context) {
        Log.i(TAG, "LOUD: --- SETUP CONTENT PANEL TEST ---")
        Log.i(TAG, "LOUD: RUN CONTENT PANEL TEST pressed.")
        activeContentStatus = "CONTENT_ATTEMPTED"

        // Dispose of any old assembly first
        activeBackplatePanel?.dispose()
        activeBackplatePanel = null
        activeScreenPanel?.dispose()
        activeScreenPanel = null
        activeActivityPanel?.dispose()
        activeActivityPanel = null
        probeUpdateHandler?.removeCallbacksAndMessages(null)

        try {
            // Poses
            val backplatePose = Pose(Vector3(currentProbeX, currentProbeY, -1.52f), Quaternion.Identity)
            val screenPose = Pose(Vector3(currentProbeX, currentProbeY, -1.50f), Quaternion.Identity)
            
            // Sizes
            val backplateSize = FloatSize2d(3.2f, 1.8f)
            val screenSize = FloatSize2d(1.6f, 0.9f)
            val screenSizeInt = IntSize2d(1920, 1080)

            // 1. Create Backplate
            val backplateView = View(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            }
            activeBackplatePanel = PanelEntity.create(session, backplateView, backplateSize, "Backplate", backplatePose)
            Log.i(TAG, "LOUD: backplate pose: $backplatePose size: $backplateSize")

            // 2. Create Activity Panel
            Log.i(TAG, "LOUD: ActivityPanelEntity creation attempted.")
            val panel = ActivityPanelEntity.create(session, screenSizeInt, "ContentPanel")
            panel.setPose(screenPose, Space.ACTIVITY)
            activeActivityPanel = panel
            Log.i(TAG, "LOUD: ActivityPanelEntity creation succeeded.")
            Log.i(TAG, "LOUD: final embedded panel pose: $screenPose size: $screenSize")

            val intent = Intent(context, CinemaContentTestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.i(TAG, "LOUD: CinemaContentTestActivity launch attempted.")
            panel.launchActivity(intent)
            activeContentStatus = "CONTENT_SUCCESS"
            Log.i(TAG, "LOUD: CinemaContentTestActivity launch succeeded.")

        } catch (e: Exception) {
            activeContentStatus = "CONTENT_FAILED"
            Log.e(TAG, "LOUD: Content Panel Test FAILURE: ${e.message}", e)
        }
    }

    /**
     * Initializes a anchored screen assembly with an ActivityPanelEntity launching an external app.
     */
    fun setupExternalAppTest(context: Context) {
        Log.i(TAG, "LOUD: --- SETUP EXTERNAL APP TEST ---")
        Log.i(TAG, "LOUD: RUN EXTERNAL APP TEST pressed.")
        activeContentStatus = "EXTERNAL_ATTEMPTED"

        // Dispose of any old assembly first
        activeBackplatePanel?.dispose()
        activeBackplatePanel = null
        activeScreenPanel?.dispose()
        activeScreenPanel = null
        activeActivityPanel?.dispose()
        activeActivityPanel = null
        probeUpdateHandler?.removeCallbacksAndMessages(null)

        try {
            // Poses
            val backplatePose = Pose(Vector3(currentProbeX, currentProbeY, -1.52f), Quaternion.Identity)
            val screenPose = Pose(Vector3(currentProbeX, currentProbeY, -1.50f), Quaternion.Identity)
            
            // Sizes
            val backplateSize = FloatSize2d(3.2f, 1.8f)
            val screenSizeInt = IntSize2d(1920, 1080)

            // 1. Create Backplate
            val backplateView = View(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            }
            activeBackplatePanel = PanelEntity.create(session, backplateView, backplateSize, "Backplate", backplatePose)
            Log.i(TAG, "LOUD: backplate pose: $backplatePose size: $backplateSize")

            // 2. Resolve External App (Chrome as default target)
            val packageName = "com.android.chrome"
            Log.i(TAG, "LOUD: external app resolution attempted for: $packageName")
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            
            if (intent == null) {
                activeContentStatus = "EXTERNAL_FAIL_RESOLVE"
                Log.e(TAG, "LOUD: external app resolution failed: package not found ($packageName)")
                return
            }
            Log.i(TAG, "LOUD: resolved external package: ${intent.component?.packageName}")

            // 3. Create Activity Panel
            Log.i(TAG, "LOUD: ActivityPanelEntity creation attempted.")
            val panel = ActivityPanelEntity.create(session, screenSizeInt, "ExternalAppPanel")
            panel.setPose(screenPose, Space.ACTIVITY)
            activeActivityPanel = panel
            Log.i(TAG, "LOUD: ActivityPanelEntity creation succeeded.")
            Log.i(TAG, "LOUD: final embedded panel pose: $screenPose size: 1.6x0.9")

            // 4. Launch External Activity
            Log.i(TAG, "LOUD: external app launch attempted.")
            panel.launchActivity(intent)
            activeContentStatus = "EXTERNAL_SUCCESS ($packageName)"
            Log.i(TAG, "LOUD: external app launch succeeded.")

        } catch (e: Exception) {
            activeContentStatus = "EXTERNAL_CRASH"
            Log.e(TAG, "LOUD: External App Test FAILURE: ${e.message}", e)
        }
    }

    /**
     * Legacy method mapping for compilation if needed
     */
    fun setupScreenSlot(context: Context) = setupScreenAssembly(context)

    /**
     * Stubs for compilation
     */
    fun setPassthroughOpacity(opacity: Float) {}
    fun getPreferredPassthroughOpacity(): Float = 1.0f
    fun isSpatialEnvironmentActive(): Boolean = false
}
