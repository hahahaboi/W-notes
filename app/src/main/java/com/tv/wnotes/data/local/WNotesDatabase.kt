package com.tv.wnotes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tv.wnotes.data.local.dao.FolderDao
import com.tv.wnotes.data.local.dao.LinkPathDao
import com.tv.wnotes.data.local.dao.NoteDao
import com.tv.wnotes.data.local.entity.Folder
import com.tv.wnotes.data.local.entity.LinkPath
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.PathMember

@Database(
    entities = [Note::class, Folder::class, LinkPath::class, PathMember::class],
    version = 4,
    exportSchema = false
)
abstract class WNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun linkPathDao(): LinkPathDao

    companion object {
        @Volatile
        private var INSTANCE: WNotesDatabase? = null

        fun getDatabase(context: Context): WNotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WNotesDatabase::class.java,
                    "wnotes_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
