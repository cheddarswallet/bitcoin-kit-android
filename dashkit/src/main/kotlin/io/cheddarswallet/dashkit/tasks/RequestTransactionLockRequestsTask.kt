package io.cheddarswallet.dashkit.tasks

import io.cheddarswallet.bitcoincore.models.InventoryItem
import io.cheddarswallet.bitcoincore.network.messages.GetDataMessage
import io.cheddarswallet.bitcoincore.network.messages.IMessage
import io.cheddarswallet.bitcoincore.network.peer.task.PeerTask
import io.cheddarswallet.bitcoincore.storage.FullTransaction
import io.cheddarswallet.dashkit.InventoryType
import io.cheddarswallet.dashkit.messages.TransactionLockMessage

class RequestTransactionLockRequestsTask(hashes: List<ByteArray>) : PeerTask() {

    val hashes = hashes.toMutableList()
    var transactions = mutableListOf<FullTransaction>()

    override fun start() {
        val items = hashes.map { hash ->
            InventoryItem(InventoryType.MSG_TXLOCK_REQUEST, hash)
        }

        requester?.send(GetDataMessage(items))
    }

    override fun handleMessage(message: IMessage) = when (message) {
        is TransactionLockMessage -> handleTransactionLockRequest(message.transaction)
        else -> false
    }

    private fun handleTransactionLockRequest(transaction: FullTransaction): Boolean {
        val hash = hashes.firstOrNull { it.contentEquals(transaction.header.hash) } ?: return false

        hashes.remove(hash)
        transactions.add(transaction)

        if (hashes.isEmpty()) {
            listener?.onTaskCompleted(this)
        }

        return true
    }

}
