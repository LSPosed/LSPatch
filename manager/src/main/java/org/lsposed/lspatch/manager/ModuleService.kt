package org.lsposed.lspatch.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.lsposed.lspatch.config.Configs
import org.lsposed.lspatch.share.Constants


class ModuleService : Service() {

    companion object {
        private const val TAG = "ModuleService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY
        if (Configs.keepAlive == Configs.KeepAlive.FOREGROUND) {
            val channel = NotificationChannel(Constants.MANAGER_PACKAGE_NAME, TAG, NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            startForeground(1, NotificationCompat.Builder(this, Constants.MANAGER_PACKAGE_NAME).build())
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        val packageName = intent.getStringExtra("packageName") ?: return null
        // TODO: Authentication
        Log.i(TAG, "$packageName requests binder")
        return ManagerService.asBinder()
    }
}
