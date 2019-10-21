package io.gnosis.safe.authenticator.ui.base

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
abstract class BaseActivity<S: BaseViewModel.State, T: BaseViewModel<S>>: AppCompatActivity() {

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
                Toast.makeText(this, viewAction.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}