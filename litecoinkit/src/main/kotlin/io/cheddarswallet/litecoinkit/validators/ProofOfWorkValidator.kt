package io.cheddarswallet.litecoinkit.validators

import io.cheddarswallet.bitcoincore.blocks.validators.BlockValidatorException
import io.cheddarswallet.bitcoincore.blocks.validators.IBlockChainedValidator
import io.cheddarswallet.bitcoincore.crypto.CompactBits
import io.cheddarswallet.bitcoincore.extensions.toHexString
import io.cheddarswallet.bitcoincore.io.BitcoinOutput
import io.cheddarswallet.bitcoincore.models.Block
import io.cheddarswallet.litecoinkit.ScryptHasher
import java.math.BigInteger

class ProofOfWorkValidator(private val scryptHasher: ScryptHasher) : IBlockChainedValidator {

    override fun validate(block: Block, previousBlock: Block) {
        val blockHeaderData = getSerializedBlockHeader(block)

        val powHash = scryptHasher.hash(blockHeaderData).toHexString()

        check(BigInteger(powHash, 16) < CompactBits.decode(block.bits)) {
            throw BlockValidatorException.InvalidProofOfWork()
        }
    }

    private fun getSerializedBlockHeader(block: Block): ByteArray {
        return BitcoinOutput()
                .writeInt(block.version)
                .write(block.previousBlockHash)
                .write(block.merkleRoot)
                .writeUnsignedInt(block.timestamp)
                .writeUnsignedInt(block.bits)
                .writeUnsignedInt(block.nonce)
                .toByteArray()
    }

    override fun isBlockValidatable(block: Block, previousBlock: Block): Boolean {
        return true
    }

}
