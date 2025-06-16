package io.cheddarswallet.bitcoincore.transactions.builder

import io.cheddarswallet.bitcoincore.core.IPluginData
import io.cheddarswallet.bitcoincore.core.IRecipientSetter
import io.cheddarswallet.bitcoincore.models.TransactionDataSortType
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters

class TransactionBuilder(
    private val recipientSetter: IRecipientSetter,
    private val outputSetter: OutputSetter,
    private val inputSetter: InputSetter,
    private val lockTimeSetter: LockTimeSetter
) {

    fun buildTransaction(
        toAddress: String,
        memo: String?,
        value: Long,
        feeRate: Int,
        senderPay: Boolean,
        sortType: TransactionDataSortType,
        unspentOutputs: List<UnspentOutput>?,
        pluginData: Map<Byte, IPluginData>,
        rbfEnabled: Boolean,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): MutableTransaction {
        val mutableTransaction = MutableTransaction()

        recipientSetter.setRecipient(mutableTransaction, toAddress, value, pluginData, false, memo)
        inputSetter.setInputs(
            mutableTransaction,
            feeRate,
            senderPay,
            unspentOutputs,
            sortType,
            rbfEnabled,
            dustThreshold,
            changeToFirstInput,
            filters,
        )
        lockTimeSetter.setLockTime(mutableTransaction)

        outputSetter.setOutputs(mutableTransaction, sortType)

        return mutableTransaction
    }

    fun buildTransaction(
        unspentOutput: UnspentOutput,
        toAddress: String,
        memo: String?,
        feeRate: Int,
        sortType: TransactionDataSortType,
        rbfEnabled: Boolean
    ): MutableTransaction {
        val mutableTransaction = MutableTransaction(false)

        recipientSetter.setRecipient(
            mutableTransaction,
            toAddress,
            unspentOutput.output.value,
            mapOf(),
            false,
            memo
        )
        inputSetter.setInputs(mutableTransaction, unspentOutput, feeRate, rbfEnabled)
        lockTimeSetter.setLockTime(mutableTransaction)

        outputSetter.setOutputs(mutableTransaction, sortType)

        return mutableTransaction
    }

    open class BuilderException : Exception() {
        class FeeMoreThanValue : BuilderException()
        class NotSupportedScriptType : BuilderException()
    }
}
