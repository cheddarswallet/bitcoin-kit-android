package io.cheddarswallet.litecoinkit

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.cheddarswallet.bitcoincore.AbstractKit
import io.cheddarswallet.bitcoincore.BitcoinCore
import io.cheddarswallet.bitcoincore.BitcoinCore.SyncMode
import io.cheddarswallet.bitcoincore.BitcoinCoreBuilder
import io.cheddarswallet.bitcoincore.apisync.BCoinApi
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairApi
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairBlockHashFetcher
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairTransactionProvider
import io.cheddarswallet.bitcoincore.blocks.validators.BitsValidator
import io.cheddarswallet.bitcoincore.blocks.validators.BlockValidatorChain
import io.cheddarswallet.bitcoincore.blocks.validators.BlockValidatorSet
import io.cheddarswallet.bitcoincore.blocks.validators.LegacyTestNetDifficultyValidator
import io.cheddarswallet.bitcoincore.core.purpose
import io.cheddarswallet.bitcoincore.managers.ApiSyncStateManager
import io.cheddarswallet.bitcoincore.managers.Bip44RestoreKeyConverter
import io.cheddarswallet.bitcoincore.managers.Bip49RestoreKeyConverter
import io.cheddarswallet.bitcoincore.managers.Bip84RestoreKeyConverter
import io.cheddarswallet.bitcoincore.managers.Bip86RestoreKeyConverter
import io.cheddarswallet.bitcoincore.managers.BlockValidatorHelper
import io.cheddarswallet.bitcoincore.models.Address
import io.cheddarswallet.bitcoincore.models.Checkpoint
import io.cheddarswallet.bitcoincore.models.WatchAddressPublicKey
import io.cheddarswallet.bitcoincore.network.Network
import io.cheddarswallet.bitcoincore.storage.CoreDatabase
import io.cheddarswallet.bitcoincore.storage.Storage
import io.cheddarswallet.bitcoincore.utils.AddressConverterChain
import io.cheddarswallet.bitcoincore.utils.Base58AddressConverter
import io.cheddarswallet.bitcoincore.utils.PaymentAddressParser
import io.cheddarswallet.bitcoincore.utils.SegwitAddressConverter
import io.cheddarswallet.hdwalletkit.HDExtendedKey
import io.cheddarswallet.hdwalletkit.HDWallet.Purpose
import io.cheddarswallet.hdwalletkit.Mnemonic
import io.cheddarswallet.litecoinkit.validators.LegacyDifficultyAdjustmentValidator
import io.cheddarswallet.litecoinkit.validators.ProofOfWorkValidator

class LitecoinKit : AbstractKit {
    enum class NetworkType {
        MainNet,
        TestNet
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
        confirmationsThreshold: Int = defaultConfirmationsThreshold,
        purpose: Purpose = Purpose.BIP44
    ) : this(context, Mnemonic().toSeed(words, passphrase), walletId, networkType, peerSize, syncMode, confirmationsThreshold, purpose)

    constructor(
        context: Context,
        seed: ByteArray,
        walletId: String,
        networkType: NetworkType = defaultNetworkType,
        peerSize: Int = defaultPeerSize,
        syncMode: SyncMode = defaultSyncMode,
        confirmationsThreshold: Int = defaultConfirmationsThreshold,
        purpose: Purpose = Purpose.BIP44
    ) : this(context, HDExtendedKey(seed, purpose), purpose, walletId, networkType, peerSize, syncMode, confirmationsThreshold)

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
        purpose: Purpose,
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
            walletId = walletId,
            syncMode = syncMode,
            purpose = purpose,
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
        val purpose = address.scriptType.purpose ?: throw IllegalStateException("Not supported scriptType ${address.scriptType}")

