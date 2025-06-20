package io.cheddarswallet.bitcoincore.apisync.legacy

import io.cheddarswallet.bitcoincore.core.IApiSyncerListener
import io.cheddarswallet.bitcoincore.core.IApiTransactionProvider
import io.cheddarswallet.bitcoincore.extensions.toReversedByteArray
import io.cheddarswallet.bitcoincore.managers.IRestoreKeyConverter
import io.cheddarswallet.bitcoincore.models.BlockHash
import io.cheddarswallet.bitcoincore.models.PublicKey

class BlockHashScanner(
    private val restoreKeyConverter: IRestoreKeyConverter,
    private val transactionProvider: IApiTransactionProvider,
    private val helper: IBlockHashScanHelper
) {

    var listener: IApiSyncerListener? = null

    fun getBlockHashes(externalKeys: List<PublicKey>, internalKeys: List<PublicKey>): BlockHashesResponse {
        val externalAddresses = externalKeys.map {
            restoreKeyConverter.keysForApiRestore(it)
        }
        val internalAddresses = internalKeys.map {
            restoreKeyConverter.keysForApiRestore(it)
        }
        val allAddresses = externalAddresses.flatten() + internalAddresses.flatten()
        val transactions = transactionProvider.transactions(allAddresses, null)

        if (transactions.isEmpty()) {
            return BlockHashesResponse(listOf(), -1, -1)
        }

        listener?.onTransactionsFound(transactions.size)

        val addressItems = transactions.flatMap { it.addressItems }
        val externalLastUsedIndex = helper.lastUsedIndex(externalAddresses, addressItems)
        val internalLastUsedIndex = helper.lastUsedIndex(internalAddresses, addressItems)

        val blockHashes = transactions.map {
            BlockHash(it.blockHash.toReversedByteArray(), it.blockHeight, 0)
        }

        return BlockHashesResponse(blockHashes, externalLastUsedIndex, internalLastUsedIndex)
    }

    data class BlockHashesResponse(
        val blockHashes: List<BlockHash>,
        val externalLastUsedIndex: Int,
        val internalLastUsedIndex: Int
    )

}
