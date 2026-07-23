package com.tv.wnotes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tv.wnotes.data.local.entity.Note

@Composable
fun NoteGrid(
    notes: List<Note>,
    onNoteClick: (Long) -> Unit,
    emptyStateMessage: String,
    modifier: Modifier = Modifier
) {
    if (notes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(emptyStateMessage)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = rememberLazyGridState(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onNoteClick(note.id) }
                )
            }
        }
    }
}
