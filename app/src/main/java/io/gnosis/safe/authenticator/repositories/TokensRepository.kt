package io.gnosis.safe.authenticator.repositories

import io.gnosis.safe.authenticator.data.RelayServiceApi
import io.gnosis.safe.authenticator.db.TokenInfoDao
import io.gnosis.safe.authenticator.db.TokenInfoDb
import io.gnosis.safe.authenticator.repositories.TokensRepository.Companion.ETH_ADDRESS
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

interface TokensRepository {

    suspend fun cacheTokenInfo(info: TokenInfo): TokenInfo
    suspend fun loadTokenInfo(token: Solidity.Address): TokenInfo

    data class TokenInfo(
        val address: Solidity.Address,
        val symbol: String,
        val decimals: Int,
        val name: String,
        val icon: String?
    )

    companion object {
        val ETH_ADDRESS = Solidity.Address(BigInteger.ZERO)
        val ETH_TOKEN_INFO = TokenInfo(ETH_ADDRESS, "ETH", 18, "Ether", "local::ethereum")
    }
}

class GnosisServiceTokenRepository(
    private val relayServiceApi: RelayServiceApi,
    private val tokenInfoDao: TokenInfoDao
) : TokensRepository {

    private val lastInit = System.currentTimeMillis()
    private val pendingTokenInfo = ConcurrentHashMap<Solidity.Address, Deferred<TokenInfoDb>>()

    override suspend fun cacheTokenInfo(info: TokensRepository.TokenInfo): TokensRepository.TokenInfo {
        if (info.address == ETH_ADDRESS) return TokensRepository.ETH_TOKEN_INFO
        TokenInfoDb(info.address, info.symbol, info.name, info.decimals, info.icon ?: "", System.currentTimeMillis()).apply {
            tokenInfoDao.insert(this)
        }
        return info
    }

    override suspend fun loadTokenInfo(token: Solidity.Address): TokensRepository.TokenInfo {
        if (token == ETH_ADDRESS) return TokensRepository.ETH_TOKEN_INFO
        val localToken = tokenInfoDao.load(token)
        val tokenInfo = if (shouldLoadRemote(localToken)) loadRemoteInfoAsync(token, localToken).await() else localToken!!
        return tokenInfo.toLocal()
    }

    private fun shouldLoadRemote(token: TokenInfoDb?) = token == null || lastInit > token.lastUpdate

    private fun loadRemoteInfoAsync(token: Solidity.Address, default: TokenInfoDb?) =
        pendingTokenInfo.getOrPut(token, {
            GlobalScope.async {
                try {
                    relayServiceApi.tokenInfo(token.asEthereumAddressChecksumString()).let {
                        TokenInfoDb(token, it.symbol, it.name, it.decimals, it.logoUri ?: "", System.currentTimeMillis()).apply {
                            tokenInfoDao.insert(this)
                            @Suppress("DeferredResultUnused")
                            pendingTokenInfo.remove(token)
                        }
                    }
                } catch (e: Exception) {
                    // Don't throw if we have a local version
                    default ?: throw e
                }
            }
        })

    private fun TokenInfoDb.toLocal() =
        TokensRepository.TokenInfo(address, symbol, decimals, name, icon)
}
