package com.tv.wnotes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.wnotes.data.local.WNotesDatabase
import com.tv.wnotes.data.local.entity.Folder
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.repository.FolderRepository
import com.tv.wnotes.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FolderViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WNotesDatabase.getDatabase(application)
    private val folderRepository = FolderRepository(database.folderDao())
    private val noteRepository = NoteRepository(database.noteDao())

    val allFolders: StateFlow<List<Folder>> = folderRepository.allFolders
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedFolderIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFolderIds: StateFlow<Set<Long>> = _selectedFolderIds

    fun toggleSelection(folderId: Long) {
        _selectedFolderIds.value = if (_selectedFolderIds.value.contains(folderId)) {
            _selectedFolderIds.value - folderId
        } else {
            _selectedFolderIds.value + folderId
        }
    }

    fun clearSelection() {
        _selectedFolderIds.value = emptySet()
    }

    fun moveFoldersToFolder(folderIds: Set<Long>, targetFolderId: Long?) {
        viewModelScope.launch {
            folderIds.forEach { id ->
                allFolders.value.find { it.id == id }?.let {
                    folderRepository.update(it.copy(parentFolderId = targetFolderId))
                }
            }
        }
    }

    fun addFolder(name: String, parentFolderId: Long? = null, onCreated: (Long) -> Unit = { _ -> }) {
        viewModelScope.launch {
            val id = folderRepository.insert(Folder(name = name, parentFolderId = parentFolderId))
            onCreated(id)
        }
    }

    fun getNotesInFolder(folderId: Long): Flow<List<Note>> {
        return database.noteDao().getNotesByFolder(folderId)
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            folderRepository.delete(folder)
        }
    }
}
