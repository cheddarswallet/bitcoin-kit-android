package io.cheddarswallet.dashkit.core

import io.cheddarswallet.bitcoincore.core.BaseTransactionInfoConverter
import io.cheddarswallet.bitcoincore.core.ITransactionInfoConverter
import io.cheddarswallet.bitcoincore.extensions.toHexString
import io.cheddarswallet.bitcoincore.models.InvalidTransaction
import io.cheddarswallet.bitcoincore.models.Transaction
import io.cheddarswallet.bitcoincore.models.TransactionMetadata
import io.cheddarswallet.bitcoincore.models.TransactionStatus
import io.cheddarswallet.bitcoincore.storage.FullTransactionInfo
import io.cheddarswallet.dashkit.instantsend.InstantTransactionManager
import io.cheddarswallet.dashkit.models.DashTransactionInfo

class DashTransactionInfoConverter(private val instantTransactionManager: InstantTransactionManager) : ITransactionInfoConverter {
    override lateinit var baseConverter: BaseTransactionInfoConverter

    override fun transactionInfo(fullTransactionInfo: FullTransactionInfo): DashTransactionInfo {
        val transaction = fullTransactionInfo.header

        if (transaction.status == Transaction.Status.INVALID) {
            (transaction as? InvalidTransaction)?.let {
                return getInvalidTransactionInfo(it, fullTransactionInfo.metadata)
            }
        }

        val txInfo = baseConverter.transactionInfo(fullTransactionInfo)

        return DashTransactionInfo(
                txInfo.uid,
                txInfo.transactionHash,
                txInfo.transactionIndex,
                txInfo.inputs,
                txInfo.outputs,
                txInfo.amount,
                txInfo.type,
                txInfo.fee,
                txInfo.blockHeight,
                txInfo.timestamp,
                txInfo.status,
                txInfo.conflictingTxHash,
                instantTransactionManager.isTransactionInstant(fullTransactionInfo.header.hash)
        )
    }

    private fun getInvalidTransactionInfo(
        transaction: InvalidTransaction,
        metadata: TransactionMetadata
    ): DashTransactionInfo {
        return try {
            DashTransactionInfo(transaction.serializedTxInfo)
        } catch (ex: Exception) {
            DashTransactionInfo(
                uid = transaction.uid,
                transactionHash = transaction.hash.toHexString(),
                transactionIndex = transaction.order,
                inputs = listOf(),
                outputs = listOf(),
                amount = metadata.amount,
                type = metadata.type,
                fee = null,
                blockHeight = null,
                timestamp = transaction.timestamp,
                status = TransactionStatus.INVALID,
                conflictingTxHash = null,
                instantTx = false
            )
        }
    }

}
