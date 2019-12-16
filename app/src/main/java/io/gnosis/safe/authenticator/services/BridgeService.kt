package io.gnosis.safe.authenticator.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.WalletConnectRepository
import io.gnosis.safe.authenticator.ui.walletconnect.WalletConnectStatusActivity
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import kotlin.coroutines.CoroutineContext


class BridgeService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()

    private val localNotificationManager: LocalNotificationManager by inject()
    private val walletConnectRepository: WalletConnectRepository by inject()

    private var enabled = false
    private var observingJob: Job? = null
    private var checkTxJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        localNotificationManager.createNotificationChannel(
            NOTIFICATION_CHANNEL,
            getString(R.string.channel_name_wallet_connect_sessions),
            getString(R.string.channel_description_wallet_connect_sessions),
            0)
    }

    override fun onDestroy() {
        super.onDestroy()
        observingJob?.cancel()
        checkTxJob?.cancel()
    }

    private fun checkEnabled() {
        if (!enabled) {
            enable()
            checkTxJob = launch {
                while(true) {
                    delay(15000)
                    walletConnectRepository.checkWatchedSafeTransactions()
                }
            }
            observingJob = launch {
                for (event in walletConnectRepository.stateChannel()) {
                    walletConnectRepository.currentSession() ?: run {
                        disable()
                    }
                }
            }
        }
    }

    private fun enable() {
        enabled = true
        // Start foreground service.
        startForeground(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val intent = WalletConnectStatusActivity.createIntent(this)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        val message = "Click to view WalletConnect connection status"
        return localNotificationManager.builder(
            "Connected to WalletConnect",
            message,
            pendingIntent,
            NOTIFICATION_CHANNEL,
            NotificationCompat.CATEGORY_SERVICE,
            NotificationCompat.PRIORITY_MIN
        ).build()
    }

    private fun disable() {
        enabled = false
        stopForeground(true)
        localNotificationManager.hide(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkEnabled()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder(this)

    class LocalBinder(val service: BridgeService) : Binder()

    companion object {
        private const val NOTIFICATION_ID = 3141
        private const val NOTIFICATION_CHANNEL = "wallet_connect_notification_channel"
    }

}
