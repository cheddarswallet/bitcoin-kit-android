package io.cheddarswallet.dashkit.messages

import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.io.BitcoinOutput
import io.cheddarswallet.bitcoincore.network.messages.IMessage
import io.cheddarswallet.bitcoincore.network.messages.IMessageSerializer

class GetMasternodeListDiffMessage(val baseBlockHash: ByteArray, val blockHash: ByteArray) : IMessage {
    override fun toString(): String {
        return "GetMasternodeListDiffMessage(baseBlockHash=${baseBlockHash.toReversedHex()}, blockHash=${blockHash.toReversedHex()})"
    }
}

class GetMasternodeListDiffMessageSerializer : IMessageSerializer {
    override val command: String = "getmnlistd"

    override fun serialize(message: IMessage): ByteArray? {
        if (message !is GetMasternodeListDiffMessage) {
            return null
        }

        return BitcoinOutput()
                .write(message.baseBlockHash)
                .write(message.blockHash)
                .toByteArray()
    }
}
