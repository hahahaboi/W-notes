package com.tv.wnotes.data.repository

import com.tv.wnotes.data.local.dao.LinkPathDao
import com.tv.wnotes.data.local.entity.LinkPath
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.PathMember
import com.tv.wnotes.data.local.model.GroupPathInfo
import com.tv.wnotes.data.local.model.NotePathInfo
import kotlinx.coroutines.flow.Flow

class LinkPathRepository(private val linkPathDao: LinkPathDao) {
    val allPaths: Flow<List<LinkPath>> = linkPathDao.getAllPaths()
    val allNotePathInfo: Flow<List<NotePathInfo>> = linkPathDao.getAllNotePathInfo()
    val allGroupPathInfo: Flow<List<GroupPathInfo>> = linkPathDao.getAllGroupPathInfo()

    suspend fun createStaple(name: String): Long {
        return linkPathDao.insertPath(LinkPath(name = name))
    }

    suspend fun stapleNote(pathId: Long, noteId: Long) {
        linkPathDao.deleteMembersByNoteId(noteId)
        val maxOrder = linkPathDao.getMaxOrderIndex(pathId) ?: -1
        linkPathDao.insertMember(PathMember(pathId = pathId, noteId = noteId, orderIndex = maxOrder + 1))
    }

    suspend fun stapleGroup(pathId: Long, groupId: Long) {
        linkPathDao.deleteMembersByGroupId(groupId)
        val maxOrder = linkPathDao.getMaxOrderIndex(pathId) ?: -1
        linkPathDao.insertMember(PathMember(pathId = pathId, groupId = groupId, orderIndex = maxOrder + 1))
    }

    suspend fun unstapleNote(noteId: Long) {
        linkPathDao.deleteMembersByNoteId(noteId)
    }

    suspend fun unstapleGroup(groupId: Long) {
        linkPathDao.deleteMembersByGroupId(groupId)
    }

    fun getNotesInPath(pathId: Long): Flow<List<Note>> {
        return linkPathDao.getNotesInPath(pathId)
    }

    fun getMembersInPath(pathId: Long): Flow<List<PathMember>> {
        return linkPathDao.getMembersInPath(pathId)
    }

    fun getPathsForNote(noteId: Long): Flow<List<LinkPath>> {
        return linkPathDao.getPathsForNote(noteId)
    }

    suspend fun updateOrder(pathId: Long, items: List<com.tv.wnotes.ui.home.HomeItem>) {
        val members = items.mapIndexed { index, item ->
            when (item) {
                is com.tv.wnotes.ui.home.HomeItem.NoteItem -> 
                    PathMember(pathId = pathId, noteId = item.note.id, orderIndex = index)
                is com.tv.wnotes.ui.home.HomeItem.GroupItem -> 
                    PathMember(pathId = pathId, groupId = item.groupWithNotes.group.id, orderIndex = index)
            }
        }
        linkPathDao.updatePathOrder(pathId, members)
    }

    suspend fun deletePath(path: LinkPath) {
        linkPathDao.deletePath(path)
    }

    suspend fun renameStaple(path: LinkPath, newName: String) {
        linkPathDao.updatePath(path.copy(name = newName))
    }
}
