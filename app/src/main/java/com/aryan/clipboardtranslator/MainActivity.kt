package com.aryan.clipboardtranslator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 28)
        }

        val title = TextView(this).apply {
            text = "Clipboard Translator"
            textSize = 25f
            setTextColor(0xFF172033.toInt())
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }

        val enable = Button(this).apply {
            text = "Enable floating button"
            setOnClickListener { enableBubble() }
        }
        val disable = Button(this).apply {
            text = "Stop floating button"
            setOnClickListener {
                stopService(Intent(this@MainActivity, BubbleService::class.java))
                updateStatus()
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(enable)
        root.addView(disable)
        setContentView(root)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun enableBubble() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        val intent = Intent(this, BubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        updateStatus()
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        status.text = if (Settings.canDrawOverlays(this)) {
            "Overlay permission: granted\nTap Enable to show the floating Hindi button."
        } else {
            "Overlay permission: not granted\nEnable it when Android opens Settings."
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }
}
