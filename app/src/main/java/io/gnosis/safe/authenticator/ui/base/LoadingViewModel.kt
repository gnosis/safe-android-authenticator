package io.gnosis.safe.authenticator.ui.base

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class LoadingViewModel<T : BaseViewModel.State>(context: Context) : BaseViewModel<T>(context) {

    private val loadingErrorHandler = CoroutineExceptionHandler { _, e ->
        Timber.e(e)
        viewModelScope.launch {
            updateState(true) {
                onLoadingError(this, e).apply {
                    viewAction = ShowToast(e.message ?: "An error occurred");
                }
            }
        }
    }

    abstract fun onLoadingError(state: T, e: Throwable): T

    protected fun loadingLaunch(block: suspend CoroutineScope.() -> Unit) =
        safeLaunch(loadingErrorHandler, block)
}
