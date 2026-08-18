package com.tv.wnotes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.wnotes.data.local.WNotesDatabase
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.NoteTab
import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.data.local.entity.TextMarker
import com.tv.wnotes.data.local.model.NotePathInfo
import com.tv.wnotes.data.pref.PreferenceManager
import com.tv.wnotes.data.repository.NoteGroupRepository
import com.tv.wnotes.data.repository.NoteRepository
import com.tv.wnotes.ui.home.HomeItem
import com.tv.wnotes.util.SmartSearchEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class UncategorizedFilter(val label: String) {
    ALL("All"),
    UNPINNED("Unpinned"),
    UNFOLDERED("No Folder"),
    UNSTAPLED("Not Stapled"),
    UNSTARRED("Unstarred")
}

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WNotesDatabase.getDatabase(application)
    private val repository = NoteRepository(database.noteDao(), database.noteTabDao(), database.annotationDao(), database.markerDao())
    private val noteGroupRepository = NoteGroupRepository(database.noteGroupDao())
    private val linkPathDao = database.linkPathDao()
    private val preferenceManager = PreferenceManager(application)

    private val _currentUncategorizedFilter = MutableStateFlow(UncategorizedFilter.UNFOLDERED)
    val currentUncategorizedFilter: StateFlow<UncategorizedFilter> = _currentUncategorizedFilter

    val allNotesItems: StateFlow<List<HomeItem>> = combine(
        repository.allNotes,
        noteGroupRepository.allGroupsWithNotes,
        noteGroupRepository.allGroupedNoteIds
    ) { notes, groups, groupedIds ->
        val groupedIdsSet = groupedIds.toSet()
        val noteItems = notes
            .filter { it.id !in groupedIdsSet && !it.isDeleted && !it.isLocked }
            .map { HomeItem.NoteItem(it) }
        val groupItems = groups
            .filter { !it.group.isDeleted && !it.group.isLocked }
            .map { HomeItem.GroupItem(it) }
        (noteItems + groupItems).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pinnedItems: StateFlow<List<HomeItem>> = combine(
        repository.allNotes,
        noteGroupRepository.allGroupsWithNotes,
        noteGroupRepository.allGroupedNoteIds
    ) { notes, groups, groupedIds ->
        val groupedIdsSet = groupedIds.toSet()
        val noteItems = notes
            .filter { it.id !in groupedIdsSet && it.isPinned && !it.isDeleted && !it.isLocked }
            .map { HomeItem.NoteItem(it) }
        val groupItems = groups
            .filter { it.group.isPinned && !it.group.isDeleted && !it.group.isLocked }
            .map { HomeItem.GroupItem(it) }
        (noteItems + groupItems).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val uncategorizedItems: StateFlow<List<HomeItem>> = combine(
        repository.allNotes,
        noteGroupRepository.allGroupsWithNotes,
        noteGroupRepository.allGroupedNoteIds,
        linkPathDao.getAllNotePathInfo(),
        _currentUncategorizedFilter
    ) { allNotes, groups, groupedIds, pathInfo, filter ->
        val groupedIdsSet = groupedIds.toSet()
        val stapledNoteIds = pathInfo.map { it.noteId }.toSet()
        
        val noteItems = allNotes.filter { note ->
            if (note.id in groupedIdsSet) return@filter false
            when (filter) {
                UncategorizedFilter.ALL -> {
                    note.folderId == null && !note.isPinned && !note.isStarred && !stapledNoteIds.contains(note.id)
                }
                UncategorizedFilter.UNPINNED -> !note.isPinned
                UncategorizedFilter.UNFOLDERED -> note.folderId == null
                UncategorizedFilter.UNSTAPLED -> !stapledNoteIds.contains(note.id)
                UncategorizedFilter.UNSTARRED -> !note.isStarred
            }
        }.map { HomeItem.NoteItem(it) }

        val groupItems = groups.filter { groupWithNotes ->
            val group = groupWithNotes.group
            when (filter) {
                UncategorizedFilter.ALL -> {
                    group.folderId == null && !group.isPinned && !group.isStarred
                }
                UncategorizedFilter.UNPINNED -> !group.isPinned
                UncategorizedFilter.UNFOLDERED -> group.folderId == null
                UncategorizedFilter.UNSTAPLED -> true // Group staples not fully tracked in info yet?
                UncategorizedFilter.UNSTARRED -> !group.isStarred
            }
        }.map { HomeItem.GroupItem(it) }

        (noteItems + groupItems).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allNotes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val homeItems: StateFlow<List<HomeItem>> = combine(
        repository.allNotes,
        noteGroupRepository.allGroupsWithNotes,
        noteGroupRepository.allGroupedNoteIds
    ) { notes, groups, groupedIds ->
        val groupedIdsSet = groupedIds.toSet()
        val noteItems = notes
            .filter { it.id !in groupedIdsSet && !it.isDeleted && !it.isLocked }
            .map { HomeItem.NoteItem(it) }
        val groupItems = groups
            .filter { !it.group.isDeleted && !it.group.isLocked }
            .map { HomeItem.GroupItem(it) }
        
        (noteItems + groupItems).sortedWith(
            compareByDescending<HomeItem> { item ->
                when (item) {
                    is HomeItem.NoteItem -> item.note.isPinned
                    is HomeItem.GroupItem -> item.groupWithNotes.group.isPinned
                }
            }.thenByDescending { it.timestamp }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val starredItems: StateFlow<List<HomeItem>> = combine(
        repository.starredNotes,
        noteGroupRepository.allGroupsWithNotes,
        noteGroupRepository.allGroupedNoteIds
    ) { notes, groups, groupedIds ->
        val groupedIdsSet = groupedIds.toSet()
        val noteItems = notes
            .filter { it.id !in groupedIdsSet && !it.isDeleted && !it.isLocked }
            .map { HomeItem.NoteItem(it) }
        val groupItems = groups
            .filter { (it.group.isStarred || it.notes.any { n -> n.isStarred }) && !it.group.isDeleted && !it.group.isLocked }
            .map { HomeItem.GroupItem(it) }
        (noteItems + groupItems).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val trashItems: StateFlow<List<HomeItem>> = combine(
        repository.trashNotes,
        noteGroupRepository.allGroupsWithNotes,
        noteGroupRepository.allGroupedNoteIds
    ) { notes, groups, groupedIds ->
        val groupedIdsSet = groupedIds.toSet()
        val noteItems = notes
            .filter { it.id !in groupedIdsSet }
            .map { HomeItem.NoteItem(it) }
        val groupItems = groups
            .filter { it.group.isDeleted }
            .map { HomeItem.GroupItem(it) }
        (noteItems + groupItems).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val lockedItems: StateFlow<List<HomeItem>> = combine(
        repository.lockedNotes,
        noteGroupRepository.allGroupsWithNotes,
        noteGroupRepository.allGroupedNoteIds
    ) { notes, groups, groupedIds ->
        val groupedIdsSet = groupedIds.toSet()
        val noteItems = notes
            .filter { it.id !in groupedIdsSet }
            .map { HomeItem.NoteItem(it) }
        val groupItems = groups
            .filter { (it.group.isLocked || it.notes.any { n -> n.isLocked }) && !it.group.isDeleted }
            .map { HomeItem.GroupItem(it) }
        (noteItems + groupItems).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val lockedPin: StateFlow<String?> = preferenceManager.lockedPin
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<HomeItem>> = combine(
        repository.allNotes,
        noteGroupRepository.allGroupsWithNotes,
        noteGroupRepository.allGroupedNoteIds,
        _searchQuery.debounce(300L)
    ) { notes, groups, groupedIds, query ->
        if (query.isBlank()) emptyList()
        else {
            val groupedIdsSet = groupedIds.toSet()
            val noteItems = SmartSearchEngine.filter(notes, query)
                .filter { it.id !in groupedIdsSet && !it.isDeleted && !it.isLocked }
                .map { HomeItem.NoteItem(it) }
            
            val groupItems = groups.filter { groupWithNotes ->
                groupWithNotes.group.name.contains(query, ignoreCase = true) ||
                groupWithNotes.notes.any { it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true) }
            }.filter { !it.group.isDeleted && !it.group.isLocked }
            .map { HomeItem.GroupItem(it) }

            (noteItems + groupItems).sortedByDescending { it.timestamp }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedNoteIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedNoteIds: StateFlow<Set<Long>> = _selectedNoteIds

    private val _selectedGroupIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedGroupIds: StateFlow<Set<Long>> = _selectedGroupIds

    val selectionState: StateFlow<SelectionState> = combine(
        _selectedNoteIds,
        _selectedGroupIds
    ) { notes, groups ->
        when {
            notes.isEmpty() && groups.isEmpty() -> SelectionState.None
            notes.size == 1 && groups.isEmpty() -> SelectionState.SingleNote(notes.first())
            notes.isEmpty() && groups.size == 1 -> SelectionState.SingleGroup(groups.first())
            notes.size > 1 && groups.isEmpty() -> SelectionState.MultipleNotes(notes)
            else -> SelectionState.MultipleItems(notes, groups)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SelectionState.None)

    fun toggleSelection(noteId: Long) {
        _selectedNoteIds.value = if (_selectedNoteIds.value.contains(noteId)) {
            _selectedNoteIds.value - noteId
        } else {
            _selectedNoteIds.value + noteId
        }
    }

    fun toggleGroupSelection(groupId: Long) {
        _selectedGroupIds.value = if (_selectedGroupIds.value.contains(groupId)) {
            _selectedGroupIds.value - groupId
        } else {
            _selectedGroupIds.value + groupId
        }
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
        _selectedGroupIds.value = emptySet()
    }

    fun isAnySelected(): Boolean = _selectedNoteIds.value.isNotEmpty() || _selectedGroupIds.value.isNotEmpty()

    fun addToSelection(noteId: Long) {
        _selectedNoteIds.value = _selectedNoteIds.value + noteId
    }

    fun addGroupToSelection(groupId: Long) {
        _selectedGroupIds.value = _selectedGroupIds.value + groupId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setUncategorizedFilter(filter: UncategorizedFilter) {
        _currentUncategorizedFilter.value = filter
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
        val noteId = repository.insert(note)
        
        // Create initial tab
        val initialTab = NoteTab(
            noteId = noteId,
            content = content,
            orderIndex = 0
        )
        repository.insertTab(initialTab)
        
        return noteId
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateNoteTitleAndFolder(noteId: Long, title: String, folderId: Long?) {
        viewModelScope.launch {
            repository.updateTitleAndFolder(noteId, title, folderId)
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

    fun toggleLock(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(isLocked = !note.isLocked, updatedAt = System.currentTimeMillis()))
        }
    }

    fun setLockedPin(pin: String) {
        viewModelScope.launch {
            preferenceManager.saveLockedPin(pin)
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

    fun createGroup(name: String, noteIds: List<Long>) {
        viewModelScope.launch {
            noteGroupRepository.createGroup(name, noteIds)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            noteGroupRepository.deleteGroup(groupId)
        }
    }

    fun ungroup(groupId: Long) {
        viewModelScope.launch {
            noteGroupRepository.deleteGroup(groupId)
        }
    }

    fun renameGroup(groupId: Long, newName: String) {
        viewModelScope.launch {
            noteGroupRepository.renameGroup(groupId, newName)
        }
    }

    fun updateGroupOrder(groupId: Long, noteIds: List<Long>) {
        viewModelScope.launch {
            noteGroupRepository.updateGroupOrder(groupId, noteIds)
        }
    }

    fun toggleGroupStar(group: com.tv.wnotes.data.local.entity.NoteGroup) {
        viewModelScope.launch {
            val newState = !group.isStarred
            noteGroupRepository.updateGroup(group.copy(isStarred = newState, updatedAt = System.currentTimeMillis()))
            // Also star/unstar all notes in the group to match "same as selecting multiple notes"
            getGroupWithNotesById(group.id).firstOrNull()?.notes?.forEach { note ->
                repository.update(note.copy(isStarred = newState))
            }
        }
    }

    fun toggleGroupPin(group: com.tv.wnotes.data.local.entity.NoteGroup) {
        viewModelScope.launch {
            val newState = !group.isPinned
            noteGroupRepository.updateGroup(group.copy(isPinned = newState, updatedAt = System.currentTimeMillis()))
            // Also pin/unpin all notes in the group
            getGroupWithNotesById(group.id).firstOrNull()?.notes?.forEach { note ->
                repository.update(note.copy(isPinned = newState))
            }
        }
    }

    fun moveGroupToFolder(group: com.tv.wnotes.data.local.entity.NoteGroup, folderId: Long?) {
        viewModelScope.launch {
            noteGroupRepository.updateGroup(group.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
            // Also move all notes in the group to the folder
            getGroupWithNotesById(group.id).firstOrNull()?.notes?.forEach { note ->
                repository.update(note.copy(folderId = folderId))
            }
        }
    }

    fun moveGroupToTrash(group: com.tv.wnotes.data.local.entity.NoteGroup) {
        viewModelScope.launch {
            noteGroupRepository.updateGroup(group.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
            // Also move all notes in the group to trash
            getGroupWithNotesById(group.id).firstOrNull()?.notes?.forEach { note ->
                repository.update(note.copy(isDeleted = true))
            }
        }
    }

    fun toggleGroupLock(group: com.tv.wnotes.data.local.entity.NoteGroup) {
        viewModelScope.launch {
            noteGroupRepository.updateGroup(group.copy(isLocked = !group.isLocked, updatedAt = System.currentTimeMillis()))
        }
    }

    fun duplicateGroup(groupWithNotes: com.tv.wnotes.data.local.model.GroupWithNotes) {
        viewModelScope.launch {
            val newGroup = groupWithNotes.group.copy(
                id = 0,
                name = "${groupWithNotes.group.name} Copy",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val noteIds = groupWithNotes.notes.map { note ->
                repository.insert(note.copy(
                    id = 0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                ))
            }
            noteGroupRepository.createGroup(newGroup.name, noteIds)
            // Note: createGroup creates a fresh group with members, but we might want to copy other properties
            // However, createGroup with members is cleaner for now.
        }
    }

    fun appendQuickNote(noteId: Long, text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            val tabs = repository.getTabsForNoteSync(noteId)
            if (tabs.isEmpty()) {
                // If no tabs (shouldn't happen with current architecture), create one
                val newTab = NoteTab(
                    noteId = noteId,
                    content = text.trim()
                )
                repository.insertTab(newTab)
            } else {
                // Append to the last tab
                val lastTab = tabs.last()
                val separator = if (lastTab.content.isBlank()) "" else "\n\n"
                val updatedContent = lastTab.content + separator + text.trim()
                repository.updateTab(lastTab.copy(
                    content = updatedContent,
                    updatedAt = System.currentTimeMillis()
                ))
            }
            rebuildNoteSummary(noteId)
        }
    }

    fun getGroupWithNotesById(groupId: Long) = noteGroupRepository.getGroupWithNotesById(groupId)

    // --- Tab Management ---
    
    fun getTabsForNote(noteId: Long): Flow<List<NoteTab>> = repository.getTabsForNote(noteId)

    suspend fun addTabAndReturnId(noteId: Long, title: String? = null, content: String = "", afterTabIndex: Int? = null): Long {
        val tabs = repository.getTabsForNoteSync(noteId)
        val insertIndex = if (afterTabIndex != null) (afterTabIndex + 1).coerceAtMost(tabs.size) else tabs.size
        
        // Shift existing tabs if necessary
        if (insertIndex < tabs.size) {
            tabs.filter { it.orderIndex >= insertIndex }.forEach { 
                repository.updateTab(it.copy(orderIndex = it.orderIndex + 1))
            }
        }

        val newTab = NoteTab(
            noteId = noteId,
            title = title,
            content = content,
            orderIndex = insertIndex
        )
        val newId = repository.insertTab(newTab)
        rebuildNoteSummary(noteId)
        return newId
    }

    fun addTab(noteId: Long, title: String? = null, content: String = "") {
        viewModelScope.launch {
            addTabAndReturnId(noteId, title, content)
        }
    }

    fun updateTab(tab: NoteTab) {
        viewModelScope.launch {
            repository.updateTab(tab.copy(updatedAt = System.currentTimeMillis()))
            rebuildNoteSummary(tab.noteId)
        }
    }

    suspend fun updateTabSync(tab: NoteTab) {
        repository.updateTab(tab.copy(updatedAt = System.currentTimeMillis()))
        rebuildNoteSummary(tab.noteId)
    }

    fun deleteTab(tab: NoteTab) {
        viewModelScope.launch {
            repository.deleteTab(tab)
            rebuildNoteSummary(tab.noteId)
        }
    }

    // --- Annotation Management ---

    fun getAnnotationsForTab(tabId: Long): Flow<List<ContextAnnotation>> = repository.getAnnotationsForTab(tabId)

    fun saveAnnotation(annotation: ContextAnnotation) {
        viewModelScope.launch {
            repository.insertAnnotation(annotation)
        }
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch {
            repository.deleteAnnotationById(id)
        }
    }

    fun syncAnnotations(tabId: Long, annotations: List<ContextAnnotation>) {
        viewModelScope.launch {
            val existing = repository.getAnnotationsForTabSync(tabId)
            val existingIds = existing.map { it.id }.toSet()
            val newIds = annotations.map { it.id }.toSet()
            
            // Delete removed
            existing.filter { it.id !in newIds }.forEach {
                repository.deleteAnnotationById(it.id)
            }
            
            // Insert/Update new or changed
            annotations.forEach { annotation ->
                val matching = existing.find { it.id == annotation.id }
                if (matching == null || matching != annotation) {
                    repository.insertAnnotation(annotation)
                }
            }
        }
    }

    // --- Marker Management ---

    fun getMarkersForTab(tabId: Long): Flow<List<TextMarker>> = repository.getMarkersForTab(tabId)

    fun syncMarkers(tabId: Long, markers: List<TextMarker>) {
        viewModelScope.launch {
            val existing = repository.getMarkersForTabSync(tabId)
            val existingIds = existing.map { it.id }.toSet()
            val newIds = markers.map { it.id }.toSet()
            
            // Delete removed
            existing.filter { it.id !in newIds }.forEach {
                repository.deleteMarkerById(it.id)
            }
            
            // Insert/Update new or changed
            markers.forEach { marker ->
                val matching = existing.find { it.id == marker.id }
                if (matching == null || matching != marker) {
                    repository.insertMarker(marker)
                }
            }
        }
    }

    private suspend fun rebuildNoteSummary(noteId: Long) {
        val tabs = repository.getTabsForNoteSync(noteId)
        
        // Combine tab titles and contents for search indexing and previews
        val combinedContent = tabs.joinToString("\n\n") { tab ->
            val titlePart = if (tab.title.isNullOrBlank()) "" else "[${tab.title}]\n"
            "$titlePart${tab.content}"
        }
        
        repository.updateContent(noteId, combinedContent)
    }
}
