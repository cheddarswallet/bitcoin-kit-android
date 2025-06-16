package io.cheddarswallet.bitcoincore.blocks

import io.cheddarswallet.bitcoincore.network.peer.Peer

interface IPeerSyncListener {
    fun onAllPeersSynced() = Unit
    fun onPeerSynced(peer: Peer) = Unit
}
