package io.cheddarswallet.bitcoincore.storage

import androidx.room.*
import io.cheddarswallet.bitcoincore.models.PublicKey

@Dao
interface PublicKeyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(keys: List<PublicKey>)

    @Delete
    fun delete(publicKey: PublicKey)

    @Query("select * from PublicKey")
    fun getAll(): List<PublicKey>

    @Query("""
        SELECT k.*, COUNT(o.publicKeyPath) c1, COUNT(bhp.publicKeyPath) c2  FROM PublicKey AS k 
        LEFT JOIN TransactionOutput o ON o.publicKeyPath = k.path 
        LEFT JOIN BlockHashPublicKey bhp on bhp.publicKeyPath = k.path
        GROUP BY k.path 
        HAVING c1 > 0 OR c2 > 0
        """)
    fun getAllUsed(): List<PublicKey>

    @Query("""
        SELECT k.*, COUNT(o.publicKeyPath) c1, COUNT(bhp.publicKeyPath) c2 FROM PublicKey AS k 
        LEFT JOIN TransactionOutput o ON o.publicKeyPath = k.path 
        LEFT JOIN BlockHashPublicKey bhp on bhp.publicKeyPath = k.path
        GROUP BY k.path 
        HAVING c1 = 0 AND c2 = 0
        """)
    fun getAllUnused(): List<PublicKey>

    @Query("""
        SELECT k.*, (COUNT(o.publicKeyPath) + COUNT(bhp.publicKeyPath)) usedCount FROM PublicKey AS k 
        LEFT JOIN TransactionOutput o ON o.publicKeyPath = k.path 
        LEFT JOIN BlockHashPublicKey bhp on bhp.publicKeyPath = k.path
        GROUP BY k.path 
        """)
    fun getAllWithUsedState(): List<PublicKeyWithUsedState>

    @Query("SELECT * from PublicKey where scriptHashP2WPKH = :keyHash limit 1")
    fun getByScriptHashWPKH(keyHash: ByteArray): PublicKey?

    @Query("SELECT * from PublicKey where publicKey = :keyHash or publicKeyHash =:keyHash limit 1")
    fun getByKeyOrKeyHash(keyHash: ByteArray): PublicKey?

    @Query("SELECT * from PublicKey where convertedForP2TR = :hashP2TR limit 1")
    fun getByHashP2TR(hashP2TR: ByteArray): PublicKey?
}
