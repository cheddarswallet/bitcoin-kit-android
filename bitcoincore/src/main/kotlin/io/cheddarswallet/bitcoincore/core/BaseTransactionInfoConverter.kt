package io.cheddarswallet.bitcoincore.core

import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.io.BitcoinInput
import io.cheddarswallet.bitcoincore.models.InvalidTransaction
import io.cheddarswallet.bitcoincore.models.Transaction
import io.cheddarswallet.bitcoincore.models.TransactionInfo
import io.cheddarswallet.bitcoincore.models.TransactionInputInfo
import io.cheddarswallet.bitcoincore.models.TransactionMetadata
import io.cheddarswallet.bitcoincore.models.TransactionOutput
import io.cheddarswallet.bitcoincore.models.TransactionOutputInfo
import io.cheddarswallet.bitcoincore.models.TransactionStatus
import io.cheddarswallet.bitcoincore.models.rbfEnabled
import io.cheddarswallet.bitcoincore.storage.FullTransactionInfo
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType
import java.io.ByteArrayInputStream

class BaseTransactionInfoConverter(private val pluginManager: PluginManager) {

    fun transactionInfo(fullTransaction: FullTransactionInfo): TransactionInfo {
        val transaction = fullTransaction.header

        if (transaction.status == Transaction.Status.INVALID) {
            (transaction as? InvalidTransaction)?.let {
                return getInvalidTransactionInfo(it, fullTransaction.metadata)
            }
        }

        val inputsInfo = mutableListOf<TransactionInputInfo>()
        val outputsInfo = mutableListOf<TransactionOutputInfo>()

        fullTransaction.inputs.forEach { input ->
            var mine = false
            var value: Long? = null

            if (input.previousOutput != null) {
                value = input.previousOutput.value
                if (input.previousOutput.publicKeyPath != null) {
                    mine = true
                }
            }

            inputsInfo.add(TransactionInputInfo(mine, value, input.input.address))
        }

        fullTransaction.outputs.forEach { output ->
            val outputInfo = TransactionOutputInfo(mine = output.publicKeyPath != null,
                    changeOutput = output.changeOutput,
                    value = output.value,
                    address = output.address,
                    memo = parseMemo(output),
                    pluginId = output.pluginId,
                    pluginDataString = output.pluginData,
                    pluginData = pluginManager.parsePluginData(output, transaction.timestamp))

            outputsInfo.add(outputInfo)
        }

        val rbfEnabled = fullTransaction.inputs.any { it.input.rbfEnabled }

        return TransactionInfo(
            uid = transaction.uid,
            transactionHash = transaction.hash.toReversedHex(),
            transactionIndex = transaction.order,
            inputs = inputsInfo,
            outputs = outputsInfo,
            amount = fullTransaction.metadata.amount,
            type = fullTransaction.metadata.type,
            fee = fullTransaction.metadata.fee,
            blockHeight = fullTransaction.block?.height,
            timestamp = transaction.timestamp,
            status = TransactionStatus.getByCode(transaction.status) ?: TransactionStatus.NEW,
            conflictingTxHash = transaction.conflictingTxHash?.toReversedHex(),
            rbfEnabled = rbfEnabled
        )
    }

    private fun parseMemo(output: TransactionOutput): String? {
        if (output.scriptType != ScriptType.NULL_DATA) return null
        val payload = output.lockingScriptPayload ?: return null
        if (payload.isEmpty()) return null

        return try {
            val input = BitcoinInput(ByteArrayInputStream(payload))
            input.readByte() // op_return
            input.readString()
        } catch (e: Throwable) {
            null
        }
    }

    private fun getInvalidTransactionInfo(
        transaction: InvalidTransaction,
        metadata: TransactionMetadata
    ): TransactionInfo {
        return try {
            TransactionInfo(transaction.serializedTxInfo)
        } catch (ex: Exception) {
            TransactionInfo(
                uid = transaction.uid,
                transactionHash = transaction.hash.toReversedHex(),
                transactionIndex = transaction.order,
                inputs = listOf(),
                outputs = listOf(),
                amount = metadata.amount,
                type = metadata.type,
                fee = metadata.fee,
                blockHeight = null,
                timestamp = transaction.timestamp,
                status = TransactionStatus.INVALID
            )
        }
    }

}
