package com.tv.wnotes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.wnotes.data.local.WNotesDatabase
import com.tv.wnotes.data.local.entity.LinkPath
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.model.NotePathInfo
import com.tv.wnotes.data.repository.LinkPathRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LinkPathViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WNotesDatabase.getDatabase(application)
    private val repository = LinkPathRepository(database.linkPathDao())

    val allPaths: StateFlow<List<LinkPath>> = repository.allPaths
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allNotePathInfo: StateFlow<List<NotePathInfo>> = repository.allNotePathInfo
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun createStaple(name: String, notes: List<Note>, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val pathId = repository.createStaple(name)
            notes.forEach { note ->
                repository.stapleNote(pathId, note.id)
            }
            onCreated(pathId)
        }
    }

    fun getNotesInPath(pathId: Long): Flow<List<Note>> {
        return repository.getNotesInPath(pathId)
    }

    fun updateOrder(pathId: Long, notes: List<Note>) {
        viewModelScope.launch {
            repository.updateOrder(pathId, notes)
        }
    }

    fun deletePath(path: LinkPath) {
        viewModelScope.launch {
            repository.deletePath(path)
        }
    }

    fun stapleNote(noteId: Long, pathId: Long) {
        viewModelScope.launch {
            repository.stapleNote(pathId, noteId)
        }
    }

    fun stapleNotes(noteIds: List<Long>, pathId: Long) {
        viewModelScope.launch {
            noteIds.forEach { repository.stapleNote(pathId, it) }
        }
    }

    fun unstapleNote(noteId: Long) {
        viewModelScope.launch {
            repository.unstapleNote(noteId)
        }
    }

    fun getPathsForNote(noteId: Long): Flow<List<LinkPath>> {
        return repository.getPathsForNote(noteId)
    }
}
