package io.cheddarswallet.bitcoincore.managers

import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters

interface IUnspentOutputProvider {
    fun getSpendableUtxo(filters: UtxoFilters): List<UnspentOutput>
}
