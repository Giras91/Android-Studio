package com.tinyreader.mari_reader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: Chapter)

    @Update
    suspend fun update(chapter: Chapter)

    @Delete
    suspend fun delete(chapter: Chapter)

    @Query("SELECT * FROM chapters WHERE mangaUrl = :mangaUrl")
    fun getChapters(mangaUrl: String): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE mangaUrl = :mangaUrl")
    suspend fun getChaptersForMangaSync(mangaUrl: String): List<Chapter>
}
