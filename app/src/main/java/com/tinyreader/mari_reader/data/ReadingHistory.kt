package com.tinyreader.mari_reader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_history")
data class ReadingHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaUrl: String,
    val chapterUrl: String,
    val lastReadAt: Long
)
