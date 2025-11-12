package com.tinyreader.mari_reader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class Source(
    @PrimaryKey val sourceUrl: String,
    val name: String,
    val iconUrl: String? = null, // Favicon
    val profileJson: String? = null // Optional JSON scraper profile for site-specific scraping rules
)
