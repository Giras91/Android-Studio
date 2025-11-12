package com.tinyreader.mari_reader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_manga")
data class LibraryManga(
    @PrimaryKey val mangaUrl: String, // The URL of the manga's main page
    val sourceUrl: String, // FK to Source
    val title: String,
    val coverImageUrl: String?,
    val description: String?,
    val isFavorite: Boolean = false
)
