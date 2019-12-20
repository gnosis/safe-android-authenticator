package io.gnosis.safe.authenticator.ui.base

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import kotlinx.coroutines.ExperimentalCoroutinesApi

abstract class BaseActivity<S: BaseViewModel.State, T: BaseViewModel<S>>: AppCompatActivity() {

    protected abstract val viewModel: T

    abstract fun updateState(state: S)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.state.observe(this, Observer {
            updateState(it)
            it.viewAction?.let { update -> if (performAction(update)) it.viewAction = null }
        })
    }

    /**
     * @return boolean if the action should be consumed (cannot be use again by other observers afterwards
     */
    protected open fun performAction(viewAction: BaseViewModel.ViewAction): Boolean {
        when (viewAction) {
            is BaseViewModel.ShowToast -> {
                Toast.makeText(this, viewAction.message, Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }
}
