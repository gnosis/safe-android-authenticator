package io.gnosis.safe.authenticator

import android.app.Application
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.data.InstantTransferServiceApi
import io.gnosis.safe.authenticator.data.JsonRpcApi
import io.gnosis.safe.authenticator.data.RelayServiceApi
import io.gnosis.safe.authenticator.data.TransactionServiceApi
import io.gnosis.safe.authenticator.data.adapter.*
import io.gnosis.safe.authenticator.db.InstantTransfersDatabase
import io.gnosis.safe.authenticator.db.TokensDatabase
import io.gnosis.safe.authenticator.repositories.GnosisServiceTokenRepository
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.SafeRepositoryImpl
import io.gnosis.safe.authenticator.repositories.TokensRepository
import io.gnosis.safe.authenticator.services.CrashReporter
import io.gnosis.safe.authenticator.services.FirebaseCrashReporter
import io.gnosis.safe.authenticator.ui.assets.AssetsContract
import io.gnosis.safe.authenticator.ui.assets.AssetsViewModel
import io.gnosis.safe.authenticator.ui.instant.InstantTransferListContract
import io.gnosis.safe.authenticator.ui.instant.InstantTransferListViewModel
import io.gnosis.safe.authenticator.ui.instant.NewInstantTransferContract
import io.gnosis.safe.authenticator.ui.instant.NewInstantTransferViewModel
import io.gnosis.safe.authenticator.ui.intro.IntroContract
import io.gnosis.safe.authenticator.ui.intro.IntroViewModel
import io.gnosis.safe.authenticator.ui.settings.*
import io.gnosis.safe.authenticator.ui.splash.SplashContract
import io.gnosis.safe.authenticator.ui.splash.SplashViewModel
import io.gnosis.safe.authenticator.ui.transactions.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import org.koin.android.ext.android.getKoin
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
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class SafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // start Koin!

        startKoin {
            // Android context
            androidContext(this@SafeApp)
            // modules
            modules(listOf(coreModule, apiModule, repositoryModule, viewModelModule))
        }

        getKoin().get<CrashReporter>().init()
    }


    private val coreModule = module {

        single { Picasso.get() }

        single {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }

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

        single<CrashReporter> { FirebaseCrashReporter() }

        // Storage / Security

        single { PreferencesManager(get()) }

        single<KeyStorage> { AndroidKeyStorage(get()) }

        single<FingerprintHelper> { AndroidFingerprintHelper(get()) }

        single<EncryptionManager> { AesEncryptionManager(get(), get(), get(), get(), 4096) }

        // Databases

        single { Room.databaseBuilder(get(), InstantTransfersDatabase::class.java, InstantTransfersDatabase.DB_NAME).build() }

        single { get<InstantTransfersDatabase>().instantTransferDao() }

        single { Room.databaseBuilder(get(), TokensDatabase::class.java, TokensDatabase.DB_NAME).build() }

        single { get<TokensDatabase>().tokenInfoDao() }
    }

    private val apiModule = module {

        single<RelayServiceApi> {
            Retrofit.Builder()
                .client(get())
                .baseUrl(RelayServiceApi.BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(get()))
                .build()
                .create(RelayServiceApi::class.java)
        }

        single<TransactionServiceApi> {
            Retrofit.Builder()
                .client(get())
                .baseUrl(TransactionServiceApi.BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(get()))
                .build()
                .create(TransactionServiceApi::class.java)
        }

        single<InstantTransferServiceApi> {
            Retrofit.Builder()
                .client(get())
                .baseUrl(InstantTransferServiceApi.BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(get()))
                .build()
                .create(InstantTransferServiceApi::class.java)
        }

        single<JsonRpcApi> {
            val baseClient: OkHttpClient = get()
            val client = baseClient.newBuilder().addInterceptor {
                val request = it.request()
                val builder = request.url.newBuilder()
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
        single<SafeRepository> { SafeRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        single<TokensRepository> { GnosisServiceTokenRepository(get(), get()) }
    }

    @ExperimentalCoroutinesApi
    private val viewModelModule = module {
        viewModel<SplashContract> { SplashViewModel(get(), get()) }
        viewModel<IntroContract> { IntroViewModel(get()) }
        viewModel<AssetsContract> { AssetsViewModel(get(), get()) }
        viewModel<TransactionsContract> { TransactionsViewModel(get()) }
        viewModel<SettingsContract> { SettingsViewModel(get()) }
        viewModel<NewTransactionContract> { NewTransactionViewModel(get()) }
        viewModel<SetAllowanceContract> { SetAllowanceViewModel(get()) }
        viewModel<ManageAllowancesContract> { ManageAllowancesViewModel(get(), get()) }
        viewModel<NewInstantTransferContract> { NewInstantTransferViewModel(get(), get()) }
        viewModel<InstantTransferListContract> { InstantTransferListViewModel(get()) }
        viewModel<TransactionConfirmationContract> { (
                                                         safe: Solidity.Address,
                                                         transactionHash: String?,
                                                         transaction: SafeRepository.SafeTx,
                                                         executionInfo: SafeRepository.SafeTxExecInfo?
                                                     ) ->
            TransactionConfirmationViewModel(safe, transactionHash, transaction, executionInfo, get())
        }
    }
}
