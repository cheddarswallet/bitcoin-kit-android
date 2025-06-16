package io.cheddarswallet.bitcoincore.core

import io.cheddarswallet.bitcoincore.models.TransactionInfo
import io.cheddarswallet.bitcoincore.storage.FullTransactionInfo

class TransactionInfoConverter : ITransactionInfoConverter {
    override lateinit var baseConverter: BaseTransactionInfoConverter

    override fun transactionInfo(fullTransactionInfo: FullTransactionInfo): TransactionInfo {
        return baseConverter.transactionInfo(fullTransactionInfo)
    }
}
