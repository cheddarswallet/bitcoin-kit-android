package io.cheddarswallet.bitcoincore

import android.content.Context
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairApi
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairApiSyncer
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairLastBlockProvider
import io.cheddarswallet.bitcoincore.apisync.blockchair.BlockchairTransactionProvider
import io.cheddarswallet.bitcoincore.apisync.legacy.ApiSyncer
import io.cheddarswallet.bitcoincore.apisync.legacy.BlockHashDiscoveryBatch
import io.cheddarswallet.bitcoincore.apisync.legacy.BlockHashScanHelper
import io.cheddarswallet.bitcoincore.apisync.legacy.BlockHashScanner
import io.cheddarswallet.bitcoincore.apisync.legacy.IMultiAccountPublicKeyFetcher
import io.cheddarswallet.bitcoincore.apisync.legacy.IPublicKeyFetcher
import io.cheddarswallet.bitcoincore.apisync.legacy.MultiAccountPublicKeyFetcher
import io.cheddarswallet.bitcoincore.apisync.legacy.PublicKeyFetcher
import io.cheddarswallet.bitcoincore.apisync.legacy.WatchAddressBlockHashScanHelper
import io.cheddarswallet.bitcoincore.apisync.legacy.WatchPublicKeyFetcher
import io.cheddarswallet.bitcoincore.blocks.BlockDownload
import io.cheddarswallet.bitcoincore.blocks.BlockSyncer
import io.cheddarswallet.bitcoincore.blocks.Blockchain
import io.cheddarswallet.bitcoincore.blocks.BloomFilterLoader
import io.cheddarswallet.bitcoincore.blocks.InitialBlockDownload
import io.cheddarswallet.bitcoincore.blocks.MerkleBlockExtractor
import io.cheddarswallet.bitcoincore.blocks.validators.IBlockValidator
import io.cheddarswallet.bitcoincore.core.AccountWallet
import io.cheddarswallet.bitcoincore.core.BaseTransactionInfoConverter
import io.cheddarswallet.bitcoincore.core.DataProvider
import io.cheddarswallet.bitcoincore.core.DoubleSha256Hasher
import io.cheddarswallet.bitcoincore.core.IApiSyncer
import io.cheddarswallet.bitcoincore.core.IApiTransactionProvider
import io.cheddarswallet.bitcoincore.core.IHasher
import io.cheddarswallet.bitcoincore.core.IInitialDownload
import io.cheddarswallet.bitcoincore.core.IPlugin
import io.cheddarswallet.bitcoincore.core.IPrivateWallet
import io.cheddarswallet.bitcoincore.core.IPublicKeyManager
import io.cheddarswallet.bitcoincore.core.IStorage
import io.cheddarswallet.bitcoincore.core.ITransactionInfoConverter
import io.cheddarswallet.bitcoincore.core.PluginManager
import io.cheddarswallet.bitcoincore.core.TransactionDataSorterFactory
import io.cheddarswallet.bitcoincore.core.TransactionInfoConverter
import io.cheddarswallet.bitcoincore.core.Wallet
import io.cheddarswallet.bitcoincore.core.WatchAccountWallet
import io.cheddarswallet.bitcoincore.core.scriptType
import io.cheddarswallet.bitcoincore.managers.AccountPublicKeyManager
import io.cheddarswallet.bitcoincore.managers.ApiSyncStateManager
import io.cheddarswallet.bitcoincore.managers.BloomFilterManager
import io.cheddarswallet.bitcoincore.managers.ConnectionManager
import io.cheddarswallet.bitcoincore.managers.IBloomFilterProvider
import io.cheddarswallet.bitcoincore.managers.IrregularOutputFinder
import io.cheddarswallet.bitcoincore.managers.PendingOutpointsProvider
import io.cheddarswallet.bitcoincore.managers.PublicKeyManager
import io.cheddarswallet.bitcoincore.managers.RestoreKeyConverterChain
import io.cheddarswallet.bitcoincore.managers.SyncManager
import io.cheddarswallet.bitcoincore.managers.UnspentOutputProvider
import io.cheddarswallet.bitcoincore.managers.UnspentOutputSelector
import io.cheddarswallet.bitcoincore.managers.UnspentOutputSelectorChain
import io.cheddarswallet.bitcoincore.managers.UnspentOutputSelectorSingleNoChange
import io.cheddarswallet.bitcoincore.models.Checkpoint
import io.cheddarswallet.bitcoincore.models.WatchAddressPublicKey
import io.cheddarswallet.bitcoincore.network.Network
import io.cheddarswallet.bitcoincore.network.messages.AddrMessageParser
import io.cheddarswallet.bitcoincore.network.messages.FilterLoadMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.GetBlocksMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.GetDataMessageParser
import io.cheddarswallet.bitcoincore.network.messages.GetDataMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.InvMessageParser
import io.cheddarswallet.bitcoincore.network.messages.InvMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.MempoolMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.MerkleBlockMessageParser
import io.cheddarswallet.bitcoincore.network.messages.NetworkMessageParser
import io.cheddarswallet.bitcoincore.network.messages.NetworkMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.PingMessageParser
import io.cheddarswallet.bitcoincore.network.messages.PingMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.PongMessageParser
import io.cheddarswallet.bitcoincore.network.messages.PongMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.RejectMessageParser
import io.cheddarswallet.bitcoincore.network.messages.TransactionMessageParser
import io.cheddarswallet.bitcoincore.network.messages.TransactionMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.VerAckMessageParser
import io.cheddarswallet.bitcoincore.network.messages.VerAckMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.VersionMessageParser
import io.cheddarswallet.bitcoincore.network.messages.VersionMessageSerializer
import io.cheddarswallet.bitcoincore.network.peer.MempoolTransactions
import io.cheddarswallet.bitcoincore.network.peer.PeerAddressManager
import io.cheddarswallet.bitcoincore.network.peer.PeerGroup
import io.cheddarswallet.bitcoincore.network.peer.PeerManager
import io.cheddarswallet.bitcoincore.rbf.ReplacementTransactionBuilder
import io.cheddarswallet.bitcoincore.serializers.BlockHeaderParser
import io.cheddarswallet.bitcoincore.serializers.TransactionSerializer
import io.cheddarswallet.bitcoincore.transactions.BlockTransactionProcessor
import io.cheddarswallet.bitcoincore.transactions.PendingTransactionProcessor
import io.cheddarswallet.bitcoincore.transactions.SendTransactionsOnPeersSynced
import io.cheddarswallet.bitcoincore.transactions.TransactionConflictsResolver
import io.cheddarswallet.bitcoincore.transactions.TransactionCreator
import io.cheddarswallet.bitcoincore.transactions.TransactionFeeCalculator
import io.cheddarswallet.bitcoincore.transactions.TransactionInvalidator
import io.cheddarswallet.bitcoincore.transactions.TransactionSendTimer
import io.cheddarswallet.bitcoincore.transactions.TransactionSender
import io.cheddarswallet.bitcoincore.transactions.TransactionSizeCalculator
import io.cheddarswallet.bitcoincore.transactions.TransactionSyncer
import io.cheddarswallet.bitcoincore.transactions.builder.EcdsaInputSigner
import io.cheddarswallet.bitcoincore.transactions.builder.InputSetter
import io.cheddarswallet.bitcoincore.transactions.builder.LockTimeSetter
import io.cheddarswallet.bitcoincore.transactions.builder.OutputSetter
import io.cheddarswallet.bitcoincore.transactions.builder.RecipientSetter
import io.cheddarswallet.bitcoincore.transactions.builder.SchnorrInputSigner
import io.cheddarswallet.bitcoincore.transactions.builder.TransactionBuilder
import io.cheddarswallet.bitcoincore.transactions.builder.TransactionSigner
import io.cheddarswallet.bitcoincore.transactions.extractors.MyOutputsCache
import io.cheddarswallet.bitcoincore.transactions.extractors.TransactionExtractor
import io.cheddarswallet.bitcoincore.transactions.extractors.TransactionMetadataExtractor
import io.cheddarswallet.bitcoincore.transactions.extractors.TransactionOutputProvider
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType
import io.cheddarswallet.bitcoincore.utils.AddressConverterChain
import io.cheddarswallet.bitcoincore.utils.Base58AddressConverter
import io.cheddarswallet.bitcoincore.utils.PaymentAddressParser
import io.cheddarswallet.hdwalletkit.HDExtendedKey
import io.cheddarswallet.hdwalletkit.HDWallet
import io.cheddarswallet.hdwalletkit.HDWalletAccount
import io.cheddarswallet.hdwalletkit.HDWalletAccountWatch

