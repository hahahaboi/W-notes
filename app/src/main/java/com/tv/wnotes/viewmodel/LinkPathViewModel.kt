package com.tv.wnotes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.wnotes.data.local.WNotesDatabase
import com.tv.wnotes.data.local.entity.LinkPath
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.model.GroupPathInfo
import com.tv.wnotes.data.local.model.NotePathInfo
import com.tv.wnotes.data.repository.LinkPathRepository
import com.tv.wnotes.ui.home.HomeItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LinkPathViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WNotesDatabase.getDatabase(application)
    private val repository = LinkPathRepository(database.linkPathDao())

    val allPaths: StateFlow<List<LinkPath>> = repository.allPaths
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allNotePathInfo: StateFlow<List<NotePathInfo>> = repository.allNotePathInfo
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allGroupPathInfo: StateFlow<List<GroupPathInfo>> = repository.allGroupPathInfo
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun createStaple(name: String, items: List<HomeItem>, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val pathId = repository.createStaple(name)
            items.forEach { item ->
                when (item) {
                    is HomeItem.NoteItem -> repository.stapleNote(pathId, item.note.id)
                    is HomeItem.GroupItem -> repository.stapleGroup(pathId, item.groupWithNotes.group.id)
                }
            }
            onCreated(pathId)
        }
    }

    fun getItemsInPath(pathId: Long): Flow<List<HomeItem>> {
        val membersFlow = repository.getMembersInPath(pathId)
        val allNotesFlow = database.noteDao().getAllNotes()
        val allGroupsFlow = database.noteGroupDao().getAllGroupsWithNotes()

        return combine(membersFlow, allNotesFlow, allGroupsFlow) { members, notes, groups ->
            members.mapNotNull { member ->
                if (member.noteId != null) {
                    notes.find { it.id == member.noteId }?.let { HomeItem.NoteItem(it) }
                } else if (member.groupId != null) {
                    groups.find { it.group.id == member.groupId }?.let { HomeItem.GroupItem(it) }
                } else null
            }
        }
    }

    fun updateOrder(pathId: Long, items: List<HomeItem>) {
        viewModelScope.launch {
            repository.updateOrder(pathId, items)
        }
    }

    fun stapleGroup(groupId: Long, pathId: Long) {
        viewModelScope.launch {
            repository.stapleGroup(pathId, groupId)
        }
    }

    fun unstapleGroup(groupId: Long) {
        viewModelScope.launch {
            repository.unstapleGroup(groupId)
        }
    }

    fun getNotesInPath(pathId: Long): Flow<List<Note>> {
        return repository.getNotesInPath(pathId)
    }

    fun createStapleWithIds(name: String, noteIds: List<Long>) {
        viewModelScope.launch {
            val pathId = repository.createStaple(name)
            noteIds.forEach { repository.stapleNote(pathId, it) }
        }
    }

    fun deletePath(path: LinkPath) {
        viewModelScope.launch {
            repository.deletePath(path)
        }
    }

    fun renameStaple(path: LinkPath, newName: String) {
        viewModelScope.launch {
            repository.renameStaple(path, newName)
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

    fun insertNoteInPath(pathId: Long, noteId: Long, index: Int) {
        viewModelScope.launch {
            val currentItems = getItemsInPath(pathId).first()
            val mutableItems = currentItems.toMutableList()
            
            // Check if note is already in the path (to avoid duplicates if called multiple times)
            if (mutableItems.any { it is HomeItem.NoteItem && it.note.id == noteId }) {
                return@launch
            }

            val note = database.noteDao().getNoteById(noteId)
            if (note != null) {
                if (index >= 0 && index <= mutableItems.size) {
                    mutableItems.add(index, HomeItem.NoteItem(note))
                } else {
                    mutableItems.add(HomeItem.NoteItem(note))
                }
                repository.updateOrder(pathId, mutableItems)
            }
        }
    }

    fun getPathsForNote(noteId: Long): Flow<List<LinkPath>> {
        return repository.getPathsForNote(noteId)
    }
}
