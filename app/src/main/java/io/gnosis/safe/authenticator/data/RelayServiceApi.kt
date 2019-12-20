package io.gnosis.safe.authenticator.data

import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.data.models.ServiceTokenInfo
import retrofit2.http.GET
import retrofit2.http.Path


interface RelayServiceApi {

    @GET("v1/tokens/{address}/")
    suspend fun tokenInfo(@Path("address") address: String): ServiceTokenInfo

    companion object {
        const val BASE_URL = BuildConfig.RELAY_SERVICE_URL
    }
}
