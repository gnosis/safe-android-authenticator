package io.gnosis.safe.authenticator.data

import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.data.models.PaginatedResult
import io.gnosis.safe.authenticator.data.models.ServiceTransaction
import io.gnosis.safe.authenticator.data.models.ServiceTransactionRequest
import retrofit2.http.*


interface TransactionServiceApi {

    @GET("v1/safes/{address}/transactions/")
    suspend fun loadTransactions(@Path("address") address: String): PaginatedResult<ServiceTransaction>

    @GET("v1/transactions/{hash}/")
    suspend fun loadTransaction(@Path("hash") hash: String): ServiceTransaction

    @POST("v1/safes/{address}/transactions/")
    suspend fun confirmTransaction(@Path("address") address: String, @Body confirmation: ServiceTransactionRequest)

    companion object {
        const val BASE_URL = BuildConfig.TRANSACTION_SERVICE_URL
    }
}
