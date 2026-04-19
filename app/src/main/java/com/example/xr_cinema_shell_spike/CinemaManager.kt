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
    private var probeUpdateHandler: Handler? = null
    private var probeRunnable: Runnable? = null

    /**
     * Resets all diagnostic state and attempts to clean up existing panels.
     */
    fun reset() {
        Log.i(TAG, "LOUD: State Reset Requested.")
        panelCreationAttempted = false
        panelCreationSucceeded = false
        activityLaunchAttempted = false
        activityLaunchSucceeded = false
        
        probeUpdateHandler?.removeCallbacksAndMessages(null)
        probeUpdateHandler = null
        probeRunnable = null

        activeActivityPanel?.dispose()
        activeActivityPanel = null
        
        activePlainPanel?.dispose()
        activePlainPanel = null
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
     */
    fun setupPlainPanelProbe(context: Context) {
        Log.i(TAG, "LOUD: --- SETUP PLAIN PANEL PROBE (DEBUG_OVERRIDE) ---")
        panelCreationAttempted = true
        try {
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

            val panel = PanelEntity.create(
                session,
                container,
                FloatSize2d(2.0f, 1.5f),
                "ProbePanel",
                Pose(Vector3(0f, 1.2f, -1.0f), Quaternion.Identity)
            )
            
            activePlainPanel = panel
            panelCreationSucceeded = true

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

            Log.i(TAG, "LOUD: Plain Panel Probe Created at 1.0m.")
        } catch (e: Exception) {
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
