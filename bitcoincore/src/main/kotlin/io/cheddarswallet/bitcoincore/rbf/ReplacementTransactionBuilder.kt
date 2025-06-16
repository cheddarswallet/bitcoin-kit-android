package io.cheddarswallet.bitcoincore.rbf

import io.cheddarswallet.bitcoincore.DustCalculator
import io.cheddarswallet.bitcoincore.core.IPublicKeyManager
import io.cheddarswallet.bitcoincore.core.IStorage
import io.cheddarswallet.bitcoincore.core.PluginManager
import io.cheddarswallet.bitcoincore.extensions.toReversedByteArray
import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.managers.UnspentOutputProvider
import io.cheddarswallet.bitcoincore.models.Address
import io.cheddarswallet.bitcoincore.models.PublicKey
import io.cheddarswallet.bitcoincore.models.TransactionInput
import io.cheddarswallet.bitcoincore.models.TransactionOutput
import io.cheddarswallet.bitcoincore.models.TransactionType
import io.cheddarswallet.bitcoincore.models.rbfEnabled
import io.cheddarswallet.bitcoincore.storage.FullTransactionInfo
import io.cheddarswallet.bitcoincore.storage.InputToSign
import io.cheddarswallet.bitcoincore.storage.InputWithPreviousOutput
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters
import io.cheddarswallet.bitcoincore.transactions.TransactionConflictsResolver
import io.cheddarswallet.bitcoincore.transactions.TransactionSizeCalculator
import io.cheddarswallet.bitcoincore.transactions.builder.LockTimeSetter
import io.cheddarswallet.bitcoincore.transactions.builder.MutableTransaction
import io.cheddarswallet.bitcoincore.transactions.extractors.TransactionMetadataExtractor
import io.cheddarswallet.bitcoincore.utils.ShuffleSorter
import kotlin.math.min

