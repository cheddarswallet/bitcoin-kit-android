package io.cheddarswallet.bitcoincore.core

interface IHasher {
    fun hash(data: ByteArray) : ByteArray
}
