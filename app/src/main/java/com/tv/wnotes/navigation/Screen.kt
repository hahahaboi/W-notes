
package com.tv.wnotes.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AllNotes : Screen("all_notes")
    data object Uncategorized : Screen("uncategorized")
    data object Editor : Screen("editor?noteId={noteId}&pathId={pathId}&folderId={folderId}&tabId={tabId}") {
        fun createRoute(noteId: Long? = null, pathId: Long? = null, folderId: Long? = null, tabId: Long? = null) = buildString {
            append("editor")
            val params = mutableListOf<String>()
            if (noteId != null) params.add("noteId=$noteId")
            if (pathId != null) params.add("pathId=$pathId")
            if (folderId != null) params.add("folderId=$folderId")
            if (tabId != null) params.add("tabId=$tabId")
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
    data object Pinned : Screen("pinned")
    data object Trash : Screen("trash")
    data object Locked : Screen("locked")
    data object FolderDetail : Screen("folder_detail/{folderId}/{folderName}") {
        fun createRoute(folderId: Long, folderName: String) = "folder_detail/$folderId/$folderName"
    }
    data object GroupDetail : Screen("group_detail/{groupId}/{groupName}") {
        fun createRoute(groupId: Long, groupName: String) = "group_detail/$groupId/$groupName"
    }
    data object About : Screen("about")
    data object Settings : Screen("settings")
}

