package com.tv.wnotes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.wnotes.data.local.WNotesDatabase
import com.tv.wnotes.data.local.entity.Folder
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.repository.FolderRepository
import com.tv.wnotes.data.repository.NoteRepository
import com.tv.wnotes.ui.home.HomeItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FolderStats(
    val noteCount: Int,
    val subfolderCount: Int
)

class FolderViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WNotesDatabase.getDatabase(application)
    private val folderRepository = FolderRepository(database.folderDao())
    private val noteRepository = NoteRepository(database.noteDao(), database.noteTabDao(), database.annotationDao())

    val allFolders: StateFlow<List<Folder>> = folderRepository.allFolders
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folderStats: StateFlow<Map<Long, FolderStats>> = combine(
        folderRepository.allFolders,
        noteRepository.allNotes,
        database.noteGroupDao().getAllGroupsWithNotes()
    ) { folders, notes, groups ->
        folders.associate { folder ->
            // Match getItemsInFolder logic for consistency
            val nCount = notes.count { it.folderId == folder.id && !it.isDeleted && !it.isLocked }
            val gCount = groups.count { groupWithNotes ->
                val group = groupWithNotes.group
                (group.folderId == folder.id || groupWithNotes.notes.any { it.folderId == folder.id }) && 
                !group.isDeleted && !group.isLocked
            }
            
            // Direct immediate subfolders only
            val sCount = folders.count { it.parentFolderId == folder.id }
            
            folder.id to FolderStats(nCount + gCount, sCount)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

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

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            folderRepository.update(folder.copy(name = newName))
        }
    }

    fun addFolder(name: String, parentFolderId: Long? = null, onCreated: (Long) -> Unit = { _ -> }) {
        viewModelScope.launch {
            val id = folderRepository.insert(Folder(name = name, parentFolderId = parentFolderId))
            onCreated(id)
        }
    }

    fun getItemsInFolder(folderId: Long): Flow<List<HomeItem>> {
        val notesFlow = database.noteDao().getNotesByFolder(folderId)
        val groupsFlow = database.noteGroupDao().getAllGroupsWithNotes()
        val groupedNoteIdsFlow = database.noteGroupDao().getAllGroupedNoteIds()

        return combine(notesFlow, groupsFlow, groupedNoteIdsFlow) { notes, groups, groupedIds ->
            val groupedIdsSet = groupedIds.toSet()
            val noteItems = notes
                .filter { it.id !in groupedIdsSet && !it.isDeleted && !it.isLocked }
                .map { HomeItem.NoteItem(it) }
            val groupItems = groups
                .filter { (it.group.folderId == folderId || it.notes.any { n -> n.folderId == folderId }) && !it.group.isDeleted && !it.group.isLocked }
                .map { HomeItem.GroupItem(it) }
            
            (noteItems + groupItems).sortedByDescending { it.timestamp }
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            folderRepository.delete(folder)
        }
    }
}
