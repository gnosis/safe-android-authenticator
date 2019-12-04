package io.gnosis.safe.authenticator.ui.overview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.gnosis.safe.authenticator.R
import kotlinx.android.synthetic.main.screen_main.*
import pm.gnosis.svalinn.common.utils.transaction


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_main)
        loadFragment(AssetsOverviewScreen.newInstance())
        main_navigation.setOnNavigationItemSelectedListener {
            loadFragment(when (it.itemId) {
                R.id.navigation_assets -> AssetsOverviewScreen.newInstance()
                R.id.navigation_transactions -> TransactionsOverviewScreen.newInstance()
                else -> throw IllegalStateException("Unknown menu item")
            })
            true
        }
        main_navigation.setOnNavigationItemReselectedListener {  }
    }


    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.transaction {
            replace(R.id.main_container, fragment)
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, MainActivity::class.java)
    }
}
