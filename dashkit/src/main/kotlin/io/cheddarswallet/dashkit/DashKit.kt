package io.cheddarswallet.dashkit

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.cheddarswallet.bitcoincore.AbstractKit
import io.cheddarswallet.bitcoincore.BitcoinCore
import io.cheddarswallet.bitcoincore.BitcoinCore.SyncMode
import io.cheddarswallet.bitcoincore.BitcoinCoreBuilder
import io.cheddarswallet.bitcoincore.DustCalculator
import io.cheddarswallet.bitcoincore.apisync.BiApiTransactionProvider
import io.cheddarswallet.bitcoincore.apisync.InsightApi
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairApi
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairBlockHashFetcher
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairTransactionProvider
import io.cheddarswallet.bitcoincore.blocks.validators.BlockValidatorChain
import io.cheddarswallet.bitcoincore.blocks.validators.BlockValidatorSet
import io.cheddarswallet.bitcoincore.blocks.validators.ProofOfWorkValidator
import io.cheddarswallet.bitcoincore.extensions.hexToByteArray
import io.cheddarswallet.bitcoincore.managers.*
import io.cheddarswallet.bitcoincore.models.Address
import io.cheddarswallet.bitcoincore.models.BalanceInfo
import io.cheddarswallet.bitcoincore.models.BlockInfo
import io.cheddarswallet.bitcoincore.models.Checkpoint
import io.cheddarswallet.bitcoincore.models.TransactionFilterType
import io.cheddarswallet.bitcoincore.models.TransactionInfo
import io.cheddarswallet.bitcoincore.models.WatchAddressPublicKey
import io.cheddarswallet.bitcoincore.network.Network
import io.cheddarswallet.bitcoincore.storage.CoreDatabase
import io.cheddarswallet.bitcoincore.storage.Storage
import io.cheddarswallet.bitcoincore.transactions.TransactionSizeCalculator
import io.cheddarswallet.bitcoincore.utils.Base58AddressConverter
import io.cheddarswallet.bitcoincore.utils.MerkleBranch
import io.cheddarswallet.bitcoincore.utils.PaymentAddressParser
import io.cheddarswallet.dashkit.core.DashTransactionInfoConverter
import io.cheddarswallet.dashkit.core.SingleSha256Hasher
import io.cheddarswallet.dashkit.instantsend.*
import io.cheddarswallet.dashkit.instantsend.instantsendlock.InstantSendLockHandler
import io.cheddarswallet.dashkit.instantsend.instantsendlock.InstantSendLockManager
import io.cheddarswallet.dashkit.instantsend.transactionlockvote.TransactionLockVoteHandler
import io.cheddarswallet.dashkit.instantsend.transactionlockvote.TransactionLockVoteManager
import io.cheddarswallet.dashkit.managers.*
import io.cheddarswallet.dashkit.masternodelist.*
import io.cheddarswallet.dashkit.messages.*
import io.cheddarswallet.dashkit.models.CoinbaseTransactionSerializer
import io.cheddarswallet.dashkit.models.DashTransactionInfo
import io.cheddarswallet.dashkit.models.InstantTransactionState
import io.cheddarswallet.dashkit.storage.DashKitDatabase
import io.cheddarswallet.dashkit.storage.DashStorage
import io.cheddarswallet.dashkit.tasks.PeerTaskFactory
import io.cheddarswallet.dashkit.validators.DarkGravityWaveTestnetValidator
import io.cheddarswallet.dashkit.validators.DarkGravityWaveValidator
import io.cheddarswallet.hdwalletkit.HDExtendedKey
import io.cheddarswallet.hdwalletkit.HDWallet.Purpose
import io.cheddarswallet.hdwalletkit.Mnemonic
import io.reactivex.Single

class DashKit : AbstractKit, IInstantTransactionDelegate, BitcoinCore.Listener {
    enum class NetworkType {
        MainNet,
        TestNet
    }

    interface Listener {
        fun onTransactionsUpdate(inserted: List<DashTransactionInfo>, updated: List<DashTransactionInfo>)
        fun onTransactionsDelete(hashes: List<String>)
        fun onBalanceUpdate(balance: BalanceInfo)
        fun onLastBlockInfoUpdate(blockInfo: BlockInfo)
        fun onKitStateUpdate(state: BitcoinCore.KitState)
    }

