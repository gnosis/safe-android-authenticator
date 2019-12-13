package io.gnosis.safe.authenticator.ui.intro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.postDelayed
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import io.gnosis.safe.authenticator.R
import kotlinx.android.synthetic.main.screen_intro.*

class IntroActivity : AppCompatActivity() {

    private val viewPagerTimer = object: CountDownTimer(PAGE_AUTO_SCROLL_TIME_MS, PAGE_AUTO_SCROLL_TIME_MS) {
        override fun onFinish() {
            intro_view_pager.currentItem = (intro_view_pager.currentItem + 1) % layouts.size
            start()
        }

        override fun onTick(p0: Long) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_intro)
        intro_view_pager.adapter = pagerAdapter
        intro_pager_indicator.setViewPager(intro_view_pager)
        intro_get_started_btn.setOnClickListener {
            startActivity(ConnectSafeActivity.createIntent(this))
        }
        intro_view_pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                when(state) {
                    ViewPager.SCROLL_STATE_IDLE -> viewPagerTimer.start()
                    else -> viewPagerTimer.cancel()
                }
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {}

        })
    }

    override fun onStart() {
        super.onStart()
        viewPagerTimer.start()
    }

    override fun onPause() {
        viewPagerTimer.cancel()
        super.onPause()
    }

    private val layouts =
        listOf(
            R.layout.screen_intro_slider_first,
            R.layout.screen_intro_slider_second,
            R.layout.screen_intro_slider_third
        )

    private val pagerAdapter = object : PagerAdapter() {

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val id = layouts[position]
            val layout = LayoutInflater.from(container.context).inflate(id, container, false) as ViewGroup
            container.addView(layout)
            return layout
        }

        override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
            (any as? View)?.let {
                container.removeView(it)
            }
        }

        override fun isViewFromObject(view: View, any: Any) = view == any

        override fun getCount(): Int = layouts.size
    }

    companion object {
        private const val PAGE_AUTO_SCROLL_TIME_MS = 3000L
        fun createIntent(context: Context) = Intent(context, IntroActivity::class.java)
    }
}