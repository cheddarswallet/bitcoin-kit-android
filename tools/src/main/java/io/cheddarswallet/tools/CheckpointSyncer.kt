package io.cheddarswallet.tools

import io.cheddarswallet.bitcoincore.core.DoubleSha256Hasher
import io.cheddarswallet.bitcoincore.core.IConnectionManager
import io.cheddarswallet.bitcoincore.core.IConnectionManagerListener
import io.cheddarswallet.bitcoincore.extensions.toReversedHex
import io.cheddarswallet.bitcoincore.models.Block
import io.cheddarswallet.bitcoincore.network.Network
import io.cheddarswallet.bitcoincore.network.messages.GetHeadersMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.HeadersMessageParser
import io.cheddarswallet.bitcoincore.network.messages.HeadersMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.InvMessageParser
import io.cheddarswallet.bitcoincore.network.messages.InvMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.NetworkMessageParser
import io.cheddarswallet.bitcoincore.network.messages.NetworkMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.PingMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.VerAckMessageParser
import io.cheddarswallet.bitcoincore.network.messages.VerAckMessageSerializer
import io.cheddarswallet.bitcoincore.network.messages.VersionMessageParser
import io.cheddarswallet.bitcoincore.network.messages.VersionMessageSerializer
import io.cheddarswallet.bitcoincore.network.peer.IPeerTaskHandler
import io.cheddarswallet.bitcoincore.network.peer.Peer
import io.cheddarswallet.bitcoincore.network.peer.PeerGroup
import io.cheddarswallet.bitcoincore.network.peer.PeerManager
import io.cheddarswallet.bitcoincore.network.peer.task.GetBlockHeadersTask
import io.cheddarswallet.bitcoincore.network.peer.task.PeerTask
import io.cheddarswallet.bitcoincore.storage.BlockHeader
import io.cheddarswallet.dashkit.MainNetDash
import io.cheddarswallet.dashkit.TestNetDash
import io.cheddarswallet.dashkit.X11Hasher
import java.util.LinkedList
import java.util.concurrent.Executors

class CheckpointSyncer(
    private val network: Network,
    private val checkpointInterval: Int,
    private val blocksToKeep: Int,
    private val listener: Listener)
    : PeerGroup.Listener, IPeerTaskHandler {

    interface Listener {
        fun onSync(network: Network, checkpoints: List<Block>)
    }

    var isSynced: Boolean = false
        private set

    @Volatile
    private var syncPeer: Peer? = null
    private val peersQueue = Executors.newSingleThreadExecutor()
    private val peerManager = PeerManager()

    private val peerSize = 2
    private val peerGroup: PeerGroup

    private val lastCheckpointBlock = network.lastCheckpoint.block
    private val checkpoints = mutableListOf(lastCheckpointBlock)
    private val blocks = LinkedList(
        (listOf(lastCheckpointBlock) + network.lastCheckpoint.additionalBlocks).reversed()
    )

    init {
        val blockHeaderHasher = when (network) {
            is TestNetDash,
            is MainNetDash -> X11Hasher()
            else -> DoubleSha256Hasher()
        }

        val networkMessageParser = NetworkMessageParser(network.magic).apply {
            add(VersionMessageParser())
            add(VerAckMessageParser())
            add(InvMessageParser())
            add(HeadersMessageParser(blockHeaderHasher))
        }

        val networkMessageSerializer = NetworkMessageSerializer(network.magic).apply {
            add(VersionMessageSerializer())
            add(VerAckMessageSerializer())
            add(InvMessageSerializer())
            add(GetHeadersMessageSerializer())
            add(HeadersMessageSerializer())
            add(PingMessageSerializer())
        }

        val connectionManager = object : IConnectionManager {
            override val listener: IConnectionManagerListener? = null
            override val isConnected = true

            override fun onEnterForeground() {
            }

            override fun onEnterBackground() {
            }
        }

        val peerHostManager = PeerAddressManager(network)
        peerGroup = PeerGroup(peerHostManager, network, peerManager, peerSize, networkMessageParser, networkMessageSerializer, connectionManager, 0, false).also {
            peerHostManager.listener = it
        }

        peerGroup.addPeerGroupListener(this)
        peerGroup.peerTaskHandler = this
    }

    fun start() {
        isSynced = false
        peerGroup.start()
        print("Started syncer")
    }

    //  PeerGroup Listener

    override fun onPeerConnect(peer: Peer) {
        print("onPeerConnect ${peer.host}")
        assignNextSyncPeer()
    }

    override fun onPeerDisconnect(peer: Peer, e: Exception?) {
        print("onPeerDisconnect, error: ${e?.message} ${e?.javaClass?.simpleName}")
        if (peer == syncPeer) {
            syncPeer = null
            assignNextSyncPeer()
        }
    }

    override fun onPeerReady(peer: Peer) {
        if (peer == syncPeer) {
            downloadBlockchain()
        }
    }

    //  IPeerTaskHandler

    override fun handleCompletedTask(peer: Peer, task: PeerTask): Boolean {
        if (task is GetBlockHeadersTask) {
            validateHeaders(peer, task.blockHeaders)
            return true
        }

        return false
    }

    private fun validateHeaders(peer: Peer, headers: Array<BlockHeader>) {
        var prevBlock = blocks.last()

        for (header in headers) {
            if (!prevBlock.headerHash.contentEquals(header.previousBlockHeaderHash)) {
                syncPeer = null
                assignNextSyncPeer()
                break
            }

            val newBlock = Block(header, prevBlock.height + 1)
            if (newBlock.height % checkpointInterval == 0) {
                print("Checkpoint block ${header.hash.toReversedHex()} at height ${newBlock.height}, time ${header.timestamp}")
                checkpoints.add(newBlock)
            }

            blocks.add(newBlock)
            prevBlock = newBlock
        }

        if (headers.size < 2000) {
            peer.synced = true
        }

        downloadBlockchain()
    }

    private fun assignNextSyncPeer() {
        peersQueue.execute {
            print("assignNextSyncPeer, connected peers: ${peerManager.connected().size}")
            print("synced peers: ${peerManager.connected().count { it.synced }}")

            if (peerManager.connected().all { it.synced }) {
                isSynced = true
                peerGroup.stop()
                print("Synced")

                val lastCheckpoint = checkpoints.last()
                val blocksPrecedingCheckpoint = blocks.dropLastWhile { it.height > lastCheckpoint.height }

                listener.onSync(network, blocksPrecedingCheckpoint.reversed().take(blocksToKeep))
            } else if (syncPeer == null) {
                val notSyncedPeers = peerManager.sorted().filter { !it.synced }
                notSyncedPeers.firstOrNull { it.ready }?.let { nonSyncedPeer ->
                    syncPeer = nonSyncedPeer

                    downloadBlockchain()
                }
            }
        }
    }

    private fun downloadBlockchain() {
        val peer = syncPeer
        if (peer == null || !peer.ready) {
            return
        }

        if (peer.synced) {
            syncPeer = null
            assignNextSyncPeer()
        } else {
            peer.addTask(GetBlockHeadersTask(getBlockLocatorHashes()))
        }
    }

    private fun getBlockLocatorHashes(): List<ByteArray> {
        return if (blocks.isEmpty()) {
            listOf(checkpoints.last().headerHash)
        } else {
            listOf(blocks.last().headerHash)
        }
    }

    private fun print(message: String) {
        println("${network.javaClass.simpleName}: $message")
    }
}
