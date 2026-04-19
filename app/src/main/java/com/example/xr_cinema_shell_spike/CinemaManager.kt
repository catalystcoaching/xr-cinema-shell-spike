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
import androidx.xr.scenecore.ActivityPanelEntity
import androidx.xr.scenecore.Space
import androidx.xr.scenecore.scene

/**
 * CinemaManager encapsulates the logic for creating and managing the XR cinema environment.
 */
@SuppressLint("RestrictedApi")
class CinemaManager(private val session: Session) {
    private val TAG = "CinemaManager"
    
    var isPanelCreated: Boolean by mutableStateOf(false)
        private set
    var isActivityLaunched: Boolean by mutableStateOf(false)
        private set

    /**
     * Initializes the cinema panel and launches the embedded activity.
     */
    fun setupCinema(context: Context) {
        if (isPanelCreated) {
            Log.i(TAG, "LOUD: Cinema panel already exists, skipping creation.")
            return
        }

        try {
            Log.i(TAG, "LOUD: Creating ActivityPanelEntity for Cinema...")
            val panel = ActivityPanelEntity.create(
                session,
                IntSize2d(1920, 1080),
                "CinemaScreen"
            )

            panel.setPose(Pose(
                Vector3(0f, 1.2f, -2.0f),
                Quaternion.Identity
            ), Space.ACTIVITY)

            isPanelCreated = true
            Log.i(TAG, "LOUD: ActivityPanelEntity created: true")

            val intent = Intent(context, TestScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            Log.i(TAG, "LOUD: Launching TestScreenActivity into Cinema Panel...")
            panel.launchActivity(intent)
            isActivityLaunched = true
            Log.i(TAG, "LOUD: TestScreenActivity launched: true")
        } catch (e: Exception) {
            Log.e(TAG, "LOUD: CRITICAL: Failed to setup cinema panel: ${e.message}", e)
        }
    }

    /**
     * Controls the passthrough opacity of the spatial environment.
     */
    fun setPassthroughOpacity(opacity: Float) {
        try {
            Log.i(TAG, "LOUD: Requesting preferred passthrough opacity: $opacity")
            session.scene.spatialEnvironment.preferredPassthroughOpacity = opacity
            Log.i(TAG, "LOUD: Preferred passthrough opacity set to: ${session.scene.spatialEnvironment.preferredPassthroughOpacity}")
            Log.i(TAG, "LOUD: Current passthrough opacity: ${session.scene.spatialEnvironment.preferredPassthroughOpacity}") // In this context, they are the same
        } catch (e: Exception) {
            Log.e(TAG, "LOUD: Failed to set passthrough opacity: ${e.message}")
        }
    }
    
    fun getPreferredPassthroughOpacity(): Float {
        return session.scene.spatialEnvironment.preferredPassthroughOpacity
    }

    fun isSpatialEnvironmentActive(): Boolean {
        // In our cinema context, "active" means the passthrough is darkened (opacity < 1.0)
        return session.scene.spatialEnvironment.preferredPassthroughOpacity < 1.0f
    }
}
