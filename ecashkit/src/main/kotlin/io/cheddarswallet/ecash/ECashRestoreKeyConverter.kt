package io.cheddarswallet.ecash

import io.cheddarswallet.bitcoincore.core.scriptType
import io.cheddarswallet.bitcoincore.extensions.toHexString
import io.cheddarswallet.bitcoincore.managers.IRestoreKeyConverter
import io.cheddarswallet.bitcoincore.models.PublicKey
import io.cheddarswallet.bitcoincore.utils.IAddressConverter
import io.cheddarswallet.hdwalletkit.HDWallet

class ECashRestoreKeyConverter(
    private val addressConverter: IAddressConverter,
    private val purpose: HDWallet.Purpose
) : IRestoreKeyConverter {
    override fun keysForApiRestore(publicKey: PublicKey): List<String> {
        return listOf(
            publicKey.publicKeyHash.toHexString(),
            addressConverter.convert(publicKey, purpose.scriptType).stringValue
        )
    }

    override fun bloomFilterElements(publicKey: PublicKey): List<ByteArray> {
        return listOf(publicKey.publicKeyHash, publicKey.publicKey)
    }
}
