package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corriente.data.db.entity.ImportAliasEntity
import com.corriente.data.db.entity.ImportAliasKind

@Dao
interface ImportAliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: ImportAliasEntity)

    @Query("SELECT * FROM import_alias WHERE source_app = :sourceApp AND kind = :kind")
    suspend fun getForApp(sourceApp: String, kind: ImportAliasKind): List<ImportAliasEntity>
}