class BitcoinCoreBuilder {

    val addressConverter = AddressConverterChain()

    // required parameters
    private var context: Context? = null
    private var extendedKey: HDExtendedKey? = null
    private var watchAddressPublicKey: WatchAddressPublicKey? = null
    private var purpose: HDWallet.Purpose? = null
    private var network: Network? = null
    private var paymentAddressParser: PaymentAddressParser? = null
    private var storage: IStorage? = null
    private var apiTransactionProvider: IApiTransactionProvider? = null
    private var blockHeaderHasher: IHasher? = null
    private var transactionInfoConverter: ITransactionInfoConverter? = null
    private var blockValidator: IBlockValidator? = null
    private var checkpoint: Checkpoint? = null
    private var apiSyncStateManager: ApiSyncStateManager? = null

    // parameters with default values
    private var confirmationsThreshold = 6
    private var syncMode: BitcoinCore.SyncMode = BitcoinCore.SyncMode.Api()
    private var peerSize = 10
    private val plugins = mutableListOf<IPlugin>()
    private var handleAddrMessage = true
    private var sendType: BitcoinCore.SendType = BitcoinCore.SendType.P2P

    fun setContext(context: Context): BitcoinCoreBuilder {
        this.context = context
        return this
    }

    fun setExtendedKey(extendedKey: HDExtendedKey?): BitcoinCoreBuilder {
        this.extendedKey = extendedKey
        return this
    }