class ReplacementTransactionBuilder(
    private val storage: IStorage,
    private val sizeCalculator: TransactionSizeCalculator,
    private val dustCalculator: DustCalculator,
    private val metadataExtractor: TransactionMetadataExtractor,
    private val pluginManager: PluginManager,
    private val unspentOutputProvider: UnspentOutputProvider,
    private val publicKeyManager: IPublicKeyManager,
    private val conflictsResolver: TransactionConflictsResolver,
    private val lockTimeSetter: LockTimeSetter
) {

    private fun replacementTransaction(
        minFee: Long,
        minFeeRate: Int,
        utxo: List<TransactionOutput>,
        fixedOutputs: List<TransactionOutput>,
        outputs: List<TransactionOutput>
    ): Pair<List<TransactionOutput>, /*Fee*/Long>? {

        var minFee = minFee
        val size = sizeCalculator.transactionSize(previousOutputs = utxo, outputs = fixedOutputs + outputs)

        val inputsValue = utxo.sumOf { it.value }
        val outputsValue = (fixedOutputs + outputs).sumOf { it.value }
        val fee = inputsValue - outputsValue
        val feeRate = fee / size

        if (feeRate < minFeeRate) {
            minFee = minFeeRate * size
        }

        if (fee >= minFee) {
            return Pair(outputs, fee)
        }

        if (outputs.isEmpty()) {
            return null
        }

        val output = TransactionOutput(outputs.first())
        output.value = output.value - (minFee - fee)

        if (output.value > dustCalculator.dust(output.scriptType, null)) {
            return Pair(listOf(output) + outputs.drop(1), fee)
        }

        return null
    }

    private fun incrementedSequence(inputWithPreviousOutput: InputWithPreviousOutput): Long {
        val input = inputWithPreviousOutput.input

        if (inputWithPreviousOutput.previousOutput?.pluginId != null) {
            return pluginManager.incrementedSequence(inputWithPreviousOutput)
        }

        return min(input.sequence + 1, 0xFFFFFFFF)
    }

    private fun inputToSign(previousOutput: TransactionOutput, prevOutputPublicKey: PublicKey, sequence: Long): InputToSign {
        val transactionInput = TransactionInput(previousOutput.transactionHash, previousOutput.index.toLong(), sequence = sequence)

        return InputToSign(transactionInput, previousOutput, prevOutputPublicKey)
    }

    private fun setInputs(
        mutableTransaction: MutableTransaction,
        originalInputs: List<InputWithPreviousOutput>,
        additionalInputs: List<UnspentOutput>
    ) {
        additionalInputs.map { utxo ->
            mutableTransaction.addInput(inputToSign(previousOutput = utxo.output, prevOutputPublicKey = utxo.publicKey, sequence = 0x0))
        }

        pluginManager.processInputs(mutableTransaction)

        originalInputs.map { inputWithPreviousOutput ->
            val previousOutput =
                inputWithPreviousOutput.previousOutput ?: throw BuildError.InvalidTransaction("No previous output of original transaction")
            val publicKey = previousOutput.publicKeyPath?.let { publicKeyManager.getPublicKeyByPath(it) }
                ?: throw BuildError.InvalidTransaction("No public key of original transaction")
            mutableTransaction.addInput(inputToSign(previousOutput = previousOutput, publicKey, incrementedSequence(inputWithPreviousOutput)))
        }
    }

    private fun setOutputs(mutableTransaction: MutableTransaction, outputs: List<TransactionOutput>) {
        val sorted = ShuffleSorter().sortOutputs(outputs)
        sorted.forEachIndexed { index, transactionOutput ->
            transactionOutput.index = index
        }

        mutableTransaction.outputs = sorted
    }

    private fun speedUpReplacement(
        originalFullInfo: FullTransactionInfo,
        minFee: Long,
        originalFeeRate: Int,
        fixedUtxo: List<TransactionOutput>
    ): MutableTransaction? {
        // If an output has a pluginId, it most probably has a time-locked value and it shouldn't be altered.
        var fixedOutputs = originalFullInfo.outputs.filter { it.publicKeyPath == null || it.pluginId != null }
        val myOutputs = originalFullInfo.outputs.filter { it.publicKeyPath != null && it.pluginId == null }
        val myChangeOutputs = myOutputs.filter { it.changeOutput }.sortedBy { it.value }
        val myExternalOutputs = myOutputs.filter { !it.changeOutput }.sortedBy { it.value }

        var sortedOutputs = myChangeOutputs + myExternalOutputs
        if (fixedOutputs.isEmpty() && sortedOutputs.isNotEmpty()) {
            fixedOutputs = listOf(sortedOutputs.last())
            sortedOutputs = sortedOutputs.dropLast(1)
        }
        val unusedUtxo = unspentOutputProvider.getConfirmedSpendableUtxo(UtxoFilters()).sortedBy { it.output.value }
        var optimalReplacement: Triple</*inputs*/ List<UnspentOutput>, /*outputs*/ List<TransactionOutput>, /*fee*/ Long>? = null

        var utxoCount = 0
        do {
            val utxo = unusedUtxo.take(utxoCount)
            var outputsCount = sortedOutputs.size

            do {
                val outputs = sortedOutputs.takeLast(outputsCount)

                replacementTransaction(
                    minFee = minFee,
                    minFeeRate = originalFeeRate,
                    utxo = fixedUtxo + utxo.map { it.output },
                    fixedOutputs = fixedOutputs,
                    outputs = outputs
                )?.let { (outputs, fee) ->
                    optimalReplacement.let { _optimalReplacement ->
                        if (_optimalReplacement != null) {
                            if (_optimalReplacement.third > fee) {
                                optimalReplacement = Triple(utxo, outputs, fee)
                            }
                        } else {
                            optimalReplacement = Triple(utxo, outputs, fee)
                        }
                    }
                }

                outputsCount--
            } while (outputsCount >= 0)

            utxoCount++
        } while (utxoCount <= unusedUtxo.size)

        return optimalReplacement?.let { (inputs, outputs, _) ->
            val mutableTransaction = MutableTransaction()
            setInputs(
                mutableTransaction = mutableTransaction,
                originalInputs = originalFullInfo.inputs,
                additionalInputs = inputs
            )
            setOutputs(
                mutableTransaction = mutableTransaction,
                outputs = fixedOutputs + outputs
            )
            lockTimeSetter.setLockTime(mutableTransaction)
            mutableTransaction
        }
    }

    private fun cancelReplacement(
        originalFullInfo: FullTransactionInfo,
        minFee: Long,
        originalFeeRate: Int,
        fixedUtxo: List<TransactionOutput>,
        userAddress: Address,
        publicKey: PublicKey
    ): MutableTransaction? {
        val unusedUtxo = unspentOutputProvider.getConfirmedSpendableUtxo(UtxoFilters()).sortedBy { it.output.value }
        val originalInputsValue = fixedUtxo.sumOf { it.value }

        var optimalReplacement: Triple</*inputs*/ List<UnspentOutput>, /*outputs*/ List<TransactionOutput>, /*fee*/ Long>? = null

        var utxoCount = 0
        val outputs = listOf(
            TransactionOutput(
                value = originalInputsValue - minFee,
                index = 0,
                script = userAddress.lockingScript,
                type = userAddress.scriptType,
                address = userAddress.stringValue,
                lockingScriptPayload = userAddress.lockingScriptPayload,
                publicKey = publicKey
            )
        )
        do {
            if (originalInputsValue - minFee < dustCalculator.dust(userAddress.scriptType, null)) {
                utxoCount++
                continue
            }

            val utxo = unusedUtxo.take(utxoCount)

            replacementTransaction(
                minFee = minFee,
                minFeeRate = originalFeeRate,
                utxo = fixedUtxo + utxo.map { it.output },
                fixedOutputs = listOf(),
                outputs = outputs
            )?.let { (outputs, fee) ->
                optimalReplacement.let { _optimalReplacement ->
                    if (_optimalReplacement != null) {
                        if (_optimalReplacement.third > fee) {
                            optimalReplacement = Triple(utxo, outputs, fee)
                        }
                    } else {
                        optimalReplacement = Triple(utxo, outputs, fee)
                    }
                }
            }

            utxoCount++
        } while (utxoCount <= unusedUtxo.size)


        return optimalReplacement?.let { (inputs, outputs, _) ->
            val mutableTransaction = MutableTransaction()
            setInputs(
                mutableTransaction = mutableTransaction,
                originalInputs = originalFullInfo.inputs,
                additionalInputs = inputs
            )
            setOutputs(
                mutableTransaction = mutableTransaction,
                outputs = outputs
            )
            mutableTransaction
        }
    }

    fun replacementTransaction(
        transactionHash: String,
        minFee: Long,
        type: ReplacementType
    ): Triple<MutableTransaction, FullTransactionInfo, List<String>> {
        val originalFullInfo = storage.getFullTransactionInfo(transactionHash.toReversedByteArray())
            ?: throw BuildError.InvalidTransaction("No FullTransactionInfo")
        check(originalFullInfo.block == null) { throw BuildError.InvalidTransaction("Transaction already in block") }

        val originalFee = originalFullInfo.metadata.fee
        checkNotNull(originalFee) { throw BuildError.InvalidTransaction("No fee for original transaction") }

        check(originalFullInfo.metadata.type != TransactionType.Incoming) { throw BuildError.InvalidTransaction("Can replace only outgoing transaction") }

        val fixedUtxo = originalFullInfo.inputs.mapNotNull { it.previousOutput }

        check(originalFullInfo.inputs.size == fixedUtxo.size) { throw BuildError.NoPreviousOutput }

        check(originalFullInfo.inputs.any { it.input.rbfEnabled }) { throw BuildError.RbfNotEnabled }

        val originalSize = sizeCalculator.transactionSize(previousOutputs = fixedUtxo, outputs = originalFullInfo.outputs)

        val originalFeeRate = (originalFee / originalSize).toInt()
        val descendantTransactions = storage.getDescendantTransactionsFullInfo(transactionHash.toReversedByteArray())
        val absoluteFee = descendantTransactions.sumOf { it.metadata.fee ?: 0 }

        check(
            descendantTransactions.all { it.header.conflictingTxHash == null } &&
                    !conflictsResolver.isTransactionReplaced(originalFullInfo.fullTransaction)
        ) {
            throw BuildError.InvalidTransaction("Already replaced")
        }

        check(absoluteFee <= minFee) { throw BuildError.FeeTooLow }

        val mutableTransaction = when (type) {
            ReplacementType.SpeedUp -> speedUpReplacement(
                originalFullInfo,
                minFee,
                originalFeeRate,
                fixedUtxo
            )
            is ReplacementType.Cancel -> cancelReplacement(
                originalFullInfo,
                minFee,
                originalFeeRate,
                fixedUtxo,
                type.address,
                type.publicKey
            )
        }

        checkNotNull(mutableTransaction) { throw BuildError.UnableToReplace }

        val fullTransaction = mutableTransaction.build()
        metadataExtractor.extract(fullTransaction)
        val metadata = fullTransaction.metadata

        return Triple(
            mutableTransaction,
            FullTransactionInfo(
                block = null,
                header = mutableTransaction.transaction,
                inputs = mutableTransaction.inputsToSign.map {
                    InputWithPreviousOutput(it.input, it.previousOutput)
                },
                outputs = mutableTransaction.outputs,
                metadata = metadata
            ),
            descendantTransactions.map { it.metadata.transactionHash.toReversedHex() }
        )
    }

    fun replacementInfo(
        transactionHash: String,
        type: ReplacementType
    ): ReplacementTransactionInfo? {
        val originalFullInfo = storage.getFullTransactionInfo(transactionHash.toReversedByteArray()) ?: return null
        check(originalFullInfo.block == null) { throw BuildError.InvalidTransaction("Transaction already in block") }
        check(originalFullInfo.metadata.type != TransactionType.Incoming) { throw BuildError.InvalidTransaction("Can replace only outgoing transaction") }

        val originalFee = originalFullInfo.metadata.fee
        checkNotNull(originalFee) { throw BuildError.InvalidTransaction("No fee for original transaction") }

        val fixedUtxo = originalFullInfo.inputs.mapNotNull { it.previousOutput }
        check(originalFullInfo.inputs.size == fixedUtxo.size) { throw BuildError.NoPreviousOutput }

        val descendantTransactions = storage.getDescendantTransactionsFullInfo(transactionHash.toReversedByteArray())
        val absoluteFee = descendantTransactions.sumOf { it.metadata.fee ?: 0 }

        check(
            descendantTransactions.all { it.header.conflictingTxHash == null } &&
                    !conflictsResolver.isTransactionReplaced(originalFullInfo.fullTransaction)
        ) {
            return null
        }

        val replacementTxMinSize: Long
        val removableOutputsValue: Long

        when (type) {
            ReplacementType.SpeedUp -> {
                var fixedOutputs = originalFullInfo.outputs.filter { it.publicKeyPath == null || it.pluginId != null }
                val myOutputs = originalFullInfo.outputs.filter { it.publicKeyPath != null && it.pluginId == null }
                val myChangeOutputs = myOutputs.filter { it.changeOutput }.sortedBy { it.value }
                val myExternalOutputs = myOutputs.filter { !it.changeOutput }.sortedBy { it.value }

                var sortedOutputs = myChangeOutputs + myExternalOutputs
                if (fixedOutputs.isEmpty() && sortedOutputs.isNotEmpty()) {
                    fixedOutputs = listOf(sortedOutputs.last())
                    sortedOutputs = sortedOutputs.dropLast(1)
                }

                replacementTxMinSize = sizeCalculator.transactionSize(fixedUtxo, fixedOutputs)
                removableOutputsValue = sortedOutputs.sumOf { it.value }
            }

            is ReplacementType.Cancel -> {
                val dustValue = dustCalculator.dust(type.address.scriptType, null).toLong()
                val fixedOutputs = listOf(
                    TransactionOutput(
                        value = dustValue,
                        index = 0,
                        script = type.address.lockingScript,
                        type = type.address.scriptType,
                        address = type.address.stringValue,
                        lockingScriptPayload = type.address.lockingScriptPayload
                    )
                )
                replacementTxMinSize = sizeCalculator.transactionSize(fixedUtxo, fixedOutputs)
                removableOutputsValue = originalFullInfo.outputs.sumOf { it.value } - dustValue
            }
        }

        val confirmedUtxoTotalValue = unspentOutputProvider.getConfirmedSpendableUtxo(UtxoFilters()).sumOf { it.output.value }
        val maxFeeAmount = originalFee + removableOutputsValue + confirmedUtxoTotalValue

        return if (absoluteFee > maxFeeAmount) {
            null
        } else {
            ReplacementTransactionInfo(replacementTxMinSize, LongRange(absoluteFee, maxFeeAmount))
        }
    }

    sealed class BuildError : Throwable() {
        class InvalidTransaction(override val message: String) : BuildError()
        object NoPreviousOutput : BuildError()
        object FeeTooLow : BuildError()
        object RbfNotEnabled : BuildError()
        object UnableToReplace : BuildError()
    }
}
