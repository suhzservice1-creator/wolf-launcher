package com.wolf.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var wasAsleep = false
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { wasAsleep = true }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true     // remembers Sound / Background choices
        web.settings.useWideViewPort = true       // lay the page out 1920 wide, then fit it to the screen
        web.settings.loadWithOverviewMode = true
        web.setBackgroundColor(0xFF04070D.toInt())
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")
        setContentView(web)
        web.requestFocus()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenOff) } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        web.evaluateJavascript("window.refresh && refresh()", null)
        if (wasAsleep) {                       // TV / box just woke up: play the start-up screen
            wasAsleep = false
            web.evaluateJavascript("window.showSplash && showSplash()", null)
        }
    }

    // Send the remote's Back button to the page (closes the settings popup) instead of ignoring it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            web.evaluateJavascript("window.onBack && onBack()", null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // A home screen should never close when Back is pressed.
    @Deprecated("Home screen ignores Back")
    override fun onBackPressed() {}

    private fun listApps(): JSONArray {
        val pm = packageManager
        val pinned = listOf("clipbox", "tizen", "arvio")
        val skipPkgs = setOf(packageName, "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher")
        val seen = LinkedHashMap<String, JSONObject>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                val pkg = ri.activityInfo.packageName
                val label = ri.loadLabel(pm).toString()
                if (pkg in skipPkgs || seen.containsKey(pkg)) continue
                seen[pkg] = JSONObject().put("pkg", pkg).put("label", label).put("icon", iconB64(ri.loadIcon(pm)))
            }
        }
        val out = JSONArray()
        fun rank(l: String): Int {
            val i = pinned.indexOfFirst { l.contains(it, ignoreCase = true) }
            return if (i < 0) 99 else i
        }
        seen.values
            .sortedWith(compareBy<JSONObject>({ rank(it.getString("label")) }, { it.getString("label").lowercase() }))
            .forEach { out.put(it) }
        return out
    }

    private fun iconB64(d: Drawable): String {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        val bytes = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    inner class Bridge {
        // Opens an installed app whose name contains the given text (ClipBox, Tizen, Arvio).
        @JavascriptInterface
        fun launch(name: String) = runOnUiThread {
            if (name == "__settings") {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@runOnUiThread
            }
            if (name == "__wifi") {
                startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@runOnUiThread
            }
            val pm = packageManager
            val app = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
                .firstOrNull { it.loadLabel(pm).toString().contains(name, ignoreCase = true) }
            if (app == null) {
                Toast.makeText(this@MainActivity, "$name isn't installed", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setClassName(app.activityInfo.packageName, app.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        @JavascriptInterface
        fun launchPkg(pkg: String) = runOnUiThread {
            val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        // Every other launchable app on the box, with its real icon.
        @JavascriptInterface
        fun apps(cb: String) {
            thread {
                val json = try { listApps().toString() } catch (e: Exception) { "[]" }
                runOnUiThread { web.evaluateJavascript("window.$cb($json)", null) }
            }
        }

        @JavascriptInterface
        fun continueWatching(cb: String) {
            thread {
                val json = try { ClipBox.continueWatching().toString() } catch (e: Exception) { "[]" }
                runOnUiThread { web.evaluateJavascript("window.$cb($json)", null) }
            }
        }
    }
}
