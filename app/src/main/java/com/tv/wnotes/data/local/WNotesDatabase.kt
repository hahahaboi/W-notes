package com.tv.wnotes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tv.wnotes.data.local.dao.FolderDao
import com.tv.wnotes.data.local.dao.LinkPathDao
import com.tv.wnotes.data.local.dao.NoteDao
import com.tv.wnotes.data.local.dao.NoteGroupDao
import com.tv.wnotes.data.local.dao.NoteTabDao
import com.tv.wnotes.data.local.dao.AnnotationDao
import com.tv.wnotes.data.local.dao.MarkerDao
import com.tv.wnotes.data.local.entity.*

@Database(
    entities = [Note::class, Folder::class, LinkPath::class, PathMember::class, NoteGroup::class, NoteGroupMember::class, NoteTab::class, ContextAnnotation::class, TextMarker::class],
    version = 11,
    exportSchema = false
)
abstract class WNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun linkPathDao(): LinkPathDao
    abstract fun noteGroupDao(): NoteGroupDao
    abstract fun noteTabDao(): NoteTabDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun markerDao(): MarkerDao

    companion object {
        @Volatile
        private var INSTANCE: WNotesDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `note_tabs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `noteId` INTEGER NOT NULL, 
                        `title` TEXT, 
                        `content` TEXT NOT NULL, 
                        `orderIndex` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tabs_noteId` ON `note_tabs` (`noteId`)")
                
                // Data migration: Create an initial tab for each note
                database.execSQL("""
                    INSERT INTO note_tabs (noteId, content, orderIndex, createdAt, updatedAt) 
                    SELECT id, content, 0, createdAt, updatedAt FROM notes
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE text_markers ADD COLUMN type TEXT NOT NULL DEFAULT 'DEFAULT'")
            }
        }

        fun getDatabase(context: Context): WNotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WNotesDatabase::class.java,
                    "wnotes_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
