package io.gnosis.safe.authenticator.data

import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.data.models.InstantTransferRequest
import io.gnosis.safe.authenticator.data.models.InstantTransferResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path


interface InstantTransferServiceApi {

    @POST("1/safes/{safe}/delegates/{delegate}/tokens/{token}/submit_instant_transfer")
    suspend fun submitInstantTransfer(
        @Path("safe") safe: String,
        @Path("delegate") delegate: String,
        @Path("token") token: String,
        @Body request: InstantTransferRequest
    ): InstantTransferResponse

    companion object {
        const val BASE_URL = BuildConfig.INSTANT_TRANSFER_SERVICE_URL
    }
}
