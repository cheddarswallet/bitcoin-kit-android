package io.cheddarswallet.bitcoincore.managers

import com.nhaarman.mockitokotlin2.any
import io.cheddarswallet.bitcoincore.DustCalculator
import io.cheddarswallet.bitcoincore.Fixtures
import io.cheddarswallet.bitcoincore.models.Block
import io.cheddarswallet.bitcoincore.models.Transaction
import io.cheddarswallet.bitcoincore.models.TransactionOutput
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UtxoFilters
import io.cheddarswallet.bitcoincore.transactions.TransactionSizeCalculator
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`


class UnspentOutputSelectorTest {

    private val calculator: TransactionSizeCalculator = mock(TransactionSizeCalculator::class.java)
    private val dustCalculator: DustCalculator = mock(DustCalculator::class.java)
    private val unspentOutputProvider: IUnspentOutputProvider =
        mock(IUnspentOutputProvider::class.java)
    private val queueParams: UnspentOutputQueue.Parameters =
        mock(UnspentOutputQueue.Parameters::class.java)
    private val dust = 100


    @Test
    fun testSelect_DustValue() {
        val value = 54L
        val selector =
            UnspentOutputSelector(calculator, dustCalculator, unspentOutputProvider, null)
        `when`(dustCalculator.dust(any(), any())).thenReturn(dust)

        assertThrows(SendValueErrors.Dust::class.java) {
            selector.select(value, null, 100, ScriptType.P2PKH, ScriptType.P2WPKH, false, 0, null, false, UtxoFilters())
        }
    }

    @Test
    fun testSelect_EmptyOutputs() {
        val selector =
            UnspentOutputSelector(calculator, dustCalculator, unspentOutputProvider, null)
        `when`(unspentOutputProvider.getSpendableUtxo(UtxoFilters())).thenReturn(emptyList())

        assertThrows(SendValueErrors.InsufficientUnspentOutputs::class.java) {
            selector.select(10000, null, 100, ScriptType.P2PKH, ScriptType.P2WPKH, false, 0, null, false, UtxoFilters())
        }
    }

    @Test
    fun testSelect_SuccessfulSelection() {
        val selector = UnspentOutputSelector(calculator, dustCalculator, unspentOutputProvider)
        val outputs = listOf(
            createUnspentOutput(5000),
            createUnspentOutput(10000)
        )

        val feeRate = 5
        val fee = 150
        val value = 12000

        `when`(unspentOutputProvider.getSpendableUtxo(UtxoFilters())).thenReturn(outputs)
        `when`(dustCalculator.dust(any(), any())).thenReturn(dust)
        `when`(calculator.inputSize(any())).thenReturn(10)
//        `when`(calculator.outputSize(any())).thenReturn(2)
        `when`(calculator.transactionSize(anyList(), anyList(), any())).thenReturn(30)
        `when`(queueParams.value).thenReturn(value.toLong())
        `when`(queueParams.fee).thenReturn(fee)

        val selectedInfo =
            selector.select(
                value.toLong(),
                null,
                feeRate,
                ScriptType.P2PKH,
                ScriptType.P2WPKH,
                false,
                0,
                null,
                false,
                UtxoFilters()
            )
        assertEquals(outputs, selectedInfo.outputs)
        assertEquals(11850, selectedInfo.recipientValue)
    }

    @Test
    fun testSelect_Limit() {
        val feeRate = 5
        val fee = 150
        val value = 11000
        val limit = 4
        val selector =
            UnspentOutputSelector(calculator, dustCalculator, unspentOutputProvider, limit)

        val outputs = listOf(
            createUnspentOutput(1000),
            createUnspentOutput(2000),
            createUnspentOutput(3000),
            createUnspentOutput(4000),
            createUnspentOutput(5000),
        )

        `when`(unspentOutputProvider.getSpendableUtxo(UtxoFilters())).thenReturn(outputs)
        `when`(dustCalculator.dust(any(), any())).thenReturn(dust)
        `when`(calculator.inputSize(any())).thenReturn(10)
//        `when`(calculator.outputSize(any())).thenReturn(2)
        `when`(calculator.transactionSize(anyList(), anyList(), any())).thenReturn(30)
        `when`(queueParams.value).thenReturn(value.toLong())
        `when`(queueParams.fee).thenReturn(fee)

        val selectedInfo =
            selector.select(
                value.toLong(),
                null,
                feeRate,
                ScriptType.P2PKH,
                ScriptType.P2WPKH,
                false,
                0,
                null,
                false,
                UtxoFilters()
            )
        assertEquals(4, selectedInfo.outputs.size)
        assertEquals(10850, selectedInfo.recipientValue)
    }

    private fun createUnspentOutput(value: Long, failedToSpend: Boolean = false): UnspentOutput {
        val output =
            TransactionOutput(
                value = value,
                index = 0,
                script = byteArrayOf(),
                type = ScriptType.P2PKH,
                lockingScriptPayload = null
            )
        if (failedToSpend) {
            output.failedToSpend = true
        }
        val pubKey = Fixtures.publicKey
        val transaction = mock(Transaction::class.java)
        val block = mock(Block::class.java)

        return UnspentOutput(output, pubKey, transaction, block)
    }
}
