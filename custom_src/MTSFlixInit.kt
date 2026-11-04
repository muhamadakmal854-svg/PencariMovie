package com.mts.mtsflix

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * PencariMovie App Initializer
 * Call MTSFlixInit.initialize(context) from LicenseCheckActivity / CloudStreamApp
 */
object MTSFlixInit {

    private const val TAG = "PencariMovie:Init"
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        // Initialize file logging first
        MTSFlixLogger.init(context)

        MTSFlixLogger.log("SYSTEM", "=======================================")
        MTSFlixLogger.log("SYSTEM", "   PENCARIMOVIE Initializing...")
        MTSFlixLogger.log("SYSTEM", "=======================================")

        // Mark CloudStream setup as complete so MainActivity goes straight to Home
        try {
            markSetupComplete(context)
            MTSFlixLogger.log("SYSTEM", "✅ CloudStream setup marked as complete")
        } catch (e: Exception) {
            MTSFlixLogger.log("SYSTEM", "Setup mark error: ${e.message}")
        }

        // Auto-start PencariMovie Local Rust Streaming Server on Port 8088
        try {
            com.mts.pencarimovie.PencariMovieLocalServerService.start(context)
            MTSFlixLogger.log("SYSTEM", "✅ PencariMovie Local Streaming Server (Port 8088) started")
        } catch (e: Exception) {
            try {
                com.mts.pencarimovie.PencariMovieServerManager.startServerAsync(context)
                MTSFlixLogger.log("SYSTEM", "✅ PencariMovie Server started via ServerManager fallback")
            } catch (err: Exception) {
                MTSFlixLogger.log("SYSTEM", "Server start error: ${err.message}")
            }
        }

        MTSFlixLogger.log("SYSTEM", "✅ PENCARIMOVIE initialized successfully")
    }

    private fun markSetupComplete(context: Context) {
        val key = "HAS_DONE_SETUP"
        try {
            context.getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
                .edit().putString(key, "true").apply()
        } catch (e: Exception) {
            Log.w("PencariMovie", "markSetupComplete rebuild_preference: ${e.message}")
        }
        try {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(key, "true").apply()
        } catch (e: Exception) {
            Log.w("PencariMovie", "markSetupComplete defaultPrefs: ${e.message}")
        }
        try {
            com.lagradost.cloudstream3.CloudStreamApp.setKey(key, true)
        } catch (e: Exception) {
            Log.w("PencariMovie", "markSetupComplete CloudStreamApp.setKey: ${e.message}")
        }
    }

    fun isInitialized() = initialized
}
