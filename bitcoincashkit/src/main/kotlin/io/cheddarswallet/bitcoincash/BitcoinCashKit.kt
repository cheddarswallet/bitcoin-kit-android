package io.cheddarswallet.bitcoincash

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.cheddarswallet.bitcoincash.blocks.BitcoinCashBlockValidatorHelper
import io.cheddarswallet.bitcoincash.blocks.validators.AsertValidator
import io.cheddarswallet.bitcoincash.blocks.validators.DAAValidator
import io.cheddarswallet.bitcoincash.blocks.validators.EDAValidator
import io.cheddarswallet.bitcoincash.blocks.validators.ForkValidator
import io.cheddarswallet.bitcoincore.AbstractKit
import io.cheddarswallet.bitcoincore.BitcoinCore
import io.cheddarswallet.bitcoincore.BitcoinCore.SyncMode
import io.cheddarswallet.bitcoincore.BitcoinCoreBuilder
import io.cheddarswallet.bitcoincore.apisync.BlockchainComApi
import io.cheddarswallet.bitcoincore.apisync.HsBlockHashFetcher
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairApi
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairBlockHashFetcher
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairTransactionProvider
import io.cheddarswallet.bitcoincore.blocks.BlockMedianTimeHelper
import io.cheddarswallet.bitcoincore.blocks.validators.BlockValidatorChain
import io.cheddarswallet.bitcoincore.blocks.validators.BlockValidatorSet
import io.cheddarswallet.bitcoincore.blocks.validators.LegacyDifficultyAdjustmentValidator
import io.cheddarswallet.bitcoincore.blocks.validators.ProofOfWorkValidator
import io.cheddarswallet.bitcoincore.core.IApiTransactionProvider
import io.cheddarswallet.bitcoincore.extensions.toReversedByteArray
import io.cheddarswallet.bitcoincore.managers.ApiSyncStateManager
import io.cheddarswallet.bitcoincore.managers.Bip44RestoreKeyConverter
import io.cheddarswallet.bitcoincore.managers.BlockchairCashRestoreKeyConverter
import io.cheddarswallet.bitcoincore.models.Address
import io.cheddarswallet.bitcoincore.models.Checkpoint
import io.cheddarswallet.bitcoincore.models.WatchAddressPublicKey
import io.cheddarswallet.bitcoincore.network.Network
import io.cheddarswallet.bitcoincore.storage.CoreDatabase
import io.cheddarswallet.bitcoincore.storage.Storage
import io.cheddarswallet.bitcoincore.utils.AddressConverterChain
import io.cheddarswallet.bitcoincore.utils.Base58AddressConverter
import io.cheddarswallet.bitcoincore.utils.CashAddressConverter
import io.cheddarswallet.bitcoincore.utils.PaymentAddressParser
import io.cheddarswallet.hdwalletkit.HDExtendedKey
import io.cheddarswallet.hdwalletkit.HDWallet.Purpose
import io.cheddarswallet.hdwalletkit.Mnemonic

class BitcoinCashKit : AbstractKit {
    sealed class NetworkType {
        class MainNet(val coinType: MainNetBitcoinCash.CoinType) : NetworkType()
        object TestNet : NetworkType()

        val description: String
            get() = when (this) {
                is MainNet -> {
                    when (coinType) {
                        MainNetBitcoinCash.CoinType.Type0 -> "mainNet" // back compatibility for database file name in old NetworkType
                        MainNetBitcoinCash.CoinType.Type145 -> "mainNet-145"
                    }
                }

                is TestNet -> "testNet"
            }
    }

    interface Listener : BitcoinCore.Listener

    override var bitcoinCore: BitcoinCore
    override var network: Network

    var listener: Listener? = null
        set(value) {
            field = value
            bitcoinCore.listener = value
        }

    constructor(
        context: Context,
        words: List<String>,
        passphrase: String,
        walletId: String,
        networkType: NetworkType = defaultNetworkType,
        peerSize: Int = defaultPeerSize,
        syncMode: SyncMode = defaultSyncMode,
        confirmationsThreshold: Int = defaultConfirmationsThreshold
    ) : this(context, Mnemonic().toSeed(words, passphrase), walletId, networkType, peerSize, syncMode, confirmationsThreshold)

    constructor(
        context: Context,
        seed: ByteArray,
        walletId: String,
        networkType: NetworkType = defaultNetworkType,
        peerSize: Int = defaultPeerSize,
        syncMode: SyncMode = defaultSyncMode,
        confirmationsThreshold: Int = defaultConfirmationsThreshold
    ) : this(context, HDExtendedKey(seed, Purpose.BIP44), walletId, networkType, peerSize, syncMode, confirmationsThreshold)

