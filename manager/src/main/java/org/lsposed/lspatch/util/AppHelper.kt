package org.lsposed.lspatch.util

import android.content.Intent
import org.lsposed.lspatch.lspApp

object AppHelper {

    private const val SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS"

    fun getSettingsIntent(packageName: String): Intent? {
        val intentToResolve = Intent(Intent.ACTION_MAIN)
        intentToResolve.addCategory(SETTINGS_CATEGORY)
        intentToResolve.setPackage(packageName)
        lspApp.packageManager.queryIntentActivities(intentToResolve, 0)
        val ris = lspApp.packageManager.queryIntentActivities(intentToResolve, 0)
        if (ris.size <= 0) {
            return getLaunchIntentForPackage(packageName)
        }
        val intent = Intent(intentToResolve)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.setClassName(
            ris[0].activityInfo.packageName,
            ris[0].activityInfo.name
        )
        return intent
    }

    fun getLaunchIntentForPackage(packageName: String): Intent? {
        val intentToResolve = Intent(Intent.ACTION_MAIN)
        intentToResolve.addCategory(Intent.CATEGORY_INFO)
        intentToResolve.setPackage(packageName)
        var ris = lspApp.packageManager.queryIntentActivities(intentToResolve, 0)
        if (ris.size <= 0) {
            intentToResolve.removeCategory(Intent.CATEGORY_INFO)
            intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER)
            intentToResolve.setPackage(packageName)
            ris = lspApp.packageManager.queryIntentActivities(intentToResolve, 0)
        }
        if (ris.size <= 0) {
            return null
        }
        val intent = Intent(intentToResolve)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.setClassName(
            ris[0].activityInfo.packageName,
            ris[0].activityInfo.name
        )
        return intent
    }
}
