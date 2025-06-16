package io.cheddarswallet.bitcoincore.network.peer.task

import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.models.InventoryItem
import io.cheddarswallet.bitcoincore.network.messages.GetDataMessage
import io.cheddarswallet.bitcoincore.network.messages.IMessage
import io.cheddarswallet.bitcoincore.network.messages.InvMessage
import io.cheddarswallet.bitcoincore.network.messages.TransactionMessage
import io.cheddarswallet.bitcoincore.storage.FullTransaction
import java.util.concurrent.TimeUnit

class SendTransactionTask(val transaction: FullTransaction) : PeerTask() {

    init {
        allowedIdleTime = TimeUnit.SECONDS.toMillis(30)
    }

    override val state: String
        get() = "transaction: ${transaction.header.hash.toReversedHex()}"

    override fun start() {
        requester?.send(InvMessage(InventoryItem.MSG_TX, transaction.header.hash))
        resetTimer()
    }

    override fun handleMessage(message: IMessage): Boolean {
        val transactionRequested =
                message is GetDataMessage &&
                message.inventory.any { it.type == InventoryItem.MSG_TX && it.hash.contentEquals(transaction.header.hash) }

        if (transactionRequested) {
            requester?.send(TransactionMessage(transaction, 0))
            listener?.onTaskCompleted(this)
        }

        return transactionRequested
    }

    override fun handleTimeout() {
        listener?.onTaskCompleted(this)
    }

}
