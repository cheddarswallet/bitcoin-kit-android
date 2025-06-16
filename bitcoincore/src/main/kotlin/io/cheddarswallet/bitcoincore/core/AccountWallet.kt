package io.cheddarswallet.bitcoincore.core

import io.cheddarswallet.bitcoincore.models.PublicKey
import io.cheddarswallet.hdwalletkit.HDKey
import io.cheddarswallet.hdwalletkit.HDWallet.*
import io.cheddarswallet.hdwalletkit.HDWalletAccount

class AccountWallet(private val hdWallet: HDWalletAccount, override val gapLimit: Int): IPrivateWallet, IAccountWallet {

    override fun publicKey(index: Int, external: Boolean): PublicKey {
        val pubKey = hdWallet.publicKey(index, if (external) Chain.EXTERNAL else Chain.INTERNAL)
        return PublicKey(0, index, external, pubKey.publicKey, pubKey.publicKeyHash)
    }

    override fun publicKeys(indices: IntRange, external: Boolean): List<PublicKey> {
        val hdPublicKeys = hdWallet.publicKeys(indices, if (external) Chain.EXTERNAL else Chain.INTERNAL)

        if (hdPublicKeys.size != indices.count()) {
            throw Wallet.HDWalletError.PublicKeysDerivationFailed()
        }

        return indices.mapIndexed { position, index ->
            val hdPublicKey = hdPublicKeys[position]
            PublicKey(0, index, external, hdPublicKey.publicKey, hdPublicKey.publicKeyHash)
        }
    }

    override fun privateKey(account: Int, index: Int, external: Boolean): HDKey {
       return hdWallet.privateKey(index, if (external) Chain.EXTERNAL else Chain.INTERNAL)
    }
}
