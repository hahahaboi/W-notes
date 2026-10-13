package com.tv.wnotes.data.local.dao

import androidx.room.*
import com.tv.wnotes.data.local.entity.NoteTab
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteTabDao {
    @Query("SELECT * FROM note_tabs")
    fun getAllTabs(): Flow<List<NoteTab>>

    @Query("SELECT * FROM note_tabs WHERE noteId = :noteId ORDER BY orderIndex ASC")
    fun getTabsForNote(noteId: Long): Flow<List<NoteTab>>

    @Query("SELECT * FROM note_tabs WHERE noteId = :noteId ORDER BY orderIndex ASC")
    suspend fun getTabsForNoteSync(noteId: Long): List<NoteTab>

    @Query("SELECT * FROM note_tabs WHERE id = :id")
    suspend fun getTabById(id: Long): NoteTab?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: NoteTab): Long

    @Update
    suspend fun updateTab(tab: NoteTab)

    @Delete
    suspend fun deleteTab(tab: NoteTab)

    @Query("DELETE FROM note_tabs WHERE noteId = :noteId")
    suspend fun deleteTabsByNoteId(noteId: Long)
}
