package io.cheddarswallet.bitcoincore.transactions.builder

import io.cheddarswallet.bitcoincore.DustCalculator
import io.cheddarswallet.bitcoincore.core.IPublicKeyManager
import io.cheddarswallet.bitcoincore.core.ITransactionDataSorterFactory
import io.cheddarswallet.bitcoincore.core.PluginManager
import io.cheddarswallet.bitcoincore.managers.IUnspentOutputSelector
import io.cheddarswallet.bitcoincore.managers.SelectedUnspentOutputInfo
import io.cheddarswallet.bitcoincore.managers.SendValueErrors
import io.cheddarswallet.bitcoincore.managers.UnspentOutputQueue
import io.cheddarswallet.bitcoincore.models.Address
import io.cheddarswallet.bitcoincore.models.TransactionDataSortType
import io.cheddarswallet.bitcoincore.models.TransactionInput
import io.cheddarswallet.bitcoincore.storage.InputToSign
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters
import io.cheddarswallet.bitcoincore.transactions.TransactionSizeCalculator
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType
import io.cheddarswallet.bitcoincore.utils.IAddressConverter

class InputSetter(
    private val unspentOutputSelector: IUnspentOutputSelector,
    private val publicKeyManager: IPublicKeyManager,
    private val addressConverter: IAddressConverter,
    private val changeScriptType: ScriptType,
    private val transactionSizeCalculator: TransactionSizeCalculator,
    private val pluginManager: PluginManager,
    private val dustCalculator: DustCalculator,
    private val transactionDataSorterFactory: ITransactionDataSorterFactory
) {
    fun setInputs(
        mutableTransaction: MutableTransaction,
        unspentOutput: UnspentOutput,
        feeRate: Int,
        rbfEnabled: Boolean
    ) {
        if (unspentOutput.output.scriptType != ScriptType.P2SH) {
            throw TransactionBuilder.BuilderException.NotSupportedScriptType()
        }

        // Calculate fee
        val transactionSize =
            transactionSizeCalculator.transactionSize(
                previousOutputs = listOf(unspentOutput.output),
                outputs = listOf(mutableTransaction.recipientAddress.scriptType),
                memo = mutableTransaction.memo,
                pluginDataOutputSize = 0
            )

        val fee = transactionSize * feeRate
        val value = unspentOutput.output.value
        if (value < fee) {
            throw TransactionBuilder.BuilderException.FeeMoreThanValue()
        }
        mutableTransaction.addInput(inputToSign(unspentOutput, rbfEnabled))
        mutableTransaction.recipientValue = value - fee
    }

    @Throws(SendValueErrors::class)
    fun setInputs(
        mutableTransaction: MutableTransaction,
        feeRate: Int,
        senderPay: Boolean,
        unspentOutputs: List<UnspentOutput>?,
        sortType: TransactionDataSortType,
        rbfEnabled: Boolean,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): OutputInfo {
        val unspentOutputInfo: SelectedUnspentOutputInfo
        if (unspentOutputs != null) {
            val params = UnspentOutputQueue.Parameters(
                value = mutableTransaction.recipientValue,
                senderPay = senderPay,
                memo = mutableTransaction.memo,
                fee = feeRate,
                outputsLimit = null,
                outputScriptType = mutableTransaction.recipientAddress.scriptType,
                changeType = changeScriptType,  // Assuming changeScriptType is defined somewhere
                pluginDataOutputSize = mutableTransaction.getPluginDataOutputSize(),
                dustThreshold = dustThreshold,
                changeToFirstInput = changeToFirstInput
            )
            val queue = UnspentOutputQueue(
                params,
                transactionSizeCalculator,
                dustCalculator,
            )
            queue.set(unspentOutputs)
            unspentOutputInfo = queue.calculate()
        } else {
            val value = mutableTransaction.recipientValue
            unspentOutputInfo = unspentOutputSelector.select(
                value,
                mutableTransaction.memo,
                feeRate,
                mutableTransaction.recipientAddress.scriptType,  // Assuming changeScriptType is defined somewhere
                changeScriptType,
                senderPay,
                mutableTransaction.getPluginDataOutputSize(),
                dustThreshold,
                changeToFirstInput,
                filters
            )
        }

        val sortedUnspentOutputs =
            transactionDataSorterFactory.sorter(sortType).sortUnspents(unspentOutputInfo.outputs)

        for (unspentOutput in sortedUnspentOutputs) {
            mutableTransaction.addInput(inputToSign(unspentOutput, rbfEnabled))
        }

        mutableTransaction.recipientValue = unspentOutputInfo.recipientValue

        // Add change output if needed
        var changeInfo: ChangeInfo? = null
        unspentOutputInfo.changeValue?.let { changeValue ->
            val firstOutput = unspentOutputInfo.outputs.firstOrNull()
            val changeAddress = if (changeToFirstInput && firstOutput != null) {
                addressConverter.convert(firstOutput.publicKey, firstOutput.output.scriptType)
            } else {
                val changePubKey = publicKeyManager.changePublicKey()
                addressConverter.convert(changePubKey, changeScriptType)
            }

            mutableTransaction.changeAddress = changeAddress
            mutableTransaction.changeValue = changeValue
            changeInfo = ChangeInfo(address = changeAddress, value = changeValue)
        }

        pluginManager.processInputs(mutableTransaction)
        return OutputInfo(
            unspentOutputs = sortedUnspentOutputs,
            changeInfo = changeInfo
        )
    }

    private fun inputToSign(unspentOutput: UnspentOutput, rbfEnabled: Boolean): InputToSign {
        val previousOutput = unspentOutput.output
        val sequence = if (rbfEnabled) {
            0x00
        } else {
            0xfffffffe
        }
        val transactionInput = TransactionInput(previousOutput.transactionHash, previousOutput.index.toLong(), sequence = sequence)

        return InputToSign(transactionInput, previousOutput, unspentOutput.publicKey)
    }

    data class ChangeInfo(
        val address: Address,
        val value: Long
    )

    data class OutputInfo(
        val unspentOutputs: List<UnspentOutput>,
        val changeInfo: ChangeInfo?
    )
}
