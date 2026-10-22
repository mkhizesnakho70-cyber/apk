package com.example.tradebot

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Api.prefs(this)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        col.addView(TextView(this).apply { text = "TradeBot Overlay"; textSize = 24f })
        col.addView(TextView(this).apply {
            text = "Shows your MT5 account in rand over other apps and lets you ALLOW or DENY the bot."
            setPadding(0, pad / 2, 0, pad)
        })

        val url = EditText(this).apply { hint = "Server URL, e.g. https://bot.example.com"; setText(p.getString("url", "")); inputType = InputType.TYPE_TEXT_VARIATION_URI }
        val token = EditText(this).apply { hint = "Token"; setText(p.getString("token", "")); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        col.addView(url); col.addView(token)

        col.addView(Button(this).apply {
            text = "Save & start overlay"
            setOnClickListener {
                p.edit().putString("url", url.text.toString().trim()).putString("token", token.text.toString().trim()).apply()
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Allow 'Display over other apps', then come back and tap again", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    startForegroundService(Intent(this@MainActivity, OverlayService::class.java))
                }
            }
        })
        col.addView(Button(this).apply {
            text = "Stop overlay"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        })
        setContentView(col)
    }
}