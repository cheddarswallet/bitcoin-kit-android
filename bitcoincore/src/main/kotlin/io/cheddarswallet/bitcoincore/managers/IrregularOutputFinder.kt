package io.cheddarswallet.bitcoincore.managers

import io.cheddarswallet.bitcoincore.core.IStorage
import io.cheddarswallet.bitcoincore.models.TransactionOutput
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType
import io.cheddarswallet.bitcoincore.utils.Utils

interface IIrregularOutputFinder {
    fun hasIrregularOutput(outputs: List<TransactionOutput>): Boolean
}

class IrregularOutputFinder(
    private val storage: IStorage,
    additionalScriptTypes: List<ScriptType>
) : IIrregularOutputFinder, IBloomFilterProvider {

    private val irregularScriptTypes = listOf(ScriptType.P2WPKHSH, ScriptType.P2WPKH, ScriptType.P2PK, ScriptType.P2TR) + additionalScriptTypes

    // IIrregularOutputFinder

    override fun hasIrregularOutput(outputs: List<TransactionOutput>): Boolean {
        return outputs.any { it.publicKeyPath != null && irregularScriptTypes.contains(it.scriptType) }
    }

    // IBloomFilterProvider

    override var bloomFilterManager: BloomFilterManager? = null

    override fun getBloomFilterElements(): List<ByteArray> {
        val elements = mutableListOf<ByteArray>()

        val transactionOutputs = storage.lastBlock()?.height?.let { lastBlockHeight ->
            // get transaction outputs which are unspent or spent in last 100 blocks
            storage.getOutputsForBloomFilter(lastBlockHeight - 100, irregularScriptTypes)
        } ?: listOf()

        for (output in transactionOutputs) {
            val outpoint = output.transactionHash + Utils.intToByteArray(output.index).reversedArray()
            elements.add(outpoint)
        }

        return elements
    }
}
