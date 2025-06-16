package io.cheddarswallet.bitcoincore.models

import io.cheddarswallet.bitcoincore.core.HashBytes
import io.cheddarswallet.bitcoincore.storage.BlockHeader
import io.cheddarswallet.bitcoincore.storage.FullTransaction

class MerkleBlock(val header: BlockHeader, val associatedTransactionHashes: Map<HashBytes, Boolean>) {

    var height: Int? = null
    var associatedTransactions = mutableListOf<FullTransaction>()
    val blockHash = header.hash

    val complete: Boolean
        get() = associatedTransactionHashes.size == associatedTransactions.size

}
