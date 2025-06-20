package io.cheddarswallet.bitcoincore.transactions

import io.cheddarswallet.bitcoincore.core.IPluginData
import io.cheddarswallet.bitcoincore.managers.BloomFilterManager
import io.cheddarswallet.bitcoincore.models.TransactionDataSortType
import io.cheddarswallet.bitcoincore.storage.FullTransaction
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters
import io.cheddarswallet.bitcoincore.transactions.builder.MutableTransaction
import io.cheddarswallet.bitcoincore.transactions.builder.TransactionBuilder
import io.cheddarswallet.bitcoincore.transactions.builder.TransactionSigner

class TransactionCreator(
    private val builder: TransactionBuilder,
    private val processor: PendingTransactionProcessor,
    private val transactionSender: TransactionSender,
    private val transactionSigner: TransactionSigner,
    private val bloomFilterManager: BloomFilterManager
) {

    @Throws
    fun create(
        toAddress: String,
        memo: String?,
        value: Long,
        feeRate: Int,
        senderPay: Boolean,
        sortType: TransactionDataSortType,
        unspentOutputs: List<UnspentOutput>?,
        pluginData: Map<Byte, IPluginData>,
        rbfEnabled: Boolean,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): FullTransaction {
        val mutableTransaction = builder.buildTransaction(
            toAddress = toAddress,
            memo = memo,
            value = value,
            feeRate = feeRate,
            senderPay = senderPay,
            sortType = sortType,
            unspentOutputs = unspentOutputs,
            pluginData = pluginData,
            rbfEnabled = rbfEnabled,
            dustThreshold = dustThreshold,
            changeToFirstInput = changeToFirstInput,
            filters = filters,
        )

        return create(mutableTransaction)
    }

    @Throws
    fun create(
        unspentOutput: UnspentOutput,
        toAddress: String,
        memo: String?,
        feeRate: Int,
        sortType: TransactionDataSortType,
        rbfEnabled: Boolean
    ): FullTransaction {
        val mutableTransaction = builder.buildTransaction(unspentOutput, toAddress, memo, feeRate, sortType, rbfEnabled)

        return create(mutableTransaction)
    }

    fun create(mutableTransaction: MutableTransaction): FullTransaction {
        transactionSigner.sign(mutableTransaction)

        val fullTransaction = mutableTransaction.build()
        processAndSend(fullTransaction)

        return fullTransaction
    }

    private fun processAndSend(transaction: FullTransaction): FullTransaction {
        transactionSender.canSendTransaction()

        try {
            processor.processCreated(transaction)
        } catch (ex: BloomFilterManager.BloomFilterExpired) {
            bloomFilterManager.regenerateBloomFilter()
        }

        try {
            transactionSender.sendPendingTransactions()
        } catch (e: Exception) {
            // ignore any exception since the tx is inserted to the db
        }

        return transaction
    }

    open class TransactionCreationException(msg: String) : Exception(msg)
    class TransactionAlreadyExists(msg: String) : TransactionCreationException(msg)

}
