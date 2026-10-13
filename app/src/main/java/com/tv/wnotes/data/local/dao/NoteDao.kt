package com.tv.wnotes.data.local.dao

import androidx.room.*
import com.tv.wnotes.data.local.entity.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isLocked = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId IS NULL AND isDeleted = 0 AND isLocked = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getUncategorizedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isStarred = 1 AND isDeleted = 0 AND isLocked = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getStarredNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 AND isLocked = 0 ORDER BY deletedAt DESC")
    fun getTrashNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND isDeleted = 0 AND isLocked = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByFolder(folderId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isLocked = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getLockedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getNotesByIds(ids: List<Long>): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Query("UPDATE notes SET title = :title, folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitleAndFolder(id: Long, title: String, folderId: Long?, updatedAt: Long)

    @Query("UPDATE notes SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, updatedAt: Long)

    @Query("UPDATE notes SET hasGoal = :hasGoal, goalBlockId = :goalBlockId, goalOffset = :goalOffset, goalTargetLength = :goalTargetLength, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGoal(id: Long, hasGoal: Boolean, goalBlockId: String?, goalOffset: Int, goalTargetLength: Int, updatedAt: Long)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("SELECT * FROM notes WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') AND isDeleted = 0 AND isLocked = 0")
    fun searchNotes(query: String): Flow<List<Note>>
}
