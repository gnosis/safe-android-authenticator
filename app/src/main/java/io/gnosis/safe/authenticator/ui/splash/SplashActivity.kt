package io.gnosis.safe.authenticator.ui.splash

import android.content.Context
import android.content.Intent
import androidx.lifecycle.liveData
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.intro.ConnectSafeActivity
import io.gnosis.safe.authenticator.ui.intro.IntroActivity
import io.gnosis.safe.authenticator.ui.overview.MainActivity
import io.gnosis.safe.authenticator.utils.nullOnThrow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
abstract class SplashContract : BaseViewModel<SplashContract.State>() {
    data class State(
        val next: Intent?,
        override var viewAction: ViewAction?
    ) :
        BaseViewModel.State
}

@ExperimentalCoroutinesApi
class SplashViewModel(
    private val context: Context,
    private val safeRepository: SafeRepository
) : SplashContract() {

    override val state = liveData {
        checkState()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    private fun checkState() {
        safeLaunch {
            val next = nullOnThrow { safeRepository.loadSafeAddress() }?.let {
                MainActivity.createIntent(context)
            } ?: IntroActivity.createIntent(context)
            updateState { copy(next = next) }
        }
    }

    override fun initialState() = State(null, null)

}

@ExperimentalCoroutinesApi
class SplashActivity : BaseActivity<SplashContract.State, SplashContract>() {
    override val viewModel: SplashContract by viewModel()

    override fun updateState(state: SplashContract.State) {
        state.next?.let {
            startActivity(it)
            finish()
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, SplashActivity::class.java)
    }

}
