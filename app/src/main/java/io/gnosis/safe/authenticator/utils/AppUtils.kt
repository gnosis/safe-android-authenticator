package io.gnosis.safe.authenticator.utils

//Map functions that throw exceptions into optional types
inline fun <T> nullOnThrow(func: () -> T): T? = try {
    func.invoke()
} catch (e: Exception) {
    null
}