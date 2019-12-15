package io.gnosis.safe.authenticator.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import io.gnosis.safe.authenticator.R

class AndroidLocalNotificationManager(
    private val context: Context
) : LocalNotificationManager {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    override fun createNotificationChannel(channelId: String, name: String, description: String, importance: Int) {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val channel = NotificationChannel(channelId, name, importance)
        channel.description = description

        channel.enableLights(true)
        channel.lightColor = LIGHT_COLOR

        channel.enableVibration(true)
        channel.vibrationPattern = VIBRATE_PATTERN

        notificationManager?.createNotificationChannel(channel)
    }

    override fun hide(id: Int) {
        notificationManager?.cancel(id)
    }

    override fun builder(title: String, message: String, intent: PendingIntent, channelId: String, category: String?, priority: Int) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setVibrate(VIBRATE_PATTERN)
            .setLights(LIGHT_COLOR, 100, 100)
            .setDefaults(Notification.DEFAULT_ALL)
            .setCategory(category)
            .setPriority(priority)
            .setContentIntent(intent)!!


    override fun show(id: Int, title: String, message: String, intent: Intent, channelId: String) =
        show(id, title, message, PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT), channelId)

    override fun show(id: Int, title: String, message: String, intent: PendingIntent, channelId: String) {
        val builder =
            builder(title, message, intent, channelId)
        show(id, builder.build())
    }

    override fun show(id: Int, notification: Notification) {
        notificationManager?.notify(id, notification)
    }

    companion object {
        private val VIBRATE_PATTERN = longArrayOf(0, 100, 50, 100)
        private const val LIGHT_COLOR = Color.MAGENTA
        private const val CHANNEL_ID = "channel_signing_requests"
    }
}

interface LocalNotificationManager {
    fun hide(id: Int)

    fun createNotificationChannel(channelId: String, name: String, description: String, importance: Int = 4)

    fun builder(
        title: String,
        message: String,
        intent: PendingIntent,
        channelId: String,
        category: String? = null,
        priority: Int = NotificationCompat.PRIORITY_HIGH
    ): NotificationCompat.Builder

    fun show(id: Int, title: String, message: String, intent: Intent, channelId: String)

    fun show(id: Int, title: String, message: String, intent: PendingIntent, channelId: String)

    fun show(id: Int, notification: Notification)
}
