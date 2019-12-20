package io.gnosis.safe.authenticator.utils

import android.content.*
import android.net.Uri
import android.provider.Browser
import timber.log.Timber

fun Context.copyToClipboard(label: String, text: String, onCopy: (String) -> Unit = {}) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    onCopy(text)
}

fun Context.createUrlIntent(url: String): Intent {
    val uri = Uri.parse(url)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    return intent.putExtra(Browser.EXTRA_APPLICATION_ID, packageName)
}
