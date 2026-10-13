package com.tv.wnotes.data.local.dao

import androidx.room.*
import com.tv.wnotes.data.local.entity.TextMarker
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    @Query("SELECT * FROM text_markers")
    fun getAllMarkers(): Flow<List<TextMarker>>

    @Query("SELECT * FROM text_markers WHERE tabId = :tabId ORDER BY createdAt ASC")
    fun getMarkersForTab(tabId: Long): Flow<List<TextMarker>>

    @Query("SELECT * FROM text_markers WHERE tabId = :tabId")
    suspend fun getMarkersForTabSync(tabId: Long): List<TextMarker>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: TextMarker)

    @Update
    suspend fun updateMarker(marker: TextMarker)

    @Delete
    suspend fun deleteMarker(marker: TextMarker)

    @Query("DELETE FROM text_markers WHERE id = :id")
    suspend fun deleteMarkerById(id: String)
}
