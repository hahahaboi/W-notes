package com.tv.wnotes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.wnotes.data.local.WNotesDatabase
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.pref.PreferenceManager
import com.tv.wnotes.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RoughNoteViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WNotesDatabase.getDatabase(application)
    private val repository = NoteRepository(database.noteDao(), database.noteTabDao(), database.annotationDao(), database.markerDao())
    private val prefManager = PreferenceManager(application)

    val floatingX = prefManager.floatingButtonX
    val floatingY = prefManager.floatingButtonY
    val isCollapsed = prefManager.isCollapsed
    val dockedEdge = prefManager.dockedEdge

    private val _lastRoughNoteContent = MutableStateFlow("")
    val lastRoughNoteContent: StateFlow<String> = _lastRoughNoteContent

    fun savePosition(x: Float, y: Float) {
        viewModelScope.launch {
            prefManager.savePosition(x, y)
        }
    }

    fun saveCollapsedState(collapsed: Boolean, edge: String? = null) {
        viewModelScope.launch {
            prefManager.saveCollapsedState(collapsed, edge)
        }
    }

    fun createRoughNote(content: String) {
        if (content.isBlank()) return
        _lastRoughNoteContent.value = content
        viewModelScope.launch {
            val note = Note(
                title = content.take(40).ifEmpty { "Rough Note" },
                content = content,
                isRough = true,
                isDeleted = true, // Requirements say move to Trash folder automatically
                deletedAt = System.currentTimeMillis()
            )
            repository.insert(note)
        }
    }

    fun restoreLastRoughNote() {
        // This is handled by the UI reopening the pill with content
    }
}
