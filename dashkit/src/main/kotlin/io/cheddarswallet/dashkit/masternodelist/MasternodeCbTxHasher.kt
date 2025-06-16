package io.cheddarswallet.dashkit.masternodelist

import io.cheddarswallet.bitcoincore.core.HashBytes
import io.cheddarswallet.bitcoincore.core.IHasher
import io.cheddarswallet.dashkit.models.CoinbaseTransaction
import io.cheddarswallet.dashkit.models.CoinbaseTransactionSerializer

class MasternodeCbTxHasher(private val coinbaseTransactionSerializer: CoinbaseTransactionSerializer, private val hasher: IHasher) {

    fun hash(coinbaseTransaction: CoinbaseTransaction): HashBytes {
        val serialized = coinbaseTransactionSerializer.serialize(coinbaseTransaction)

        return HashBytes(hasher.hash(serialized))
    }

}
