package com.tv.wnotes.data.repository

import com.tv.wnotes.data.local.dao.NoteDao
import com.tv.wnotes.data.local.entity.Note
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val uncategorizedNotes: Flow<List<Note>> = noteDao.getUncategorizedNotes()
    val starredNotes: Flow<List<Note>> = noteDao.getStarredNotes()
    val trashNotes: Flow<List<Note>> = noteDao.getTrashNotes()

    suspend fun insert(note: Note) = noteDao.insertNote(note)
    suspend fun update(note: Note) = noteDao.updateNote(note)
    suspend fun delete(note: Note) = noteDao.deleteNote(note)
    suspend fun deleteById(id: Long) = noteDao.deleteNoteById(id)
    suspend fun getByIds(ids: List<Long>) = noteDao.getNotesByIds(ids)
    fun search(query: String) = noteDao.searchNotes(query)
}
