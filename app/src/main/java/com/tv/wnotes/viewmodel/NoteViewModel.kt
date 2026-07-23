package com.tv.wnotes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.wnotes.data.local.WNotesDatabase
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WNotesDatabase.getDatabase(application)
    private val repository = NoteRepository(database.noteDao())

    val uncategorizedNotes: StateFlow<List<Note>> = repository.uncategorizedNotes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allNotes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val starredNotes: StateFlow<List<Note>> = repository.starredNotes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val trashNotes: StateFlow<List<Note>> = repository.trashNotes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<Note>> = _searchQuery
        .debounce(300L)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedNoteIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedNoteIds: StateFlow<Set<Long>> = _selectedNoteIds

    fun toggleSelection(noteId: Long) {
        _selectedNoteIds.value = if (_selectedNoteIds.value.contains(noteId)) {
            _selectedNoteIds.value - noteId
        } else {
            _selectedNoteIds.value + noteId
        }
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
    }

    fun addToSelection(noteId: Long) {
        _selectedNoteIds.value = _selectedNoteIds.value + noteId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getNoteById(id: Long): Note? {
        return database.noteDao().getNoteById(id)
    }

    suspend fun getNotesByIds(ids: List<Long>): List<Note> {
        return repository.getByIds(ids)
    }

    fun addNote(title: String, content: String, folderId: Long? = null) {
        viewModelScope.launch {
            val note = Note(
                title = title.ifEmpty { 
                    if (content.isNotEmpty()) content.take(40) else "Untitled" 
                }, 
                content = content, 
                folderId = folderId
            )
            repository.insert(note)
        }
    }

    suspend fun createNoteAndReturnId(title: String, content: String, folderId: Long? = null): Long {
        val note = Note(
            title = title.ifEmpty {
                if (content.isNotEmpty()) content.take(40) else "Untitled"
            },
            content = content,
            folderId = folderId
        )
        return repository.insert(note)
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun moveToTrash(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
        }
    }

    fun moveNotesToTrash(notes: List<Note>) {
        viewModelScope.launch {
            notes.forEach { note ->
                repository.update(note.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
            }
        }
    }

    fun restoreFromTrash(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(
                isDeleted = false, 
                deletedAt = null,
                isRough = false // Requirements: Remove Rough badge and convert to normal note
            ))
        }
    }

    fun toggleStar(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(isStarred = !note.isStarred, updatedAt = System.currentTimeMillis()))
        }
    }

    fun starNotes(notes: List<Note>) {
        viewModelScope.launch {
            notes.forEach { note ->
                repository.update(note.copy(isStarred = true, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun moveNotesToFolder(notes: List<Note>, folderId: Long?) {
        viewModelScope.launch {
            notes.forEach { note ->
                repository.update(note.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun duplicate(note: Note) {
        viewModelScope.launch {
            repository.insert(
                note.copy(
                    id = 0,
                    title = "${note.title} Copy",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    deletedAt = null
                )
            )
        }
    }

    fun rename(note: Note, title: String) {
        viewModelScope.launch {
            repository.update(note.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deletePermanently(note: Note) {
        viewModelScope.launch {
            repository.deleteById(note.id)
        }
    }

    fun deletePermanently(notes: List<Note>) {
        viewModelScope.launch {
            notes.forEach { repository.deleteById(it.id) }
        }
    }
}
