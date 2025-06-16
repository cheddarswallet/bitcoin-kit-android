package io.cheddarswallet.bitcoincore.rbf

import io.cheddarswallet.bitcoincore.models.TransactionInfo
import io.cheddarswallet.bitcoincore.transactions.builder.MutableTransaction

data class ReplacementTransaction(
    internal val mutableTransaction: MutableTransaction,
    val info: TransactionInfo,
    val replacedTransactionHashes: List<String>
)

data class ReplacementTransactionInfo(
    val replacementTxMinSize: Long,
    val feeRange: LongRange
)