    /**
     * @constructor Creates and initializes the BitcoinKit
     * @param context The Android context
     * @param extendedKey HDExtendedKey that contains HDKey and version
     * @param walletId an arbitrary ID of type String.
     * @param networkType The network type. The default is MainNet.
     * @param peerSize The # of peer-nodes required. The default is 10 peers.
     * @param syncMode How the kit syncs with the blockchain. The default is SyncMode.Api().
     * @param confirmationsThreshold How many confirmations required to be considered confirmed. The default is 6 confirmations.
     */
    constructor(
        context: Context,
        extendedKey: HDExtendedKey,
        walletId: String,
        networkType: NetworkType = defaultNetworkType,
        peerSize: Int = defaultPeerSize,
        syncMode: SyncMode = defaultSyncMode,
        confirmationsThreshold: Int = defaultConfirmationsThreshold
    ) {
        network = network(networkType)

        bitcoinCore = bitcoinCore(
            context = context,
            extendedKey = extendedKey,
            watchAddressPublicKey = null,
            networkType = networkType,
            network = network,
            walletId = walletId,
            syncMode = syncMode,
            peerSize = peerSize,
            confirmationsThreshold = confirmationsThreshold
        )
    }

    /**
     * @constructor Creates and initializes the BitcoinKit
     * @param context The Android context
     * @param watchAddress address for watching in read-only mode
     * @param walletId an arbitrary ID of type String.
     * @param networkType The network type. The default is MainNet.
     * @param peerSize The # of peer-nodes required. The default is 10 peers.
     * @param syncMode How the kit syncs with the blockchain. The default is SyncMode.Api().
     * @param confirmationsThreshold How many confirmations required to be considered confirmed. The default is 6 confirmations.
     */
    constructor(
        context: Context,
        watchAddress: String,
        walletId: String,
        networkType: NetworkType = defaultNetworkType,
        peerSize: Int = defaultPeerSize,
        syncMode: SyncMode = defaultSyncMode,
        confirmationsThreshold: Int = defaultConfirmationsThreshold
    ) {
        network = network(networkType)

        val address = parseAddress(watchAddress, network)
        val watchAddressPublicKey = WatchAddressPublicKey(address.lockingScriptPayload, address.scriptType)

        bitcoinCore = bitcoinCore(
            context = context,
            extendedKey = null,
            watchAddressPublicKey = watchAddressPublicKey,
            networkType = networkType,
            network = network,
            walletId = walletId,
            syncMode = syncMode,
            peerSize = peerSize,
            confirmationsThreshold = confirmationsThreshold
        )
    }

    private fun bitcoinCore(
        context: Context,
        extendedKey: HDExtendedKey?,
        watchAddressPublicKey: WatchAddressPublicKey?,
        networkType: NetworkType,
        network: Network,
        walletId: String,
        syncMode: SyncMode,
        peerSize: Int,
        confirmationsThreshold: Int
    ): BitcoinCore {
        val database = CoreDatabase.getInstance(context, getDatabaseName(networkType, walletId, syncMode))
        val storage = Storage(database)
        val checkpoint = Checkpoint.resolveCheckpoint(syncMode, network, storage)
        val apiSyncStateManager = ApiSyncStateManager(storage, network.syncableFromApi && syncMode !is SyncMode.Full)
        val apiTransactionProvider = apiTransactionProvider(networkType, syncMode)
        val paymentAddressParser = PaymentAddressParser("bitcoincash", removeScheme = false)
        val blockValidatorSet = blockValidatorSet(networkType, storage)

        val bitcoinCore = BitcoinCoreBuilder()
            .setContext(context)
            .setExtendedKey(extendedKey)
            .setWatchAddressPublicKey(watchAddressPublicKey)
            .setPurpose(Purpose.BIP44)
            .setNetwork(network)
            .setCheckpoint(checkpoint)
            .setPaymentAddressParser(paymentAddressParser)
            .setPeerSize(peerSize)
            .setSyncMode(syncMode)
            .setConfirmationThreshold(confirmationsThreshold)
            .setStorage(storage)
            .setApiTransactionProvider(apiTransactionProvider)
            .setApiSyncStateManager(apiSyncStateManager)
            .setBlockValidator(blockValidatorSet)
            .build()

        //  extending bitcoinCore

        val bech32 = CashAddressConverter(network.addressSegwitHrp)
        bitcoinCore.prependAddressConverter(bech32)

        val restoreKeyConverter = if (syncMode is SyncMode.Blockchair) {
            BlockchairCashRestoreKeyConverter(bech32)
        } else {
            val base58 = Base58AddressConverter(network.addressVersion, network.addressScriptVersion)
            Bip44RestoreKeyConverter(base58)
        }
        bitcoinCore.addRestoreKeyConverter(restoreKeyConverter)

        return bitcoinCore
    }

    private fun parseAddress(address: String, network: Network): Address {
        val addressConverter = AddressConverterChain().apply {
            prependConverter(CashAddressConverter(network.addressSegwitHrp))
            prependConverter(Base58AddressConverter(network.addressVersion, network.addressScriptVersion))
        }
        return addressConverter.convert(address)
    }

