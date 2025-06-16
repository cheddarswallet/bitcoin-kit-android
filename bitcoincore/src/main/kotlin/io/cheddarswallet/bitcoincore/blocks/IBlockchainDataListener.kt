package io.cheddarswallet.bitcoincore.blocks

import io.cheddarswallet.bitcoincore.models.Block
import io.cheddarswallet.bitcoincore.models.Transaction

interface IBlockchainDataListener {
    fun onBlockInsert(block: Block)
    fun onTransactionsUpdate(inserted: List<Transaction>, updated: List<Transaction>, block: Block?)
    fun onTransactionsDelete(hashes: List<String>)
}
