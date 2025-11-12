package com.tinyreader.mari_reader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey val chapterUrl: String, // The URL to the chapter's reader page
    val mangaUrl: String, // FK to LibraryManga
    val title: String,
    val chapterNumber: Float = -1f,
    val dateUploaded: Long = 0,
    val isRead: Boolean = false
)
