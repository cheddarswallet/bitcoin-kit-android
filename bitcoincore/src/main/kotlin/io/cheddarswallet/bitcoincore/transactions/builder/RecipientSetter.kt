package io.cheddarswallet.bitcoincore.transactions.builder

import io.cheddarswallet.bitcoincore.core.IPluginData
import io.cheddarswallet.bitcoincore.core.IRecipientSetter
import io.cheddarswallet.bitcoincore.core.PluginManager
import io.cheddarswallet.bitcoincore.utils.IAddressConverter

class RecipientSetter(
        private val addressConverter: IAddressConverter,
        private val pluginManager: PluginManager
) : IRecipientSetter {

    override fun setRecipient(
        mutableTransaction: MutableTransaction,
        toAddress: String,
        value: Long,
        pluginData: Map<Byte, IPluginData>,
        skipChecking: Boolean,
        memo: String?
    ) {
        mutableTransaction.recipientAddress = addressConverter.convert(toAddress)
        mutableTransaction.recipientValue = value
        mutableTransaction.memo = memo

        pluginManager.processOutputs(mutableTransaction, pluginData, skipChecking)
    }

}
