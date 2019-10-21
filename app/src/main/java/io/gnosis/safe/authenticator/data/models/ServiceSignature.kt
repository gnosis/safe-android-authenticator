package io.gnosis.safe.authenticator.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.gnosis.safe.authenticator.data.adapter.DecimalNumber
import pm.gnosis.crypto.ECDSASignature
import java.math.BigInteger

@JsonClass(generateAdapter = true)
data class ServiceSignature(
    @Json(name = "r") @field:DecimalNumber val r: BigInteger,
    @Json(name = "s") @field:DecimalNumber val s: BigInteger,
    @Json(name = "v") val v: Int
) {
    fun toSignature() = ECDSASignature(r, s).let { it.v = v.toByte() }

    companion object {
        fun fromSignature(signature: ECDSASignature) = ServiceSignature(
            signature.r,
            signature.s,
            signature.v.toInt()
        )
    }
}
