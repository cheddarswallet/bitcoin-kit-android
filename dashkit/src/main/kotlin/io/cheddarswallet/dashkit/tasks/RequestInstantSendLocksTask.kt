package io.cheddarswallet.dashkit.tasks

import io.cheddarswallet.bitcoincore.models.InventoryItem
import io.cheddarswallet.bitcoincore.network.messages.GetDataMessage
import io.cheddarswallet.bitcoincore.network.messages.IMessage
import io.cheddarswallet.bitcoincore.network.peer.task.PeerTask
import io.cheddarswallet.dashkit.InventoryType
import io.cheddarswallet.dashkit.messages.ISLockMessage

class RequestInstantSendLocksTask(hashes: List<ByteArray>) : PeerTask() {

    val hashes = hashes.toMutableList()
    var isLocks = mutableListOf<ISLockMessage>()

    override fun start() {
        requester?.send(GetDataMessage(hashes.map { InventoryItem(InventoryType.MSG_ISLOCK, it) }))
    }

    override fun handleMessage(message: IMessage) = when (message) {
        is ISLockMessage -> handleISLockVote(message)
        else -> false
    }

    private fun handleISLockVote(isLockMessage: ISLockMessage): Boolean {
        val hash = hashes.firstOrNull { it.contentEquals(isLockMessage.hash) } ?: return false

        hashes.remove(hash)
        isLocks.add(isLockMessage)

        if (hashes.isEmpty()) {
            listener?.onTaskCompleted(this)
        }

        return true
    }

}
