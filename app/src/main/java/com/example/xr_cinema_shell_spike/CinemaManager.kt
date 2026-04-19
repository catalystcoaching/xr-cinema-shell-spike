package com.example.xr_cinema_shell_spike

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.ActivityPanelEntity
import androidx.xr.scenecore.Space
import androidx.xr.scenecore.scene

/**
 * CinemaManager encapsulates the logic for creating and managing the XR cinema environment,
 * including activity embedding and passthrough control.
 */
@SuppressLint("RestrictedApi")
class CinemaManager(private val session: Session) {
    private val TAG = "CinemaManager"
    private var cinemaPanel: ActivityPanelEntity? = null

    /**
     * Initializes the cinema panel and launches the embedded activity.
     */
    fun setupCinema(context: Context) {
        if (cinemaPanel != null) {
            Log.i(TAG, "Cinema panel already exists, skipping creation.")
            return
        }

        try {
            Log.i(TAG, "Creating ActivityPanelEntity for Cinema...")
            // 1920x1080 for a standard 16:9 aspect ratio.
            val panel = ActivityPanelEntity.create(
                session,
                IntSize2d(1920, 1080),
                "CinemaScreen"
            )

            // Position: 1.2m up, 2m in front of the user's initial activity space origin.
            panel.setPose(Pose(
                Vector3(0f, 1.2f, -2.0f),
                Quaternion.Identity
            ), Space.ACTIVITY)

            cinemaPanel = panel

            val intent = Intent(context, TestScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            Log.i(TAG, "Launching TestScreenActivity into Cinema Panel...")
            panel.launchActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to setup cinema panel: ${e.message}", e)
        }
    }

    /**
     * Controls the passthrough opacity of the spatial environment.
     * 0.0f is fully opaque (dark environment), 1.0f is fully transparent (passthrough).
     */
    fun setPassthroughOpacity(opacity: Float) {
        try {
            Log.i(TAG, "Requesting preferred passthrough opacity: $opacity")
            session.scene.spatialEnvironment.preferredPassthroughOpacity = opacity
            Log.i(TAG, "Current preferred passthrough opacity: ${session.scene.spatialEnvironment.preferredPassthroughOpacity}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set passthrough opacity: ${e.message}")
        }
    }
}
