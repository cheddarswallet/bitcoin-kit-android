package io.cheddarswallet.dashkit.core

import io.cheddarswallet.bitcoincore.core.IHasher
import io.cheddarswallet.bitcoincore.utils.HashUtils

class SingleSha256Hasher : IHasher {
    override fun hash(data: ByteArray): ByteArray {
        return HashUtils.sha256(data)
    }
}
