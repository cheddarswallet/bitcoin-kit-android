package io.cheddarswallet.bitcoincore.transactions

import io.cheddarswallet.bitcoincore.core.IPluginData
import io.cheddarswallet.bitcoincore.core.IPublicKeyManager
import io.cheddarswallet.bitcoincore.core.IRecipientSetter
import io.cheddarswallet.bitcoincore.models.BitcoinSendInfo
import io.cheddarswallet.bitcoincore.models.TransactionDataSortType
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters
import io.cheddarswallet.bitcoincore.transactions.builder.InputSetter
import io.cheddarswallet.bitcoincore.transactions.builder.MutableTransaction
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType
import io.cheddarswallet.bitcoincore.utils.AddressConverterChain

class TransactionFeeCalculator(
    private val recipientSetter: IRecipientSetter,
    private val inputSetter: InputSetter,
    private val addressConverter: AddressConverterChain,
    private val publicKeyManager: IPublicKeyManager,
    private val changeScriptType: ScriptType,
) {

    fun sendInfo(
        value: Long,
        feeRate: Int,
        senderPay: Boolean,
        toAddress: String?,
        memo: String?,
        unspentOutputs: List<UnspentOutput>?,
        pluginData: Map<Byte, IPluginData>,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): BitcoinSendInfo {
        val mutableTransaction = MutableTransaction()

        recipientSetter.setRecipient(
            mutableTransaction = mutableTransaction,
            toAddress = toAddress ?: sampleAddress(),
            value = value,
            pluginData = pluginData,
            skipChecking = true,
            memo = memo
        )

        val outputInfo = inputSetter.setInputs(
            mutableTransaction = mutableTransaction,
            feeRate = feeRate,
            senderPay = senderPay,
            unspentOutputs = unspentOutputs,
            sortType = TransactionDataSortType.None,
            rbfEnabled = false,
            dustThreshold = dustThreshold,
            changeToFirstInput = changeToFirstInput,
            filters = filters,
        )

        val inputsTotalValue = mutableTransaction.inputsToSign.sumOf { it.previousOutput.value }
        val outputsTotalValue = mutableTransaction.recipientValue + mutableTransaction.changeValue

        return BitcoinSendInfo(
            unspentOutputs = outputInfo.unspentOutputs,
            fee = inputsTotalValue - outputsTotalValue,
            changeValue = outputInfo.changeInfo?.value,
            changeAddress = outputInfo.changeInfo?.address
        )
    }

    private fun sampleAddress(): String {
        return addressConverter.convert(
            publicKey = publicKeyManager.changePublicKey(),
            scriptType = changeScriptType
        ).stringValue
    }
}
