package io.cheddarswallet.bitcoincore.apisync.blockchair

import io.cheddarswallet.bitcoincore.apisync.model.BlockHeaderItem

class BlockchairLastBlockProvider(
    private val blockchairApi: BlockchairApi
) {
    fun lastBlockHeader(): BlockHeaderItem {
        return blockchairApi.lastBlockHeader()
    }
}