        bitcoinCore = bitcoinCore(
            context = context,
            extendedKey = null,
            watchAddressPublicKey = watchAddressPublicKey,
            networkType = networkType,
            walletId = walletId,
            syncMode = syncMode,
            purpose = purpose,
            peerSize = peerSize,
            confirmationsThreshold = confirmationsThreshold
        )
    }

    private fun bitcoinCore(
        context: Context,
        extendedKey: HDExtendedKey?,
        watchAddressPublicKey: WatchAddressPublicKey?,
        networkType: NetworkType,
        walletId: String,
        syncMode: SyncMode,
        purpose: Purpose,
        peerSize: Int,
        confirmationsThreshold: Int
    ): BitcoinCore {
        val database = CoreDatabase.getInstance(context, getDatabaseName(networkType, walletId, syncMode, purpose))
        val storage = Storage(database)
        val checkpoint = Checkpoint.resolveCheckpoint(syncMode, network, storage)
        val apiSyncStateManager = ApiSyncStateManager(storage, network.syncableFromApi && syncMode !is SyncMode.Full)
        val blockchairApi = BlockchairApi(network.blockchairChainId)
        val apiTransactionProvider = apiTransactionProvider(networkType, blockchairApi)
        val paymentAddressParser = PaymentAddressParser("litecoin", removeScheme = true)
        val blockValidatorSet = blockValidatorSet(storage, networkType)

        val coreBuilder = BitcoinCoreBuilder()

        val bitcoinCore = coreBuilder
            .setContext(context)
            .setExtendedKey(extendedKey)
            .setWatchAddressPublicKey(watchAddressPublicKey)
            .setPurpose(purpose)
            .setNetwork(network)
            .setCheckpoint(checkpoint)
            .setPaymentAddressParser(paymentAddressParser)
            .setPeerSize(peerSize)
            .setSyncMode(syncMode)
            .setSendType(BitcoinCore.SendType.API(blockchairApi))
            .setConfirmationThreshold(confirmationsThreshold)
            .setStorage(storage)
            .setApiTransactionProvider(apiTransactionProvider)
            .setApiSyncStateManager(apiSyncStateManager)
            .setBlockValidator(blockValidatorSet)
            .build()

        //  extending bitcoinCore

        val bech32AddressConverter = SegwitAddressConverter(network.addressSegwitHrp)
        val base58AddressConverter = Base58AddressConverter(network.addressVersion, network.addressScriptVersion)

        bitcoinCore.prependAddressConverter(bech32AddressConverter)

        when (purpose) {
            Purpose.BIP44 -> {
                bitcoinCore.addRestoreKeyConverter(Bip44RestoreKeyConverter(base58AddressConverter))
            }

            Purpose.BIP49 -> {
                bitcoinCore.addRestoreKeyConverter(Bip49RestoreKeyConverter(base58AddressConverter))
            }

            Purpose.BIP84 -> {
                bitcoinCore.addRestoreKeyConverter(Bip84RestoreKeyConverter(SegwitAddressConverter(network.addressSegwitHrp)))
            }

            Purpose.BIP86 -> {
                bitcoinCore.addRestoreKeyConverter(Bip86RestoreKeyConverter(SegwitAddressConverter(network.addressSegwitHrp)))
            }
        }

        return bitcoinCore
    }

    private fun parseAddress(address: String, network: Network): Address {
        val addressConverter = AddressConverterChain().apply {
            prependConverter(SegwitAddressConverter(network.addressSegwitHrp))
            prependConverter(Base58AddressConverter(network.addressVersion, network.addressScriptVersion))
        }
        return addressConverter.convert(address)
    }

    private fun blockValidatorSet(
        storage: Storage,
        networkType: NetworkType
    ): BlockValidatorSet {
        val blockValidatorSet = BlockValidatorSet()

        val proofOfWorkValidator = ProofOfWorkValidator(ScryptHasher())
        blockValidatorSet.addBlockValidator(proofOfWorkValidator)

        val blockValidatorChain = BlockValidatorChain()

        val blockHelper = BlockValidatorHelper(storage)

        if (networkType == NetworkType.MainNet) {
            blockValidatorChain.add(LegacyDifficultyAdjustmentValidator(blockHelper, heightInterval, targetTimespan, maxTargetBits))
            blockValidatorChain.add(BitsValidator())
        } else if (networkType == NetworkType.TestNet) {
            blockValidatorChain.add(LegacyDifficultyAdjustmentValidator(blockHelper, heightInterval, targetTimespan, maxTargetBits))
            blockValidatorChain.add(LegacyTestNetDifficultyValidator(storage, heightInterval, targetSpacing, maxTargetBits))
            blockValidatorChain.add(BitsValidator())
        }

        blockValidatorSet.addBlockValidator(blockValidatorChain)
        return blockValidatorSet
    }

    private fun apiTransactionProvider(
        networkType: NetworkType,
        blockchairApi: BlockchairApi
    ) = when (networkType) {
        NetworkType.MainNet -> {
            val blockchairBlockHashFetcher = BlockchairBlockHashFetcher(blockchairApi)
            BlockchairTransactionProvider(blockchairApi, blockchairBlockHashFetcher)
        }

        NetworkType.TestNet -> {
            BCoinApi("")
        }
    }

    companion object {

        const val maxTargetBits: Long = 0x1e0fffff      // Maximum difficulty
        const val targetSpacing = 150                   // 2.5 minutes per block.
        const val targetTimespan: Long = 302400         // 3.5 days per difficulty cycle, on average.
        const val heightInterval = targetTimespan / targetSpacing // 2016 blocks

        val defaultNetworkType: NetworkType = NetworkType.MainNet
        val defaultSyncMode: SyncMode = SyncMode.Api()
        const val defaultPeerSize: Int = 10
        const val defaultConfirmationsThreshold: Int = 6

        private fun getDatabaseName(networkType: NetworkType, walletId: String, syncMode: SyncMode, purpose: Purpose): String =
            "Litecoin-${networkType.name}-$walletId-${syncMode.javaClass.simpleName}-${purpose.name}"

        fun clear(context: Context, networkType: NetworkType, walletId: String) {
            for (syncMode in listOf(SyncMode.Api(), SyncMode.Full(), SyncMode.Blockchair())) {
                for (purpose in Purpose.values())
                    try {
                        SQLiteDatabase.deleteDatabase(context.getDatabasePath(getDatabaseName(networkType, walletId, syncMode, purpose)))
                    } catch (ex: Exception) {
                        continue
                    }
            }
        }

        private fun network(networkType: NetworkType) = when (networkType) {
            NetworkType.MainNet -> MainNetLitecoin()
            NetworkType.TestNet -> TestNetLitecoin()
        }

        private fun addressConverter(purpose: Purpose, network: Network): AddressConverterChain {
            val addressConverter = AddressConverterChain()
            when (purpose) {
                Purpose.BIP44,
                Purpose.BIP49 -> {
                    addressConverter.prependConverter(
                        Base58AddressConverter(network.addressVersion, network.addressScriptVersion)
                    )
                }

                Purpose.BIP84,
                Purpose.BIP86 -> {
                    addressConverter.prependConverter(
                        SegwitAddressConverter(network.addressSegwitHrp)
                    )
                }
            }

            return addressConverter
        }

        fun firstAddress(
            seed: ByteArray,
            purpose: Purpose,
            networkType: NetworkType = NetworkType.MainNet,
        ): Address {
            return BitcoinCore.firstAddress(
                seed,
                purpose,
                network(networkType),
                addressConverter(purpose, network(networkType))
            )
        }

        fun firstAddress(
            extendedKey: HDExtendedKey,
            purpose: Purpose,
            networkType: NetworkType = NetworkType.MainNet,
        ): Address {
            return BitcoinCore.firstAddress(
                extendedKey,
                purpose,
                network(networkType),
                addressConverter(purpose, network(networkType))
            )
        }
    }

}
