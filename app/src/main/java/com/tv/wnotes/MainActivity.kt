package com.tv.wnotes

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tv.wnotes.navigation.NavGraph
import com.tv.wnotes.navigation.Screen
import com.tv.wnotes.ui.components.FloatingRoughNote
import com.tv.wnotes.ui.components.FocusTimerSidebar
import com.tv.wnotes.ui.components.WAnimatedIcon
import com.tv.wnotes.ui.theme.WNotesTheme
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import com.tv.wnotes.viewmodel.RoughNoteViewModel
import com.tv.wnotes.viewmodel.SessionViewModel
import com.tv.wnotes.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val savedDarkMode by settingsViewModel.darkMode.collectAsState()
            val isDarkMode = savedDarkMode ?: isSystemInDarkTheme()

            // Keep status/navigation bar icons readable against the chosen background.
            LaunchedEffect(isDarkMode) {
                val barStyle = if (isDarkMode) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            WNotesTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                var isDrawerOpen by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                // Very smooth and slow easing
                val customEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                
                val sidebarProgress by animateFloatAsState(
                    targetValue = if (isDrawerOpen) 1f else 0f,
                    animationSpec = tween(durationMillis = 1400, easing = customEasing),
                    label = "sidebarProgress"
                )

                // W expands only after sidebar is mostly open, collapses immediately
                val iconProgress by animateFloatAsState(
                    targetValue = if (isDrawerOpen && sidebarProgress > 0.8f) 1f else 0f,
                    animationSpec = tween(durationMillis = 1200, easing = customEasing),
                    label = "iconProgress"
                )

                BackHandler(enabled = isDrawerOpen) {
                    isDrawerOpen = false
                }
                
                val noteViewModel: NoteViewModel = viewModel()
                val folderViewModel: FolderViewModel = viewModel()
                val linkPathViewModel: LinkPathViewModel = viewModel()
                val roughNoteViewModel: RoughNoteViewModel = viewModel()
                val sessionViewModel: SessionViewModel = viewModel()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val snackbarHostState = remember { SnackbarHostState() }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main App Content
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        modifier = Modifier.fillMaxSize()
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            NavGraph(
                                navController = navController,
                                noteViewModel = noteViewModel,
                                folderViewModel = folderViewModel,
                                linkPathViewModel = linkPathViewModel,
                                onOpenDrawer = { isDrawerOpen = true },
                                drawerProgress = sidebarProgress,
                                isDarkMode = isDarkMode,
                                onDarkModeChange = settingsViewModel::setDarkMode
                            )

                            FocusTimerSidebar(viewModel = sessionViewModel)

                            FloatingRoughNote(
                                viewModel = roughNoteViewModel,
                                onShowSnackbar = { message, onUndo ->
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = message,
                                            actionLabel = "Undo"
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onUndo()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Custom Drawer Overlays
                    if (sidebarProgress > 0f) {
                        // Scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f * sidebarProgress))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isDrawerOpen = false }
                        )

                        // Drawer Sheet
                        val drawerWidth = 280.dp
                        val drawerEdgeColor = MaterialTheme.colorScheme.outlineVariant
                        
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(drawerWidth)
                                .offset { IntOffset(( (sidebarProgress - 1f) * drawerWidth.toPx() ).roundToInt(), 0) }
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                // Right edge line so the drawer stands apart from the dimmed screen in dark mode.
                                .drawWithContent {
                                    drawContent()
                                    drawLine(
                                        color = drawerEdgeColor,
                                        start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                .clickable(enabled = false) { } // Prevent clicks from going through to scrim
                        ) {
                            val drawerScrollState = rememberScrollState()
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(drawerScrollState)
                                        .padding(horizontal = 16.dp, vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    WAnimatedIcon(
                                        progress = iconProgress,
                                        modifier = Modifier.size(80.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        strokeWidth = 10f
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    DrawerRow(Icons.Outlined.Home, "Home", currentRoute == Screen.Home.route) { 
                                        navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } }
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.Outlined.Folder, "Folders", currentRoute == Screen.Folders.route) {
                                        navController.navigate(Screen.Folders.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.Outlined.Link, "Stapled", currentRoute == Screen.LinkPaths.route) { 
                                        navController.navigate(Screen.LinkPaths.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.Outlined.PushPin, "Pinned", currentRoute == Screen.Pinned.route) { 
                                        navController.navigate(Screen.Pinned.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.Outlined.StarBorder, "Starred", currentRoute == Screen.Starred.route) { 
                                        navController.navigate(Screen.Starred.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.AutoMirrored.Outlined.Notes, "All Notes", currentRoute == Screen.AllNotes.route) { 
                                        navController.navigate(Screen.AllNotes.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.AutoMirrored.Outlined.InsertDriveFile, "Uncategorized", currentRoute == Screen.Uncategorized.route) { 
                                        navController.navigate(Screen.Uncategorized.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.Outlined.Lock, "Locked", currentRoute == Screen.Locked.route) { 
                                        navController.navigate(Screen.Locked.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.Outlined.Delete, "Trash", currentRoute == Screen.Trash.route) { 
                                        navController.navigate(Screen.Trash.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.Outlined.Settings, "Settings", currentRoute == Screen.Settings.route) {
                                        navController.navigate(Screen.Settings.route)
                                        isDrawerOpen = false
                                    }
                                    DrawerRow(Icons.Outlined.Info, "About", currentRoute == Screen.About.route) { 
                                        navController.navigate(Screen.About.route)
                                        isDrawerOpen = false
                                    }

                                    // Spacer for bottom accessibility on small screens
                                    Spacer(modifier = Modifier.height(48.dp))
                                }

                                com.tv.wnotes.ui.components.WScrollbar(
                                    scrollState = drawerScrollState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, isSelected: Boolean, onClick: () -> Unit) {
    // The drawer sits outside the Scaffold, so set the text colour explicitly instead of inheriting it.
    val textColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        contentColor = textColor,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            // Large icon on the right with fade effect
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(90.dp)
                    .offset(x = 40.dp) // Moved more right (partially off-screen)
                    .graphicsLayer(alpha = 0.25f, compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Black, Color.Transparent),
                                startX = 0f,
                                endX = size.width
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                tint = textColor
            )

            // Title on the left, drawn on top
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium, // Decreased font size
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
            )
        }
    }
}
