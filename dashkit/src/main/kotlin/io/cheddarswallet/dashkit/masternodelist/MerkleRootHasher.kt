package io.cheddarswallet.dashkit.masternodelist

import io.cheddarswallet.bitcoincore.core.IHasher
import io.cheddarswallet.bitcoincore.utils.HashUtils
import io.cheddarswallet.dashkit.IMerkleHasher

class MerkleRootHasher: IHasher, IMerkleHasher {

    override fun hash(data: ByteArray): ByteArray {
        return HashUtils.doubleSha256(data)
    }

    override fun hash(first: ByteArray, second: ByteArray): ByteArray {
        return HashUtils.doubleSha256(first + second)
    }
}
