package com.tv.wnotes.data.local.dao

import androidx.room.*
import com.tv.wnotes.data.local.entity.LinkPath
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.PathMember
import com.tv.wnotes.data.local.model.GroupPathInfo
import com.tv.wnotes.data.local.model.NotePathInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkPathDao {
    @Query("SELECT * FROM link_paths")
    fun getAllPaths(): Flow<List<LinkPath>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPath(path: LinkPath): Long

    @Update
    suspend fun updatePath(path: LinkPath)

    @Delete
    suspend fun deletePath(path: LinkPath)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: PathMember)

    @Query("DELETE FROM path_members WHERE pathId = :pathId")
    suspend fun deleteMembersByPath(pathId: Long)

    @Query("DELETE FROM path_members WHERE noteId = :noteId")
    suspend fun deleteMembersByNoteId(noteId: Long)

    @Query("DELETE FROM path_members WHERE groupId = :groupId")
    suspend fun deleteMembersByGroupId(groupId: Long)

    @Query("SELECT * FROM path_members WHERE pathId = :pathId ORDER BY orderIndex ASC")
    fun getMembersInPath(pathId: Long): Flow<List<PathMember>>

    @Query("""
        SELECT notes.* FROM notes 
        INNER JOIN path_members ON notes.id = path_members.noteId 
        WHERE path_members.pathId = :pathId 
        ORDER BY path_members.orderIndex ASC
    """)
    fun getNotesInPath(pathId: Long): Flow<List<Note>>

    @Query("""
        SELECT link_paths.* FROM link_paths
        INNER JOIN path_members ON link_paths.id = path_members.pathId
        WHERE path_members.noteId = :noteId
        ORDER BY path_members.orderIndex ASC
    """)
    fun getPathsForNote(noteId: Long): Flow<List<LinkPath>>

    @Query("""
        SELECT path_members.noteId AS noteId, link_paths.id AS pathId, link_paths.name AS pathName
        FROM path_members
        INNER JOIN link_paths ON link_paths.id = path_members.pathId
        WHERE path_members.noteId IS NOT NULL
    """)
    fun getAllNotePathInfo(): Flow<List<NotePathInfo>>

    @Query("""
        SELECT path_members.groupId AS groupId, link_paths.id AS pathId, link_paths.name AS pathName
        FROM path_members
        INNER JOIN link_paths ON link_paths.id = path_members.pathId
        WHERE path_members.groupId IS NOT NULL
    """)
    fun getAllGroupPathInfo(): Flow<List<GroupPathInfo>>

    @Query("SELECT MAX(orderIndex) FROM path_members WHERE pathId = :pathId")
    suspend fun getMaxOrderIndex(pathId: Long): Int?

    @Transaction
    suspend fun updatePathOrder(pathId: Long, members: List<PathMember>) {
        deleteMembersByPath(pathId)
        members.forEach { insertMember(it) }
    }
}
