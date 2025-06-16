package io.cheddarswallet.bitcoincore.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import io.cheddarswallet.bitcoincore.models.BlockHashPublicKey

@Dao
interface BlockHashPublicKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(users: List<BlockHashPublicKey>)

}
