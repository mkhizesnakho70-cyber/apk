package com.example.tradebot

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    data class Snapshot(
        val allow: Boolean,
        val ageSec: Int,
        val balance: Double,
        val equity: Double,
        val profit: Double,
        val positions: Int,
        val symbol: String,
        val currency: String,
        val signal: String
    )

    fun prefs(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private fun call(base: String, token: String, method: String, path: String, body: String?): JSONObject {
        val conn = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("X-Token", token)
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
        return JSONObject(text)
    }

    fun fetchState(base: String, token: String): Snapshot {
        val j = call(base, token, "GET", "/api/state", null)
        val s = j.optJSONObject("status") ?: JSONObject()
        return Snapshot(
            allow = j.optBoolean("allow", false),
            ageSec = j.optInt("age", -1),
            balance = s.optDouble("balance", 0.0),
            equity = s.optDouble("equity", 0.0),
            profit = s.optDouble("profit", 0.0),
            positions = s.optInt("positions", 0),
            symbol = s.optString("symbol", ""),
            currency = s.optString("currency", "USD"),
            signal = s.optString("signal", "")
        )
    }

    fun setAllow(base: String, token: String, allow: Boolean): Boolean =
        call(base, token, "POST", "/api/allow", """{"allow":$allow}""").optBoolean("allow", false)

    fun usdToZar(): Double {
        val conn = URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(text).getJSONObject("rates").getDouble("ZAR")
    }
}