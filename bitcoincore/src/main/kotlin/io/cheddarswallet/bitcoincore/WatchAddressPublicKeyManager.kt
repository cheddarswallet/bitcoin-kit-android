package io.cheddarswallet.bitcoincore

import io.cheddarswallet.bitcoincore.apisync.legacy.IPublicKeyFetcher
import io.cheddarswallet.bitcoincore.core.IPublicKeyManager
import io.cheddarswallet.bitcoincore.managers.AccountPublicKeyManager
import io.cheddarswallet.bitcoincore.managers.BloomFilterManager
import io.cheddarswallet.bitcoincore.managers.IBloomFilterProvider
import io.cheddarswallet.bitcoincore.managers.RestoreKeyConverterChain
import io.cheddarswallet.bitcoincore.models.PublicKey
import io.cheddarswallet.bitcoincore.models.WatchAddressPublicKey

class WatchAddressPublicKeyManager(
    private val publicKey: WatchAddressPublicKey,
    private val restoreKeyConverter: RestoreKeyConverterChain
) : IPublicKeyFetcher, IPublicKeyManager, IBloomFilterProvider {

    override fun publicKeys(indices: IntRange, external: Boolean) = listOf(publicKey)

    override fun changePublicKey() = publicKey

    override fun receivePublicKey() = publicKey

    override fun usedExternalPublicKeys(change: Boolean): List<PublicKey> = listOf(publicKey)

    override fun fillGap() {
        bloomFilterManager?.regenerateBloomFilter()
    }

    override fun addKeys(keys: List<PublicKey>) = Unit

    override fun gapShifts(): Boolean = false

    override fun getPublicKeyByPath(path: String): PublicKey {
        throw AccountPublicKeyManager.Error.InvalidPath
    }

    override var bloomFilterManager: BloomFilterManager? = null

    override fun getBloomFilterElements() = restoreKeyConverter.bloomFilterElements(publicKey)
}
