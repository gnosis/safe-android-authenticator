package io.gnosis.safe.authenticator.ui.transactions

import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
abstract class TransactionsContract: BaseViewModel<TransactionsContract.State>() {
    data class State(override var viewAction: ViewAction?): BaseViewModel.State
}

@ExperimentalCoroutinesApi
class TransactionsActivity: BaseActivity<TransactionsContract.State, TransactionsContract>() {
    override val viewModel: TransactionsContract by viewModel()

    override fun updateState(state: TransactionsContract.State) {
    }

}