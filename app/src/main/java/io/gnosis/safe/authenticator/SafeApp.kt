package io.gnosis.safe.authenticator

import android.app.Application
import com.squareup.moshi.Moshi
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.data.JsonRpcApi
import io.gnosis.safe.authenticator.data.TransactionServiceApi
import io.gnosis.safe.authenticator.data.adapter.*
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.SafeRepositoryImpl
import io.gnosis.safe.authenticator.ui.transactions.TransactionsContract
import io.gnosis.safe.authenticator.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import pm.gnosis.mnemonic.Bip39
import pm.gnosis.mnemonic.Bip39Generator
import pm.gnosis.mnemonic.android.AndroidWordListProvider
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.security.EncryptionManager
import pm.gnosis.svalinn.security.FingerprintHelper
import pm.gnosis.svalinn.security.KeyStorage
import pm.gnosis.svalinn.security.impls.AesEncryptionManager
import pm.gnosis.svalinn.security.impls.AndroidFingerprintHelper
import pm.gnosis.svalinn.security.impls.AndroidKeyStorage
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@ExperimentalCoroutinesApi
class SafeApp: Application() {
    override fun onCreate() {
        super.onCreate()
        // start Koin!

        startKoin {
            // Android context
            androidContext(this@SafeApp)
            // modules
            modules(listOf(coreModule, apiModule, repositoryModule, viewModelModule))
        }
    }


    private val coreModule = module {

        single { Picasso.get() }

        single { OkHttpClient.Builder().build() }

        single {
            Moshi.Builder()
                .add(WeiAdapter())
                .add(HexNumberAdapter())
                .add(DecimalNumberAdapter())
                .add(DefaultNumberAdapter())
                .add(SolidityAddressAdapter())
                .build()
        }

        single<Bip39> { Bip39Generator(AndroidWordListProvider(get())) }

        single { PreferencesManager(get()) }

        single<KeyStorage> { AndroidKeyStorage(get()) }

        single<FingerprintHelper> { AndroidFingerprintHelper(get()) }

        single<EncryptionManager> { AesEncryptionManager(get(), get(), get(), get(), 4096) }
    }

    private val apiModule = module {
        single<TransactionServiceApi> {
            Retrofit.Builder()
                .client(get())
                .baseUrl(TransactionServiceApi.BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(get()))
                .build()
                .create(TransactionServiceApi::class.java)
        }

        single<JsonRpcApi> {
            val baseClient: OkHttpClient = get()
            val client = baseClient.newBuilder().addInterceptor {
                val request = it.request()
                val builder = request.url().newBuilder()
                val url = builder.addPathSegment(BuildConfig.INFURA_API_KEY).build()
                it.proceed(request.newBuilder().url(url).build())
            }.build()
            Retrofit.Builder()
                .client(client)
                .baseUrl(JsonRpcApi.BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(get()))
                .build()
                .create(JsonRpcApi::class.java)
        }
    }

    private val repositoryModule = module {
        single<SafeRepository> { SafeRepositoryImpl(get(), get(), get(), get(), get()) }
    }

    @ExperimentalCoroutinesApi
    private val viewModelModule = module {
        viewModel<TransactionsContract> { TransactionsViewModel(get()) }
    }
}