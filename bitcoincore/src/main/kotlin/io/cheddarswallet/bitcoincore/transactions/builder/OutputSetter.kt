package io.cheddarswallet.bitcoincore.transactions.builder

import io.cheddarswallet.bitcoincore.core.ITransactionDataSorterFactory
import io.cheddarswallet.bitcoincore.io.BitcoinOutput
import io.cheddarswallet.bitcoincore.models.TransactionDataSortType
import io.cheddarswallet.bitcoincore.models.TransactionOutput
import io.cheddarswallet.bitcoincore.transactions.scripts.OP_RETURN
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType

class OutputSetter(private val transactionDataSorterFactory: ITransactionDataSorterFactory) {

    fun setOutputs(transaction: MutableTransaction, sortType: TransactionDataSortType) {
        val list = mutableListOf<TransactionOutput>()

        transaction.recipientAddress.let {
            list.add(TransactionOutput(transaction.recipientValue, 0, it.lockingScript, it.scriptType, it.stringValue, it.lockingScriptPayload))
        }

        transaction.changeAddress?.let {
            list.add(TransactionOutput(transaction.changeValue, 0, it.lockingScript, it.scriptType, it.stringValue, it.lockingScriptPayload))
        }

        if (transaction.getPluginData().isNotEmpty()) {
            var data = byteArrayOf(OP_RETURN.toByte())
            transaction.getPluginData().forEach {
                data += byteArrayOf(it.key) + it.value
            }

            list.add(TransactionOutput(0, 0, data, ScriptType.NULL_DATA))
        }

        val sorted = transactionDataSorterFactory.sorter(sortType).sortOutputs(list).toMutableList()

        transaction.memo?.let { memo ->
            val data = BitcoinOutput()
                .writeByte(OP_RETURN)
                .writeString(memo)
                .toByteArray()

            sorted.add(TransactionOutput(0, 0, data, ScriptType.NULL_DATA))
        }

        sorted.forEachIndexed { index, transactionOutput ->
            transactionOutput.index = index
        }

        transaction.outputs = sorted
    }

}
