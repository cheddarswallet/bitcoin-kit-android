package io.cheddarswallet.bitcoincore.serializers

import io.cheddarswallet.bitcoincore.io.BitcoinInputMarkable
import io.cheddarswallet.bitcoincore.io.BitcoinOutput
import io.cheddarswallet.bitcoincore.models.TransactionOutput

object OutputSerializer {
    fun deserialize(input: BitcoinInputMarkable, vout: Long): TransactionOutput {
        val value = input.readLong()
        val scriptLength = input.readVarInt() // do not store
        val lockingScript = input.readBytes(scriptLength.toInt())
        val index = vout.toInt()

        return TransactionOutput(value, index, lockingScript)
    }

    fun serialize(output: TransactionOutput): ByteArray {
        return BitcoinOutput()
                .writeLong(output.value)
                .writeVarInt(output.lockingScript.size.toLong())
                .write(output.lockingScript)
                .toByteArray()
    }
}
