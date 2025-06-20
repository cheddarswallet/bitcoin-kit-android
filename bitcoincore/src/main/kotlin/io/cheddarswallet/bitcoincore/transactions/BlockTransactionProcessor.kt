package io.cheddarswallet.bitcoincore.transactions

import io.cheddarswallet.bitcoincore.WatchedTransactionManager
import io.cheddarswallet.bitcoincore.blocks.IBlockchainDataListener
import io.cheddarswallet.bitcoincore.core.IPublicKeyManager
import io.cheddarswallet.bitcoincore.core.IStorage
import io.cheddarswallet.bitcoincore.core.inTopologicalOrder
import io.cheddarswallet.bitcoincore.managers.BloomFilterManager
import io.cheddarswallet.bitcoincore.managers.IIrregularOutputFinder
import io.cheddarswallet.bitcoincore.models.Block
import io.cheddarswallet.bitcoincore.models.Transaction
import io.cheddarswallet.bitcoincore.storage.FullTransaction
import io.cheddarswallet.bitcoincore.transactions.extractors.TransactionExtractor

class BlockTransactionProcessor(
    private val storage: IStorage,
    private val extractor: TransactionExtractor,
    private val publicKeyManager: IPublicKeyManager,
    private val irregularOutputFinder: IIrregularOutputFinder,
    private val dataListener: IBlockchainDataListener,
    private val conflictsResolver: TransactionConflictsResolver,
    private val invalidator: TransactionInvalidator
) {

    var transactionListener: WatchedTransactionManager? = null

    private fun resolveConflicts(fullTransaction: FullTransaction) {
        for (transaction in conflictsResolver.getTransactionsConflictingWithInBlockTransaction(fullTransaction)) {
            transaction.conflictingTxHash = fullTransaction.header.hash
            invalidator.invalidate(transaction)
        }
    }

    @Throws(BloomFilterManager.BloomFilterExpired::class)
    fun processReceived(transactions: List<FullTransaction>, block: Block, skipCheckBloomFilter: Boolean) {
        var needToUpdateBloomFilter = false

        val inserted = mutableListOf<Transaction>()
        val updated = mutableListOf<Transaction>()

        // when the same transaction came in merkle block and from another peer's mempool we need to process it serial
        synchronized(this) {
            for ((index, fullTransaction) in transactions.inTopologicalOrder().withIndex()) {
                val transaction = fullTransaction.header
                val existingTransaction = storage.getFullTransaction(transaction.hash)
                if (existingTransaction != null) {
                    extractor.extract(existingTransaction)
                    transactionListener?.onTransactionReceived(existingTransaction)
                    relay(existingTransaction.header, index, block)
                    resolveConflicts(fullTransaction)

                    storage.updateTransaction(existingTransaction)
                    updated.add(existingTransaction.header)

                    continue
                }

                extractor.extract(fullTransaction)
                transactionListener?.onTransactionReceived(fullTransaction)

                if (!transaction.isMine) {
                    conflictsResolver.getIncomingPendingTransactionsConflictingWith(fullTransaction).forEach { tx ->
                        tx.conflictingTxHash = fullTransaction.header.hash
                        invalidator.invalidate(tx)
                        needToUpdateBloomFilter = true
                    }

                    continue
                }

                relay(transaction, index, block)
                resolveConflicts(fullTransaction)


                val invalidTransaction = storage.getInvalidTransaction(transaction.hash)
                if (invalidTransaction != null) {
                    storage.moveInvalidTransactionToTransactions(invalidTransaction, fullTransaction)
                    updated.add(transaction)
                } else {
                    storage.addTransaction(fullTransaction)
                    inserted.add(transaction)
                }

                if (!skipCheckBloomFilter) {
                    needToUpdateBloomFilter = needToUpdateBloomFilter ||
                            publicKeyManager.gapShifts() ||
                            irregularOutputFinder.hasIrregularOutput(fullTransaction.outputs)
                }
            }
        }

        if (inserted.isNotEmpty() || updated.isNotEmpty()) {
            if (!block.hasTransactions) {
                block.hasTransactions = true
                storage.updateBlock(block)

            }
            dataListener.onTransactionsUpdate(inserted, updated, block)
        }

        if (needToUpdateBloomFilter) {
            throw BloomFilterManager.BloomFilterExpired
        }
    }


    private fun relay(transaction: Transaction, order: Int, block: Block) {
        transaction.blockHash = block.headerHash
        transaction.timestamp = block.timestamp
        transaction.conflictingTxHash = null
        transaction.status = Transaction.Status.RELAYED
        transaction.order = order
    }

}