    fun setWatchAddressPublicKey(publicKey: WatchAddressPublicKey?): BitcoinCoreBuilder {
        this.watchAddressPublicKey = publicKey
        return this
    }

    fun setPurpose(purpose: HDWallet.Purpose): BitcoinCoreBuilder {
        this.purpose = purpose
        return this
    }

    fun setNetwork(network: Network): BitcoinCoreBuilder {
        this.network = network
        return this
    }

    fun setPaymentAddressParser(paymentAddressParser: PaymentAddressParser): BitcoinCoreBuilder {
        this.paymentAddressParser = paymentAddressParser
        return this
    }

    fun setConfirmationThreshold(confirmationsThreshold: Int): BitcoinCoreBuilder {
        this.confirmationsThreshold = confirmationsThreshold
        return this
    }

    fun setSyncMode(syncMode: BitcoinCore.SyncMode): BitcoinCoreBuilder {
        this.syncMode = syncMode
        return this
    }

    fun setSendType(sendType: BitcoinCore.SendType): BitcoinCoreBuilder {
        this.sendType = sendType
        return this
    }

    fun setPeerSize(peerSize: Int): BitcoinCoreBuilder {
        if (peerSize < TransactionSender.minConnectedPeerSize) {
            throw Error("Peer size cannot be less than ${TransactionSender.minConnectedPeerSize}")
        }

        this.peerSize = peerSize
        return this
    }

    fun setStorage(storage: IStorage): BitcoinCoreBuilder {
        this.storage = storage
        return this
    }

    fun setBlockHeaderHasher(blockHeaderHasher: IHasher): BitcoinCoreBuilder {
        this.blockHeaderHasher = blockHeaderHasher
        return this
    }

    fun setApiTransactionProvider(apiTransactionProvider: IApiTransactionProvider?): BitcoinCoreBuilder {
        this.apiTransactionProvider = apiTransactionProvider
        return this
    }

    fun setTransactionInfoConverter(transactionInfoConverter: ITransactionInfoConverter): BitcoinCoreBuilder {
        this.transactionInfoConverter = transactionInfoConverter
        return this
    }

