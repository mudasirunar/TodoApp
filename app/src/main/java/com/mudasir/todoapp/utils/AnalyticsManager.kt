package com.mudasir.todoapp.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        firebaseAnalytics?.logEvent(eventName, params)
    }

    // Pre-defined events
    fun logAddNewTaskGroup() {
        logEvent("add_new_task_group")
    }

    fun logOpenSettings() {
        logEvent("open_settings")
    }

    fun logSettingsChanged(settingName: String, newValue: String) {
        val bundle = Bundle().apply {
            putString("setting_name", settingName)
            putString("new_value", newValue)
        }
        logEvent("settings_changed", bundle)
    }

    fun logImportData(success: Boolean, tasksImported: Int = 0) {
        val bundle = Bundle().apply {
            putBoolean("success", success)
            putInt("tasks_imported", tasksImported)
        }
        logEvent("import_data", bundle)
    }

    fun logExportData(success: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("success", success)
        }
        logEvent("export_data", bundle)
    }

    fun logGoogleLogin(success: Boolean, isNewUser: Boolean = false) {
        val bundle = Bundle().apply {
            putBoolean("success", success)
            putBoolean("is_new_user", isNewUser)
        }
        logEvent("google_login", bundle)
    }

    fun logPdfAction(action: String, groupId: String) {
        // action: "preview", "download", "share"
        val bundle = Bundle().apply {
            putString("action", action)
            putString("group_id", groupId)
        }
        logEvent("pdf_action", bundle)
    }
}