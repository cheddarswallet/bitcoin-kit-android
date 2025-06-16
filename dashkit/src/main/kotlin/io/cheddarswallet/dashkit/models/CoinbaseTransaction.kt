package io.cheddarswallet.dashkit.models

import io.cheddarswallet.bitcoincore.io.BitcoinInputMarkable
import io.cheddarswallet.bitcoincore.serializers.TransactionSerializer

class CoinbaseTransaction(input: BitcoinInputMarkable) {
    val transaction = TransactionSerializer.deserialize(input)
    val coinbaseTransactionSize: Long
    val version: Int
    val height: Long
    val merkleRootMNList: ByteArray
    val merkleRootQuorums: ByteArray?
    var bestCLHeightDiff: Long? = null
    var bestCLSignature: ByteArray? = null
    var creditPoolBalance: Long? = null

    init {
        coinbaseTransactionSize = input.readVarInt()

        version = input.readUnsignedShort()
        height = input.readUnsignedInt()
        merkleRootMNList = input.readBytes(32)
        merkleRootQuorums = when {
            version >= 2 -> input.readBytes(32)
            else -> null
        }

        if (version >= 3) {
            bestCLHeightDiff = input.readVarInt()
            bestCLSignature = input.readBytes(96)
            creditPoolBalance = input.readLong()
        }
    }
}
