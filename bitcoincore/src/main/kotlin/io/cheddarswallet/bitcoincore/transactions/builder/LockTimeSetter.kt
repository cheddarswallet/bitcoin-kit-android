package io.cheddarswallet.bitcoincore.transactions.builder

import io.cheddarswallet.bitcoincore.core.IStorage

class LockTimeSetter(private val storage: IStorage) {

    fun setLockTime(transaction: MutableTransaction) {
        transaction.transaction.lockTime = storage.lastBlock()?.height?.toLong() ?: 0
    }

}
