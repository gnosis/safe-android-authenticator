package io.gnosis.safe.authenticator.ui.overview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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


class AssetsOverviewScreen : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.screen_overview, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        overview_title.text = getString(R.string.assets)
        overview_pager.adapter = AssetsOverviewAdapter(childFragmentManager)
        overview_pager_indicator.setViewPager(overview_pager)
        overview_pager.offscreenPageLimit = 4
        overview_settings_btn.setOnClickListener {
            startActivity(SettingsActivity.createIntent(context!!))
        }
    }

    companion object {
        fun newInstance() = AssetsOverviewScreen()
    }
}

private class AssetsOverviewAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    // Returns total number of pages
    override fun getCount(): Int {
        return NUM_ITEMS
    }

    // Returns the fragment to display for that page
    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> AssetsScreen.newInstance()
            1 -> AssetsScreen.newInstance(true)
            else -> throw IllegalArgumentException("unknown position")
        }
    }

    // Returns the page title for the top indicator
    override fun getPageTitle(position: Int): CharSequence? {
        return when (position) {
            0 -> "Safe"
            1 -> "Allowances"
            else -> throw IllegalArgumentException("unknown position")
        }
    }

    companion object {
        private const val NUM_ITEMS = 2
    }

}
