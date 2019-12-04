package io.gnosis.safe.authenticator.utils

import android.widget.ImageView
import androidx.core.view.setPadding
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.R
import pm.gnosis.svalinn.common.utils.getColorCompat

fun ImageView.setTransactionIcon(picasso: Picasso, icon: String?) {
    setPadding(0)
    background = null
    setImageDrawable(null)
    colorFilter = null
    when {
        icon == "local::ethereum" -> {
            setPadding(context.resources.getDimension(R.dimen.icon_padding).toInt())
            setBackgroundResource(R.drawable.circle_background)
            setImageResource(R.drawable.ic_ethereum_logo)
        }
        icon == "local::settings" -> {
            setColorFilter(context.getColorCompat(R.color.white))
            setPadding(context.resources.getDimension(R.dimen.icon_padding).toInt())
            setBackgroundResource(R.drawable.circle_background)
            setImageResource(R.drawable.ic_settings_24dp)
        }
        icon?.startsWith("local::") == true -> {
        }
        !icon.isNullOrBlank() ->
            picasso.load(icon).transform(CircleTransformation).into(this)
    }
}
    
