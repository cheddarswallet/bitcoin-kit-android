package io.cheddarswallet.dashkit

interface IMerkleHasher {
    fun hash(first: ByteArray, second: ByteArray) : ByteArray
}
