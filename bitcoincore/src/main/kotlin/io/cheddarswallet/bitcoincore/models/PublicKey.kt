package io.cheddarswallet.bitcoincore.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import io.cheddarswallet.bitcoincore.core.IStorage
import io.cheddarswallet.bitcoincore.transactions.scripts.OpCodes
import io.cheddarswallet.bitcoincore.utils.Utils
import io.cheddarswallet.hdwalletkit.ECKey

@Entity(
    primaryKeys = ["path"],
    indices = [
        Index("publicKey"),
        Index("publicKeyHash"),
        Index("scriptHashP2WPKH"),
        Index("convertedForP2TR")
    ]
)

open class PublicKey() {
    var path: String = ""

    var account = 0

    @ColumnInfo(name = "address_index")
    var index = 0
    var external = true

    var publicKeyHash = byteArrayOf()
    var publicKey = byteArrayOf()
    var scriptHashP2WPKH = byteArrayOf()
    var convertedForP2TR = byteArrayOf()

    fun used(storage: IStorage): Boolean {
        return storage.getOutputsOfPublicKey(this).isNotEmpty()
    }

    constructor(account: Int, index: Int, external: Boolean, publicKey: ByteArray, publicKeyHash: ByteArray) : this() {
        this.path = "$account/${if (external) 0 else 1}/$index"
        this.account = account
        this.index = index
        this.external = external
        this.publicKey = publicKey
        this.publicKeyHash = publicKeyHash

        val version = 0
        val redeemScript = OpCodes.push(version) + OpCodes.push(this.publicKeyHash)
        this.scriptHashP2WPKH = Utils.sha256Hash160(redeemScript)
        this.convertedForP2TR = ECKey(publicKey).tweakedOutputKey.pubKeyXCoord
    }

    override fun equals(other: Any?): Boolean {
        return other is PublicKey && other.path == path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}
