package io.cheddarswallet.bitcoincore.network

import io.cheddarswallet.bitcoincore.models.Checkpoint
import io.cheddarswallet.bitcoincore.transactions.scripts.Sighash
import io.cheddarswallet.bitcoincore.utils.HashUtils

abstract class Network {

    open val protocolVersion = 70014
    open val syncableFromApi = true
    val bloomFilterVersion = 70000
    open val noBloomVersion = 70011
    val networkServices = 0L
    val serviceFullNode = 1L
    val serviceBloomFilter = 4L
    val zeroHashBytes = HashUtils.toBytesAsLE("0000000000000000000000000000000000000000000000000000000000000000")

    abstract val blockchairChainId: String

    abstract val maxBlockSize: Int
    abstract val dustRelayTxFee: Int

    abstract var port: Int
    abstract var magic: Long
    abstract var bip32HeaderPub: Int
    abstract var bip32HeaderPriv: Int
    abstract var coinType: Int
    abstract var dnsSeeds: List<String>
    abstract var addressVersion: Int
    abstract var addressSegwitHrp: String
    abstract var addressScriptVersion: Int

    open val bip44Checkpoint = Checkpoint("${javaClass.simpleName}-bip44.checkpoint")
    open val lastCheckpoint = Checkpoint("${javaClass.simpleName}.checkpoint")

    open val sigHashForked: Boolean = false
    open val sigHashValue = Sighash.ALL
}
