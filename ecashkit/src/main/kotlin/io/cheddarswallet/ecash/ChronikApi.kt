package io.cheddarswallet.ecash

import chronik.Chronik
import io.cheddarswallet.bitcoincore.core.IApiTransactionProvider
import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.managers.ApiManager
import io.cheddarswallet.bitcoincore.apisync.model.TransactionItem
import io.cheddarswallet.bitcoincore.apisync.model.AddressItem

class ChronikApi : IApiTransactionProvider {
    private val apiManager = ApiManager("https://chronik.fabien.cash")

    override fun transactions(addresses: List<String>, stopHeight: Int?): List<TransactionItem> {
        val transactionItems = mutableListOf<TransactionItem>()

        for (address in addresses) {
            try {
                var page = 0

                while (true) {
                    Thread.sleep(10)
                    val get = apiManager.get("script/p2pkh/$address/history?page=$page")
                    val txHistoryPage = Chronik.TxHistoryPage.parseFrom(get)
                    transactionItems.addAll(
                        txHistoryPage.txsList.map {
                            TransactionItem(
                                blockHash = it.block.hash.toByteArray().toReversedHex(),
                                blockHeight = it.block.height,
                                addressItems = it.outputsList.map { txOutput ->
                                    AddressItem(
                                        script = txOutput.outputScript.toByteArray().toReversedHex(),
                                        address = ""
                                    )
                                }.toMutableList()
                            )
                        }
                    )

                    page++

                    if (txHistoryPage.numPages < page + 1)
                        break
                }
            } catch (e: Throwable) {
                continue
            }
        }

        return transactionItems
    }
}
