package com.tinyreader.mari_reader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: Source)

    @Update
    suspend fun update(source: Source)

    @Delete
    suspend fun delete(source: Source)

    @Query("SELECT * FROM sources WHERE sourceUrl = :url")
    suspend fun getSource(url: String): Source?

    @Query("SELECT * FROM sources")
    fun getAllSources(): Flow<List<Source>>
}
