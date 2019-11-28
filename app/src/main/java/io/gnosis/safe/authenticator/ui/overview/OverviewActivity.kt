package io.gnosis.safe.authenticator.ui.overview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.ui.assets.AssetsScreen
import io.gnosis.safe.authenticator.ui.instant.InstantTransferListScreen
import io.gnosis.safe.authenticator.ui.settings.SettingsActivity
import io.gnosis.safe.authenticator.ui.transactions.TransactionsScreen
import kotlinx.android.synthetic.main.screen_overview.*


class OverviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_overview)
        overview_pager.adapter = MyPagerAdapter(supportFragmentManager)
        overview_pager_indicator.setViewPager(overview_pager)
        overview_pager.currentItem = 1
        overview_pager.offscreenPageLimit = 4
        overview_settings_btn.setOnClickListener {
            startActivity(SettingsActivity.createIntent(this))
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, OverviewActivity::class.java)
    }
}

class MyPagerAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    // Returns total number of pages
    override fun getCount(): Int {
        return NUM_ITEMS
    }

    // Returns the fragment to display for that page
    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> InstantTransferListScreen.newInstance()
            1 -> AssetsScreen.newInstance(true)
            2 -> AssetsScreen.newInstance()
            3 -> TransactionsScreen.newInstance()
            else -> throw IllegalArgumentException("unknown position")
        }
    }

    // Returns the page title for the top indicator
    override fun getPageTitle(position: Int): CharSequence? {
        return when (position) {
            0 -> "Instant Transfers"
            1 -> "Allowances"
            2 -> "Assets"
            3 -> "Transactions"
            else -> throw IllegalArgumentException("unknown position")
        }
    }

    companion object {
        private const val NUM_ITEMS = 4
    }

}
