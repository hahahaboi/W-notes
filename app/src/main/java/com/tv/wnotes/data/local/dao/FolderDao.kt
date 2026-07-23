package com.tv.wnotes.data.local.dao

import androidx.room.*
import com.tv.wnotes.data.local.entity.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY name ASC")
    fun getSubfolders(parentId: Long): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY name ASC")
    fun getRootFolders(): Flow<List<Folder>>
}
