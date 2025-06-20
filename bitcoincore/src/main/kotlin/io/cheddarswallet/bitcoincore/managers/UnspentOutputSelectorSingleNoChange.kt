package io.cheddarswallet.bitcoincore.managers

import io.cheddarswallet.bitcoincore.DustCalculator
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters
import io.cheddarswallet.bitcoincore.transactions.TransactionSizeCalculator
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType

class UnspentOutputSelectorSingleNoChange(
    private val calculator: TransactionSizeCalculator,
    private val dustCalculator: DustCalculator,
    private val unspentOutputProvider: IUnspentOutputProvider
) : IUnspentOutputSelector {

    override fun select(
        value: Long,
        memo: String?,
        feeRate: Int,
        outputScriptType: ScriptType,
        changeType: ScriptType,
        senderPay: Boolean,
        pluginDataOutputSize: Int,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): SelectedUnspentOutputInfo {
        val dust = dustCalculator.dust(outputScriptType, dustThreshold)
        if (value <= dust) {
            throw SendValueErrors.Dust
        }

        val sortedOutputs =
            unspentOutputProvider.getSpendableUtxo(filters).sortedWith(compareByDescending<UnspentOutput> {
                it.output.failedToSpend
            }.thenBy {
                it.output.value
            })

        if (sortedOutputs.isEmpty()) {
            throw SendValueErrors.EmptyOutputs
        }

        if (sortedOutputs.any { it.output.failedToSpend }) {
            throw SendValueErrors.HasOutputFailedToSpend
        }

        val params = UnspentOutputQueue.Parameters(
            value = value,
            senderPay = senderPay,
            memo = memo,
            fee = feeRate,
            outputsLimit = null,
            outputScriptType = outputScriptType,
            changeType = changeType,
            pluginDataOutputSize = pluginDataOutputSize,
            dustThreshold = dustThreshold,
            changeToFirstInput = changeToFirstInput,
        )
        val queue = UnspentOutputQueue(params, calculator, dustCalculator)

        //  try to find 1 unspent output with exactly matching value
        for (unspentOutput in sortedOutputs) {
            queue.set(listOf(unspentOutput))

            try {
                val info = queue.calculate()
                if (info.changeValue == null) {
                    return info
                }
            } catch (error: SendValueErrors) {
                //  ignore
            }
        }

        throw SendValueErrors.NoSingleOutput
    }
}
