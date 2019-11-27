package io.gnosis.safe.authenticator.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import pm.gnosis.model.Solidity
import java.math.BigInteger

@JsonClass(generateAdapter = true)
data class InstantTransferRequest(
    @Json(name = "target") val target: Solidity.Address,
    @Json(name = "amount") val amount: BigInteger,
    @Json(name = "signature") val signature: String
)

@JsonClass(generateAdapter = true)
data class InstantTransferResponse(
    @Json(name = "hash") val hash: String
)
