package io.cheddarswallet.bitcoincore.core

import io.cheddarswallet.bitcoincore.models.TransactionDataSortType
import io.cheddarswallet.bitcoincore.utils.Bip69Sorter
import io.cheddarswallet.bitcoincore.utils.ShuffleSorter
import io.cheddarswallet.bitcoincore.utils.StraightSorter

class TransactionDataSorterFactory : ITransactionDataSorterFactory {
    override fun sorter(type: TransactionDataSortType): ITransactionDataSorter {
        return when (type) {
            TransactionDataSortType.None -> StraightSorter()
            TransactionDataSortType.Shuffle -> ShuffleSorter()
            TransactionDataSortType.Bip69 -> Bip69Sorter()
        }
    }
}
