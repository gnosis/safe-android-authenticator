package io.gnosis.safe.authenticator.ui.base

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer

abstract class BaseFragment<S: BaseViewModel.State, T: BaseViewModel<S>>: Fragment() {

    protected abstract val viewModel: T

    abstract fun updateState(state: S)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.state.observe(this, Observer {
            updateState(it)
            it.viewAction?.let { update -> performAction(update) }
        })
    }

    protected open fun performAction(viewAction: BaseViewModel.ViewAction) {
        when (viewAction) {
            is BaseViewModel.ShowToast -> {
                Toast.makeText(context, viewAction.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
