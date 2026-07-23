package com.tv.wnotes.data.repository

import com.tv.wnotes.data.local.dao.FolderDao
import com.tv.wnotes.data.local.entity.Folder
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val folderDao: FolderDao) {
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()

    suspend fun insert(folder: Folder): Long = folderDao.insertFolder(folder)
    suspend fun update(folder: Folder) = folderDao.updateFolder(folder)
    suspend fun delete(folder: Folder) = folderDao.deleteFolder(folder)
    fun getSubfolders(parentId: Long) = folderDao.getSubfolders(parentId)
    fun getRootFolders() = folderDao.getRootFolders()
}
