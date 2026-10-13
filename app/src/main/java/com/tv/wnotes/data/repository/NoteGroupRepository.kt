package com.tv.wnotes.data.repository

import com.tv.wnotes.data.local.dao.NoteGroupDao
import com.tv.wnotes.data.local.entity.NoteGroup
import com.tv.wnotes.data.local.model.GroupWithNotes
import kotlinx.coroutines.flow.Flow

class NoteGroupRepository(private val noteGroupDao: NoteGroupDao) {
    val allGroupsWithNotes: Flow<List<GroupWithNotes>> = noteGroupDao.getAllGroupsWithNotes()
    val allGroupedNoteIds: Flow<List<Long>> = noteGroupDao.getAllGroupedNoteIds()

    fun getGroupWithNotesById(groupId: Long): Flow<GroupWithNotes?> = noteGroupDao.getGroupWithNotesById(groupId)

    suspend fun createGroup(name: String, noteIds: List<Long>) {
        noteGroupDao.createGroupWithNotes(name, noteIds)
    }

    suspend fun deleteGroup(groupId: Long) {
        noteGroupDao.deleteGroup(groupId)
    }

    suspend fun updateGroup(group: NoteGroup) {
        noteGroupDao.updateGroup(group)
    }

    suspend fun renameGroup(groupId: Long, newName: String) {
        noteGroupDao.renameGroup(groupId, newName)
    }

    suspend fun updateGroupOrder(groupId: Long, noteIds: List<Long>) {
        noteGroupDao.updateGroupOrder(groupId, noteIds)
    }
}
