package io.cheddarswallet.dashkit

// TODO Rename to listener
interface IInstantTransactionDelegate {
    fun onUpdateInstant(transactionHash: ByteArray)
}
