package io.gnosis.safe.authenticator.ui.base

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import io.gnosis.safe.authenticator.utils.ExceptionUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class BaseViewModel<T : BaseViewModel.State>(
    protected val context: Context
) : ViewModel() {

    protected abstract fun initialState(): T

    interface State {
        var viewAction: ViewAction?
    }

    interface ViewAction

    data class ShowToast(val message: String) : ViewAction

    @Suppress("LeakingThis")
    protected val stateChannel = ConflatedBroadcastChannel(initialState())

    val state: LiveData<T> = liveData {
        onStart()
        for (state in stateChannel.openSubscription()) emit(state)
    }

    open fun onStart() {}

    protected val coroutineErrorHandler by lazy {
        CoroutineExceptionHandler { _, e ->
            Timber.e(e)
            viewModelScope.launch { updateState(true) { viewAction = ShowToast(ExceptionUtils.extractMessage(context, e)!!); this } }
        }
    }

    protected fun currentState(): T = stateChannel.value

    protected suspend fun updateState(forceViewAction: Boolean = false, update: T.() -> T) {
        try {
            val currentState = currentState()
            val nextState = currentState.run(update)
            // Reset view action if the same
            if (!forceViewAction && nextState.viewAction === currentState.viewAction) nextState.viewAction = null
            stateChannel.send(nextState)
        } catch (e: Exception) {
            // Could not submit update
            Timber.e(e)
        }
    }

    protected fun safeLaunch(errorHandler: CoroutineExceptionHandler = coroutineErrorHandler, block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch(Dispatchers.IO + errorHandler, block = block)

    override fun onCleared() {
        super.onCleared()
        stateChannel.close()
    }
}
