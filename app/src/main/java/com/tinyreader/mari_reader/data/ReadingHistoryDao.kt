package com.tinyreader.mari_reader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ReadingHistory)

    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC")
    fun getAllHistory(): Flow<List<ReadingHistory>>

    @Query("SELECT * FROM reading_history WHERE mangaUrl = :mangaUrl ORDER BY lastReadAt DESC LIMIT 1")
    suspend fun getLastReadChapter(mangaUrl: String): ReadingHistory?
}
