package io.cheddarswallet.bitcoincore.storage

import androidx.room.Embedded
import io.cheddarswallet.bitcoincore.core.IStorage
import io.cheddarswallet.bitcoincore.extensions.toHexString
import io.cheddarswallet.bitcoincore.models.Block
import io.cheddarswallet.bitcoincore.models.PublicKey
import io.cheddarswallet.bitcoincore.models.Transaction
import io.cheddarswallet.bitcoincore.models.TransactionInput
import io.cheddarswallet.bitcoincore.models.TransactionMetadata
import io.cheddarswallet.bitcoincore.models.TransactionOutput
import io.cheddarswallet.bitcoincore.serializers.TransactionSerializer
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType
import io.cheddarswallet.bitcoincore.utils.HashUtils

class BlockHeader(
        val version: Int,
        val previousBlockHeaderHash: ByteArray,
        val merkleRoot: ByteArray,
        val timestamp: Long,
        val bits: Long,
        val nonce: Long,
        val hash: ByteArray)

open class FullTransaction(
    val header: Transaction,
    val inputs: List<TransactionInput>,
    val outputs: List<TransactionOutput>,
    val forceHashUpdate: Boolean = true
) {

    lateinit var metadata: TransactionMetadata

    init {
        if (forceHashUpdate) {
            setHash(HashUtils.doubleSha256(TransactionSerializer.serialize(this, withWitness = false)))
        }
    }

    fun setHash(hash: ByteArray) {
        header.hash = hash

        metadata = TransactionMetadata(header.hash)

        inputs.forEach {
            it.transactionHash = header.hash
        }
        outputs.forEach {
            it.transactionHash = header.hash
        }
    }

}

class InputToSign(
        val input: TransactionInput,
        val previousOutput: TransactionOutput,
        val previousOutputPublicKey: PublicKey)

class TransactionWithBlock(
        @Embedded val transaction: Transaction,
        @Embedded val block: Block?)

class PublicKeyWithUsedState(
        @Embedded val publicKey: PublicKey,
        val usedCount: Int) {

    val used: Boolean
        get() = usedCount > 0
}

class InputWithPreviousOutput(
    val input: TransactionInput,
    val previousOutput: TransactionOutput?
)

class UnspentOutput(
    @Embedded val output: TransactionOutput,
    @Embedded val publicKey: PublicKey,
    @Embedded val transaction: Transaction,
    @Embedded val block: Block?
)

data class UtxoFilters(
    val scriptTypes: List<ScriptType>? = null,
    val maxOutputsCountForInputs: Int? = null
) {
    fun isEmpty() = scriptTypes == null && maxOutputsCountForInputs == null

    fun filterUtxo(utxo: UnspentOutput, storage: IStorage): Boolean {
        if (
            scriptTypes != null &&
            !scriptTypes.contains(utxo.output.scriptType)
        ) {
            return false
        }

        if (
            maxOutputsCountForInputs != null &&
            storage.getTransactionOutputsCount(utxo.transaction.hash) > maxOutputsCountForInputs
        ) {
            return false
        }


        return true
    }
}

class UnspentOutputInfo(
    val outputIndex: Int,
    val transactionHash: ByteArray,
    val timestamp: Long,
    val address: String?,
    val value: Long
) {
    companion object {
        fun fromUnspentOutput(unspentOutput: UnspentOutput): UnspentOutputInfo {
            return UnspentOutputInfo(
                unspentOutput.output.index,
                unspentOutput.output.transactionHash,
                unspentOutput.transaction.timestamp,
                unspentOutput.output.address,
                unspentOutput.output.value,
            )
        }
    }
}

class FullTransactionInfo(
        val block: Block?,
        val header: Transaction,
        val inputs: List<InputWithPreviousOutput>,
        val outputs: List<TransactionOutput>,
        val metadata: TransactionMetadata
) {

    val rawTransaction: String
        get() {
            return TransactionSerializer.serialize(fullTransaction).toHexString()
        }

    val fullTransaction: FullTransaction
        get() = FullTransaction(header, inputs.map { it.input }, outputs)

}

