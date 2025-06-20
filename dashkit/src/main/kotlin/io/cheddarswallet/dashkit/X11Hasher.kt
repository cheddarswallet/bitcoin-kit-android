package io.cheddarswallet.dashkit

import fr.cryptohash.*
import io.cheddarswallet.bitcoincore.core.IHasher
import java.util.*

class X11Hasher : IHasher {
    private val algorithms = listOf(
            BLAKE512(),
            BMW512(),
            Groestl512(),
            Skein512(),
            JH512(),
            Keccak512(),
            Luffa512(),
            CubeHash512(),
            SHAvite512(),
            SIMD512(),
            ECHO512()
    )

    override fun hash(data: ByteArray): ByteArray {
        var hash = data

        algorithms.forEach {
            hash = it.digest(hash)
        }

        return hash.copyOfRange(0, 32)
    }
}
