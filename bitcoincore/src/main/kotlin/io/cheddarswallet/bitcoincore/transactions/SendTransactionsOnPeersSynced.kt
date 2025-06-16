package io.cheddarswallet.bitcoincore.transactions

import io.cheddarswallet.bitcoincore.blocks.IPeerSyncListener

class SendTransactionsOnPeersSynced(var transactionSender: TransactionSender) : IPeerSyncListener {

    override fun onAllPeersSynced() {
        transactionSender.sendPendingTransactions()
    }

}

