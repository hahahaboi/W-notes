package com.tv.wnotes.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.tv.wnotes.data.local.entity.NoteTab
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StackedTabsWorkspace(
    tabs: List<NoteTab>,
    activeTabId: Long?,
    editorHosts: Map<Long, WEditorHost>,
    tabTitles: Map<Long, String>,
    onTabSelected: (Long) -> Unit,
    onClose: () -> Unit,
    onAddTab: () -> Unit,
    onTitleChange: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialPage = remember(tabs, activeTabId) {
        tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
    }
    
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    
    // Sync pager state back to activeTabId when settled
    LaunchedEffect(pagerState.currentPage) {
        if (tabs.isNotEmpty()) {
            onTabSelected(tabs[pagerState.currentPage].id)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            .clickable(enabled = true, onClick = {}) // Intercept clicks to prevent closing from background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Stacked Workspace",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} of ${tabs.size} tabs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Close, "Close Workspace")
                }
            }

            // The Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 32.dp),
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                val tab = tabs[page]
                val host = editorHosts[tab.id]
                val title = tabTitles[tab.id] ?: tab.title ?: ""
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .graphicsLayer {
                            // Subtle parallax/scaling effect
                            val pageOffset = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            
                            alpha = lerp(
                                start = 0.5f,
                                stop = 1f,
                                fraction = 1f - pageOffset.coerceIn(0f, 1f)
                            )
                            
                            scaleY = lerp(
                                start = 0.9f,
                                stop = 1f,
                                fraction = 1f - pageOffset.coerceIn(0f, 1f)
                            )
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Page Title Area (Static label for now, titles are managed by WEditor or could be editable here)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = deriveTabLabel(title, page),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Page Content (The actual WEditor)
                        Box(modifier = Modifier.weight(1f)) {
                            if (host != null) {
                                WEditor(
                                    host = host,
                                    title = title,
                                    onTitleChange = { onTitleChange(tab.id, it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // Fallback/Loading state
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }

            // Footer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (tabs.size < 10) {
                    Button(
                        onClick = onAddTab,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add New Tab")
                    }
                }
            }
        }
    }
}
