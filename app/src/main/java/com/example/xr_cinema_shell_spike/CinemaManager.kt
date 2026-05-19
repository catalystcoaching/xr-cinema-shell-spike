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
import android.net.Uri
import android.content.pm.PackageManager
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
    
    // App Picker state
    private var appCandidates: List<android.content.pm.ResolveInfo> = emptyList()
    private var selectedCandidateIndex: Int = -1

    var qaMode: String = "DEBUG_OVERRIDE" // Local state for the spatial controller
    
    // Probe position state
    private val DEFAULT_PROBE_X = 0.0f
    private val DEFAULT_PROBE_Y = 0.0f
    private var currentProbeX = DEFAULT_PROBE_X
    private var currentProbeY = DEFAULT_PROBE_Y

    // Proven baseline - keep unchanged during environment proof
    private val BASE_BACKPLATE_Z = -1.52f
    private val BASE_SCREEN_Z = -1.50f

    private val BASE_BACKPLATE_WIDTH = 3.2f
    private val BASE_BACKPLATE_HEIGHT = 1.8f

    private val BASE_SCREEN_WIDTH = 1.6f
    private val BASE_SCREEN_HEIGHT = 0.9f

    private val BASE_SCREEN_PIXEL_WIDTH = 1920
    private val BASE_SCREEN_PIXEL_HEIGHT = 1080

    private fun getBackplatePose(): Pose =
        Pose(Vector3(currentProbeX, currentProbeY, BASE_BACKPLATE_Z), Quaternion.Identity)

    private fun getScreenPose(): Pose =
        Pose(Vector3(currentProbeX, currentProbeY, BASE_SCREEN_Z), Quaternion.Identity)

    private fun getBackplateSize(): FloatSize2d =
        FloatSize2d(BASE_BACKPLATE_WIDTH, BASE_BACKPLATE_HEIGHT)

    private fun getScreenSize(): FloatSize2d =
        FloatSize2d(BASE_SCREEN_WIDTH, BASE_SCREEN_HEIGHT)

    private fun getScreenSizeInt(): IntSize2d =
        IntSize2d(BASE_SCREEN_PIXEL_WIDTH, BASE_SCREEN_PIXEL_HEIGHT)

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

            val verifyEnvironmentAssetButton = AndroidButton(context).apply {
                text = "VERIFY ENV ASSET"
                setOnClickListener {
                    Log.i(TAG, "LOUD: [Spatial UI] VERIFY ENV ASSET pressed.")
                    verifyEnvironmentAsset(context)
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

            val refreshAppsButton = AndroidButton(context).apply {
                text = "REFRESH APP LIST"
                setOnClickListener {
                    Log.i(TAG, "LOUD: [Spatial UI] REFRESH APP LIST pressed.")
                    refreshAppCandidates(context)
                }
            }

            val appListText = TextView(context).apply {
                text = "No apps discovered."
                textSize = 14f
                setTextColor(android.graphics.Color.YELLOW)
                gravity = Gravity.CENTER
            }

            val launchSelectedButton = AndroidButton(context).apply {
                text = "LAUNCH SELECTED APP"
                setOnClickListener {
                    Log.i(TAG, "LOUD: [Spatial UI] LAUNCH SELECTED APP pressed.")
                    launchSelectedApp(context)
                }
            }

            val nextAppButton = AndroidButton(context).apply {
                text = "NEXT APP"
                setOnClickListener {
                    if (appCandidates.isNotEmpty()) {
                        selectedCandidateIndex = (selectedCandidateIndex + 1) % appCandidates.size
                        Log.i(TAG, "LOUD: [Spatial UI] NEXT APP pressed. Index: $selectedCandidateIndex")
                    }
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
                addView(verifyEnvironmentAssetButton)
                addView(toggleButton)
                addView(downButton)
                addView(upButton)
                addView(resetButton)
                addView(leftButton)
                addView(rightButton)
                addView(resetXButton)
                addView(android.view.View(context).apply { minimumHeight = 20 }) // Spacer
                addView(refreshAppsButton)
                addView(appListText)
                addView(nextAppButton)
                addView(launchSelectedButton)
            }

            val scrollView = android.widget.ScrollView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(layout)
            }

            // Fixed Pose: slightly to the left, 0.4m high (lowered from 1.2m), 1.0m ahead
            val controllerPose = Pose(Vector3(-0.6f, 0.4f, -1.0f), Quaternion.Identity)
            Log.i(TAG, "LOUD: Creating Startup Controller at $controllerPose")
            Log.i(TAG, "LOUD: Startup Controller layout is now scrollable.")
            
            val panel = PanelEntity.create(
                session,
                scrollView,
                FloatSize2d(0.8f, 1.4f),
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
                    
                    if (appCandidates.isEmpty()) {
                        appListText.text = "No apps discovered. Press REFRESH."
                    } else if (selectedCandidateIndex in appCandidates.indices) {
                        val candidate = appCandidates[selectedCandidateIndex]
                        appListText.text = "Selected (%d/%d):\n%s\n%s".format(
                            selectedCandidateIndex + 1,
                            appCandidates.size,
                            candidate.activityInfo.packageName,
                            candidate.activityInfo.name.split(".").last()
                        )
                    }

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
     * Verifies that the first cinema environment model is packaged in app assets.
     */
    fun verifyEnvironmentAsset(context: Context) {
        Log.i(TAG, "LOUD: --- VERIFY ENVIRONMENT ASSET ---")
        val assetPath = "environment/candidate_01/cinema.glb"
        try {
            val sizeBytes = context.assets.open(assetPath).use { it.readBytes().size }
            activeContentStatus = "ENV_ASSET_OK: $sizeBytes bytes"
            Log.i(TAG, "LOUD: environment asset open succeeded for $assetPath ($sizeBytes bytes)")
        } catch (e: Exception) {
            activeContentStatus = "ENV_ASSET_FAIL: ${e.message}"
            Log.e(TAG, "LOUD: environment asset open failed for $assetPath: ${e.message}", e)
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
            val backplatePose = getBackplatePose()
            val screenPose = getScreenPose()
            
            // Sizes
            val backplateSize = getBackplateSize() // Larger than screen
            val screenSize = getScreenSize() // 16:9 ratio

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
            val backplatePose = getBackplatePose()
            val screenPose = getScreenPose()
            
            // Sizes
            val backplateSize = getBackplateSize()
            val screenSize = getScreenSize()
            val screenSizeInt = getScreenSizeInt()

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
            val backplatePose = getBackplatePose()
            val screenPose = getScreenPose()
            
            // Sizes
            val backplateSize = getBackplateSize()
            val screenSize = getScreenSize()
            val screenSizeInt = getScreenSizeInt()

            // 1. Create Backplate
            val backplateView = View(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            }
            activeBackplatePanel = PanelEntity.create(session, backplateView, backplateSize, "Backplate", backplatePose)
            Log.i(TAG, "LOUD: backplate pose: $backplatePose size: $backplateSize")

            // 2. Resolve External App (Browser candidate discovery)
            Log.i(TAG, "LOUD: browser candidate discovery attempted via queryIntentActivities.")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            val candidates = context.packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
            
            Log.i(TAG, "LOUD: number of browser candidates found: ${candidates.size}")
            
            val explicitIntent = if (candidates.isNotEmpty()) {
                // Deterministically choose the first candidate
                val resolveInfo = candidates[0]
                val pkg = resolveInfo.activityInfo.packageName
                val act = resolveInfo.activityInfo.name
                Log.i(TAG, "LOUD: chosen browser candidate package: $pkg")
                Log.i(TAG, "LOUD: chosen browser candidate activity: $act")
                activeContentStatus = "EXTERNAL_RESOLVED (${candidates.size}): $pkg\n$act"
                
                // Build explicit intent
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    setClassName(pkg, act)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                null
            }
            
            if (explicitIntent == null) {
                activeContentStatus = "EXTERNAL_FAIL_RESOLVE (0 candidates)"
                Log.e(TAG, "LOUD: browser resolution failed: zero candidates found for ACTION_VIEW")
                return
            }
            val resolvedPkg = explicitIntent.component?.packageName ?: "Unknown"
            val resolvedAct = explicitIntent.component?.className ?: "Unknown"

            // 3. Create Activity Panel
            Log.i(TAG, "LOUD: ActivityPanelEntity creation attempted.")
            val panel = ActivityPanelEntity.create(session, screenSizeInt, "ExternalAppPanel")
            panel.setPose(screenPose, Space.ACTIVITY)
            activeActivityPanel = panel
            Log.i(TAG, "LOUD: ActivityPanelEntity creation succeeded.")
            Log.i(TAG, "LOUD: final embedded panel pose: $screenPose size: $screenSize")

            // 4. Launch External Activity
            Log.i(TAG, "LOUD: external explicit app launch attempted for $resolvedPkg / $resolvedAct")
            try {
                panel.launchActivity(explicitIntent)
                activeContentStatus = "EXTERNAL_SUCCESS: $resolvedPkg"
                Log.i(TAG, "LOUD: external app launch succeeded.")
            } catch (e: Exception) {
                activeContentStatus = "EXTERNAL_LAUNCH_FAIL: ${e.message}"
                Log.e(TAG, "LOUD: external app launch failed: ${e.message}")
            }

        } catch (e: Exception) {
            activeContentStatus = "EXTERNAL_CRASH: ${e.message}"
            Log.e(TAG, "LOUD: External App Test FAILURE: ${e.message}", e)
        }
    }

    /**
     * Discovers browser and launcher candidates.
     */
    private fun refreshAppCandidates(context: Context) {
        Log.i(TAG, "LOUD: Refreshing app candidates...")
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        val browsers = context.packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
        
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val launchers = context.packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        
        // Merge and deduplicate by component name
        val allCandidates = (browsers + launchers).distinctBy { it.activityInfo.packageName + it.activityInfo.name }
        
        appCandidates = allCandidates
        selectedCandidateIndex = if (appCandidates.isNotEmpty()) 0 else -1
        Log.i(TAG, "LOUD: Discovered ${appCandidates.size} candidates.")
    }

    /**
     * Launches the selected app from the picker.
     */
    private fun launchSelectedApp(context: Context) {
        if (selectedCandidateIndex !in appCandidates.indices) {
            Log.e(TAG, "LOUD: No app selected to launch.")
            return
        }
        
        val candidate = appCandidates[selectedCandidateIndex]
        val pkg = candidate.activityInfo.packageName
        val act = candidate.activityInfo.name
        
        Log.i(TAG, "LOUD: Launching selected app: $pkg / $act")
        
        // Re-use external app test logic but with the selected component
        setupExternalAppWithComponent(context, pkg, act)
    }

    private fun setupExternalAppWithComponent(context: Context, pkg: String, act: String) {
        Log.i(TAG, "LOUD: --- SETUP EXTERNAL APP WITH COMPONENT ---")
        activeContentStatus = "EXTERNAL_PICKER_ATTEMPT"

        activeBackplatePanel?.dispose()
        activeBackplatePanel = null
        activeScreenPanel?.dispose()
        activeScreenPanel = null
        activeActivityPanel?.dispose()
        activeActivityPanel = null
        probeUpdateHandler?.removeCallbacksAndMessages(null)

        try {
            val backplatePose = getBackplatePose()
            val screenPose = getScreenPose()
            val backplateSize = getBackplateSize()
            val screenSizeInt = getScreenSizeInt()

            val backplateView = View(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            }
            activeBackplatePanel = PanelEntity.create(session, backplateView, backplateSize, "Backplate", backplatePose)

            // Determine if we should use ACTION_VIEW (for browsers) or ACTION_MAIN
            val isBrowser = pkg.contains("chrome") || pkg.contains("browser") // Simple heuristic
            val intent = if (isBrowser) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    setClassName(pkg, act)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_MAIN).apply {
                    setClassName(pkg, act)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val panel = ActivityPanelEntity.create(session, screenSizeInt, "ExternalAppPanel")
            panel.setPose(screenPose, Space.ACTIVITY)
            activeActivityPanel = panel

            Log.i(TAG, "LOUD: Launching explicit component: $pkg / $act")
            panel.launchActivity(intent)
            activeContentStatus = "EXTERNAL_SUCCESS: $pkg"
            Log.i(TAG, "LOUD: External app launch succeeded.")
        } catch (e: Exception) {
            activeContentStatus = "EXTERNAL_FAIL: ${e.message}"
            Log.e(TAG, "LOUD: External app launch failed: ${e.message}", e)
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
