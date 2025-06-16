package io.cheddarswallet.bitcoincore.blocks.validators

import io.cheddarswallet.bitcoincore.crypto.CompactBits
import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.models.Block
import java.math.BigInteger

class ProofOfWorkValidator : IBlockChainedValidator {

    override fun isBlockValidatable(block: Block, previousBlock: Block): Boolean {
        return true
    }

    override fun validate(block: Block, previousBlock: Block) {
        check(BigInteger(block.headerHash.toReversedHex(), 16) < CompactBits.decode(block.bits)) {
            throw BlockValidatorException.InvalidProofOfWork()
        }
    }
}
