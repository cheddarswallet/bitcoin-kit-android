package io.cheddarswallet.dashkit.messages

import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.io.BitcoinInputMarkable
import io.cheddarswallet.bitcoincore.io.BitcoinOutput
import io.cheddarswallet.bitcoincore.network.messages.IMessage
import io.cheddarswallet.bitcoincore.network.messages.IMessageParser
import io.cheddarswallet.bitcoincore.utils.HashUtils

class ISLockMessage(
        val inputs: List<Outpoint>,
        val txHash: ByteArray,
        val sign: ByteArray,
        val hash: ByteArray,
        val requestId: ByteArray
) : IMessage {

    override fun toString(): String {
        return "ISLockMessage(hash=${hash.toReversedHex()}, txHash=${txHash.toReversedHex()})"
    }
}

class ISLockMessageParser : IMessageParser {
    override val command: String = "islock"

    override fun parseMessage(input: BitcoinInputMarkable): IMessage {
        input.mark()
        val payload = input.readBytes(input.count)
        input.reset()

        val inputsSize = input.readVarInt()
        val inputs = List(inputsSize.toInt()) {
            Outpoint(input)
        }
        val txHash = input.readBytes(32)
        val sign = input.readBytes(96)

        val requestPayload = BitcoinOutput()
        requestPayload.writeString("islock")
        requestPayload.writeVarInt(inputsSize)
        inputs.forEach {
            requestPayload.write(it.txHash)
            requestPayload.writeUnsignedInt(it.vout)
        }

        val requestId = HashUtils.doubleSha256(requestPayload.toByteArray())

        val hash = HashUtils.doubleSha256(payload)

        return ISLockMessage(inputs, txHash, sign, hash, requestId)
    }
}


