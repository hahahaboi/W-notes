package com.tv.wnotes.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tv.wnotes.ui.editor.NoteEditorScreen
import com.tv.wnotes.ui.folders.FolderDetailScreen
import com.tv.wnotes.ui.folders.FoldersScreen
import com.tv.wnotes.ui.home.*
import com.tv.wnotes.ui.paths.LinkPathsScreen
import com.tv.wnotes.ui.paths.PathDetailScreen
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    noteViewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onOpenDrawer: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = noteViewModel,
                folderViewModel = folderViewModel,
                linkPathViewModel = linkPathViewModel,
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onNavigateToFolders = { navController.navigate(Screen.Folders.route) },
                onNavigateToLinkPaths = { navController.navigate(Screen.LinkPaths.route) },
                onNavigateToStarred = { navController.navigate(Screen.Starred.route) },
                onNavigateToAllNotes = { navController.navigate(Screen.AllNotes.route) },
                onNavigateToUncategorized = { navController.navigate(Screen.Uncategorized.route) },
                onNavigateToTrash = { navController.navigate(Screen.Trash.route) },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(Screen.AllNotes.route) {
            AllNotesScreen(
                viewModel = noteViewModel,
                folderViewModel = folderViewModel,
                linkPathViewModel = linkPathViewModel,
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(Screen.Uncategorized.route) {
            UncategorizedNotesScreen(
                viewModel = noteViewModel,
                folderViewModel = folderViewModel,
                linkPathViewModel = linkPathViewModel,
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(Screen.Starred.route) {
            StarredNotesScreen(
                viewModel = noteViewModel,
                folderViewModel = folderViewModel,
                linkPathViewModel = linkPathViewModel,
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(Screen.Trash.route) {
            TrashNotesScreen(
                viewModel = noteViewModel,
                folderViewModel = folderViewModel,
                linkPathViewModel = linkPathViewModel,
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(
            route = Screen.Folders.route,
            arguments = listOf(
                navArgument("parentId") { 
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val parentId = backStackEntry.arguments?.getLong("parentId")
            FoldersScreen(
                parentId = if (parentId == -1L) null else parentId,
                viewModel = folderViewModel,
                onBack = { navController.popBackStack() },
                onFolderClick = { id, name ->
                    // Navigate deeper into subfolders
                    navController.navigate(Screen.Folders.createRoute(id))
                },
                onOpenDrawer = onOpenDrawer,
                onViewNotes = { id, name ->
                    navController.navigate(Screen.FolderDetail.createRoute(id, name))
                }
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType },
                navArgument("folderName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: 0L
            val folderName = backStackEntry.arguments?.getString("folderName") ?: ""
            FolderDetailScreen(
                folderId = folderId,
                folderName = folderName,
                folderViewModel = folderViewModel,
                noteViewModel = noteViewModel,
                linkPathViewModel = linkPathViewModel,
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onFolderClick = { id, name ->
                    navController.navigate(Screen.FolderDetail.createRoute(id, name))
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(Screen.LinkPaths.route) {
            LinkPathsScreen(
                viewModel = linkPathViewModel,
                noteViewModel = noteViewModel,
                onBack = { navController.popBackStack() },
                onPathClick = { id, name ->
                    navController.navigate(Screen.PathDetail.createRoute(id, name))
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(
            route = Screen.PathDetail.route,
            arguments = listOf(
                navArgument("pathId") { type = NavType.LongType },
                navArgument("pathName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val pathId = backStackEntry.arguments?.getLong("pathId") ?: 0L
            val pathName = backStackEntry.arguments?.getString("pathName") ?: ""
            PathDetailScreen(
                pathId = pathId,
                pathName = pathName,
                linkPathViewModel = linkPathViewModel,
                folderViewModel = folderViewModel,
                noteViewModel = noteViewModel,
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId, pathId))
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("noteId") { 
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("pathId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId")
            val pathId = backStackEntry.arguments?.getLong("pathId")
            NoteEditorScreen(
                noteId = if (noteId == -1L) null else noteId,
                pathId = if (pathId == -1L) null else pathId,
                viewModel = noteViewModel,
                folderViewModel = folderViewModel,
                linkPathViewModel = linkPathViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToNote = { id, pId ->
                    navController.navigate(Screen.Editor.createRoute(id, pId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }
    }
}
