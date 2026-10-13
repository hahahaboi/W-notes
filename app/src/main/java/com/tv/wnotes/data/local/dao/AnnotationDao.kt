package com.tv.wnotes.data.local.dao

import androidx.room.*
import com.tv.wnotes.data.local.entity.ContextAnnotation
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM context_annotations")
    fun getAllAnnotations(): Flow<List<ContextAnnotation>>

    @Query("SELECT * FROM context_annotations WHERE tabId = :tabId")
    fun getAnnotationsForTab(tabId: Long): Flow<List<ContextAnnotation>>

    @Query("SELECT * FROM context_annotations WHERE tabId = :tabId")
    suspend fun getAnnotationsForTabSync(tabId: Long): List<ContextAnnotation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: ContextAnnotation)

    @Update
    suspend fun updateAnnotation(annotation: ContextAnnotation)

    @Delete
    suspend fun deleteAnnotation(annotation: ContextAnnotation)

    @Query("DELETE FROM context_annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: String)
}
