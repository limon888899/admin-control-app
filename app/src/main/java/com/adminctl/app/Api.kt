package com.adminctl.app

import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Talks to a site's existing /api/admin/* routes. Session cookie is kept in memory. */
object Api {
    init { if (CookieHandler.getDefault() == null) CookieHandler.setDefault(CookieManager()) }

    private fun call(url: String, method: String = "GET", body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000; c.readTimeout = 20000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return code to text
    }

    /** Returns null on success, else an error message. */
    fun login(site: Site, user: String, pass: String): String? = try {
        val body = JSONObject().put("username", user).put("password", pass).toString()
        val (code, text) = call("${site.baseUrl}/api/admin/login", "POST", body)
        when {
            code in 200..299 -> null
            code == 401 -> "Wrong username or password"
            else -> runCatching { JSONObject(text).getString("error") }.getOrDefault("Login failed ($code)")
        }
    } catch (e: Exception) { "Network error: ${e.message}" }

    fun list(site: Site, path: String): Result<List<JSONObject>> = runCatching {
        val (code, text) = call("${site.baseUrl}/api/admin/$path")
        if (code == 401) error("Session expired, log in again")
        if (code !in 200..299) error("Error $code")
        val arr = JSONArray(text)
        List(arr.length()) { arr.getJSONObject(it) }
    }

    fun setPaymentStatus(site: Site, id: String, status: String): String? = try {
        val (code, _) = call("${site.baseUrl}/api/admin/payments/$id", "PATCH", JSONObject().put("status", status).toString())
        if (code in 200..299) null else "Error $code"
    } catch (e: Exception) { e.message }
}
