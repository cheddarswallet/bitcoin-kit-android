package io.cheddarswallet.dashkit.instantsend.instantsendlock

import io.cheddarswallet.bitcoincore.core.HashBytes
import io.cheddarswallet.dashkit.instantsend.InstantSendLockValidator
import io.cheddarswallet.dashkit.messages.ISLockMessage

class InstantSendLockManager(private val instantSendLockValidator: InstantSendLockValidator) {
    private val relayedLocks = mutableMapOf<HashBytes, ISLockMessage>()

    fun add(relayed: ISLockMessage) {
        relayedLocks[HashBytes(relayed.txHash)] = relayed
    }

    fun takeRelayedLock(txHash: ByteArray): ISLockMessage? {
        relayedLocks[HashBytes(txHash)]?.let {
            relayedLocks.remove(HashBytes(txHash))
            return it
        }
        return null
    }

    @Throws
    fun validate(isLock: ISLockMessage) {
        instantSendLockValidator.validate(isLock)
    }

}
