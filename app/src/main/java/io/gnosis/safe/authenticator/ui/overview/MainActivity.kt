package io.gnosis.safe.authenticator.ui.overview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.utils.nullOnThrow
import kotlinx.android.synthetic.main.screen_main.*
import pm.gnosis.svalinn.common.utils.transaction

interface OverviewTypeSwitchCallback {
    fun onSwitchType(type: Type)
    fun getCurrentType(): Type

    enum class Type(val id: Int) {
        SAFE(0),
        ALLOWANCE(1)
    }

    companion object {
        fun parseType(value: Int) = when (value) {
            0 -> Type.SAFE
            1 -> Type.ALLOWANCE
            else -> throw IllegalArgumentException()
        }
    }
}

class MainActivity : AppCompatActivity(), OverviewTypeSwitchCallback {

    private var currentOverviewType = OverviewTypeSwitchCallback.Type.SAFE

    override fun getCurrentType() = currentOverviewType

    override fun onSwitchType(type: OverviewTypeSwitchCallback.Type) {
        currentOverviewType = type
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        setOverviewType(intent.getIntExtra(EXTRA_SELECTED_TAB, currentOverviewType.id))
        selectTab(intent.getIntExtra(EXTRA_SELECTED_TAB, 0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_OVERVIEW_TYPE, currentOverviewType.id)
        outState.putInt(EXTRA_SELECTED_TAB, main_navigation.selectedItemId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_main)
        main_navigation.setOnNavigationItemSelectedListener {
            loadFragment(
                when (it.itemId) {
                    R.id.navigation_assets -> AssetsOverviewScreen.newInstance()
                    R.id.navigation_transactions -> TransactionsOverviewScreen.newInstance()
                    else -> throw IllegalStateException("Unknown menu item")
                }
            )
            true
        }
        main_navigation.itemTextAppearanceActive = main_navigation.itemTextAppearanceInactive
        selectTab(savedInstanceState?.getInt(EXTRA_SELECTED_TAB) ?: intent.getIntExtra(EXTRA_SELECTED_TAB, R.id.navigation_assets))
        setOverviewType(savedInstanceState?.getInt(EXTRA_OVERVIEW_TYPE) ?: intent.getIntExtra(EXTRA_OVERVIEW_TYPE, -1))
        main_navigation.setOnNavigationItemReselectedListener { }
    }

    private fun setOverviewType(type: Int) {
        onSwitchType(nullOnThrow { OverviewTypeSwitchCallback.parseType(type) } ?: return)
    }

    private fun selectTab(id: Int) {
        if (id == 0) return
        main_navigation.selectedItemId = id
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.transaction {
            replace(R.id.main_container, fragment)
        }
    }

    companion object {
        private const val EXTRA_SELECTED_TAB = "extra.int.selected_tab"
        private const val EXTRA_OVERVIEW_TYPE = "extra.int.overview_type"
        fun createIntent(context: Context, selectedTab: Int? = null, overviewType: OverviewTypeSwitchCallback.Type? = null) =
            Intent(context, MainActivity::class.java).apply {
                selectedTab?.let { putExtra(EXTRA_SELECTED_TAB, it) }
                overviewType?.let { putExtra(EXTRA_OVERVIEW_TYPE, it.id) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
