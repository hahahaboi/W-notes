package com.tv.wnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tv.wnotes.navigation.NavGraph
import com.tv.wnotes.navigation.Screen
import com.tv.wnotes.ui.components.FloatingRoughNote
import com.tv.wnotes.ui.components.FocusTimerSidebar
import com.tv.wnotes.ui.theme.WNotesTheme
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import com.tv.wnotes.viewmodel.RoughNoteViewModel
import com.tv.wnotes.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WNotesTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                
                val noteViewModel: NoteViewModel = viewModel()
                val folderViewModel: FolderViewModel = viewModel()
                val linkPathViewModel: LinkPathViewModel = viewModel()
                val roughNoteViewModel: RoughNoteViewModel = viewModel()
                val sessionViewModel: SessionViewModel = viewModel()

                val allNotes by noteViewModel.allNotes.collectAsState()
                val folders by folderViewModel.allFolders.collectAsState()
                val starredNotes by noteViewModel.starredNotes.collectAsState()
                val trashNotes by noteViewModel.trashNotes.collectAsState()
                val uncategorizedNotes by noteViewModel.uncategorizedNotes.collectAsState()
                val linkPaths by linkPathViewModel.allPaths.collectAsState()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val snackbarHostState = remember { SnackbarHostState() }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "W Notes", 
                                    style = MaterialTheme.typography.headlineMedium, 
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                DrawerRow("🏠", "Home", currentRoute == Screen.Home.route) { 
                                    navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } }
                                    scope.launch { drawerState.close() } 
                                }
                                DrawerRow("📁", "Folders", currentRoute == Screen.Folders.route) {
                                    navController.navigate(Screen.Folders.route)
                                    scope.launch { drawerState.close() } 
                                }
                                DrawerRow("📎", "Stapled", currentRoute == Screen.LinkPaths.route) { 
                                    navController.navigate(Screen.LinkPaths.route)
                                    scope.launch { drawerState.close() } 
                                }
                                DrawerRow("⭐", "Starred", currentRoute == Screen.Starred.route) { 
                                    navController.navigate(Screen.Starred.route)
                                    scope.launch { drawerState.close() } 
                                }
                                DrawerRow("📝", "All Notes", currentRoute == Screen.AllNotes.route) { 
                                    navController.navigate(Screen.AllNotes.route)
                                    scope.launch { drawerState.close() } 
                                }
                                DrawerRow("📄", "Uncategorized", currentRoute == Screen.Uncategorized.route) { 
                                    navController.navigate(Screen.Uncategorized.route)
                                    scope.launch { drawerState.close() } 
                                }
                                DrawerRow("🗑", "Trash", currentRoute == Screen.Trash.route) { 
                                    navController.navigate(Screen.Trash.route)
                                    scope.launch { drawerState.close() } 
                                }
                            }
                        }
                    }
                ) {
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
                                onOpenDrawer = { scope.launch { drawerState.open() } }
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
                }
            }
        }
    }
}

@Composable
fun DrawerRow(icon: String, title: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
