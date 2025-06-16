package io.cheddarswallet.bitcoincore.rbf

import io.cheddarswallet.bitcoincore.models.Address
import io.cheddarswallet.bitcoincore.models.PublicKey

sealed class ReplacementType {
    object SpeedUp : ReplacementType()
    data class Cancel(val address: Address, val publicKey: PublicKey) : ReplacementType()
}