    private fun blockValidatorSet(
        networkType: NetworkType,
        storage: Storage
    ): BlockValidatorSet {
        val blockValidatorSet = BlockValidatorSet()
        blockValidatorSet.addBlockValidator(ProofOfWorkValidator())

        val blockValidatorChain = BlockValidatorChain()
        if (networkType is NetworkType.MainNet) {
            val blockHelper = BitcoinCashBlockValidatorHelper(storage)

            val daaValidator = DAAValidator(targetSpacing, blockHelper)
            val asertValidator = AsertValidator()

            blockValidatorChain.add(ForkValidator(bchnChainForkHeight, bchnChainForkBlockHash, asertValidator))
            blockValidatorChain.add(asertValidator)

            blockValidatorChain.add(ForkValidator(svForkHeight, abcForkBlockHash, daaValidator))
            blockValidatorChain.add(daaValidator)

            blockValidatorChain.add(LegacyDifficultyAdjustmentValidator(blockHelper, heightInterval, targetTimespan, maxTargetBits))
            blockValidatorChain.add(EDAValidator(maxTargetBits, blockHelper, BlockMedianTimeHelper(storage)))
        }

        blockValidatorSet.addBlockValidator(blockValidatorChain)
        return blockValidatorSet
    }

    private fun apiTransactionProvider(
        networkType: NetworkType,
        syncMode: SyncMode
    ): IApiTransactionProvider {
        val blockchairApi = BlockchairApi(network.blockchairChainId)
        val blockchairBlockHashFetcher = BlockchairBlockHashFetcher(blockchairApi)
        return when (networkType) {
            is NetworkType.MainNet -> {
                if (syncMode is SyncMode.Blockchair) {
                    BlockchairTransactionProvider(blockchairApi, blockchairBlockHashFetcher)
                } else {
                    BlockchainComApi("https://api.haskoin.com/bch/blockchain", blockchairBlockHashFetcher)
                }
            }

            NetworkType.TestNet -> {
                BlockchainComApi(
                    transactionApiUrl = "https://api.haskoin.com/bchtest/blockchain",
                    blockHashFetcher = HsBlockHashFetcher("https://api.blocksdecoded.com/v1/blockchains/bitcoin-cash")
                )
            }
        }
    }

    companion object {
        const val maxTargetBits: Long = 0x1d00ffff              // Maximum difficulty
        const val targetSpacing = 10 * 60                       // 10 minutes per block.
        const val targetTimespan: Long = 14 * 24 * 60 * 60      // 2 weeks per difficulty cycle, on average.
        var heightInterval = targetTimespan / targetSpacing     // 2016 blocks

        const val svForkHeight = 556767                         // 2018 November 14
        const val bchnChainForkHeight = 661648                  // 2020 November 15, 14:13 GMT

        val abcForkBlockHash = "0000000000000000004626ff6e3b936941d341c5932ece4357eeccac44e6d56c".toReversedByteArray()
        val bchnChainForkBlockHash = "0000000000000000029e471c41818d24b8b74c911071c4ef0b4a0509f9b5a8ce".toReversedByteArray()

        val defaultNetworkType: NetworkType = NetworkType.MainNet(MainNetBitcoinCash.CoinType.Type145)
        val defaultSyncMode: SyncMode = SyncMode.Api()
        const val defaultPeerSize: Int = 10
        const val defaultConfirmationsThreshold: Int = 6

        private fun getDatabaseName(networkType: NetworkType, walletId: String, syncMode: SyncMode): String =
            "BitcoinCash-${networkType.description}-$walletId-${syncMode.javaClass.simpleName}"

        fun clear(context: Context, networkType: NetworkType, walletId: String) {
            for (syncMode in listOf(SyncMode.Api(), SyncMode.Full(), SyncMode.Blockchair())) {
                try {
                    SQLiteDatabase.deleteDatabase(context.getDatabasePath(getDatabaseName(networkType, walletId, syncMode)))
                } catch (ex: Exception) {
                    continue
                }
            }
        }

        private fun network(networkType: NetworkType) = when (networkType) {
            is NetworkType.MainNet -> MainNetBitcoinCash(networkType.coinType)
            NetworkType.TestNet -> TestNetBitcoinCash()
        }

        private fun addressConverter(network: Network): AddressConverterChain {
            val addressConverter = AddressConverterChain()

            val bech32 = CashAddressConverter(network.addressSegwitHrp)
            addressConverter.prependConverter(bech32)

            return addressConverter
        }

        fun firstAddress(
            seed: ByteArray,
            networkType: NetworkType,
        ): Address {
            return BitcoinCore.firstAddress(
                seed,
                Purpose.BIP44,
                network(networkType),
                addressConverter(network(networkType))
            )
        }

        fun firstAddress(
            extendedKey: HDExtendedKey,
            networkType: NetworkType,
        ): Address {
            return BitcoinCore.firstAddress(
                extendedKey,
                Purpose.BIP44,
                network(networkType),
                addressConverter(network(networkType))
            )
        }
    }
}
