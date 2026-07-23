package com.tv.wnotes.data.repository

import com.tv.wnotes.data.local.dao.LinkPathDao
import com.tv.wnotes.data.local.entity.LinkPath
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.PathMember
import com.tv.wnotes.data.local.model.NotePathInfo
import kotlinx.coroutines.flow.Flow

class LinkPathRepository(private val linkPathDao: LinkPathDao) {
    val allPaths: Flow<List<LinkPath>> = linkPathDao.getAllPaths()
    val allNotePathInfo: Flow<List<NotePathInfo>> = linkPathDao.getAllNotePathInfo()

    suspend fun createStaple(name: String): Long {
        return linkPathDao.insertPath(LinkPath(name = name))
    }

    private suspend fun stapleNoteInternal(pathId: Long, noteId: Long) {
        val maxOrder = linkPathDao.getMaxOrderIndex(pathId) ?: -1
        linkPathDao.insertMember(PathMember(pathId = pathId, noteId = noteId, orderIndex = maxOrder + 1))
    }

    suspend fun stapleNote(pathId: Long, noteId: Long) {
        linkPathDao.deleteMembersByNoteId(noteId)
        stapleNoteInternal(pathId, noteId)
    }

    suspend fun unstapleNote(noteId: Long) {
        linkPathDao.deleteMembersByNoteId(noteId)
    }

    fun getNotesInPath(pathId: Long): Flow<List<Note>> {
        return linkPathDao.getNotesInPath(pathId)
    }

    fun getPathsForNote(noteId: Long): Flow<List<LinkPath>> {
        return linkPathDao.getPathsForNote(noteId)
    }

    suspend fun updateOrder(pathId: Long, notes: List<Note>) {
        val members = notes.mapIndexed { index, note ->
            PathMember(pathId = pathId, noteId = note.id, orderIndex = index)
        }
        linkPathDao.updatePathOrder(pathId, members)
    }

    suspend fun deletePath(path: LinkPath) {
        linkPathDao.deletePath(path)
    }
}
