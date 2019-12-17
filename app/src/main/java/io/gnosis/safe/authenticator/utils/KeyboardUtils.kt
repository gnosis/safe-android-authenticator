package io.gnosis.safe.authenticator.utils

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlin.math.roundToInt

private fun Activity.getRootView(): View {
    return findViewById<View>(android.R.id.content)
}
private fun Context.convertDpToPx(dp: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        this.resources.displayMetrics
    )
}

private fun Activity.isKeyboardOpen(): Boolean {
    val displayMetrics = DisplayMetrics()
    windowManager.defaultDisplay.getMetrics(displayMetrics)
    val visibleBounds = Rect()
    val rootView = getRootView()
    rootView.getWindowVisibleDisplayFrame(visibleBounds)
    val heightDiff = displayMetrics.heightPixels - visibleBounds.bottom
    val marginOfError = this.convertDpToPx(48F).roundToInt()
    return heightDiff > marginOfError
}

class KeyboardEventListener(
    private val activity: AppCompatActivity,
    private val callback: (isOpen: Boolean) -> Unit
) : LifecycleObserver {

    private val listener = object : ViewTreeObserver.OnPreDrawListener {
        private var lastState: Boolean = activity.isKeyboardOpen()

        override fun onPreDraw(): Boolean {
            val isOpen = activity.isKeyboardOpen()
            return if (isOpen == lastState) {
                true
            } else {
                dispatchKeyboardEvent(isOpen)
                lastState = isOpen
                false
            }
        }
    }

    init {
        // Dispatch the current state of the keyboard
        dispatchKeyboardEvent(activity.isKeyboardOpen())
        // Make the component lifecycle aware
        activity.lifecycle.addObserver(this)
    }

    private fun dispatchKeyboardEvent(isOpen: Boolean) {
        when {
            isOpen  -> callback(true)
            !isOpen -> callback(false)
        }
    }

    @OnLifecycleEvent(value = Lifecycle.Event.ON_RESUME)
    @CallSuper
    fun onLifecycleResume() {
        registerKeyboardListener()
    }

    @OnLifecycleEvent(value = Lifecycle.Event.ON_PAUSE)
    @CallSuper
    fun onLifecyclePause() {
        unregisterKeyboardListener()
    }

    private fun registerKeyboardListener() {
        activity.getRootView().viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun unregisterKeyboardListener() {
        activity.getRootView().viewTreeObserver.removeOnPreDrawListener(listener)
    }
}