    fun setBlockValidator(blockValidator: IBlockValidator): BitcoinCoreBuilder {
        this.blockValidator = blockValidator
        return this
    }

    fun setHandleAddrMessage(handle: Boolean): BitcoinCoreBuilder {
        handleAddrMessage = handle
        return this
    }

    fun addPlugin(plugin: IPlugin): BitcoinCoreBuilder {
        plugins.add(plugin)
        return this
    }

    fun setCheckpoint(checkpoint: Checkpoint): BitcoinCoreBuilder {
        this.checkpoint = checkpoint
        return this
    }

    fun setApiSyncStateManager(apiSyncStateManager: ApiSyncStateManager): BitcoinCoreBuilder {
        this.apiSyncStateManager = apiSyncStateManager
        return this
    }

    fun build(): BitcoinCore {
        val context = checkNotNull(this.context)
        val extendedKey = this.extendedKey
        val watchAddressPublicKey = this.watchAddressPublicKey
        val purpose = checkNotNull(this.purpose)
        val network = checkNotNull(this.network)
        val paymentAddressParser = checkNotNull(this.paymentAddressParser)
        val storage = checkNotNull(this.storage)
        val apiTransactionProvider = checkNotNull(this.apiTransactionProvider)
        val checkpoint = checkNotNull(this.checkpoint)
        val apiSyncStateManager = checkNotNull(this.apiSyncStateManager)
        val blockHeaderHasher = this.blockHeaderHasher ?: DoubleSha256Hasher()
        val transactionInfoConverter = this.transactionInfoConverter ?: TransactionInfoConverter()

        val restoreKeyConverterChain = RestoreKeyConverterChain()

        val pluginManager = PluginManager()
        plugins.forEach { pluginManager.addPlugin(it) }

        transactionInfoConverter.baseConverter = BaseTransactionInfoConverter(pluginManager)

        val unspentOutputProvider = UnspentOutputProvider(storage, confirmationsThreshold, pluginManager)

        val dataProvider = DataProvider(storage, unspentOutputProvider, transactionInfoConverter)

        val connectionManager = ConnectionManager(context)

        var privateWallet: IPrivateWallet? = null
        val publicKeyFetcher: IPublicKeyFetcher
        var multiAccountPublicKeyFetcher: IMultiAccountPublicKeyFetcher? = null
        val publicKeyManager: IPublicKeyManager
        val bloomFilterProvider: IBloomFilterProvider
        val gapLimit = 20

        if (watchAddressPublicKey != null) {
            storage.savePublicKeys(listOf(watchAddressPublicKey))

            WatchAddressPublicKeyManager(watchAddressPublicKey, restoreKeyConverterChain).let {
                publicKeyFetcher = it
                publicKeyManager = it
                bloomFilterProvider = it
            }
        } else if (extendedKey != null) {
            if (!extendedKey.isPublic) {
                when (extendedKey.derivedType) {
                    HDExtendedKey.DerivedType.Master -> {
                        val wallet = Wallet(HDWallet(extendedKey.key, network.coinType, purpose), gapLimit)
                        privateWallet = wallet
                        val fetcher = MultiAccountPublicKeyFetcher(wallet)
                        publicKeyFetcher = fetcher
                        multiAccountPublicKeyFetcher = fetcher
                        PublicKeyManager.create(storage, wallet, restoreKeyConverterChain).apply {
                            publicKeyManager = this
                            bloomFilterProvider = this
                        }
                    }

                    HDExtendedKey.DerivedType.Account -> {
                        val wallet = AccountWallet(HDWalletAccount(extendedKey.key), gapLimit)
                        privateWallet = wallet
                        val fetcher = PublicKeyFetcher(wallet)
                        publicKeyFetcher = fetcher
                        AccountPublicKeyManager.create(storage, wallet, restoreKeyConverterChain).apply {
                            publicKeyManager = this
                            bloomFilterProvider = this
                        }

                    }

                    HDExtendedKey.DerivedType.Bip32 -> {
                        throw IllegalStateException("Custom Bip32 Extended Keys are not supported")
                    }
                }
            } else {
                when (extendedKey.derivedType) {
                    HDExtendedKey.DerivedType.Account -> {
                        val wallet = WatchAccountWallet(HDWalletAccountWatch(extendedKey.key), gapLimit)
                        val fetcher = WatchPublicKeyFetcher(wallet)
                        publicKeyFetcher = fetcher
                        AccountPublicKeyManager.create(storage, wallet, restoreKeyConverterChain).apply {
                            publicKeyManager = this
                            bloomFilterProvider = this
                        }

                    }

                    HDExtendedKey.DerivedType.Bip32, HDExtendedKey.DerivedType.Master -> {
                        throw IllegalStateException("Only Account Extended Public Keys are supported")
                    }
                }
            }
        } else {
            throw IllegalStateException("Both extendedKey and watchAddressPublicKey are NULL!")
        }

        val pendingOutpointsProvider = PendingOutpointsProvider(storage)

        val additionalScriptTypes = if (watchAddressPublicKey != null) listOf(ScriptType.P2PKH) else emptyList()
        val irregularOutputFinder = IrregularOutputFinder(storage, additionalScriptTypes)
        val metadataExtractor = TransactionMetadataExtractor(
            MyOutputsCache.create(storage),
            TransactionOutputProvider(storage)
        )
        val transactionExtractor = TransactionExtractor(addressConverter, storage, pluginManager, metadataExtractor)

        val conflictsResolver = TransactionConflictsResolver(storage)
        val ignorePendingIncoming = syncMode is BitcoinCore.SyncMode.Blockchair
        val pendingTransactionProcessor = PendingTransactionProcessor(
            storage,
            transactionExtractor,
            publicKeyManager,
            irregularOutputFinder,
            dataProvider,
            conflictsResolver,
            ignorePendingIncoming
        )
        val invalidator = TransactionInvalidator(storage, transactionInfoConverter, dataProvider)
        val blockTransactionProcessor = BlockTransactionProcessor(
            storage,
            transactionExtractor,
            publicKeyManager,
            irregularOutputFinder,
            dataProvider,
            conflictsResolver,
            invalidator
        )

        val peerHostManager = PeerAddressManager(network, storage)
        val bloomFilterManager = BloomFilterManager()

        val peerManager = PeerManager()

        val networkMessageParser = NetworkMessageParser(network.magic)
        val networkMessageSerializer = NetworkMessageSerializer(network.magic)

        val blockchain = Blockchain(storage, blockValidator, dataProvider)
        val blockSyncer = BlockSyncer(storage, blockchain, blockTransactionProcessor, publicKeyManager, checkpoint)


        val peerGroup = PeerGroup(
            peerHostManager,
            network,
            peerManager,
            peerSize,
            networkMessageParser,
            networkMessageSerializer,
            connectionManager,
            blockSyncer.localDownloadedBestBlockHeight,
            handleAddrMessage
        )
        peerHostManager.listener = peerGroup

        val blockHashScanHelper = if (watchAddressPublicKey == null) BlockHashScanHelper() else WatchAddressBlockHashScanHelper()
        val blockHashScanner = BlockHashScanner(restoreKeyConverterChain, apiTransactionProvider, blockHashScanHelper)

        val apiSyncer: IApiSyncer
        val initialDownload: IInitialDownload
        val merkleBlockExtractor = MerkleBlockExtractor(network.maxBlockSize)

        when (val syncMode = syncMode) {
            is BitcoinCore.SyncMode.Blockchair -> {
                val blockchairApi = if (apiTransactionProvider is BlockchairTransactionProvider) {
                    apiTransactionProvider.blockchairApi
                } else {
                    BlockchairApi(network.blockchairChainId)
                }
                val lastBlockProvider = BlockchairLastBlockProvider(blockchairApi)
                apiSyncer = BlockchairApiSyncer(
                    storage,
                    restoreKeyConverterChain,
                    apiTransactionProvider,
                    lastBlockProvider,
                    publicKeyManager,
                    blockchain,
                    apiSyncStateManager
                )
                initialDownload = BlockDownload(blockSyncer, peerManager, merkleBlockExtractor)
            }

            else -> {
                val blockDiscovery = BlockHashDiscoveryBatch(blockHashScanner, publicKeyFetcher, checkpoint.block.height, gapLimit)
                apiSyncer = ApiSyncer(
                    storage,
                    blockDiscovery,
                    publicKeyManager,
                    multiAccountPublicKeyFetcher,
                    apiSyncStateManager
                )
                initialDownload = InitialBlockDownload(blockSyncer, peerManager, merkleBlockExtractor)
            }
        }

        val syncManager = SyncManager(connectionManager, apiSyncer, peerGroup, storage, syncMode, blockSyncer.localDownloadedBestBlockHeight)
        apiSyncer.listener = syncManager
        connectionManager.listener = syncManager
        blockSyncer.listener = syncManager
        initialDownload.listener = syncManager
        blockHashScanner.listener = syncManager

        val unspentOutputSelector = UnspentOutputSelectorChain(unspentOutputProvider)
        val pendingTransactionSyncer = TransactionSyncer(storage, pendingTransactionProcessor, invalidator, publicKeyManager)
        val transactionDataSorterFactory = TransactionDataSorterFactory()

        var dustCalculator: DustCalculator? = null
        var transactionSizeCalculator: TransactionSizeCalculator? = null
        var transactionFeeCalculator: TransactionFeeCalculator? = null
        var transactionSender: TransactionSender? = null
        var transactionCreator: TransactionCreator? = null
        var replacementTransactionBuilder: ReplacementTransactionBuilder? = null

        if (privateWallet != null) {
            val ecdsaInputSigner = EcdsaInputSigner(privateWallet, network)
            val schnorrInputSigner = SchnorrInputSigner(privateWallet)
            val transactionSizeCalculatorInstance = TransactionSizeCalculator()
            val dustCalculatorInstance = DustCalculator(network.dustRelayTxFee, transactionSizeCalculatorInstance)
            val recipientSetter = RecipientSetter(addressConverter, pluginManager)
            val outputSetter = OutputSetter(transactionDataSorterFactory)
            val inputSetter = InputSetter(
                unspentOutputSelector,
                publicKeyManager,
                addressConverter,
                purpose.scriptType,
                transactionSizeCalculatorInstance,
                pluginManager,
                dustCalculatorInstance,
                transactionDataSorterFactory
            )
            val lockTimeSetter = LockTimeSetter(storage)
            val transactionBuilder = TransactionBuilder(recipientSetter, outputSetter, inputSetter, lockTimeSetter)
            transactionFeeCalculator = TransactionFeeCalculator(
                recipientSetter,
                inputSetter,
                addressConverter,
                publicKeyManager,
                purpose.scriptType,
            )
            val transactionSendTimer = TransactionSendTimer(60)
            val transactionSenderInstance = TransactionSender(
                pendingTransactionSyncer,
                peerManager,
                initialDownload,
                storage,
                transactionSendTimer,
                sendType,
                TransactionSerializer
            )

            dustCalculator = dustCalculatorInstance
            transactionSizeCalculator = transactionSizeCalculatorInstance
            transactionSender = transactionSenderInstance

            transactionSendTimer.listener = transactionSender
            val signer = TransactionSigner(ecdsaInputSigner, schnorrInputSigner)
            transactionCreator = TransactionCreator(transactionBuilder, pendingTransactionProcessor, transactionSenderInstance, signer, bloomFilterManager)
            replacementTransactionBuilder = ReplacementTransactionBuilder(
                storage, transactionSizeCalculator, dustCalculator, metadataExtractor, pluginManager, unspentOutputProvider, publicKeyManager, conflictsResolver, lockTimeSetter
            )
        }

        val bitcoinCore = BitcoinCore(
            storage,
            dataProvider,
            publicKeyManager,
            addressConverter,
            restoreKeyConverterChain,
            transactionCreator,
            transactionFeeCalculator,
            replacementTransactionBuilder,
            paymentAddressParser,
            syncManager,
            purpose,
            peerManager,
            dustCalculator,
            pluginManager,
            connectionManager
        )

        dataProvider.listener = bitcoinCore
        syncManager.listener = bitcoinCore

        val watchedTransactionManager = WatchedTransactionManager()
        bloomFilterManager.addBloomFilterProvider(watchedTransactionManager)
        bloomFilterManager.addBloomFilterProvider(bloomFilterProvider)
        bloomFilterManager.addBloomFilterProvider(pendingOutpointsProvider)
        bloomFilterManager.addBloomFilterProvider(irregularOutputFinder)

        bitcoinCore.watchedTransactionManager = watchedTransactionManager
        pendingTransactionProcessor.transactionListener = watchedTransactionManager
        blockTransactionProcessor.transactionListener = watchedTransactionManager

        bitcoinCore.peerGroup = peerGroup
        bitcoinCore.transactionSyncer = pendingTransactionSyncer
        bitcoinCore.networkMessageParser = networkMessageParser
        bitcoinCore.networkMessageSerializer = networkMessageSerializer
        bitcoinCore.unspentOutputSelector = unspentOutputSelector

        peerGroup.peerTaskHandler = bitcoinCore.peerTaskHandlerChain
        peerGroup.inventoryItemsHandler = bitcoinCore.inventoryItemsHandlerChain

        bitcoinCore.prependAddressConverter(Base58AddressConverter(network.addressVersion, network.addressScriptVersion))

        // this part can be moved to another place

        bitcoinCore.addMessageParser(AddrMessageParser())
            .addMessageParser(MerkleBlockMessageParser(BlockHeaderParser(blockHeaderHasher)))
            .addMessageParser(InvMessageParser())
            .addMessageParser(GetDataMessageParser())
            .addMessageParser(PingMessageParser())
            .addMessageParser(PongMessageParser())
            .addMessageParser(TransactionMessageParser())
            .addMessageParser(VerAckMessageParser())
            .addMessageParser(VersionMessageParser())
            .addMessageParser(RejectMessageParser())

        bitcoinCore.addMessageSerializer(FilterLoadMessageSerializer())
            .addMessageSerializer(GetBlocksMessageSerializer())
            .addMessageSerializer(InvMessageSerializer())
            .addMessageSerializer(GetDataMessageSerializer())
            .addMessageSerializer(MempoolMessageSerializer())
            .addMessageSerializer(PingMessageSerializer())
            .addMessageSerializer(PongMessageSerializer())
            .addMessageSerializer(TransactionMessageSerializer())
            .addMessageSerializer(VerAckMessageSerializer())
            .addMessageSerializer(VersionMessageSerializer())

        val bloomFilterLoader = BloomFilterLoader(bloomFilterManager, peerManager)
        bloomFilterManager.listener = bloomFilterLoader
        bitcoinCore.addPeerGroupListener(bloomFilterLoader)

        // todo: now this part cannot be moved to another place since bitcoinCore requires initialBlockDownload to be set. find solution to do so
        bitcoinCore.initialDownload = initialDownload
        bitcoinCore.addPeerTaskHandler(initialDownload)
        bitcoinCore.addInventoryItemsHandler(initialDownload)
        bitcoinCore.addPeerGroupListener(initialDownload)


        val mempoolTransactions = MempoolTransactions(pendingTransactionSyncer, transactionSender)
        bitcoinCore.addPeerTaskHandler(mempoolTransactions)
        bitcoinCore.addInventoryItemsHandler(mempoolTransactions)
        bitcoinCore.addPeerGroupListener(mempoolTransactions)

        transactionSender?.let {
            bitcoinCore.addPeerSyncListener(SendTransactionsOnPeersSynced(transactionSender))
            bitcoinCore.addPeerTaskHandler(transactionSender)
        }

        if (transactionSizeCalculator != null && dustCalculator != null) {
            bitcoinCore.prependUnspentOutputSelector(
                UnspentOutputSelector(
                    transactionSizeCalculator,
                    dustCalculator,
                    unspentOutputProvider
                )
            )
            bitcoinCore.prependUnspentOutputSelector(
                UnspentOutputSelectorSingleNoChange(
                    transactionSizeCalculator,
                    dustCalculator,
                    unspentOutputProvider
                )
            )
        }

        return bitcoinCore
    }
}
