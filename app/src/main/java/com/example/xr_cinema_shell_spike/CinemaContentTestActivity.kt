package com.example.xr_cinema_shell_spike

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import androidx.activity.ComponentActivity
import java.util.Date

/**
 * A simple internal activity to test ActivityPanelEntity embedding.
 */
class CinemaContentTestActivity : ComponentActivity() {
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            // Optional: Add a border to the root layout if needed, 
            // but the requirement says "obvious border or frame"
        }

        val border = FrameLayout(this).apply {
            val padding = 10
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.CYAN) // Obvious border color
        }

        val content = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(20, 20, 20)) // Dark background
        }

        val title = TextView(this).apply {
            text = "CINEMA CONTENT TEST"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val clock = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.GREEN)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 50)
        }

        content.addView(title)
        content.addView(clock)
        border.addView(content)
        root.addView(border)

        setContentView(root)

        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                clock.text = "LIVE CLOCK: ${Date()}"
                timerHandler?.postDelayed(this, 1000)
            }
        }
        timerHandler?.post(timerRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler?.removeCallbacksAndMessages(null)
    }
}
