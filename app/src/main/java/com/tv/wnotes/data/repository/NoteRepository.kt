package com.tv.wnotes.data.repository

import com.tv.wnotes.data.local.dao.NoteDao
import com.tv.wnotes.data.local.dao.NoteTabDao
import com.tv.wnotes.data.local.dao.AnnotationDao
import com.tv.wnotes.data.local.dao.MarkerDao
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.NoteTab
import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.data.local.entity.TextMarker
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val noteTabDao: NoteTabDao,
    private val annotationDao: AnnotationDao,
    private val markerDao: MarkerDao
) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val uncategorizedNotes: Flow<List<Note>> = noteDao.getUncategorizedNotes()
    val starredNotes: Flow<List<Note>> = noteDao.getStarredNotes()
    val trashNotes: Flow<List<Note>> = noteDao.getTrashNotes()
    val lockedNotes: Flow<List<Note>> = noteDao.getLockedNotes()

    val allMarkers: Flow<List<TextMarker>> = markerDao.getAllMarkers()
    val allAnnotations: Flow<List<ContextAnnotation>> = annotationDao.getAllAnnotations()
    val allTabs: Flow<List<NoteTab>> = noteTabDao.getAllTabs()

    suspend fun insert(note: Note) = noteDao.insertNote(note)
    suspend fun update(note: Note) = noteDao.updateNote(note)
    suspend fun updateTitleAndFolder(id: Long, title: String, folderId: Long?) = 
        noteDao.updateTitleAndFolder(id, title, folderId, System.currentTimeMillis())
    suspend fun updateContent(id: Long, content: String) = 
        noteDao.updateContent(id, content, System.currentTimeMillis())
    suspend fun updateGoal(id: Long, hasGoal: Boolean, goalBlockId: String?, goalOffset: Int, goalTargetLength: Int) =
        noteDao.updateGoal(id, hasGoal, goalBlockId, goalOffset, goalTargetLength, System.currentTimeMillis())
    suspend fun delete(note: Note) = noteDao.deleteNote(note)
    suspend fun deleteById(id: Long) = noteDao.deleteNoteById(id)
    suspend fun getByIds(ids: List<Long>) = noteDao.getNotesByIds(ids)
    suspend fun getNoteById(id: Long) = noteDao.getNoteById(id)
    fun search(query: String) = noteDao.searchNotes(query)

    // Tab methods
    fun getTabsForNote(noteId: Long) = noteTabDao.getTabsForNote(noteId)
    suspend fun getTabsForNoteSync(noteId: Long) = noteTabDao.getTabsForNoteSync(noteId)
    suspend fun insertTab(tab: NoteTab) = noteTabDao.insertTab(tab)
    suspend fun updateTab(tab: NoteTab) = noteTabDao.updateTab(tab)
    suspend fun deleteTab(tab: NoteTab) = noteTabDao.deleteTab(tab)
    suspend fun getTabById(id: Long) = noteTabDao.getTabById(id)

    // Annotation methods
    fun getAnnotationsForTab(tabId: Long) = annotationDao.getAnnotationsForTab(tabId)
    suspend fun getAnnotationsForTabSync(tabId: Long) = annotationDao.getAnnotationsForTabSync(tabId)
    suspend fun insertAnnotation(annotation: ContextAnnotation) = annotationDao.insertAnnotation(annotation)
    suspend fun updateAnnotation(annotation: ContextAnnotation) = annotationDao.updateAnnotation(annotation)
    suspend fun deleteAnnotation(annotation: ContextAnnotation) = annotationDao.deleteAnnotation(annotation)
    suspend fun deleteAnnotationById(id: String) = annotationDao.deleteAnnotationById(id)

    // Marker methods
    fun getMarkersForTab(tabId: Long) = markerDao.getMarkersForTab(tabId)
    suspend fun getMarkersForTabSync(tabId: Long) = markerDao.getMarkersForTabSync(tabId)
    suspend fun insertMarker(marker: TextMarker) = markerDao.insertMarker(marker)
    suspend fun updateMarker(marker: TextMarker) = markerDao.updateMarker(marker)
    suspend fun deleteMarker(marker: TextMarker) = markerDao.deleteMarker(marker)
    suspend fun deleteMarkerById(id: String) = markerDao.deleteMarkerById(id)

}
