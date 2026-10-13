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
    version = 18,
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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `text_markers` (
                        `id` TEXT NOT NULL, 
                        `tabId` INTEGER NOT NULL, 
                        `startBlockId` TEXT NOT NULL, 
                        `startOffset` INTEGER NOT NULL, 
                        `endBlockId` TEXT NOT NULL, 
                        `endOffset` INTEGER NOT NULL, 
                        `anchorText` TEXT NOT NULL, 
                        `label` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`tabId`) REFERENCES `note_tabs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_text_markers_tabId` ON `text_markers` (`tabId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Bridge migration to version 10
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Check if type column exists before adding it
                val cursor = database.query("PRAGMA table_info(text_markers)")
                var hasType = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "type") {
                        hasType = true
                        break
                    }
                }
                cursor.close()

                if (!hasType) {
                    database.execSQL("ALTER TABLE text_markers ADD COLUMN type TEXT NOT NULL DEFAULT 'DEFAULT'")
                }
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Check if isRough column exists before adding it
                val cursorNotes = database.query("PRAGMA table_info(notes)")
                var hasIsRough = false
                while (cursorNotes.moveToNext()) {
                    if (cursorNotes.getString(cursorNotes.getColumnIndexOrThrow("name")) == "isRough") {
                        hasIsRough = true
                        break
                    }
                }
                cursorNotes.close()

                if (!hasIsRough) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN isRough INTEGER NOT NULL DEFAULT 0")
                }
                
                // Create context_annotations table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `context_annotations` (
                        `id` TEXT NOT NULL, 
                        `tabId` INTEGER NOT NULL, 
                        `blockId` TEXT NOT NULL, 
                        `startOffset` INTEGER NOT NULL, 
                        `endOffset` INTEGER NOT NULL, 
                        `anchorText` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `metadata` TEXT, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`tabId`) REFERENCES `note_tabs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_context_annotations_tabId` ON `context_annotations` (`tabId`)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Check if isRough column exists before adding it
                val cursor = database.query("PRAGMA table_info(notes)")
                var hasIsRough = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "isRough") {
                        hasIsRough = true
                        break
                    }
                }
                cursor.close()

                if (!hasIsRough) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN isRough INTEGER NOT NULL DEFAULT 0")
                }

                // Create context_annotations table if it doesn't exist
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `context_annotations` (
                        `id` TEXT NOT NULL, 
                        `tabId` INTEGER NOT NULL, 
                        `blockId` TEXT NOT NULL, 
                        `startOffset` INTEGER NOT NULL, 
                        `endOffset` INTEGER NOT NULL, 
                        `anchorText` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `metadata` TEXT, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`tabId`) REFERENCES `note_tabs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_context_annotations_tabId` ON `context_annotations` (`tabId`)")
            }
        }

        val MIGRATION_13_15 = object : Migration(13, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No schema changes required for this version jump.
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `tab_ties`")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema catch-up: ensure context_annotations and text_markers match the required hash
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Force metadata refresh and drop obsolete tables
                database.execSQL("DROP TABLE IF EXISTS `tab_ties`")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info(notes)")
                var hasGoalCol = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "hasGoal") {
                        hasGoalCol = true
                        break
                    }
                }
                cursor.close()

                if (!hasGoalCol) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN hasGoal INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE notes ADD COLUMN goalBlockId TEXT")
                    database.execSQL("ALTER TABLE notes ADD COLUMN goalOffset INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE notes ADD COLUMN goalTargetLength INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun getDatabase(context: Context): WNotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WNotesDatabase::class.java,
                    "wnotes_database"
                )
                    .addMigrations(
                        MIGRATION_7_8, 
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11, 
                        MIGRATION_11_12, 
                        MIGRATION_12_13,
                        MIGRATION_13_15,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
