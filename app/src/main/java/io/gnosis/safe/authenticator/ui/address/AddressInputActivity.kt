package io.gnosis.safe.authenticator.ui.address

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.liveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.AddressRepository
import io.gnosis.safe.authenticator.ui.adapter.*
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.utils.loggedTry
import io.gnosis.safe.authenticator.utils.nullOnThrow
import io.gnosis.safe.authenticator.utils.parseEthereumAddress
import io.gnosis.safe.authenticator.utils.shortChecksumString
import kotlinx.android.synthetic.main.screen_address_input.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.hideSoftKeyboard
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.removeHexPrefix

@ExperimentalCoroutinesApi
abstract class AddressInputContract : LoadingViewModel<AddressInputContract.State>() {
    abstract fun handleInput(input: String, force: Boolean)
    abstract fun selectAddress(address: Solidity.Address)

    data class State(
        val loading: Boolean,
        val results: List<ListEntry>,
        val selectedAddress: Solidity.Address?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State
}

@ExperimentalCoroutinesApi
class AddressInputViewModel(
    private val context: Context,
    private val addressRepository: AddressRepository
) : AddressInputContract() {

    override val state = liveData {
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun selectAddress(address: Solidity.Address) {
        safeLaunch {
            loggedTry { addressRepository.addRecentAddress(address) }
            updateState { copy(selectedAddress = address) }
        }
    }

    private var inputJob: Job? = null
    override fun handleInput(input: String, force: Boolean) {
        if (!force && state.value?.loading == true) return
        inputJob?.cancel()
        inputJob = loadingLaunch {
            loadData(input, 500)
        }
    }

    private suspend fun loadData(input: String, delayMs: Long = 0) {
        updateState { copy(loading = true) }
        if (delayMs > 0) delay(delayMs)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val entries = mutableListOf<ListEntry>()
        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.parseEthereumAddress()?.let {
            if (it.asEthereumAddressString().removeHexPrefix().startsWith(input.toLowerCase().trim().removeHexPrefix())) {
                entries += HeaderEntry("Clipboard")
                entries += AddressEntry(it, it.shortChecksumString(), null)
            }
        }
        if (!input.isBlank()) {
            input.parseEthereumAddress()?.let {
                entries += HeaderEntry("Entered Address")
                entries += AddressEntry(it, it.shortChecksumString(), null)
            }
            entries += HeaderEntry("ENS")
            entries += nullOnThrow { addressRepository.resolveEns(input) }?.let {
                AddressEntry(it, it.shortChecksumString(), null)
            } ?: NoticeEntry("Could not resolve input as ENS entry")
        }
        addressRepository.recentAddresses(input).let { recents ->
            entries.maybeAddWithHeader("Recent Addresses", recents.map { AddressEntry(it, it.shortChecksumString(), null) })
        }
        updateState { copy(results = entries, loading = false) }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, emptyList(), null, null)

}

class AddressInputActivity : BaseActivity<AddressInputContract.State, AddressInputContract>() {
    override val viewModel: AddressInputContract by viewModel()
    private val adapter = AddressAdapter()
    private val layoutManager: RecyclerView.LayoutManager by lazy { LinearLayoutManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_address_input)
        address_input_back_btn.setOnClickListener { onBackPressed() }
        val spinnerOffset = resources.getDimension(R.dimen.address_input_spinner_offset).toInt()
        address_input_refresh.setProgressViewOffset(true, 0, spinnerOffset)
        address_input_refresh.setOnRefreshListener {
            viewModel.handleInput(address_input_search_field.text.toString(), false)
        }
        address_input_search_field.addTextChangedListener {
            viewModel.handleInput(it.toString(), true)
        }
        address_input_list.adapter = adapter
        address_input_list.layoutManager = layoutManager
        address_input_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == SCROLL_STATE_DRAGGING) hideSoftKeyboard()
            }
        })
    }

    override fun updateState(state: AddressInputContract.State) {
        address_input_refresh.isRefreshing = state.loading
        adapter.submitList(state.results)
        state.selectedAddress?.let {
            setResult(Activity.RESULT_OK, Intent().apply { putExtra(RESULT_EXTRA_ADDRESS, it.asEthereumAddressString()) })
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.handleInput(address_input_search_field.text.toString(), false)
        Handler(mainLooper).postDelayed({ address_input_search_field.showKeyboardForView() }, 400)
    }

    inner class AddressAdapter : ListAdapter<ListEntry, ListEntryViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            viewType.typeToAddressViewHolder(parent, ::onSelected)

        private fun onSelected(entry: AddressEntry) {
            viewModel.selectAddress(entry.address)
        }

        override fun getItemViewType(position: Int): Int {
            return getItem(position).type
        }

        override fun onBindViewHolder(holder: ListEntryViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    companion object {
        private const val REQUEST_CODE = 4211
        private const val RESULT_EXTRA_ADDRESS = "extra.string.address_result"

        fun handleResult(
            requestCode: Int, resultCode: Int, data: Intent?, onCancelledResult: (() -> Unit)? = null, onAddressResult: (Solidity.Address) -> Unit
        ): Boolean {
            if (requestCode == REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(RESULT_EXTRA_ADDRESS)) {
                    onAddressResult(data.getStringExtra(RESULT_EXTRA_ADDRESS)!!.asEthereumAddress()!!)
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    onCancelledResult?.invoke()
                }
                return true
            }
            return false
        }

        fun startForResult(activity: Activity) =
            activity.startActivityForResult(createIntent(activity), REQUEST_CODE)

        fun createIntent(context: Context) = Intent(context, AddressInputActivity::class.java)
    }
}
