package com.tinyreader.mari_reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Source::class, LibraryManga::class, Chapter::class, ReadingHistory::class], version = 4, exportSchema = false)
abstract class MariReaderDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingHistoryDao(): ReadingHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MariReaderDatabase? = null

        // Migration from version 3 -> 4: add profileJson column to sources
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add a nullable TEXT column 'profileJson' to 'sources'
                database.execSQL("ALTER TABLE sources ADD COLUMN profileJson TEXT")
            }
        }

        fun getDatabase(context: Context): MariReaderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MariReaderDatabase::class.java,
                    "mari_reader_database"
                ).addMigrations(MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
