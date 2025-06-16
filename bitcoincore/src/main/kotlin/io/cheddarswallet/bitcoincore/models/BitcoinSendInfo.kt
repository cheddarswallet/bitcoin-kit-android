package io.cheddarswallet.bitcoincore.models

import io.cheddarswallet.bitcoincore.storage.UnspentOutput

data class BitcoinSendInfo(
    val unspentOutputs: List<UnspentOutput>,
    val fee: Long,
    val changeValue: Long?,
    val changeAddress: Address?
)
