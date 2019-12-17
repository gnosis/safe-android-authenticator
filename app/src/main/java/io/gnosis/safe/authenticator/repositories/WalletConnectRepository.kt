package io.gnosis.safe.authenticator.repositories

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.services.BridgeService
import io.gnosis.safe.authenticator.services.LocalNotificationManager
import io.gnosis.safe.authenticator.ui.walletconnect.WalletConnectStatusActivity
import io.gnosis.safe.authenticator.utils.shortChecksumString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.walletconnect.Session
import org.walletconnect.impls.WCSession
import org.walletconnect.impls.WCSessionStore
import pm.gnosis.svalinn.common.utils.edit
import pm.gnosis.utils.*
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

interface WalletConnectRepository {
    fun init()
    fun watchSafeTransaction(requestId: Long, safeTxHash: String)
    fun checkWatchedSafeTransactions()
    fun rejectRequest(requestId: Long)
    fun createSession(uri: String)
    fun currentSession(): DappSession?
    fun stateChannel(): ReceiveChannel<Session.Status>
    fun closeSession()

    data class DappSession(
        val url: String?,
        val name: String?,
        val icon: String?,
        val active: Boolean
    )
}

class WalletConnectRepositoryImpl(
    private val context: Context,
    private val notificationManager: LocalNotificationManager,
    private val picasso: Picasso,
    private val safeRepository: SafeRepository,
    private val sessionStore: WCSessionStore,
    private val sessionBuilder: SessionBuilder
) : WalletConnectRepository, CoroutineScope, Session.Callback {

    private val observedSafeTxPrefs = context.getSharedPreferences(PREFERENCES_OBSERVED_SAFE_TX, Context.MODE_PRIVATE)

    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()

    private var session: Session? = null
    private val sessionLock = Any()

    private val stateChannel = BroadcastChannel<Session.Status>(Channel.CONFLATED)

    override fun stateChannel() = stateChannel.openSubscription()

    override fun init() {
        notificationManager.createNotificationChannel(
            CHANNEL_WALLET_CONNECT_REQUESTS,
            context.getString(R.string.channel_name_wallet_connect_requests),
            context.getString(R.string.channel_description_wallet_connect_requests)
        )

        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    activateSession()
                }
            })
    }

    private fun activateSession() {
        launch {
            session?.let { return@launch } // We already have an active session
            val sessionData = sessionStore.list().firstOrNull() ?: return@launch
            createSession(sessionData.config)
        }
    }

    override fun currentSession(): WalletConnectRepository.DappSession? =
        sessionStore.list().firstOrNull()?.let {
            val meta = it.peerData?.meta
            WalletConnectRepository.DappSession(meta?.url, meta?.name, meta?.icons?.firstOrNull(), session != null)
        }

    override fun createSession(uri: String) {
        val config = Session.Config.fromWCUri(uri)
        closeSession()
        createSession(config)
    }

    override fun closeSession() {
        synchronized(sessionLock) {
            cleanupSession()?.kill()
            launch { stateChannel.send(Session.Status.Closed) }
        }
    }

    private fun cleanupSession(): Session? {
        synchronized(sessionLock) {
            val currentSession = session
            session = null
            currentSession?.removeCallback(this@WalletConnectRepositoryImpl)
            // Clear all existing session (should only be one)
            sessionStore.list().forEach {
                sessionStore.remove(it.config.handshakeTopic)
            }
            observedSafeTxPrefs.edit { clear() }
            return currentSession
        }
    }

    private fun createSession(config: Session.Config) {
        synchronized(sessionLock) {
            session = sessionBuilder.build(config, Session.PeerMeta(name = context.getString(R.string.app_name))).apply {
                init()
                addCallback(this@WalletConnectRepositoryImpl)
            }
            startBridgeService()
        }
    }

    private fun startBridgeService() {
        context.startService(Intent(context, BridgeService::class.java))
    }

    override fun onMethodCall(call: Session.MethodCall) {
        when (call) {
            is Session.MethodCall.SessionRequest -> {
                launch {
                    val safe = safeRepository.loadSafeAddress()
                    session?.approve(listOf(safe.asEthereumAddressString()), BuildConfig.BLOCKCHAIN_CHAIN_ID)
                }
            }
            is Session.MethodCall.SendTransaction -> handleSendTransaction(call)
            is Session.MethodCall.Custom, is Session.MethodCall.SignMessage -> {
                session?.rejectRequest(call.id(), 0, "Unsupported call")
            }
            is Session.MethodCall.SessionUpdate, is Session.MethodCall.Response -> {
            }
        }
    }


    private fun handleSendTransaction(
        call: Session.MethodCall.SendTransaction
    ) {
        launch {
            try {
                val safe = safeRepository.loadSafeAddress()
                if (call.from.toLowerCase() != safe.asEthereumAddressString()) throw IllegalArgumentException("Invalid from provided")
                val to = call.to.asEthereumAddress() ?: throw IllegalArgumentException("Invalid to provided")
                val value = (if (call.value.startsWith("0x")) call.value.hexAsBigIntegerOrNull() else call.value.decimalAsBigIntegerOrNull())
                    ?: throw IllegalArgumentException("Invalid value provided")
                val data =
                    nullOnThrow { call.data.hexStringToByteArray().toHex().addHexPrefix() } ?: throw IllegalArgumentException("Invalid data provided")
                val intent = WalletConnectStatusActivity.createIntent(context, safe, to, value, data, call.id())
                val peerMeta = session?.peerMeta()
                val icon = peerMeta?.icons?.firstOrNull()?.let { nullOnThrow { picasso.load(it).get() } }
                val notification = notificationManager.builder(
                    peerMeta?.name ?: context.getString(R.string.unknown_dapp),
                    context.getString(R.string.notification_new_transaction_request),
                    PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT),
                    CHANNEL_WALLET_CONNECT_REQUESTS
                )
                    .setSubText(safe.shortChecksumString())
                    .setLargeIcon(icon)
                    .build()
                notificationManager.show(
                    call.id().toInt(),
                    notification
                )
            } catch (e: Exception) {
                session?.rejectRequest(call.id, 0, e.message ?: "An error occured")
            }
        }
    }

    override fun onStatus(status: Session.Status) {
        launch {
            stateChannel.send(status)
        }
        when (status) {
            Session.Status.Closed -> cleanupSession()
            is Session.Status.Error -> Timber.e(status.throwable)
        }
    }

    override fun checkWatchedSafeTransactions() {
        launch {
            session ?: return@launch
            observedSafeTxPrefs.all.forEach {(safeTxHash, referenceId) ->
                safeRepository.loadPendingTransaction(safeTxHash).txHash?.let {
                    val s = session ?: return@forEach
                    if (referenceId !is Long) return@forEach
                    s.approveRequest(referenceId, it)
                    observedSafeTxPrefs.edit { remove(safeTxHash) }
                }
            }
        }
    }

    override fun watchSafeTransaction(requestId: Long, safeTxHash: String) {
        observedSafeTxPrefs.edit { putLong(safeTxHash, requestId) }
    }

    override fun rejectRequest(requestId: Long) {
        session?.rejectRequest(requestId, 0, "User rejected request")
    }

    companion object {
        private const val CHANNEL_WALLET_CONNECT_REQUESTS = "channel_wallet_connect_requests"
        private const val PREFERENCES_OBSERVED_SAFE_TX = "preferences_observed_safe_tx"
    }
}

interface SessionBuilder {
    fun build(config: Session.Config, clientMeta: Session.PeerMeta): Session
}

class WCSessionBuilder(
    private val sessionStore: WCSessionStore,
    private val sessionPayloadAdapter: Session.PayloadAdapter,
    private val sessionTransportBuilder: Session.Transport.Builder
) : SessionBuilder {
    override fun build(config: Session.Config, clientMeta: Session.PeerMeta): Session = WCSession(
        config,
        sessionPayloadAdapter,
        sessionStore,
        sessionTransportBuilder,
        clientMeta
    )
}
