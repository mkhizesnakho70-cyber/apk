package com.example.tradebot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var tvEquity: TextView
    private lateinit var tvPnl: TextView
    private lateinit var tvInfo: TextView
    private lateinit var btnAllow: Button
    private lateinit var btnDeny: Button

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var running = true
    private var zar = 0.0
    private var zarAt = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlay()
        poll()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        if (::root.isInitialized) runCatching { wm.removeView(root) }
        io.shutdownNow()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("overlay", "Overlay", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, "overlay")
            .setContentTitle("TradeBot overlay running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildOverlay() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 15, 18, 28)); cornerRadius = dp(14).toFloat()
            }
        }
        tvEquity = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; text = "R --" }
        tvPnl = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 14f; text = "P/L R --" }
        tvInfo = TextView(this).apply { setTextColor(Color.GRAY); textSize = 11f; text = "connecting..." }

        btnAllow = Button(this).apply { text = "ALLOW"; textSize = 12f; setOnClickListener { setAllow(true) } }
        btnDeny = Button(this).apply { text = "DENY"; textSize = 12f; setOnClickListener { setAllow(false) } }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnAllow, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(btnDeny, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        root.addView(tvEquity); root.addView(tvPnl); root.addView(tvInfo); root.addView(row)

        params = WindowManager.LayoutParams(
            dp(210), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(120) }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        val drag = View.OnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    params.x = sx + (e.rawX - tx).toInt(); params.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(root, params)
                }
            }
            true
        }
        tvEquity.setOnTouchListener(drag); tvPnl.setOnTouchListener(drag); tvInfo.setOnTouchListener(drag)
        wm.addView(root, params)
        paintButtons(false)
    }

    private fun paintButtons(allow: Boolean) {
        btnAllow.setBackgroundColor(if (allow) Color.rgb(34, 160, 80) else Color.rgb(60, 66, 80))
        btnDeny.setBackgroundColor(if (!allow) Color.rgb(200, 60, 60) else Color.rgb(60, 66, 80))
        btnAllow.setTextColor(Color.WHITE); btnDeny.setTextColor(Color.WHITE)
    }

    private fun setAllow(v: Boolean) {
        val p = Api.prefs(this)
        io.execute {
            try {
                val now = Api.setAllow(p.getString("url", "")!!, p.getString("token", "")!!, v)
                main.post { paintButtons(now) }
            } catch (e: Exception) {
                main.post { tvInfo.text = "toggle failed: ${e.message}" }
            }
        }
    }

    private fun zarStr(v: Double, signed: Boolean = false): String {
        val s = String.format(Locale.US, "%,.2f", Math.abs(v))
        val sign = if (v < 0) "-" else if (signed) "+" else ""
        return "${sign}R $s"
    }

    private fun poll() {
        if (!running) return
        io.execute {
            val p = Api.prefs(this)
            try {
                if (zar <= 0.0 || System.currentTimeMillis() - zarAt > 30 * 60_000) {
                    zar = Api.usdToZar(); zarAt = System.currentTimeMillis()
                }
                val s = Api.fetchState(p.getString("url", "")!!, p.getString("token", "")!!)
                val toUsd = if (s.currency.equals("USC", true)) 0.01 else 1.0
                val eq = s.equity * toUsd * zar
                val pl = s.profit * toUsd * zar
                main.post {
                    tvEquity.text = zarStr(eq)
                    tvPnl.text = "P/L " + zarStr(pl, true)
                    tvPnl.setTextColor(if (pl < 0) Color.rgb(240, 100, 100) else Color.rgb(90, 210, 130))
                    val offline = s.ageSec < 0 || s.ageSec > 60
                    tvInfo.text = (if (offline) "EA OFFLINE (no trading) | " else "") +
                        "${s.symbol} ${s.positions} open | \$1 = R${String.format(Locale.US, "%.2f", zar)}"
                    paintButtons(s.allow)
                }
            } catch (e: Exception) {
                main.post { tvInfo.text = "no connection: ${e.message}" }
            }
        }
        main.postDelayed({ poll() }, 5000)
    }
}