    var listener: Listener? = null

    override var bitcoinCore: BitcoinCore
    override var network: Network

    private val dashStorage: DashStorage
    private val instantSend: InstantSend
    private val dashTransactionInfoConverter: DashTransactionInfoConverter

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
    ) : this(
        context = context,
        extendedKey = HDExtendedKey(seed, Purpose.BIP44),
        walletId = walletId,
        networkType = networkType,
        peerSize = peerSize,
        syncMode = syncMode,
        confirmationsThreshold = confirmationsThreshold
    )

    constructor(
        context: Context,
        watchAddress: String,
        walletId: String,
        networkType: NetworkType = defaultNetworkType,
        peerSize: Int = defaultPeerSize,
        syncMode: SyncMode = defaultSyncMode,
        confirmationsThreshold: Int = defaultConfirmationsThreshold
    ) : this(
        context = context,
        extendedKey = null,
        watchAddress = parseAddress(watchAddress, network(networkType)),
        walletId = walletId,
        networkType = networkType,
        peerSize = peerSize,
        syncMode = syncMode,
        confirmationsThreshold = confirmationsThreshold
    )

    constructor(
        context: Context,
        extendedKey: HDExtendedKey,
        walletId: String,
        networkType: NetworkType = defaultNetworkType,
        peerSize: Int = defaultPeerSize,
        syncMode: SyncMode = defaultSyncMode,
        confirmationsThreshold: Int = defaultConfirmationsThreshold
    ) : this(
        context = context,
        extendedKey = extendedKey,
        watchAddress = null,
        walletId = walletId,
        networkType = networkType,
        peerSize = peerSize,
        syncMode = syncMode,
        confirmationsThreshold = confirmationsThreshold
    )
    /**
     * @constructor Creates and initializes the BitcoinKit
     * @param context The Android context
     * @param extendedKey HDExtendedKey that contains HDKey and version
     * @param watchAddress address for watching in read-only mode
     * @param walletId an arbitrary ID of type String.
     * @param networkType The network type. The default is MainNet.
     * @param peerSize The # of peer-nodes required. The default is 10 peers.
     * @param syncMode How the kit syncs with the blockchain. The default is SyncMode.Api().
     * @param confirmationsThreshold How many confirmations required to be considered confirmed. The default is 6 confirmations.
     */
    private constructor(
        context: Context,
        extendedKey: HDExtendedKey?,
        watchAddress: Address?,
        walletId: String,
        networkType: NetworkType,
        peerSize: Int,
        syncMode: SyncMode,
        confirmationsThreshold: Int
    ) {
        val coreDatabase = CoreDatabase.getInstance(context, getDatabaseNameCore(networkType, walletId, syncMode))
        val dashDatabase = DashKitDatabase.getInstance(context, getDatabaseName(networkType, walletId, syncMode))

        val coreStorage = Storage(coreDatabase)
        dashStorage = DashStorage(dashDatabase, coreStorage)

        network = network(networkType)

        val checkpoint = Checkpoint.resolveCheckpoint(syncMode, network, coreStorage)
        val apiSyncStateManager = ApiSyncStateManager(coreStorage, network.syncableFromApi && syncMode !is SyncMode.Full)

        val apiTransactionProvider = apiTransactionProvider(networkType, syncMode, apiSyncStateManager)

        val paymentAddressParser = PaymentAddressParser("dash", removeScheme = true)
        val instantTransactionManager = InstantTransactionManager(dashStorage, InstantSendFactory(), InstantTransactionState())

        dashTransactionInfoConverter = DashTransactionInfoConverter(instantTransactionManager)

        val blockHelper = BlockValidatorHelper(coreStorage)

        val blockValidatorSet = BlockValidatorSet()
        blockValidatorSet.addBlockValidator(ProofOfWorkValidator())

        val blockValidatorChain = BlockValidatorChain()

        if (network is MainNetDash) {
            blockValidatorChain.add(DarkGravityWaveValidator(blockHelper, heightInterval, targetTimespan, maxTargetBits, 68589))
        } else {
            blockValidatorChain.add(DarkGravityWaveTestnetValidator(targetSpacing, targetTimespan, maxTargetBits, 4002))
            blockValidatorChain.add(DarkGravityWaveValidator(blockHelper, heightInterval, targetTimespan, maxTargetBits, 4002))
        }

        blockValidatorSet.addBlockValidator(blockValidatorChain)

        val watchAddressPublicKey = watchAddress?.let {
            WatchAddressPublicKey(watchAddress.lockingScriptPayload, watchAddress.scriptType)
        }

        bitcoinCore = BitcoinCoreBuilder()
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
            .setStorage(coreStorage)
            .setBlockHeaderHasher(X11Hasher())
            .setApiTransactionProvider(apiTransactionProvider)
            .setApiSyncStateManager(apiSyncStateManager)
            .setTransactionInfoConverter(dashTransactionInfoConverter)
            .setBlockValidator(blockValidatorSet)
            .build()

        bitcoinCore.listener = this

        //  extending bitcoinCore

        bitcoinCore.addMessageParser(MasternodeListDiffMessageParser())
            .addMessageParser(TransactionLockMessageParser())
            .addMessageParser(TransactionLockVoteMessageParser())
            .addMessageParser(ISLockMessageParser())
            .addMessageParser(TransactionMessageParser())

        bitcoinCore.addMessageSerializer(GetMasternodeListDiffMessageSerializer())

        val merkleRootHasher = MerkleRootHasher()
        val merkleRootCreator = MerkleRootCreator(merkleRootHasher)
        val masternodeListMerkleRootCalculator = MasternodeListMerkleRootCalculator(merkleRootCreator)
        val masternodeCbTxHasher = MasternodeCbTxHasher(CoinbaseTransactionSerializer(), merkleRootHasher)

        val quorumListManager = QuorumListManager(dashStorage, QuorumListMerkleRootCalculator(merkleRootCreator), QuorumSortedList())
        val masternodeListManager = MasternodeListManager(
            dashStorage,
            masternodeListMerkleRootCalculator,
            masternodeCbTxHasher,
            MerkleBranch(),
            MasternodeSortedList(),
            quorumListManager
        )
        val masternodeSyncer = MasternodeListSyncer(bitcoinCore, PeerTaskFactory(), masternodeListManager, bitcoinCore.initialDownload)

        bitcoinCore.addPeerTaskHandler(masternodeSyncer)
        bitcoinCore.addPeerSyncListener(masternodeSyncer)
        bitcoinCore.addPeerGroupListener(masternodeSyncer)

        val base58AddressConverter = Base58AddressConverter(network.addressVersion, network.addressScriptVersion)
        bitcoinCore.addRestoreKeyConverter(Bip44RestoreKeyConverter(base58AddressConverter))

        val singleHasher = SingleSha256Hasher()
        val bls = BLS()
        val transactionLockVoteValidator = TransactionLockVoteValidator(dashStorage, singleHasher, bls)
        val instantSendLockValidator = InstantSendLockValidator(quorumListManager, bls)

        val transactionLockVoteManager = TransactionLockVoteManager(transactionLockVoteValidator)
        val instantSendLockManager = InstantSendLockManager(instantSendLockValidator)

        val instantSendLockHandler = InstantSendLockHandler(instantTransactionManager, instantSendLockManager)
        instantSendLockHandler.delegate = this
        val transactionLockVoteHandler = TransactionLockVoteHandler(instantTransactionManager, transactionLockVoteManager)
        transactionLockVoteHandler.delegate = this

        val instantSend = InstantSend(bitcoinCore.transactionSyncer, transactionLockVoteHandler, instantSendLockHandler)
        this.instantSend = instantSend

        bitcoinCore.addInventoryItemsHandler(instantSend)
        bitcoinCore.addPeerTaskHandler(instantSend)

        val calculator = TransactionSizeCalculator()
        val dustCalculator = DustCalculator(network.dustRelayTxFee, calculator)
        val confirmedUnspentOutputProvider = ConfirmedUnspentOutputProvider(coreStorage, confirmationsThreshold)
        bitcoinCore.prependUnspentOutputSelector(UnspentOutputSelector(calculator, dustCalculator, confirmedUnspentOutputProvider))
        bitcoinCore.prependUnspentOutputSelector(UnspentOutputSelectorSingleNoChange(calculator, dustCalculator, confirmedUnspentOutputProvider))
    }

    private fun apiTransactionProvider(
        networkType: NetworkType,
        syncMode: SyncMode,
        apiSyncStateManager: ApiSyncStateManager
    ) = when (networkType) {
        NetworkType.MainNet -> {
            val insightApiProvider = InsightApi("https://insight.dash.org/insight-api")

            if (syncMode is SyncMode.Blockchair) {
                val blockchairApi = BlockchairApi(network.blockchairChainId)
                val blockchairBlockHashFetcher = BlockchairBlockHashFetcher(blockchairApi)
                val blockchairProvider = BlockchairTransactionProvider(blockchairApi, blockchairBlockHashFetcher)

                BiApiTransactionProvider(
                    restoreProvider = insightApiProvider,
                    syncProvider = blockchairProvider,
                    syncStateManager = apiSyncStateManager
                )
            } else {
                insightApiProvider
            }
        }

        NetworkType.TestNet -> {
            InsightApi("https://testnet-insight.dash.org/insight-api")
        }
    }

    fun dashTransactions(fromUid: String? = null, type: TransactionFilterType? = null, limit: Int? = null): Single<List<DashTransactionInfo>> {
        return transactions(fromUid, type, limit).map {
            it.mapNotNull { it as? DashTransactionInfo }
        }
    }

    fun getDashTransaction(hash: String): DashTransactionInfo? {
        return getTransaction(hash) as? DashTransactionInfo
    }

    // BitcoinCore.Listener
    override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
        // check for all new transactions if it's has instant lock
        inserted.map { it.transactionHash.hexToByteArray().reversedArray() }.forEach {
            instantSend.handle(it)
        }

        listener?.onTransactionsUpdate(inserted.mapNotNull { it as? DashTransactionInfo }, updated.mapNotNull { it as? DashTransactionInfo })
    }

    override fun onTransactionsDelete(hashes: List<String>) {
        listener?.onTransactionsDelete(hashes)
    }

    override fun onBalanceUpdate(balance: BalanceInfo) {
        listener?.onBalanceUpdate(balance)
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        listener?.onLastBlockInfoUpdate(blockInfo)
    }

    override fun onKitStateUpdate(state: BitcoinCore.KitState) {
        listener?.onKitStateUpdate(state)
    }

    // IInstantTransactionDelegate
    override fun onUpdateInstant(transactionHash: ByteArray) {
        val transaction = dashStorage.getFullTransactionInfo(transactionHash) ?: return
        val transactionInfo = dashTransactionInfoConverter.transactionInfo(transaction)

        bitcoinCore.listenerExecutor.execute {
            listener?.onTransactionsUpdate(listOf(), listOf(transactionInfo))
        }
    }

    companion object {
        const val maxTargetBits: Long = 0x1e0fffff

        const val targetSpacing = 150             // 2.5 min. for mining 1 Block
        const val targetTimespan = 3600L          // 1 hour for 24 blocks
        const val heightInterval = targetTimespan / targetSpacing

        val defaultNetworkType: NetworkType = NetworkType.MainNet
        val defaultSyncMode: SyncMode = SyncMode.Api()
        const val defaultPeerSize: Int = 10
        const val defaultConfirmationsThreshold: Int = 6

        private fun getDatabaseNameCore(networkType: NetworkType, walletId: String, syncMode: SyncMode) =
            "${getDatabaseName(networkType, walletId, syncMode)}-core"

        private fun getDatabaseName(networkType: NetworkType, walletId: String, syncMode: SyncMode) =
            "Dash-${networkType.name}-$walletId-${syncMode.javaClass.simpleName}"

        private fun parseAddress(address: String, network: Network): Address {
            return Base58AddressConverter(network.addressVersion, network.addressScriptVersion).convert(address)
        }

        private fun network(networkType: NetworkType) = when (networkType) {
            NetworkType.MainNet -> MainNetDash()
            NetworkType.TestNet -> TestNetDash()
        }

        fun clear(context: Context, networkType: NetworkType, walletId: String) {
            for (syncMode in listOf(SyncMode.Api(), SyncMode.Full(), SyncMode.Blockchair())) {
                try {
                    SQLiteDatabase.deleteDatabase(context.getDatabasePath(getDatabaseNameCore(networkType, walletId, syncMode)))
                    SQLiteDatabase.deleteDatabase(context.getDatabasePath(getDatabaseName(networkType, walletId, syncMode)))
                } catch (ex: Exception) {
                    continue
                }
            }
        }
    }

}
