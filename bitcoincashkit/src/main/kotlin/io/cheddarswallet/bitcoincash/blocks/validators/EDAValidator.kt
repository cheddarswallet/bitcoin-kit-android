package io.cheddarswallet.bitcoincash.blocks.validators

import io.cheddarswallet.bitcoincash.blocks.BitcoinCashBlockValidatorHelper
import io.cheddarswallet.bitcoincore.blocks.BlockMedianTimeHelper
import io.cheddarswallet.bitcoincore.blocks.validators.BlockValidatorException
import io.cheddarswallet.bitcoincore.blocks.validators.IBlockChainedValidator
import io.cheddarswallet.bitcoincore.crypto.CompactBits
import io.cheddarswallet.bitcoincore.models.Block

// Emergency Difficulty Adjustment
class EDAValidator(
        private val maxTargetBits: Long,
        private val blockValidatorHelper: BitcoinCashBlockValidatorHelper,
        private val blockMedianTimeHelper: BlockMedianTimeHelper
) : IBlockChainedValidator {

    override fun isBlockValidatable(block: Block, previousBlock: Block): Boolean {
        return true
    }

    override fun validate(block: Block, previousBlock: Block) {
        if (previousBlock.bits == maxTargetBits) {
            if (block.bits != maxTargetBits) {
                throw BlockValidatorException.NotEqualBits()
            }

            return
        }

        val cursorBlock = checkNotNull(blockValidatorHelper.getPrevious(previousBlock, 6)) {
            throw BlockValidatorException.NoPreviousBlock()
        }

        val mpt6blocks = medianTimePast(previousBlock) - medianTimePast(cursorBlock)
        if (mpt6blocks >= 12 * 3600) {
            val decodedBits = CompactBits.decode(previousBlock.bits)
            val pow = decodedBits + (decodedBits shr 2)
            var powBits = CompactBits.encode(pow)
            if (powBits > maxTargetBits)
                powBits = maxTargetBits
            if (powBits != block.bits) {
                throw BlockValidatorException.NotEqualBits()
            }
        } else if (previousBlock.bits != block.bits) {
            throw BlockValidatorException.NotEqualBits()
        }
    }

    private fun medianTimePast(block: Block): Long {
        return blockMedianTimeHelper.medianTimePast(block) ?: block.height.toLong()
    }
}
