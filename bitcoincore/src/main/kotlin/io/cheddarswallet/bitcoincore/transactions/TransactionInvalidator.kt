package io.cheddarswallet.bitcoincore.transactions

import io.cheddarswallet.bitcoincore.blocks.IBlockchainDataListener
import io.cheddarswallet.bitcoincore.core.IStorage
import io.cheddarswallet.bitcoincore.core.ITransactionInfoConverter
import io.cheddarswallet.bitcoincore.models.InvalidTransaction
import io.cheddarswallet.bitcoincore.models.Transaction
import io.cheddarswallet.bitcoincore.storage.FullTransactionInfo

class TransactionInvalidator(
        private val storage: IStorage,
        private val transactionInfoConverter: ITransactionInfoConverter,
        private val listener: IBlockchainDataListener
) {

    fun invalidate(transaction: Transaction) {
        val invalidTransactionsFullInfo = storage.getDescendantTransactionsFullInfo(transaction.hash)

        if (invalidTransactionsFullInfo.isEmpty()) return

        invalidTransactionsFullInfo.forEach { fullTxInfo ->
            fullTxInfo.header.status = Transaction.Status.INVALID
        }

        val invalidTransactions = invalidTransactionsFullInfo.map { fullTxInfo ->
            val txInfo = transactionInfoConverter.transactionInfo(fullTxInfo)
            val serializedTxInfo = txInfo.serialize()
            InvalidTransaction(fullTxInfo.header, serializedTxInfo, fullTxInfo.rawTransaction)
        }

        storage.moveTransactionToInvalidTransactions(invalidTransactions)
        listener.onTransactionsUpdate(listOf(), invalidTransactions, null)
    }

}
