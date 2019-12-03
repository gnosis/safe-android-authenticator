package io.gnosis.safe.authenticator.db

import androidx.room.*
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.security.db.EncryptedByteArray

@Database(
    entities = [
        TokenInfoDb::class
    ], version = 1
)
@TypeConverters(BigIntegerConverter::class, SolidityAddressConverter::class, EncryptedByteArray.Converter::class)
abstract class TokensDatabase : RoomDatabase() {
    abstract fun tokenInfoDao(): TokenInfoDao

    companion object {
        const val DB_NAME = "safe_tokens_db"
    }
}


@Entity(tableName = TokenInfoDb.TABLE_NAME)
data class TokenInfoDb(
    @PrimaryKey
    @ColumnInfo(name = COL_ADDRESS)
    val address: Solidity.Address,

    @ColumnInfo(name = COL_SYMBOL)
    val symbol: String,

    @ColumnInfo(name = COL_NAME)
    val name: String,

    @ColumnInfo(name = COL_DECIMALS)
    val decimals: Int,

    @ColumnInfo(name = COL_ICON)
    val icon: String,

    @ColumnInfo(name = COL_LAST_UPDATE)
    val lastUpdate: Long
) {
    companion object {
        const val TABLE_NAME = "token_info"
        const val COL_ADDRESS = "address"
        const val COL_SYMBOL = "symbol"
        const val COL_NAME = "name"
        const val COL_DECIMALS = "decimals"
        const val COL_ICON = "icon"
        const val COL_LAST_UPDATE = "last_update"
    }
}


@Dao
interface TokenInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: TokenInfoDb)

    @Query("SELECT * FROM ${TokenInfoDb.TABLE_NAME} WHERE ${TokenInfoDb.COL_ADDRESS} = :address")
    suspend fun load(address: Solidity.Address): TokenInfoDb?

    @Query("DELETE FROM ${TokenInfoDb.TABLE_NAME} WHERE ${TokenInfoDb.COL_ADDRESS} = :address")
    fun delete(address: Solidity.Address)
}
