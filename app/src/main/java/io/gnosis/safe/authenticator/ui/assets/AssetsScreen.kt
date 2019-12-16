package io.gnosis.safe.authenticator.ui.assets

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.liveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.TokensRepository
import io.gnosis.safe.authenticator.ui.base.BaseFragment
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.instant.NewInstantTransferActivity
import io.gnosis.safe.authenticator.ui.instant.NewInstantTransferAddressInputActivity
import io.gnosis.safe.authenticator.utils.*
import kotlinx.android.synthetic.main.item_token_balance.view.*
import kotlinx.android.synthetic.main.screen_assets.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class AssetsContract : LoadingViewModel<AssetsContract.State>() {
    abstract fun setup(showOnlyAllowance: Boolean)
    abstract fun loadAssets()

    data class State(
        val loading: Boolean,
        val safe: Solidity.Address?,
        val qrCode: Bitmap?,
        val deviceIdString: String?,
        val assets: List<TokenBalance>?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class TokenBalance(
        val address: Solidity.Address,
        val balance: BigInteger,
        val info: TokensRepository.TokenInfo?
    )
}

@ExperimentalCoroutinesApi
class AssetsViewModel(
    private val safeRepository: SafeRepository,
    private val tokensRepository: TokensRepository
) : AssetsContract() {

    override val state = liveData {
        loadAssets()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    private var showOnlyAllowance: Boolean = false

    override fun setup(showOnlyAllowance: Boolean) {
        this.showOnlyAllowance = showOnlyAllowance
    }

    override fun loadAssets() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val safeTokens = safeRepository.loadTokenBalances(safe)
            val balances = if (showOnlyAllowance) {
                val allowances = safeRepository.loadAllowances(safe)
                safeTokens.mapNotNull { (address, balance) ->
                    val allowance = allowances.find { it.token == address } ?: return@mapNotNull null
                    val remaining = allowance.amount - allowance.spent
                    if (remaining <= BigInteger.ZERO) return@mapNotNull null
                    address to balance.min(remaining)
                }
            } else {
                safeTokens
            }
            val balancesWithTokenInfo = balances.map { (address, balance) ->
                val info = nullOnThrow { tokensRepository.loadTokenInfo(address) }
                TokenBalance(address, balance, info)
            }
            val deviceIdString = safeRepository.loadDeviceId().asEthereumAddressChecksumString()
            val qrCode =
                if (balancesWithTokenInfo.isEmpty())
                    deviceIdString.generateQrCode(512, 512)
                else null
            updateState { copy(loading = false, safe = safe, assets = balancesWithTokenInfo, qrCode = qrCode, deviceIdString = deviceIdString) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null, null, null, null, null)

}

@ExperimentalCoroutinesApi
class AssetsScreen : BaseFragment<AssetsContract.State, AssetsContract>() {
    override val viewModel: AssetsContract by viewModel()
    private val picasso: Picasso by inject()
    private lateinit var adapter: BalancesAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var showOnlyAllowance: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.screen_assets, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showOnlyAllowance = arguments?.getBoolean(ARGUMENT_SHOW_ALLOWANCES, false) ?: false
        viewModel.setup(showOnlyAllowance)
        adapter = BalancesAdapter()
        layoutManager = LinearLayoutManager(context)
        assets_list.adapter = adapter
        assets_list.layoutManager = layoutManager
        assets_refresh.setOnRefreshListener {
            viewModel.loadAssets()
        }
        assets_empty_title.text = getString(if (showOnlyAllowance) R.string.no_allowances_title else R.string.no_balances_title)
        assets_empty_message.text = getString(if (showOnlyAllowance) R.string.no_allowances_message else R.string.no_balances_message)
    }

    override fun updateState(state: AssetsContract.State) {
        assets_refresh.isRefreshing = state.loading
        adapter.submitList(state.assets)
        assets_list.isVisible = !state.assets.isNullOrEmpty()
        assets_empty_views.isVisible = state.assets?.isEmpty() == true
        assets_qr_views.isVisible = state.qrCode != null
        assets_device_qr.setImageBitmap(state.qrCode)
        if (state.deviceIdString != null) {
            assets_copy_btn.setOnClickListener {
                context?.apply {
                    copyToClipboard("Device Id", state.deviceIdString) {
                        Toast.makeText(this, "Copied device id to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            assets_copy_btn.setOnClickListener(null)
        }
    }

    inner class BalancesAdapter : ListAdapter<AssetsContract.TokenBalance, ViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_token_balance, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: AssetsContract.TokenBalance) {
            if (showOnlyAllowance) {
                itemView.setOnClickListener {
                    startActivity(NewInstantTransferAddressInputActivity.createIntent(context!!, item.address))
                }
            }
            itemView.token_balance_token.text = item.info?.symbol ?: item.address.asEthereumAddressChecksumString().substring(0, 6)
            itemView.token_balance_amount.text = item.balance.shiftedString(item.info?.decimals ?: 0)
            itemView.token_balance_icon.setTransactionIcon(picasso, item.info?.icon)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AssetsContract.TokenBalance>() {
        override fun areItemsTheSame(oldItem: AssetsContract.TokenBalance, newItem: AssetsContract.TokenBalance) =
            oldItem.address == newItem.address

        override fun areContentsTheSame(oldItem: AssetsContract.TokenBalance, newItem: AssetsContract.TokenBalance) =
            oldItem == newItem

    }

    companion object {
        private const val ARGUMENT_SHOW_ALLOWANCES = "argument.boolean.show_allowances"
        fun newInstance(showOnlyAllowance: Boolean = false) = AssetsScreen().apply {
            arguments = Bundle().apply {
                putBoolean(ARGUMENT_SHOW_ALLOWANCES, showOnlyAllowance)
            }
        }
    }

}
