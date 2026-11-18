
package com.aryan.clipboardtranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble == null) showBubble()
        return START_STICKY
    }

    // =========================================================
    // FOREGROUND SERVICE
    // =========================================================

    private fun startAsForeground() {

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard Translator is active")
            .setContentText("Tap the floating हिन्दी button after copying text.")
            .setSmallIcon(android.R.drawable.ic_menu_translate)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Translator service",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // =========================================================
    // FLOATING BUBBLE
    // =========================================================

    private fun showBubble() {

        if (bubble != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = TextView(this).apply {

            text = "हि"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF2457C5.toInt())
                setStroke(2, 0xFFFFFFFF.toInt())
            }

            elevation = 12f
            contentDescription = "Translate clipboard to Hindi"
        }

        params = WindowManager.LayoutParams(
            dp(58),
            dp(58),

            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,

            PixelFormat.TRANSLUCENT
        ).apply {

            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(220)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    startX = params.x
                    startY = params.y

                    touchX = event.rawX
                    touchY = event.rawY

                    moved = false

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()

                    if (
                        kotlin.math.abs(dx) > dp(5) ||
                        kotlin.math.abs(dy) > dp(5)
                    ) {
                        moved = true
                    }

                    params.x = startX + dx
                    params.y = startY + dy

                    windowManager.updateViewLayout(view, params)

                    true
                }

                MotionEvent.ACTION_UP -> {

                    if (!moved) {
                        translateClipboard()
                    }

                    true
                }

                else -> true
            }
        }

        bubble = view
        windowManager.addView(view, params)
    }

    // =========================================================
    // CLIPBOARD TRANSLATION
    // =========================================================

    private fun translateClipboard() {

        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val text = try {

            if (clipboard.hasPrimaryClip()) {

                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(this)
                    ?.toString()
                    .orEmpty()

            } else {
                ""
            }

        } catch (e: Exception) {

            showSimpleNotification(
                "Clipboard Translator",
                "Clipboard access failed: ${e.message}"
            )

            return
        }

        if (text.isBlank()) {

            showSimpleNotification(
                "Clipboard Translator",
                "Clipboard is empty. Copy text first."
            )

            return
        }

        if (text.length > 4500) {

            showSimpleNotification(
                "Clipboard Translator",
                "Text is too long. Please copy a shorter passage."
            )

            return
        }

        showSimpleNotification(
            "Clipboard Translator",
            "Translating clipboard text..."
        )

        Thread {

            try {

                val hindi = translateWithGoogle(text)

                showTranslationNotification(text, hindi)

            } catch (e: Exception) {

                showSimpleNotification(
                    "Translation Failed",
                    e.message ?: "Unable to translate text."
                )
            }

        }.start()
    }

    // =========================================================
    // GOOGLE TRANSLATION
    // =========================================================

    private fun translateWithGoogle(input: String): String {

        val encoded = URLEncoder.encode(input, "UTF-8")

        val endpoint =
            "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=en&tl=hi&dt=t&q=$encoded"

        val connection =
            (URL(endpoint).openConnection() as HttpURLConnection).apply {

                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 20000

                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0"
                )
            }

        try {

            val code = connection.responseCode

            if (code !in 200..299) {
                throw Exception("Google endpoint returned HTTP $code")
            }

            val body = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val root = JSONArray(body)
            val chunks = root.getJSONArray(0)

            val output = StringBuilder()

            for (i in 0 until chunks.length()) {

                val chunk = chunks.optJSONArray(i) ?: continue
                val part = chunk.optString(0)

                if (part != "null") {
                    output.append(part)
                }
            }

            return output.toString()
                .ifBlank {
                    throw Exception("Empty translation received")
                }

        } finally {

            connection.disconnect()
        }
    }

    // =========================================================
    // TRANSLATION NOTIFICATION
    // =========================================================

    private fun showTranslationNotification(
        english: String,
        hindi: String
    ) {

        val manager =
            getSystemService(NotificationManager::class.java)

        val channelId = "translation_results"

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                channelId,
                "Translation results",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            manager.createNotificationChannel(channel)
        }

        val preview = if (hindi.length > 180) {
            hindi.take(177) + "..."
        } else {
            hindi
        }

        val notification = Notification.Builder(this, channelId)

            .setSmallIcon(android.R.drawable.ic_menu_translate)

            .setContentTitle("English → Hindi")

            .setContentText(preview)

            .setStyle(
                Notification.BigTextStyle()
                    .bigText(
                        "EN: $english\n\nHI: $hindi"
                    )
            )

            .setAutoCancel(true)

            .build()

        manager.notify(42, notification)
    }

    // =========================================================
    // SIMPLE NOTIFICATION
    // =========================================================

    private fun showSimpleNotification(
        title: String,
        message: String
    ) {

        val manager =
            getSystemService(NotificationManager::class.java)

        val channelId = "translation_results"

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                channelId,
                "Translation results",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            manager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)

            .setSmallIcon(android.R.drawable.ic_menu_translate)

            .setContentTitle(title)

            .setContentText(message)

            .setAutoCancel(true)

            .build()

        manager.notify(43, notification)
    }

    // =========================================================
    // UTILITIES
    // =========================================================

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {

        bubble?.let {

            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }

        bubble = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {

        private const val CHANNEL_ID = "translator_service"
        private const val NOTIFICATION_ID = 41
    }
}
