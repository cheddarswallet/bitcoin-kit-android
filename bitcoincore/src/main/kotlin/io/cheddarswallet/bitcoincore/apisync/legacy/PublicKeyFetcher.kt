package io.cheddarswallet.bitcoincore.apisync.legacy

import io.cheddarswallet.bitcoincore.core.AccountWallet
import io.cheddarswallet.bitcoincore.core.Wallet
import io.cheddarswallet.bitcoincore.core.WatchAccountWallet
import io.cheddarswallet.bitcoincore.models.PublicKey


interface IPublicKeyFetcher {
    fun publicKeys(indices: IntRange, external: Boolean): List<PublicKey>
}

interface IMultiAccountPublicKeyFetcher {
    val currentAccount: Int
    fun increaseAccount()
}

class PublicKeyFetcher(private val accountWallet: AccountWallet) : IPublicKeyFetcher {
    override fun publicKeys(indices: IntRange, external: Boolean): List<PublicKey> {
        return accountWallet.publicKeys(indices, external)
    }
}

class WatchPublicKeyFetcher(private val watchAccountWallet: WatchAccountWallet) : IPublicKeyFetcher {
    override fun publicKeys(indices: IntRange, external: Boolean): List<PublicKey> {
        return watchAccountWallet.publicKeys(indices, external)
    }
}

class MultiAccountPublicKeyFetcher(private val wallet: Wallet) : IPublicKeyFetcher, IMultiAccountPublicKeyFetcher {
    override fun publicKeys(indices: IntRange, external: Boolean): List<PublicKey> {
        return wallet.publicKeys(currentAccount, indices, external)
    }

    override var currentAccount: Int = 0
        private set

    @Synchronized
    override fun increaseAccount() {
        currentAccount++
    }
}
