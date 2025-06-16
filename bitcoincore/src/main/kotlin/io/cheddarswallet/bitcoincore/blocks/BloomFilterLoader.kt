package io.cheddarswallet.bitcoincore.blocks

import io.cheddarswallet.bitcoincore.crypto.BloomFilter
import io.cheddarswallet.bitcoincore.managers.BloomFilterManager
import io.cheddarswallet.bitcoincore.network.peer.Peer
import io.cheddarswallet.bitcoincore.network.peer.PeerGroup
import io.cheddarswallet.bitcoincore.network.peer.PeerManager

class BloomFilterLoader(private val bloomFilterManager: BloomFilterManager, private val peerManager: PeerManager)
    : PeerGroup.Listener, BloomFilterManager.Listener {

    override fun onPeerConnect(peer: Peer) {
        bloomFilterManager.bloomFilter?.let {
            peer.filterLoad(it)
        }
    }

    override fun onFilterUpdated(bloomFilter: BloomFilter) {
        peerManager.connected().forEach {
            it.filterLoad(bloomFilter)
        }
    }
}
