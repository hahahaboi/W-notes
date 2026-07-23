
package com.tv.wnotes.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AllNotes : Screen("all_notes")
    data object Uncategorized : Screen("uncategorized")
    data object Editor : Screen("editor?noteId={noteId}&pathId={pathId}") {
        fun createRoute(noteId: Long? = null, pathId: Long? = null) = buildString {
            append("editor")
            val params = mutableListOf<String>()
            if (noteId != null) params.add("noteId=$noteId")
            if (pathId != null) params.add("pathId=$pathId")
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }
    }
    data object Folders : Screen("folders?parentId={parentId}") {
        fun createRoute(parentId: Long? = null) = if (parentId != null) "folders?parentId=$parentId" else "folders"
    }
    data object LinkPaths : Screen("linkpaths")
    data object PathDetail : Screen("path_detail/{pathId}/{pathName}") {
        fun createRoute(pathId: Long, pathName: String) = "path_detail/$pathId/$pathName"
    }
    data object Starred : Screen("starred")
    data object Trash : Screen("trash")
    data object FolderDetail : Screen("folder_detail/{folderId}/{folderName}") {
        fun createRoute(folderId: Long, folderName: String) = "folder_detail/$folderId/$folderName"
    }
}

