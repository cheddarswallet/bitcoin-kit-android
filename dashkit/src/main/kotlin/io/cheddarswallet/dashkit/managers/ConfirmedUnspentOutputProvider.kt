package io.cheddarswallet.dashkit.managers

import io.cheddarswallet.bitcoincore.core.IStorage
import io.cheddarswallet.bitcoincore.managers.IUnspentOutputProvider
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters

class ConfirmedUnspentOutputProvider(private val storage: IStorage, private val confirmationsThreshold: Int) : IUnspentOutputProvider {
    override fun getSpendableUtxo(filters: UtxoFilters): List<UnspentOutput> {
        val lastBlockHeight = storage.lastBlock()?.height ?: 0

        return storage.getUnspentOutputs().filter {
            isOutputConfirmed(it, lastBlockHeight) && filters.filterUtxo(it, storage)
        }
    }

    private fun isOutputConfirmed(unspentOutput: UnspentOutput, lastBlockHeight: Int): Boolean {
        val block = unspentOutput.block ?: return false

        return block.height <= lastBlockHeight - confirmationsThreshold + 1
    }
}
