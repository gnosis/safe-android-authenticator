package io.gnosis.safe.authenticator.data

import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.data.models.*
import retrofit2.http.*


interface TransferLimitServiceApi {

    @POST("1/safes/{safe}/tokens/{token}/execute_limit_transfer")
    suspend fun executeLimitTransfer(
        @Path("safe") safe: String,
        @Path("token") token: String,
        @Body execution: LimitTransferExecution
    ): LimitTransferExecutionResponse

    companion object {
        const val BASE_URL = BuildConfig.TRANSFER_LIMIT_SERVICE_URL
    }
}
