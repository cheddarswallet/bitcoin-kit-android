package io.cheddarswallet.dashkit.messages

import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.io.BitcoinInputMarkable
import io.cheddarswallet.bitcoincore.network.messages.IMessage
import io.cheddarswallet.bitcoincore.network.messages.IMessageParser
import io.cheddarswallet.bitcoincore.serializers.TransactionSerializer
import io.cheddarswallet.bitcoincore.storage.FullTransaction

class TransactionLockMessage(var transaction: FullTransaction) : IMessage {
    override fun toString(): String {
        return "TransactionLockMessage(${transaction.header.hash.toReversedHex()})"
    }
}

class TransactionLockMessageParser : IMessageParser {
    override val command: String = "ix"

    override fun parseMessage(input: BitcoinInputMarkable): IMessage {
        val transaction = TransactionSerializer.deserialize(input)
        return TransactionLockMessage(transaction)
    }
}
