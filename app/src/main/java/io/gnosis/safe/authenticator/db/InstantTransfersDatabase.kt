package io.gnosis.safe.authenticator.db

import androidx.room.*
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.security.db.EncryptedByteArray
import java.math.BigInteger

@Database(
    entities = [
        InstantTransferDb::class
    ], version = 1
)
@TypeConverters(BigIntegerConverter::class, SolidityAddressConverter::class, EncryptedByteArray.Converter::class)
abstract class InstantTransfersDatabase : RoomDatabase() {
    abstract fun instantTransferDao(): InstantTransferDao

    companion object {
        const val DB_NAME = "safe_instant_transfers_db"
    }
}


@Entity(tableName = InstantTransferDb.TABLE_NAME)
data class InstantTransferDb(
    @PrimaryKey
    @ColumnInfo(name = COL_TX_HASH)
    val txHash: String,

    @ColumnInfo(name = COL_TOKEN)
    val token: Solidity.Address,

    @ColumnInfo(name = COL_TO)
    val to: Solidity.Address,

    @ColumnInfo(name = COL_VALUE)
    val value: BigInteger,

    @ColumnInfo(name = COL_NONCE)
    val nonce: BigInteger
) {
    companion object {
        const val TABLE_NAME = "instant_transfers"
        const val COL_TO = "address"
        const val COL_TOKEN = "token"
        const val COL_VALUE = "value"
        const val COL_NONCE = "nonce"
        const val COL_TX_HASH = "tx_hash"
    }
}


@Dao
interface InstantTransferDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entry: InstantTransferDb)

    @Query("SELECT * FROM ${InstantTransferDb.TABLE_NAME} ORDER BY ${InstantTransferDb.COL_NONCE}")
    suspend fun load(): List<InstantTransferDb>

    @Query("DELETE FROM ${InstantTransferDb.TABLE_NAME} WHERE ${InstantTransferDb.COL_TX_HASH} = :hash")
    fun delete(hash: String)
}
