package io.gnosis.safe.authenticator.utils

import android.content.Context
import io.gnosis.safe.authenticator.R
import retrofit2.HttpException
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

object ExceptionUtils {

    fun rethrowWithMessage(context: Context, e: Throwable, errorCodeMapping: Map<Int, Int>? = null) {
        when {
            e is HttpException ->
                errorCodeMapping?.get(e.code())?.let { throw IllegalArgumentException(context.getString(it)) }
                    ?: throw IllegalStateException(context.getString(R.string.unknown_server_error))

            e is SSLHandshakeException || e.cause is SSLHandshakeException ->
                throw IllegalStateException(context.getString(R.string.error_ssl_handshake))

            e is UnknownHostException || e is SocketTimeoutException || e is ConnectException ->
                throw IllegalStateException(context.getString(R.string.error_check_internet_connection))

            else -> throw e
        }
    }
}
