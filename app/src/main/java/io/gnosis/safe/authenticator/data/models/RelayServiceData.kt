package io.gnosis.safe.authenticator.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import pm.gnosis.model.Solidity

@JsonClass(generateAdapter = true)
data class ServiceTokenInfo(
    @Json(name = "address") val address: Solidity.Address,
    @Json(name = "decimals") val decimals: Int,
    @Json(name = "symbol") val symbol: String,
    @Json(name = "name") val name: String,
    @Json(name = "logoUri") val logoUri: String?
)