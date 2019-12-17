package io.gnosis.safe.authenticator.utils

import android.content.Context
import io.gnosis.safe.authenticator.R
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

object ExceptionUtils {

    fun rethrowWithMessage(context: Context, e: Throwable, errorCodeMapping: Map<Int, Int>? = null) {
        extractMessage(context, e, errorCodeMapping, null)?.let {
            throw IllegalStateException(it)
        } ?: throw e
    }

    fun extractMessage(
        context: Context,
        e: Throwable,
        errorCodeMapping: Map<Int, Int>? = null,
        defaultMessage: String? = context.getString(R.string.unknown_error)
    ) =
        when {
            e is HttpException ->
                errorCodeMapping?.get(e.code())?.let { context.getString(it) }
                    ?: context.getString(R.string.unknown_server_error)

            e is SSLHandshakeException || e.cause is SSLHandshakeException ->
                context.getString(R.string.error_ssl_handshake)

            e is UnknownHostException || e is SocketTimeoutException || e is ConnectException ->
                context.getString(R.string.error_check_internet_connection)

            else ->
                e.message ?: defaultMessage
        }
}
