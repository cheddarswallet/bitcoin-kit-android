package io.cheddarswallet.dashkit.messages

import io.cheddarswallet.bitcoincore.io.BitcoinInputMarkable
import io.cheddarswallet.bitcoincore.io.BitcoinOutput
import io.cheddarswallet.bitcoincore.network.messages.IMessage
import io.cheddarswallet.bitcoincore.network.messages.IMessageParser
import io.cheddarswallet.bitcoincore.network.messages.TransactionMessage
import io.cheddarswallet.bitcoincore.serializers.TransactionSerializer
import io.cheddarswallet.bitcoincore.storage.FullTransaction
import io.cheddarswallet.bitcoincore.utils.HashUtils
import io.cheddarswallet.dashkit.models.SpecialTransaction
import java.nio.ByteBuffer

class TransactionMessageParser : IMessageParser {
    override val command: String = "tx"

    private fun parseSpecialTxData(input: BitcoinInputMarkable, transaction: FullTransaction): SpecialTransaction {
        val payloadSize = input.readVarInt()
        val payload = input.readBytes(payloadSize.toInt())

        val output = BitcoinOutput()
        output.write(TransactionSerializer.serialize(transaction))
        output.writeVarInt(payloadSize)
        output.write(payload)

        val hash = HashUtils.doubleSha256(output.toByteArray())
        transaction.setHash(hash)

        return SpecialTransaction(transaction, payload, false)
    }

    override fun parseMessage(input: BitcoinInputMarkable): IMessage {
        var transaction = TransactionSerializer.deserialize(input)

        val bytes = ByteBuffer.allocate(4).putInt(transaction.header.version).array()
        val txType = bytes[0] + bytes[1]

        if (txType > 0) {
            transaction = parseSpecialTxData(input, transaction)
        }

        return TransactionMessage(transaction, input.count)
    }

}
