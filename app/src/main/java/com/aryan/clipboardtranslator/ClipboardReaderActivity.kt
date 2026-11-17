package com.aryan.clipboardtranslator

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

class ClipboardReaderActivity : Activity() {
    private lateinit var sourceView: TextView
    private lateinit var resultView: TextView
    private lateinit var statusView: TextView
    private var translatedText: String = ""
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 22)
        }
        val title = TextView(this).apply {
            text = "English → Hindi"
            textSize = 23f
            setTextColor(0xFF172033.toInt())
        }
        statusView = TextView(this).apply {
            text = "Reading clipboard..."
            textSize = 14f
            setPadding(0, 12, 0, 12)
        }
        sourceView = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFF333333.toInt())
        }
        resultView = TextView(this).apply {
            textSize = 19f
            setTextColor(0xFF172033.toInt())
            setPadding(0, 18, 0, 18)
        }
        val copyButton = Button(this).apply {
            text = "Copy Hindi translation"
            setOnClickListener {
                if (translatedText.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Hindi translation", translatedText))
                    statusView.text = "Hindi translation copied."
                }
            }
        }
        val closeButton = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(statusView)
            addView(TextView(this@ClipboardReaderActivity).apply {
                text = "COPIED TEXT"
                textSize = 12f
                setTextColor(0xFF687386.toInt())
                setPadding(0, 10, 0, 6)
            })
            addView(sourceView)
            addView(TextView(this@ClipboardReaderActivity).apply {
                text = "HINDI TRANSLATION"
                textSize = 12f
                setTextColor(0xFF687386.toInt())
                setPadding(0, 20, 0, 6)
            })
            addView(resultView)
            addView(copyButton)
            addView(closeButton)
        }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        // This Activity is now foregrounded, so Android permits clipboard access.
        window.decorView.postDelayed({ readAndTranslateClipboard() }, 250)
    }

    private fun readAndTranslateClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = try {
            if (clipboard.hasPrimaryClip()) clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            else ""
        } catch (e: Exception) {
            statusView.text = "Clipboard access failed: ${e.message}"
            return
        }

        if (text.isBlank()) {
            statusView.text = "Clipboard is empty. Copy some text and tap the floating button again."
            return
        }

        sourceView.text = text
        statusView.text = "Translating..."
        executor.execute {
            try {
                val hindi = translateWithGoogle(text)
                runOnUiThread {
                    translatedText = hindi
                    resultView.text = hindi
                    statusView.text = "Translation complete."
                    showTranslationNotification(text, hindi)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusView.text = "Translation failed: ${e.message}"
                    resultView.text = "Check your internet connection and try again."
                }
            }
        }
    }

    // Uses Google's unofficial web translation endpoint. It does not require an API key,
    // but Google may throttle requests; this is not a supported production API.
    private fun translateWithGoogle(input: String): String {
        require(input.length <= 4500) { "Text is too long. Please copy a shorter passage." }

        val encoded = URLEncoder.encode(input, "UTF-8")
        val endpoint = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=en&tl=hi&dt=t&q=$encoded"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw Exception("Google endpoint returned HTTP $code")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONArray(body)
            val chunks = root.getJSONArray(0)
            val output = StringBuilder()
            for (i in 0 until chunks.length()) {
                val chunk = chunks.optJSONArray(i) ?: continue
                val part = chunk.optString(0)
                if (part != "null") output.append(part)
            }
            return output.toString().ifBlank { throw Exception("Empty translation received") }
        } finally {
            connection.disconnect()
        }
    }

    private fun showTranslationNotification(english: String, hindi: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "translation_results"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Translation results", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val preview = if (hindi.length > 180) hindi.take(177) + "..." else hindi
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("English → Hindi")
                .setContentText(preview)
                .setStyle(Notification.BigTextStyle().bigText("EN: $english\n\nHI: $hindi"))
                .setAutoCancel(true)
                .build()
        } else {
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("English → Hindi")
                .setContentText(preview)
                .setAutoCancel(true)
                .build()
        }
        manager.notify(42, notification)
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
