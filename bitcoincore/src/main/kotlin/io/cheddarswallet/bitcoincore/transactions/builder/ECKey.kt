package io.cheddarswallet.bitcoincore.transactions.builder

import io.cheddarswallet.bitcoincore.crypto.schnorr.Schnorr
import io.cheddarswallet.hdwalletkit.ECKey

fun ECKey.signSchnorr(input: ByteArray, auxRand: ByteArray = ByteArray(32)): ByteArray {
    return Schnorr.sign(input, privKeyBytes, auxRand)
}

fun ECKey.verifySchnorr(input: ByteArray, signature: ByteArray): Boolean {
    return Schnorr.verify(input, pubKeyXCoord, signature)
}
