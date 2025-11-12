package com.tinyreader.mari_reader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: LibraryManga)

    @Update
    suspend fun update(manga: LibraryManga)

    @Delete
    suspend fun delete(manga: LibraryManga)

    @Query("SELECT * FROM library_manga WHERE mangaUrl = :url")
    suspend fun getManga(url: String): LibraryManga?

    @Query("SELECT * FROM library_manga")
    fun getAllManga(): Flow<List<LibraryManga>>

    @Query("SELECT * FROM library_manga")
    suspend fun getAllMangasSync(): List<LibraryManga>
}
