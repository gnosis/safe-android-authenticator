package io.gnosis.safe.authenticator.ui.splash

import android.content.Context
import android.content.Intent
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.intro.IntroActivity
import io.gnosis.safe.authenticator.ui.overview.MainActivity
import io.gnosis.safe.authenticator.utils.nullOnThrow
import org.koin.androidx.viewmodel.ext.android.viewModel

abstract class SplashContract(context: Context) : BaseViewModel<SplashContract.State>(context) {
    data class State(
        val next: Intent?,
        override var viewAction: ViewAction?
    ) :
        BaseViewModel.State
}

class SplashViewModel(
    context: Context,
    private val safeRepository: SafeRepository
) : SplashContract(context) {

    override fun onStart() {
        checkState()
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
