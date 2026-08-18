package com.tv.wnotes.data.local.dao

import androidx.room.*
import com.tv.wnotes.data.local.entity.NoteGroup
import com.tv.wnotes.data.local.entity.NoteGroupMember
import com.tv.wnotes.data.local.model.GroupWithNotes
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteGroupDao {
    @Transaction
    @Query("SELECT * FROM note_groups ORDER BY isPinned DESC, createdAt DESC")
    fun getAllGroupsWithNotes(): Flow<List<GroupWithNotes>>

    @Transaction
    @Query("SELECT * FROM note_groups WHERE id = :groupId")
    fun getGroupWithNotesById(groupId: Long): Flow<GroupWithNotes?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: NoteGroup): Long

    @Update
    suspend fun updateGroup(group: NoteGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<NoteGroupMember>)

    @Query("DELETE FROM note_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("DELETE FROM note_group_members WHERE groupId = :groupId")
    suspend fun removeAllMembers(groupId: Long)

    @Query("UPDATE note_groups SET name = :newName, updatedAt = :updatedAt WHERE id = :groupId")
    suspend fun renameGroup(groupId: Long, newName: String, updatedAt: Long = System.currentTimeMillis())
    
    @Query("SELECT noteId FROM note_group_members")
    fun getAllGroupedNoteIds(): Flow<List<Long>>

    @Transaction
    suspend fun createGroupWithNotes(name: String, noteIds: List<Long>) {
        val groupId = insertGroup(NoteGroup(name = name))
        val members = noteIds.mapIndexed { index, noteId ->
            NoteGroupMember(groupId = groupId, noteId = noteId, orderIndex = index)
        }
        insertMembers(members)
    }

    @Transaction
    suspend fun updateGroupOrder(groupId: Long, noteIds: List<Long>) {
        removeAllMembers(groupId)
        val members = noteIds.mapIndexed { index, noteId ->
            NoteGroupMember(groupId = groupId, noteId = noteId, orderIndex = index)
        }
        insertMembers(members)
    }
}
